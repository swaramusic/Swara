import type { Track } from '@swara/core';
import { Music2 } from 'lucide-react';
import { useEffect, useState } from 'react';

interface TrackArtworkProps {
  track: Track | null | undefined;
  className?: string;
}

/**
 * Renders track cover art with automatic fallback from `yt3.googleusercontent.com`
 * to `i.ytimg.com` and finally a clean gradient placeholder (never a broken img icon).
 */
export function TrackArtwork({ track, className = 'w-12 h-12 rounded-xl' }: TrackArtworkProps) {
  const [stage, setStage] = useState<'primary' | 'ytimg' | 'fallback'>('primary');

  useEffect(() => {
    setStage('primary');
  }, [track?.id, track?.artworkUrl]);

  if (!track) {
    return (
      <div className={`${className} bg-white/5 flex items-center justify-center shrink-0`}>
        <Music2 className="w-5 h-5 opacity-40" />
      </div>
    );
  }

  const ytFallback =
    track.providerId === 'youtube' ? `https://i.ytimg.com/vi/${track.id}/hqdefault.jpg` : undefined;

  const src =
    stage === 'primary'
      ? (track.artworkUrl ?? ytFallback)
      : stage === 'ytimg'
        ? ytFallback
        : undefined;

  if (!src || stage === 'fallback') {
    return (
      <div
        className={`${className} flex items-center justify-center shrink-0`}
        style={{
          background: 'linear-gradient(135deg, color-mix(in srgb, var(--primary) 35%, transparent), var(--surface-container))',
        }}
      >
        <Music2 className="w-5 h-5 opacity-80" style={{ color: 'var(--primary)' }} />
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={track.title}
      referrerPolicy="no-referrer"
      onError={() => {
        if (stage === 'primary' && ytFallback && src !== ytFallback) {
          setStage('ytimg');
        } else {
          setStage('fallback');
        }
      }}
      className={`${className} object-cover shrink-0`}
    />
  );
}
