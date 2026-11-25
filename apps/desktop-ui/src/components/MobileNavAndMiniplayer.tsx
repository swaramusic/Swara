import type { PlayState, Track } from '@swara/core';
import { motion, type PanInfo } from 'framer-motion';
import {
  Heart,
  Home,
  Library,
  Pause,
  Play,
  Search,
  SkipForward,
  User,
} from 'lucide-react';
import { useRef } from 'react';
import type { UIStrings } from '../i18n/translations.js';
import { TrackArtwork } from './TrackArtwork.js';
import type { NavSection } from './types.js';

interface MobileNavAndMiniplayerProps {
  t: UIStrings;
  activeSection: NavSection;
  onSelectSection: (section: NavSection) => void;
  currentTrack: Track | null;
  playState: PlayState;
  positionMs?: number;
  durationMs?: number;
  isLiked?: boolean;
  onToggleLike?: () => void;
  onTogglePlayPause: () => void;
  onNext: () => void;
  onPrevious: () => void;
  onOpenFullscreen: () => void;
}

export function MobileNavAndMiniplayer({
  t,
  activeSection,
  onSelectSection,
  currentTrack,
  playState,
  positionMs = 0,
  durationMs = 0,
  isLiked = false,
  onToggleLike,
  onTogglePlayPause,
  onNext,
  onPrevious,
  onOpenFullscreen,
}: MobileNavAndMiniplayerProps) {
  const isPlaying = playState === 'playing';
  const didSwipeRef = useRef(false);

  const mobileTabs: Array<{
    id: NavSection;
    label: string;
    icon: typeof Home;
    matches: NavSection[];
  }> = [
    { id: 'home', label: t.nav.home, icon: Home, matches: ['home'] },
    { id: 'search', label: t.nav.search, icon: Search, matches: ['search', 'explore'] },
    {
      id: 'library',
      label: t.nav.library,
      icon: Library,
      matches: ['library', 'playlists', 'liked', 'recent', 'local', 'offline'],
    },
    { id: 'you', label: t.nav.you, icon: User, matches: ['you', 'wrapped', 'settings'] },
  ];

  const progressPct = durationMs > 0 ? Math.min(100, (positionMs / durationMs) * 100) : 0;

  const handleDragStart = () => {
    didSwipeRef.current = true;
  };

  const handleDragEnd = (_: MouseEvent | TouchEvent | PointerEvent, info: PanInfo) => {
    window.setTimeout(() => {
      didSwipeRef.current = false;
    }, 120);

    if (info.offset.y < -45) {
      onOpenFullscreen();
      return;
    }
    if (info.offset.x < -60) {
      onNext();
    } else if (info.offset.x > 60) {
      onPrevious();
    }
  };

  return (
    <div className="md:hidden fixed inset-x-0 bottom-0 z-30 flex flex-col gap-2.5 px-4 pb-3.5 pointer-events-none">
      {currentTrack && (
        <motion.div
          drag
          dragConstraints={{ left: 0, right: 0, top: 0, bottom: 0 }}
          dragElastic={0.35}
          onDragStart={handleDragStart}
          onDragEnd={handleDragEnd}
          onClick={() => {
            if (!didSwipeRef.current) onOpenFullscreen();
          }}
          className="pointer-events-auto relative overflow-hidden flex items-center justify-between gap-2.5 px-3 py-2 rounded-full glass-panel shadow-2xl border border-white/15 backdrop-blur-2xl cursor-grab active:cursor-grabbing"
          aria-label="Swipe left or right to skip, swipe up for fullscreen"
        >
          {/* Thin progress indicator */}
          <div className="absolute inset-x-5 top-0 h-[2px] bg-white/10 rounded-full overflow-hidden">
            <div
              className="h-full transition-[width] duration-200"
              style={{ width: `${progressPct}%`, backgroundColor: 'var(--primary)' }}
            />
          </div>

          <div className="flex items-center gap-2.5 min-w-0 flex-1">
            <TrackArtwork track={currentTrack} className="w-10 h-10 rounded-full shrink-0" />
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold truncate">{currentTrack.title}</p>
              <p className="text-xs opacity-70 truncate">
                {currentTrack.artists.map((a) => a.name).join(', ')}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1 shrink-0">
            {onToggleLike && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onToggleLike();
                }}
                className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-white/10"
                aria-label={isLiked ? 'Hapus suka' : 'Sukai lagu'}
              >
                <Heart
                  className="w-4 h-4"
                  style={isLiked ? { color: 'var(--primary)', fill: 'var(--primary)' } : { opacity: 0.8 }}
                />
              </button>
            )}
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onTogglePlayPause();
              }}
              className="w-9 h-9 rounded-full flex items-center justify-center shrink-0"
              style={{ backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }}
              aria-label={isPlaying ? 'Pause' : 'Play'}
            >
              {isPlaying ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4 ml-0.5" />}
            </button>
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onNext();
              }}
              className="w-9 h-9 rounded-full flex items-center justify-center hover:bg-white/10"
              aria-label="Next track"
            >
              <SkipForward className="w-4 h-4" />
            </button>
          </div>
        </motion.div>
      )}

      <nav
        className="pointer-events-auto flex items-center justify-around gap-1 p-1.5 rounded-full glass-panel shadow-2xl border border-white/15 backdrop-blur-2xl"
        aria-label="Mobile Navigation"
      >
        {mobileTabs.map(({ id, label, icon: Icon, matches }) => {
          const active = matches.includes(activeSection);
          return (
            <button
              key={id}
              type="button"
              onClick={() => onSelectSection(id)}
              className="flex-1 flex flex-col items-center justify-center py-1.5 px-2 rounded-full text-[11px] font-medium transition-all"
              style={
                active
                  ? { backgroundColor: 'rgba(255,255,255,0.14)', color: 'var(--on-surface)' }
                  : { color: 'var(--on-surface)', opacity: 0.65 }
              }
            >
              <Icon className="w-5 h-5 mb-0.5" style={active ? { color: 'var(--primary)' } : undefined} />
              <span className="truncate max-w-full">{label}</span>
            </button>
          );
        })}
      </nav>
    </div>
  );
}
