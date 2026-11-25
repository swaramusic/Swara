import type {
  Album,
  Artist,
  AudioQuality,
  LyricsData,
  StreamSource,
  Track,
} from '../models/types.js';
import type {
  MusicProvider,
  ProviderCapabilities,
  SearchOptions,
  SearchResults,
} from './music-provider.js';

export interface SubsonicConfig {
  baseUrl: string;
  username: string;
  /** Pre-computed Subsonic salted MD5 token, or plaintext password for dev/local servers. */
  token?: string;
  salt?: string;
  password?: string;
  clientName?: string;
}

interface SubsonicSong {
  id: string;
  title: string;
  artist?: string;
  artistId?: string;
  album?: string;
  albumId?: string;
  duration?: number;
  track?: number;
  discNumber?: number;
  coverArt?: string;
  contentType?: string;
  bitRate?: number;
}

interface SubsonicResponse<T> {
  'subsonic-response': {
    status: 'ok' | 'failed';
    error?: { code: number; message: string };
  } & T;
}

/**
 * OpenSubsonic / Navidrome / Jellyfin-compatible provider skeleton.
 * Uses standard fetch so it runs identically in Browser, Tauri, and React Native.
 */
export class SubsonicProvider implements MusicProvider {
  readonly id = 'subsonic';
  readonly name = 'OpenSubsonic';
  readonly capabilities: ProviderCapabilities = {
    search: true,
    stream: true,
    lyrics: true,
    recommendations: true,
    offlineCache: true,
  };

  private readonly config: SubsonicConfig;

  constructor(config: SubsonicConfig) {
    this.config = {
      ...config,
      baseUrl: config.baseUrl.replace(/\/+$/, ''),
      clientName: config.clientName ?? 'Swara',
    };
  }

  private buildUrl(endpoint: string, params: Record<string, string | number> = {}): string {
    const search = new URLSearchParams({
      u: this.config.username,
      v: '1.16.1',
      c: this.config.clientName ?? 'Swara',
      f: 'json',
    });

    if (this.config.token && this.config.salt) {
      search.set('t', this.config.token);
      search.set('s', this.config.salt);
    } else if (this.config.password) {
      search.set('p', this.config.password);
    }

    for (const [k, v] of Object.entries(params)) {
      search.set(k, String(v));
    }

    return `${this.config.baseUrl}/rest/${endpoint}.view?${search.toString()}`;
  }

  private async request<T>(endpoint: string, params: Record<string, string | number> = {}): Promise<T> {
    const res = await fetch(this.buildUrl(endpoint, params));
    if (!res.ok) {
      throw new Error(`Subsonic HTTP ${res.status} on ${endpoint}`);
    }
    const data = (await res.json()) as SubsonicResponse<T>;
    const body = data['subsonic-response'];
    if (body.status !== 'ok') {
      throw new Error(body.error?.message ?? `Subsonic error on ${endpoint}`);
    }
    return body;
  }

  private mapSong(song: SubsonicSong): Track {
    const coverUrl = song.coverArt
      ? this.buildUrl('getCoverArt', { id: song.coverArt, size: 512 })
      : undefined;

    return {
      id: song.id,
      providerId: this.id,
      title: song.title,
      artists: [
        {
          id: song.artistId ?? 'unknown',
          providerId: this.id,
          name: song.artist ?? 'Unknown Artist',
        },
      ],
      ...(song.albumId
        ? {
            album: {
              id: song.albumId,
              providerId: this.id,
              title: song.album ?? 'Unknown Album',
              ...(coverUrl ? { artworkUrl: coverUrl } : {}),
            },
          }
        : {}),
      durationMs: (song.duration ?? 0) * 1000,
      ...(song.track !== undefined ? { trackNumber: song.track } : {}),
      ...(song.discNumber !== undefined ? { discNumber: song.discNumber } : {}),
      ...(coverUrl ? { artworkUrl: coverUrl } : {}),
    };
  }

  async search(query: string, options?: SearchOptions): Promise<SearchResults> {
    const limit = options?.limit ?? 20;
    const offset = options?.offset ?? 0;

    const body = await this.request<{
      searchResult3?: {
        song?: SubsonicSong[];
        album?: Array<{ id: string; name: string; artist?: string; artistId?: string; year?: number; coverArt?: string; songCount?: number }>;
        artist?: Array<{ id: string; name: string; coverArt?: string }>;
      };
    }>('search3', {
      query,
      songCount: limit,
      songOffset: offset,
      albumCount: limit,
      albumOffset: offset,
      artistCount: limit,
      artistOffset: offset,
    });

    const sr = body.searchResult3 ?? {};
    return {
      tracks: (sr.song ?? []).map((s) => this.mapSong(s)),
      albums: (sr.album ?? []).map((a) => ({
        id: a.id,
        providerId: this.id,
        title: a.name,
        artists: [{ id: a.artistId ?? 'unknown', providerId: this.id, name: a.artist ?? 'Unknown' }],
        ...(a.year !== undefined ? { releaseYear: a.year } : {}),
        ...(a.songCount !== undefined ? { trackCount: a.songCount } : {}),
        ...(a.coverArt ? { artworkUrl: this.buildUrl('getCoverArt', { id: a.coverArt, size: 512 }) } : {}),
      })),
      artists: (sr.artist ?? []).map((ar) => ({
        id: ar.id,
        providerId: this.id,
        name: ar.name,
        ...(ar.coverArt ? { artworkUrl: this.buildUrl('getCoverArt', { id: ar.coverArt, size: 512 }) } : {}),
      })),
    };
  }

  async getStreamUrl(trackId: string, preferredQuality?: AudioQuality): Promise<StreamSource> {
    const bitrateMap: Record<AudioQuality, number | undefined> = {
      low: 96,
      normal: 160,
      high: 320,
      lossless: undefined,
      hires: undefined,
    };
    const maxBitRate = preferredQuality ? bitrateMap[preferredQuality] : undefined;
    const params: Record<string, string | number> = { id: trackId };
    if (maxBitRate) {
      params.maxBitRate = maxBitRate;
    }

    return {
      url: this.buildUrl('stream', params),
      mimeType: maxBitRate ? 'audio/mpeg' : 'audio/flac',
      quality: preferredQuality ?? 'lossless',
      ...(maxBitRate ? { bitrateKbps: maxBitRate } : {}),
    };
  }

  async getAlbum(albumId: string): Promise<Album | null> {
    const body = await this.request<{
      album?: {
        id: string;
        name: string;
        artist?: string;
        artistId?: string;
        year?: number;
        coverArt?: string;
        song?: SubsonicSong[];
      };
    }>('getAlbum', { id: albumId });

    const a = body.album;
    if (!a) return null;

    return {
      id: a.id,
      providerId: this.id,
      title: a.name,
      artists: [{ id: a.artistId ?? 'unknown', providerId: this.id, name: a.artist ?? 'Unknown' }],
      ...(a.year !== undefined ? { releaseYear: a.year } : {}),
      ...(a.coverArt ? { artworkUrl: this.buildUrl('getCoverArt', { id: a.coverArt, size: 512 }) } : {}),
      tracks: (a.song ?? []).map((s) => this.mapSong(s)),
    };
  }

  async getArtist(artistId: string): Promise<Artist | null> {
    const body = await this.request<{
      artist?: { id: string; name: string; coverArt?: string };
    }>('getArtist', { id: artistId });

    const ar = body.artist;
    if (!ar) return null;

    return {
      id: ar.id,
      providerId: this.id,
      name: ar.name,
      ...(ar.coverArt ? { artworkUrl: this.buildUrl('getCoverArt', { id: ar.coverArt, size: 512 }) } : {}),
    };
  }

  async getLyrics(track: Track): Promise<LyricsData | null> {
    const artist = track.artists[0]?.name ?? '';
    const body = await this.request<{
      lyrics?: { value?: string };
    }>('getLyrics', { artist, title: track.title });

    const text = body.lyrics?.value;
    if (!text) return null;

    return {
      trackId: track.id,
      provider: this.id,
      synced: false,
      lines: text.split('\n').map((line) => ({
        startMs: 0,
        endMs: 0,
        text: line,
      })),
    };
  }

  async getRecommendations(seedTrackId: string, limit = 15): Promise<Track[]> {
    const body = await this.request<{
      similarSongs2?: { song?: SubsonicSong[] };
    }>('getSimilarSongs2', { id: seedTrackId, count: limit });

    return (body.similarSongs2?.song ?? []).map((s) => this.mapSong(s));
  }
}
