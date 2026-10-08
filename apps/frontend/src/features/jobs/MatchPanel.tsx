import { useMutation } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Gauge, MessageSquareText } from 'lucide-react';
import { matchingApi } from '../../api/endpoints';
import { toApiError } from '../../api/errors';
import type { GapItem, MatchResult } from '../../api/types';
import { AiLabel, Badge } from '../../components/ui/Badge';
import { Button, buttonClasses } from '../../components/ui/Button';
import { BulletList, Chips, ProgressBar, ScoreRing } from '../../components/ui/Data';
import { AiProgress, ErrorState, Notice } from '../../components/ui/Feedback';
import { VerificationNotice } from '../../components/shared';
import { humanize, isNum } from '../../lib/format';

function NoResumeHint({ error }: { error: unknown }) {
  const err = toApiError(error);
  if (err.code !== 'CONFLICT') return null;
  return (
    <Link to="/resumes" className={buttonClasses('secondary', 'sm', 'mt-2')}>
      Go to Resumes
    </Link>
  );
}

function Gaps({ items }: { items: GapItem[] | null }) {
  if (!items || items.length === 0) return <p className="text-sm italic text-fg-subtle">None reported</p>;
  return (
    <ul className="space-y-2">
      {items.map((g, i) => (
        <li key={i} className="rounded-lg border border-border bg-surface-2 p-2.5 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium text-fg">{g.name ?? humanize(g.type)}</span>
            <Badge tone={g.severity === 'CRITICAL' ? 'danger' : 'warning'}>{humanize(g.severity)}</Badge>
          </div>
          {g.description && <p className="mt-1 text-fg-muted">{g.description}</p>}
          {g.suggestedAction && <p className="mt-1 text-xs text-fg-subtle">Suggested: {g.suggestedAction}</p>}
        </li>
      ))}
    </ul>
  );
}

function MatchDetails({ m, onFullMatch, fullPending }: { m: MatchResult; onFullMatch: () => void; fullPending: boolean }) {
  const scores = Object.entries(m.individualScores ?? {}).filter(([, v]) => isNum(v));
  return (
    <div className="space-y-4">
      {m.jobAnalyzed === false && (
        <Notice tone="warning" title="Partial match">
          This job has not been analysed yet, so only basic factors were compared. Analyse the job for a full match.
          <div className="mt-2">
            <Button size="sm" onClick={onFullMatch} loading={fullPending} disabled={fullPending}>
              Analyse the job for a full match
            </Button>
          </div>
        </Notice>
      )}
      <div className="flex flex-wrap items-center gap-5">
        <ScoreRing score={m.overallScore} label="Overall match" size={88} />
        <div className="text-sm text-fg-muted">
          <p>
            Confidence:{' '}
            <span className="font-medium text-fg">{isNum(m.confidenceScore) ? `${Math.round(m.confidenceScore * 100)}%` : 'Not available'}</span>
          </p>
          {m.explanation && <p className="mt-1 max-w-prose">{m.explanation}</p>}
        </div>
      </div>
      {scores.length > 0 && (
        <ul className="grid gap-x-6 gap-y-2.5 sm:grid-cols-2">
          {scores.map(([k, v]) => (
            <li key={k}>
              <div className="mb-1 flex justify-between text-xs">
                <span className="text-fg-muted">{humanize(k.replace(/([A-Z])/g, '_$1'))}</span>
                <span className="font-medium tabular-nums text-fg">{Math.round(v)}</span>
              </div>
              <ProgressBar value={v} label={k} />
            </li>
          ))}
        </ul>
      )}
      {(m.notAssessedFactors?.length ?? 0) > 0 && (
        <div>
          <p className="mb-1 text-xs font-medium text-fg-muted">Not assessed (not enough data)</p>
          <Chips items={(m.notAssessedFactors ?? []).map((f) => humanize(f.replace(/([A-Z])/g, '_$1')))} />
        </div>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <p className="mb-1 text-xs font-medium text-fg-muted">Matched skills</p>
          <Chips items={m.matchedSkills} tone="success" empty="None found" />
        </div>
        <div>
          <p className="mb-1 text-xs font-medium text-fg-muted">Missing skills</p>
          <Chips items={m.missingSkills} tone="danger" empty="None found" />
        </div>
        <div>
          <p className="mb-1 text-xs font-medium text-fg-muted">Strengths</p>
          <BulletList items={m.strengths} />
        </div>
        <div>
          <p className="mb-1 text-xs font-medium text-fg-muted">Weaknesses</p>
          <BulletList items={m.weaknesses} />
        </div>
      </div>
      <div>
        <p className="mb-1 text-xs font-medium text-fg-muted">Critical gaps</p>
        <Gaps items={m.criticalGaps} />
      </div>
      {(m.recommendations?.length ?? 0) > 0 && (
        <div>
          <p className="mb-1 text-xs font-medium text-fg-muted">Recommendations</p>
          <ul className="space-y-1.5">
            {(m.recommendations ?? []).map((r, i) => (
              <li key={i} className="text-sm text-fg">
                <Badge tone={r.priority === 'HIGH' ? 'danger' : r.priority === 'MEDIUM' ? 'warning' : 'neutral'} className="mr-2">
                  {humanize(r.priority)}
                </Badge>
                {r.action ?? r.description ?? humanize(r.type)}
                {r.rationale && <span className="block text-xs text-fg-muted">{r.rationale}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export function MatchPanel({ jobId, onAnalyzed }: { jobId: string; onAnalyzed?: () => void }) {
  const match = useMutation({
    mutationFn: (analyzeJob: boolean) => matchingApi.match(jobId, analyzeJob),
    onSuccess: (_d, analyzeJob) => {
      if (analyzeJob) onAnalyzed?.();
    },
  });
  const explain = useMutation({ mutationFn: () => matchingApi.explain(jobId) });
  const busy = match.isPending || explain.isPending;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        <Button icon={<Gauge className="h-4 w-4" />} onClick={() => match.mutate(false)} disabled={busy} loading={match.isPending}>
          {match.data ? 'Recalculate match' : 'Calculate match'}
        </Button>
        <Button
          variant="secondary"
          icon={<MessageSquareText className="h-4 w-4" />}
          onClick={() => explain.mutate()}
          disabled={busy}
          loading={explain.isPending}
        >
          Explain match (AI)
        </Button>
      </div>
      {match.isPending && <AiProgress label={match.variables ? 'Analysing the job and matching your resume…' : 'Matching your resume to this job…'} />}
      {match.isError && (
        <div>
          <ErrorState error={match.error} />
          <NoResumeHint error={match.error} />
        </div>
      )}
      {match.data && !match.isPending && (
        <MatchDetails m={match.data} onFullMatch={() => match.mutate(true)} fullPending={match.isPending} />
      )}
      {!match.data && !match.isPending && !match.isError && (
        <p className="text-sm text-fg-muted">Compare this job with your processed default resume.</p>
      )}

      {explain.isPending && <AiProgress label="Writing a match explanation…" />}
      {explain.isError && (
        <div>
          <ErrorState error={explain.error} />
          <NoResumeHint error={explain.error} />
        </div>
      )}
      {explain.data && (
        <div className="space-y-3 rounded-xl border border-border bg-surface-2 p-4">
          <div className="flex items-center gap-2">
            <AiLabel />
            <p className="text-sm font-semibold text-fg">{explain.data.aiExplanation.headline}</p>
          </div>
          <p className="whitespace-pre-line text-sm text-fg">{explain.data.aiExplanation.explanation}</p>
          <div className="grid gap-4 sm:grid-cols-3">
            <div>
              <p className="mb-1 text-xs font-medium text-fg-muted">Strengths</p>
              <BulletList items={explain.data.aiExplanation.strengths} />
            </div>
            <div>
              <p className="mb-1 text-xs font-medium text-fg-muted">Gaps</p>
              <BulletList items={explain.data.aiExplanation.gaps} />
            </div>
            <div>
              <p className="mb-1 text-xs font-medium text-fg-muted">Recommendations</p>
              <BulletList items={explain.data.aiExplanation.recommendations} />
            </div>
          </div>
          <VerificationNotice verification={explain.data.aiExplanation.verification} />
        </div>
      )}
    </div>
  );
}
