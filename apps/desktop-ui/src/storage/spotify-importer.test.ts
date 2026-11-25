import { describe, expect, it } from 'vitest';
import { extractSpotifyPlaylistId, parseSpotifyEmbedHtml } from './spotify-importer.js';

describe('spotify-importer', () => {
  it('extracts playlist ID from standard URL, intl URL, and URI', () => {
    expect(extractSpotifyPlaylistId('https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc')).toBe(
      '37i9dQZF1DXcBWIGoYBM5M'
    );
    expect(extractSpotifyPlaylistId('https://open.spotify.com/intl-id/playlist/37i9dQZF1DXcBWIGoYBM5M')).toBe(
      '37i9dQZF1DXcBWIGoYBM5M'
    );
    expect(extractSpotifyPlaylistId('spotify:playlist:37i9dQZF1DXcBWIGoYBM5M')).toBe(
      '37i9dQZF1DXcBWIGoYBM5M'
    );
    expect(extractSpotifyPlaylistId('not-a-valid-url')).toBeNull();
  });

  it('parses Spotify embed __NEXT_DATA__ JSON', () => {
    const payload = {
      props: {
        pageProps: {
          state: {
            data: {
              entity: {
                name: 'Indo Pop Chill',
                trackList: [
                  { title: 'Bermuara', subtitle: 'Rizky Febian, Mahalini' },
                  { title: 'Sialan', subtitle: 'Adrian Khalif, Juicy Luicy' },
                ],
              },
            },
          },
        },
      },
    };
    const html = `<html><body><script id="__NEXT_DATA__" type="application/json">${JSON.stringify(payload)}</script></body></html>`;
    const result = parseSpotifyEmbedHtml(html);
    expect(result.title).toBe('Indo Pop Chill');
    expect(result.tracks).toEqual([
      { title: 'Bermuara', artist: 'Rizky Febian, Mahalini' },
      { title: 'Sialan', artist: 'Adrian Khalif, Juicy Luicy' },
    ]);
  });
});
