import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Bell, CheckCheck, LogOut, Menu, Settings, User as UserIcon } from 'lucide-react';
import { useNotifications, qk } from '../../api/queries';
import { authApi, notificationsApi } from '../../api/endpoints';
import { useAuthStore } from '../../store/auth';
import { clearUserState } from '../../store/session';
import { toast } from '../../store/toast';
import { cn, formatRelative, fullName } from '../../lib/format';
import { titleForPath } from './nav';
import { ErrorState, Spinner } from '../ui/Feedback';

function usePopover() {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);
  return { open, setOpen, ref };
}

function NotificationsPopover() {
  const { open, setOpen, ref } = usePopover();
  const q = useNotifications();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const unread = (q.data ?? []).filter((n) => !n.read).length;

  const markRead = useMutation({
    mutationFn: notificationsApi.markRead,
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.notifications }),
    onError: toast.apiError,
  });
  const markAll = useMutation({
    mutationFn: notificationsApi.markAllRead,
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.notifications }),
    onError: toast.apiError,
  });

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen(!open)}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
        className="relative rounded-lg p-2 text-fg-muted hover:bg-surface-2 hover:text-fg"
      >
        <Bell className="h-5 w-5" />
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 min-w-[18px] rounded-full bg-danger px-1 text-center text-[10px] font-semibold leading-[18px] text-bg">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>
      {open && (
        <div
          role="dialog"
          aria-label="Notifications"
          className="absolute right-0 z-40 mt-2 w-[min(92vw,380px)] overflow-hidden rounded-xl border border-border bg-surface shadow-card"
        >
          <div className="flex items-center justify-between border-b border-border px-4 py-3">
            <p className="text-sm font-semibold text-fg">Notifications</p>
            <button
              type="button"
              disabled={unread === 0 || markAll.isPending}
              onClick={() => markAll.mutate()}
              className="inline-flex items-center gap-1 text-xs font-medium text-primary disabled:opacity-40"
            >
              <CheckCheck className="h-3.5 w-3.5" /> Mark all read
            </button>
          </div>
          <div className="max-h-[60vh] overflow-y-auto">
            {q.isLoading && <Spinner className="p-4" label="Loading notifications" />}
            {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} compact className="m-3" />}
            {q.data && q.data.length === 0 && <p className="px-4 py-8 text-center text-sm text-fg-muted">You have no notifications.</p>}
            <ul>
              {(q.data ?? []).map((n) => (
                <li key={n.id} className={cn('border-b border-border last:border-0', !n.read && 'bg-primary-soft/40')}>
                  <button
                    type="button"
                    className="w-full px-4 py-3 text-left hover:bg-surface-2"
                    onClick={() => {
                      if (!n.read) markRead.mutate(n.id);
                      setOpen(false);
                      if (n.applicationId) navigate(`/applications?id=${n.applicationId}`);
                    }}
                  >
                    <div className="flex items-start gap-2">
                      {!n.read && <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-primary" aria-label="Unread" />}
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-fg">{n.title}</p>
                        <p className="mt-0.5 text-xs text-fg-muted">{n.message}</p>
                        <p className="mt-1 text-[11px] text-fg-subtle">{formatRelative(n.createdAt)}</p>
                      </div>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
    </div>
  );
}

function UserMenu() {
  const { open, setOpen, ref } = usePopover();
  const user = useAuthStore((s) => s.user);
  const navigate = useNavigate();
  const name = fullName(user?.firstName, user?.lastName) || user?.email || 'Account';
  const initials = (fullName(user?.firstName, user?.lastName) || user?.email || '?')
    .split(/\s+/)
    .map((p) => p[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  const logout = async () => {
    setOpen(false);
    try {
      await authApi.logout();
    } catch {
      /* the session is cleared locally regardless */
    }
    clearUserState();
    navigate('/login', { replace: true });
  };

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen(!open)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex items-center gap-2 rounded-lg p-1 pr-2 hover:bg-surface-2"
      >
        <span className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-soft text-xs font-semibold text-primary-soft-fg">
          {initials}
        </span>
        <span className="hidden max-w-[160px] truncate text-sm font-medium text-fg sm:block">{name}</span>
      </button>
      {open && (
        <div role="menu" className="absolute right-0 z-40 mt-2 w-56 overflow-hidden rounded-xl border border-border bg-surface py-1 shadow-card">
          <div className="border-b border-border px-4 py-2.5">
            <p className="truncate text-sm font-medium text-fg">{name}</p>
            {user?.email && <p className="truncate text-xs text-fg-muted">{user.email}</p>}
          </div>
          <Link role="menuitem" to="/profile" onClick={() => setOpen(false)} className="flex items-center gap-2 px-4 py-2 text-sm text-fg hover:bg-surface-2">
            <UserIcon className="h-4 w-4" /> Profile
          </Link>
          <Link role="menuitem" to="/settings" onClick={() => setOpen(false)} className="flex items-center gap-2 px-4 py-2 text-sm text-fg hover:bg-surface-2">
            <Settings className="h-4 w-4" /> Settings
          </Link>
          <button role="menuitem" type="button" onClick={logout} className="flex w-full items-center gap-2 px-4 py-2 text-sm text-danger hover:bg-surface-2">
            <LogOut className="h-4 w-4" /> Sign out
          </button>
        </div>
      )}
    </div>
  );
}

export function TopBar({ onOpenMobile }: { onOpenMobile: () => void }) {
  const { pathname } = useLocation();
  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border bg-surface/90 px-4 backdrop-blur sm:px-6">
      <button
        type="button"
        onClick={onOpenMobile}
        className="rounded-lg p-2 text-fg-muted hover:bg-surface-2 hover:text-fg lg:hidden"
        aria-label="Open navigation"
      >
        <Menu className="h-5 w-5" />
      </button>
      <p className="min-w-0 flex-1 truncate text-sm font-semibold text-fg">{titleForPath(pathname)}</p>
      <NotificationsPopover />
      <UserMenu />
    </header>
  );
}
