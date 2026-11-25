import type { Track } from '@swara/core';
import { AnimatePresence, motion } from 'framer-motion';
import { BarChart3, Maximize2, Play, Search, Sparkles } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { NavSection } from './types.js';

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
  tracks: Track[];
  onPlayTrack: (track: Track) => void;
  onNavigate: (section: NavSection) => void;
  onOpenFullscreen: () => void;
}

export function CommandPalette({
  open,
  onClose,
  tracks,
  onPlayTrack,
  onNavigate,
  onOpenFullscreen,
}: CommandPaletteProps) {
  const [query, setQuery] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) {
      setQuery('');
      requestAnimationFrame(() => inputRef.current?.focus());
    }
  }, [open]);

  const matchingTracks = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return tracks.slice(0, 6);
    return tracks
      .filter(
        (t) =>
          t.title.toLowerCase().includes(q) ||
          t.artists.some((a) => a.name.toLowerCase().includes(q)) ||
          (t.localPath?.toLowerCase().includes(q) ?? false)
      )
      .slice(0, 8);
  }, [query, tracks]);

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          className="fixed inset-0 z-50 flex items-start justify-center pt-20 px-4 bg-black/60 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-label="Global Command Palette"
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.96, y: -12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: -12 }}
            transition={{ duration: 0.16 }}
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-xl rounded-3xl glass-panel shadow-2xl overflow-hidden"
          >
            <div className="flex items-center gap-3 px-4 py-3.5 border-b border-white/10">
              <Search className="w-5 h-5 opacity-70" />
              <input
                ref={inputRef}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Escape') onClose();
                }}
                placeholder="Search songs, artists, local files, or actions..."
                className="flex-1 bg-transparent outline-none text-sm"
              />
              <kbd className="px-2 py-0.5 rounded bg-white/10 text-[10px] font-mono">ESC</kbd>
            </div>

            <div className="max-h-80 overflow-y-auto p-2 space-y-1">
              <p className="px-3 py-1 text-[11px] uppercase tracking-wider opacity-50">
                Quick Actions
              </p>
              <button
                type="button"
                onClick={() => {
                  onOpenFullscreen();
                  onClose();
                }}
                className="w-full flex items-center gap-3 px-3 py-2 rounded-2xl text-sm hover:bg-white/10 text-left"
              >
                <Maximize2 className="w-4 h-4 opacity-75" />
                <span>Open Fullscreen Karaoke Lyrics</span>
              </button>
              <button
                type="button"
                onClick={() => {
                  onNavigate('wrapped');
                  onClose();
                }}
                className="w-full flex items-center gap-3 px-3 py-2 rounded-2xl text-sm hover:bg-white/10 text-left"
              >
                <BarChart3 className="w-4 h-4 opacity-75" />
                <span>View Local Wrapped Stats</span>
              </button>

              <p className="px-3 pt-2 pb-1 text-[11px] uppercase tracking-wider opacity-50 flex items-center gap-1.5">
                <Sparkles className="w-3 h-3" />
                Tracks & Local Files
              </p>
              {matchingTracks.map((track) => (
                <button
                  key={`${track.providerId}-${track.id}`}
                  type="button"
                  onClick={() => {
                    onPlayTrack(track);
                    onClose();
                  }}
                  className="w-full flex items-center justify-between gap-3 px-3 py-2 rounded-2xl hover:bg-white/10 text-left"
                >
                  <div className="min-w-0">
                    <p className="text-sm font-medium truncate">{track.title}</p>
                    <p className="text-xs opacity-65 truncate">
                      {track.artists.map((a) => a.name).join(', ')}
                    </p>
                  </div>
                  <Play className="w-4 h-4 shrink-0 opacity-70" />
                </button>
              ))}
              {matchingTracks.length === 0 && (
                <p className="px-3 py-4 text-xs opacity-60 text-center">No matching tracks found.</p>
              )}
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
