import { createStore } from 'zustand/vanilla';
import type { AudioEngineAdapter } from '../adapters/audio-engine.js';
import type {
  AudioQuality,
  EQBand,
  PlayState,
  RepeatMode,
  Track,
} from '../models/types.js';
import type { MusicProvider } from '../providers/music-provider.js';

export interface PlayerState {
  queue: Track[];
  /** Original unshuffled order preserved so toggling shuffle off restores queue order. */
  originalQueue: Track[];
  currentIndex: number;
  currentTrack: Track | null;
  playState: PlayState;
  positionMs: number;
  durationMs: number;
  volume: number;
  repeatMode: RepeatMode;
  shuffleMode: boolean;
  autoPlaySimilar: boolean;
  crossfadeDuration: number;
  skipSilence: boolean;
  playbackSpeed: number;
  preferredQuality: AudioQuality;
  history: Track[];
}

export interface PlayerActions {
  setQueue(tracks: Track[], startIndex?: number): Promise<void>;
  enqueue(track: Track, playNext?: boolean): void;
  playTrack(track: Track): Promise<void>;
  play(): Promise<void>;
  pause(): Promise<void>;
  togglePlayPause(): Promise<void>;
  next(): Promise<void>;
  previous(): Promise<void>;
  seek(positionMs: number): Promise<void>;
  setVolume(volume01: number): Promise<void>;
  setRepeatMode(mode: RepeatMode): void;
  toggleShuffle(): void;
  setAutoPlaySimilar(enabled: boolean): void;
  setCrossfadeDuration(seconds: number): Promise<void>;
  setSkipSilence(enabled: boolean): Promise<void>;
  setPlaybackSpeed(rate: number): Promise<void>;
  setEQBands(bands: EQBand[], preampDb?: number): Promise<void>;
  setPreferredQuality(quality: AudioQuality): void;
  removeFromQueue(index: number): void;
  clearUpcoming(): void;
  clearQueue(): void;
}

export type PlayerStore = PlayerState & PlayerActions;

export interface PlayerStoreDeps {
  audioEngine: AudioEngineAdapter;
  getProvider: (providerId: string) => MusicProvider | undefined;
}

/**
 * Unbiased Fisher-Yates shuffle. Keeps `pinnedIndex` at index 0 when provided
 * so the currently playing track stays at the front of the shuffled queue.
 */
export function fisherYatesShuffle<T>(items: readonly T[], pinnedIndex?: number): T[] {
  const result = [...items];
  let start = 0;

  if (pinnedIndex !== undefined && pinnedIndex >= 0 && pinnedIndex < result.length) {
    const pinned = result[pinnedIndex]!;
    result[pinnedIndex] = result[0]!;
    result[0] = pinned;
    start = 1;
  }

  for (let i = result.length - 1; i > start; i--) {
    const j = start + Math.floor(Math.random() * (i - start + 1));
    const tmp = result[i]!;
    result[i] = result[j]!;
    result[j] = tmp;
  }

  return result;
}

const MAX_HISTORY = 100;

function pushHistory(history: Track[], track: Track | null): Track[] {
  if (!track) return history;
  const filtered = history.filter((t) => !(t.id === track.id && t.providerId === track.providerId));
  return [track, ...filtered].slice(0, MAX_HISTORY);
}

export function createPlayerStore(deps: PlayerStoreDeps) {
  const store = createStore<PlayerStore>((set, get) => {
    const loadAndPlayIndex = async (index: number) => {
      const { queue, currentTrack, history, preferredQuality } = get();
      const target = queue[index];
      if (!target) {
        set({ currentIndex: -1, currentTrack: null, playState: 'idle', positionMs: 0, durationMs: 0 });
        return;
      }

      const provider = deps.getProvider(target.providerId);
      if (!provider) {
        set({ playState: 'error' });
        throw new Error(`No MusicProvider registered for "${target.providerId}"`);
      }

      set({
        currentIndex: index,
        currentTrack: target,
        playState: 'loading',
        positionMs: 0,
        durationMs: target.durationMs,
        history: pushHistory(history, currentTrack),
      });

      try {
        const stream = await provider.getStreamUrl(target.id, preferredQuality);
        await deps.audioEngine.load(target, stream);
        await deps.audioEngine.play();
        set({ playState: 'playing' });
      } catch (err) {
        set({ playState: 'error' });
        throw err;
      }
    };

    deps.audioEngine.subscribe({
      onStateChange: (playState) => set({ playState }),
      onProgress: (positionMs, durationMs) => set({ positionMs, durationMs }),
      onTrackEnded: () => {
        void get().next();
      },
      onError: () => set({ playState: 'error' }),
    });

    return {
      queue: [],
      originalQueue: [],
      currentIndex: -1,
      currentTrack: null,
      playState: 'idle',
      positionMs: 0,
      durationMs: 0,
      volume: 1,
      repeatMode: 'off',
      shuffleMode: false,
      autoPlaySimilar: true,
      crossfadeDuration: 0,
      skipSilence: false,
      playbackSpeed: 1,
      preferredQuality: 'lossless',
      history: [],

      async setQueue(tracks, startIndex = 0) {
        const { shuffleMode } = get();
        const clampedIndex = tracks.length === 0 ? -1 : Math.max(0, Math.min(startIndex, tracks.length - 1));
        const activeQueue =
          shuffleMode && clampedIndex >= 0
            ? fisherYatesShuffle(tracks, clampedIndex)
            : [...tracks];
        const nextIndex = shuffleMode && clampedIndex >= 0 ? 0 : clampedIndex;

        set({
          originalQueue: [...tracks],
          queue: activeQueue,
        });

        if (nextIndex >= 0) {
          await loadAndPlayIndex(nextIndex);
        } else {
          set({ currentIndex: -1, currentTrack: null, playState: 'idle' });
        }
      },

      enqueue(track, playNext = false) {
        const { queue, originalQueue, currentIndex } = get();
        const insertAt = playNext && currentIndex >= 0 ? currentIndex + 1 : queue.length;
        const nextQueue = [...queue];
        nextQueue.splice(insertAt, 0, track);
        set({
          queue: nextQueue,
          originalQueue: [...originalQueue, track],
        });
      },

      async playTrack(track) {
        await get().setQueue([track], 0);
        if (get().autoPlaySimilar) {
          const provider = deps.getProvider(track.providerId);
          if (provider?.capabilities.recommendations) {
            void provider
              .getRecommendations(track.id, 15)
              .then((recs) => {
                if (recs.length === 0) return;
                const { queue, originalQueue, currentTrack } = get();
                // Only append if user is still on the seeded track
                if (currentTrack?.id !== track.id) return;
                const existingIds = new Set(queue.map((t) => `${t.providerId}:${t.id}`));
                const fresh = recs.filter((r) => !existingIds.has(`${r.providerId}:${r.id}`));
                if (fresh.length > 0) {
                  set({
                    queue: [...queue, ...fresh],
                    originalQueue: [...originalQueue, ...fresh],
                  });
                }
              })
              .catch(() => {});
          }
        }
      },

      async play() {
        await deps.audioEngine.play();
        set({ playState: 'playing' });
      },

      async pause() {
        await deps.audioEngine.pause();
        set({ playState: 'paused' });
      },

      async togglePlayPause() {
        const { playState } = get();
        if (playState === 'playing') {
          await get().pause();
        } else if (playState === 'paused' || playState === 'ended') {
          await get().play();
        }
      },

      async next() {
        const { queue, currentIndex, repeatMode, autoPlaySimilar, currentTrack } = get();
        if (queue.length === 0) return;

        if (repeatMode === 'one' && currentIndex >= 0) {
          await deps.audioEngine.seek(0);
          await deps.audioEngine.play();
          set({ positionMs: 0, playState: 'playing' });
          return;
        }

        const nextIdx = currentIndex + 1;
        if (nextIdx < queue.length) {
          await loadAndPlayIndex(nextIdx);
        } else if (repeatMode === 'all') {
          await loadAndPlayIndex(0);
        } else if (autoPlaySimilar && currentTrack) {
          const provider = deps.getProvider(currentTrack.providerId);
          if (provider?.capabilities.recommendations) {
            const recs = await provider.getRecommendations(currentTrack.id, 15).catch(() => []);
            const existingIds = new Set(queue.map((t) => `${t.providerId}:${t.id}`));
            const fresh = recs.filter((r) => !existingIds.has(`${r.providerId}:${r.id}`));
            if (fresh.length > 0) {
              const nextQueue = [...queue, ...fresh];
              set({ queue: nextQueue, originalQueue: [...get().originalQueue, ...fresh] });
              await loadAndPlayIndex(nextIdx);
              return;
            }
          }
          set({ playState: 'ended' });
        } else {
          set({ playState: 'ended' });
        }
      },

      async previous() {
        const { queue, currentIndex, positionMs, repeatMode } = get();
        if (queue.length === 0) return;

        // Standard player UX: restart current track if >3s elapsed.
        if (positionMs > 3000) {
          await deps.audioEngine.seek(0);
          set({ positionMs: 0 });
          return;
        }

        const prevIdx = currentIndex - 1;
        if (prevIdx >= 0) {
          await loadAndPlayIndex(prevIdx);
        } else if (repeatMode === 'all') {
          await loadAndPlayIndex(queue.length - 1);
        } else {
          await deps.audioEngine.seek(0);
          set({ positionMs: 0 });
        }
      },

      async seek(positionMs) {
        const clamped = Math.max(0, positionMs);
        await deps.audioEngine.seek(clamped);
        set({ positionMs: clamped });
      },

      async setVolume(volume01) {
        const clamped = Math.max(0, Math.min(1, volume01));
        await deps.audioEngine.setVolume(clamped);
        set({ volume: clamped });
      },

      setRepeatMode(mode) {
        set({ repeatMode: mode });
      },

      setAutoPlaySimilar(enabled) {
        set({ autoPlaySimilar: enabled });
      },

      toggleShuffle() {
        const { shuffleMode, originalQueue, queue, currentTrack } = get();
        if (!shuffleMode) {
          const currentIdx = currentTrack
            ? queue.findIndex((t) => t.id === currentTrack.id && t.providerId === currentTrack.providerId)
            : -1;
          const shuffled = fisherYatesShuffle(queue, currentIdx >= 0 ? currentIdx : undefined);
          set({
            shuffleMode: true,
            queue: shuffled,
            currentIndex: currentTrack ? 0 : -1,
          });
        } else {
          const restoredIdx = currentTrack
            ? originalQueue.findIndex((t) => t.id === currentTrack.id && t.providerId === currentTrack.providerId)
            : -1;
          set({
            shuffleMode: false,
            queue: [...originalQueue],
            currentIndex: restoredIdx,
          });
        }
      },

      async setCrossfadeDuration(seconds) {
        const clamped = Math.max(0, Math.min(12, seconds));
        await deps.audioEngine.setCrossfade(clamped);
        set({ crossfadeDuration: clamped });
      },

      async setSkipSilence(enabled) {
        await deps.audioEngine.setSkipSilence(enabled);
        set({ skipSilence: enabled });
      },

      async setPlaybackSpeed(rate) {
        const clamped = Math.max(0.25, Math.min(3, rate));
        await deps.audioEngine.setPlaybackSpeed(clamped);
        set({ playbackSpeed: clamped });
      },

      async setEQBands(bands, preampDb = 0) {
        await deps.audioEngine.setEQBands(bands, preampDb);
      },

      setPreferredQuality(quality) {
        set({ preferredQuality: quality });
      },

      removeFromQueue(index) {
        const { queue, originalQueue, currentIndex } = get();
        if (index < 0 || index >= queue.length || index === currentIndex) return;
        const removed = queue[index]!;
        const nextQueue = queue.filter((_, i) => i !== index);
        const nextOriginal = originalQueue.filter(
          (t) => !(t.id === removed.id && t.providerId === removed.providerId)
        );
        const nextIndex = index < currentIndex ? currentIndex - 1 : currentIndex;
        set({
          queue: nextQueue,
          originalQueue: nextOriginal,
          currentIndex: nextIndex,
        });
      },

      clearUpcoming() {
        const { queue, currentIndex } = get();
        if (currentIndex < 0) {
          set({ queue: [], originalQueue: [] });
          return;
        }
        const active = queue[currentIndex];
        set({
          queue: active ? [active] : [],
          originalQueue: active ? [active] : [],
          currentIndex: active ? 0 : -1,
        });
      },

      clearQueue() {
        set({
          queue: [],
          originalQueue: [],
          currentIndex: -1,
          currentTrack: null,
          playState: 'idle',
          positionMs: 0,
          durationMs: 0,
        });
      },
    };
  });

  return store;
}
