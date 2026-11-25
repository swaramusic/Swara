import { describe, expect, it } from 'vitest';
import { extractYouTubePlaylistId, parseLrc, YouTubeProvider } from './youtube-provider.js';

describe('YouTubeProvider & parseLrc', () => {
  it('parses LRC timestamps and computes endMs from next line', () => {
    const lrc = `[00:05.50] First line\n[00:10.00] Second line`;
    const lines = parseLrc(lrc);
    expect(lines).toHaveLength(2);
    expect(lines[0]).toEqual({ startMs: 5500, endMs: 10000, text: 'First line' });
    expect(lines[1]?.startMs).toBe(10000);
  });

  it('returns cached trending tracks when search query is empty', async () => {
    const provider = new YouTubeProvider({
      initialTracks: [
        {
          id: 'E7kHvjvU6JY',
          providerId: 'youtube',
          title: 'Hati-Hati di Jalan',
          artists: [{ id: 'yt-tulus', providerId: 'youtube', name: 'Tulus' }],
          durationMs: 242_000,
        },
      ],
    });

    const res = await provider.search('');
    expect(res.tracks).toHaveLength(1);
    expect(res.tracks[0]?.id).toBe('E7kHvjvU6JY');
  });

  it('extracts YouTube / YouTube Music playlist IDs from URLs', () => {
    expect(
      extractYouTubePlaylistId('https://music.youtube.com/playlist?list=PL4fGSI1pDJn6puJdseH2Rt9sMvt9E2M4i')
    ).toBe('PL4fGSI1pDJn6puJdseH2Rt9sMvt9E2M4i');
    expect(
      extractYouTubePlaylistId('https://www.youtube.com/watch?v=abc&list=PL12345_abc')
    ).toBe('PL12345_abc');
    expect(extractYouTubePlaylistId('invalid-link')).toBeNull();
  });
});
