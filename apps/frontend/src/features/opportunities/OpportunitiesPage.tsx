import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ExternalLink, MapPin, PlusCircle, Target } from 'lucide-react';
import { opportunitiesApi } from '../../api/endpoints';
import { qk } from '../../api/queries';
import type { Opportunity, PriorityLevel } from '../../api/types';
import { Badge } from '../../components/ui/Badge';
import { Button, ButtonLink, ExternalButtonLink } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { BulletList, Chips } from '../../components/ui/Data';
import { Input, Select } from '../../components/ui/Field';
import { EmptyState, ErrorState, SkeletonRows } from '../../components/ui/Feedback';
import { PageHeader } from '../../components/ui/PageHeader';
import { toast } from '../../store/toast';
import { priorityTone, stateLabel, stateTone } from '../../lib/domain';
import { humanize, isNum } from '../../lib/format';

const PRIORITIES: PriorityLevel[] = ['HIGH_PRIORITY', 'MEDIUM_PRIORITY', 'LOW_PRIORITY', 'NOT_RECOMMENDED', 'UNSCORED'];
const WORK_MODES = ['REMOTE', 'HYBRID', 'ONSITE'];

function useDebounced<T>(value: T, ms = 400): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = window.setTimeout(() => setV(value), ms);
    return () => window.clearTimeout(t);
  }, [value, ms]);
  return v;
}

function OpportunityCard({ o }: { o: Opportunity }) {
  const qc = useQueryClient();
  const [createdId, setCreatedId] = useState<string | null>(null);
  const create = useMutation({
    mutationFn: () => opportunitiesApi.createApplication(o.jobId),
    onSuccess: (app) => {
      setCreatedId(app.applicationId);
      qc.invalidateQueries({ queryKey: qk.applications });
      qc.invalidateQueries({ queryKey: ['opportunities'] });
      toast.success('Application created', `${o.title ?? 'Job'} is now in your applications.`);
    },
    onError: toast.apiError,
  });

  const notApplied = o.applicationStatus === 'NOT_APPLIED';
  const blocked = o.applicationStatus === 'APPLICATION_BLOCKED_BY_DAILY_LIMIT';
  const tracked = !notApplied && !blocked && !!o.applicationStatus;

  return (
    <Card className="p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <Link to={`/jobs?jobId=${o.jobId}`} className="text-sm font-semibold text-fg hover:text-primary hover:underline">
            {o.title ?? 'Untitled job'}
          </Link>
          <p className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-fg-muted">
            {o.company && <span>{o.company}</span>}
            {o.location && (
              <span className="inline-flex items-center gap-1">
                <MapPin className="h-3 w-3" aria-hidden />
                {o.location}
              </span>
            )}
            {o.workMode && <span>{o.workMode}</span>}
            {(o.source ?? o.connectorId) && <span>via {o.source ?? o.connectorId}</span>}
          </p>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-1.5">
          {tracked ? (
            <Badge tone={stateTone(o.applicationStatus)}>Tracked: {stateLabel(o.applicationStatus)}</Badge>
          ) : (
            <Badge tone={priorityTone(o.priorityLevel)}>{humanize(o.priorityLevel)}</Badge>
          )}
          {o.matchAvailable === true ? (
            <Badge tone="primary" title="Resume-to-job match score">
              Match {Math.round(o.matchScore)}%
            </Badge>
          ) : (
            <Badge title="No processed resume, so this job could not be scored">Unscored</Badge>
          )}
        </div>
      </div>

      <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-xs sm:grid-cols-4">
        <div>
          <dt className="text-fg-subtle">Priority score</dt>
          <dd className="text-fg">{o.matchAvailable === true && isNum(o.priorityScore) ? Math.round(o.priorityScore) : 'Not available'}</dd>
        </div>
        <div>
          <dt className="text-fg-subtle">Historical data</dt>
          <dd className="text-fg">{o.historicalConfidence ? humanize(o.historicalConfidence) : 'Not available'}</dd>
        </div>
        <div>
          <dt className="text-fg-subtle">Submission</dt>
          <dd className="text-fg">{o.submissionMode ? humanize(o.submissionMode) : 'Not available'}</dd>
        </div>
        <div>
          <dt className="text-fg-subtle">Recommended action</dt>
          <dd className="text-fg">{o.recommendedAction ? humanize(o.recommendedAction) : 'Not available'}</dd>
        </div>
      </dl>

      {(o.notAssessedFactors?.length ?? 0) > 0 && (
        <div className="mt-3">
          <p className="mb-1 text-xs text-fg-subtle">Not assessed</p>
          <Chips items={(o.notAssessedFactors ?? []).map((f) => humanize(f.replace(/([A-Z])/g, '_$1')))} />
        </div>
      )}
      {(o.reasons?.length ?? 0) > 0 && (
        <div className="mt-3">
          <p className="mb-1 text-xs text-fg-subtle">Why</p>
          <BulletList items={o.reasons} />
        </div>
      )}

      <div className="mt-4 flex flex-wrap gap-2">
        {notApplied && !createdId && (
          <Button size="sm" icon={<PlusCircle className="h-3.5 w-3.5" />} onClick={() => create.mutate()} loading={create.isPending}>
            Create application
          </Button>
        )}
        {blocked && <p className="self-center text-xs text-warning">Daily application limit reached. Try again tomorrow.</p>}
        {(tracked || createdId) && (
          <ButtonLink size="sm" variant="secondary" to={createdId ? `/applications?id=${createdId}` : '/applications'}>
            Open in Applications
          </ButtonLink>
        )}
        <ButtonLink size="sm" variant="ghost" to={`/jobs?jobId=${o.jobId}`}>
          Job details
        </ButtonLink>
        {o.sourceUrl && (
          <ExternalButtonLink size="sm" variant="ghost" href={o.sourceUrl} icon={<ExternalLink className="h-3.5 w-3.5" />}>
            Official posting
          </ExternalButtonLink>
        )}
      </div>
    </Card>
  );
}

export function OpportunitiesPage() {
  const [search, setSearch] = useState('');
  const [priority, setPriority] = useState('');
  const [workMode, setWorkMode] = useState('');
  const debounced = useDebounced(search.trim());
  const filters = useMemo(() => ({ search: debounced, priority, workMode }), [debounced, priority, workMode]);

  const q = useQuery({ queryKey: qk.opportunities(filters), queryFn: () => opportunitiesApi.list(filters) });
  const items = useMemo(
    () => [...(q.data?.content ?? [])].sort((a, b) => (b.priorityScore ?? 0) - (a.priorityScore ?? 0)),
    [q.data],
  );

  return (
    <div>
      <PageHeader title="Opportunities" description="Discovered jobs ranked by how well they fit you. Scores come only from your processed resume and real history." />
      <Card className="mb-4 grid gap-2 p-4 sm:grid-cols-3">
        <Input label="Search" placeholder="Title or company" value={search} onChange={(e) => setSearch(e.target.value)} />
        <Select
          label="Priority"
          value={priority}
          onChange={(e) => setPriority(e.target.value)}
          placeholder="All priorities"
          options={PRIORITIES.map((p) => ({ value: p, label: humanize(p) }))}
        />
        <Select
          label="Work mode"
          value={workMode}
          onChange={(e) => setWorkMode(e.target.value)}
          placeholder="All work modes"
          options={WORK_MODES.map((m) => ({ value: m, label: humanize(m) }))}
        />
      </Card>

      {q.isLoading && <SkeletonRows rows={4} />}
      {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (
        <>
          <p className="mb-3 text-xs text-fg-muted" aria-live="polite">
            {q.data.totalElements} {q.data.totalElements === 1 ? 'opportunity' : 'opportunities'}
            {q.data.totalElements > items.length && ` · showing the first ${items.length}`}
            {q.isFetching && ' · updating…'}
          </p>
          {items.length === 0 ? (
            <EmptyState
              icon={<Target className="h-5 w-5" />}
              title="No opportunities"
              description={
                debounced || priority || workMode
                  ? 'Nothing matches these filters.'
                  : 'No jobs have been discovered yet. Opportunities appear once connectors discover jobs.'
              }
            />
          ) : (
            <div className="space-y-3">
              {items.map((o) => (
                <OpportunityCard key={o.jobId} o={o} />
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
