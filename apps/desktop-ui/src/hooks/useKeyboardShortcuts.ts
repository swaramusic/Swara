import { useEffect } from 'react';

export interface KeyboardShortcutHandlers {
  onTogglePlayPause: () => void;
  onSeekBy: (deltaMs: number) => void;
  onToggleMute: () => void;
  onToggleFullscreen: () => void;
  onToggleCommandPalette: () => void;
}

function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName;
  return tag === 'INPUT' || tag === 'TEXTAREA' || target.isContentEditable;
}

export function useKeyboardShortcuts(handlers: KeyboardShortcutHandlers): void {
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        handlers.onToggleCommandPalette();
        return;
      }

      if (isEditableTarget(e.target)) return;

      switch (e.key.toLowerCase()) {
        case ' ':
          e.preventDefault();
          handlers.onTogglePlayPause();
          break;
        case 'j':
          e.preventDefault();
          handlers.onSeekBy(-10_000);
          break;
        case 'l':
          e.preventDefault();
          handlers.onSeekBy(10_000);
          break;
        case 'arrowleft':
          e.preventDefault();
          handlers.onSeekBy(-5_000);
          break;
        case 'arrowright':
          e.preventDefault();
          handlers.onSeekBy(5_000);
          break;
        case 'm':
          e.preventDefault();
          handlers.onToggleMute();
          break;
        case 'f':
          e.preventDefault();
          handlers.onToggleFullscreen();
          break;
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [handlers]);
}
