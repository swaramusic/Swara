import type { AudioQuality, EQBand } from '@swara/core';
import {
  Disc3,
  Download,
  Globe,
  HardDrive,
  Info,
  Mic2,
  Palette,
  Save,
  Shield,
  SlidersHorizontal,
  Sparkles,
  Upload,
} from 'lucide-react';
import { useRef, useState } from 'react';
import {
  DEFAULT_APP_SETTINGS,
  DEFAULT_DYNAMIC_ISLAND_SETTINGS,
  type AppSettings,
  type DynamicIslandSettings,
} from '../config/app-settings.js';
import {
  SUPPORTED_COUNTRIES,
  SUPPORTED_LANGUAGES,
  type AppCountry,
  type AppLanguage,
  type UIStrings,
} from '../i18n/translations.js';
import { useTheme } from '../theme/ThemeProvider.js';
import type { ThemeMode } from '../theme/color-extractor.js';
import { EqualizerPanel } from './EqualizerPanel.js';

interface SettingsViewProps {
  t: UIStrings;
  nativeMode: boolean;
  appSettings: AppSettings;
  onChangeAppSettings: (next: AppSettings) => void;
  language: AppLanguage;
  country: AppCountry;
  onChangeLanguage: (lang: AppLanguage) => void;
  onChangeCountry: (country: AppCountry) => void;
  activeEqId: string;
  eqBands: EQBand[];
  onSelectEqPreset: (id: string) => void;
  onChangeEqBand: (index: number, gainDb: number) => void;
  crossfadeDuration: number;
  onCrossfadeChange: (seconds: number) => void;
  preferredQuality: AudioQuality;
  onQualityChange: (q: AudioQuality) => void;
  historyCount: number;
  offlineCount: number;
  onClearHistory: () => void;
  onClearOffline: () => void;
  onExportBackup: () => void;
  onImportBackup: (file: File) => void;
}

type SettingsTab =
  | 'appearance'
  | 'player'
  | 'lyrics'
  | 'content'
  | 'storage'
  | 'privacy'
  | 'backup'
  | 'island'
  | 'about';

const QUALITIES: Array<{ id: AudioQuality; label: string }> = [
  { id: 'lossless', label: 'Lossless' },
  { id: 'high', label: 'Tinggi' },
  { id: 'normal', label: 'Normal' },
  { id: 'low', label: 'Hemat' },
];

function ToggleRow({
  title,
  desc,
  checked,
  onChange,
}: {
  title: string;
  desc?: string;
  checked: boolean;
  onChange: (next: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between gap-4 py-2.5">
      <div className="min-w-0">
        <p className="text-sm font-medium">{title}</p>
        {desc && <p className="text-xs opacity-60 mt-0.5">{desc}</p>}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className="w-11 h-6 rounded-full p-0.5 transition-colors shrink-0"
        style={{ backgroundColor: checked ? 'var(--primary)' : 'rgba(255,255,255,0.16)' }}
      >
        <span
          className={`block w-5 h-5 rounded-full bg-white shadow transition-transform ${
            checked ? 'translate-x-5' : 'translate-x-0'
          }`}
        />
      </button>
    </div>
  );
}

function PillRow<T extends string | number>({
  title,
  desc,
  options,
  value,
  onChange,
}: {
  title: string;
  desc?: string;
  options: Array<{ id: T; label: string }>;
  value: T;
  onChange: (next: T) => void;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 py-2.5">
      <div className="min-w-0">
        <p className="text-sm font-medium">{title}</p>
        {desc && <p className="text-xs opacity-60 mt-0.5">{desc}</p>}
      </div>
      <div className="flex flex-wrap gap-1.5">
        {options.map((opt) => (
          <button
            key={String(opt.id)}
            type="button"
            onClick={() => onChange(opt.id)}
            className="px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors"
            style={
              value === opt.id
                ? { backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }
                : { backgroundColor: 'rgba(255,255,255,0.08)' }
            }
          >
            {opt.label}
          </button>
        ))}
      </div>
    </div>
  );
}

export function SettingsView({
  t,
  nativeMode,
  appSettings,
  onChangeAppSettings,
  language,
  country,
  onChangeLanguage,
  onChangeCountry,
  activeEqId,
  eqBands,
  onSelectEqPreset,
  onChangeEqBand,
  crossfadeDuration,
  onCrossfadeChange,
  preferredQuality,
  onQualityChange,
  historyCount,
  offlineCount,
  onClearHistory,
  onClearOffline,
  onExportBackup,
  onImportBackup,
}: SettingsViewProps) {
  const { mode, setMode } = useTheme();
  const [tab, setTab] = useState<SettingsTab>('appearance');
  const [clearedNotice, setClearedNotice] = useState(false);
  const backupInputRef = useRef<HTMLInputElement | null>(null);

  const patchSettings = (partial: Partial<AppSettings>) => {
    onChangeAppSettings({ ...appSettings, ...partial });
  };

  const patchIsland = (partial: Partial<DynamicIslandSettings>) => {
    onChangeAppSettings({
      ...appSettings,
      dynamicIsland: { ...appSettings.dynamicIsland, ...partial },
    });
  };

  const tabs: Array<{ id: SettingsTab; label: string; icon: typeof Palette }> = [
    { id: 'appearance', label: t.tabAppearance, icon: Palette },
    { id: 'player', label: t.tabPlayer, icon: SlidersHorizontal },
    { id: 'lyrics', label: t.lyrics, icon: Mic2 },
    { id: 'content', label: t.tabRegion, icon: Globe },
    { id: 'storage', label: t.tabStorage, icon: HardDrive },
    { id: 'privacy', label: 'Privasi', icon: Shield },
    { id: 'backup', label: 'Cadangan', icon: Save },
    { id: 'island', label: 'Dynamic Island', icon: Sparkles },
    { id: 'about', label: 'Tentang', icon: Info },
  ];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl md:text-3xl font-bold">{t.settingsTitle}</h1>

      <div className="flex items-center gap-2 overflow-x-auto md:flex-wrap md:overflow-visible no-scrollbar border-b border-white/10 pb-3">
        {tabs.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            type="button"
            onClick={(e) => {
              setTab(id);
              e.currentTarget.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
            }}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-full text-xs font-semibold whitespace-nowrap transition-colors shrink-0"
            style={
              tab === id
                ? { backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }
                : { backgroundColor: 'rgba(255,255,255,0.06)' }
            }
          >
            <Icon className="w-3.5 h-3.5" />
            {label}
          </button>
        ))}
      </div>

      {tab === 'appearance' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="p-5 rounded-3xl bg-white/5 border border-white/10 divide-y divide-white/10">
            <PillRow<ThemeMode>
              title="Tema Warna"
              desc="Tema adaptif menyesuaikan warna sampul lagu"
              value={mode}
              onChange={setMode}
              options={[
                { id: 'dark', label: 'Gelap' },
                { id: 'amoled', label: 'AMOLED' },
                { id: 'glass', label: 'Glass' },
                { id: 'light', label: 'Terang' },
              ]}
            />
            <PillRow
              title="Ukuran Tampilan"
              value={appSettings.uiScale}
              onChange={(uiScale) => patchSettings({ uiScale })}
              options={[
                { id: 80, label: '80%' },
                { id: 90, label: '90%' },
                { id: 100, label: '100%' },
                { id: 110, label: '110%' },
              ]}
            />
          </div>

          <div className="p-5 rounded-3xl bg-white/5 border border-white/10 divide-y divide-white/10">
            <PillRow
              title="Kepadatan Daftar"
              value={appSettings.listDensity}
              onChange={(listDensity) => patchSettings({ listDensity })}
              options={[
                { id: 'comfortable', label: 'Nyaman' },
                { id: 'compact', label: 'Rapat' },
              ]}
            />
            <PillRow
              title="Sudut Kartu & Sampul"
              value={appSettings.cornerRadius}
              onChange={(cornerRadius) => patchSettings({ cornerRadius })}
              options={[
                { id: 'sharp', label: 'Tajam' },
                { id: 'medium', label: 'Sedang' },
                { id: 'round', label: 'Bulat' },
              ]}
            />
            <ToggleRow
              title="Tampilkan Sapaan di Beranda"
              desc="Mati: judul halaman tetap Beranda"
              checked={appSettings.showGreeting}
              onChange={(showGreeting) => patchSettings({ showGreeting })}
            />
            <ToggleRow
              title="Tampilkan Terakhir Diputar di Beranda"
              checked={appSettings.showRecentOnHome}
              onChange={(showRecentOnHome) => patchSettings({ showRecentOnHome })}
            />
          </div>
        </div>
      )}

      {tab === 'player' && (
        <div className="space-y-4">
          <EqualizerPanel
            activeEqId={activeEqId}
            eqBands={eqBands}
            onSelectEqPreset={onSelectEqPreset}
            onChangeEqBand={onChangeEqBand}
          />

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="p-5 rounded-3xl bg-white/5 border border-white/10 divide-y divide-white/10">
              <ToggleRow
                title="Putar Otomatis Lagu Serupa"
                desc="Tambahkan lagu serupa saat antrean hampir habis"
                checked={appSettings.autoPlaySimilar}
                onChange={(autoPlaySimilar) => patchSettings({ autoPlaySimilar })}
              />
              <ToggleRow
                title="Bilah Progres Bergelombang"
                desc="Mati: garis lurus"
                checked={appSettings.wavyProgressBar}
                onChange={(wavyProgressBar) => patchSettings({ wavyProgressBar })}
              />
            </div>

            <div className="p-5 rounded-3xl bg-white/5 border border-white/10 divide-y divide-white/10">
              <PillRow
                title="Peralihan Lagu (Crossfade)"
                value={crossfadeDuration}
                onChange={onCrossfadeChange}
                options={[
                  { id: 0, label: 'Mati' },
                  { id: 2, label: '2 dtk' },
                  { id: 4, label: '4 dtk' },
                  { id: 6, label: '6 dtk' },
                  { id: 8, label: '8 dtk' },
                  { id: 12, label: '12 dtk' },
                ]}
              />
              <PillRow
                title="Kualitas Audio"
                value={preferredQuality}
                onChange={onQualityChange}
                options={QUALITIES}
              />
            </div>
          </div>
        </div>
      )}

      {tab === 'lyrics' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="p-5 rounded-3xl bg-white/5 border border-white/10 divide-y divide-white/10">
            <PillRow
              title="Ukuran Huruf Lirik"
              value={appSettings.lyricFontSize}
              onChange={(lyricFontSize) => patchSettings({ lyricFontSize })}
              options={[
                { id: 'small', label: 'Kecil' },
                { id: 'medium', label: 'Sedang' },
                { id: 'large', label: 'Besar' },
              ]}
            />
            <ToggleRow
              title="Sapuan Per Kata"
              desc="Sorot kata demi kata jika data karaoke tersedia"
              checked={appSettings.wordByWordSweep}
              onChange={(wordByWordSweep) => patchSettings({ wordByWordSweep })}
            />
          </div>
          <div className="p-5 rounded-3xl bg-white/5 border border-white/10 divide-y divide-white/10">
            <ToggleRow
              title="Kaburkan Baris Lain"
              checked={appSettings.blurInactiveLines}
              onChange={(blurInactiveLines) => patchSettings({ blurInactiveLines })}
            />
            <ToggleRow
              title="Tampilkan Sumber Lirik"
              checked={appSettings.showLyricSource}
              onChange={(showLyricSource) => patchSettings({ showLyricSource })}
            />
          </div>
        </div>
      )}

      {tab === 'content' && (
        <div className="space-y-4">
          <div className="p-5 rounded-3xl bg-white/5 border border-white/10">
            <ToggleRow
              title="Sembunyikan Lagu Eksplisit"
              desc="Saring lagu dengan label eksplisit di hasil pencarian"
              checked={appSettings.hideExplicit}
              onChange={(hideExplicit) => patchSettings({ hideExplicit })}
            />
          </div>

          <div className="p-5 rounded-3xl bg-white/5 border border-white/10 space-y-3">
            <div>
              <p className="text-sm font-semibold">{t.languageLabel}</p>
              <p className="text-xs opacity-65">{t.languageDesc}</p>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2">
              {SUPPORTED_LANGUAGES.map((l) => (
                <button
                  key={l.code}
                  type="button"
                  onClick={() => onChangeLanguage(l.code)}
                  className="px-3.5 py-2.5 rounded-2xl text-xs font-semibold text-left transition-colors"
                  style={
                    language === l.code
                      ? { backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }
                      : { backgroundColor: 'rgba(255,255,255,0.06)' }
                  }
                >
                  {l.label}
                </button>
              ))}
            </div>
          </div>

          <div className="p-5 rounded-3xl bg-white/5 border border-white/10 space-y-3">
            <div>
              <p className="text-sm font-semibold">{t.countryLabel}</p>
              <p className="text-xs opacity-65">{t.countryDesc}</p>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2 max-h-72 overflow-y-auto pr-1">
              {SUPPORTED_COUNTRIES.map((c) => (
                <button
                  key={c.code}
                  type="button"
                  onClick={() => onChangeCountry(c.code)}
                  className="px-3 py-2.5 rounded-2xl text-xs font-semibold text-left transition-colors"
                  style={
                    country === c.code
                      ? { backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }
                      : { backgroundColor: 'rgba(255,255,255,0.06)' }
                  }
                >
                  {c.label}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {tab === 'storage' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="p-5 rounded-3xl bg-white/5 border border-white/10 space-y-3">
            <p className="text-sm font-semibold">Penyimpanan Lokal & Cache</p>
            <p className="text-xs opacity-65">
              Riwayat putar ({historyCount} catatan) disimpan lokal di perangkat ini.
            </p>
            <button
              type="button"
              onClick={() => {
                onClearHistory();
                setClearedNotice(true);
                window.setTimeout(() => setClearedNotice(false), 2000);
              }}
              className="px-4 py-2 rounded-full text-xs font-semibold bg-white/10 hover:bg-white/20 transition-colors"
            >
              {clearedNotice ? 'Riwayat Dibersihkan!' : 'Hapus Riwayat Putar'}
            </button>
          </div>

          {nativeMode && (
            <div className="p-5 rounded-3xl bg-white/5 border border-white/10 space-y-3">
              <p className="text-sm font-semibold">Unduhan Offline ({offlineCount} lagu)</p>
              <p className="text-xs opacity-65">Lagu yang diunduh disimpan untuk pemutaran tanpa internet.</p>
              <button
                type="button"
                onClick={onClearOffline}
                className="px-4 py-2 rounded-full text-xs font-semibold bg-white/10 hover:bg-white/20 transition-colors"
              >
                Hapus Semua Unduhan
              </button>
            </div>
          )}
        </div>
      )}

      {tab === 'privacy' && (
        <div className="p-5 rounded-3xl bg-white/5 border border-white/10 divide-y divide-white/10 max-w-2xl">
          <ToggleRow
            title="Simpan Riwayat Dengar"
            desc="Dipakai untuk Terakhir Diputar dan Statistik Lokal"
            checked={appSettings.savePlayHistory}
            onChange={(savePlayHistory) => patchSettings({ savePlayHistory })}
          />
          <ToggleRow
            title="Simpan Riwayat Pencarian"
            checked={appSettings.saveSearchHistory}
            onChange={(saveSearchHistory) => patchSettings({ saveSearchHistory })}
          />
        </div>
      )}

      {tab === 'backup' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="p-5 rounded-3xl bg-white/5 border border-white/10 space-y-3">
            <p className="text-sm font-semibold">Ekspor Data</p>
            <p className="text-xs opacity-65">
              Simpan playlist, lagu disukai, riwayat putar, dan pengaturan ke satu file JSON.
            </p>
            <button
              type="button"
              onClick={onExportBackup}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-xs font-semibold"
              style={{ backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }}
            >
              <Download className="w-3.5 h-3.5" />
              Ekspor Cadangan
            </button>
          </div>

          <div className="p-5 rounded-3xl bg-white/5 border border-white/10 space-y-3">
            <p className="text-sm font-semibold">Impor Data</p>
            <p className="text-xs opacity-65">
              Pulihkan playlist, lagu disukai, dan pengaturan dari file cadangan JSON.
            </p>
            <input
              ref={backupInputRef}
              type="file"
              accept="application/json,.json"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) onImportBackup(file);
                e.target.value = '';
              }}
            />
            <button
              type="button"
              onClick={() => backupInputRef.current?.click()}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-full text-xs font-semibold bg-white/10 hover:bg-white/20"
            >
              <Upload className="w-3.5 h-3.5" />
              Impor Cadangan
            </button>
          </div>
        </div>
      )}

      {tab === 'island' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="p-5 rounded-3xl bg-white/5 border border-white/10 divide-y divide-white/10">
            <ToggleRow
              title="Dynamic Island"
              desc="Pemutar mini di tepi atas layar untuk melihat lagu & lirik"
              checked={appSettings.dynamicIsland.enabled}
              onChange={(enabled) => patchIsland({ enabled })}
            />
            <PillRow
              title="Gaya Ringkas"
              value={appSettings.dynamicIsland.compactStyle}
              onChange={(compactStyle) => patchIsland({ compactStyle })}
              options={[
                { id: 'compact', label: 'Ringkas' },
                { id: 'lyric', label: 'Satu baris lirik' },
                { id: 'hidden', label: 'Tersembunyi' },
              ]}
            />
            <PillRow
              title="Posisi"
              value={appSettings.dynamicIsland.position}
              onChange={(position) => patchIsland({ position })}
              options={[
                { id: 'attached', label: 'Menempel' },
                { id: 'floating', label: 'Mengambang' },
              ]}
            />
            <PillRow
              title="Ukuran"
              value={appSettings.dynamicIsland.size}
              onChange={(size) => patchIsland({ size })}
              options={[
                { id: 'small', label: 'Kecil' },
                { id: 'medium', label: 'Sedang' },
                { id: 'large', label: 'Besar' },
              ]}
            />
            <PillRow
              title="Bentuk Sampul"
              value={appSettings.dynamicIsland.artworkShape}
              onChange={(artworkShape) => patchIsland({ artworkShape })}
              options={[
                { id: 'square', label: 'Persegi' },
                { id: 'circle', label: 'Lingkaran' },
                { id: 'disc', label: 'Piringan' },
              ]}
            />
            <PillRow
              title="Warna Aksen"
              value={appSettings.dynamicIsland.accentColor}
              onChange={(accentColor) => patchIsland({ accentColor })}
              options={[
                { id: 'theme', label: 'Tema' },
                { id: 'pink', label: 'Merah muda' },
                { id: 'blue', label: 'Biru' },
              ]}
            />
          </div>

          <div className="p-5 rounded-3xl bg-white/5 border border-white/10 divide-y divide-white/10">
            <PillRow
              title="Membuka"
              value={appSettings.dynamicIsland.openTrigger}
              onChange={(openTrigger) => patchIsland({ openTrigger })}
              options={[
                { id: 'hover', label: 'Saat kursor di atas' },
                { id: 'click', label: 'Saat diklik' },
              ]}
            />
            <ToggleRow
              title="Sembunyikan Saat Dijeda"
              checked={appSettings.dynamicIsland.hideWhenPaused}
              onChange={(hideWhenPaused) => patchIsland({ hideWhenPaused })}
            />
            <ToggleRow
              title="Tampilkan Sampul"
              checked={appSettings.dynamicIsland.showArtwork}
              onChange={(showArtwork) => patchIsland({ showArtwork })}
            />
            <ToggleRow
              title="Tampilkan Nama Artis"
              checked={appSettings.dynamicIsland.showArtist}
              onChange={(showArtist) => patchIsland({ showArtist })}
            />
            <ToggleRow
              title="Tampilkan Baris Lirik"
              checked={appSettings.dynamicIsland.showLyricLine}
              onChange={(showLyricLine) => patchIsland({ showLyricLine })}
            />
            <ToggleRow
              title="Tampilkan Bilah Progres & Equalizer"
              checked={appSettings.dynamicIsland.showProgressBar}
              onChange={(showProgressBar) => patchIsland({ showProgressBar, showEqualizer: showProgressBar })}
            />
            <div className="pt-3 flex justify-end">
              <button
                type="button"
                onClick={() => patchIsland(DEFAULT_DYNAMIC_ISLAND_SETTINGS)}
                className="px-4 py-2 rounded-full text-xs font-semibold bg-white/10 hover:bg-white/20"
              >
                Atur Ulang Dynamic Island
              </button>
            </div>
          </div>
        </div>
      )}

      {tab === 'about' && (
        <div className="p-6 rounded-3xl bg-white/5 border border-white/10 max-w-2xl space-y-4">
          <div className="flex items-center gap-4">
            <div
              className="w-12 h-12 rounded-2xl flex items-center justify-center"
              style={{ backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }}
            >
              <Disc3 className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-lg font-bold">Swara Music Player</h2>
              <p className="text-xs opacity-65">
                {nativeMode ? 'Aplikasi Native (Desktop/Mobile)' : 'Versi Web Browser'} • Universal Music Player
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => onChangeAppSettings(DEFAULT_APP_SETTINGS)}
            className="px-4 py-2 rounded-full text-xs font-semibold bg-white/10 hover:bg-white/20"
          >
            Reset Semua Pengaturan
          </button>
        </div>
      )}
    </div>
  );
}
