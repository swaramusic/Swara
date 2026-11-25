import type {
  AudioEngineAdapter,
  AudioEngineEvents,
  AudioQuality,
  EQBand,
  MusicProvider,
  PlayState,
  StreamSource,
  Track,
} from '@swara/core';
import { EQ_FREQUENCIES } from './eq-presets.js';
import { MediaSessionController } from './media-session.js';

export interface WebAudioEngineOptions {
  getProvider?: (providerId: string) => MusicProvider | undefined;
  preferredQuality?: AudioQuality;
  enableNormalization?: boolean;
  mediaSessionHandlers?: {
    onNext?: () => void;
    onPrevious?: () => void;
  };
}

interface Deck {
  id: 'A' | 'B';
  element: HTMLAudioElement;
  source: MediaElementAudioSourceNode;
  gain: GainNode;
  track: Track | null;
  stream: StreamSource | null;
  retriedRefresh: boolean;
  cleanupListeners: () => void;
}

const STREAM_EXPIRY_BUFFER_MS = 5_000;

function dbToLinear(db: number): number {
  return Math.pow(10, db / 20);
}

/**
 * Dual-deck Web Audio API engine implementing gapless playback, equal-power crossfade,
 * 10-band parametric EQ, ReplayGain/DynamicsCompressor normalization, and MediaSession.
 */
export class WebAudioEngine implements AudioEngineAdapter {
  private ctx: AudioContext | null = null;
  private preampGain: GainNode | null = null;
  private eqFilters: BiquadFilterNode[] = [];
  private compressor: DynamicsCompressorNode | null = null;
  private masterGain: GainNode | null = null;

  private deckA: Deck | null = null;
  private deckB: Deck | null = null;
  private activeDeckId: 'A' | 'B' = 'A';

  private volume = 1;
  private crossfadeSec = 0;
  private playbackSpeed = 1;
  private preservePitch = true;
  private normalizationEnabled: boolean;
  private crossfading = false;

  private readonly listeners = new Set<AudioEngineEvents>();
  private readonly options: WebAudioEngineOptions;
  private readonly mediaSession: MediaSessionController;

  constructor(options: WebAudioEngineOptions = {}) {
    this.options = options;
    this.normalizationEnabled = options.enableNormalization ?? true;

    this.mediaSession = new MediaSessionController({
      onPlay: () => void this.play(),
      onPause: () => void this.pause(),
      onNext: () => this.options.mediaSessionHandlers?.onNext?.(),
      onPrevious: () => this.options.mediaSessionHandlers?.onPrevious?.(),
      onSeekTo: (ms) => void this.seek(ms),
      onSeekBy: (offsetMs) => {
        const deck = this.getActiveDeck();
        const currentMs = (deck?.element.currentTime ?? 0) * 1000;
        void this.seek(Math.max(0, currentMs + offsetMs));
      },
    });
  }

  private ensureGraph(): void {
    if (this.ctx) return;

    const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    const ctx = new AudioCtx();
    this.ctx = ctx;

    const preamp = ctx.createGain();
    preamp.gain.value = 1;
    this.preampGain = preamp;

    const filters = EQ_FREQUENCIES.map((freq, idx) => {
      const filter = ctx.createBiquadFilter();
      filter.type = idx === 0 ? 'lowshelf' : idx === EQ_FREQUENCIES.length - 1 ? 'highshelf' : 'peaking';
      filter.frequency.value = freq;
      filter.Q.value = 1.41;
      filter.gain.value = 0;
      return filter;
    });
    this.eqFilters = filters;

    const compressor = ctx.createDynamicsCompressor();
    compressor.threshold.value = this.normalizationEnabled ? -14 : 0;
    compressor.knee.value = 12;
    compressor.ratio.value = this.normalizationEnabled ? 4 : 1;
    compressor.attack.value = 0.005;
    compressor.release.value = 0.25;
    this.compressor = compressor;

    const master = ctx.createGain();
    master.gain.value = this.volume;
    this.masterGain = master;

    // Connect: preamp -> eq[0..9] -> compressor -> master -> destination
    let tail: AudioNode = preamp;
    for (const f of filters) {
      tail.connect(f);
      tail = f;
    }
    tail.connect(compressor);
    compressor.connect(master);
    master.connect(ctx.destination);

    this.deckA = this.createDeck('A', ctx, preamp);
    this.deckB = this.createDeck('B', ctx, preamp);
  }

  private createDeck(id: 'A' | 'B', ctx: AudioContext, destination: AudioNode): Deck {
    const element = new Audio();
    element.crossOrigin = 'anonymous';
    element.preload = 'auto';
    element.playbackRate = this.playbackSpeed;
    this.applyPreservePitch(element, this.preservePitch);

    const source = ctx.createMediaElementSource(element);
    const gain = ctx.createGain();
    gain.gain.value = id === 'A' ? 1 : 0;
    source.connect(gain);
    gain.connect(destination);

    const deck: Deck = {
      id,
      element,
      source,
      gain,
      track: null,
      stream: null,
      retriedRefresh: false,
      cleanupListeners: () => {},
    };

    const onTimeUpdate = () => {
      if (this.activeDeckId !== deck.id) return;
      const durationMs = Number.isFinite(element.duration) ? element.duration * 1000 : (deck.track?.durationMs ?? 0);
      const positionMs = element.currentTime * 1000;

      this.emitProgress(positionMs, durationMs);
      this.mediaSession.setPositionState(positionMs, durationMs, element.playbackRate);

      if (
        this.crossfadeSec > 0 &&
        !this.crossfading &&
        durationMs > 0 &&
        durationMs - positionMs <= this.crossfadeSec * 1000
      ) {
        const standby = this.getStandbyDeck();
        if (standby?.track && standby.element.src) {
          void this.triggerCrossfade(deck, standby);
        }
      }
    };

    const onEnded = () => {
      if (this.activeDeckId !== deck.id) return;
      const standby = this.getStandbyDeck();
      // True gapless handoff if standby deck is already preloaded and crossfade is 0.
      if (this.crossfadeSec === 0 && standby?.track && standby.element.src) {
        void this.handoffGapless(deck, standby);
        return;
      }
      this.emitState('ended');
      this.mediaSession.setPlaybackState('ended');
      for (const l of this.listeners) l.onTrackEnded?.();
    };

    const onError = () => {
      if (this.activeDeckId !== deck.id) return;
      void this.handleDeckError(deck);
    };

    element.addEventListener('timeupdate', onTimeUpdate);
    element.addEventListener('ended', onEnded);
    element.addEventListener('error', onError);

    deck.cleanupListeners = () => {
      element.removeEventListener('timeupdate', onTimeUpdate);
      element.removeEventListener('ended', onEnded);
      element.removeEventListener('error', onError);
    };

    return deck;
  }

  private getActiveDeck(): Deck | null {
    return this.activeDeckId === 'A' ? this.deckA : this.deckB;
  }

  private getStandbyDeck(): Deck | null {
    return this.activeDeckId === 'A' ? this.deckB : this.deckA;
  }

  private async resumeContext(): Promise<void> {
    if (!this.ctx) return;
    if (this.ctx.state === 'suspended') {
      try {
        await this.ctx.resume();
      } catch (err) {
        const error = err instanceof Error ? err : new Error('AudioContext resume blocked by autoplay policy');
        this.emitError(error);
        throw error;
      }
    }
  }

  private async resolveValidStream(track: Track, stream: StreamSource): Promise<StreamSource> {
    const isExpired =
      stream.expiresAtMs !== undefined &&
      stream.expiresAtMs <= Date.now() + STREAM_EXPIRY_BUFFER_MS;

    if (!isExpired) return stream;

    const provider = this.options.getProvider?.(track.providerId);
    if (!provider) return stream;

    return provider.getStreamUrl(track.id, this.options.preferredQuality ?? stream.quality);
  }

  private async handleDeckError(deck: Deck): Promise<void> {
    if (!deck.retriedRefresh && deck.track && this.options.getProvider) {
      const provider = this.options.getProvider(deck.track.providerId);
      if (provider) {
        deck.retriedRefresh = true;
        try {
          const fresh = await provider.getStreamUrl(
            deck.track.id,
            this.options.preferredQuality ?? deck.stream?.quality
          );
          deck.stream = fresh;
          deck.element.src = fresh.url;
          deck.element.load();
          await deck.element.play();
          return;
        } catch (refreshErr) {
          this.emitState('error');
          this.emitError(refreshErr instanceof Error ? refreshErr : new Error('Failed to refresh expired stream'));
          return;
        }
      }
    }

    const mediaErr = deck.element.error;
    this.emitState('error');
    this.emitError(new Error(mediaErr?.message || `Audio playback error (code ${mediaErr?.code ?? 'unknown'})`));
  }

  private computeReplayGainLinear(track: Track): number {
    if (!this.normalizationEnabled || track.replayGainDb === undefined) return 1;
    // Clamp ReplayGain compensation to [-12, +6] dB to prevent clipping.
    const clampedDb = Math.max(-12, Math.min(6, track.replayGainDb));
    return dbToLinear(clampedDb);
  }

  async load(track: Track, stream: StreamSource): Promise<void> {
    this.ensureGraph();
    const active = this.getActiveDeck();
    const standby = this.getStandbyDeck();
    if (!active || !this.ctx) return;

    const validStream = await this.resolveValidStream(track, stream);

    // Stop standby if any previous crossfade was in flight.
    if (standby) {
      standby.element.pause();
      standby.gain.gain.setValueAtTime(0, this.ctx.currentTime);
    }

    this.crossfading = false;
    active.track = track;
    active.stream = validStream;
    active.retriedRefresh = false;
    active.element.src = validStream.url;
    active.element.currentTime = 0;
    active.element.playbackRate = this.playbackSpeed;
    active.gain.gain.setValueAtTime(this.computeReplayGainLinear(track), this.ctx.currentTime);
    active.element.load();

    this.mediaSession.setTrack(track);
    this.emitState('loading');
  }

  /**
   * Preloads the next track onto the standby deck for gapless or crossfade transition.
   */
  async preloadNext(track: Track, stream: StreamSource): Promise<void> {
    this.ensureGraph();
    const standby = this.getStandbyDeck();
    if (!standby || !this.ctx) return;

    const validStream = await this.resolveValidStream(track, stream);
    standby.track = track;
    standby.stream = validStream;
    standby.retriedRefresh = false;
    standby.element.src = validStream.url;
    standby.element.currentTime = 0;
    standby.element.playbackRate = this.playbackSpeed;
    standby.gain.gain.setValueAtTime(0, this.ctx.currentTime);
    standby.element.load();
  }

  private async handoffGapless(outgoing: Deck, incoming: Deck): Promise<void> {
    if (!this.ctx || !incoming.track) return;
    const now = this.ctx.currentTime;
    outgoing.element.pause();
    outgoing.gain.gain.setValueAtTime(0, now);

    incoming.gain.gain.setValueAtTime(this.computeReplayGainLinear(incoming.track), now);
    this.activeDeckId = incoming.id;
    this.mediaSession.setTrack(incoming.track);
    await this.play();
  }

  private async triggerCrossfade(outgoing: Deck, incoming: Deck): Promise<void> {
    if (!this.ctx || !incoming.track) return;
    this.crossfading = true;

    const now = this.ctx.currentTime;
    const dur = this.crossfadeSec;
    const steps = 20;
    const fadeOutCurve = new Float32Array(steps);
    const fadeInCurve = new Float32Array(steps);
    const outBase = this.computeReplayGainLinear(outgoing.track!);
    const inBase = this.computeReplayGainLinear(incoming.track);

    // Equal-power crossfade curves: cos(t * pi/2) & sin(t * pi/2)
    for (let i = 0; i < steps; i++) {
      const t = i / (steps - 1);
      fadeOutCurve[i] = Math.cos(t * Math.PI * 0.5) * outBase;
      fadeInCurve[i] = Math.sin(t * Math.PI * 0.5) * inBase;
    }

    outgoing.gain.gain.setValueCurveAtTime(fadeOutCurve, now, dur);
    incoming.gain.gain.setValueCurveAtTime(fadeInCurve, now, dur);

    this.activeDeckId = incoming.id;
    this.mediaSession.setTrack(incoming.track);

    try {
      await incoming.element.play();
      this.emitState('playing');
      this.mediaSession.setPlaybackState('playing');
    } catch (err) {
      this.emitError(err instanceof Error ? err : new Error('Crossfade playback failed'));
    } finally {
      window.setTimeout(() => {
        outgoing.element.pause();
        this.crossfading = false;
      }, dur * 1000);
    }
  }

  async play(): Promise<void> {
    this.ensureGraph();
    await this.resumeContext();
    const active = this.getActiveDeck();
    if (!active) return;

    try {
      await active.element.play();
      this.emitState('playing');
      this.mediaSession.setPlaybackState('playing');
    } catch (err) {
      const error = err instanceof Error ? err : new Error('Playback blocked');
      this.emitState(error.name === 'NotAllowedError' ? 'paused' : 'error');
      this.emitError(error);
      throw error;
    }
  }

  async pause(): Promise<void> {
    const active = this.getActiveDeck();
    if (!active) return;
    active.element.pause();
    this.emitState('paused');
    this.mediaSession.setPlaybackState('paused');
  }

  async seek(positionMs: number): Promise<void> {
    const active = this.getActiveDeck();
    if (!active) return;
    const targetSec = Math.max(0, positionMs / 1000);
    active.element.currentTime = targetSec;
    const durationMs = Number.isFinite(active.element.duration) ? active.element.duration * 1000 : 0;
    this.emitProgress(targetSec * 1000, durationMs);
  }

  async setVolume(volume01: number): Promise<void> {
    this.volume = Math.max(0, Math.min(1, volume01));
    if (this.masterGain && this.ctx) {
      this.masterGain.gain.setTargetAtTime(this.volume, this.ctx.currentTime, 0.015);
    }
  }

  async setEQBands(bands: EQBand[], preampDb = 0): Promise<void> {
    this.ensureGraph();
    if (!this.ctx || !this.preampGain) return;

    const now = this.ctx.currentTime;
    this.preampGain.gain.setTargetAtTime(dbToLinear(Math.max(-12, Math.min(12, preampDb))), now, 0.015);

    this.eqFilters.forEach((filter, idx) => {
      const band = bands[idx];
      if (!band) return;
      filter.frequency.setTargetAtTime(band.frequencyHz, now, 0.015);
      filter.gain.setTargetAtTime(Math.max(-12, Math.min(12, band.gainDb)), now, 0.015);
      filter.Q.setTargetAtTime(Math.max(0.1, Math.min(18, band.q)), now, 0.015);
    });
  }

  async setCrossfade(durationSec: number): Promise<void> {
    this.crossfadeSec = Math.max(0, Math.min(12, durationSec));
  }

  async setPlaybackSpeed(rate: number): Promise<void> {
    this.playbackSpeed = Math.max(0.25, Math.min(3, rate));
    if (this.deckA) this.deckA.element.playbackRate = this.playbackSpeed;
    if (this.deckB) this.deckB.element.playbackRate = this.playbackSpeed;
  }

  setPreservePitch(enabled: boolean): void {
    this.preservePitch = enabled;
    if (this.deckA) this.applyPreservePitch(this.deckA.element, enabled);
    if (this.deckB) this.applyPreservePitch(this.deckB.element, enabled);
  }

  private applyPreservePitch(el: HTMLAudioElement, enabled: boolean): void {
    const anyEl = el as HTMLAudioElement & {
      preservesPitch?: boolean;
      webkitPreservesPitch?: boolean;
    };
    if ('preservesPitch' in anyEl) anyEl.preservesPitch = enabled;
    if ('webkitPreservesPitch' in anyEl) anyEl.webkitPreservesPitch = enabled;
  }

  async setSkipSilence(_enabled: boolean): Promise<void> {
    // Web Audio silence-skipping requires an AudioWorklet RMS detector; reserved for Phase 2 DSP.
  }

  subscribe(events: AudioEngineEvents): () => void {
    this.listeners.add(events);
    return () => {
      this.listeners.delete(events);
    };
  }

  private emitState(state: PlayState): void {
    for (const l of this.listeners) l.onStateChange?.(state);
  }

  private emitProgress(positionMs: number, durationMs: number): void {
    for (const l of this.listeners) l.onProgress?.(positionMs, durationMs);
  }

  private emitError(error: Error): void {
    for (const l of this.listeners) l.onError?.(error);
  }

  async dispose(): Promise<void> {
    this.mediaSession.dispose();
    this.listeners.clear();

    for (const deck of [this.deckA, this.deckB]) {
      if (!deck) continue;
      deck.cleanupListeners();
      deck.element.pause();
      deck.element.removeAttribute('src');
      deck.element.load();
      deck.source.disconnect();
      deck.gain.disconnect();
    }

    this.preampGain?.disconnect();
    for (const f of this.eqFilters) f.disconnect();
    this.compressor?.disconnect();
    this.masterGain?.disconnect();

    this.deckA = null;
    this.deckB = null;
    this.eqFilters = [];

    if (this.ctx) {
      await this.ctx.close();
      this.ctx = null;
    }
  }
}
