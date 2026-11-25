import type { EQBand, EqualizerPreset } from '@swara/core';

export const EQ_FREQUENCIES = [32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000] as const;

function makeBands(gains: readonly number[], q = 1.41): EQBand[] {
  return EQ_FREQUENCIES.map((frequencyHz, i) => ({
    frequencyHz,
    gainDb: gains[i] ?? 0,
    q,
  }));
}

export const FLAT_PRESET: EqualizerPreset = {
  id: 'builtin-flat',
  name: 'Flat',
  source: 'builtin',
  preampDb: 0,
  bands: makeBands([0, 0, 0, 0, 0, 0, 0, 0, 0, 0]),
};

export const BASS_BOOST_PRESET: EqualizerPreset = {
  id: 'builtin-bass-boost',
  name: 'Bass Boost',
  source: 'builtin',
  preampDb: -2,
  bands: makeBands([5.5, 4.5, 3.0, 1.5, 0, 0, 0, 0.5, 1.0, 1.0]),
};

export const VOCAL_PRESET: EqualizerPreset = {
  id: 'builtin-vocal',
  name: 'Vocal',
  source: 'builtin',
  preampDb: -1,
  bands: makeBands([-2.0, -1.5, -0.5, 1.0, 2.5, 3.5, 3.0, 1.5, 0.5, 0]),
};

export const BUILTIN_EQ_PRESETS: readonly EqualizerPreset[] = [
  FLAT_PRESET,
  BASS_BOOST_PRESET,
  VOCAL_PRESET,
];

interface RawAutoEQBand {
  frequency?: number;
  fc?: number;
  gain?: number;
  q?: number;
}

interface RawAutoEQPayload {
  name?: string;
  preamp?: number;
  filters?: RawAutoEQBand[];
  bands?: RawAutoEQBand[];
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

/**
 * Parses an AutoEQ parametric JSON profile (either `{ preamp, filters/bands }` or a raw band array)
 * into a Swara `EqualizerPreset` mapped onto the 10-band parametric EQ.
 */
export function parseAutoEQJson(rawJson: string, fallbackName = 'AutoEQ Imported'): EqualizerPreset {
  const parsed = JSON.parse(rawJson) as RawAutoEQPayload | RawAutoEQBand[];
  const rawBands = Array.isArray(parsed) ? parsed : (parsed.filters ?? parsed.bands ?? []);

  if (!Array.isArray(rawBands) || rawBands.length === 0) {
    throw new Error('Invalid AutoEQ JSON: no EQ bands found.');
  }

  const preampDb = !Array.isArray(parsed) && typeof parsed.preamp === 'number'
    ? clamp(parsed.preamp, -12, 12)
    : 0;

  const name = !Array.isArray(parsed) && typeof parsed.name === 'string' && parsed.name.trim()
    ? parsed.name.trim()
    : fallbackName;

  const bands: EQBand[] = rawBands
    .slice(0, 10)
    .map((band, idx) => {
      const freq = band.frequency ?? band.fc ?? EQ_FREQUENCIES[idx] ?? 1000;
      return {
        frequencyHz: clamp(freq, 20, 20000),
        gainDb: clamp(band.gain ?? 0, -12, 12),
        q: clamp(band.q ?? 1.41, 0.1, 18),
      };
    });

  return {
    id: `autoeq-${Date.now()}`,
    name,
    source: 'autoeq',
    preampDb,
    bands,
  };
}
