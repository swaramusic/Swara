import type { AudioEngineAdapter, AudioEngineEvents, EQBand, StreamSource, Track } from '@swara/core';
import { AndroidNativeAudioEngine, isAndroidNativeAudio } from './android-native-engine.js';
import { WebAudioEngine } from './web-audio-engine.js';
import { YouTubeIFrameEngine } from './youtube-iframe-engine.js';

/**
 * Routes playback seamlessly between `AndroidNativeAudioEngine` (on Android APK),
 * `WebAudioEngine` (local/direct lossless streams), and `YouTubeIFrameEngine` (Desktop/Web fallback).
 */
export class HybridAudioEngine implements AudioEngineAdapter {
  private readonly webEngine: WebAudioEngine;
  private readonly ytEngine: YouTubeIFrameEngine;
  private readonly androidEngine: AndroidNativeAudioEngine;
  private activeBackend: 'web' | 'youtube' | 'android' = 'web';
  private lastLoad: { track: Track; stream: StreamSource } | null = null;
  private readonly listeners = new Set<AudioEngineEvents>();

  preloadYouTube(): void {
    if (!isAndroidNativeAudio()) {
      this.ytEngine.preload();
    }
  }

  constructor(
    webEngine: WebAudioEngine,
    ytEngine = new YouTubeIFrameEngine(),
    androidEngine = new AndroidNativeAudioEngine()
  ) {
    this.webEngine = webEngine;
    this.ytEngine = ytEngine;
    this.androidEngine = androidEngine;

    const forward = (backend: 'web' | 'youtube' | 'android'): AudioEngineEvents => ({
      onStateChange: (s) => {
        if (this.activeBackend === backend) {
          for (const l of this.listeners) l.onStateChange?.(s);
        }
      },
      onProgress: (pos, dur) => {
        if (this.activeBackend === backend) {
          for (const l of this.listeners) l.onProgress?.(pos, dur);
        }
      },
      onTrackEnded: () => {
        if (this.activeBackend === backend) {
          for (const l of this.listeners) l.onTrackEnded?.();
        }
      },
      onError: (err) => {
        if (this.activeBackend === 'android' && backend === 'android' && this.lastLoad) {
          // Fallback to YouTubeIFrameEngine when ANDROID_VR is bot-gated (LOGIN_REQUIRED)
          const { track, stream } = this.lastLoad;
          this.activeBackend = 'youtube';
          void this.ytEngine.load(track, stream).then(() => this.ytEngine.play()).catch((e) => {
            for (const l of this.listeners) l.onError?.(e instanceof Error ? e : err);
          });
          return;
        }
        if (this.activeBackend === backend) {
          for (const l of this.listeners) l.onError?.(err);
        }
      },
    });

    this.webEngine.subscribe(forward('web'));
    this.ytEngine.subscribe(forward('youtube'));
    this.androidEngine.subscribe(forward('android'));
  }

  private current(): AudioEngineAdapter {
    if (this.activeBackend === 'android') return this.androidEngine;
    return this.activeBackend === 'youtube' ? this.ytEngine : this.webEngine;
  }

  async load(track: Track, stream: StreamSource): Promise<void> {
    this.lastLoad = { track, stream };
    const isYt = track.providerId === 'youtube' || stream.url.startsWith('youtube://');
    const nextBackend = isYt
      ? isAndroidNativeAudio()
        ? 'android'
        : 'youtube'
      : 'web';

    if (this.activeBackend !== nextBackend) {
      await this.current().pause();
      this.activeBackend = nextBackend;
    }
    await this.current().load(track, stream);
  }

  async play(): Promise<void> {
    await this.current().play();
  }

  async pause(): Promise<void> {
    await this.current().pause();
  }

  async seek(positionMs: number): Promise<void> {
    await this.current().seek(positionMs);
  }

  async setVolume(volume01: number): Promise<void> {
    await Promise.all([this.webEngine.setVolume(volume01), this.ytEngine.setVolume(volume01)]);
  }

  async setEQBands(bands: EQBand[], preampDb?: number): Promise<void> {
    await Promise.all([
      this.webEngine.setEQBands(bands, preampDb),
      this.androidEngine.setEQBands(bands),
    ]);
  }

  async setCrossfade(durationSec: number): Promise<void> {
    await this.webEngine.setCrossfade(durationSec);
  }

  async setPlaybackSpeed(rate: number): Promise<void> {
    await Promise.all([this.webEngine.setPlaybackSpeed(rate), this.ytEngine.setPlaybackSpeed(rate)]);
  }

  async setSkipSilence(enabled: boolean): Promise<void> {
    await this.webEngine.setSkipSilence(enabled);
  }

  subscribe(events: AudioEngineEvents): () => void {
    this.listeners.add(events);
    return () => {
      this.listeners.delete(events);
    };
  }

  async dispose(): Promise<void> {
    this.listeners.clear();
    await Promise.all([this.webEngine.dispose(), this.ytEngine.dispose()]);
  }
}
