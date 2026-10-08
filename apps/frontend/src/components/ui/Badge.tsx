import React from 'react';
import { cn } from '../../lib/format';

export type Tone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'info';

const tones: Record<Tone, string> = {
  neutral: 'bg-surface-2 text-fg-muted border-border',
  primary: 'bg-primary-soft text-primary-soft-fg border-primary/25',
  success: 'bg-success-soft text-success border-success/25',
  warning: 'bg-warning-soft text-warning border-warning/30',
  danger: 'bg-danger-soft text-danger border-danger/25',
  info: 'bg-info-soft text-info border-info/25',
};

export function Badge({
  tone = 'neutral',
  className,
  children,
  icon,
  title,
}: {
  tone?: Tone;
  className?: string;
  children: React.ReactNode;
  icon?: React.ReactNode;
  title?: string;
}) {
  return (
    <span
      title={title}
      className={cn(
        'inline-flex max-w-full items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-medium leading-4',
        tones[tone],
        className,
      )}
    >
      {icon}
      <span className="truncate">{children}</span>
    </span>
  );
}

/** Small "AI-generated" label used on every piece of model output. */
export function AiLabel({ children = 'AI-generated' }: { children?: React.ReactNode }) {
  return (
    <Badge tone="primary" className="uppercase tracking-wide">
      {children}
    </Badge>
  );
}
