import type { AudioEngineAdapter, AudioEngineEvents, EQBand, PlayState, StreamSource, Track } from '@swara/core';

interface YTPlayer {
  loadVideoById(videoId: string): void;
  playVideo(): void;
  pauseVideo(): void;
  seekTo(seconds: number, allowSeekAhead: boolean): void;
  setVolume(volume0to100: number): void;
  setPlaybackRate(rate: number): void;
  getCurrentTime(): number;
  getDuration(): number;
  getPlayerState(): number;
  destroy(): void;
}

interface YTGlobal {
  Player: new (
    elementId: string | HTMLElement,
    options: {
      height?: string | number;
      width?: string | number;
      videoId?: string;
      playerVars?: Record<string, number | string>;
      events?: {
        onReady?: () => void;
        onStateChange?: (event: { data: number }) => void;
        onError?: (event: { data: number }) => void;
      };
    }
  ) => YTPlayer;
}

declare global {
  interface Window {
    YT?: YTGlobal;
    onYouTubeIframeAPIReady?: () => void;
  }
}

let ytApiPromise: Promise<YTGlobal> | null = null;

function loadYouTubeIframeApi(): Promise<YTGlobal> {
  if (typeof window === 'undefined') {
    return Promise.reject(new Error('YouTube IFrame API requires a browser environment'));
  }
  if (window.YT?.Player) {
    return Promise.resolve(window.YT);
  }
  if (!ytApiPromise) {
    ytApiPromise = new Promise<YTGlobal>((resolve) => {
      const prev = window.onYouTubeIframeAPIReady;
      window.onYouTubeIframeAPIReady = () => {
        prev?.();
        if (window.YT) resolve(window.YT);
      };
      const script = document.createElement('script');
      script.src = 'https://www.youtube.com/iframe_api';
      script.async = true;
      document.head.appendChild(script);
    });
  }
  return ytApiPromise;
}

/**
 * AudioEngineAdapter implementation for YouTube tracks using the official YouTube IFrame Player API.
 * Uses a real off-screen 240x135 container so browsers do not throttle media playback or timers.
 */
export class YouTubeIFrameEngine implements AudioEngineAdapter {
  private player: YTPlayer | null = null;
  private container: HTMLDivElement | null = null;
  private progressTimer: number | null = null;
  private readyPromise: Promise<YTPlayer> | null = null;
  private currentVideoId: string | null = null;
  private readonly listeners = new Set<AudioEngineEvents>();

  /** Preloads the YouTube IFrame API script on app mount so first click starts playback immediately. */
  preload(): void {
    void loadYouTubeIframeApi().catch(() => {});
  }

  private ensurePlayer(initialVideoId?: string): Promise<YTPlayer> {
    if (this.readyPromise) return this.readyPromise;

    this.readyPromise = loadYouTubeIframeApi().then(
      (YT) =>
        new Promise<YTPlayer>((resolve) => {
          const host = document.createElement('div');
          host.style.position = 'fixed';
          host.style.right = '0';
          host.style.top = '0';
          host.style.width = '4px';
          host.style.height = '4px';
          host.style.opacity = '0.01';
          host.style.overflow = 'hidden';
          host.style.pointerEvents = 'none';
          host.style.zIndex = '0';
          document.body.appendChild(host);
          this.container = host;

          const targetEl = document.createElement('div');
          host.appendChild(targetEl);

          const player = new YT.Player(targetEl, {
            width: 4,
            height: 4,
            ...(initialVideoId ? { videoId: initialVideoId } : {}),
            playerVars: {
              autoplay: 1,
              controls: 1,
              playsinline: 1,
              enablejsapi: 1,
              rel: 0,
            },
            events: {
              onReady: () => {
                this.player = player;
                if (this.currentVideoId && this.currentVideoId !== initialVideoId) {
                  player.loadVideoById(this.currentVideoId);
                }
                player.playVideo();
                this.startProgressLoop();
                resolve(player);
              },
              onStateChange: ({ data }) => {
                // YT states: -1 unstarted, 0 ended, 1 playing, 2 paused, 3 buffering, 5 cued
                if (data === 1) {
                  this.startProgressLoop();
                  this.emitState('playing');
                } else if (data === 2) {
                  this.stopProgressLoop();
                  this.emitState('paused');
                } else if (data === 3) {
                  this.emitState('loading');
                } else if (data === 0) {
                  this.stopProgressLoop();
                  this.emitState('ended');
                  for (const l of this.listeners) l.onTrackEnded?.();
                }
              },
              onError: ({ data }) => {
                this.stopProgressLoop();
                this.emitState('error');
                const msg =
                  data === 101 || data === 150
                    ? 'Lagu ini dibatasi pemiliknya untuk diputar di luar YouTube (Error 150). Coba versi lain.'
                    : `YouTube player error (code ${data})`;
                for (const l of this.listeners) {
                  l.onError?.(new Error(msg));
                }
              },
            },
          });
        })
    );

    return this.readyPromise;
  }

  private startProgressLoop(): void {
    this.stopProgressLoop();
    const tick = () => {
      if (!this.player) return;
      const pos = (this.player.getCurrentTime?.() || 0) * 1000;
      const dur = (this.player.getDuration?.() || 0) * 1000;
      if (dur > 0 || pos > 0) {
        for (const l of this.listeners) l.onProgress?.(pos, dur);
      }
    };
    tick();
    this.progressTimer = window.setInterval(tick, 250);
  }

  private stopProgressLoop(): void {
    if (this.progressTimer !== null) {
      window.clearInterval(this.progressTimer);
      this.progressTimer = null;
    }
  }

  private emitState(state: PlayState): void {
    for (const l of this.listeners) l.onStateChange?.(state);
  }

  async load(track: Track, stream: StreamSource): Promise<void> {
    const videoId = stream.url.startsWith('youtube://')
      ? stream.url.slice('youtube://'.length)
      : track.id;

    this.currentVideoId = videoId;
    this.emitState('loading');
    const wasInitialized = this.readyPromise !== null;
    const player = await this.ensurePlayer(videoId);
    if (wasInitialized) {
      player.loadVideoById(videoId);
    }
  }

  async play(): Promise<void> {
    const player = await this.ensurePlayer(this.currentVideoId ?? undefined);
    player.playVideo();
    this.startProgressLoop();
    this.emitState('playing');
  }

  async pause(): Promise<void> {
    this.stopProgressLoop();
    this.player?.pauseVideo();
  }

  async seek(positionMs: number): Promise<void> {
    this.player?.seekTo(Math.max(0, positionMs / 1000), true);
  }

  async setVolume(volume01: number): Promise<void> {
    this.player?.setVolume(Math.round(Math.max(0, Math.min(1, volume01)) * 100));
  }

  async setEQBands(_bands: EQBand[], _preampDb?: number): Promise<void> {}

  async setCrossfade(_durationSec: number): Promise<void> {}

  async setPlaybackSpeed(rate: number): Promise<void> {
    this.player?.setPlaybackRate(rate);
  }

  async setSkipSilence(_enabled: boolean): Promise<void> {}

  subscribe(events: AudioEngineEvents): () => void {
    this.listeners.add(events);
    return () => {
      this.listeners.delete(events);
    };
  }

  async dispose(): Promise<void> {
    this.stopProgressLoop();
    this.listeners.clear();
    this.player?.destroy();
    this.player = null;
    this.container?.remove();
    this.container = null;
    this.readyPromise = null;
  }
}
