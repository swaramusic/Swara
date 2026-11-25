import type { PlayState, RepeatMode, Track } from '@swara/core';
import {
  Heart,
  ListOrdered,
  Loader2,
  Maximize2,
  Mic2,
  Moon,
  Pause,
  PictureInPicture2,
  Play,
  Repeat,
  Repeat1,
  Shuffle,
  SkipBack,
  SkipForward,
  Volume2,
  VolumeX,
} from 'lucide-react';
import { TrackArtwork } from './TrackArtwork.js';
import { formatTimeMs, type RightPanelTab } from './types.js';

interface PlayerBarProps {
  selectSongText?: string;
  currentTrack: Track | null;
  playState: PlayState;
  positionMs: number;
  durationMs: number;
  volume: number;
  playbackSpeed: number;
  sleepRemainingMin: number | null;
  shuffleMode: boolean;
  repeatMode: RepeatMode;
  isLiked: boolean;
  drawerOpen: boolean;
  drawerTab: RightPanelTab;
  lyricsViewOpen: boolean;
  miniplayerOpen: boolean;
  onToggleLyricsView: () => void;
  onToggleMiniplayer: () => void;
  onToggleDrawerTab: (tab: RightPanelTab) => void;
  onToggleLike: () => void;
  onTogglePlayPause: () => void;
  onNext: () => void;
  onPrevious: () => void;
  onSeek: (ms: number) => void;
  onVolumeChange: (v: number) => void;
  onCycleSpeed: () => void;
  onCycleSleepTimer: () => void;
  onToggleShuffle: () => void;
  onCycleRepeat: () => void;
  onOpenFullscreen: () => void;
}

export function PlayerBar({
  selectSongText = 'Select a track to play',
  currentTrack,
  playState,
  positionMs,
  durationMs,
  volume,
  playbackSpeed,
  sleepRemainingMin,
  shuffleMode,
  repeatMode,
  isLiked,
  drawerOpen,
  drawerTab,
  lyricsViewOpen,
  miniplayerOpen,
  onToggleLyricsView,
  onToggleMiniplayer,
  onToggleDrawerTab,
  onToggleLike,
  onTogglePlayPause,
  onNext,
  onPrevious,
  onSeek,
  onVolumeChange,
  onCycleSpeed,
  onCycleSleepTimer,
  onToggleShuffle,
  onCycleRepeat,
  onOpenFullscreen,
}: PlayerBarProps) {
  const isPlaying = playState === 'playing';
  const progressPct = durationMs > 0 ? Math.min(100, (positionMs / durationMs) * 100) : 0;

  return (
    <footer
      className="hidden md:flex items-center justify-between gap-4 px-5 py-2.5 rounded-3xl glass-panel shadow-2xl"
      style={{ boxShadow: '0 16px 40px -12px var(--glow)' }}
      aria-label="Audio Player Controls"
    >
      {/* Left: Track Info + Visualizer + Like */}
      <div className="flex items-center gap-3 w-64 lg:w-72 min-w-0">
        <div className="relative shrink-0">
          <TrackArtwork track={currentTrack} className="w-11 h-11 rounded-2xl shadow-md" />
          {isPlaying && (
            <div className="absolute -bottom-1 -right-1 flex items-end gap-0.5 px-1.5 py-0.5 rounded-md bg-black/80">
              <span className="w-0.5 h-2 rounded-full animate-pulse" style={{ backgroundColor: 'var(--primary)' }} />
              <span className="w-0.5 h-3 rounded-full animate-bounce" style={{ backgroundColor: 'var(--accent)' }} />
              <span className="w-0.5 h-1.5 rounded-full animate-pulse" style={{ backgroundColor: 'var(--primary)' }} />
            </div>
          )}
        </div>
        <div className="min-w-0 flex-1">
          <p className="font-semibold text-sm truncate">
            {currentTrack?.title ?? selectSongText}
          </p>
          <p className="text-xs opacity-65 truncate">
            {currentTrack?.artists.map((a) => a.name).join(', ') || 'Swara Player'}
          </p>
        </div>
        {currentTrack && (
          <button
            type="button"
            onClick={onToggleLike}
            className="p-1.5 rounded-full hover:bg-white/10 transition-colors shrink-0"
            aria-label={isLiked ? 'Hapus dari Lagu Disukai' : 'Tambah ke Lagu Disukai'}
          >
            <Heart
              className="w-4 h-4"
              style={isLiked ? { color: 'var(--primary)', fill: 'var(--primary)' } : { opacity: 0.65 }}
            />
          </button>
        )}
      </div>

      {/* Center: Transport & Seek */}
      <div className="flex-1 max-w-xl flex flex-col items-center gap-1">
        <div className="flex items-center gap-4">
          <button
            type="button"
            onClick={onToggleShuffle}
            className="p-1.5 rounded-full transition-opacity"
            style={{ color: shuffleMode ? 'var(--primary)' : 'inherit', opacity: shuffleMode ? 1 : 0.65 }}
            aria-label="Toggle shuffle"
          >
            <Shuffle className="w-4 h-4" />
          </button>
          <button
            type="button"
            onClick={onPrevious}
            className="p-1.5 rounded-full hover:bg-white/10 transition-colors"
            aria-label="Previous track"
          >
            <SkipBack className="w-5 h-5" />
          </button>
          <button
            type="button"
            onClick={onTogglePlayPause}
            className="w-9 h-9 rounded-full flex items-center justify-center transition-transform active:scale-95"
            style={{ backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }}
            aria-label={isPlaying ? 'Pause' : 'Play'}
          >
            {playState === 'loading' ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : isPlaying ? (
              <Pause className="w-4 h-4" />
            ) : (
              <Play className="w-4 h-4 ml-0.5" />
            )}
          </button>
          <button
            type="button"
            onClick={onNext}
            className="p-1.5 rounded-full hover:bg-white/10 transition-colors"
            aria-label="Next track"
          >
            <SkipForward className="w-5 h-5" />
          </button>
          <button
            type="button"
            onClick={onCycleRepeat}
            className="p-1.5 rounded-full transition-opacity"
            style={{ color: repeatMode !== 'off' ? 'var(--primary)' : 'inherit', opacity: repeatMode !== 'off' ? 1 : 0.65 }}
            aria-label="Cycle repeat mode"
          >
            {repeatMode === 'one' ? <Repeat1 className="w-4 h-4" /> : <Repeat className="w-4 h-4" />}
          </button>
        </div>

        <div className="w-full flex items-center gap-2.5 text-[11px] opacity-75 font-mono">
          <span className="w-10 text-right">{formatTimeMs(positionMs)}</span>
          <input
            type="range"
            min={0}
            max={Math.max(1, durationMs)}
            value={Math.min(positionMs, durationMs)}
            onChange={(e) => onSeek(Number(e.target.value))}
            aria-label="Seek position"
            className="flex-1 h-1.5 rounded-full appearance-none cursor-pointer bg-white/15 accent-[var(--primary)]"
            style={{
              background: `linear-gradient(to right, var(--primary) ${progressPct}%, rgba(255,255,255,0.15) ${progressPct}%)`,
            }}
          />
          <span className="w-10">{formatTimeMs(durationMs)}</span>
        </div>
      </div>

      {/* Right: Lyrics, Queue, Speed, Sleep Timer, Volume, Miniplayer, Fullscreen */}
      <div className="flex items-center justify-end gap-1.5 w-84">
        <button
          type="button"
          onClick={onToggleLyricsView}
          className="relative p-1.5 rounded-xl hover:bg-white/10 transition-colors"
          style={lyricsViewOpen ? { color: 'var(--primary)' } : { opacity: 0.75 }}
          aria-label="Buka Lirik"
          title="Lirik"
        >
          <Mic2 className="w-4 h-4" />
          {lyricsViewOpen && (
            <span
              className="absolute left-1/2 -translate-x-1/2 -bottom-0.5 w-1 h-1 rounded-full"
              style={{ backgroundColor: 'var(--primary)' }}
            />
          )}
        </button>
        <button
          type="button"
          onClick={() => onToggleDrawerTab('queue')}
          className="relative p-1.5 rounded-xl hover:bg-white/10 transition-colors"
          style={
            drawerOpen && (drawerTab === 'queue' || drawerTab === 'recent')
              ? { color: 'var(--primary)' }
              : { opacity: 0.75 }
          }
          aria-label="Buka Antrean"
          title="Antrean"
        >
          <ListOrdered className="w-4 h-4" />
          {drawerOpen && (drawerTab === 'queue' || drawerTab === 'recent') && (
            <span
              className="absolute left-1/2 -translate-x-1/2 -bottom-0.5 w-1 h-1 rounded-full"
              style={{ backgroundColor: 'var(--primary)' }}
            />
          )}
        </button>
        <button
          type="button"
          onClick={onCycleSpeed}
          className="px-2 py-1 rounded-lg text-[11px] font-mono font-semibold bg-white/10 hover:bg-white/15"
          title="Kecepatan putar"
        >
          {playbackSpeed}x
        </button>
        <button
          type="button"
          onClick={onCycleSleepTimer}
          className="inline-flex items-center gap-1 px-2 py-1 rounded-lg text-[11px] font-medium bg-white/10 hover:bg-white/15"
          style={sleepRemainingMin !== null ? { color: 'var(--primary)' } : {}}
          title="Sleep Timer"
        >
          <Moon className="w-3.5 h-3.5" />
          {sleepRemainingMin !== null ? `${sleepRemainingMin}m` : ''}
        </button>
        <button
          type="button"
          onClick={() => onVolumeChange(volume > 0 ? 0 : 1)}
          className="p-1.5 rounded-full hover:bg-white/10"
          aria-label={volume === 0 ? 'Unmute' : 'Mute'}
        >
          {volume === 0 ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
        </button>
        <input
          type="range"
          min={0}
          max={1}
          step={0.01}
          value={volume}
          onChange={(e) => onVolumeChange(Number(e.target.value))}
          aria-label="Volume"
          className="w-16 h-1.5 rounded-full appearance-none cursor-pointer bg-white/15 accent-[var(--primary)]"
        />
        <button
          type="button"
          onClick={onToggleMiniplayer}
          className="p-1.5 rounded-xl hover:bg-white/10 transition-colors"
          style={miniplayerOpen ? { color: 'var(--primary)' } : { opacity: 0.75 }}
          aria-label="Buka Miniplayer"
          title="Miniplayer"
        >
          <PictureInPicture2 className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={onOpenFullscreen}
          className="p-1.5 rounded-xl hover:bg-white/10 transition-colors"
          aria-label="Open fullscreen lyrics"
          title="Layar Penuh (F)"
        >
          <Maximize2 className="w-4 h-4" />
        </button>
      </div>
    </footer>
  );
}
