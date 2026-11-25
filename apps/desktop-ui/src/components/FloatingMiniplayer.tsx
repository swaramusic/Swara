import type { PlayState, Track } from '@swara/core';
import { AnimatePresence, motion } from 'framer-motion';
import { Maximize2, Pause, Play, SkipBack, SkipForward, X } from 'lucide-react';
import { TrackArtwork } from './TrackArtwork.js';

interface FloatingMiniplayerProps {
  open: boolean;
  onClose: () => void;
  onExpandFullscreen: () => void;
  currentTrack: Track | null;
  playState: PlayState;
  positionMs: number;
  durationMs: number;
  onTogglePlayPause: () => void;
  onNext: () => void;
  onPrevious: () => void;
}

/**
 * Draggable floating Picture-in-Picture style miniplayer card for desktop/web.
 */
export function FloatingMiniplayer({
  open,
  onClose,
  onExpandFullscreen,
  currentTrack,
  playState,
  positionMs,
  durationMs,
  onTogglePlayPause,
  onNext,
  onPrevious,
}: FloatingMiniplayerProps) {
  const isPlaying = playState === 'playing';
  const pct = durationMs > 0 ? Math.min(100, (positionMs / durationMs) * 100) : 0;

  return (
    <AnimatePresence>
      {open && currentTrack && (
        <motion.div
          drag
          dragMomentum={false}
          initial={{ opacity: 0, scale: 0.9, y: 20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.9, y: 20 }}
          className="fixed bottom-24 right-6 z-50 w-72 rounded-2xl glass-panel shadow-2xl overflow-hidden cursor-grab active:cursor-grabbing"
          style={{ boxShadow: '0 20px 45px -10px var(--glow)' }}
          aria-label="Floating Miniplayer"
        >
          <div className="p-3 flex items-center gap-3">
            <TrackArtwork track={currentTrack} className="w-12 h-12 rounded-xl" />
            <div className="min-w-0 flex-1">
              <p className="text-xs font-bold truncate">{currentTrack.title}</p>
              <p className="text-[11px] opacity-65 truncate">
                {currentTrack.artists.map((a) => a.name).join(', ')}
              </p>
            </div>
            <div className="flex items-center gap-1 shrink-0">
              <button
                type="button"
                onClick={onExpandFullscreen}
                className="p-1 rounded-lg hover:bg-white/10"
                aria-label="Perbesar"
              >
                <Maximize2 className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                onClick={onClose}
                className="p-1 rounded-lg hover:bg-white/10"
                aria-label="Tutup miniplayer"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          <div className="px-3 pb-3 flex items-center justify-center gap-4">
            <button
              type="button"
              onClick={onPrevious}
              className="p-1.5 rounded-full hover:bg-white/10"
              aria-label="Sebelumnya"
            >
              <SkipBack className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={onTogglePlayPause}
              className="w-8 h-8 rounded-full flex items-center justify-center"
              style={{ backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }}
              aria-label={isPlaying ? 'Jeda' : 'Putar'}
            >
              {isPlaying ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4 ml-0.5" />}
            </button>
            <button
              type="button"
              onClick={onNext}
              className="p-1.5 rounded-full hover:bg-white/10"
              aria-label="Berikutnya"
            >
              <SkipForward className="w-4 h-4" />
            </button>
          </div>

          <div className="h-1 w-full bg-white/10">
            <div
              className="h-full transition-all duration-200"
              style={{ width: `${pct}%`, backgroundColor: 'var(--primary)' }}
            />
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
