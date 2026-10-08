import React, { useId } from 'react';
import { cn } from '../../lib/format';

const control =
  'w-full rounded-lg border bg-surface px-3 text-sm text-fg placeholder:text-fg-subtle ' +
  'focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-ring ' +
  'disabled:opacity-60 disabled:cursor-not-allowed';

interface FieldShellProps {
  label?: React.ReactNode;
  hint?: React.ReactNode;
  error?: string | null;
  required?: boolean;
  className?: string;
  children: (ids: { id: string; describedBy: string | undefined; invalid: boolean }) => React.ReactNode;
}

export function FieldShell({ label, hint, error, required, className, children }: FieldShellProps) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errId = error ? `${id}-err` : undefined;
  const describedBy = [hintId, errId].filter(Boolean).join(' ') || undefined;
  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      {label && (
        <label htmlFor={id} className="text-xs font-medium text-fg-muted">
          {label}
          {required && (
            <span className="ml-0.5 text-danger" aria-hidden>
              *
            </span>
          )}
        </label>
      )}
      {children({ id, describedBy, invalid: Boolean(error) })}
      {hint && !error && (
        <p id={hintId} className="text-xs text-fg-subtle">
          {hint}
        </p>
      )}
      {error && (
        <p id={errId} className="text-xs text-danger" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

type InputProps = React.InputHTMLAttributes<HTMLInputElement> & {
  label?: React.ReactNode;
  hint?: React.ReactNode;
  error?: string | null;
  containerClassName?: string;
};

export const Input = React.forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, hint, error, required, containerClassName, className, ...rest },
  ref,
) {
  return (
    <FieldShell label={label} hint={hint} error={error} required={required} className={containerClassName}>
      {({ id, describedBy, invalid }) => (
        <input
          ref={ref}
          id={rest.id ?? id}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          required={required}
          className={cn(control, 'h-10', invalid ? 'border-danger' : 'border-border', className)}
          {...rest}
        />
      )}
    </FieldShell>
  );
});

type TextareaProps = React.TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label?: React.ReactNode;
  hint?: React.ReactNode;
  error?: string | null;
  containerClassName?: string;
};

export const Textarea = React.forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { label, hint, error, required, containerClassName, className, rows = 4, ...rest },
  ref,
) {
  return (
    <FieldShell label={label} hint={hint} error={error} required={required} className={containerClassName}>
      {({ id, describedBy, invalid }) => (
        <textarea
          ref={ref}
          id={rest.id ?? id}
          rows={rows}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          required={required}
          className={cn(control, 'py-2 leading-relaxed', invalid ? 'border-danger' : 'border-border', className)}
          {...rest}
        />
      )}
    </FieldShell>
  );
});

type SelectProps = React.SelectHTMLAttributes<HTMLSelectElement> & {
  label?: React.ReactNode;
  hint?: React.ReactNode;
  error?: string | null;
  containerClassName?: string;
  options: Array<{ value: string; label: string; disabled?: boolean }>;
  placeholder?: string;
};

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, hint, error, required, containerClassName, className, options, placeholder, ...rest },
  ref,
) {
  return (
    <FieldShell label={label} hint={hint} error={error} required={required} className={containerClassName}>
      {({ id, describedBy, invalid }) => (
        <select
          ref={ref}
          id={rest.id ?? id}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          required={required}
          className={cn(control, 'h-10 pr-8', invalid ? 'border-danger' : 'border-border', className)}
          {...rest}
        >
          {placeholder !== undefined && <option value="">{placeholder}</option>}
          {options.map((o) => (
            <option key={o.value} value={o.value} disabled={o.disabled}>
              {o.label}
            </option>
          ))}
        </select>
      )}
    </FieldShell>
  );
});

export function Checkbox({
  label,
  checked,
  onChange,
  disabled,
  description,
}: {
  label: React.ReactNode;
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
  description?: React.ReactNode;
}) {
  const id = useId();
  return (
    <div className="flex items-start gap-2.5">
      <input
        id={id}
        type="checkbox"
        className="mt-0.5 h-4 w-4 cursor-pointer accent-[var(--cp-primary)]"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
      />
      <label htmlFor={id} className="cursor-pointer text-sm text-fg">
        {label}
        {description && <span className="block text-xs text-fg-subtle">{description}</span>}
      </label>
    </div>
  );
}
