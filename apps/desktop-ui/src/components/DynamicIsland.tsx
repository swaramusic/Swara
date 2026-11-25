import type { LyricsData, PlayState, Track } from '@swara/core';
import { motion } from 'framer-motion';
import { Heart, Maximize2, Pause, Play, SkipBack, SkipForward } from 'lucide-react';
import { useMemo, useState } from 'react';
import type { DynamicIslandSettings } from '../config/app-settings.js';
import { TrackArtwork } from './TrackArtwork.js';
import { formatTimeMs } from './types.js';

interface DynamicIslandProps {
  settings: DynamicIslandSettings;
  track: Track | null;
  playState: PlayState;
  positionMs: number;
  durationMs: number;
  isLiked: boolean;
  lyrics: LyricsData | null;
  onTogglePlayPause: () => void;
  onNext: () => void;
  onPrevious: () => void;
  onToggleLike: () => void;
  onOpenFullscreen: () => void;
}

export function DynamicIsland({
  settings,
  track,
  playState,
  positionMs,
  durationMs,
  isLiked,
  lyrics,
  onTogglePlayPause,
  onNext,
  onPrevious,
  onToggleLike,
  onOpenFullscreen,
}: DynamicIslandProps) {
  const [expanded, setExpanded] = useState(false);
  const isPlaying = playState === 'playing';

  const currentLyric = useMemo(() => {
    if (!lyrics?.synced || lyrics.lines.length === 0) return '';
    let active = '';
    for (const line of lyrics.lines) {
      if (positionMs >= line.startMs) active = line.text;
      else break;
    }
    return active;
  }, [lyrics, positionMs]);

  if (!settings.enabled || !track || settings.compactStyle === 'hidden') return null;
  if (settings.hideWhenPaused && !isPlaying) return null;

  const accent =
    settings.accentColor === 'pink'
      ? '#ff4d8d'
      : settings.accentColor === 'blue'
        ? '#3b82f6'
        : 'var(--primary)';

  const artworkClass =
    settings.artworkShape === 'circle'
      ? 'rounded-full'
      : settings.artworkShape === 'disc'
        ? `rounded-full ring-2 ring-white/25 ${isPlaying ? 'animate-spin' : ''}`
        : 'rounded-lg';

  const maxCompactWidth =
    settings.size === 'small' ? 'max-w-[230px]' : settings.size === 'large' ? 'max-w-[330px]' : 'max-w-[280px]';

  const progressPct = durationMs > 0 ? Math.min(100, (positionMs / durationMs) * 100) : 0;

  return (
    <div
      className="fixed inset-x-0 z-40 flex justify-center pointer-events-none"
      style={{
        top:
          settings.position === 'attached'
            ? 'env(safe-area-inset-top, 0px)'
            : 'calc(env(safe-area-inset-top, 10px) + 6px)',
      }}
    >
      <motion.div
        layout
        transition={{ type: 'spring', stiffness: 360, damping: 30 }}
        onMouseEnter={() => {
          if (settings.openTrigger === 'hover') setExpanded(true);
        }}
        onMouseLeave={() => {
          if (settings.openTrigger === 'hover') setExpanded(false);
        }}
        onClick={() => {
          if (settings.openTrigger === 'click' || !expanded) setExpanded((v) => !v);
        }}
        style={{
          backgroundColor: 'rgba(12, 12, 16, 0.92)',
          borderColor: 'rgba(255,255,255,0.14)',
        }}
        className={`pointer-events-auto border shadow-2xl backdrop-blur-2xl text-white overflow-hidden cursor-pointer ${
          settings.position === 'attached' ? 'rounded-b-3xl' : 'rounded-3xl'
        } ${expanded ? 'w-[340px] max-w-[calc(100vw-24px)] p-3.5' : `w-auto ${maxCompactWidth} px-3 py-1.5`}`}
        role="region"
        aria-label="Dynamic Island"
      >
        {!expanded ? (
          <div className="flex items-center gap-2.5 min-w-0">
            {settings.showArtwork && (
              <TrackArtwork
                track={track}
                className={`w-6 h-6 shrink-0 ${artworkClass}`}
              />
            )}
            <div className="min-w-0 flex-1">
              <p className="text-xs font-semibold truncate">
                {settings.compactStyle === 'lyric' && currentLyric ? currentLyric : track.title}
              </p>
            </div>
            {settings.showEqualizer && (
              <div className="flex items-end gap-[2px] h-3 shrink-0" aria-hidden="true">
                {[0.5, 1, 0.7, 0.9].map((h, i) => (
                  <span
                    key={i}
                    className={`w-[2.5px] rounded-full ${
                      isPlaying && settings.eqStyle !== 'static' ? 'animate-pulse' : ''
                    }`}
                    style={{
                      height: isPlaying ? `${Math.round(h * 12)}px` : '4px',
                      backgroundColor: accent,
                      animationDelay: `${i * 120}ms`,
                    }}
                  />
                ))}
              </div>
            )}
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onTogglePlayPause();
              }}
              className="p-1 rounded-full hover:bg-white/15 shrink-0"
              aria-label={isPlaying ? 'Pause' : 'Play'}
            >
              {isPlaying ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
            </button>
          </div>
        ) : (
          <div className="space-y-2.5" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center gap-3">
              {settings.showArtwork && (
                <TrackArtwork
                  track={track}
                  className={`w-12 h-12 shrink-0 ${artworkClass}`}
                />
              )}
              <div className="min-w-0 flex-1">
                <p className="text-sm font-bold truncate">{track.title}</p>
                {settings.showArtist && (
                  <p className="text-xs opacity-70 truncate">
                    {track.artists.map((a) => a.name).join(', ')}
                  </p>
                )}
                {settings.showLyricLine && currentLyric && (
                  <p className="text-[11px] truncate mt-0.5" style={{ color: accent }}>
                    {currentLyric}
                  </p>
                )}
              </div>
              <div className="flex items-center gap-1 shrink-0">
                {settings.showLikeButton && (
                  <button
                    type="button"
                    onClick={onToggleLike}
                    className="p-1.5 rounded-full hover:bg-white/10"
                    aria-label="Toggle Like"
                  >
                    <Heart
                      className="w-4 h-4"
                      style={isLiked ? { color: accent, fill: accent } : { opacity: 0.75 }}
                    />
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => {
                    setExpanded(false);
                    onOpenFullscreen();
                  }}
                  className="p-1.5 rounded-full hover:bg-white/10"
                  aria-label="Open Fullscreen"
                >
                  <Maximize2 className="w-4 h-4 opacity-75" />
                </button>
              </div>
            </div>

            {settings.showProgressBar && (
              <div className="space-y-1">
                <div
                  className={`w-full rounded-full bg-white/15 overflow-hidden relative ${
                    settings.progressStyle === 'thick' ? 'h-2' : 'h-1'
                  }`}
                >
                  <div
                    className="h-full rounded-full transition-[width] duration-200"
                    style={{ width: `${progressPct}%`, backgroundColor: accent }}
                  />
                </div>
                <div className="flex justify-between text-[10px] font-mono opacity-60">
                  <span>{formatTimeMs(positionMs)}</span>
                  <span>{formatTimeMs(durationMs)}</span>
                </div>
              </div>
            )}

            <div className="flex items-center justify-center gap-4">
              <button
                type="button"
                onClick={onPrevious}
                className="p-1.5 rounded-full hover:bg-white/10"
                aria-label="Previous"
              >
                <SkipBack className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={onTogglePlayPause}
                className="w-9 h-9 rounded-full flex items-center justify-center"
                style={{ backgroundColor: accent, color: '#0b0b0f' }}
                aria-label={isPlaying ? 'Pause' : 'Play'}
              >
                {isPlaying ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4 ml-0.5" />}
              </button>
              <button
                type="button"
                onClick={onNext}
                className="p-1.5 rounded-full hover:bg-white/10"
                aria-label="Next"
              >
                <SkipForward className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </motion.div>
    </div>
  );
}
