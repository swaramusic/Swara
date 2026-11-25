import type { LyricsData, PlayState, RepeatMode, Track } from '@swara/core';
import { AnimatePresence, motion } from 'framer-motion';
import {
  ChevronDown,
  Download,
  Heart,
  Link2,
  ListOrdered,
  ListPlus,
  Mic2,
  Moon,
  MoreVertical,
  Pause,
  Play,
  Repeat,
  Repeat1,
  Share2,
  Shuffle,
  SkipBack,
  SkipForward,
  Sliders,
  User,
  X,
} from 'lucide-react';
import { useState } from 'react';
import { SyncedLyrics } from './SyncedLyrics.js';
import { TrackArtwork } from './TrackArtwork.js';
import { formatTimeMs } from './types.js';

interface FullscreenNowPlayingProps {
  open: boolean;
  onClose: () => void;
  currentTrack: Track | null;
  playState: PlayState;
  positionMs: number;
  durationMs: number;
  shuffleMode: boolean;
  repeatMode: RepeatMode;
  isLiked: boolean;
  sleepRemainingMin?: number | null;
  lyrics: LyricsData | null;
  onTogglePlayPause: () => void;
  onNext: () => void;
  onPrevious: () => void;
  onSeek: (ms: number) => void;
  onToggleShuffle: () => void;
  onCycleRepeat: () => void;
  onToggleLike: () => void;
  onOpenQueue: () => void;
  onAddToPlaylist?: () => void;
  onCycleSleepTimer?: () => void;
  onOpenEqualizer?: () => void;
  onViewArtist?: (artistName: string) => void;
  isDownloaded?: boolean;
  onDownloadTrack?: (() => void) | undefined;
}

export function FullscreenNowPlaying({
  open,
  onClose,
  currentTrack,
  playState,
  positionMs,
  durationMs,
  shuffleMode,
  repeatMode,
  isLiked,
  sleepRemainingMin = null,
  lyrics,
  onTogglePlayPause,
  onNext,
  onPrevious,
  onSeek,
  onToggleShuffle,
  onCycleRepeat,
  onToggleLike,
  onOpenQueue,
  onAddToPlaylist,
  onCycleSleepTimer,
  onOpenEqualizer,
  onViewArtist,
  isDownloaded = false,
  onDownloadTrack,
}: FullscreenNowPlayingProps) {
  const isPlaying = playState === 'playing';
  const [mobileShowLyrics, setMobileShowLyrics] = useState(false);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [copiedNotice, setCopiedNotice] = useState(false);

  const trackUrl = currentTrack
    ? `https://music.youtube.com/watch?v=${currentTrack.id}`
    : '';

  const handleCopyLink = () => {
    if (!trackUrl) return;
    void navigator.clipboard?.writeText(trackUrl);
    setCopiedNotice(true);
    window.setTimeout(() => setCopiedNotice(false), 1800);
  };

  const handleShare = () => {
    if (!currentTrack) return;
    if (navigator.share) {
      void navigator
        .share({
          title: currentTrack.title,
          text: `${currentTrack.title} - ${currentTrack.artists.map((a) => a.name).join(', ')}`,
          url: trackUrl,
        })
        .catch(() => {});
    } else {
      handleCopyLink();
    }
  };

  const progressPct = durationMs > 0 ? Math.min(100, (positionMs / durationMs) * 100) : 0;

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0, y: 40 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 40 }}
          transition={{ type: 'spring', stiffness: 280, damping: 30 }}
          className="fixed inset-0 z-50 flex flex-col overflow-hidden pt-[env(safe-area-inset-top)] pb-[env(safe-area-inset-bottom)]"
          style={{ backgroundColor: 'var(--surface)', color: 'var(--on-surface)' }}
          role="dialog"
          aria-modal="true"
          aria-label="Fullscreen Now Playing"
        >
          {/* Animated ambient mesh gradient */}
          <div className="absolute inset-0 pointer-events-none overflow-hidden">
            <div
              className="absolute -top-1/4 -left-1/4 w-3/4 h-3/4 rounded-full blur-3xl opacity-40 animate-mesh-slow"
              style={{ background: 'radial-gradient(circle, var(--primary), transparent 70%)' }}
            />
            <div
              className="absolute -bottom-1/4 -right-1/4 w-3/4 h-3/4 rounded-full blur-3xl opacity-35 animate-mesh-slow"
              style={{ background: 'radial-gradient(circle, var(--accent), transparent 70%)' }}
            />
          </div>

          <header className="relative z-10 flex items-center justify-between px-4 md:px-6 py-3.5 shrink-0">
            <button
              type="button"
              onClick={onClose}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white/10 hover:bg-white/15 text-xs md:text-sm font-medium"
              aria-label="Close fullscreen view"
            >
              <ChevronDown className="w-4 h-4" />
              <span className="hidden sm:inline">Tutup</span>
            </button>

            <div className="text-center min-w-0 px-2">
              <p className="text-[11px] uppercase tracking-widest opacity-65">Now Playing</p>
              {currentTrack?.album?.title && (
                <p className="text-xs font-medium truncate max-w-[180px] opacity-85">
                  {currentTrack.album.title}
                </p>
              )}
            </div>

            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={() => setSheetOpen(true)}
                className="p-2 rounded-full bg-white/10 hover:bg-white/15"
                aria-label="More options"
              >
                <MoreVertical className="w-4 h-4" />
              </button>
            </div>
          </header>

          <div className="relative z-10 flex-1 flex flex-col lg:grid lg:grid-cols-2 gap-4 lg:gap-8 px-6 md:px-12 pb-5 min-h-0 overflow-hidden">
            {/* Left: Artwork & Controls */}
            <div
              className={`${
                mobileShowLyrics ? 'hidden lg:flex' : 'flex-1 flex'
              } flex-col items-center justify-evenly lg:justify-center gap-4 min-h-0 w-full`}
            >
              <TrackArtwork
                track={currentTrack}
                className="w-full max-w-[270px] sm:max-w-[300px] md:max-w-[320px] aspect-square rounded-3xl shadow-2xl shrink-0"
              />

              <div className="w-full max-w-md space-y-4">
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <h2 className="text-xl md:text-3xl font-bold truncate">
                      {currentTrack?.title ?? 'Nothing Playing'}
                    </h2>
                    <p className="text-sm md:text-base opacity-75 mt-0.5 truncate">
                      {currentTrack?.artists.map((a) => a.name).join(', ')}
                    </p>
                  </div>
                  {currentTrack && (
                    <div className="flex items-center gap-1 shrink-0">
                      <button
                        type="button"
                        onClick={onToggleLike}
                        className="p-2.5 rounded-full bg-white/10 hover:bg-white/15"
                        aria-label={isLiked ? 'Hapus suka' : 'Sukai lagu'}
                      >
                        <Heart
                          className="w-5 h-5"
                          style={isLiked ? { color: 'var(--primary)', fill: 'var(--primary)' } : { opacity: 0.85 }}
                        />
                      </button>
                      {onAddToPlaylist && (
                        <button
                          type="button"
                          onClick={onAddToPlaylist}
                          className="p-2.5 rounded-full bg-white/10 hover:bg-white/15"
                          aria-label="Tambah ke Playlist"
                        >
                          <ListPlus className="w-5 h-5 opacity-85" />
                        </button>
                      )}
                    </div>
                  )}
                </div>

                <div className="w-full space-y-2">
                  <input
                    type="range"
                    min={0}
                    max={Math.max(1, durationMs)}
                    value={Math.min(positionMs, durationMs)}
                    onChange={(e) => onSeek(Number(e.target.value))}
                    aria-label="Seek position"
                    className="w-full h-1.5 rounded-full appearance-none cursor-pointer accent-[var(--primary)]"
                    style={{
                      background: `linear-gradient(to right, var(--primary) ${progressPct}%, rgba(255,255,255,0.2) ${progressPct}%)`,
                    }}
                  />
                  <div className="flex justify-between text-xs font-mono opacity-70">
                    <span>{formatTimeMs(positionMs)}</span>
                    <span>{formatTimeMs(durationMs)}</span>
                  </div>
                </div>

                <div className="w-full flex items-center justify-between px-1">
                  <button
                    type="button"
                    onClick={onToggleShuffle}
                    className="p-2.5 rounded-full hover:bg-white/10"
                    style={shuffleMode ? { color: 'var(--primary)' } : { opacity: 0.65 }}
                    aria-label="Toggle shuffle"
                  >
                    <Shuffle className="w-5 h-5" />
                  </button>
                  <button
                    type="button"
                    onClick={onPrevious}
                    className="p-3 rounded-full hover:bg-white/10"
                    aria-label="Previous track"
                  >
                    <SkipBack className="w-6 h-6" />
                  </button>
                  <button
                    type="button"
                    onClick={onTogglePlayPause}
                    className="w-15 h-15 w-14 h-14 rounded-full flex items-center justify-center shadow-lg active:scale-95 transition-transform"
                    style={{ backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }}
                    aria-label={isPlaying ? 'Pause' : 'Play'}
                  >
                    {isPlaying ? <Pause className="w-6 h-6" /> : <Play className="w-6 h-6 ml-0.5" />}
                  </button>
                  <button
                    type="button"
                    onClick={onNext}
                    className="p-3 rounded-full hover:bg-white/10"
                    aria-label="Next track"
                  >
                    <SkipForward className="w-6 h-6" />
                  </button>
                  <button
                    type="button"
                    onClick={onCycleRepeat}
                    className="p-2.5 rounded-full hover:bg-white/10"
                    style={repeatMode !== 'off' ? { color: 'var(--primary)' } : { opacity: 0.65 }}
                    aria-label="Cycle repeat mode"
                  >
                    {repeatMode === 'one' ? <Repeat1 className="w-5 h-5" /> : <Repeat className="w-5 h-5" />}
                  </button>
                </div>

                {/* Bottom Utility Pill Bar (Mobile) */}
                <div className="lg:hidden w-full flex items-center justify-around py-2 px-3 rounded-full bg-white/10 backdrop-blur-xl border border-white/10">
                  <button
                    type="button"
                    onClick={() => setMobileShowLyrics((v) => !v)}
                    className="p-2 rounded-full hover:bg-white/10"
                    style={mobileShowLyrics ? { color: 'var(--primary)' } : undefined}
                    aria-label="Toggle lyrics view"
                  >
                    <Mic2 className="w-5 h-5" />
                  </button>
                  <button
                    type="button"
                    onClick={onOpenQueue}
                    className="p-2 rounded-full hover:bg-white/10"
                    aria-label="Buka Antrean"
                  >
                    <ListOrdered className="w-5 h-5" />
                  </button>
                  <button
                    type="button"
                    onClick={handleShare}
                    className="p-2 rounded-full hover:bg-white/10"
                    aria-label="Bagikan lagu"
                  >
                    <Share2 className="w-5 h-5" />
                  </button>
                  {onCycleSleepTimer && (
                    <button
                      type="button"
                      onClick={onCycleSleepTimer}
                      className="flex items-center gap-1 p-2 rounded-full hover:bg-white/10"
                      style={sleepRemainingMin ? { color: 'var(--primary)' } : undefined}
                      aria-label="Sleep timer"
                    >
                      <Moon className="w-5 h-5" />
                      {sleepRemainingMin !== null && (
                        <span className="text-[11px] font-bold">{sleepRemainingMin}m</span>
                      )}
                    </button>
                  )}
                </div>
              </div>
            </div>

            {/* Right: Interactive Karaoke Lyrics */}
            <div className={`${mobileShowLyrics ? 'flex' : 'hidden lg:flex'} flex-col h-full min-h-0 overflow-hidden`}>
              {mobileShowLyrics && (
                <div className="lg:hidden flex justify-end pb-2 shrink-0">
                  <button
                    type="button"
                    onClick={() => setMobileShowLyrics(false)}
                    className="px-3 py-1 rounded-full text-xs font-semibold bg-white/10 hover:bg-white/15"
                  >
                    Kembali ke Pemutar
                  </button>
                </div>
              )}
              <SyncedLyrics lyrics={lyrics} positionMs={positionMs} onSeek={onSeek} />
            </div>
          </div>

          {/* Mobile/Fullscreen Song Action Sheet */}
          <AnimatePresence>
            {sheetOpen && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                onClick={() => setSheetOpen(false)}
                className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-end justify-center"
              >
                <motion.div
                  initial={{ y: '100%' }}
                  animate={{ y: 0 }}
                  exit={{ y: '100%' }}
                  transition={{ type: 'spring', stiffness: 320, damping: 32 }}
                  onClick={(e) => e.stopPropagation()}
                  className="w-full max-w-md rounded-t-3xl p-5 pb-7 space-y-4 border-t border-white/15 shadow-2xl"
                  style={{ backgroundColor: 'var(--surface-container)', color: 'var(--on-surface)' }}
                >
                  <div className="w-10 h-1 rounded-full bg-white/25 mx-auto" />

                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-3 min-w-0">
                      <TrackArtwork track={currentTrack} className="w-11 h-11 rounded-xl shrink-0" />
                      <div className="min-w-0">
                        <p className="text-sm font-bold truncate">{currentTrack?.title ?? 'Swara'}</p>
                        <p className="text-xs opacity-65 truncate">
                          {currentTrack?.artists.map((a) => a.name).join(', ')}
                        </p>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => setSheetOpen(false)}
                      className="p-2 rounded-full hover:bg-white/10"
                      aria-label="Tutup menu"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  </div>

                  {/* Quick Tiles */}
                  <div className="grid grid-cols-3 gap-2.5">
                    {onAddToPlaylist && (
                      <button
                        type="button"
                        onClick={() => {
                          setSheetOpen(false);
                          onAddToPlaylist();
                        }}
                        className="flex flex-col items-center justify-center gap-1.5 py-3 px-2 rounded-2xl bg-white/[0.07] hover:bg-white/15 text-xs font-medium"
                      >
                        <ListPlus className="w-5 h-5" />
                        <span className="truncate">Playlist</span>
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={handleCopyLink}
                      className="flex flex-col items-center justify-center gap-1.5 py-3 px-2 rounded-2xl bg-white/[0.07] hover:bg-white/15 text-xs font-medium"
                    >
                      <Link2 className="w-5 h-5" />
                      <span className="truncate">{copiedNotice ? 'Tersalin!' : 'Salin Link'}</span>
                    </button>
                    <button
                      type="button"
                      onClick={handleShare}
                      className="flex flex-col items-center justify-center gap-1.5 py-3 px-2 rounded-2xl bg-white/[0.07] hover:bg-white/15 text-xs font-medium"
                    >
                      <Share2 className="w-5 h-5" />
                      <span className="truncate">Bagikan</span>
                    </button>
                  </div>

                  {/* Action Rows */}
                  <div className="rounded-2xl bg-white/[0.04] divide-y divide-white/10 overflow-hidden">
                    {onViewArtist && currentTrack?.artists[0]?.name && (
                      <button
                        type="button"
                        onClick={() => {
                          setSheetOpen(false);
                          onViewArtist(currentTrack.artists[0]!.name);
                        }}
                        className="w-full flex items-center justify-between gap-3 px-4 py-3 hover:bg-white/10 text-sm text-left"
                      >
                        <span className="flex items-center gap-3 min-w-0">
                          <User className="w-4 h-4 opacity-75 shrink-0" />
                          <span className="truncate">Lihat Artis</span>
                        </span>
                        <span className="text-xs opacity-60 truncate max-w-[140px]">
                          {currentTrack.artists[0].name}
                        </span>
                      </button>
                    )}
                    {onDownloadTrack && currentTrack && (
                      <button
                        type="button"
                        onClick={() => {
                          setSheetOpen(false);
                          onDownloadTrack();
                        }}
                        className="w-full flex items-center justify-between px-4 py-3 hover:bg-white/10 text-sm text-left"
                      >
                        <span className="flex items-center gap-3">
                          <Download className="w-4 h-4 opacity-75" />
                          <span>{isDownloaded ? 'Hapus dari Unduhan' : 'Unduh Offline'}</span>
                        </span>
                        {isDownloaded && (
                          <span className="text-[11px] font-semibold" style={{ color: 'var(--primary)' }}>
                            Tersimpan
                          </span>
                        )}
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => {
                        setSheetOpen(false);
                        onOpenQueue();
                      }}
                      className="w-full flex items-center gap-3 px-4 py-3 hover:bg-white/10 text-sm text-left"
                    >
                      <ListOrdered className="w-4 h-4 opacity-75" />
                      <span>Buka Antrean</span>
                    </button>
                    {onOpenEqualizer && (
                      <button
                        type="button"
                        onClick={() => {
                          setSheetOpen(false);
                          onOpenEqualizer();
                        }}
                        className="w-full flex items-center gap-3 px-4 py-3 hover:bg-white/10 text-sm text-left"
                      >
                        <Sliders className="w-4 h-4 opacity-75" />
                        <span>Equalizer</span>
                      </button>
                    )}
                    {onCycleSleepTimer && (
                      <button
                        type="button"
                        onClick={() => {
                          onCycleSleepTimer();
                        }}
                        className="w-full flex items-center justify-between px-4 py-3 hover:bg-white/10 text-sm text-left"
                      >
                        <span className="flex items-center gap-3">
                          <Moon className="w-4 h-4 opacity-75" />
                          <span>Sleep Timer</span>
                        </span>
                        <span className="text-xs opacity-65">
                          {sleepRemainingMin ? `${sleepRemainingMin}m` : 'Off'}
                        </span>
                      </button>
                    )}
                  </div>
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
