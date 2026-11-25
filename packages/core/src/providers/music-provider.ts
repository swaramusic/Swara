import type {
  Album,
  Artist,
  AudioQuality,
  LyricsData,
  StreamSource,
  Track,
} from '../models/types.js';

export interface ProviderCapabilities {
  search: boolean;
  stream: boolean;
  lyrics: boolean;
  recommendations: boolean;
  offlineCache: boolean;
}

export interface SearchOptions {
  limit?: number;
  offset?: number;
  types?: Array<'track' | 'album' | 'artist'>;
}

export interface SearchResults {
  tracks: Track[];
  albums: Album[];
  artists: Artist[];
}

/**
 * Abstraction over any audio source (local filesystem, OpenSubsonic/Navidrome,
 * Jamendo, or community JS/JSON extensions). Keeps Swara decoupled from any single backend.
 */
export interface MusicProvider {
  readonly id: string;
  readonly name: string;
  readonly capabilities: ProviderCapabilities;

  search(query: string, options?: SearchOptions): Promise<SearchResults>;
  getStreamUrl(trackId: string, preferredQuality?: AudioQuality): Promise<StreamSource>;
  getAlbum(albumId: string): Promise<Album | null>;
  getArtist(artistId: string): Promise<Artist | null>;
  getLyrics(track: Track): Promise<LyricsData | null>;
  getRecommendations(seedTrackId: string, limit?: number): Promise<Track[]>;
}
