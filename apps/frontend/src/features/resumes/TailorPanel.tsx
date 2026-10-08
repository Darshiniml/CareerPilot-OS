import { useState, type ReactNode } from 'react';
import { useMutation } from '@tanstack/react-query';
import { AlertTriangle, ArrowRight, Wand2 } from 'lucide-react';
import { resumesApi } from '../../api/endpoints';
import type { ResumeOptimization } from '../../api/types';
import { AiLabel, AiProgress, Badge, BulletList, Button, Chips, EmptyState, ErrorState, Notice } from '../../components/ui';
import { JobPicker } from '../../components/shared';

function Block({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-2">
      <h3 className="text-sm font-semibold text-fg">{title}</h3>
      {children}
    </section>
  );
}

function Result({ r }: { r: ResumeOptimization }) {
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-2 text-xs text-fg-muted">
        <AiLabel>AI-generated suggestions</AiLabel>
        <span>Based on resume version {r.resumeVersion}. Nothing has been changed in your resume.</span>
      </div>

      <Block title="Missing skills">
        {r.missingSkills.length === 0 ? (
          <p className="text-sm italic text-fg-subtle">No missing skills identified.</p>
        ) : (
          <ul className="space-y-2">
            {r.missingSkills.map((m, i) => (
              <li key={i} className="rounded-lg border border-border p-3 text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium text-fg">{m.skill}</span>
                  {m.importance && <Badge tone={/req|high|crit/i.test(m.importance) ? 'danger' : 'neutral'}>{m.importance}</Badge>}
                </div>
                {m.jobEvidence && (
                  <p className="mt-1 text-xs text-fg-muted">
                    Job says: <q className="italic">{m.jobEvidence}</q>
                  </p>
                )}
              </li>
            ))}
          </ul>
        )}
      </Block>

      <Block title="Keyword gaps">
        <Chips items={r.keywordGaps} empty="No keyword gaps found." tone="danger" />
      </Block>

      {r.experienceGaps && r.experienceGaps.length > 0 && (
        <Block title="Experience gaps">
          <BulletList items={r.experienceGaps} />
        </Block>
      )}

      <Block title="Bullet improvements">
        {r.bulletImprovements.length === 0 ? (
          <p className="text-sm italic text-fg-subtle">No bullet rewrites suggested.</p>
        ) : (
          <ul className="space-y-3">
            {r.bulletImprovements.map((b, i) => (
              <li key={i} className="space-y-2 rounded-lg border border-border p-3 text-sm">
                <div className="grid gap-2 md:grid-cols-[1fr_auto_1fr] md:items-start">
                  <div>
                    <p className="mb-0.5 text-xs font-medium text-fg-subtle">Before</p>
                    <p className="text-fg-muted">{b.original}</p>
                  </div>
                  <ArrowRight className="hidden h-4 w-4 text-fg-subtle md:mt-5 md:block" aria-hidden />
                  <div>
                    <p className="mb-0.5 text-xs font-medium text-fg-subtle">Suggested</p>
                    <p className="text-fg">{b.suggested}</p>
                  </div>
                </div>
                {b.rationale && <p className="text-xs text-fg-muted">Why: {b.rationale}</p>}
                {b.introducesUnverifiedClaims && (
                  <div className="rounded-md border border-warning/30 bg-warning-soft px-3 py-2 text-xs text-warning">
                    <p className="flex items-center gap-1.5 font-semibold">
                      <AlertTriangle className="h-3.5 w-3.5" /> Contains unverified numbers — replace with your real figures
                    </p>
                    {b.unverifiedValues && b.unverifiedValues.length > 0 && (
                      <div className="mt-1.5">
                        <Chips items={b.unverifiedValues} tone="danger" />
                      </div>
                    )}
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </Block>

      {r.summarySuggestion && (
        <Block title="Summary suggestion">
          <p className="whitespace-pre-line rounded-lg border border-border bg-surface-2 p-3 text-sm text-fg">{r.summarySuggestion}</p>
        </Block>
      )}

      <Block title="Project relevance">
        {r.projectRelevance.length === 0 ? (
          <p className="text-sm italic text-fg-subtle">No project assessment returned.</p>
        ) : (
          <ul className="space-y-2">
            {r.projectRelevance.map((p, i) => (
              <li key={i} className="text-sm">
                <span className="font-medium text-fg">{p.project}</span>{' '}
                <Badge tone={p.relevance === 'HIGH' ? 'success' : p.relevance === 'MEDIUM' ? 'info' : 'neutral'}>{p.relevance}</Badge>
                {p.reason && <p className="text-xs text-fg-muted">{p.reason}</p>}
              </li>
            ))}
          </ul>
        )}
      </Block>

      <div className="grid gap-6 md:grid-cols-2">
        <Block title="Measurable impact ideas">
          <BulletList items={r.measurableImpactSuggestions} empty="None suggested." />
        </Block>
        <Block title="ATS improvements">
          <BulletList items={r.atsImprovements} empty="None suggested." />
        </Block>
      </div>

      {r.rejectedSuggestions.length > 0 && (
        <Notice tone="neutral" title={`${r.rejectedSuggestions.length} suggestion(s) were rejected by verification`}>
          <ul className="mt-1 space-y-1">
            {r.rejectedSuggestions.map((s, i) => (
              <li key={i} className="text-xs">
                <span className="line-through">{s.suggested}</span> — {s.reason}
              </li>
            ))}
          </ul>
        </Notice>
      )}
      {r.policy && <p className="text-xs text-fg-subtle">Policy: {r.policy}</p>}
    </div>
  );
}

export function TailorPanel({ resumeId, ready, initialJobId }: { resumeId: string; ready: boolean; initialJobId?: string }) {
  const [jobId, setJobId] = useState(initialJobId ?? '');
  const optimize = useMutation({ mutationFn: (j: string) => resumesApi.optimize(resumeId, j) });

  if (!ready) {
    return (
      <EmptyState title="Resume not ready" description="Tailoring needs a successfully processed resume. Wait for processing to finish or re-process it." />
    );
  }

  return (
    <div className="space-y-5">
      <p className="text-sm text-fg-muted">
        Compare this resume with a job posting. The AI suggests changes; it never edits your resume and flags any figures it could not
        verify.
      </p>
      <JobPicker value={jobId} onChange={setJobId} label="Target job" required />
      <div className="flex justify-end">
        <Button
          onClick={() => optimize.mutate(jobId)}
          disabled={!jobId || optimize.isPending}
          loading={optimize.isPending}
          icon={<Wand2 className="h-4 w-4" />}
        >
          Tailor to this job
        </Button>
      </div>
      {optimize.isPending && <AiProgress label="Comparing your resume with the job…" />}
      {optimize.isError && <ErrorState error={optimize.error} />}
      {optimize.data && !optimize.isPending && <Result r={optimize.data} />}
    </div>
  );
}
