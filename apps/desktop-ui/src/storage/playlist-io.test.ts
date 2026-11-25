import type { Playlist } from '@swara/core';
import { describe, expect, it } from 'vitest';
import { exportPlaylist, importPlaylist } from './playlist-io.js';

const samplePlaylist: Playlist = {
  id: 'pl-1',
  providerId: 'local',
  title: 'Night Drive',
  tracks: [
    {
      id: 't1',
      providerId: 'local',
      title: 'Midnight, City',
      artists: [{ id: 'a1', providerId: 'local', name: 'M83' }],
      album: { id: 'alb1', providerId: 'local', title: 'Hurry Up' },
      durationMs: 243_000,
      localPath: '/music/m83.flac',
    },
  ],
  createdAtMs: 1000,
  updatedAtMs: 1000,
};

describe('playlist-io', () => {
  it('round-trips M3U playlists', () => {
    const m3u = exportPlaylist(samplePlaylist, 'm3u');
    const parsed = importPlaylist(m3u, 'm3u');
    expect(parsed.title).toBe('Night Drive');
    expect(parsed.tracks).toHaveLength(1);
    expect(parsed.tracks[0]?.title).toBe('Midnight, City');
    expect(parsed.tracks[0]?.artists[0]?.name).toBe('M83');
  });

  it('round-trips CSV playlists with commas in track title', () => {
    const csv = exportPlaylist(samplePlaylist, 'csv');
    const parsed = importPlaylist(csv, 'csv', 'CSV Import');
    expect(parsed.tracks[0]?.title).toBe('Midnight, City');
    expect(parsed.tracks[0]?.album?.title).toBe('Hurry Up');
    expect(parsed.tracks[0]?.durationMs).toBe(243_000);
  });

  it('round-trips JSON playlists', () => {
    const json = exportPlaylist(samplePlaylist, 'json');
    const parsed = importPlaylist(json, 'json');
    expect(parsed.id).toBe('pl-1');
    expect(parsed.tracks[0]?.localPath).toBe('/music/m83.flac');
  });
});
