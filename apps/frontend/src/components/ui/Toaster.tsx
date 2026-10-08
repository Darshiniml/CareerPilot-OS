import { CheckCircle2, AlertTriangle, Info, X, XCircle } from 'lucide-react';
import { useToastStore } from '../../store/toast';
import { cn } from '../../lib/format';

const icons = {
  success: <CheckCircle2 className="h-4 w-4 text-success" />,
  error: <XCircle className="h-4 w-4 text-danger" />,
  warning: <AlertTriangle className="h-4 w-4 text-warning" />,
  info: <Info className="h-4 w-4 text-info" />,
};

export function Toaster() {
  const toasts = useToastStore((s) => s.toasts);
  const dismiss = useToastStore((s) => s.dismiss);
  return (
    <div
      aria-live="polite"
      aria-relevant="additions"
      className="pointer-events-none fixed inset-x-0 bottom-0 z-[60] flex flex-col items-center gap-2 p-4 sm:inset-x-auto sm:right-0 sm:items-end"
    >
      {toasts.map((t) => (
        <div
          key={t.id}
          role={t.tone === 'error' ? 'alert' : 'status'}
          className={cn(
            'pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl border border-border bg-surface px-4 py-3 shadow-card',
          )}
        >
          <div className="mt-0.5 shrink-0">{icons[t.tone]}</div>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold text-fg">{t.title}</p>
            {t.description && <p className="mt-0.5 break-words text-xs text-fg-muted">{t.description}</p>}
          </div>
          <button
            type="button"
            onClick={() => dismiss(t.id)}
            className="rounded p-1 text-fg-subtle hover:bg-surface-2 hover:text-fg"
            aria-label="Dismiss notification"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      ))}
    </div>
  );
}
