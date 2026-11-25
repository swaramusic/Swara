import type { EQBand } from '@swara/core';
import { BUILTIN_EQ_PRESETS } from '../audio/eq-presets.js';

interface EqualizerPanelProps {
  activeEqId: string;
  eqBands: EQBand[];
  onSelectEqPreset: (id: string) => void;
  onChangeEqBand: (index: number, gainDb: number) => void;
}

function formatFreq(hz: number): string {
  return hz >= 1000 ? `${hz / 1000}k` : `${hz}`;
}

export function EqualizerPanel({
  activeEqId,
  eqBands,
  onSelectEqPreset,
  onChangeEqBand,
}: EqualizerPanelProps) {
  return (
    <div className="p-5 rounded-3xl bg-white/5 border border-white/10 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold">10-Band Parametric Equalizer</p>
          <p className="text-xs opacity-65">Aktif pada file audio lokal & stream langsung</p>
        </div>
        <div className="flex flex-wrap gap-1.5">
          {BUILTIN_EQ_PRESETS.map((preset) => (
            <button
              key={preset.id}
              type="button"
              onClick={() => onSelectEqPreset(preset.id)}
              className="px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors"
              style={
                activeEqId === preset.id
                  ? { backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }
                  : { backgroundColor: 'rgba(255,255,255,0.07)' }
              }
            >
              {preset.name}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-5 lg:grid-cols-10 gap-3 pt-2">
        {eqBands.map((band, idx) => (
          <div key={band.frequencyHz} className="flex flex-col items-center gap-1.5 min-w-0">
            <span className="text-[10px] font-mono opacity-70">
              {band.gainDb > 0 ? `+${band.gainDb.toFixed(0)}` : band.gainDb.toFixed(0)}dB
            </span>
            <input
              type="range"
              min={-12}
              max={12}
              step={0.5}
              value={band.gainDb}
              onChange={(e) => onChangeEqBand(idx, Number(e.target.value))}
              aria-label={`${band.frequencyHz}Hz gain`}
              className="w-full accent-[var(--primary)] cursor-pointer"
            />
            <span className="text-[10px] font-mono opacity-60">{formatFreq(band.frequencyHz)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
