import React, { useEffect, useState } from 'react';
import { AlertTriangle, Inbox, Loader2, Lock, RefreshCw, Sparkles, Info } from 'lucide-react';
import { ApiError, AI_UNAVAILABLE_CODES, friendlyDetail, friendlyTitle, toApiError } from '../../api/errors';
import { cn } from '../../lib/format';
import { Button } from './Button';

export function Spinner({ className, label }: { className?: string; label?: string }) {
  return (
    <span role="status" className={cn('inline-flex items-center gap-2 text-sm text-fg-muted', className)}>
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
      {label ?? <span className="sr-only">Loading</span>}
    </span>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('cp-skeleton rounded-md bg-surface-3', className)} aria-hidden />;
}

export function SkeletonRows({ rows = 3, className }: { rows?: number; className?: string }) {
  return (
    <div className={cn('space-y-3', className)} role="status" aria-label="Loading">
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-14 w-full" />
      ))}
    </div>
  );
}

export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
  compact,
}: {
  icon?: React.ReactNode;
  title: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
  compact?: boolean;
}) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center rounded-xl border border-dashed border-border text-center',
        compact ? 'px-4 py-6' : 'px-6 py-12',
        className,
      )}
    >
      <div className="mb-3 rounded-full bg-surface-2 p-3 text-fg-subtle">{icon ?? <Inbox className="h-5 w-5" />}</div>
      <h3 className="text-sm font-semibold text-fg">{title}</h3>
      {description && <p className="mt-1 max-w-md text-sm text-fg-muted">{description}</p>}
      {action && <div className="mt-4 flex flex-wrap justify-center gap-2">{action}</div>}
    </div>
  );
}

/** Honest error panel: friendly headline, server message, code and correlation id. */
export function ErrorState({
  error,
  onRetry,
  className,
  compact,
}: {
  error: unknown;
  onRetry?: () => void;
  className?: string;
  compact?: boolean;
}) {
  const err: ApiError = toApiError(error);
  const forbidden = err.code === 'FORBIDDEN' || err.status === 403;
  const ai = AI_UNAVAILABLE_CODES.has(err.code);
  return (
    <div
      role="alert"
      className={cn(
        'rounded-xl border px-4 text-sm',
        compact ? 'py-3' : 'py-4',
        ai ? 'border-warning/30 bg-warning-soft' : 'border-danger/30 bg-danger-soft',
        className,
      )}
    >
      <div className="flex items-start gap-3">
        <div className={cn('mt-0.5', ai ? 'text-warning' : 'text-danger')}>
          {forbidden ? <Lock className="h-4 w-4" /> : <AlertTriangle className="h-4 w-4" />}
        </div>
        <div className="min-w-0 flex-1">
          <p className="font-semibold text-fg">{friendlyTitle(err)}</p>
          <p className="mt-0.5 break-words text-fg-muted">{friendlyDetail(err)}</p>
          {err.details !== null && typeof err.details === 'object' && Object.keys(err.details as object).length > 0 && (
            <ul className="mt-1.5 list-inside list-disc text-xs text-fg-muted">
              {Object.entries(err.details as Record<string, unknown>).map(([field, msg]) => (
                <li key={field}>
                  <span className="font-medium text-fg">{field}</span>: {String(msg)}
                </li>
              ))}
            </ul>
          )}
          <p className="mt-1.5 text-xs text-fg-subtle">
            Code: <span className="font-mono">{err.code}</span>
            {err.status !== null && <> · HTTP {err.status}</>}
            {err.correlationId && (
              <>
                {' '}
                · Ref: <span className="font-mono select-all">{err.correlationId}</span>
              </>
            )}
          </p>
        </div>
        {onRetry && !forbidden && (
          <Button size="sm" variant="secondary" onClick={onRetry} icon={<RefreshCw className="h-3.5 w-3.5" />}>
            Retry
          </Button>
        )}
      </div>
    </div>
  );
}

export function Notice({
  tone = 'info',
  title,
  children,
  className,
  icon,
}: {
  tone?: 'info' | 'warning' | 'success' | 'danger' | 'neutral';
  title?: React.ReactNode;
  children?: React.ReactNode;
  className?: string;
  icon?: React.ReactNode;
}) {
  const styles = {
    info: 'border-info/25 bg-info-soft text-info',
    warning: 'border-warning/30 bg-warning-soft text-warning',
    success: 'border-success/25 bg-success-soft text-success',
    danger: 'border-danger/25 bg-danger-soft text-danger',
    neutral: 'border-border bg-surface-2 text-fg-muted',
  }[tone];
  return (
    <div className={cn('flex items-start gap-3 rounded-lg border px-3.5 py-3 text-sm', styles, className)}>
      <div className="mt-0.5 shrink-0">{icon ?? (tone === 'warning' || tone === 'danger' ? <AlertTriangle className="h-4 w-4" /> : <Info className="h-4 w-4" />)}</div>
      <div className="min-w-0 flex-1">
        {title && <p className="font-semibold text-fg">{title}</p>}
        {children && <div className="text-fg-muted">{children}</div>}
      </div>
    </div>
  );
}

/** In-progress panel for slow AI calls: spinner, elapsed time, honest expectation. */
export function AiProgress({ label, className }: { label: string; className?: string }) {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    const t = window.setInterval(() => setSeconds((s) => s + 1), 1000);
    return () => window.clearInterval(t);
  }, []);
  const mm = Math.floor(seconds / 60);
  const ss = String(seconds % 60).padStart(2, '0');
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn('flex items-start gap-3 rounded-lg border border-primary/25 bg-primary-soft px-4 py-3 text-sm', className)}
    >
      <Loader2 className="mt-0.5 h-4 w-4 shrink-0 animate-spin text-primary" aria-hidden />
      <div className="min-w-0">
        <p className="font-medium text-fg">
          {label} <span className="font-mono text-xs text-fg-muted">{mm}:{ss}</span>
        </p>
        <p className="text-xs text-fg-muted">
          <Sparkles className="mr-1 inline h-3 w-3" aria-hidden />
          This can take a minute or more on a local model. Please keep this page open.
        </p>
      </div>
    </div>
  );
}

/** Renders `children` for a value, or a muted "Not available" text. */
export function Unavailable({ children = 'Not available', className }: { children?: React.ReactNode; className?: string }) {
  return <span className={cn('text-sm italic text-fg-subtle', className)}>{children}</span>;
}
