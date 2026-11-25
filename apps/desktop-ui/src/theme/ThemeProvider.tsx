import { DEFAULT_DARK_PALETTE } from '@swara/ui-tokens';
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import {
  buildPaletteFromSeed,
  extractPaletteFromImage,
  hexToRgb,
  type SwaraThemePalette,
  type ThemeMode,
} from './color-extractor.js';

interface ThemeContextValue {
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
  dynamicCoverColor: boolean;
  setDynamicCoverColor: (enabled: boolean) => void;
  palette: SwaraThemePalette;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export interface ThemeProviderProps {
  artworkUrl?: string | undefined;
  initialMode?: ThemeMode;
  children: ReactNode;
}

const BRAND_DEFAULT_PALETTE: SwaraThemePalette = {
  ...DEFAULT_DARK_PALETTE,
  glow: 'rgba(244, 63, 94, 0.25)',
};

export function ThemeProvider({
  artworkUrl,
  initialMode = 'dark',
  children,
}: ThemeProviderProps) {
  const [mode, setMode] = useState<ThemeMode>(initialMode);
  const [dynamicCoverColor, setDynamicCoverColor] = useState(false);
  const [palette, setPalette] = useState<SwaraThemePalette>(BRAND_DEFAULT_PALETTE);

  useEffect(() => {
    if (!dynamicCoverColor) {
      if (mode === 'dark') {
        setPalette(BRAND_DEFAULT_PALETTE);
      } else {
        setPalette(buildPaletteFromSeed(hexToRgb(DEFAULT_DARK_PALETTE.primary), mode));
      }
      return;
    }

    let cancelled = false;
    void extractPaletteFromImage(artworkUrl, mode).then((next) => {
      if (!cancelled) setPalette(next);
    });
    return () => {
      cancelled = true;
    };
  }, [artworkUrl, mode, dynamicCoverColor]);

  useEffect(() => {
    const root = document.documentElement.style;
    root.setProperty('--surface', palette.surface);
    root.setProperty('--surface-container', palette.surfaceContainer);
    root.setProperty('--primary', palette.primary);
    root.setProperty('--on-primary', palette.onPrimary);
    root.setProperty('--on-surface', palette.onSurface);
    root.setProperty('--accent', palette.accent);
    root.setProperty('--glow', palette.glow);
  }, [palette]);

  return (
    <ThemeContext.Provider
      value={{ mode, setMode, dynamicCoverColor, setDynamicCoverColor, palette }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used inside ThemeProvider');
  return ctx;
}
