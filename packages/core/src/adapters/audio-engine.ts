import type { EQBand, PlayState, StreamSource, Track } from '../models/types.js';

export interface AudioEngineEvents {
  onStateChange?: (state: PlayState) => void;
  onProgress?: (positionMs: number, durationMs: number) => void;
  onTrackEnded?: () => void;
  onError?: (error: Error) => void;
}

/**
 * Platform-specific audio backend contract:
 * - Web: Web Audio API + HTMLAudioElement / AudioWorklet
 * - Desktop: Tauri v2 Rust backend (rodio / symphonia / libmpv)
 * - Mobile: react-native-track-player (ExoPlayer / AVPlayer)
 */
export interface AudioEngineAdapter {
  load(track: Track, stream: StreamSource): Promise<void>;
  play(): Promise<void>;
  pause(): Promise<void>;
  seek(positionMs: number): Promise<void>;
  setVolume(volume01: number): Promise<void>;
  setEQBands(bands: EQBand[], preampDb?: number): Promise<void>;
  setCrossfade(durationSec: number): Promise<void>;
  setPlaybackSpeed(rate: number): Promise<void>;
  setSkipSilence(enabled: boolean): Promise<void>;
  subscribe(events: AudioEngineEvents): () => void;
  dispose(): Promise<void>;
}
