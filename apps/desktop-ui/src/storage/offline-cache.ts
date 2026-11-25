import type { OfflineCacheEntry } from '@swara/core';
import { STORES, idbRequest, idbTxDone, openSwaraDB } from './idb-helper.js';

export interface CacheRecord extends OfflineCacheEntry {
  lastAccessedAtMs: number;
  mimeType: string;
}

const DEFAULT_MAX_BYTES = 2 * 1024 * 1024 * 1024; // 2 GB
const OPFS_DIR = 'swara-audio-cache';

/**
 * Pure LRU planner: given current entries and an incoming byte size, returns
 * the ordered list of `trackId`s to evict so `total + incoming <= maxBytes`.
 */
export function planLRUEviction(
  entries: readonly CacheRecord[],
  incomingBytes: number,
  maxBytes: number
): string[] {
  if (incomingBytes > maxBytes) {
    throw new Error(`File size (${incomingBytes}B) exceeds cache limit (${maxBytes}B).`);
  }

  let total = entries.reduce((sum, e) => sum + e.sizeBytes, 0);
  if (total + incomingBytes <= maxBytes) return [];

  const sorted = [...entries].sort((a, b) => a.lastAccessedAtMs - b.lastAccessedAtMs);
  const toEvict: string[] = [];

  for (const entry of sorted) {
    if (total + incomingBytes <= maxBytes) break;
    toEvict.push(entry.trackId);
    total -= entry.sizeBytes;
  }

  return toEvict;
}

/**
 * Offline audio cache backed by OPFS when available, falling back to IndexedDB blobs.
 * Enforces a configurable byte quota with Least Recently Used (LRU) eviction.
 */
export class OfflineAudioCache {
  private maxBytes: number;

  constructor(maxBytes = DEFAULT_MAX_BYTES) {
    this.maxBytes = maxBytes;
  }

  setMaxBytes(maxBytes: number): void {
    this.maxBytes = Math.max(0, maxBytes);
  }

  private async getOpfsDir(): Promise<FileSystemDirectoryHandle | null> {
    if (typeof navigator === 'undefined' || !navigator.storage?.getDirectory) {
      return null;
    }
    try {
      const root = await navigator.storage.getDirectory();
      return await root.getDirectoryHandle(OPFS_DIR, { create: true });
    } catch {
      return null;
    }
  }

  private sanitizeFilename(trackId: string): string {
    return trackId.replace(/[^a-zA-Z0-9._-]/g, '_');
  }

  async put(trackId: string, providerId: string, data: Blob): Promise<CacheRecord> {
    const existing = await this.listEntries();
    const filtered = existing.filter((e) => e.trackId !== trackId);
    const evictIds = planLRUEviction(filtered, data.size, this.maxBytes);

    for (const id of evictIds) {
      await this.remove(id);
    }

    const opfsDir = await this.getOpfsDir();
    const fileKey = this.sanitizeFilename(trackId);

    if (opfsDir) {
      const handle = await opfsDir.getFileHandle(fileKey, { create: true });
      const writable = await handle.createWritable();
      await writable.write(data);
      await writable.close();
    } else {
      const db = await openSwaraDB();
      const tx = db.transaction(STORES.cacheBlobs, 'readwrite');
      tx.objectStore(STORES.cacheBlobs).put(data, trackId);
      await idbTxDone(tx);
    }

    const now = Date.now();
    const record: CacheRecord = {
      trackId,
      providerId,
      localUri: opfsDir ? `opfs://${OPFS_DIR}/${fileKey}` : `idb://${trackId}`,
      sizeBytes: data.size,
      cachedAtMs: now,
      lastAccessedAtMs: now,
      mimeType: data.type || 'audio/mpeg',
    };

    const db = await openSwaraDB();
    const tx = db.transaction(STORES.cacheMeta, 'readwrite');
    tx.objectStore(STORES.cacheMeta).put(record);
    await idbTxDone(tx);

    return record;
  }

  async getBlobUrl(trackId: string): Promise<string | null> {
    const db = await openSwaraDB();
    const tx = db.transaction(STORES.cacheMeta, 'readwrite');
    const store = tx.objectStore(STORES.cacheMeta);
    const record = (await idbRequest(store.get(trackId))) as CacheRecord | undefined;
    if (!record) return null;

    record.lastAccessedAtMs = Date.now();
    store.put(record);
    await idbTxDone(tx);

    const opfsDir = await this.getOpfsDir();
    if (opfsDir && record.localUri.startsWith('opfs://')) {
      try {
        const handle = await opfsDir.getFileHandle(this.sanitizeFilename(trackId));
        const file = await handle.getFile();
        return URL.createObjectURL(file);
      } catch {
        return null;
      }
    }

    const blobTx = db.transaction(STORES.cacheBlobs, 'readonly');
    const blob = (await idbRequest(blobTx.objectStore(STORES.cacheBlobs).get(trackId))) as Blob | undefined;
    return blob ? URL.createObjectURL(blob) : null;
  }

  async remove(trackId: string): Promise<void> {
    const opfsDir = await this.getOpfsDir();
    if (opfsDir) {
      try {
        await opfsDir.removeEntry(this.sanitizeFilename(trackId));
      } catch {
        // Ignore if file already missing.
      }
    }

    const db = await openSwaraDB();
    const tx = db.transaction([STORES.cacheMeta, STORES.cacheBlobs], 'readwrite');
    tx.objectStore(STORES.cacheMeta).delete(trackId);
    tx.objectStore(STORES.cacheBlobs).delete(trackId);
    await idbTxDone(tx);
  }

  async listEntries(): Promise<CacheRecord[]> {
    const db = await openSwaraDB();
    const tx = db.transaction(STORES.cacheMeta, 'readonly');
    const all = (await idbRequest(tx.objectStore(STORES.cacheMeta).getAll())) as CacheRecord[];
    return all;
  }
}
