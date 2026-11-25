import { describe, expect, it } from 'vitest';
import { type CacheRecord, planLRUEviction } from './offline-cache.js';

function makeEntry(trackId: string, sizeBytes: number, lastAccessedAtMs: number): CacheRecord {
  return {
    trackId,
    providerId: 'local',
    localUri: `opfs://swara-audio-cache/${trackId}`,
    sizeBytes,
    cachedAtMs: 100,
    lastAccessedAtMs,
    mimeType: 'audio/flac',
  };
}

describe('planLRUEviction', () => {
  it('evicts least recently used tracks until incoming file fits', () => {
    const entries = [
      makeEntry('t1', 500, 300),
      makeEntry('t2', 400, 100), // oldest
      makeEntry('t3', 300, 200), // second oldest
    ];

    // Total = 1200, max = 1000, incoming = 400 -> need total <= 600 -> evict t2 (400) and t3 (300)
    const evict = planLRUEviction(entries, 400, 1000);
    expect(evict).toEqual(['t2', 't3']);
  });

  it('throws if a single file exceeds maxBytes', () => {
    expect(() => planLRUEviction([], 2000, 1000)).toThrow(/exceeds cache limit/);
  });
});
