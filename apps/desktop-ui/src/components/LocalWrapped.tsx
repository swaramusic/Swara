import type { PlayHistoryEntry, Track } from '@swara/core';
import { Download, Headphones, ShieldCheck, Sparkles } from 'lucide-react';
import { useMemo, useRef } from 'react';
import { useTheme } from '../theme/ThemeProvider.js';

export interface WrappedStats {
  totalMinutes: number;
  topArtists: Array<{ name: string; minutes: number }>;
  topTracks: Array<{ title: string; artist: string; plays: number }>;
}

export function computeWrappedStats(
  history: readonly PlayHistoryEntry[],
  catalog: readonly Track[]
): WrappedStats {
  const byId = new Map(catalog.map((t) => [`${t.providerId}:${t.id}`, t]));
  const artistMs = new Map<string, number>();
  const trackCounts = new Map<string, { title: string; artist: string; plays: number }>();
  let totalMs = 0;

  for (const entry of history) {
    totalMs += entry.durationListenedMs;
    const track = byId.get(`${entry.providerId}:${entry.trackId}`);
    if (!track) continue;

    const artistName = track.artists.map((a) => a.name).join(', ') || 'Unknown Artist';
    artistMs.set(artistName, (artistMs.get(artistName) ?? 0) + entry.durationListenedMs);

    const key = `${track.providerId}:${track.id}`;
    const prev = trackCounts.get(key);
    trackCounts.set(key, {
      title: track.title,
      artist: artistName,
      plays: (prev?.plays ?? 0) + 1,
    });
  }

  const topArtists = [...artistMs.entries()]
    .map(([name, ms]) => ({ name, minutes: Math.round(ms / 60_000) }))
    .sort((a, b) => b.minutes - a.minutes)
    .slice(0, 5);

  const topTracks = [...trackCounts.values()]
    .sort((a, b) => b.plays - a.plays)
    .slice(0, 5);

  return {
    totalMinutes: Math.round(totalMs / 60_000),
    topArtists,
    topTracks,
  };
}

interface LocalWrappedProps {
  history: PlayHistoryEntry[];
  catalog: Track[];
}

export function LocalWrapped({ history, catalog }: LocalWrappedProps) {
  const { palette } = useTheme();
  const canvasRef = useRef<HTMLCanvasElement>(null);

  const stats = useMemo(() => computeWrappedStats(history, catalog), [history, catalog]);

  const handleDownloadCard = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    canvas.width = 1080;
    canvas.height = 1350;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const grad = ctx.createLinearGradient(0, 0, 1080, 1350);
    grad.addColorStop(0, palette.surface);
    grad.addColorStop(1, palette.surfaceContainer);
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, 1080, 1350);

    // Glow orb
    const orb = ctx.createRadialGradient(860, 220, 40, 860, 220, 520);
    orb.addColorStop(0, palette.primary);
    orb.addColorStop(1, 'transparent');
    ctx.globalAlpha = 0.35;
    ctx.fillStyle = orb;
    ctx.fillRect(0, 0, 1080, 1350);
    ctx.globalAlpha = 1;

    ctx.fillStyle = palette.primary;
    ctx.font = 'bold 34px system-ui, sans-serif';
    ctx.fillText('SWARA LOCAL WRAPPED', 84, 120);

    ctx.fillStyle = palette.onSurface;
    ctx.font = 'bold 112px system-ui, sans-serif';
    ctx.fillText(`${stats.totalMinutes} min`, 84, 270);
    ctx.font = '32px system-ui, sans-serif';
    ctx.globalAlpha = 0.75;
    ctx.fillText('100% private • computed on-device', 84, 330);
    ctx.globalAlpha = 1;

    ctx.fillStyle = palette.accent;
    ctx.font = 'bold 36px system-ui, sans-serif';
    ctx.fillText('TOP ARTISTS', 84, 460);

    ctx.fillStyle = palette.onSurface;
    stats.topArtists.forEach((artist, i) => {
      ctx.font = 'bold 44px system-ui, sans-serif';
      ctx.fillText(`${i + 1}. ${artist.name}`, 84, 535 + i * 74);
    });

    ctx.fillStyle = palette.accent;
    ctx.font = 'bold 36px system-ui, sans-serif';
    ctx.fillText('TOP TRACKS', 84, 960);

    ctx.fillStyle = palette.onSurface;
    stats.topTracks.forEach((track, i) => {
      ctx.font = 'bold 38px system-ui, sans-serif';
      ctx.fillText(`${i + 1}. ${track.title} (${track.plays}x)`, 84, 1030 + i * 64);
    });

    canvas.toBlob((blob) => {
      if (!blob) return;
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'swara-local-wrapped.png';
      a.click();
      URL.revokeObjectURL(url);
    }, 'image/png');
  };

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <span className="inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full bg-white/10 mb-2">
            <ShieldCheck className="w-3.5 h-3.5" />
            Zero-Login • 100% On-Device
          </span>
          <h1 className="text-2xl md:text-3xl font-bold">Local Wrapped</h1>
        </div>
        <button
          type="button"
          onClick={handleDownloadCard}
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-2xl text-sm font-semibold shadow-lg active:scale-95 transition-transform"
          style={{ backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }}
        >
          <Download className="w-4 h-4" />
          Generate Shareable PNG Card
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="p-5 rounded-3xl glass-panel">
          <div className="flex items-center gap-2 text-xs opacity-70 mb-2">
            <Headphones className="w-4 h-4" />
            Total Listening Time
          </div>
          <p className="text-4xl font-bold" style={{ color: 'var(--primary)' }}>
            {stats.totalMinutes} <span className="text-base font-normal opacity-75">min</span>
          </p>
        </div>

        <div className="p-5 rounded-3xl glass-panel">
          <div className="flex items-center gap-2 text-xs opacity-70 mb-3">
            <Sparkles className="w-4 h-4" />
            Top Artists
          </div>
          <ol className="space-y-1.5 text-sm">
            {stats.topArtists.map((a, i) => (
              <li key={a.name} className="flex justify-between">
                <span className="truncate font-medium">
                  {i + 1}. {a.name}
                </span>
                <span className="opacity-65">{a.minutes}m</span>
              </li>
            ))}
            {stats.topArtists.length === 0 && <li className="opacity-60">No listening history yet.</li>}
          </ol>
        </div>

        <div className="p-5 rounded-3xl glass-panel">
          <div className="flex items-center gap-2 text-xs opacity-70 mb-3">
            <Sparkles className="w-4 h-4" />
            Top Tracks
          </div>
          <ol className="space-y-1.5 text-sm">
            {stats.topTracks.map((t, i) => (
              <li key={`${t.title}-${i}`} className="flex justify-between gap-2">
                <span className="truncate font-medium">
                  {i + 1}. {t.title}
                </span>
                <span className="opacity-65 shrink-0">{t.plays}x</span>
              </li>
            ))}
            {stats.topTracks.length === 0 && <li className="opacity-60">No tracks played yet.</li>}
          </ol>
        </div>
      </div>

      <canvas ref={canvasRef} className="hidden" aria-hidden="true" />
    </section>
  );
}
