import type { LyricsData, Track } from '@swara/core';
import { ChevronDown, Heart, ListPlus, Maximize2, Moon } from 'lucide-react';
import type { UIStrings } from '../i18n/translations.js';
import { SyncedLyrics } from './SyncedLyrics.js';
import { TrackArtwork } from './TrackArtwork.js';

interface LyricsMainViewProps {
  t: UIStrings;
  track: Track | null;
  lyrics: LyricsData | null;
  positionMs: number;
  isLiked: boolean;
  sleepRemainingMin: number | null;
  onClose: () => void;
  onSeek: (ms: number) => void;
  onToggleLike: () => void;
  onAddToPlaylist: () => void;
  onCycleSleepTimer: () => void;
  onOpenFullscreen: () => void;
}

/**
 * Immersive in-app Lyrics view rendered inside the main canvas so the Sidebar
 * and PlayerBar remain accessible, with blurred album art backdrop and lyric offset controls.
 */
export function LyricsMainView({
  t,
  track,
  lyrics,
  positionMs,
  isLiked,
  sleepRemainingMin,
  onClose,
  onSeek,
  onToggleLike,
  onAddToPlaylist,
  onCycleSleepTimer,
  onOpenFullscreen,
}: LyricsMainViewProps) {
  return (
    <div className="relative h-full flex flex-col overflow-hidden rounded-2xl">
      {/* Blurred Album Art Ambient Backdrop */}
      {track?.artworkUrl && (
        <div className="absolute inset-0 pointer-events-none overflow-hidden">
          <img
            src={track.artworkUrl}
            alt=""
            referrerPolicy="no-referrer"
            className="w-full h-full object-cover scale-125 blur-3xl opacity-30"
          />
          <div className="absolute inset-0 bg-gradient-to-b from-black/35 via-black/45 to-black/70" />
        </div>
      )}

      {/* Top Controls: Collapse */}
      <div className="relative z-10 flex items-center justify-between gap-3 pb-4">
        <button
          type="button"
          onClick={onClose}
          className="p-2 rounded-full bg-white/10 hover:bg-white/15 transition-colors"
          aria-label="Tutup tampilan lirik"
          title="Kembali"
        >
          <ChevronDown className="w-4 h-4" />
        </button>
      </div>

      {/* Main Split: Left Artwork + Info | Right Synced Lyrics */}
      <div className="relative z-10 flex-1 grid grid-cols-1 grid-rows-[auto_1fr] lg:grid-rows-1 lg:grid-cols-12 gap-4 lg:gap-8 items-center min-h-0 overflow-hidden px-2 md:px-6">
        <div className="lg:col-span-5 flex flex-row lg:flex-col items-center lg:items-start justify-start lg:justify-center gap-4 min-w-0">
          <TrackArtwork
            track={track}
            className="w-16 h-16 sm:w-20 sm:h-20 lg:w-64 lg:h-64 xl:w-72 xl:h-72 rounded-2xl lg:rounded-3xl shadow-2xl"
          />

          <div className="space-y-1 min-w-0 flex-1 lg:flex-none lg:max-w-xs">
            <h2 className="text-base sm:text-lg lg:text-2xl font-bold leading-tight truncate">
              {track?.title ?? 'Belum ada lagu'}
            </h2>
            <p className="text-xs lg:text-sm opacity-75 truncate">
              {track?.artists.map((a) => a.name).join(', ') || 'Swara'}
            </p>
          </div>

          {track && (
            <div className="flex items-center gap-1 lg:gap-2 pt-0 lg:pt-1 shrink-0">
              <button
                type="button"
                onClick={onToggleLike}
                className="p-2 rounded-full hover:bg-white/10 transition-colors"
                aria-label={isLiked ? 'Hapus suka' : 'Sukai lagu'}
              >
                <Heart
                  className="w-4 h-4"
                  style={isLiked ? { color: 'var(--primary)', fill: 'var(--primary)' } : { opacity: 0.75 }}
                />
              </button>
              <button
                type="button"
                onClick={onAddToPlaylist}
                className="p-2 rounded-full hover:bg-white/10 opacity-75 hover:opacity-100"
                aria-label="Tambah ke Playlist"
              >
                <ListPlus className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={onCycleSleepTimer}
                className="inline-flex items-center gap-1 p-2 rounded-full hover:bg-white/10 opacity-75 hover:opacity-100 text-xs"
                style={sleepRemainingMin !== null ? { color: 'var(--primary)', opacity: 1 } : {}}
                aria-label="Sleep Timer"
              >
                <Moon className="w-4 h-4" />
                {sleepRemainingMin !== null && <span>{sleepRemainingMin}m</span>}
              </button>
              <button
                type="button"
                onClick={onOpenFullscreen}
                className="p-2 rounded-full hover:bg-white/10 opacity-75 hover:opacity-100"
                aria-label="Layar penuh"
              >
                <Maximize2 className="w-4 h-4" />
              </button>
            </div>
          )}
        </div>

        <div className="lg:col-span-7 h-full min-h-0 flex flex-col overflow-hidden">
          <div className="flex-1 min-h-0 overflow-hidden">
            <SyncedLyrics
              lyrics={lyrics}
              positionMs={positionMs}
              onSeek={onSeek}
              emptyText={t.noLyricsAvailable}
            />
          </div>
          {lyrics && (
            <p className="text-[11px] opacity-50 text-right pt-2">
              {t.lyrics}: {lyrics.provider.toUpperCase()}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
