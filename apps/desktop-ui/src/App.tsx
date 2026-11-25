import {
  createPlayerStore,
  extractYouTubePlaylistId,
  LocalFileProvider,
  YouTubeProvider,
  type EQBand,
  type HomeShelf,
  type LyricsData,
  type PlayHistoryEntry,
  type Playlist,
  type Track,
} from '@swara/core';
import {
  BarChart3,
  ChevronLeft,
  ChevronRight,
  Clock,
  Download,
  Flame,
  FolderPlus,
  Heart,
  Link2,
  ListPlus,
  Minus,
  Play,
  Plus,
  Search as SearchIcon,
  Settings,
  Square,
  Trash2,
  Upload,
  X,
} from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { useStore } from 'zustand';
import { BUILTIN_EQ_PRESETS, FLAT_PRESET } from './audio/eq-presets.js';
import { HybridAudioEngine } from './audio/hybrid-audio-engine.js';
import { WebAudioEngine } from './audio/web-audio-engine.js';
import { CommandPalette } from './components/CommandPalette.js';
import { DynamicIsland } from './components/DynamicIsland.js';
import { EqualizerPanel } from './components/EqualizerPanel.js';
import { FloatingMiniplayer } from './components/FloatingMiniplayer.js';
import { FullscreenNowPlaying } from './components/FullscreenNowPlaying.js';
import { LocalWrapped } from './components/LocalWrapped.js';
import { LyricsMainView } from './components/LyricsMainView.js';
import { MobileNavAndMiniplayer } from './components/MobileNavAndMiniplayer.js';
import { PlayerBar } from './components/PlayerBar.js';
import { RightSidePanel } from './components/RightSidePanel.js';
import { SettingsView } from './components/SettingsView.js';
import { Sidebar } from './components/Sidebar.js';
import { TrackArtwork } from './components/TrackArtwork.js';
import { formatTimeMs, type NavSection, type RightPanelTab } from './components/types.js';
import { INITIAL_YOUTUBE_TRACKS } from './data/youtube-catalog.js';
import { useKeyboardShortcuts } from './hooks/useKeyboardShortcuts.js';
import {
  isNativeApp,
  loadAppSettings,
  saveAppSettings,
  type AppSettings,
} from './config/app-settings.js';
import { isTauriDesktop, tauriFetch, tauriInvoke } from './config/tauri-bridge.js';
import { OfflineAudioCache } from './storage/offline-cache.js';
import {
  getTranslations,
  type AppCountry,
  type AppLanguage,
} from './i18n/translations.js';
import { importPlaylist, type PlaylistFormat } from './storage/playlist-io.js';
import { importSpotifyPlaylist } from './storage/spotify-importer.js';
import { ThemeProvider } from './theme/ThemeProvider.js';

const LIKED_STORAGE_KEY = 'swara_liked_tracks';
const PLAYLISTS_STORAGE_KEY = 'swara_playlists';
const RECENT_STORAGE_KEY = 'swara_recent_tracks';
const LANGUAGE_STORAGE_KEY = 'swara_language';
const COUNTRY_STORAGE_KEY = 'swara_country';
const DEFAULT_MOODS = ['Pop Indonesia', 'Lofi Beats', 'Galau', 'Indie', 'Rock', 'Jazz', 'EDM', 'K-Pop'];

function loadJsonStorage<T>(key: string, fallback: T): T {
  if (typeof localStorage === 'undefined') return fallback;
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

function getGreeting(t: ReturnType<typeof getTranslations>): string {
  const hour = new Date().getHours();
  if (hour < 11) return t.greetingMorning;
  if (hour < 15) return t.greetingAfternoon;
  if (hour < 18) return t.greetingEvening;
  return t.greetingNight;
}

function HorizontalShelf({
  title,
  tracks,
  onPlayIndex,
}: {
  title: string;
  tracks: Track[];
  onPlayIndex: (idx: number) => void;
}) {
  const scrollRef = useRef<HTMLDivElement>(null);
  if (tracks.length === 0) return null;

  const scrollBy = (dx: number) => {
    scrollRef.current?.scrollBy({ left: dx, behavior: 'smooth' });
  };

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold tracking-tight">{title}</h2>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => scrollBy(-360)}
            className="p-1.5 rounded-full bg-white/5 hover:bg-white/10"
            aria-label={`Geser ${title} ke kiri`}
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={() => scrollBy(360)}
            className="p-1.5 rounded-full bg-white/5 hover:bg-white/10"
            aria-label={`Geser ${title} ke kanan`}
          >
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>

      <div ref={scrollRef} className="flex gap-3.5 overflow-x-auto no-scrollbar pb-1">
        {tracks.map((track, idx) => (
          <button
            key={`${track.providerId}-${track.id}-${idx}`}
            type="button"
            onClick={() => onPlayIndex(idx)}
            className="group w-40 shrink-0 text-left space-y-2 p-2.5 rounded-2xl bg-white/[0.04] hover:bg-white/10 transition-colors"
          >
            <div className="relative">
              <TrackArtwork track={track} className="w-full aspect-square rounded-xl" />
              <span
                className="absolute bottom-2 right-2 w-8 h-8 rounded-full flex items-center justify-center opacity-90 md:opacity-0 md:group-hover:opacity-100 md:translate-y-1 md:group-hover:translate-y-0 transition-all shadow-lg"
                style={{ backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }}
              >
                <Play className="w-3.5 h-3.5 ml-0.5" />
              </span>
            </div>
            <div className="min-w-0">
              <p className="text-sm font-semibold truncate">{track.title}</p>
              <p className="text-xs opacity-60 truncate">
                {track.artists.map((a) => a.name).join(', ')}
              </p>
            </div>
          </button>
        ))}
      </div>
    </section>
  );
}

export function App() {
  const [section, setSection] = useState<NavSection>('home');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [eqModalOpen, setEqModalOpen] = useState(false);
  const nativeMode = useMemo(() => isNativeApp(), []);
  const tauriDesktop = useMemo(() => isTauriDesktop(), []);
  const [appSettings, setAppSettings] = useState<AppSettings>(() => loadAppSettings());
  const offlineCacheRef = useRef(new OfflineAudioCache());
  const [offlineTracks, setOfflineTracks] = useState<Track[]>(() =>
    loadJsonStorage<Track[]>('swara:offlineTracks', [])
  );
  const [drawerTab, setDrawerTab] = useState<RightPanelTab>('queue');
  const [lyricsViewOpen, setLyricsViewOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [fullscreenOpen, setFullscreenOpen] = useState(false);
  const [miniplayerOpen, setMiniplayerOpen] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);

  const [language, setLanguage] = useState<AppLanguage>(() =>
    loadJsonStorage<AppLanguage>(LANGUAGE_STORAGE_KEY, 'id')
  );
  const [country, setCountry] = useState<AppCountry>(() =>
    loadJsonStorage<AppCountry>(COUNTRY_STORAGE_KEY, 'ID')
  );
  const t = useMemo(() => getTranslations(language), [language]);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<Track[] | null>(null);
  const [searching, setSearching] = useState(false);

  const [trendingTracks, setTrendingTracks] = useState<Track[]>(INITIAL_YOUTUBE_TRACKS);
  const [homeShelves, setHomeShelves] = useState<HomeShelf[]>([]);
  const [personalizedShelves, setPersonalizedShelves] = useState<HomeShelf[]>([]);
  const [newReleases, setNewReleases] = useState<Track[]>([]);
  const [moods, setMoods] = useState<string[]>(DEFAULT_MOODS);
  const [localTracks, setLocalTracks] = useState<Track[]>([]);
  const [likedTracks, setLikedTracks] = useState<Track[]>(() => loadJsonStorage(LIKED_STORAGE_KEY, []));
  const [recentTracks, setRecentTracks] = useState<Track[]>(() => loadJsonStorage(RECENT_STORAGE_KEY, []));
  const [playlists, setPlaylists] = useState<Playlist[]>(() => loadJsonStorage(PLAYLISTS_STORAGE_KEY, []));
  const [newPlaylistName, setNewPlaylistName] = useState('');
  const [spotifyUrl, setSpotifyUrl] = useState('');
  const [importingPlaylist, setImportingPlaylist] = useState<string | null>(null);
  const [history, setHistory] = useState<PlayHistoryEntry[]>([]);
  const [lyrics, setLyrics] = useState<LyricsData | null>(null);
  const [activeEqId, setActiveEqId] = useState(BUILTIN_EQ_PRESETS[0]!.id);
  const [eqBands, setEqBands] = useState<EQBand[]>(FLAT_PRESET.bands);
  const [sleepRemainingMin, setSleepRemainingMin] = useState<number | null>(null);
  const [playbackError, setPlaybackError] = useState<string | null>(null);
  const [playlistPickerTrack, setPlaylistPickerTrack] = useState<Track | null>(null);

  const lastVolumeRef = useRef(1);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const playlistFileInputRef = useRef<HTMLInputElement>(null);
  const objectUrlsRef = useRef<Map<string, string>>(new Map());

  const { playerStore, localProvider, ytProvider, audioEngine } = useMemo(() => {
    const local = new LocalFileProvider({
      tracks: [],
      albums: [],
      artists: [],
      resolveUri: async (uri) => uri,
    });
    const yt = new YouTubeProvider({
      baseUrl: import.meta.env.DEV ? '/api/ytm' : 'https://music.youtube.com/youtubei/v1',
      hl: language,
      gl: country,
      initialTracks: INITIAL_YOUTUBE_TRACKS,
      fetchFn: tauriDesktop ? tauriFetch : undefined,
    });
    const getProvider = (id: string) => {
      if (id === 'local') return local;
      if (id === 'youtube') return yt;
      return undefined;
    };
    const webEngine = new WebAudioEngine({ getProvider });
    const hybrid = new HybridAudioEngine(webEngine);
    const store = createPlayerStore({ audioEngine: hybrid, getProvider });
    return { playerStore: store, localProvider: local, ytProvider: yt, audioEngine: hybrid };
  }, []);

  const state = useStore(playerStore);

  useEffect(() => {
    localStorage.setItem(LIKED_STORAGE_KEY, JSON.stringify(likedTracks));
  }, [likedTracks]);

  useEffect(() => {
    localStorage.setItem(PLAYLISTS_STORAGE_KEY, JSON.stringify(playlists));
  }, [playlists]);

  useEffect(() => {
    localStorage.setItem(RECENT_STORAGE_KEY, JSON.stringify(recentTracks));
  }, [recentTracks]);

  useEffect(() => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, JSON.stringify(language));
  }, [language]);

  useEffect(() => {
    localStorage.setItem(COUNTRY_STORAGE_KEY, JSON.stringify(country));
  }, [country]);

  useEffect(() => {
    audioEngine.preloadYouTube();
  }, [audioEngine]);

  useEffect(() => {
    let cancelled = false;
    ytProvider.setRegion(language, country);
    void ytProvider
      .getHomeFeed()
      .then((feed) => {
        if (cancelled) return;
        if (feed.shelves.length > 0) {
          setHomeShelves(feed.shelves);
          const firstTrending = feed.shelves.find((s) => s.id === 'trending')?.tracks ?? feed.shelves[0]?.tracks;
          if (firstTrending && firstTrending.length > 0) setTrendingTracks(firstTrending);
          const rel = feed.shelves.find((s) => s.id === 'new-releases')?.tracks;
          if (rel && rel.length > 0) setNewReleases(rel);
        }
        if (feed.moods.length > 0) setMoods(feed.moods);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [ytProvider, language, country]);

  // Taste-Aware Personalization: adapt Home shelves based on what the user actually listens to.
  const tasteSeed = recentTracks[0] ?? likedTracks[0];
  useEffect(() => {
    if (!tasteSeed || tasteSeed.providerId !== 'youtube') {
      setPersonalizedShelves([]);
      return;
    }
    let cancelled = false;
    const artistName = tasteSeed.artists[0]?.name ?? '';
    void Promise.allSettled([
      ytProvider.getRecommendations(tasteSeed.id, 12),
      artistName ? ytProvider.search(artistName, { limit: 12 }) : Promise.resolve({ tracks: [], albums: [], artists: [] }),
    ]).then(([simRes, artRes]) => {
      if (cancelled) return;
      const next: HomeShelf[] = [];
      if (simRes.status === 'fulfilled' && simRes.value.length > 0) {
        next.push({
          id: 'taste-similar',
          title:
            language === 'en'
              ? `Similar to ${tasteSeed.title}`
              : `Mirip dengan ${tasteSeed.title}`,
          layout: 'carousel',
          tracks: simRes.value,
        });
      }
      if (artRes.status === 'fulfilled' && artRes.value.tracks.length > 0 && artistName) {
        next.push({
          id: 'taste-artist',
          title: language === 'en' ? `More from ${artistName}` : `Lebih banyak dari ${artistName}`,
          layout: 'carousel',
          tracks: artRes.value.tracks,
        });
      }
      setPersonalizedShelves(next);
    });
    return () => {
      cancelled = true;
    };
  }, [tasteSeed?.id, ytProvider, language]);

  // Live Instant Search As-You-Type (350ms debounce, no "Cari" button required)
  useEffect(() => {
    const q = searchQuery.trim();
    if (!q) {
      setSearchResults(null);
      setSearching(false);
      return;
    }

    setSearching(true);
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      try {
        const [ytRes, localRes] = await Promise.all([
          ytProvider.search(q, { limit: 24 }),
          localProvider.search(q, { limit: 10 }),
        ]);
        if (!cancelled) {
          setSearchResults([...localRes.tracks, ...ytRes.tracks]);
        }
      } catch (err) {
        if (!cancelled) {
          setPlaybackError(err instanceof Error ? err.message : 'Gagal mencari lagu');
        }
      } finally {
        if (!cancelled) setSearching(false);
      }
    }, 350);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [searchQuery, ytProvider, localProvider]);

  useEffect(() => {
    const unsub = audioEngine.subscribe({
      onError: (err) => setPlaybackError(err.message),
      onStateChange: (s) => {
        if (s === 'playing') setPlaybackError(null);
      },
    });
    return () => {
      unsub();
      for (const url of objectUrlsRef.current.values()) URL.revokeObjectURL(url);
    };
  }, [audioEngine]);

  // Fetch synced lyrics & update Recent + Wrapped history on track change
  useEffect(() => {
    const track = state.currentTrack;
    if (!track) {
      setLyrics(null);
      return;
    }

    setRecentTracks((prev) =>
      [track, ...prev.filter((t) => !(t.id === track.id && t.providerId === track.providerId))].slice(0, 20)
    );
    setHistory((prev) => [
      {
        trackId: track.id,
        providerId: track.providerId,
        playedAtMs: Date.now(),
        durationListenedMs: track.durationMs > 0 ? track.durationMs : 180_000,
      },
      ...prev,
    ]);

    let cancelled = false;
    void ytProvider.getLyrics(track).then((res) => {
      if (!cancelled) setLyrics(res);
    });
    return () => {
      cancelled = true;
    };
  }, [state.currentTrack, ytProvider]);

  useEffect(() => {
    if (sleepRemainingMin === null) return;
    if (sleepRemainingMin <= 0) {
      void state.pause();
      setSleepRemainingMin(null);
      return;
    }
    const id = window.setTimeout(() => {
      setSleepRemainingMin((m) => (m !== null ? m - 1 : null));
    }, 60_000);
    return () => window.clearTimeout(id);
  }, [sleepRemainingMin, state]);

  const cycleSpeed = () => {
    const speeds = [1, 1.25, 1.5, 0.75];
    const next = speeds[(speeds.indexOf(state.playbackSpeed) + 1) % speeds.length] ?? 1;
    void state.setPlaybackSpeed(next);
  };

  const cycleSleepTimer = () => {
    const options: Array<number | null> = [null, 15, 30, 60];
    const idx = options.indexOf(sleepRemainingMin);
    setSleepRemainingMin(options[(idx + 1) % options.length] ?? null);
  };

  const toggleDrawerTab = (tab: RightPanelTab) => {
    const isQueueGroup = (t: RightPanelTab) => t === 'queue' || t === 'recent';
    if (drawerOpen && (drawerTab === tab || (isQueueGroup(drawerTab) && isQueueGroup(tab)))) {
      setDrawerOpen(false);
    } else {
      setDrawerTab(tab);
      setDrawerOpen(true);
    }
  };

  const isTrackLiked = (track: Track | null | undefined) =>
    Boolean(track && likedTracks.some((t) => t.id === track.id && t.providerId === track.providerId));

  const toggleLikeTrack = (track: Track) => {
    setLikedTracks((prev) => {
      const exists = prev.some((t) => t.id === track.id && t.providerId === track.providerId);
      return exists
        ? prev.filter((t) => !(t.id === track.id && t.providerId === track.providerId))
        : [track, ...prev];
    });
  };

  const createPlaylist = (e: FormEvent) => {
    e.preventDefault();
    const title = newPlaylistName.trim();
    if (!title) return;
    const now = Date.now();
    setPlaylists((prev) => [
      { id: `pl-${now}`, providerId: 'local', title, tracks: [], createdAtMs: now, updatedAtMs: now },
      ...prev,
    ]);
    setNewPlaylistName('');
  };

  const handleImportSpotifyOrYtm = async (e: FormEvent) => {
    e.preventDefault();
    const raw = spotifyUrl.trim();
    if (!raw) return;
    setPlaybackError(null);

    const ytListId = extractYouTubePlaylistId(raw);
    if (ytListId) {
      setImportingPlaylist('Mengimpor playlist YouTube Music...');
      try {
        const pl = await ytProvider.importPlaylist(ytListId);
        setPlaylists((prev) => [pl, ...prev]);
        setSpotifyUrl('');
      } catch (err) {
        setPlaybackError(err instanceof Error ? err.message : 'Gagal mengimpor playlist YouTube Music');
      } finally {
        setImportingPlaylist(null);
      }
      return;
    }

    setImportingPlaylist('Mengambil playlist Spotify...');
    try {
      const pl = await importSpotifyPlaylist(raw, ytProvider, (done, total) => {
        setImportingPlaylist(`Mengonversi ${done}/${total} lagu ke Swara...`);
      });
      setPlaylists((prev) => [pl, ...prev]);
      setSpotifyUrl('');
    } catch (err) {
      setPlaybackError(err instanceof Error ? err.message : 'Gagal mengimpor playlist');
    } finally {
      setImportingPlaylist(null);
    }
  };

  const handleImportPlaylistFile = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const ext = file.name.split('.').pop()?.toLowerCase();
    const format: PlaylistFormat = ext === 'csv' ? 'csv' : ext === 'json' ? 'json' : 'm3u';
    try {
      const text = await file.text();
      const imported = importPlaylist(text, format, file.name.replace(/\.[^/.]+$/, ''));
      setPlaylists((prev) => [imported, ...prev]);
    } catch (err) {
      setPlaybackError(err instanceof Error ? err.message : 'Gagal mengimpor file playlist');
    } finally {
      e.target.value = '';
    }
  };

  const addTrackToPlaylist = (playlistId: string, track: Track) => {
    const now = Date.now();
    setPlaylists((prev) =>
      prev.map((pl) =>
        pl.id === playlistId && !pl.tracks.some((t) => t.id === track.id)
          ? { ...pl, tracks: [...pl.tracks, track], updatedAtMs: now }
          : pl
      )
    );
    setPlaylistPickerTrack(null);
  };

  const openPlaylistPicker = (track: Track) => {
    if (playlists.length === 0) {
      const now = Date.now();
      setPlaylists([
        {
          id: `pl-${now}`,
          providerId: 'local',
          title: 'Playlist Saya',
          tracks: [track],
          createdAtMs: now,
          updatedAtMs: now,
        },
      ]);
      return;
    }
    setPlaylistPickerTrack(track);
  };

  const handleAddLocalFiles = (e: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    if (files.length === 0) return;

    const added: Track[] = files.map((file, idx) => {
      const objectUrl = URL.createObjectURL(file);
      const id = `local-${Date.now()}-${idx}`;
      objectUrlsRef.current.set(id, objectUrl);
      const cleanName = file.name.replace(/\.[^/.]+$/, '');
      return {
        id,
        providerId: 'local',
        title: cleanName,
        artists: [{ id: 'local-artist', providerId: 'local', name: 'File Lokal' }],
        durationMs: 0,
        localPath: objectUrl,
      };
    });

    const nextLocal = [...added, ...localTracks];
    setLocalTracks(nextLocal);
    localProvider.updateIndex({
      tracks: nextLocal,
      albums: [],
      artists: [],
      resolveUri: async (uri) => uri,
    });
    setPlaybackError(null);
    void playerStore.getState().setQueue(nextLocal, 0);
  };

  useEffect(() => {
    saveAppSettings(appSettings);
  }, [appSettings]);

  // Sync playback state & 10-band EQ to native Android Foreground Service / AudioFX Equalizer
  useEffect(() => {
    const w = window as unknown as {
      __swaraMediaAction?: (action: 'toggle' | 'next' | 'prev') => void;
    };
    w.__swaraMediaAction = (action: string) => {
      if (action === 'toggle') void state.togglePlayPause();
      else if (action === 'sync_play') void state.play();
      else if (action === 'sync_pause') void state.pause();
      else if (action === 'next') void state.next();
      else if (action === 'prev') void state.previous();
      else if (action === 'ended') void state.next();
      else if (action.startsWith('seek:')) {
        const ms = Number(action.slice(5));
        if (Number.isFinite(ms)) void state.seek(ms);
      }
    };
    return () => {
      delete w.__swaraMediaAction;
    };
  }, [state]);

  useEffect(() => {
    const bridge = (
      window as unknown as {
        SwaraNativeMedia?: {
          updateMediaState: (
            title: string,
            artist: string,
            artworkUrl: string,
            durationMs: number,
            positionMs: number,
            playing: boolean
          ) => void;
          stopMediaService: () => void;
          setEqBands: (csv: string) => boolean;
        };
      }
    ).SwaraNativeMedia;
    if (!bridge) return;
    if (state.currentTrack && (state.playState === 'playing' || state.playState === 'loading')) {
      bridge.updateMediaState(
        state.currentTrack.title,
        state.currentTrack.artists.map((a) => a.name).join(', '),
        state.currentTrack.artworkUrl ?? '',
        state.durationMs,
        state.positionMs,
        true
      );
    } else if (state.currentTrack && state.playState === 'paused') {
      bridge.updateMediaState(
        state.currentTrack.title,
        state.currentTrack.artists.map((a) => a.name).join(', '),
        state.currentTrack.artworkUrl ?? '',
        state.durationMs,
        state.positionMs,
        false
      );
    }
  }, [state.currentTrack, state.playState, state.durationMs]);

  useEffect(() => {
    const w = window as unknown as {
      __swaraMediaAction?: (action: 'toggle' | 'next' | 'prev') => void;
    };
    w.__swaraMediaAction = (action) => {
      if (action === 'toggle') void state.togglePlayPause();
      else if (action === 'next') void state.next();
      else if (action === 'prev') void state.previous();
    };
    return () => {
      delete w.__swaraMediaAction;
    };
  }, [state]);

  useEffect(() => {
    const bridge = (
      window as unknown as {
        SwaraNativeMedia?: { setEqBands: (csv: string) => boolean };
      }
    ).SwaraNativeMedia;
    bridge?.setEqBands(eqBands.map((b) => b.gainDb).join(','));
  }, [eqBands]);

  useEffect(() => {
    try {
      localStorage.setItem('swara:offlineTracks', JSON.stringify(offlineTracks));
    } catch {
      // Ignore quota errors
    }
  }, [offlineTracks]);

  const isTrackDownloaded = (track: Track | null) =>
    Boolean(track && offlineTracks.some((x) => x.id === track.id));

  const handleDownloadTrack = async (track: Track) => {
    setPlaybackError(null);
    if (isTrackDownloaded(track)) {
      await offlineCacheRef.current.remove(track.id);
      setOfflineTracks((prev) => prev.filter((x) => x.id !== track.id));
      return;
    }

    if (track.providerId === 'youtube' && !track.localPath) {
      setPlaybackError(
        'Download audio YouTube Music membutuhkan build APK Android/iOS native; saat ini simpan offline aktif untuk file audio lokal & stream langsung.'
      );
      return;
    }

    const sourceUrl = track.localPath;
    if (!sourceUrl) {
      setPlaybackError('Sumber audio tidak tersedia untuk disimpan offline.');
      return;
    }

    try {
      const res = await fetch(sourceUrl);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const blob = await res.blob();
      await offlineCacheRef.current.put(track.id, track.providerId, blob);
      setOfflineTracks((prev) => [track, ...prev.filter((x) => x.id !== track.id)]);
    } catch (err) {
      setPlaybackError(err instanceof Error ? err.message : 'Gagal mengunduh lagu untuk offline');
    }
  };

  const handlePlayTrackList = async (list: Track[], index: number) => {
    setPlaybackError(null);
    try {
      const resolved = await Promise.all(
        list.map(async (item) => {
          if (offlineTracks.some((x) => x.id === item.id)) {
            const blobUrl = await offlineCacheRef.current.getBlobUrl(item.id);
            if (blobUrl) {
              const offlineTrack: Track = { ...item, providerId: 'local', localPath: blobUrl };
              localProvider.updateIndex({
                tracks: [...localTracks, offlineTrack],
                albums: [],
                artists: [],
                resolveUri: async (uri) => uri,
              });
              return offlineTrack;
            }
          }
          return item;
        })
      );
      await state.setQueue(resolved, index);
    } catch (err) {
      setPlaybackError(err instanceof Error ? err.message : 'Gagal memutar lagu');
    }
  };

  const cycleRepeat = () => {
    const order = ['off', 'all', 'one'] as const;
    const next = order[(order.indexOf(state.repeatMode) + 1) % order.length]!;
    state.setRepeatMode(next);
  };

  useKeyboardShortcuts(
    useMemo(
      () => ({
        onTogglePlayPause: () => void state.togglePlayPause(),
        onSeekBy: (deltaMs) => void state.seek(Math.max(0, state.positionMs + deltaMs)),
        onToggleMute: () => {
          if (state.volume > 0) {
            lastVolumeRef.current = state.volume;
            void state.setVolume(0);
          } else {
            void state.setVolume(lastVolumeRef.current || 1);
          }
        },
        onToggleFullscreen: () => setFullscreenOpen((v) => !v),
        onToggleCommandPalette: () => setPaletteOpen((v) => !v),
      }),
      [state]
    )
  );

  const renderTrackGrid = (list: Track[], emptyText: string, singleWithAutoQueue = false) => (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-2.5">
      {list.map((track, idx) => {
        const isActive = state.currentTrack?.id === track.id;
        const liked = isTrackLiked(track);
        const onClickTrack = () => {
          if (singleWithAutoQueue) {
            setPlaybackError(null);
            void state.playTrack(track).catch((err) => {
              setPlaybackError(err instanceof Error ? err.message : 'Gagal memutar lagu');
            });
          } else {
            void handlePlayTrackList(list, idx);
          }
        };
        return (
          <div
            key={`${track.providerId}-${track.id}-${idx}`}
            onClick={onClickTrack}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onClickTrack();
              }
            }}
            className="group flex items-center gap-3 p-2.5 rounded-2xl bg-white/[0.04] hover:bg-white/10 transition-colors cursor-pointer"
            style={isActive ? { outline: '1px solid var(--primary)' } : {}}
          >
            <TrackArtwork track={track} className="w-11 h-11 rounded-xl" />
            <div className="min-w-0 flex-1">
              <p className="font-semibold text-sm truncate">{track.title}</p>
              <p className="text-xs opacity-60 truncate">
                {track.artists.map((a) => a.name).join(', ')}
                {track.durationMs > 0 ? ` • ${formatTimeMs(track.durationMs)}` : ''}
              </p>
            </div>
            <div className="flex items-center gap-0.5 opacity-85 md:opacity-0 md:group-hover:opacity-100 transition-opacity shrink-0">
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  toggleLikeTrack(track);
                }}
                className="p-2 rounded-full hover:bg-white/10"
                aria-label={liked ? 'Hapus suka' : 'Sukai lagu'}
              >
                <Heart
                  className="w-4 h-4"
                  style={liked ? { color: 'var(--primary)', fill: 'var(--primary)' } : {}}
                />
              </button>
              {nativeMode && (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    void handleDownloadTrack(track);
                  }}
                  className="p-2 rounded-full hover:bg-white/10"
                  aria-label={isTrackDownloaded(track) ? 'Hapus dari Unduhan' : 'Unduh Offline'}
                >
                  <Download
                    className="w-4 h-4"
                    style={isTrackDownloaded(track) ? { color: 'var(--primary)' } : {}}
                  />
                </button>
              )}
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  openPlaylistPicker(track);
                }}
                className="p-2 rounded-full hover:bg-white/10"
                aria-label="Tambah ke Playlist"
              >
                <ListPlus className="w-4 h-4" />
              </button>
            </div>
          </div>
        );
      })}
      {list.length === 0 && (
        <p className="text-sm opacity-60 col-span-full py-10 text-center">{emptyText}</p>
      )}
    </div>
  );

  const continueTrack = recentTracks[0] ?? trendingTracks[0];

  return (
    <ThemeProvider artworkUrl={state.currentTrack?.artworkUrl}>
      <div
        onContextMenu={(e) => {
          if (tauriDesktop) e.preventDefault();
        }}
        className={`h-[100dvh] w-screen flex flex-col gap-3 p-0 md:p-4 pt-[env(safe-area-inset-top)] pb-[env(safe-area-inset-bottom)] overflow-hidden ${
          tauriDesktop ? 'select-none md:pt-2' : ''
        }`}
      >
        {tauriDesktop && (
          <div
            onMouseDown={(e) => {
              if ((e.target as HTMLElement).closest('button')) return;
              void tauriInvoke('win_start_dragging');
            }}
            className="flex items-center justify-between h-8 px-2 shrink-0 cursor-default"
          >
            <span className="text-xs font-bold tracking-wide opacity-75">Swara Desktop</span>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => void tauriInvoke('win_minimize')}
                className="w-8 h-7 rounded-lg flex items-center justify-center hover:bg-white/10"
                aria-label="Minimize"
              >
                <Minus className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                onClick={() => void tauriInvoke('win_toggle_maximize')}
                className="w-8 h-7 rounded-lg flex items-center justify-center hover:bg-white/10"
                aria-label="Maximize"
              >
                <Square className="w-3 h-3" />
              </button>
              <button
                type="button"
                onClick={() => void tauriInvoke('win_close')}
                className="w-8 h-7 rounded-lg flex items-center justify-center hover:bg-red-500/80"
                aria-label="Close"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        )}
        <input
          ref={fileInputRef}
          type="file"
          accept="audio/*"
          multiple
          onChange={handleAddLocalFiles}
          className="hidden"
        />

        <div className="flex-1 flex gap-3 min-h-0">
          <Sidebar
            activeSection={section}
            onSelectSection={(s) => {
              setSection(s);
              setLyricsViewOpen(false);
              setSearchResults(null);
              setSearchQuery('');
            }}
            collapsed={sidebarCollapsed}
            onToggleCollapse={() => setSidebarCollapsed((v) => !v)}
            likedCount={likedTracks.length}
            nativeMode={nativeMode}
            labels={t.nav}
          />

          <main className="flex-1 md:rounded-3xl md:glass-panel px-3.5 py-3 md:px-8 md:py-6 overflow-y-auto no-scrollbar pb-40 md:pb-8 space-y-6">
            {lyricsViewOpen ? (
              <LyricsMainView
                t={t}
                track={state.currentTrack}
                lyrics={lyrics}
                positionMs={state.positionMs}
                isLiked={isTrackLiked(state.currentTrack)}
                sleepRemainingMin={sleepRemainingMin}
                onClose={() => setLyricsViewOpen(false)}
                onSeek={(ms) => void state.seek(ms)}
                onToggleLike={() => {
                  if (state.currentTrack) toggleLikeTrack(state.currentTrack);
                }}
                onAddToPlaylist={() => {
                  if (state.currentTrack) openPlaylistPicker(state.currentTrack);
                }}
                onCycleSleepTimer={cycleSleepTimer}
                onOpenFullscreen={() => setFullscreenOpen(true)}
              />
            ) : (
              <>
            {/* Top Live Search Bar (hidden on mobile except in Search tab) */}
            <div
              className={
                section === 'search'
                  ? 'flex items-center justify-between gap-4'
                  : 'hidden md:flex items-center justify-between gap-4'
              }
            >
              <div className="relative flex-1 max-w-md flex items-center gap-2.5 px-4 py-2 rounded-full bg-white/[0.06] border border-white/10 focus-within:border-[var(--primary)] transition-colors">
                <SearchIcon className="w-4 h-4 opacity-60 shrink-0" />
                <input
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder={t.searchPlaceholder}
                  className="flex-1 bg-transparent outline-none text-sm"
                />
                {searching && (
                  <span className="text-[11px] opacity-60 animate-pulse">{t.searching}</span>
                )}
                {searchQuery && (
                  <button
                    type="button"
                    onClick={() => {
                      setSearchQuery('');
                      setSearchResults(null);
                    }}
                    className="p-0.5 rounded-full hover:bg-white/10"
                    aria-label="Hapus pencarian"
                  >
                    <X className="w-3.5 h-3.5 opacity-70" />
                  </button>
                )}
              </div>

              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="hidden sm:inline-flex items-center gap-2 px-3.5 py-2 rounded-full text-xs font-semibold bg-white/[0.07] hover:bg-white/15 transition-colors"
              >
                <FolderPlus className="w-3.5 h-3.5" />
                {t.localFilesBtn}
              </button>
            </div>

            {playbackError && (
              <div className="px-4 py-2.5 rounded-2xl bg-red-500/15 border border-red-400/30 text-xs flex items-center justify-between gap-2">
                <span>{playbackError}</span>
                <button
                  type="button"
                  onClick={() => setPlaybackError(null)}
                  className="underline opacity-80 hover:opacity-100"
                >
                  Tutup
                </button>
              </div>
            )}

            {/* Mobile Library Sub-Nav Pills */}
            {searchResults === null &&
              ['library', 'playlists', 'liked', 'recent', 'local', 'offline'].includes(section) && (
                <div className="md:hidden flex items-center gap-2 overflow-x-auto no-scrollbar pb-1">
                  {(
                    [
                      ['playlists', t.nav.playlists],
                      ['liked', t.nav.liked],
                      ['recent', t.nav.recent],
                      ['local', t.nav.local],
                      ...(nativeMode ? ([['offline', t.nav.offline]] as const) : []),
                    ] as const
                  ).map(([id, label]) => {
                    const active = section === id || (section === 'library' && id === 'playlists');
                    return (
                      <button
                        key={id}
                        type="button"
                        onClick={(e) => {
                          setSection(id);
                          e.currentTarget.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
                        }}
                        className="px-3.5 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap transition-colors"
                        style={
                          active
                            ? { backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }
                            : { backgroundColor: 'rgba(255,255,255,0.08)' }
                        }
                      >
                        {label}
                      </button>
                    );
                  })}
                </div>
              )}

            {/* Instant Search Results take priority when user is typing */}
            {searchResults !== null ? (
              <div className="space-y-4">
                <h1 className="text-xl font-bold">{t.searchResultsFor} &ldquo;{searchQuery}&rdquo;</h1>
                {renderTrackGrid(searchResults, t.noSearchResults, true)}
              </div>
            ) : section === 'you' ? (
              <div className="space-y-5">
                <h1 className="text-2xl md:text-3xl font-bold">{t.nav.you}</h1>

                <div className="rounded-3xl p-4 bg-white/[0.06] border border-white/10 flex items-center gap-3.5">
                  <div
                    className="w-11 h-11 rounded-2xl flex items-center justify-center shrink-0"
                    style={{ backgroundColor: 'rgba(255,255,255,0.08)' }}
                  >
                    <Flame className="w-6 h-6" style={{ color: 'var(--primary)' }} />
                  </div>
                  <div className="min-w-0">
                    <p className="text-base font-bold">
                      {history.length > 0 ? `${history.length} ${t.playedThisSession}` : t.readyToPlay}
                    </p>
                    <p className="text-xs opacity-65 truncate">{t.continueListening}</p>
                  </div>
                </div>

                <div className="rounded-3xl bg-white/[0.05] border border-white/10 divide-y divide-white/10 overflow-hidden">
                  <button
                    type="button"
                    onClick={() => setSection('recent')}
                    className="w-full flex items-center justify-between px-4 py-3.5 hover:bg-white/5 text-left"
                  >
                    <span className="flex items-center gap-3 text-sm font-medium">
                      <Clock className="w-4 h-4 opacity-75" />
                      {t.recentlyPlayed}
                    </span>
                    <ChevronRight className="w-4 h-4 opacity-50" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setSection('wrapped')}
                    className="w-full flex items-center justify-between px-4 py-3.5 hover:bg-white/5 text-left"
                  >
                    <span className="flex items-center gap-3 text-sm font-medium">
                      <BarChart3 className="w-4 h-4 opacity-75" />
                      {t.nav.stats}
                    </span>
                    <ChevronRight className="w-4 h-4 opacity-50" />
                  </button>
                </div>

                <div className="rounded-3xl bg-white/[0.05] border border-white/10 overflow-hidden">
                  <button
                    type="button"
                    onClick={() => setSection('settings')}
                    className="w-full flex items-center justify-between px-4 py-3.5 hover:bg-white/5 text-left"
                  >
                    <span className="flex items-center gap-3 text-sm font-medium">
                      <Settings className="w-4 h-4 opacity-75" />
                      {t.nav.settings}
                    </span>
                    <ChevronRight className="w-4 h-4 opacity-50" />
                  </button>
                </div>
              </div>
            ) : section === 'wrapped' ? (
              <LocalWrapped
                history={history}
                catalog={[...trendingTracks, ...newReleases, ...localTracks, ...likedTracks]}
              />
            ) : section === 'settings' ? (
              <SettingsView
                t={t}
                nativeMode={nativeMode}
                appSettings={appSettings}
                onChangeAppSettings={setAppSettings}
                language={language}
                country={country}
                onChangeLanguage={setLanguage}
                onChangeCountry={setCountry}
                activeEqId={activeEqId}
                eqBands={eqBands}
                onSelectEqPreset={(id) => {
                  const preset = BUILTIN_EQ_PRESETS.find((p) => p.id === id);
                  if (preset) {
                    setActiveEqId(id);
                    setEqBands(preset.bands);
                    void state.setEQBands(preset.bands, preset.preampDb);
                  }
                }}
                onChangeEqBand={(idx, gainDb) => {
                  const next = eqBands.map((b, i) => (i === idx ? { ...b, gainDb } : b));
                  setActiveEqId('custom');
                  setEqBands(next);
                  void state.setEQBands(next, 0);
                }}
                crossfadeDuration={state.crossfadeDuration}
                onCrossfadeChange={(s) => void state.setCrossfadeDuration(s)}
                preferredQuality={state.preferredQuality}
                onQualityChange={(q) => state.setPreferredQuality(q)}
                historyCount={history.length}
                offlineCount={offlineTracks.length}
                onClearHistory={() => {
                  setRecentTracks([]);
                  setHistory([]);
                }}
                onClearOffline={() => {
                  void Promise.all(offlineTracks.map((tr) => offlineCacheRef.current.remove(tr.id)));
                  setOfflineTracks([]);
                }}
                onExportBackup={() => {
                  const payload = {
                    version: 1,
                    exportedAt: new Date().toISOString(),
                    likedTracks,
                    playlists,
                    recentTracks,
                    history,
                    appSettings,
                    language,
                    country,
                  };
                  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
                  const url = URL.createObjectURL(blob);
                  const a = document.createElement('a');
                  a.href = url;
                  a.download = `swara-backup-${new Date().toISOString().slice(0, 10)}.json`;
                  a.click();
                  URL.revokeObjectURL(url);
                }}
                onImportBackup={(file) => {
                  void file.text().then((raw) => {
                    try {
                      const data = JSON.parse(raw) as Record<string, unknown>;
                      if (Array.isArray(data.likedTracks)) setLikedTracks(data.likedTracks as Track[]);
                      if (Array.isArray(data.playlists)) setPlaylists(data.playlists as Playlist[]);
                      if (Array.isArray(data.recentTracks)) setRecentTracks(data.recentTracks as Track[]);
                      if (Array.isArray(data.history)) setHistory(data.history as PlayHistoryEntry[]);
                      if (data.appSettings && typeof data.appSettings === 'object') {
                        setAppSettings({ ...appSettings, ...(data.appSettings as Partial<AppSettings>) });
                      }
                    } catch {
                      setPlaybackError('File cadangan JSON tidak valid.');
                    }
                  });
                }}
              />
            ) : section === 'explore' || section === 'search' ? (
              <div className="space-y-7">
                <h1 className="text-2xl md:text-3xl font-bold">
                  {section === 'search' ? t.nav.search : t.nav.explore}
                </h1>
                <HorizontalShelf
                  title={t.newReleases}
                  tracks={newReleases.length > 0 ? newReleases : trendingTracks}
                  onPlayIndex={(idx) =>
                    void handlePlayTrackList(
                      newReleases.length > 0 ? newReleases : trendingTracks,
                      idx
                    )
                  }
                />
                <section className="space-y-3">
                  <h2 className="text-lg font-bold">{t.moodsAndGenres}</h2>
                  <div className="flex flex-wrap gap-2">
                    {moods.map((mood) => (
                      <button
                        key={mood}
                        type="button"
                        onClick={() => setSearchQuery(mood)}
                        className="px-4 py-2 rounded-2xl text-xs font-semibold bg-white/[0.06] hover:bg-white/15 transition-colors"
                      >
                        {mood}
                      </button>
                    ))}
                  </div>
                </section>
              </div>
            ) : section === 'liked' ? (
              <div className="space-y-5">
                <div className="flex items-center justify-between">
                  <h1 className="text-2xl md:text-3xl font-bold flex items-center gap-2.5">
                    <Heart className="w-6 h-6" style={{ color: 'var(--primary)', fill: 'var(--primary)' }} />
                    {t.likedSongs} ({likedTracks.length})
                  </h1>
                  {likedTracks.length > 0 && (
                    <button
                      type="button"
                      onClick={() => void handlePlayTrackList(likedTracks, 0)}
                      className="px-4 py-2 rounded-full text-xs font-semibold"
                      style={{ backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }}
                    >
                      {t.playAll}
                    </button>
                  )}
                </div>
                {renderTrackGrid(likedTracks, t.noLikedSongs)}
              </div>
            ) : section === 'recent' ? (
              <div className="space-y-5">
                <h1 className="text-2xl md:text-3xl font-bold">{t.recentlyPlayed}</h1>
                {renderTrackGrid(recentTracks, t.noRecentTracks)}
              </div>
            ) : section === 'playlists' || section === 'library' ? (
              <div className="space-y-5">
                <h1 className="text-2xl md:text-3xl font-bold">{t.nav.playlists}</h1>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
                  <form onSubmit={createPlaylist} className="flex gap-2">
                    <input
                      value={newPlaylistName}
                      onChange={(e) => setNewPlaylistName(e.target.value)}
                      placeholder={t.playlistNamePlaceholder}
                      className="flex-1 px-4 py-2 rounded-full bg-white/5 border border-white/10 text-sm outline-none"
                    />
                    <button
                      type="submit"
                      className="inline-flex items-center gap-1.5 px-4 py-2 rounded-full text-xs font-semibold shrink-0"
                      style={{ backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }}
                    >
                      <Plus className="w-4 h-4" />
                      {t.create}
                    </button>
                  </form>

                  <form onSubmit={handleImportSpotifyOrYtm} className="flex flex-col sm:flex-row gap-2">
                    <div className="flex-1 flex items-center gap-2 px-3.5 py-2 rounded-full bg-white/5 border border-white/10 min-w-0">
                      <Link2 className="w-4 h-4 opacity-60 shrink-0" />
                      <input
                        value={spotifyUrl}
                        onChange={(e) => setSpotifyUrl(e.target.value)}
                        placeholder="Tempel link playlist Spotify / YouTube Music..."
                        className="flex-1 bg-transparent text-sm outline-none min-w-0"
                      />
                    </div>
                    <div className="flex gap-2 shrink-0">
                      <button
                        type="submit"
                        disabled={Boolean(importingPlaylist)}
                        className="flex-1 sm:flex-none px-4 py-2 rounded-full text-xs font-semibold bg-white/10 hover:bg-white/15 shrink-0"
                      >
                        {importingPlaylist ? 'Mengimpor...' : 'Impor Link'}
                      </button>
                      <input
                        ref={playlistFileInputRef}
                        type="file"
                        accept=".m3u,.m3u8,.csv,.json"
                        onChange={handleImportPlaylistFile}
                        className="hidden"
                      />
                      <button
                        type="button"
                        onClick={() => playlistFileInputRef.current?.click()}
                        className="inline-flex items-center justify-center gap-1.5 px-3.5 py-2 rounded-full text-xs font-semibold bg-white/10 hover:bg-white/15 shrink-0"
                        title="Impor M3U / CSV / JSON"
                      >
                        <Upload className="w-3.5 h-3.5" />
                        File
                      </button>
                    </div>
                  </form>
                </div>

                {importingPlaylist && (
                  <p className="text-xs opacity-75 animate-pulse">{importingPlaylist}</p>
                )}

                {playlists.length === 0 ? (
                  <p className="text-sm opacity-60 py-6">
                    {t.noPlaylists}
                  </p>
                ) : (
                  <div className="space-y-4">
                    {playlists.map((pl) => (
                      <div key={pl.id} className="p-4 rounded-3xl bg-white/[0.04] border border-white/10 space-y-3">
                        <div className="flex items-center justify-between">
                          <div>
                            <h2 className="font-bold text-base">{pl.title}</h2>
                            <p className="text-xs opacity-65">{pl.tracks.length} {t.songsCount}</p>
                          </div>
                          <div className="flex items-center gap-2">
                            {pl.tracks.length > 0 && (
                              <button
                                type="button"
                                onClick={() => void handlePlayTrackList(pl.tracks, 0)}
                                className="px-3.5 py-1.5 rounded-full text-xs font-semibold"
                                style={{ backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }}
                              >
                                {t.play}
                              </button>
                            )}
                            <button
                              type="button"
                              onClick={() => setPlaylists((prev) => prev.filter((x) => x.id !== pl.id))}
                              className="p-1.5 rounded-xl hover:bg-white/10 opacity-70 hover:opacity-100"
                              aria-label="Delete playlist"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                        </div>
                        {pl.tracks.length > 0 && renderTrackGrid(pl.tracks, '')}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ) : section === 'offline' ? (
              <div className="space-y-5">
                <div className="flex flex-wrap items-center justify-between gap-4">
                  <h1 className="text-2xl md:text-3xl font-bold flex items-center gap-2.5">
                    <Download className="w-6 h-6" style={{ color: 'var(--primary)' }} />
                    {t.nav.offline} ({offlineTracks.length})
                  </h1>
                </div>
                {renderTrackGrid(
                  offlineTracks,
                  'Belum ada lagu tersimpan offline. Gunakan tombol unduh pada file audio lokal atau stream langsung.'
                )}
              </div>
            ) : section === 'local' ? (
              <div className="space-y-5">
                <div className="flex flex-wrap items-center justify-between gap-4">
                  <h1 className="text-2xl md:text-3xl font-bold">{t.nav.local}</h1>
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-xs font-semibold"
                    style={{ backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }}
                  >
                    <FolderPlus className="w-4 h-4" />
                    {t.chooseAudioFiles}
                  </button>
                </div>
                {renderTrackGrid(localTracks, t.noLocalFiles)}
              </div>
            ) : (
              /* Home View */
              <div className="space-y-7">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <h1 className="text-2xl md:text-3xl font-bold">
                    {appSettings.showGreeting ? getGreeting(t) : t.nav.home}
                  </h1>
                  <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/[0.06] text-xs font-medium">
                    <Flame className="w-3.5 h-3.5" style={{ color: 'var(--primary)' }} />
                    <span>{history.length > 0 ? `${history.length} ${t.playedThisSession}` : t.readyToPlay}</span>
                  </div>
                </div>

                {continueTrack && (
                  <div
                    className="rounded-3xl p-4 md:p-5 border border-white/10 flex flex-wrap items-center justify-between gap-4"
                    style={{
                      background:
                        'linear-gradient(120deg, color-mix(in srgb, var(--primary) 16%, var(--surface-container)), var(--surface-container))',
                    }}
                  >
                    <div className="flex items-center gap-4 min-w-0">
                      <TrackArtwork track={continueTrack} className="w-14 h-14 rounded-2xl shadow-lg" />
                      <div className="min-w-0">
                        <p className="text-[11px] uppercase tracking-wider opacity-65">
                          {recentTracks.length > 0 ? t.continueListening : t.topPick}
                        </p>
                        <p className="text-base md:text-lg font-bold truncate">{continueTrack.title}</p>
                        <p className="text-xs opacity-70 truncate">
                          {continueTrack.artists.map((a) => a.name).join(', ')}
                        </p>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => void handlePlayTrackList([continueTrack, ...trendingTracks], 0)}
                      className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full text-xs font-bold shadow-md active:scale-95 transition-transform"
                      style={{ backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }}
                    >
                      <Play className="w-3.5 h-3.5" />
                      {t.play}
                    </button>
                  </div>
                )}

                {/* Mobile 2-column compact Quick Picks */}
                {(recentTracks.length > 0 || trendingTracks.length > 0) && (
                  <div className="md:hidden grid grid-cols-2 gap-2.5">
                    {(recentTracks.length > 0 ? recentTracks : trendingTracks).slice(0, 6).map((tr, idx, arr) => (
                      <button
                        key={`m-qp-${tr.id}`}
                        type="button"
                        onClick={() => void handlePlayTrackList(arr, idx)}
                        className="flex items-center gap-2.5 p-1.5 rounded-xl bg-white/[0.06] hover:bg-white/10 text-left min-w-0 transition-colors"
                      >
                        <TrackArtwork track={tr} className="w-11 h-11 rounded-lg shrink-0" />
                        <span className="text-xs font-semibold line-clamp-2 leading-tight">{tr.title}</span>
                      </button>
                    ))}
                  </div>
                )}

                {appSettings.showRecentOnHome && recentTracks.length > 0 && (
                  <HorizontalShelf
                    title={t.recentlyPlayed}
                    tracks={recentTracks}
                    onPlayIndex={(idx) => void handlePlayTrackList(recentTracks, idx)}
                  />
                )}

                {personalizedShelves.map((shelf) => (
                  <HorizontalShelf
                    key={shelf.id}
                    title={shelf.title}
                    tracks={shelf.tracks}
                    onPlayIndex={(idx) => void handlePlayTrackList(shelf.tracks, idx)}
                  />
                ))}

                {homeShelves.length > 0 ? (
                  homeShelves.map((shelf) => {
                    const shelfTitle =
                      shelf.id === 'trending'
                        ? t.trendingForYou
                        : shelf.id === 'new-releases'
                          ? t.newReleases
                          : shelf.id === 'home-0'
                            ? t.quickPicks
                            : shelf.title;

                    return shelf.layout === 'carousel' ? (
                      <HorizontalShelf
                        key={shelf.id}
                        title={shelfTitle}
                        tracks={shelf.tracks}
                        onPlayIndex={(idx) => void handlePlayTrackList(shelf.tracks, idx)}
                      />
                    ) : (
                      <section key={shelf.id} className="space-y-3">
                        <div className="flex items-center justify-between">
                          <h2 className="text-lg font-bold tracking-tight">{shelfTitle}</h2>
                          <button
                            type="button"
                            onClick={() => void handlePlayTrackList(shelf.tracks, 0)}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold bg-white/10 hover:bg-white/15"
                          >
                            <Play className="w-3 h-3" />
                            {t.playAll}
                          </button>
                        </div>
                        {renderTrackGrid(shelf.tracks.slice(0, 4), '')}
                      </section>
                    );
                  })
                ) : (
                  <section className="space-y-3">
                    <h2 className="text-lg font-bold tracking-tight">{t.trendingForYou}</h2>
                    {renderTrackGrid(trendingTracks.slice(0, 4), '')}
                  </section>
                )}
              </div>
            )}
              </>
            )}
          </main>
        </div>

        <RightSidePanel
          t={t}
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          activeTab={drawerTab}
          onSelectTab={setDrawerTab}
          queue={state.queue}
          currentIndex={state.currentIndex}
          recentTracks={recentTracks}
          autoPlaySimilar={state.autoPlaySimilar}
          onToggleAutoPlaySimilar={() => state.setAutoPlaySimilar(!state.autoPlaySimilar)}
          onPlayIndex={(idx) => void handlePlayTrackList(state.queue, idx)}
          onRemoveFromQueue={(idx) => state.removeFromQueue(idx)}
          onClearUpcoming={() => state.clearUpcoming()}
          onPlayTrack={(t) => void handlePlayTrackList([t, ...recentTracks], 0)}
          lyrics={lyrics}
          positionMs={state.positionMs}
          onSeek={(ms) => void state.seek(ms)}
        />

        <FloatingMiniplayer
          open={miniplayerOpen}
          onClose={() => setMiniplayerOpen(false)}
          onExpandFullscreen={() => {
            setMiniplayerOpen(false);
            setFullscreenOpen(true);
          }}
          currentTrack={state.currentTrack}
          playState={state.playState}
          positionMs={state.positionMs}
          durationMs={state.durationMs}
          onTogglePlayPause={() => void state.togglePlayPause()}
          onNext={() => void state.next()}
          onPrevious={() => void state.previous()}
        />

        <PlayerBar
          selectSongText={t.selectSongToStart}
          currentTrack={state.currentTrack}
          playState={state.playState}
          positionMs={state.positionMs}
          durationMs={state.durationMs}
          volume={state.volume}
          playbackSpeed={state.playbackSpeed}
          sleepRemainingMin={sleepRemainingMin}
          shuffleMode={state.shuffleMode}
          repeatMode={state.repeatMode}
          isLiked={isTrackLiked(state.currentTrack)}
          drawerOpen={drawerOpen}
          drawerTab={drawerTab}
          lyricsViewOpen={lyricsViewOpen}
          miniplayerOpen={miniplayerOpen}
          onToggleLyricsView={() => setLyricsViewOpen((v) => !v)}
          onToggleMiniplayer={() => setMiniplayerOpen((v) => !v)}
          onToggleDrawerTab={toggleDrawerTab}
          onToggleLike={() => {
            if (state.currentTrack) toggleLikeTrack(state.currentTrack);
          }}
          onTogglePlayPause={() => void state.togglePlayPause()}
          onNext={() => void state.next()}
          onPrevious={() => void state.previous()}
          onSeek={(ms) => void state.seek(ms)}
          onVolumeChange={(v) => void state.setVolume(v)}
          onCycleSpeed={cycleSpeed}
          onCycleSleepTimer={cycleSleepTimer}
          onToggleShuffle={() => state.toggleShuffle()}
          onCycleRepeat={cycleRepeat}
          onOpenFullscreen={() => setFullscreenOpen(true)}
        />

        <MobileNavAndMiniplayer
          t={t}
          activeSection={section}
          onSelectSection={(s) => {
            setSection(s);
            setLyricsViewOpen(false);
            if (s !== 'search') {
              setSearchResults(null);
              setSearchQuery('');
            }
          }}
          currentTrack={state.currentTrack}
          playState={state.playState}
          positionMs={state.positionMs}
          durationMs={state.durationMs}
          isLiked={isTrackLiked(state.currentTrack)}
          onToggleLike={() => {
            if (state.currentTrack) toggleLikeTrack(state.currentTrack);
          }}
          onTogglePlayPause={() => void state.togglePlayPause()}
          onNext={() => void state.next()}
          onPrevious={() => void state.previous()}
          onOpenFullscreen={() => setFullscreenOpen(true)}
        />

        <FullscreenNowPlaying
          open={fullscreenOpen}
          onClose={() => setFullscreenOpen(false)}
          currentTrack={state.currentTrack}
          playState={state.playState}
          positionMs={state.positionMs}
          durationMs={state.durationMs}
          shuffleMode={state.shuffleMode}
          repeatMode={state.repeatMode}
          isLiked={isTrackLiked(state.currentTrack)}
          sleepRemainingMin={sleepRemainingMin}
          lyrics={lyrics}
          onTogglePlayPause={() => void state.togglePlayPause()}
          onNext={() => void state.next()}
          onPrevious={() => void state.previous()}
          onSeek={(ms) => void state.seek(ms)}
          onToggleShuffle={() => state.toggleShuffle()}
          onCycleRepeat={cycleRepeat}
          onToggleLike={() => {
            if (state.currentTrack) toggleLikeTrack(state.currentTrack);
          }}
          onOpenQueue={() => {
            setDrawerTab('queue');
            setDrawerOpen(true);
          }}
          onAddToPlaylist={() => {
            if (state.currentTrack) openPlaylistPicker(state.currentTrack);
          }}
          onCycleSleepTimer={cycleSleepTimer}
          onOpenEqualizer={() => setEqModalOpen(true)}
          isDownloaded={isTrackDownloaded(state.currentTrack)}
          onDownloadTrack={
            nativeMode
              ? () => {
                  if (state.currentTrack) void handleDownloadTrack(state.currentTrack);
                }
              : undefined
          }
          onViewArtist={(artistName) => {
            setFullscreenOpen(false);
            setSection('search');
            setSearchQuery(artistName);
          }}
        />

        {!fullscreenOpen && tauriDesktop && (
          <DynamicIsland
            settings={appSettings.dynamicIsland}
            track={state.currentTrack}
            playState={state.playState}
            positionMs={state.positionMs}
            durationMs={state.durationMs}
            isLiked={isTrackLiked(state.currentTrack)}
            lyrics={lyrics}
            onTogglePlayPause={() => void state.togglePlayPause()}
            onNext={() => void state.next()}
            onPrevious={() => void state.previous()}
            onToggleLike={() => {
              if (state.currentTrack) toggleLikeTrack(state.currentTrack);
            }}
            onOpenFullscreen={() => setFullscreenOpen(true)}
          />
        )}

        <CommandPalette
          open={paletteOpen}
          onClose={() => setPaletteOpen(false)}
          tracks={[...localTracks, ...trendingTracks, ...likedTracks]}
          onPlayTrack={(t) => {
            const list = [...localTracks, ...trendingTracks, ...likedTracks];
            const idx = list.findIndex((x) => x.id === t.id);
            void handlePlayTrackList(list, idx >= 0 ? idx : 0);
          }}
          onNavigate={setSection}
          onOpenFullscreen={() => setFullscreenOpen(true)}
        />

        {eqModalOpen && (
          <div
            className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-black/65 backdrop-blur-sm"
            onClick={() => setEqModalOpen(false)}
          >
            <div
              className="w-full max-w-2xl rounded-3xl glass-panel p-5 space-y-4 shadow-2xl max-h-[85vh] overflow-y-auto"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center justify-between">
                <h3 className="font-bold text-base">Equalizer</h3>
                <button
                  type="button"
                  onClick={() => setEqModalOpen(false)}
                  className="p-2 rounded-full hover:bg-white/10"
                  aria-label="Tutup Equalizer"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
              <EqualizerPanel
                activeEqId={activeEqId}
                eqBands={eqBands}
                onSelectEqPreset={(id) => {
                  const preset = BUILTIN_EQ_PRESETS.find((p) => p.id === id);
                  if (preset) {
                    setActiveEqId(id);
                    setEqBands(preset.bands);
                    void state.setEQBands(preset.bands, preset.preampDb);
                  }
                }}
                onChangeEqBand={(idx, gainDb) => {
                  const next = eqBands.map((b, i) => (i === idx ? { ...b, gainDb } : b));
                  setActiveEqId('custom');
                  setEqBands(next);
                  void state.setEQBands(next, 0);
                }}
              />
            </div>
          </div>
        )}

        {playlistPickerTrack && (
          <div
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm"
            onClick={() => setPlaylistPickerTrack(null)}
          >
            <div
              className="w-full max-w-sm rounded-3xl glass-panel p-5 space-y-3 shadow-2xl"
              onClick={(e) => e.stopPropagation()}
            >
              <h3 className="font-bold text-base">Tambah ke Playlist</h3>
              <p className="text-xs opacity-70 truncate">{playlistPickerTrack.title}</p>
              <div className="space-y-1.5 max-h-60 overflow-y-auto">
                {playlists.map((pl) => (
                  <button
                    key={pl.id}
                    type="button"
                    onClick={() => addTrackToPlaylist(pl.id, playlistPickerTrack)}
                    className="w-full flex items-center justify-between px-3.5 py-2.5 rounded-2xl bg-white/5 hover:bg-white/10 text-sm text-left"
                  >
                    <span className="truncate font-medium">{pl.title}</span>
                    <span className="text-xs opacity-60">{pl.tracks.length} lagu</span>
                  </button>
                ))}
              </div>
              <button
                type="button"
                onClick={() => setPlaylistPickerTrack(null)}
                className="w-full py-2 rounded-2xl text-xs font-semibold bg-white/10 hover:bg-white/15"
              >
                Batal
              </button>
            </div>
          </div>
        )}
      </div>
    </ThemeProvider>
  );
}
