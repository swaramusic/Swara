import type { LyricsData, Track } from '@swara/core';
import { AnimatePresence, motion } from 'framer-motion';
import { X } from 'lucide-react';
import type { UIStrings } from '../i18n/translations.js';
import { SyncedLyrics } from './SyncedLyrics.js';
import { TrackArtwork } from './TrackArtwork.js';
import type { RightPanelTab } from './types.js';

interface RightSidePanelProps {
  t: UIStrings;
  open: boolean;
  onClose: () => void;
  activeTab: RightPanelTab;
  onSelectTab: (tab: RightPanelTab) => void;
  queue: Track[];
  currentIndex: number;
  recentTracks: Track[];
  autoPlaySimilar: boolean;
  onToggleAutoPlaySimilar: () => void;
  onPlayIndex: (index: number) => void;
  onRemoveFromQueue: (index: number) => void;
  onClearUpcoming: () => void;
  onPlayTrack: (track: Track) => void;
  lyrics: LyricsData | null;
  positionMs: number;
  onSeek: (ms: number) => void;
}

export function RightSidePanel({
  t,
  open,
  onClose,
  activeTab,
  onSelectTab,
  queue,
  currentIndex,
  recentTracks,
  autoPlaySimilar,
  onToggleAutoPlaySimilar,
  onPlayIndex,
  onRemoveFromQueue,
  onClearUpcoming,
  onPlayTrack,
  lyrics,
  positionMs,
  onSeek,
}: RightSidePanelProps) {
  const currentTrack = currentIndex >= 0 ? queue[currentIndex] : undefined;
  const nextTracks = currentIndex >= 0 ? queue.slice(currentIndex + 1) : queue;

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 z-[55] bg-black/40 backdrop-blur-xs"
          />
          <motion.aside
            initial={{ x: 380, opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            exit={{ x: 380, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 320, damping: 32 }}
            className="fixed right-3 top-3 bottom-3 md:bottom-24 z-[60] w-[350px] max-w-[calc(100vw-24px)] flex flex-col rounded-3xl p-5 glass-panel shadow-2xl overflow-hidden"
            aria-label="Queue and Lyrics Drawer"
          >
            {/* Header tabs with underline indicator */}
            <div className="flex items-center justify-between border-b border-white/10 pb-2.5 mb-4">
              <div className="flex items-center gap-5">
                {activeTab === 'lyrics' ? (
                  <button
                    type="button"
                    onClick={() => onSelectTab('lyrics')}
                    className="relative pb-1 text-sm font-bold"
                  >
                    Lirik
                    <span
                      className="absolute left-0 right-0 -bottom-2.5 h-0.5 rounded-full"
                      style={{ backgroundColor: 'var(--primary)' }}
                    />
                  </button>
                ) : (
                  <>
                    <button
                      type="button"
                      onClick={() => onSelectTab('queue')}
                      className={`relative pb-1 text-sm font-bold transition-opacity ${
                        activeTab === 'queue' ? 'opacity-100' : 'opacity-55 hover:opacity-85'
                      }`}
                    >
                      {t.queue}
                      {activeTab === 'queue' && (
                        <span
                          className="absolute left-0 right-0 -bottom-2.5 h-0.5 rounded-full"
                          style={{ backgroundColor: 'var(--primary)' }}
                        />
                      )}
                    </button>
                    <button
                      type="button"
                      onClick={() => onSelectTab('recent')}
                      className={`relative pb-1 text-sm font-bold transition-opacity ${
                        activeTab === 'recent' ? 'opacity-100' : 'opacity-55 hover:opacity-85'
                      }`}
                    >
                      {t.recentlyPlayedTab}
                      {activeTab === 'recent' && (
                        <span
                          className="absolute left-0 right-0 -bottom-2.5 h-0.5 rounded-full"
                          style={{ backgroundColor: 'var(--primary)' }}
                        />
                      )}
                    </button>
                  </>
                )}
              </div>

              <button
                type="button"
                onClick={onClose}
                className="p-1.5 rounded-full hover:bg-white/10 opacity-75 hover:opacity-100"
                aria-label="Tutup panel"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {activeTab === 'lyrics' ? (
              <div className="flex-1 overflow-hidden">
                <SyncedLyrics lyrics={lyrics} positionMs={positionMs} onSeek={onSeek} compact />
              </div>
            ) : activeTab === 'recent' ? (
              <div className="flex-1 overflow-y-auto space-y-1.5 pr-1">
                {recentTracks.length === 0 && (
                  <p className="text-sm opacity-60 text-center py-10">{t.noRecentTracks}</p>
                )}
                {recentTracks.map((track, idx) => (
                  <button
                    key={`recent-${track.providerId}-${track.id}-${idx}`}
                    type="button"
                    onClick={() => onPlayTrack(track)}
                    className="w-full flex items-center gap-3 p-2 rounded-xl text-left hover:bg-white/10 transition-colors"
                  >
                    <TrackArtwork track={track} className="w-10 h-10 rounded-lg" />
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-semibold truncate">{track.title}</p>
                      <p className="text-xs opacity-60 truncate">
                        {track.artists.map((a) => a.name).join(', ')}
                      </p>
                    </div>
                  </button>
                ))}
              </div>
            ) : (
              <div className="flex-1 overflow-y-auto space-y-5 pr-1">
                {currentTrack && (
                  <div className="space-y-2">
                    <p className="text-xs font-bold tracking-wide opacity-80">{t.nowPlaying}</p>
                    <div className="flex items-center gap-3 p-2 rounded-xl bg-white/[0.05]">
                      <TrackArtwork track={currentTrack} className="w-10 h-10 rounded-lg" />
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-bold truncate" style={{ color: 'var(--primary)' }}>
                          {currentTrack.title}
                        </p>
                        <p className="text-xs opacity-65 truncate">
                          {currentTrack.artists.map((a) => a.name).join(', ')}
                        </p>
                      </div>
                    </div>
                  </div>
                )}

                <div className="space-y-2">
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-xs font-bold tracking-wide opacity-80">{t.nextUp}</p>
                    <div className="flex items-center gap-3">
                      {nextTracks.length > 0 && (
                        <button
                          type="button"
                          onClick={onClearUpcoming}
                          className="text-[11px] opacity-65 hover:opacity-100 underline"
                        >
                          Bersihkan
                        </button>
                      )}
                      <label className="inline-flex items-center gap-1.5 text-[11px] opacity-75 cursor-pointer select-none">
                        <span>Autoplay</span>
                        <input
                          type="checkbox"
                          checked={autoPlaySimilar}
                          onChange={onToggleAutoPlaySimilar}
                          className="w-3.5 h-3.5 accent-[var(--primary)]"
                        />
                      </label>
                    </div>
                  </div>
                  {nextTracks.length === 0 ? (
                    <p className="text-xs opacity-55 py-4">{t.noNextTracks}</p>
                  ) : (
                    <div className="space-y-1">
                      {nextTracks.map((track, offset) => {
                        const actualIdx = (currentIndex >= 0 ? currentIndex + 1 : 0) + offset;
                        return (
                          <div
                            key={`${track.providerId}-${track.id}-${actualIdx}`}
                            onClick={() => onPlayIndex(actualIdx)}
                            role="button"
                            tabIndex={0}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter' || e.key === ' ') {
                                e.preventDefault();
                                onPlayIndex(actualIdx);
                              }
                            }}
                            className="group w-full flex items-center gap-3 p-2 rounded-xl text-left hover:bg-white/10 transition-colors cursor-pointer"
                          >
                            <TrackArtwork track={track} className="w-10 h-10 rounded-lg" />
                            <div className="min-w-0 flex-1">
                              <p className="text-sm font-semibold truncate">{track.title}</p>
                              <p className="text-xs opacity-60 truncate">
                                {track.artists.map((a) => a.name).join(', ')}
                              </p>
                            </div>
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                onRemoveFromQueue(actualIdx);
                              }}
                              className="p-2 rounded-full opacity-75 md:opacity-0 md:group-hover:opacity-100 hover:bg-white/15 transition-opacity shrink-0"
                              aria-label={`Keluarkan ${track.title} dari antrean`}
                              title="Keluarkan dari antrean"
                            >
                              <X className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>
            )}
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  );
}
