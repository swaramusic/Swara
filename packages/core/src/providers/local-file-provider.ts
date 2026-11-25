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

export interface LocalLibraryIndex {
  tracks: Track[];
  albums: Album[];
  artists: Artist[];
  resolveUri: (localPath: string) => Promise<string>;
}

/**
 * Platform-agnostic local music provider. Native/Web scanners populate the index
 * and supply a URI resolver (e.g., Tauri `convertFileSrc`, Web `URL.createObjectURL`).
 */
export class LocalFileProvider implements MusicProvider {
  readonly id = 'local';
  readonly name = 'Local Files';
  readonly capabilities: ProviderCapabilities = {
    search: true,
    stream: true,
    lyrics: false,
    recommendations: false,
    offlineCache: false,
  };

  private index: LocalLibraryIndex;

  constructor(index: LocalLibraryIndex) {
    this.index = index;
  }

  updateIndex(index: LocalLibraryIndex): void {
    this.index = index;
  }

  async search(query: string, options?: SearchOptions): Promise<SearchResults> {
    const q = query.trim().toLowerCase();
    const limit = options?.limit ?? 25;
    const offset = options?.offset ?? 0;

    if (!q) {
      return { tracks: [], albums: [], artists: [] };
    }

    const tracks = this.index.tracks
      .filter(
        (t) =>
          t.title.toLowerCase().includes(q) ||
          t.artists.some((a) => a.name.toLowerCase().includes(q)) ||
          (t.album?.title.toLowerCase().includes(q) ?? false)
      )
      .slice(offset, offset + limit);

    const albums = this.index.albums
      .filter((a) => a.title.toLowerCase().includes(q))
      .slice(offset, offset + limit);

    const artists = this.index.artists
      .filter((a) => a.name.toLowerCase().includes(q))
      .slice(offset, offset + limit);

    return { tracks, albums, artists };
  }

  async getStreamUrl(trackId: string, _preferredQuality?: AudioQuality): Promise<StreamSource> {
    const track = this.index.tracks.find((t) => t.id === trackId);
    if (!track?.localPath) {
      throw new Error(`Local track not found or missing path: ${trackId}`);
    }

    const uri = await this.index.resolveUri(track.localPath);
    const ext = track.localPath.split('.').pop()?.toLowerCase();
    const mimeMap: Record<string, string> = {
      flac: 'audio/flac',
      wav: 'audio/wav',
      opus: 'audio/opus',
      m4a: 'audio/mp4',
      mp3: 'audio/mpeg',
    };

    return {
      url: uri,
      mimeType: (ext && mimeMap[ext]) ?? 'audio/mpeg',
      quality: ext === 'flac' || ext === 'wav' ? 'lossless' : 'high',
    };
  }

  async getAlbum(albumId: string): Promise<Album | null> {
    const album = this.index.albums.find((a) => a.id === albumId);
    if (!album) return null;
    const tracks = this.index.tracks.filter((t) => t.album?.id === albumId);
    return { ...album, tracks };
  }

  async getArtist(artistId: string): Promise<Artist | null> {
    return this.index.artists.find((a) => a.id === artistId) ?? null;
  }

  async getLyrics(_track: Track): Promise<LyricsData | null> {
    // Embedded/sidecar .lrc parsing is handled by the platform scanner or LRCLIB fallback.
    return null;
  }

  async getRecommendations(_seedTrackId: string, _limit = 10): Promise<Track[]> {
    return [];
  }
}
