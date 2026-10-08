import React from 'react';
import { cn, isNum } from '../../lib/format';
import { EmptyState } from './Feedback';
import { BarChart3 } from 'lucide-react';

export function ProgressBar({
  value,
  max = 100,
  label,
  tone = 'primary',
  className,
}: {
  value: number;
  max?: number;
  label?: string;
  tone?: 'primary' | 'success' | 'warning' | 'danger';
  className?: string;
}) {
  const pct = Math.max(0, Math.min(100, (value / (max || 1)) * 100));
  const color = { primary: 'bg-primary', success: 'bg-success', warning: 'bg-warning', danger: 'bg-danger' }[tone];
  return (
    <div
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={max}
      aria-valuenow={value}
      aria-label={label}
      className={cn('h-2 w-full overflow-hidden rounded-full bg-surface-3', className)}
    >
      <div className={cn('h-full rounded-full transition-[width]', color)} style={{ width: `${pct}%` }} />
    </div>
  );
}

function toneFor(score: number) {
  if (score >= 70) return 'var(--cp-success)';
  if (score >= 45) return 'var(--cp-warning)';
  return 'var(--cp-danger)';
}

/** Circular meter for a 0..100 score. Null renders an explicit "N/A" ring, never a fake number. */
export function ScoreRing({
  score,
  size = 72,
  label,
  emptyText = 'N/A',
}: {
  score: number | null | undefined;
  size?: number;
  label?: string;
  emptyText?: string;
}) {
  const stroke = 7;
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const has = isNum(score);
  const pct = has ? Math.max(0, Math.min(100, score)) : 0;
  return (
    <div className="inline-flex flex-col items-center gap-1">
      <svg
        width={size}
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        role="img"
        aria-label={label ? `${label}: ${has ? Math.round(pct) : 'not available'}` : undefined}
      >
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--cp-surface-3)" strokeWidth={stroke} />
        {has && (
          <circle
            cx={size / 2}
            cy={size / 2}
            r={r}
            fill="none"
            stroke={toneFor(pct)}
            strokeWidth={stroke}
            strokeLinecap="round"
            strokeDasharray={c}
            strokeDashoffset={c - (pct / 100) * c}
            transform={`rotate(-90 ${size / 2} ${size / 2})`}
          />
        )}
        <text
          x="50%"
          y="50%"
          dominantBaseline="central"
          textAnchor="middle"
          fill={has ? 'var(--cp-fg)' : 'var(--cp-fg-subtle)'}
          fontSize={has ? size * 0.28 : size * 0.18}
          fontWeight={600}
        >
          {has ? Math.round(pct) : emptyText}
        </text>
      </svg>
      {label && <span className="text-center text-xs text-fg-muted">{label}</span>}
    </div>
  );
}

/** A labelled metric tile. `value` null → explicit empty text. */
export function Stat({
  label,
  value,
  hint,
  icon,
  emptyText = 'Not enough data',
  className,
}: {
  label: string;
  value: React.ReactNode | null | undefined;
  hint?: React.ReactNode;
  icon?: React.ReactNode;
  emptyText?: string;
  className?: string;
}) {
  const empty = value === null || value === undefined || value === '';
  return (
    <div className={cn('rounded-xl border border-border bg-surface p-4', className)}>
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-medium text-fg-muted">{label}</p>
        {icon && <span className="text-fg-subtle">{icon}</span>}
      </div>
      <p className={cn('mt-2', empty ? 'text-sm italic text-fg-subtle' : 'text-2xl font-semibold text-fg')}>
        {empty ? emptyText : value}
      </p>
      {hint && <p className="mt-1 text-xs text-fg-subtle">{hint}</p>}
    </div>
  );
}

export interface BarDatum {
  label: string;
  value: number;
  hint?: string;
}

/** Accessible horizontal bar chart (pure CSS). Shows an empty state when there is no data. */
export function BarList({
  data,
  emptyTitle = 'No data yet',
  emptyDescription,
  valueFormatter = (v: number) => String(v),
  max,
  tone = 'primary',
  ariaLabel,
}: {
  data: BarDatum[];
  emptyTitle?: string;
  emptyDescription?: React.ReactNode;
  valueFormatter?: (v: number) => string;
  max?: number;
  tone?: 'primary' | 'success' | 'warning' | 'danger';
  ariaLabel?: string;
}) {
  if (!data.length) {
    return <EmptyState compact icon={<BarChart3 className="h-5 w-5" />} title={emptyTitle} description={emptyDescription} />;
  }
  const top = max ?? Math.max(...data.map((d) => d.value), 1);
  const color = { primary: 'bg-primary', success: 'bg-success', warning: 'bg-warning', danger: 'bg-danger' }[tone];
  return (
    <ul className="space-y-2.5" aria-label={ariaLabel}>
      {data.map((d) => (
        <li key={d.label} className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-1">
          <span className="truncate text-sm text-fg" title={d.label}>
            {d.label}
          </span>
          <span className="text-right text-sm font-medium tabular-nums text-fg">{valueFormatter(d.value)}</span>
          <div className="col-span-2 h-2 overflow-hidden rounded-full bg-surface-3" aria-hidden>
            <div className={cn('h-full rounded-full', color)} style={{ width: `${Math.max(2, (d.value / top) * 100)}%` }} />
          </div>
          {d.hint && <span className="col-span-2 text-xs text-fg-subtle">{d.hint}</span>}
        </li>
      ))}
    </ul>
  );
}

/** Definition list of label/value pairs. */
export function KeyValue({ items, className }: { items: Array<{ label: string; value: React.ReactNode }>; className?: string }) {
  return (
    <dl className={cn('grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2', className)}>
      {items.map((i) => (
        <div key={i.label} className="min-w-0">
          <dt className="text-xs text-fg-subtle">{i.label}</dt>
          <dd className="mt-0.5 break-words text-sm text-fg">{i.value}</dd>
        </div>
      ))}
    </dl>
  );
}

/** Bulleted list with an explicit empty text. */
export function BulletList({
  items,
  empty = 'None reported',
  className,
  marker,
}: {
  items: Array<React.ReactNode> | null | undefined;
  empty?: string;
  className?: string;
  marker?: React.ReactNode;
}) {
  if (!items || items.length === 0) return <p className="text-sm italic text-fg-subtle">{empty}</p>;
  return (
    <ul className={cn('space-y-1.5', className)}>
      {items.map((it, i) => (
        <li key={i} className="flex gap-2 text-sm text-fg">
          <span className="mt-1.5 shrink-0 text-fg-subtle" aria-hidden>
            {marker ?? <span className="block h-1.5 w-1.5 rounded-full bg-fg-subtle" />}
          </span>
          <span className="min-w-0 break-words">{it}</span>
        </li>
      ))}
    </ul>
  );
}

export function Chips({ items, empty = 'None', tone }: { items: string[] | null | undefined; empty?: string; tone?: 'success' | 'danger' | 'neutral' | 'primary' }) {
  if (!items || items.length === 0) return <p className="text-sm italic text-fg-subtle">{empty}</p>;
  const cls = {
    success: 'bg-success-soft text-success border-success/25',
    danger: 'bg-danger-soft text-danger border-danger/25',
    primary: 'bg-primary-soft text-primary-soft-fg border-primary/25',
    neutral: 'bg-surface-2 text-fg-muted border-border',
  }[tone ?? 'neutral'];
  return (
    <div className="flex flex-wrap gap-1.5">
      {items.map((s, i) => (
        <span key={`${s}-${i}`} className={cn('rounded-md border px-2 py-0.5 text-xs', cls)}>
          {s}
        </span>
      ))}
    </div>
  );
}
