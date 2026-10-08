import React, { useId, useRef } from 'react';
import { cn } from '../../lib/format';

export interface TabItem<T extends string> {
  id: T;
  label: React.ReactNode;
  count?: number | null;
}

/** Accessible tab list (arrow keys move between tabs). Render the panel yourself based on `value`. */
export function Tabs<T extends string>({
  items,
  value,
  onChange,
  className,
  ariaLabel,
}: {
  items: TabItem<T>[];
  value: T;
  onChange: (v: T) => void;
  className?: string;
  ariaLabel?: string;
}) {
  const refs = useRef<Array<HTMLButtonElement | null>>([]);
  const baseId = useId();
  const onKeyDown = (e: React.KeyboardEvent, idx: number) => {
    let next = -1;
    if (e.key === 'ArrowRight') next = (idx + 1) % items.length;
    if (e.key === 'ArrowLeft') next = (idx - 1 + items.length) % items.length;
    if (e.key === 'Home') next = 0;
    if (e.key === 'End') next = items.length - 1;
    if (next >= 0) {
      e.preventDefault();
      refs.current[next]?.focus();
      onChange(items[next].id);
    }
  };
  return (
    <div
      role="tablist"
      aria-label={ariaLabel}
      className={cn('flex max-w-full gap-1 overflow-x-auto border-b border-border', className)}
    >
      {items.map((t, i) => {
        const selected = t.id === value;
        return (
          <button
            key={t.id}
            ref={(el) => {
              refs.current[i] = el;
            }}
            id={`${baseId}-${t.id}`}
            role="tab"
            type="button"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onKeyDown={(e) => onKeyDown(e, i)}
            onClick={() => onChange(t.id)}
            className={cn(
              '-mb-px flex shrink-0 items-center gap-1.5 border-b-2 px-3 py-2 text-sm font-medium transition-colors',
              selected ? 'border-primary text-fg' : 'border-transparent text-fg-muted hover:text-fg',
            )}
          >
            {t.label}
            {typeof t.count === 'number' && (
              <span className="rounded-full bg-surface-2 px-1.5 text-[11px] text-fg-muted">{t.count}</span>
            )}
          </button>
        );
      })}
    </div>
  );
}
