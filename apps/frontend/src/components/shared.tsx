import { useMemo, useState } from 'react';
import { ShieldAlert, ShieldCheck } from 'lucide-react';
import { useJobIndex } from '../api/queries';
import type { GroundingVerification } from '../api/types';
import { Input, Select } from './ui/Field';
import { Notice, Spinner, ErrorState } from './ui/Feedback';
import { Chips } from './ui/Data';
import { parseJson } from '../lib/format';

/**
 * Pick one of the discovered jobs (single GET /discovery/jobs, cached). Includes a text filter so the
 * list stays usable with many jobs.
 */
export function JobPicker({
  value,
  onChange,
  label = 'Job',
  required,
  hint,
}: {
  value: string;
  onChange: (jobId: string) => void;
  label?: string;
  required?: boolean;
  hint?: string;
}) {
  const { data, isLoading, isError, error, refetch } = useJobIndex();
  const [filter, setFilter] = useState('');
  const options = useMemo(() => {
    const f = filter.trim().toLowerCase();
    return (data ?? [])
      .filter((j) => !f || `${j.title ?? ''} ${j.company ?? ''} ${j.location ?? ''}`.toLowerCase().includes(f) || j.id === value)
      .sort((a, b) => (b.discoveredAt ?? '').localeCompare(a.discoveredAt ?? ''))
      .slice(0, 300)
      .map((j) => ({
        value: j.id,
        label: `${j.title ?? 'Untitled job'}${j.company ? ` · ${j.company}` : ''}${j.location ? ` (${j.location})` : ''}`,
      }));
  }, [data, filter, value]);

  if (isLoading) return <Spinner label="Loading jobs…" />;
  if (isError) return <ErrorState error={error} onRetry={() => refetch()} compact />;
  if (!data || data.length === 0) {
    return (
      <Notice tone="neutral" title="No jobs discovered yet">
        Jobs appear here once a connector has discovered them, or after you paste a job on the Jobs page.
      </Notice>
    );
  }
  return (
    <div className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
      <Input label="Filter jobs" placeholder="Title, company, location" value={filter} onChange={(e) => setFilter(e.target.value)} />
      <Select
        label={label}
        required={required}
        hint={hint}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Select a job…"
        options={options}
      />
    </div>
  );
}

/**
 * Shows the grounding verification of AI-generated text. Unsupported numbers/technologies are claims
 * the model produced that could not be found in your data → the user must review them.
 */
export function VerificationNotice({ verification }: { verification: GroundingVerification | string | null | undefined }) {
  const v = typeof verification === 'string' ? parseJson<GroundingVerification>(verification) : verification ?? null;
  if (!v) {
    return (
      <Notice tone="neutral" title="Verification not available">
        No verification result was returned for this text. Review it carefully before using it.
      </Notice>
    );
  }
  const numbers = v.unsupportedNumbers ?? [];
  const tech = v.unsupportedTechnologies ?? [];
  const flagged = numbers.length > 0 || tech.length > 0 || v.status === 'NEEDS_REVIEW';
  if (!flagged) {
    return (
      <Notice tone="success" icon={<ShieldCheck className="h-4 w-4" />} title="Verification passed">
        Every number and technology mentioned was found in your resume, profile or the job posting.
      </Notice>
    );
  }
  return (
    <Notice tone="warning" icon={<ShieldAlert className="h-4 w-4" />} title="Review these claims">
      <p className="mb-2">The AI mentioned things that could not be found in your data. Remove or correct them before using this text.</p>
      {numbers.length > 0 && (
        <div className="mb-2">
          <p className="mb-1 text-xs font-medium text-fg">Unsupported numbers</p>
          <Chips items={numbers} tone="danger" />
        </div>
      )}
      {tech.length > 0 && (
        <div>
          <p className="mb-1 text-xs font-medium text-fg">Unsupported technologies</p>
          <Chips items={tech} tone="danger" />
        </div>
      )}
    </Notice>
  );
}
