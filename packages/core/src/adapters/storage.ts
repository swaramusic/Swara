import type { EqualizerPreset, Playlist, Track } from '../models/types.js';

export interface PlayHistoryEntry {
  trackId: string;
  providerId: string;
  playedAtMs: number;
  durationListenedMs: number;
}

export interface OfflineCacheEntry {
  trackId: string;
  providerId: string;
  localUri: string;
  sizeBytes: number;
  cachedAtMs: number;
}

/**
 * Persistence contract implemented by SQLite (Desktop/Mobile) and IndexedDB/OPFS (Web).
 * Keeps zero-login local data, playlists, history, and offline cache metadata uniform.
 */
export interface StorageAdapter {
  getSetting<T>(key: string): Promise<T | null>;
  setSetting<T>(key: string, value: T): Promise<void>;

  getPlaylists(): Promise<Playlist[]>;
  savePlaylist(playlist: Playlist): Promise<void>;
  deletePlaylist(playlistId: string): Promise<void>;

  appendHistory(entry: PlayHistoryEntry): Promise<void>;
  getHistory(limit?: number): Promise<PlayHistoryEntry[]>;

  getEQPresets(): Promise<EqualizerPreset[]>;
  saveEQPreset(preset: EqualizerPreset): Promise<void>;

  getCachedTracks(): Promise<OfflineCacheEntry[]>;
  putCachedTrack(entry: OfflineCacheEntry): Promise<void>;
  removeCachedTrack(trackId: string): Promise<void>;

  saveFavoriteTrack(track: Track): Promise<void>;
  getFavoriteTracks(): Promise<Track[]>;
}
