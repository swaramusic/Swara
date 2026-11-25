import type { PlayState, Track } from '@swara/core';

export interface MediaSessionHandlers {
  onPlay: () => void;
  onPause: () => void;
  onNext: () => void;
  onPrevious: () => void;
  onSeekTo: (positionMs: number) => void;
  onSeekBy?: (offsetMs: number) => void;
}

const SUPPORTED_ACTIONS: MediaSessionAction[] = [
  'play',
  'pause',
  'previoustrack',
  'nexttrack',
  'seekto',
  'seekbackward',
  'seekforward',
];

/**
 * Bridges Swara playback state and OS lock-screen / hardware media keys via navigator.mediaSession.
 */
export class MediaSessionController {
  private readonly supported =
    typeof navigator !== 'undefined' && 'mediaSession' in navigator;

  constructor(handlers: MediaSessionHandlers) {
    if (!this.supported) return;

    const ms = navigator.mediaSession;
    const safeSet = (action: MediaSessionAction, fn: MediaSessionActionHandler | null) => {
      try {
        ms.setActionHandler(action, fn);
      } catch {
        // Some browsers throw for unsupported MediaSession actions.
      }
    };

    safeSet('play', () => handlers.onPlay());
    safeSet('pause', () => handlers.onPause());
    safeSet('nexttrack', () => handlers.onNext());
    safeSet('previoustrack', () => handlers.onPrevious());
    safeSet('seekto', (details) => {
      if (details.seekTime !== undefined) {
        handlers.onSeekTo(details.seekTime * 1000);
      }
    });
    safeSet('seekbackward', (details) => {
      const offsetMs = (details.seekOffset ?? 10) * -1000;
      handlers.onSeekBy?.(offsetMs);
    });
    safeSet('seekforward', (details) => {
      const offsetMs = (details.seekOffset ?? 10) * 1000;
      handlers.onSeekBy?.(offsetMs);
    });
  }

  setTrack(track: Track | null): void {
    if (!this.supported || typeof MediaMetadata === 'undefined') return;

    if (!track) {
      navigator.mediaSession.metadata = null;
      return;
    }

    const artwork: MediaImage[] = track.artworkUrl
      ? [{ src: track.artworkUrl, sizes: '512x512', type: 'image/png' }]
      : [];

    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: track.artists.map((a) => a.name).join(', '),
      album: track.album?.title ?? '',
      artwork,
    });
  }

  setPlaybackState(state: PlayState): void {
    if (!this.supported) return;
    if (state === 'playing') {
      navigator.mediaSession.playbackState = 'playing';
    } else if (state === 'paused') {
      navigator.mediaSession.playbackState = 'paused';
    } else {
      navigator.mediaSession.playbackState = 'none';
    }
  }

  setPositionState(positionMs: number, durationMs: number, playbackRate: number): void {
    if (!this.supported || !navigator.mediaSession.setPositionState) return;
    if (durationMs <= 0 || !Number.isFinite(durationMs)) return;

    const duration = durationMs / 1000;
    const position = Math.max(0, Math.min(positionMs / 1000, duration));

    try {
      navigator.mediaSession.setPositionState({
        duration,
        playbackRate: playbackRate > 0 ? playbackRate : 1,
        position,
      });
    } catch {
      // Ignore transient state mismatches during track transitions.
    }
  }

  dispose(): void {
    if (!this.supported) return;
    for (const action of SUPPORTED_ACTIONS) {
      try {
        navigator.mediaSession.setActionHandler(action, null);
      } catch {
        // Ignore unsupported actions.
      }
    }
    navigator.mediaSession.metadata = null;
  }
}
