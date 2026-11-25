import { describe, expect, it } from 'vitest';
import { BUILTIN_EQ_PRESETS, EQ_FREQUENCIES, parseAutoEQJson } from './eq-presets.js';

describe('eq-presets', () => {
  it('defines 10 standard parametric bands on all built-in presets', () => {
    for (const preset of BUILTIN_EQ_PRESETS) {
      expect(preset.bands).toHaveLength(10);
      expect(preset.bands.map((b) => b.frequencyHz)).toEqual([...EQ_FREQUENCIES]);
    }
  });

  it('parses AutoEQ JSON and clamps extreme gain/preamp values', () => {
    const raw = JSON.stringify({
      name: 'Sony WH-1000XM5',
      preamp: -20,
      filters: [
        { fc: 32, gain: 18, q: 1.4 },
        { fc: 1000, gain: -3.5, q: 2.0 },
      ],
    });

    const preset = parseAutoEQJson(raw);
    expect(preset.name).toBe('Sony WH-1000XM5');
    expect(preset.source).toBe('autoeq');
    expect(preset.preampDb).toBe(-12);
    expect(preset.bands[0]?.gainDb).toBe(12);
    expect(preset.bands[1]?.gainDb).toBe(-3.5);
  });
});
