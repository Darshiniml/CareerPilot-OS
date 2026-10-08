import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { KanbanSquare, LayoutList, Columns3 } from 'lucide-react';
import { applicationsApi } from '../../api/endpoints';
import { qk, useApplications, useJobIndex } from '../../api/queries';
import type { ApplicationRecord, ApplicationStatistics } from '../../api/types';
import {
  Badge,
  ButtonLink,
  Card,
  CardBody,
  CardHeader,
  EmptyState,
  ErrorState,
  PageHeader,
  SkeletonRows,
  Stat,
} from '../../components/ui';
import { PIPELINE_GROUPS, jobLabel, stateLabel, stateTone } from '../../lib/domain';
import { cn, formatRelative, isNum } from '../../lib/format';
import { ApplicationDetail } from './ApplicationDetail';

const pct = (v: number | null | undefined) => (isNum(v) ? `${Math.round(v)}%` : null);

function StatisticsCard() {
  const q = useQuery({ queryKey: qk.applicationStats, queryFn: applicationsApi.statistics });
  if (q.isLoading) return <SkeletonRows rows={1} />;
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} compact />;
  const s: ApplicationStatistics | undefined = q.data;
  if (!s) return null;
  return (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
      <Stat label="Submitted" value={s.applicationsSubmitted} />
      <Stat label="Approval rate" value={pct(s.approvalRate)} />
      <Stat label="Submission success" value={pct(s.submissionSuccessRate)} />
      <Stat label="Failure rate" value={pct(s.failureRate)} />
      <Stat label="Interview rate" value={pct(s.interviewRate)} />
      <Stat label="Offer rate" value={pct(s.offerRate)} />
    </div>
  );
}

function AppCard({ app, title, onOpen }: { app: ApplicationRecord; title: string; onOpen: () => void }) {
  return (
    <button
      type="button"
      onClick={onOpen}
      className="w-full rounded-lg border border-border bg-surface p-3 text-left transition-colors hover:border-border-strong hover:bg-surface-2"
    >
      <p className="line-clamp-2 text-sm font-medium text-fg">{title}</p>
      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        <Badge tone={stateTone(app.workflowState)}>{stateLabel(app.workflowState)}</Badge>
        {isNum(app.matchScore) && <Badge tone="neutral">Match {Math.round(app.matchScore)}</Badge>}
      </div>
      <p className="mt-2 text-[11px] text-fg-subtle">Updated {formatRelative(app.updatedAt)}</p>
    </button>
  );
}

export function ApplicationsPage() {
  const [params, setParams] = useSearchParams();
  const selectedId = params.get('id');
  const [view, setView] = useState<'board' | 'list'>('board');
  const apps = useApplications();
  const jobs = useJobIndex();

  const groups = useMemo(() => {
    const list = apps.data ?? [];
    const grouped = PIPELINE_GROUPS.map((g) => ({
      ...g,
      items: list
        .filter((a) => (g.states as string[]).includes(a.workflowState))
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)),
    }));
    const known = new Set(PIPELINE_GROUPS.flatMap((g) => g.states as string[]));
    const other = list.filter((a) => !known.has(a.workflowState));
    if (other.length) grouped.push({ id: 'other', label: 'Other', states: [], tone: 'neutral', items: other });
    return grouped;
  }, [apps.data]);

  const open = (id: string) => setParams((p) => {
    const n = new URLSearchParams(p);
    n.set('id', id);
    return n;
  });
  const close = () => setParams((p) => {
    const n = new URLSearchParams(p);
    n.delete('id');
    return n;
  });

  const titleOf = (a: ApplicationRecord) => jobLabel(jobs.index.get(a.jobId), a.jobId);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Applications"
        description="Every application you are tracking, grouped by its real workflow state."
        actions={
          <div className="inline-flex rounded-lg border border-border p-0.5" role="group" aria-label="View">
            <button
              type="button"
              aria-pressed={view === 'board'}
              onClick={() => setView('board')}
              className={cn('flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium', view === 'board' ? 'bg-primary-soft text-primary-soft-fg' : 'text-fg-muted hover:text-fg')}
            >
              <Columns3 className="h-3.5 w-3.5" /> Board
            </button>
            <button
              type="button"
              aria-pressed={view === 'list'}
              onClick={() => setView('list')}
              className={cn('flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium', view === 'list' ? 'bg-primary-soft text-primary-soft-fg' : 'text-fg-muted hover:text-fg')}
            >
              <LayoutList className="h-3.5 w-3.5" /> List
            </button>
          </div>
        }
      />

      <StatisticsCard />

      {apps.isLoading && <SkeletonRows rows={4} />}
      {apps.isError && <ErrorState error={apps.error} onRetry={() => apps.refetch()} />}
      {jobs.isError && (
        <ErrorState error={jobs.error} onRetry={() => jobs.refetch()} compact className="text-xs" />
      )}

      {apps.data && apps.data.length === 0 && (
        <EmptyState
          icon={<KanbanSquare className="h-5 w-5" />}
          title="No applications yet"
          description="Track a job from the Jobs or Opportunities page to start an application."
          action={
            <>
              <ButtonLink to="/opportunities">Browse opportunities</ButtonLink>
              <ButtonLink to="/jobs" variant="secondary">Search jobs</ButtonLink>
            </>
          }
        />
      )}

      {apps.data && apps.data.length > 0 && view === 'board' && (
        <div className="flex flex-col gap-4 lg:flex-row lg:overflow-x-auto lg:pb-2">
          {groups.map((g) => (
            <section key={g.id} aria-label={g.label} className="flex w-full flex-col rounded-xl border border-border bg-surface-2 p-3 lg:w-72 lg:shrink-0">
              <div className="mb-3 flex items-center justify-between">
                <h2 className="text-sm font-semibold text-fg">{g.label}</h2>
                <Badge tone={g.tone}>{g.items.length}</Badge>
              </div>
              {g.items.length === 0 ? (
                <p className="py-4 text-center text-xs text-fg-subtle">Nothing here</p>
              ) : (
                <div className="space-y-2">
                  {g.items.map((a) => (
                    <AppCard key={a.applicationId} app={a} title={titleOf(a)} onOpen={() => open(a.applicationId)} />
                  ))}
                </div>
              )}
            </section>
          ))}
        </div>
      )}

      {apps.data && apps.data.length > 0 && view === 'list' && (
        <div className="space-y-4">
          {groups
            .filter((g) => g.items.length > 0)
            .map((g) => (
              <Card key={g.id}>
                <CardHeader title={g.label} actions={<Badge tone={g.tone}>{g.items.length}</Badge>} />
                <CardBody className="p-0">
                  <ul className="divide-y divide-border">
                    {g.items.map((a) => (
                      <li key={a.applicationId}>
                        <button
                          type="button"
                          onClick={() => open(a.applicationId)}
                          className="flex w-full flex-col gap-1 px-5 py-3 text-left hover:bg-surface-2 sm:flex-row sm:items-center sm:justify-between"
                        >
                          <span className="min-w-0 truncate text-sm font-medium text-fg">{titleOf(a)}</span>
                          <span className="flex shrink-0 items-center gap-2">
                            <Badge tone={stateTone(a.workflowState)}>{stateLabel(a.workflowState)}</Badge>
                            <span className="text-xs text-fg-subtle">{formatRelative(a.updatedAt)}</span>
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                </CardBody>
              </Card>
            ))}
        </div>
      )}

      {selectedId && (
        <ApplicationDetail
          applicationId={selectedId}
          job={(() => {
            const a = apps.data?.find((x) => x.applicationId === selectedId);
            return a ? jobs.index.get(a.jobId) : undefined;
          })()}
          onClose={close}
        />
      )}
    </div>
  );
}
