export type AudioQuality = 'low' | 'normal' | 'high' | 'lossless' | 'hires';

export interface StreamSource {
  url: string;
  mimeType: string;
  quality: AudioQuality;
  bitrateKbps?: number;
  sampleRateHz?: number;
  bitDepth?: number;
  expiresAtMs?: number;
}

export interface ArtistRef {
  id: string;
  providerId: string;
  name: string;
}

export interface AlbumRef {
  id: string;
  providerId: string;
  title: string;
  artworkUrl?: string;
}

export interface Artist extends ArtistRef {
  artworkUrl?: string;
  genres?: string[];
  bio?: string;
}

export interface Track {
  id: string;
  providerId: string;
  title: string;
  artists: ArtistRef[];
  album?: AlbumRef;
  durationMs: number;
  trackNumber?: number;
  discNumber?: number;
  artworkUrl?: string;
  explicit?: boolean;
  replayGainDb?: number;
  bpm?: number;
  isrc?: string;
  availableQualities?: AudioQuality[];
  localPath?: string;
}

export interface Album extends AlbumRef {
  artists: ArtistRef[];
  releaseYear?: number;
  trackCount?: number;
  tracks?: Track[];
}

export interface Playlist {
  id: string;
  providerId: string;
  title: string;
  description?: string;
  artworkUrl?: string;
  tracks: Track[];
  isCollaborative?: boolean;
  createdAtMs: number;
  updatedAtMs: number;
}

export interface LyricsWord {
  text: string;
  startMs: number;
  endMs: number;
}

export interface LyricsLine {
  startMs: number;
  endMs: number;
  text: string;
  words?: LyricsWord[];
  translation?: string;
  romanization?: string;
}

export interface LyricsData {
  trackId: string;
  provider: string;
  synced: boolean;
  lines: LyricsLine[];
}

export interface EQBand {
  frequencyHz: number;
  gainDb: number;
  q: number;
}

export interface EqualizerPreset {
  id: string;
  name: string;
  source: 'builtin' | 'autoeq' | 'custom';
  preampDb: number;
  bands: EQBand[];
}

export type PlayState = 'idle' | 'loading' | 'playing' | 'paused' | 'ended' | 'error';
export type RepeatMode = 'off' | 'all' | 'one';
