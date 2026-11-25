import type {
  Album,
  Artist,
  AudioQuality,
  LyricsData,
  LyricsLine,
  Playlist,
  StreamSource,
  Track,
} from '../models/types.js';
import type {
  MusicProvider,
  ProviderCapabilities,
  SearchOptions,
  SearchResults,
} from './music-provider.js';

export interface YouTubeProviderConfig {
  /** Base URL for InnerTube API. Use `/api/ytm` in Web (via proxy) or `https://music.youtube.com/youtubei/v1` in native. */
  baseUrl?: string;
  hl?: string;
  gl?: string;
  initialTracks?: Track[];
  fetchFn?: (typeof fetch) | undefined;
}

export interface ExploreData {
  trending: Track[];
  newReleases: Track[];
  moods: string[];
}

export interface HomeShelf {
  id: string;
  title: string;
  layout: 'grid' | 'carousel';
  tracks: Track[];
}

interface InnerTubeRun {
  text?: string;
}

interface InnerTubeResponsiveItem {
  playlistItemData?: { videoId?: string };
  overlay?: {
    musicItemThumbnailOverlayRenderer?: {
      content?: {
        musicPlayButtonRenderer?: {
          playNavigationEndpoint?: {
            watchEndpoint?: { videoId?: string };
          };
        };
      };
    };
  };
  flexColumns?: Array<{
    musicResponsiveListItemFlexColumnRenderer?: {
      text?: { runs?: InnerTubeRun[] };
    };
  }>;
  thumbnail?: {
    musicThumbnailRenderer?: {
      thumbnail?: { thumbnails?: Array<{ url?: string }> };
    };
  };
}

interface LrcLibRecord {
  syncedLyrics?: string;
  plainLyrics?: string;
}

/**
 * Parses standard LRC timestamps (`[mm:ss.xx] line`) into `LyricsLine[]`.
 */
export function parseLrc(lrcText: string): LyricsLine[] {
  const lines: LyricsLine[] = [];
  const pattern = /\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?\]\s*(.*)/;

  for (const raw of lrcText.split(/\r?\n/)) {
    const match = pattern.exec(raw);
    if (!match) continue;
    const min = Number.parseInt(match[1] ?? '0', 10);
    const sec = Number.parseInt(match[2] ?? '0', 10);
    const fracRaw = match[3] ?? '0';
    const ms = fracRaw.length === 2 ? Number.parseInt(fracRaw, 10) * 10 : Number.parseInt(fracRaw, 10);
    const startMs = min * 60_000 + sec * 1000 + ms;
    const text = (match[4] ?? '').trim();
    if (!text) continue;
    lines.push({ startMs, endMs: startMs + 4000, text });
  }

  for (let i = 0; i < lines.length - 1; i++) {
    lines[i]!.endMs = lines[i + 1]!.startMs;
  }

  return lines;
}

function parseDurationText(raw: string): number {
  const normalized = raw.trim().replace(/\./g, ':');
  const parts = normalized.split(':').map((p) => Number.parseInt(p, 10));
  if (parts.some((n) => Number.isNaN(n))) return 0;
  if (parts.length === 2) return (parts[0]! * 60 + parts[1]!) * 1000;
  if (parts.length === 3) return (parts[0]! * 3600 + parts[1]! * 60 + parts[2]!) * 1000;
  return 0;
}

function upgradeThumbnail(url: string | undefined, videoId: string): string {
  if (!url) return `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;
  return url.replace(/=w\d+-h\d+/, '=w544-h544');
}

function parseResponsiveTrack(item: InnerTubeResponsiveItem, providerId: string): Track | null {
  const videoId =
    item.playlistItemData?.videoId ??
    item.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer
      ?.playNavigationEndpoint?.watchEndpoint?.videoId;
  if (!videoId) return null;

  const title = item.flexColumns?.[0]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.[0]?.text ?? 'Untitled';
  const metaRuns = (item.flexColumns?.[1]?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?? [])
    .map((r) => r.text?.trim() ?? '')
    .filter((t) => t.length > 0 && t !== '•');

  const artistName = metaRuns[0] ?? 'YouTube Music';
  const lastToken = metaRuns[metaRuns.length - 1] ?? '';
  const durationMs = /^\d+[:.]\d{2}/.test(lastToken) ? parseDurationText(lastToken) : 0;

  const thumbs = item.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails;
  const rawThumb = thumbs?.[thumbs.length - 1]?.url;

  return {
    id: videoId,
    providerId,
    title,
    artists: [{ id: `yt-${artistName}`, providerId, name: artistName }],
    durationMs,
    artworkUrl: upgradeThumbnail(rawThumb, videoId),
  };
}

export function extractYouTubePlaylistId(input: string): string | null {
  const trimmed = input.trim();
  const listMatch = /[?&]list=([a-zA-Z0-9_-]+)/.exec(trimmed);
  if (listMatch?.[1]) return listMatch[1];
  if (/^(PL|OLAK5uy_|RD|UU|FL)[a-zA-Z0-9_-]+$/.test(trimmed)) return trimmed;
  return null;
}

/**
 * YouTube Music InnerTube (`WEB_REMIX`) provider — zero API keys required.
 * Fetches live Trending songs, Moods/Genres, and full catalog search.
 */
export class YouTubeProvider implements MusicProvider {
  readonly id = 'youtube';
  readonly name = 'YouTube Music';
  readonly capabilities: ProviderCapabilities = {
    search: true,
    stream: true,
    lyrics: true,
    recommendations: true,
    offlineCache: false,
  };

  private readonly baseUrl: string;
  private hl: string;
  private gl: string;
  private cachedTrending: Track[];
  private readonly fetchFn: typeof fetch;

  constructor(config: YouTubeProviderConfig = {}) {
    this.baseUrl = (config.baseUrl ?? 'https://music.youtube.com/youtubei/v1').replace(/\/+$/, '');
    this.hl = config.hl ?? 'id';
    this.gl = config.gl ?? 'ID';
    this.cachedTrending = config.initialTracks ?? [];
    this.fetchFn = config.fetchFn ?? globalThis.fetch.bind(globalThis);
  }

  setRegion(hl: string, gl: string): void {
    this.hl = hl;
    this.gl = gl;
  }

  private async postInnerTube<T>(endpoint: string, payload: Record<string, unknown>): Promise<T> {
    const res = await this.fetchFn(`${this.baseUrl}/${endpoint}?prettyPrint=false`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        context: {
          client: {
            clientName: 'WEB_REMIX',
            clientVersion: '1.20241118.01.00',
            hl: this.hl,
            gl: this.gl,
          },
        },
        ...payload,
      }),
    });

    if (!res.ok) {
      throw new Error(`YouTube Music InnerTube HTTP ${res.status}`);
    }
    return (await res.json()) as T;
  }

  /**
   * Fetches live Trending songs and Mood/Genre categories from YouTube Music Explore (`FEmusic_explore`).
   */
  async getExplore(): Promise<ExploreData> {
    type ExploreResponse = {
      contents?: {
        singleColumnBrowseResultsRenderer?: {
          tabs?: Array<{
            tabRenderer?: {
              content?: {
                sectionListRenderer?: {
                  contents?: Array<{
                    musicCarouselShelfRenderer?: {
                      contents?: Array<{
                        musicResponsiveListItemRenderer?: InnerTubeResponsiveItem;
                        musicTwoRowItemRenderer?: {
                          title?: { runs?: InnerTubeRun[] };
                          subtitle?: { runs?: InnerTubeRun[] };
                          navigationEndpoint?: { watchEndpoint?: { videoId?: string } };
                          thumbnailRenderer?: {
                            musicThumbnailRenderer?: {
                              thumbnail?: { thumbnails?: Array<{ url?: string }> };
                            };
                          };
                        };
                        musicNavigationButtonRenderer?: { buttonText?: { runs?: InnerTubeRun[] } };
                      }>;
                    };
                  }>;
                };
              };
            };
          }>;
        };
      };
    };

    const data = await this.postInnerTube<ExploreResponse>('browse', { browseId: 'FEmusic_explore' });
    const sections =
      data.contents?.singleColumnBrowseResultsRenderer?.tabs?.[0]?.tabRenderer?.content
        ?.sectionListRenderer?.contents ?? [];

    const trending: Track[] = [];
    const newReleases: Track[] = [];
    const moods: string[] = [];

    for (const section of sections) {
      const items = section.musicCarouselShelfRenderer?.contents ?? [];
      for (const entry of items) {
        if (entry.musicResponsiveListItemRenderer) {
          const track = parseResponsiveTrack(entry.musicResponsiveListItemRenderer, this.id);
          if (track) trending.push(track);
        } else if (entry.musicTwoRowItemRenderer) {
          const two = entry.musicTwoRowItemRenderer;
          const videoId = two.navigationEndpoint?.watchEndpoint?.videoId;
          if (videoId) {
            const title = two.title?.runs?.[0]?.text ?? 'Untitled';
            const sub = (two.subtitle?.runs ?? []).map((r) => r.text ?? '').join('').trim();
            const thumbs = two.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails;
            newReleases.push({
              id: videoId,
              providerId: this.id,
              title,
              artists: [{ id: `yt-${sub}`, providerId: this.id, name: sub || 'YouTube Music' }],
              durationMs: 0,
              artworkUrl: upgradeThumbnail(thumbs?.[thumbs.length - 1]?.url, videoId),
            });
          }
        } else if (entry.musicNavigationButtonRenderer) {
          const label = entry.musicNavigationButtonRenderer.buttonText?.runs?.[0]?.text;
          if (label) moods.push(label);
        }
      }
    }

    if (trending.length > 0) {
      this.cachedTrending = trending;
    }

    return { trending, newReleases: newReleases.slice(0, 16), moods: moods.slice(0, 24) };
  }

  /**
   * Builds a rich multi-shelf Home feed combining `FEmusic_home` (Quick Picks),
   * `FEmusic_explore` (Trending, New Releases), and curated genre searches.
   */
  async getHomeFeed(): Promise<{ shelves: HomeShelf[]; moods: string[] }> {
    type HomeBrowseResponse = {
      contents?: {
        singleColumnBrowseResultsRenderer?: {
          tabs?: Array<{
            tabRenderer?: {
              content?: {
                sectionListRenderer?: {
                  contents?: Array<{
                    musicCarouselShelfRenderer?: {
                      header?: {
                        musicCarouselShelfBasicHeaderRenderer?: {
                          title?: { runs?: InnerTubeRun[] };
                        };
                      };
                      contents?: Array<{
                        musicResponsiveListItemRenderer?: InnerTubeResponsiveItem;
                      }>;
                    };
                  }>;
                };
              };
            };
          }>;
        };
      };
    };

    const regionalCuration: Record<string, { popQuery: string; popTitle: string; chillQuery: string; chillTitle: string }> = {
      ID: { popQuery: 'Pop Indonesia hits terbaru', popTitle: 'Pop Indonesia Terkini', chillQuery: 'Indie Indonesia chill', chillTitle: 'Indie & Santai' },
      US: { popQuery: 'US Top Pop Hits', popTitle: 'Top Pop Hits', chillQuery: 'Indie Chill Vibes', chillTitle: 'Indie & Chill' },
      GB: { popQuery: 'UK Top 40 Hits', popTitle: 'UK Top Hits', chillQuery: 'British Indie Chill', chillTitle: 'UK Indie & Chill' },
      JP: { popQuery: 'J-Pop hits 2025', popTitle: 'J-Pop Hits', chillQuery: 'Japanese City Pop chill', chillTitle: 'City Pop & Indie' },
      KR: { popQuery: 'K-Pop hits terbaru', popTitle: 'K-Pop Hits', chillQuery: 'Korean R&B Indie chill', chillTitle: 'K-Indie & R&B' },
      MY: { popQuery: 'Lagu Melayu hits terbaru', popTitle: 'Hits Malaysia', chillQuery: 'Indie Malaysia chill', chillTitle: 'Indie & Santai' },
      SG: { popQuery: 'Singapore Top Hits', popTitle: 'Top Hits Singapore', chillQuery: 'Mandopop chill hits', chillTitle: 'Mandopop & Chill' },
    };

    const regionLabel = (() => {
      try {
        return new Intl.DisplayNames(['en'], { type: 'region' }).of(this.gl) ?? this.gl;
      } catch {
        return this.gl;
      }
    })();

    const curation = regionalCuration[this.gl] ?? {
      popQuery: `${regionLabel} Top Pop Hits`,
      popTitle: `${regionLabel} Top Hits`,
      chillQuery: `${regionLabel} Indie Chill`,
      chillTitle: `${regionLabel} Indie & Chill`,
    };

    const [homeRes, exploreRes, popRes, chillRes, rockHitsRes] = await Promise.allSettled([
      this.postInnerTube<HomeBrowseResponse>('browse', { browseId: 'FEmusic_home' }),
      this.getExplore(),
      this.search(curation.popQuery, { limit: 12 }),
      this.search(curation.chillQuery, { limit: 12 }),
      this.search('Rock classic greatest hits', { limit: 12 }),
    ]);

    const shelves: HomeShelf[] = [];

    if (homeRes.status === 'fulfilled') {
      const homeSections =
        homeRes.value.contents?.singleColumnBrowseResultsRenderer?.tabs?.[0]?.tabRenderer?.content
          ?.sectionListRenderer?.contents ?? [];

      for (const sec of homeSections) {
        const shelf = sec.musicCarouselShelfRenderer;
        if (!shelf) continue;
        const title =
          shelf.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.[0]?.text ?? 'Pilihan Cepat';
        const items: Track[] = [];
        for (const entry of shelf.contents ?? []) {
          if (entry.musicResponsiveListItemRenderer) {
            const parsed = parseResponsiveTrack(entry.musicResponsiveListItemRenderer, this.id);
            if (parsed) items.push(parsed);
          }
        }
        if (items.length > 0) {
          shelves.push({ id: `home-${shelves.length}`, title, layout: 'grid', tracks: items });
        }
      }
    }

    const moods = exploreRes.status === 'fulfilled' ? exploreRes.value.moods : [];

    if (exploreRes.status === 'fulfilled') {
      if (exploreRes.value.trending.length > 0) {
        shelves.push({
          id: 'trending',
          title: 'Trending Untukmu',
          layout: 'carousel',
          tracks: exploreRes.value.trending,
        });
      }
      if (exploreRes.value.newReleases.length > 0) {
        shelves.push({
          id: 'new-releases',
          title: 'Rilis Baru',
          layout: 'carousel',
          tracks: exploreRes.value.newReleases,
        });
      }
    }

    if (popRes.status === 'fulfilled' && popRes.value.tracks.length > 0) {
      shelves.push({
        id: 'regional-pop',
        title: curation.popTitle,
        layout: 'grid',
        tracks: popRes.value.tracks,
      });
    }

    if (chillRes.status === 'fulfilled' && chillRes.value.tracks.length > 0) {
      shelves.push({
        id: 'regional-chill',
        title: curation.chillTitle,
        layout: 'carousel',
        tracks: chillRes.value.tracks,
      });
    }

    if (rockHitsRes.status === 'fulfilled' && rockHitsRes.value.tracks.length > 0) {
      shelves.push({
        id: 'rock-hits',
        title: 'All Time Rock & Hits',
        layout: 'carousel',
        tracks: rockHitsRes.value.tracks,
      });
    }

    return { shelves, moods };
  }

  async search(query: string, options?: SearchOptions): Promise<SearchResults> {
    const q = query.trim();
    const limit = options?.limit ?? 20;

    if (!q) {
      return { tracks: this.cachedTrending.slice(0, limit), albums: [], artists: [] };
    }

    type SearchResponse = {
      contents?: {
        tabbedSearchResultsRenderer?: {
          tabs?: Array<{
            tabRenderer?: {
              content?: {
                sectionListRenderer?: {
                  contents?: Array<{
                    musicShelfRenderer?: {
                      contents?: Array<{ musicResponsiveListItemRenderer?: InnerTubeResponsiveItem }>;
                    };
                  }>;
                };
              };
            };
          }>;
        };
      };
    };

    const data = await this.postInnerTube<SearchResponse>('search', {
      query: q,
      // InnerTube "Songs" filter param
      params: 'EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D',
    });

    const sections =
      data.contents?.tabbedSearchResultsRenderer?.tabs?.[0]?.tabRenderer?.content
        ?.sectionListRenderer?.contents ?? [];

    const tracks: Track[] = [];
    for (const sec of sections) {
      for (const item of sec.musicShelfRenderer?.contents ?? []) {
        if (!item.musicResponsiveListItemRenderer) continue;
        const parsed = parseResponsiveTrack(item.musicResponsiveListItemRenderer, this.id);
        if (parsed) tracks.push(parsed);
        if (tracks.length >= limit) break;
      }
      if (tracks.length >= limit) break;
    }

    return { tracks, albums: [], artists: [] };
  }

  async getStreamUrl(trackId: string, _preferredQuality?: AudioQuality): Promise<StreamSource> {
    return {
      url: `youtube://${trackId}`,
      mimeType: 'video/youtube',
      quality: 'high',
    };
  }

  async getAlbum(_albumId: string): Promise<Album | null> {
    return null;
  }

  async getArtist(_artistId: string): Promise<Artist | null> {
    return null;
  }

  async getLyrics(track: Track): Promise<LyricsData | null> {
    const cleanTitle = track.title
      .replace(/\s*[\(\[](?:official|lyric|audio|video|mv|hd|4k|feat\.?|ft\.?)[^\)\]]*[\)\]]/gi, '')
      .trim();
    const artist = (track.artists[0]?.name ?? '').replace(/\s*-\s*Topic$/i, '').trim();

    const queries: URL[] = [];
    const exact = new URL('https://lrclib.net/api/search');
    exact.searchParams.set('track_name', cleanTitle || track.title);
    if (artist) exact.searchParams.set('artist_name', artist);
    queries.push(exact);

    const broad = new URL('https://lrclib.net/api/search');
    broad.searchParams.set('q', artist ? `${cleanTitle} ${artist}` : cleanTitle);
    queries.push(broad);

    for (const url of queries) {
      try {
        const res = await fetch(url.toString());
        if (!res.ok) continue;
        const items = (await res.json()) as LrcLibRecord[];
        const hit = items.find((i) => i.syncedLyrics) ?? items[0];
        if (!hit) continue;

        if (hit.syncedLyrics) {
          const lines = parseLrc(hit.syncedLyrics);
          if (lines.length > 0) {
            return { trackId: track.id, provider: 'lrclib', synced: true, lines };
          }
        }

        if (hit.plainLyrics) {
          return {
            trackId: track.id,
            provider: 'lrclib',
            synced: false,
            lines: hit.plainLyrics.split('\n').map((text) => ({ startMs: 0, endMs: 0, text })),
          };
        }
      } catch {
        // Try next query strategy.
      }
    }
    return null;
  }

  async getRecommendations(seedTrackId: string, limit = 15): Promise<Track[]> {
    type NextResponse = {
      contents?: {
        singleColumnMusicWatchNextResultsRenderer?: {
          tabbedRenderer?: {
            watchNextTabbedResultsRenderer?: {
              tabs?: Array<{
                tabRenderer?: {
                  content?: {
                    musicQueueRenderer?: {
                      content?: {
                        playlistPanelRenderer?: {
                          contents?: Array<{
                            playlistPanelVideoRenderer?: {
                              videoId?: string;
                              title?: { runs?: InnerTubeRun[] };
                              shortBylineText?: { runs?: InnerTubeRun[] };
                              lengthText?: { runs?: InnerTubeRun[] };
                              thumbnail?: { thumbnails?: Array<{ url?: string }> };
                            };
                          }>;
                        };
                      };
                    };
                  };
                };
              }>;
            };
          };
        };
      };
    };

    try {
      const data = await this.postInnerTube<NextResponse>('next', {
        videoId: seedTrackId,
        playlistId: `RDAMVM${seedTrackId}`,
      });

      const items =
        data.contents?.singleColumnMusicWatchNextResultsRenderer?.tabbedRenderer
          ?.watchNextTabbedResultsRenderer?.tabs?.[0]?.tabRenderer?.content?.musicQueueRenderer
          ?.content?.playlistPanelRenderer?.contents ?? [];

      const tracks: Track[] = [];
      for (const entry of items) {
        const r = entry.playlistPanelVideoRenderer;
        if (!r?.videoId || r.videoId === seedTrackId) continue;

        const title = r.title?.runs?.[0]?.text ?? 'Untitled';
        const artistName = r.shortBylineText?.runs?.[0]?.text ?? 'YouTube Music';
        const lengthStr = r.lengthText?.runs?.[0]?.text ?? '';
        const durationMs = /^\d+[:.]\d{2}/.test(lengthStr) ? parseDurationText(lengthStr) : 0;
        const thumbs = r.thumbnail?.thumbnails;
        const rawThumb = thumbs?.[thumbs.length - 1]?.url;

        tracks.push({
          id: r.videoId,
          providerId: this.id,
          title,
          artists: [{ id: `yt-${artistName}`, providerId: this.id, name: artistName }],
          durationMs,
          artworkUrl: upgradeThumbnail(rawThumb, r.videoId),
        });

        if (tracks.length >= limit) break;
      }

      if (tracks.length > 0) return tracks;
    } catch {
      // Fallback to cached trending tracks if radio endpoint fails.
    }

    return this.cachedTrending.filter((t) => t.id !== seedTrackId).slice(0, limit);
  }

  /**
   * Imports a public YouTube or YouTube Music playlist by URL or `list=` ID.
   */
  async importPlaylist(urlOrId: string): Promise<Playlist> {
    const rawId = extractYouTubePlaylistId(urlOrId);
    if (!rawId) {
      throw new Error('Link playlist YouTube / YouTube Music tidak valid.');
    }

    const browseId = rawId.startsWith('VL') ? rawId : `VL${rawId}`;
    type PlaylistBrowseResponse = {
      header?: {
        musicDetailHeaderRenderer?: { title?: { runs?: InnerTubeRun[] } };
      };
      contents?: {
        twoColumnBrowseResultsRenderer?: {
          tabs?: Array<{
            tabRenderer?: {
              content?: {
                sectionListRenderer?: {
                  contents?: Array<{
                    musicResponsiveHeaderRenderer?: { title?: { runs?: InnerTubeRun[] } };
                  }>;
                };
              };
            };
          }>;
          secondaryContents?: {
            sectionListRenderer?: {
              contents?: Array<{
                musicPlaylistShelfRenderer?: {
                  contents?: Array<{ musicResponsiveListItemRenderer?: InnerTubeResponsiveItem }>;
                };
              }>;
            };
          };
        };
        singleColumnBrowseResultsRenderer?: {
          tabs?: Array<{
            tabRenderer?: {
              content?: {
                sectionListRenderer?: {
                  contents?: Array<{
                    musicPlaylistShelfRenderer?: {
                      contents?: Array<{ musicResponsiveListItemRenderer?: InnerTubeResponsiveItem }>;
                    };
                  }>;
                };
              };
            };
          }>;
        };
      };
    };

    const data = await this.postInnerTube<PlaylistBrowseResponse>('browse', { browseId });
    const title =
      data.header?.musicDetailHeaderRenderer?.title?.runs?.[0]?.text ??
      data.contents?.twoColumnBrowseResultsRenderer?.tabs?.[0]?.tabRenderer?.content
        ?.sectionListRenderer?.contents?.[0]?.musicResponsiveHeaderRenderer?.title?.runs?.[0]?.text ??
      'YouTube Music Playlist';

    const items =
      data.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
        ?.contents?.[0]?.musicPlaylistShelfRenderer?.contents ??
      data.contents?.singleColumnBrowseResultsRenderer?.tabs?.[0]?.tabRenderer?.content
        ?.sectionListRenderer?.contents?.[0]?.musicPlaylistShelfRenderer?.contents ??
      [];

    const tracks: Track[] = [];
    for (const entry of items) {
      if (!entry.musicResponsiveListItemRenderer) continue;
      const parsed = parseResponsiveTrack(entry.musicResponsiveListItemRenderer, this.id);
      if (parsed) tracks.push(parsed);
    }

    if (tracks.length === 0) {
      throw new Error('Playlist YouTube Music kosong atau bersifat privat.');
    }

    const now = Date.now();
    return {
      id: `pl-ytm-${now}`,
      providerId: this.id,
      title,
      tracks,
      createdAtMs: now,
      updatedAtMs: now,
    };
  }
}
