import { useEffect, useRef } from 'react';
import { NavLink } from 'react-router-dom';
import { ChevronsLeft, ChevronsRight, Plane, X } from 'lucide-react';
import { NAV } from './nav';
import { cn } from '../../lib/format';

function NavContent({ collapsed, onNavigate }: { collapsed: boolean; onNavigate?: () => void }) {
  return (
    <nav aria-label="Main" className="flex-1 overflow-y-auto px-2 py-3">
      {NAV.map((section) => (
        <div key={section.title} className="mb-4">
          {!collapsed && (
            <p className="px-3 pb-1.5 text-[11px] font-semibold uppercase tracking-wider text-fg-subtle">{section.title}</p>
          )}
          <ul className="space-y-0.5">
            {section.items.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  onClick={onNavigate}
                  title={collapsed ? item.label : undefined}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                      collapsed && 'justify-center px-0',
                      isActive ? 'bg-primary-soft text-primary-soft-fg' : 'text-fg-muted hover:bg-surface-2 hover:text-fg',
                    )
                  }
                >
                  <item.icon className="h-4 w-4 shrink-0" aria-hidden />
                  {collapsed ? <span className="sr-only">{item.label}</span> : <span className="truncate">{item.label}</span>}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );
}

function Brand({ collapsed }: { collapsed: boolean }) {
  return (
    <div className={cn('flex items-center gap-2.5', collapsed && 'justify-center')}>
      <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-fg">
        <Plane className="h-4 w-4" aria-hidden />
      </div>
      {!collapsed && <span className="text-sm font-semibold tracking-tight text-fg">CareerPilot</span>}
    </div>
  );
}

export function Sidebar({
  collapsed,
  onToggleCollapsed,
  mobileOpen,
  onCloseMobile,
}: {
  collapsed: boolean;
  onToggleCollapsed: () => void;
  mobileOpen: boolean;
  onCloseMobile: () => void;
}) {
  const drawerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!mobileOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onCloseMobile();
    document.addEventListener('keydown', onKey);
    drawerRef.current?.querySelector<HTMLElement>('a,button')?.focus();
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prev;
    };
  }, [mobileOpen, onCloseMobile]);

  return (
    <>
      {/* Desktop */}
      <aside
        className={cn(
          'sticky top-0 hidden h-screen shrink-0 flex-col border-r border-border bg-surface lg:flex',
          collapsed ? 'w-[68px]' : 'w-60',
        )}
      >
        <div className="flex h-14 items-center border-b border-border px-4">
          <Brand collapsed={collapsed} />
        </div>
        <NavContent collapsed={collapsed} />
        <div className="border-t border-border p-2">
          <button
            type="button"
            onClick={onToggleCollapsed}
            className="flex w-full items-center justify-center gap-2 rounded-lg px-3 py-2 text-xs text-fg-muted hover:bg-surface-2 hover:text-fg"
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? <ChevronsRight className="h-4 w-4" /> : <ChevronsLeft className="h-4 w-4" />}
            {!collapsed && 'Collapse'}
          </button>
        </div>
      </aside>

      {/* Mobile drawer (overlays content) */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-overlay" aria-hidden onClick={onCloseMobile} />
          <div
            ref={drawerRef}
            role="dialog"
            aria-modal="true"
            aria-label="Navigation"
            className="absolute inset-y-0 left-0 flex w-72 max-w-[85vw] flex-col border-r border-border bg-surface shadow-card"
          >
            <div className="flex h-14 items-center justify-between border-b border-border px-4">
              <Brand collapsed={false} />
              <button
                type="button"
                onClick={onCloseMobile}
                className="rounded-md p-1.5 text-fg-muted hover:bg-surface-2 hover:text-fg"
                aria-label="Close navigation"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <NavContent collapsed={false} onNavigate={onCloseMobile} />
          </div>
        </div>
      )}
    </>
  );
}
