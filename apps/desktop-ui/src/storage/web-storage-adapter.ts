import type {
  EqualizerPreset,
  OfflineCacheEntry,
  PlayHistoryEntry,
  Playlist,
  StorageAdapter,
  Track,
} from '@swara/core';
import { STORES, idbRequest, idbTxDone, openSwaraDB } from './idb-helper.js';
import { OfflineAudioCache } from './offline-cache.js';

/**
 * Web/PWA implementation of `@swara/core`'s `StorageAdapter`,
 * backed by IndexedDB + OPFS via `OfflineAudioCache`.
 */
export class WebStorageAdapter implements StorageAdapter {
  private readonly cache: OfflineAudioCache;

  constructor(cache = new OfflineAudioCache()) {
    this.cache = cache;
  }

  async getSetting<T>(key: string): Promise<T | null> {
    const db = await openSwaraDB();
    const tx = db.transaction(STORES.settings, 'readonly');
    const val = await idbRequest(tx.objectStore(STORES.settings).get(key));
    return (val as T | undefined) ?? null;
  }

  async setSetting<T>(key: string, value: T): Promise<void> {
    const db = await openSwaraDB();
    const tx = db.transaction(STORES.settings, 'readwrite');
    tx.objectStore(STORES.settings).put(value, key);
    await idbTxDone(tx);
  }

  async getPlaylists(): Promise<Playlist[]> {
    const db = await openSwaraDB();
    const tx = db.transaction(STORES.playlists, 'readonly');
    return (await idbRequest(tx.objectStore(STORES.playlists).getAll())) as Playlist[];
  }

  async savePlaylist(playlist: Playlist): Promise<void> {
    const db = await openSwaraDB();
    const tx = db.transaction(STORES.playlists, 'readwrite');
    tx.objectStore(STORES.playlists).put(playlist);
    await idbTxDone(tx);
  }

  async deletePlaylist(playlistId: string): Promise<void> {
    const db = await openSwaraDB();
    const tx = db.transaction(STORES.playlists, 'readwrite');
    tx.objectStore(STORES.playlists).delete(playlistId);
    await idbTxDone(tx);
  }

  async appendHistory(entry: PlayHistoryEntry): Promise<void> {
    const db = await openSwaraDB();
    const tx = db.transaction(STORES.history, 'readwrite');
    tx.objectStore(STORES.history).add(entry);
    await idbTxDone(tx);
  }

  async getHistory(limit = 50): Promise<PlayHistoryEntry[]> {
    const db = await openSwaraDB();
    const tx = db.transaction(STORES.history, 'readonly');
    const all = (await idbRequest(tx.objectStore(STORES.history).getAll())) as PlayHistoryEntry[];
    return all.slice(-limit).reverse();
  }

  async getEQPresets(): Promise<EqualizerPreset[]> {
    const db = await openSwaraDB();
    const tx = db.transaction(STORES.eqPresets, 'readonly');
    return (await idbRequest(tx.objectStore(STORES.eqPresets).getAll())) as EqualizerPreset[];
  }

  async saveEQPreset(preset: EqualizerPreset): Promise<void> {
    const db = await openSwaraDB();
    const tx = db.transaction(STORES.eqPresets, 'readwrite');
    tx.objectStore(STORES.eqPresets).put(preset);
    await idbTxDone(tx);
  }

  async getCachedTracks(): Promise<OfflineCacheEntry[]> {
    return this.cache.listEntries();
  }

  async putCachedTrack(entry: OfflineCacheEntry): Promise<void> {
    const db = await openSwaraDB();
    const tx = db.transaction(STORES.cacheMeta, 'readwrite');
    tx.objectStore(STORES.cacheMeta).put({
      ...entry,
      lastAccessedAtMs: Date.now(),
      mimeType: 'audio/mpeg',
    });
    await idbTxDone(tx);
  }

  async removeCachedTrack(trackId: string): Promise<void> {
    await this.cache.remove(trackId);
  }

  async saveFavoriteTrack(track: Track): Promise<void> {
    const db = await openSwaraDB();
    const tx = db.transaction(STORES.favorites, 'readwrite');
    tx.objectStore(STORES.favorites).put(track);
    await idbTxDone(tx);
  }

  async getFavoriteTracks(): Promise<Track[]> {
    const db = await openSwaraDB();
    const tx = db.transaction(STORES.favorites, 'readonly');
    return (await idbRequest(tx.objectStore(STORES.favorites).getAll())) as Track[];
  }
}
