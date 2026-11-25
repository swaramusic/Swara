import { describe, expect, it } from 'vitest';
import { buildPaletteFromSeed, contrastRatio } from './color-extractor.js';

describe('color-extractor WCAG compliance', () => {
  it('guarantees >= 4.5:1 contrast for onSurface and onPrimary across all theme modes', () => {
    const seeds = [
      { r: 255, g: 240, b: 120 },
      { r: 20, g: 30, b: 180 },
      { r: 220, g: 40, b: 90 },
    ];

    for (const seed of seeds) {
      for (const mode of ['light', 'dark', 'amoled', 'glass'] as const) {
        const palette = buildPaletteFromSeed(seed, mode);
        expect(contrastRatio(palette.surface, palette.onSurface)).toBeGreaterThanOrEqual(4.5);
        expect(contrastRatio(palette.primary, palette.onPrimary)).toBeGreaterThanOrEqual(4.5);
      }
    }
  });
});
