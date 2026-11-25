import { describe, expect, it, vi } from 'vitest';
import type { AudioEngineAdapter } from '../adapters/audio-engine.js';
import type { Track } from '../models/types.js';
import { LocalFileProvider } from '../providers/local-file-provider.js';
import { createPlayerStore, fisherYatesShuffle } from './player-store.js';

function makeTrack(id: string): Track {
  return {
    id,
    providerId: 'local',
    title: `Track ${id}`,
    artists: [{ id: 'a1', providerId: 'local', name: 'Artist 1' }],
    durationMs: 180_000,
    localPath: `/music/${id}.flac`,
  };
}

function makeMockAudioEngine(): AudioEngineAdapter {
  return {
    load: vi.fn(async () => {}),
    play: vi.fn(async () => {}),
    pause: vi.fn(async () => {}),
    seek: vi.fn(async () => {}),
    setVolume: vi.fn(async () => {}),
    setEQBands: vi.fn(async () => {}),
    setCrossfade: vi.fn(async () => {}),
    setPlaybackSpeed: vi.fn(async () => {}),
    setSkipSilence: vi.fn(async () => {}),
    subscribe: vi.fn(() => () => {}),
    dispose: vi.fn(async () => {}),
  };
}

describe('fisherYatesShuffle', () => {
  it('pins the active index at 0 and preserves all elements', () => {
    const items = ['a', 'b', 'c', 'd', 'e'];
    const shuffled = fisherYatesShuffle(items, 2);
    expect(shuffled[0]).toBe('c');
    expect(shuffled).toHaveLength(items.length);
    expect([...shuffled].sort()).toEqual([...items].sort());
  });
});

describe('createPlayerStore', () => {
  it('loads and plays tracks, toggles shuffle, and restores original queue order', async () => {
    const tracks = [makeTrack('1'), makeTrack('2'), makeTrack('3')];
    const provider = new LocalFileProvider({
      tracks,
      albums: [],
      artists: [],
      resolveUri: async (p) => `file://${p}`,
    });
    const audioEngine = makeMockAudioEngine();
    const store = createPlayerStore({
      audioEngine,
      getProvider: (id) => (id === 'local' ? provider : undefined),
    });

    await store.getState().setQueue(tracks, 1);
    expect(store.getState().currentTrack?.id).toBe('2');
    expect(store.getState().playState).toBe('playing');
    expect(audioEngine.load).toHaveBeenCalledTimes(1);

    store.getState().toggleShuffle();
    expect(store.getState().shuffleMode).toBe(true);
    expect(store.getState().queue[0]?.id).toBe('2');
    expect(store.getState().currentIndex).toBe(0);

    store.getState().toggleShuffle();
    expect(store.getState().shuffleMode).toBe(false);
    expect(store.getState().queue.map((t) => t.id)).toEqual(['1', '2', '3']);
    expect(store.getState().currentIndex).toBe(1);
  });

  it('clamps crossfade duration and playback speed', async () => {
    const store = createPlayerStore({
      audioEngine: makeMockAudioEngine(),
      getProvider: () => undefined,
    });

    await store.getState().setCrossfadeDuration(20);
    expect(store.getState().crossfadeDuration).toBe(12);

    await store.getState().setPlaybackSpeed(0.1);
    expect(store.getState().playbackSpeed).toBe(0.25);
  });

  it('auto-populates queue with similar recommendations on playTrack when autoPlaySimilar is true', async () => {
    const t1 = makeTrack('1');
    const rec1 = makeTrack('rec-1');
    const rec2 = makeTrack('rec-2');
    const provider = new LocalFileProvider({
      tracks: [t1, rec1, rec2],
      albums: [],
      artists: [],
      resolveUri: async (p) => `file://${p}`,
    });
    provider.capabilities.recommendations = true;
    provider.getRecommendations = vi.fn(async () => [rec1, rec2]);

    const store = createPlayerStore({
      audioEngine: makeMockAudioEngine(),
      getProvider: () => provider,
    });

    await store.getState().playTrack(t1);
    await Promise.resolve();
    expect(store.getState().queue.map((t) => t.id)).toEqual(['1', 'rec-1', 'rec-2']);

    store.getState().removeFromQueue(1);
    expect(store.getState().queue.map((t) => t.id)).toEqual(['1', 'rec-2']);
  });
});
