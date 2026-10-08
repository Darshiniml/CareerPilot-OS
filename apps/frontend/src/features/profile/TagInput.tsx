import { useId, useState, type KeyboardEvent } from 'react';
import { X } from 'lucide-react';

/** Free-text tag list input: Enter or comma adds, Backspace on empty removes the last tag. */
export function TagInput({
  label,
  values,
  onChange,
  placeholder,
  hint,
  max = 20,
}: {
  label: string;
  values: string[];
  onChange: (v: string[]) => void;
  placeholder?: string;
  hint?: string;
  max?: number;
}) {
  const id = useId();
  const [draft, setDraft] = useState('');

  const add = (raw: string) => {
    const v = raw.trim();
    if (!v) return;
    if (values.some((x) => x.toLowerCase() === v.toLowerCase())) {
      setDraft('');
      return;
    }
    if (values.length >= max) return;
    onChange([...values, v]);
    setDraft('');
  };

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      add(draft);
    } else if (e.key === 'Backspace' && !draft && values.length) {
      onChange(values.slice(0, -1));
    }
  };

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-xs font-medium text-fg-muted">
        {label}
      </label>
      <div className="flex min-h-10 flex-wrap items-center gap-1.5 rounded-lg border border-border bg-surface px-2 py-1.5 focus-within:outline-2 focus-within:outline-offset-1 focus-within:outline-ring">
        {values.map((v) => (
          <span key={v} className="inline-flex items-center gap-1 rounded-md bg-primary-soft px-2 py-0.5 text-xs text-primary-soft-fg">
            {v}
            <button
              type="button"
              onClick={() => onChange(values.filter((x) => x !== v))}
              className="rounded hover:text-fg"
              aria-label={`Remove ${v}`}
            >
              <X className="h-3 w-3" />
            </button>
          </span>
        ))}
        <input
          id={id}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKeyDown}
          onBlur={() => add(draft)}
          placeholder={values.length ? '' : placeholder}
          className="min-w-[8rem] flex-1 bg-transparent py-1 text-sm text-fg placeholder:text-fg-subtle focus:outline-none"
        />
      </div>
      <p className="text-xs text-fg-subtle">{hint ?? 'Press Enter or comma to add.'}</p>
    </div>
  );
}
