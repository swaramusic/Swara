const DB_NAME = 'swara-web-db';
const DB_VERSION = 1;

export const STORES = {
  settings: 'settings',
  playlists: 'playlists',
  history: 'history',
  eqPresets: 'eq_presets',
  favorites: 'favorites',
  cacheMeta: 'cache_meta',
  cacheBlobs: 'cache_blobs',
} as const;

export function openSwaraDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORES.settings)) db.createObjectStore(STORES.settings);
      if (!db.objectStoreNames.contains(STORES.playlists)) db.createObjectStore(STORES.playlists, { keyPath: 'id' });
      if (!db.objectStoreNames.contains(STORES.history)) db.createObjectStore(STORES.history, { autoIncrement: true });
      if (!db.objectStoreNames.contains(STORES.eqPresets)) db.createObjectStore(STORES.eqPresets, { keyPath: 'id' });
      if (!db.objectStoreNames.contains(STORES.favorites)) db.createObjectStore(STORES.favorites, { keyPath: 'id' });
      if (!db.objectStoreNames.contains(STORES.cacheMeta)) db.createObjectStore(STORES.cacheMeta, { keyPath: 'trackId' });
      if (!db.objectStoreNames.contains(STORES.cacheBlobs)) db.createObjectStore(STORES.cacheBlobs);
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error('Failed to open IndexedDB'));
  });
}

export function idbRequest<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error('IndexedDB request failed'));
  });
}

export function idbTxDone(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error ?? new Error('IndexedDB transaction failed'));
    tx.onabort = () => reject(tx.error ?? new Error('IndexedDB transaction aborted'));
  });
}
