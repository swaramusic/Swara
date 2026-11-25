import type { MusicProvider, Playlist, Track } from '@swara/core';

import { isTauriDesktop, tauriFetch } from '../config/tauri-bridge.js';

export interface ParsedSpotifyPlaylist {
  title: string;
  tracks: Array<{ title: string; artist: string }>;
}

export function extractSpotifyPlaylistId(input: string): string | null {
  const trimmed = input.trim();
  const urlMatch = /open\.spotify\.com\/(?:intl-[a-z]+\/)?playlist\/([a-zA-Z0-9]+)/.exec(trimmed);
  if (urlMatch?.[1]) return urlMatch[1];

  const uriMatch = /spotify:playlist:([a-zA-Z0-9]+)/.exec(trimmed);
  if (uriMatch?.[1]) return uriMatch[1];

  if (/^[a-zA-Z0-9]{15,30}$/.test(trimmed)) return trimmed;
  return null;
}

export function parseSpotifyEmbedHtml(html: string): ParsedSpotifyPlaylist {
  const match = /<script id="__NEXT_DATA__" type="application\/json">([\s\S]*?)<\/script>/.exec(html);
  if (!match?.[1]) {
    throw new Error('Playlist Spotify tidak ditemukan atau bersifat privat.');
  }

  const parsed = JSON.parse(match[1]) as {
    props?: {
      pageProps?: {
        state?: {
          data?: {
            entity?: {
              name?: string;
              trackList?: Array<{ title?: string; subtitle?: string }>;
            };
          };
        };
      };
    };
  };

  const entity = parsed.props?.pageProps?.state?.data?.entity;
  const rawList = entity?.trackList ?? [];
  if (rawList.length === 0) {
    throw new Error('Playlist Spotify kosong atau tidak dapat dibaca.');
  }

  return {
    title: entity?.name?.trim() || 'Spotify Import',
    tracks: rawList
      .filter((t) => t.title)
      .map((t) => ({
        title: t.title!.trim(),
        artist: (t.subtitle ?? '').replace(/\u00a0/g, ' ').trim(),
      })),
  };
}

/**
 * Imports a public Spotify playlist by URL/ID and resolves tracks via any `MusicProvider` (e.g. YouTubeProvider).
 */
export async function importSpotifyPlaylist(
  urlOrId: string,
  provider: MusicProvider,
  onProgress?: (resolved: number, total: number) => void
): Promise<Playlist> {
  const id = extractSpotifyPlaylistId(urlOrId);
  if (!id) {
    throw new Error('Link playlist Spotify tidak valid. Contoh: https://open.spotify.com/playlist/...');
  }

  const base =
    typeof import.meta !== 'undefined' && (import.meta as { env?: { DEV?: boolean } }).env?.DEV
      ? '/api/spotify-embed'
      : 'https://open.spotify.com/embed';
  const res = await (isTauriDesktop() ? tauriFetch : fetch)(`${base}/playlist/${id}`);
  if (!res.ok) {
    throw new Error(`Gagal mengambil playlist Spotify (HTTP ${res.status})`);
  }

  const html = await res.text();
  const parsed = parseSpotifyEmbedHtml(html);

  // Resolve up to 30 tracks in batches of 5 so import stays fast without rate-limiting.
  const targets = parsed.tracks.slice(0, 30);
  const resolvedTracks: Track[] = [];
  const batchSize = 5;

  for (let i = 0; i < targets.length; i += batchSize) {
    const batch = targets.slice(i, i + batchSize);
    const results = await Promise.all(
      batch.map(async (item) => {
        try {
          const q = item.artist ? `${item.title} ${item.artist}` : item.title;
          const found = await provider.search(q, { limit: 1 });
          return found.tracks[0] ?? null;
        } catch {
          return null;
        }
      })
    );
    for (const track of results) {
      if (track) resolvedTracks.push(track);
    }
    onProgress?.(Math.min(i + batchSize, targets.length), targets.length);
  }

  const now = Date.now();
  return {
    id: `pl-spotify-${now}`,
    providerId: provider.id,
    title: parsed.title,
    tracks: resolvedTracks,
    createdAtMs: now,
    updatedAtMs: now,
  };
}
