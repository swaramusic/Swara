import type { LyricsData } from '@swara/core';
import { useEffect, useMemo, useRef } from 'react';

interface SyncedLyricsProps {
  lyrics: LyricsData | null;
  positionMs: number;
  offsetMs?: number;
  onSeek: (ms: number) => void;
  compact?: boolean;
  emptyText?: string;
}

export function SyncedLyrics({
  lyrics,
  positionMs,
  offsetMs = 0,
  onSeek,
  compact = false,
  emptyText = 'No lyrics available for this track.',
}: SyncedLyricsProps) {
  const activeRef = useRef<HTMLButtonElement | null>(null);

  const effectivePositionMs = positionMs + offsetMs;

  const activeIndex = useMemo(() => {
    if (!lyrics?.synced || lyrics.lines.length === 0) return -1;
    let idx = -1;
    for (let i = 0; i < lyrics.lines.length; i++) {
      if (effectivePositionMs >= lyrics.lines[i]!.startMs) {
        idx = i;
      } else {
        break;
      }
    }
    return idx;
  }, [lyrics, effectivePositionMs]);

  useEffect(() => {
    activeRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }, [activeIndex]);

  if (!lyrics || lyrics.lines.length === 0) {
    return (
      <div className="h-full flex items-center justify-center text-sm opacity-60 text-center px-4">
        {emptyText}
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full min-h-0">
      <div className="flex-1 min-h-0 overflow-y-auto space-y-4 pr-2 py-4">
        {lyrics.lines.map((line, idx) => {
          const isActive = idx === activeIndex;
          return (
            <button
              key={`${line.startMs}-${idx}`}
              ref={isActive ? activeRef : undefined}
              type="button"
              onClick={() => {
                if (lyrics.synced) onSeek(line.startMs);
              }}
              className={`w-full text-left transition-all duration-300 rounded-2xl px-3 py-1.5 hover:bg-white/5 ${
                isActive
                  ? 'opacity-100 scale-[1.01]'
                  : lyrics.synced
                    ? 'opacity-40 blur-[1.2px] hover:blur-0 hover:opacity-80'
                    : 'opacity-85'
              }`}
            >
              <p className={compact ? 'text-base font-bold leading-snug' : 'text-2xl md:text-3xl font-bold leading-tight'}>
                {line.words && isActive
                  ? line.words.map((w, wIdx) => {
                      const spoken = effectivePositionMs >= w.startMs;
                      return (
                        <span
                          key={`${w.startMs}-${wIdx}`}
                          style={{
                            color: spoken ? 'var(--primary)' : 'var(--on-surface)',
                            transition: 'color 120ms linear',
                          }}
                        >
                          {w.text}{' '}
                        </span>
                      );
                    })
                  : line.text}
              </p>

              {line.romanization && (
                <p className="text-xs md:text-sm opacity-75 mt-1 font-medium">
                  {line.romanization}
                </p>
              )}

              {line.translation && (
                <p className="text-xs md:text-sm opacity-65 mt-0.5 italic">
                  {line.translation}
                </p>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
