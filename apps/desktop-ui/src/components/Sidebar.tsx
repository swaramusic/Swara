import { motion } from 'framer-motion';
import {
  BarChart3,
  ChevronLeft,
  ChevronRight,
  Compass,
  Disc3,
  Download,
  FolderOpen,
  Heart,
  History,
  Home,
  ListMusic,
  Settings,
} from 'lucide-react';
import type { UIStrings } from '../i18n/translations.js';
import type { NavSection } from './types.js';

interface SidebarProps {
  activeSection: NavSection;
  onSelectSection: (section: NavSection) => void;
  collapsed: boolean;
  onToggleCollapse: () => void;
  likedCount: number;
  nativeMode?: boolean;
  labels: UIStrings['nav'];
}

export function Sidebar({
  activeSection,
  onSelectSection,
  collapsed,
  onToggleCollapse,
  likedCount,
  nativeMode = false,
  labels,
}: SidebarProps) {
  const primaryItems: Array<{ id: NavSection; label: string; icon: typeof Home }> = [
    { id: 'home', label: labels.home, icon: Home },
    { id: 'explore', label: labels.explore, icon: Compass },
  ];

  const collectionItems: Array<{ id: NavSection; label: string; icon: typeof Home }> = [
    { id: 'liked', label: labels.liked, icon: Heart },
    { id: 'recent', label: labels.recent, icon: History },
    { id: 'playlists', label: labels.playlists, icon: ListMusic },
    { id: 'local', label: labels.local, icon: FolderOpen },
    ...(nativeMode ? [{ id: 'offline' as NavSection, label: labels.offline, icon: Download }] : []),
  ];

  const footerItems: Array<{ id: NavSection; label: string; icon: typeof Home }> = [
    { id: 'wrapped', label: labels.stats, icon: BarChart3 },
    { id: 'settings', label: labels.settings, icon: Settings },
  ];
  const renderButton = ({ id, label, icon: Icon }: { id: NavSection; label: string; icon: typeof Home }) => {
    const active = activeSection === id;
    return (
      <button
        key={id}
        type="button"
        onClick={() => onSelectSection(id)}
        className="w-full flex items-center justify-between gap-3 px-3 py-2 rounded-xl text-sm font-medium transition-all"
        style={
          active
            ? { backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }
            : { color: 'var(--on-surface)' }
        }
      >
        <span className="flex items-center gap-3 min-w-0">
          <Icon className="w-4 h-4 shrink-0" />
          {!collapsed && <span className="truncate">{label}</span>}
        </span>
        {!collapsed && id === 'liked' && likedCount > 0 && (
          <span className="text-[11px] px-1.5 py-0.5 rounded-full bg-black/25 font-mono">
            {likedCount}
          </span>
        )}
      </button>
    );
  };

  return (
    <motion.aside
      animate={{ width: collapsed ? 72 : 228 }}
      transition={{ type: 'spring', stiffness: 320, damping: 32 }}
      className="hidden md:flex flex-col shrink-0 h-full rounded-3xl p-3 select-none glass-panel"
      aria-label="Primary Navigation"
    >
      <div className="flex items-center justify-between px-2 py-1.5 mb-4">
        <div className="flex items-center gap-2.5 overflow-hidden">
          <div
            className="w-8 h-8 rounded-xl flex items-center justify-center shrink-0"
            style={{ backgroundColor: 'var(--primary)', color: 'var(--on-primary)' }}
          >
            <Disc3 className="w-4 h-4" />
          </div>
          {!collapsed && <span className="font-bold tracking-tight text-base">Swara</span>}
        </div>
        <button
          type="button"
          onClick={onToggleCollapse}
          className="p-1.5 rounded-xl hover:bg-white/10 transition-colors"
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {collapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
        </button>
      </div>

      <nav className="flex-1 space-y-5 overflow-y-auto no-scrollbar">
        <div className="space-y-1">{primaryItems.map(renderButton)}</div>

        <div className="space-y-1">
          {!collapsed && (
            <p className="px-3 text-[11px] font-semibold uppercase tracking-wider opacity-45 mb-1">
              {labels.collection}
            </p>
          )}
          {collectionItems.map(renderButton)}
        </div>
      </nav>

      <div className="pt-3 border-t border-white/10 space-y-1">
        {footerItems.map(renderButton)}
      </div>
    </motion.aside>
  );
}
