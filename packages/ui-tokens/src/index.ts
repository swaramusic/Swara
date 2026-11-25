export interface DynamicColorPalette {
  primary: string;
  onPrimary: string;
  surface: string;
  surfaceContainer: string;
  onSurface: string;
  accent: string;
}

export const DEFAULT_DARK_PALETTE: DynamicColorPalette = {
  primary: '#F43F5E',
  onPrimary: '#FFFFFF',
  surface: '#090A0F',
  surfaceContainer: '#12131A',
  onSurface: '#F8FAFC',
  accent: '#818CF8',
};

export const GLASS_SURFACES = {
  blurPx: 24,
  borderOpacity: 0.08,
  surfaceOpacity: 0.8,
} as const;
