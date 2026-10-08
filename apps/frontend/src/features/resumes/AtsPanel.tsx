import { useQuery } from '@tanstack/react-query';
import { resumesApi } from '../../api/endpoints';
import { qk } from '../../api/queries';
import type { ResumeAts } from '../../api/types';
import { AiLabel, BulletList, Chips, EmptyState, ErrorState, Notice, ProgressBar, ScoreRing, SkeletonRows } from '../../components/ui';
import { humanize, isNum } from '../../lib/format';

const METRICS: Array<{ key: keyof ResumeAts; label: string }> = [
  { key: 'completenessScore', label: 'Completeness' },
  { key: 'sectionCoverage', label: 'Section coverage' },
  { key: 'contactQuality', label: 'Contact details' },
  { key: 'skillDiversity', label: 'Skill diversity' },
  { key: 'projectStrength', label: 'Project strength' },
  { key: 'experienceStrength', label: 'Experience strength' },
  { key: 'readabilityScore', label: 'Readability' },
  { key: 'keywordCoverage', label: 'Keyword coverage' },
  { key: 'formattingQuality', label: 'Formatting quality' },
];

const pct = (v: number) => Math.round(v * 100);

export function AtsPanel({ resumeId, ready }: { resumeId: string; ready: boolean }) {
  const q = useQuery({ queryKey: qk.resumeAts(resumeId), queryFn: () => resumesApi.ats(resumeId), enabled: ready });

  if (!ready) {
    return <EmptyState title="Not processed yet" description="The ATS analysis is available once AI processing has finished." />;
  }
  if (q.isLoading) return <SkeletonRows rows={4} />;
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} />;
  const ats = q.data;
  if (!ats || Object.keys(ats).length === 0) {
    return <EmptyState title="No ATS analysis yet" description="The resume has not been analysed. Try re-processing it." />;
  }

  const unavailable = ats.details?.unavailableMetrics ?? [];
  const warnings = ats.details?.warnings ?? [];
  const review = ats.aiReview;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-6">
        <ScoreRing score={isNum(ats.atsScore) ? pct(ats.atsScore) : null} size={96} label="ATS score" />
        <p className="max-w-md text-sm text-fg-muted">
          Computed from the parsed resume. Metrics that cannot be measured are shown as “Not available” rather than estimated.
          {ats.details?.method && <span className="mt-1 block text-xs text-fg-subtle">Method: {ats.details.method}</span>}
        </p>
      </div>

      <ul className="grid gap-4 sm:grid-cols-2">
        {METRICS.map((m) => {
          const v = ats[m.key];
          const has = isNum(v);
          return (
            <li key={m.key} className="rounded-lg border border-border p-3">
              <div className="mb-1.5 flex items-center justify-between gap-2 text-sm">
                <span className="text-fg">{m.label}</span>
                {has ? (
                  <span className="font-medium tabular-nums text-fg">{pct(v)}%</span>
                ) : (
                  <span className="text-xs italic text-fg-subtle">Not available</span>
                )}
              </div>
              {has && <ProgressBar value={pct(v)} label={m.label} />}
            </li>
          );
        })}
      </ul>

      {unavailable.length > 0 && (
        <Notice tone="neutral" title="Not measurable for this resume">
          <Chips items={unavailable.map(humanize)} />
        </Notice>
      )}
      {warnings.length > 0 && (
        <Notice tone="warning" title="Sections missing or weak">
          <Chips items={warnings.map(humanize)} />
        </Notice>
      )}

      {review ? (
        <section className="space-y-4 rounded-xl border border-border p-4">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-semibold text-fg">AI review</h3>
            <AiLabel />
          </div>
          {review.overallAssessment && <p className="text-sm text-fg">{review.overallAssessment}</p>}
          <div className="grid gap-4 md:grid-cols-3">
            <div>
              <p className="mb-1.5 text-xs font-semibold text-success">Strengths</p>
              <BulletList items={review.strengths} />
            </div>
            <div>
              <p className="mb-1.5 text-xs font-semibold text-danger">Weaknesses</p>
              <BulletList items={review.weaknesses} />
            </div>
            <div>
              <p className="mb-1.5 text-xs font-semibold text-primary">Improvements</p>
              <BulletList items={review.improvements} />
            </div>
          </div>
          {review.missingSections && review.missingSections.length > 0 && (
            <div>
              <p className="mb-1.5 text-xs font-semibold text-fg-muted">Missing sections</p>
              <Chips items={review.missingSections} />
            </div>
          )}
        </section>
      ) : (
        <Notice tone="neutral" title="No AI review">
          The AI review is not available for this resume.
        </Notice>
      )}
    </div>
  );
}
