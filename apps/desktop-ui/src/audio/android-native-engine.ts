import type { AudioEngineAdapter, AudioEngineEvents, EQBand, StreamSource, Track } from '@swara/core';

interface SwaraNativeBridge {
  isNativeAudioAvailable?: () => boolean;
  playNativeTrack: (
    videoId: string,
    streamUrl: string,
    title: string,
    artist: string,
    artworkUrl: string,
    durationMs: number
  ) => void;
  playNative: () => void;
  pauseNative: () => void;
  seekNative: (posMs: number) => void;
  setVolumeNative: (vol: number) => void;
  setEqBands: (csv: string) => boolean;
}

function getBridge(): SwaraNativeBridge | null {
  if (typeof window === 'undefined') return null;
  const w = window as unknown as { SwaraNativeMedia?: SwaraNativeBridge };
  return w.SwaraNativeMedia ?? null;
}

export function isAndroidNativeAudio(): boolean {
  return Boolean(getBridge()?.isNativeAudioAvailable?.());
}

/**
 * Plays audio natively inside Android's foreground `SwaraMediaService` (`MediaPlayer` + `Equalizer`),
 * surviving app backgrounding/minimize and driving system MediaSession + HyperOS Dynamic Island.
 */
export class AndroidNativeAudioEngine implements AudioEngineAdapter {
  private readonly listeners = new Set<AudioEngineEvents>();

  constructor() {
    if (typeof window !== 'undefined') {
      const w = window as unknown as {
        __swaraNativeProgress?: (posMs: number, durMs: number, state: string) => void;
      };
      w.__swaraNativeProgress = (posMs, durMs, state) => {
        for (const l of this.listeners) {
          l.onProgress?.(posMs, durMs);
          if (state === 'playing' || state === 'paused' || state === 'loading') {
            l.onStateChange?.(state);
          } else if (state === 'ended') {
            l.onTrackEnded?.();
          } else if (state === 'error') {
            l.onError?.(new Error('Gagal memutar audio di pemutar native Android'));
          }
        }
      };
    }
  }

  async load(track: Track, stream: StreamSource): Promise<void> {
    const bridge = getBridge();
    if (!bridge) throw new Error('Android native bridge not available');
    for (const l of this.listeners) l.onStateChange?.('loading');
    bridge.playNativeTrack(
      track.id,
      stream.url,
      track.title,
      track.artists.map((a) => a.name).join(', '),
      track.artworkUrl ?? '',
      track.durationMs
    );
  }

  async play(): Promise<void> {
    getBridge()?.playNative();
  }

  async pause(): Promise<void> {
    getBridge()?.pauseNative();
  }

  async seek(positionMs: number): Promise<void> {
    getBridge()?.seekNative(Math.max(0, Math.round(positionMs)));
  }

  async setVolume(volume01: number): Promise<void> {
    getBridge()?.setVolumeNative(volume01);
  }

  async setEQBands(bands: EQBand[]): Promise<void> {
    getBridge()?.setEqBands(bands.map((b) => b.gainDb).join(','));
  }

  async setCrossfade(): Promise<void> {}
  async setPlaybackSpeed(): Promise<void> {}
  async setSkipSilence(): Promise<void> {}

  subscribe(events: AudioEngineEvents): () => void {
    this.listeners.add(events);
    return () => {
      this.listeners.delete(events);
    };
  }

  async dispose(): Promise<void> {
    this.listeners.clear();
  }
}
