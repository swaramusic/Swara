import { DEFAULT_DARK_PALETTE, type DynamicColorPalette } from '@swara/ui-tokens';

export type ThemeMode = 'light' | 'dark' | 'amoled' | 'glass';

export interface SwaraThemePalette extends DynamicColorPalette {
  glow: string;
}

export interface RGB {
  r: number;
  g: number;
  b: number;
}

function clampByte(n: number): number {
  return Math.max(0, Math.min(255, Math.round(n)));
}

export function rgbToHex({ r, g, b }: RGB): string {
  return (
    '#' +
    [clampByte(r), clampByte(g), clampByte(b)]
      .map((v) => v.toString(16).padStart(2, '0'))
      .join('')
  );
}

export function hexToRgb(hex: string): RGB {
  const clean = hex.replace('#', '');
  const full = clean.length === 3
    ? clean.split('').map((c) => c + c).join('')
    : clean;
  const num = Number.parseInt(full, 16);
  return {
    r: (num >> 16) & 255,
    g: (num >> 8) & 255,
    b: num & 255,
  };
}

function linearizeChannel(channel255: number): number {
  const s = channel255 / 255;
  return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
}

export function relativeLuminance(rgb: RGB): number {
  return (
    0.2126 * linearizeChannel(rgb.r) +
    0.7152 * linearizeChannel(rgb.g) +
    0.0722 * linearizeChannel(rgb.b)
  );
}

/**
 * WCAG 2.1 contrast ratio between two hex colors (1..21).
 */
export function contrastRatio(hexA: string, hexB: string): number {
  const l1 = relativeLuminance(hexToRgb(hexA));
  const l2 = relativeLuminance(hexToRgb(hexB));
  const bright = Math.max(l1, l2);
  const dark = Math.min(l1, l2);
  return (bright + 0.05) / (dark + 0.05);
}

export function pickAccessibleText(bgHex: string, lightText = '#F7F2FA', darkText = '#141218'): string {
  const lightRatio = contrastRatio(bgHex, lightText);
  const darkRatio = contrastRatio(bgHex, darkText);
  if (Math.max(lightRatio, darkRatio) >= 4.5) {
    return lightRatio >= darkRatio ? lightText : darkText;
  }
  return contrastRatio(bgHex, '#FFFFFF') >= contrastRatio(bgHex, '#000000')
    ? '#FFFFFF'
    : '#000000';
}

function mixRgb(a: RGB, b: RGB, weightB: number): RGB {
  const wA = 1 - weightB;
  return {
    r: a.r * wA + b.r * weightB,
    g: a.g * wA + b.g * weightB,
    b: a.b * wA + b.b * weightB,
  };
}

/**
 * Generates an accessible Material 3 Expressive palette from a seed RGB color.
 */
export function buildPaletteFromSeed(seed: RGB, mode: ThemeMode): SwaraThemePalette {
  const white: RGB = { r: 255, g: 255, b: 255 };
  const black: RGB = { r: 0, g: 0, b: 0 };

  if (mode === 'light') {
    const surface = rgbToHex(mixRgb(white, seed, 0.06));
    const surfaceContainer = rgbToHex(mixRgb(white, seed, 0.13));
    let primary = rgbToHex(mixRgb(seed, black, 0.25));
    if (contrastRatio(primary, surface) < 4.5) {
      primary = rgbToHex(mixRgb(seed, black, 0.48));
    }
    const accent = rgbToHex(mixRgb(seed, { r: 239, g: 184, b: 200 }, 0.35));
    return {
      primary,
      onPrimary: pickAccessibleText(primary),
      surface,
      surfaceContainer,
      onSurface: pickAccessibleText(surface),
      accent,
      glow: `rgba(${clampByte(seed.r)}, ${clampByte(seed.g)}, ${clampByte(seed.b)}, 0.28)`,
    };
  }

  const surface = mode === 'amoled' ? '#000000' : rgbToHex(mixRgb({ r: 9, g: 10, b: 15 }, seed, 0.06));
  const surfaceContainer =
    mode === 'amoled'
      ? '#09090D'
      : rgbToHex(mixRgb({ r: 18, g: 19, b: 26 }, seed, 0.09));

  let primary = rgbToHex(mixRgb(seed, white, 0.22));
  if (contrastRatio(primary, surface) < 4.5) {
    primary = rgbToHex(mixRgb(seed, white, 0.52));
  }

  const accent = rgbToHex(mixRgb(seed, { r: 129, g: 140, b: 248 }, 0.35));
  return {
    primary,
    onPrimary: pickAccessibleText(primary),
    surface,
    surfaceContainer,
    onSurface: pickAccessibleText(surface),
    accent,
    glow: `rgba(${clampByte(seed.r)}, ${clampByte(seed.g)}, ${clampByte(seed.b)}, 0.38)`,
  };
}

/**
 * Quantizes dominant/vibrant color from an album artwork URL using an offscreen 32x32 canvas.
 * Falls back to `DEFAULT_DARK_PALETTE` if CORS or image loading fails.
 */
export async function extractPaletteFromImage(
  imageUrl: string | undefined,
  mode: ThemeMode
): Promise<SwaraThemePalette> {
  if (!imageUrl || typeof document === 'undefined') {
    return buildPaletteFromSeed(hexToRgb(DEFAULT_DARK_PALETTE.primary), mode);
  }

  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const el = new Image();
      el.crossOrigin = 'anonymous';
      el.onload = () => resolve(el);
      el.onerror = () => reject(new Error('Image load failed'));
      el.src = imageUrl;
    });

    const size = 32;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    if (!ctx) throw new Error('No 2D context');

    ctx.drawImage(img, 0, 0, size, size);
    const { data } = ctx.getImageData(0, 0, size, size);

    // Bucket quantized 4-bit-per-channel colors weighted by chroma so vibrant accents win over grey.
    const buckets = new Map<string, { r: number; g: number; b: number; weight: number }>();

    for (let i = 0; i < data.length; i += 4) {
      const a = data[i + 3] ?? 255;
      if (a < 128) continue;
      const r = data[i] ?? 0;
      const g = data[i + 1] ?? 0;
      const b = data[i + 2] ?? 0;

      const max = Math.max(r, g, b);
      const min = Math.min(r, g, b);
      const chroma = max - min;
      const key = `${r >> 4}-${g >> 4}-${b >> 4}`;
      const weight = 1 + chroma / 32;

      const existing = buckets.get(key);
      if (existing) {
        existing.r += r * weight;
        existing.g += g * weight;
        existing.b += b * weight;
        existing.weight += weight;
      } else {
        buckets.set(key, { r: r * weight, g: g * weight, b: b * weight, weight });
      }
    }

    let best: { r: number; g: number; b: number; weight: number } | null = null;
    for (const bucket of buckets.values()) {
      if (!best || bucket.weight > best.weight) best = bucket;
    }

    if (!best) throw new Error('Empty image');
    return buildPaletteFromSeed(
      { r: best.r / best.weight, g: best.g / best.weight, b: best.b / best.weight },
      mode
    );
  } catch {
    return buildPaletteFromSeed(hexToRgb(DEFAULT_DARK_PALETTE.primary), mode);
  }
}
