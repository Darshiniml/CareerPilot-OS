import { useMemo } from 'react';
import { useApplications, useJobIndex } from '../../api/queries';
import type { DraftStatus } from '../../api/types';
import type { Tone } from '../../components/ui/Badge';
import { jobLabel, stateLabel } from '../../lib/domain';

/** Matches the backend placeholder rule: `[Something]` of 2–60 characters. */
export function findPlaceholders(...texts: string[]): string[] {
  const found = new Set<string>();
  for (const t of texts) for (const m of t.match(/\[[^\]]{2,60}\]/g) ?? []) found.add(m);
  return Array.from(found);
}

/** Renders text with placeholders highlighted. */
export function HighlightedText({ text }: { text: string }) {
  const parts = text.split(/(\[[^\]]{2,60}\])/g);
  return (
    <>
      {parts.map((p, i) =>
        i % 2 === 1 ? (
          <mark key={i} className="rounded bg-warning-soft px-0.5 font-medium text-warning">
            {p}
          </mark>
        ) : (
          <span key={i}>{p}</span>
        ),
      )}
    </>
  );
}

export function draftStatusTone(s: DraftStatus | string): Tone {
  switch (s) {
    case 'APPROVED':
      return 'info';
    case 'SENDING':
      return 'primary';
    case 'SENT':
      return 'success';
    case 'FAILED':
      return 'danger';
    default:
      return 'neutral';
  }
}

export const EDITABLE_STATUSES = ['DRAFT', 'APPROVED', 'FAILED'];

/** Local YYYY-MM-DD for today (used for date input max/validation). */
export function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Extracts the bare address from "Name <a@b.com>" (or returns the trimmed input). */
export function bareAddress(v: string | null | undefined): string {
  if (!v) return '';
  const m = v.match(/<([^>]+)>/);
  return (m ? m[1] : v).trim().toLowerCase();
}

/** Labels for applications built from one GET /applications + one GET /discovery/jobs. */
export function useApplicationLabels() {
  const apps = useApplications();
  const jobs = useJobIndex();
  const labels = useMemo(() => {
    const m = new Map<string, string>();
    for (const a of apps.data ?? []) m.set(a.applicationId, jobLabel(jobs.index.get(a.jobId), a.jobId));
    return m;
  }, [apps.data, jobs.index]);
  const options = useMemo(
    () =>
      (apps.data ?? []).map((a) => ({
        value: a.applicationId,
        label: `${labels.get(a.applicationId) ?? a.applicationId} — ${stateLabel(a.workflowState)}`,
      })),
    [apps.data, labels],
  );
  return { apps, labels, options };
}
