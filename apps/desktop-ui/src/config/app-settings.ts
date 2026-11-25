export interface DynamicIslandSettings {
  enabled: boolean;
  compactStyle: 'compact' | 'lyric' | 'hidden';
  position: 'attached' | 'floating';
  size: 'small' | 'medium' | 'large';
  artworkShape: 'square' | 'circle' | 'disc';
  accentColor: 'artwork' | 'theme' | 'pink' | 'blue';
  openTrigger: 'hover' | 'click';
  hideWhenPaused: boolean;
  showArtwork: boolean;
  showArtist: boolean;
  showLyricLine: boolean;
  showLikeButton: boolean;
  showProgressBar: boolean;
  progressStyle: 'thin' | 'thick' | 'dot';
  showEqualizer: boolean;
  eqStyle: 'bars' | 'wave' | 'static';
}

export interface AppSettings {
  // Tampilan
  themeMode: 'dark' | 'light' | 'system';
  uiScale: 80 | 90 | 100 | 110;
  listDensity: 'comfortable' | 'compact';
  cornerRadius: 'sharp' | 'medium' | 'round';
  showGreeting: boolean;
  showRecentOnHome: boolean;
  // Pemutar
  autoPlaySimilar: boolean;
  wavyProgressBar: boolean;
  // Lirik
  lyricFontSize: 'small' | 'medium' | 'large';
  wordByWordSweep: boolean;
  blurInactiveLines: boolean;
  showLyricSource: boolean;
  // Konten
  hideExplicit: boolean;
  // Privasi
  savePlayHistory: boolean;
  saveSearchHistory: boolean;
  // Dynamic Island
  dynamicIsland: DynamicIslandSettings;
}

export const DEFAULT_DYNAMIC_ISLAND_SETTINGS: DynamicIslandSettings = {
  enabled: true,
  compactStyle: 'compact',
  position: 'floating',
  size: 'medium',
  artworkShape: 'square',
  accentColor: 'theme',
  openTrigger: 'hover',
  hideWhenPaused: false,
  showArtwork: true,
  showArtist: true,
  showLyricLine: true,
  showLikeButton: true,
  showProgressBar: true,
  progressStyle: 'thin',
  showEqualizer: true,
  eqStyle: 'bars',
};

export const DEFAULT_APP_SETTINGS: AppSettings = {
  themeMode: 'dark',
  uiScale: 100,
  listDensity: 'comfortable',
  cornerRadius: 'medium',
  showGreeting: true,
  showRecentOnHome: true,
  autoPlaySimilar: true,
  wavyProgressBar: true,
  lyricFontSize: 'medium',
  wordByWordSweep: true,
  blurInactiveLines: true,
  showLyricSource: true,
  hideExplicit: false,
  savePlayHistory: true,
  saveSearchHistory: true,
  dynamicIsland: DEFAULT_DYNAMIC_ISLAND_SETTINGS,
};

const APP_SETTINGS_KEY = 'swara:appSettings';

export function loadAppSettings(): AppSettings {
  try {
    const raw = localStorage.getItem(APP_SETTINGS_KEY);
    if (!raw) return DEFAULT_APP_SETTINGS;
    const parsed = JSON.parse(raw) as Partial<AppSettings>;
    return {
      ...DEFAULT_APP_SETTINGS,
      ...parsed,
      dynamicIsland: {
        ...DEFAULT_DYNAMIC_ISLAND_SETTINGS,
        ...(parsed.dynamicIsland ?? {}),
      },
    };
  } catch {
    return DEFAULT_APP_SETTINGS;
  }
}

export function saveAppSettings(settings: AppSettings): void {
  try {
    localStorage.setItem(APP_SETTINGS_KEY, JSON.stringify(settings));
  } catch {
    // Ignore storage quota errors
  }
}

/**
 * Swara targets Desktop (Tauri) and Mobile (Android/iOS) apps, so native
 * features such as Offline Downloads are always enabled.
 */
export function isNativeApp(): boolean {
  return true;
}
