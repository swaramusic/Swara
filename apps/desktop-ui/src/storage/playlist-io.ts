import type { Playlist, Track } from '@swara/core';

export type PlaylistFormat = 'm3u' | 'csv' | 'json';

function escapeCsvCell(value: string): string {
  if (/[",\n\r]/.test(value)) {
    return `"${value.replace(/"/g, '""')}"`;
  }
  return value;
}

function parseCsvRow(line: string): string[] {
  const cells: string[] = [];
  let current = '';
  let inQuotes = false;

  for (let i = 0; i < line.length; i++) {
    const ch = line[i]!;
    if (inQuotes) {
      if (ch === '"' && line[i + 1] === '"') {
        current += '"';
        i++;
      } else if (ch === '"') {
        inQuotes = false;
      } else {
        current += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === ',') {
      cells.push(current);
      current = '';
    } else {
      current += ch;
    }
  }
  cells.push(current);
  return cells;
}

export function exportPlaylist(playlist: Playlist, format: PlaylistFormat): string {
  if (format === 'json') {
    return JSON.stringify(playlist, null, 2);
  }

  if (format === 'm3u') {
    const lines = ['#EXTM3U', `#PLAYLIST:${playlist.title}`];
    for (const track of playlist.tracks) {
      const seconds = Math.round(track.durationMs / 1000);
      const artist = track.artists.map((a) => a.name).join(', ') || 'Unknown Artist';
      lines.push(`#EXTINF:${seconds},${artist} - ${track.title}`);
      lines.push(track.localPath ?? `swara://${track.providerId}/${track.id}`);
    }
    return lines.join('\n');
  }

  // CSV
  const header = 'id,providerId,title,artists,album,durationMs,localPath';
  const rows = playlist.tracks.map((t) =>
    [
      t.id,
      t.providerId,
      t.title,
      t.artists.map((a) => a.name).join('; '),
      t.album?.title ?? '',
      String(t.durationMs),
      t.localPath ?? '',
    ]
      .map(escapeCsvCell)
      .join(',')
  );
  return [header, ...rows].join('\n');
}

export function importPlaylist(
  content: string,
  format: PlaylistFormat,
  fallbackTitle = 'Imported Playlist'
): Playlist {
  const now = Date.now();

  if (format === 'json') {
    const parsed = JSON.parse(content) as Partial<Playlist>;
    return {
      id: parsed.id ?? `pl-${now}`,
      providerId: parsed.providerId ?? 'local',
      title: parsed.title ?? fallbackTitle,
      ...(parsed.description ? { description: parsed.description } : {}),
      tracks: Array.isArray(parsed.tracks) ? parsed.tracks : [],
      createdAtMs: parsed.createdAtMs ?? now,
      updatedAtMs: now,
    };
  }

  if (format === 'm3u') {
    const lines = content.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    let title = fallbackTitle;
    let pendingMeta: { durationSec: number; artist: string; title: string } | null = null;
    const tracks: Track[] = [];

    for (const line of lines) {
      if (line.startsWith('#PLAYLIST:')) {
        title = line.slice('#PLAYLIST:'.length).trim() || fallbackTitle;
      } else if (line.startsWith('#EXTINF:')) {
        const commaIdx = line.indexOf(',');
        const dur = Number.parseInt(line.slice(8, commaIdx > -1 ? commaIdx : undefined), 10) || 0;
        const label = commaIdx > -1 ? line.slice(commaIdx + 1).trim() : 'Unknown';
        const dashIdx = label.indexOf(' - ');
        pendingMeta = {
          durationSec: dur,
          artist: dashIdx > -1 ? label.slice(0, dashIdx).trim() : 'Unknown Artist',
          title: dashIdx > -1 ? label.slice(dashIdx + 3).trim() : label,
        };
      } else if (!line.startsWith('#')) {
        const idx = tracks.length;
        tracks.push({
          id: `m3u-${now}-${idx}`,
          providerId: 'local',
          title: pendingMeta?.title ?? line,
          artists: [{ id: `a-${idx}`, providerId: 'local', name: pendingMeta?.artist ?? 'Unknown Artist' }],
          durationMs: (pendingMeta?.durationSec ?? 0) * 1000,
          localPath: line,
        });
        pendingMeta = null;
      }
    }

    return {
      id: `pl-${now}`,
      providerId: 'local',
      title,
      tracks,
      createdAtMs: now,
      updatedAtMs: now,
    };
  }

  // CSV
  const lines = content.split(/\r?\n/).filter((l) => l.trim().length > 0);
  const dataLines = lines[0]?.toLowerCase().startsWith('id,') ? lines.slice(1) : lines;
  const tracks: Track[] = dataLines.map((line, idx) => {
    const [id, providerId, title, artistsRaw, albumTitle, durationMsRaw, localPath] = parseCsvRow(line);
    const pid = providerId || 'local';
    const artists = (artistsRaw || 'Unknown Artist')
      .split(';')
      .map((name, aIdx) => ({ id: `a-${idx}-${aIdx}`, providerId: pid, name: name.trim() }));

    return {
      id: id || `csv-${now}-${idx}`,
      providerId: pid,
      title: title || 'Untitled',
      artists,
      ...(albumTitle ? { album: { id: `alb-${idx}`, providerId: pid, title: albumTitle } } : {}),
      durationMs: Number.parseInt(durationMsRaw ?? '0', 10) || 0,
      ...(localPath ? { localPath } : {}),
    };
  });

  return {
    id: `pl-${now}`,
    providerId: 'local',
    title: fallbackTitle,
    tracks,
    createdAtMs: now,
    updatedAtMs: now,
  };
}
