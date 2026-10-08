import { useMemo, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Briefcase, ClipboardPaste, Link2, MapPin, Search } from 'lucide-react';
import { jobsApi } from '../../api/endpoints';
import { qk } from '../../api/queries';
import type { JobCard } from '../../api/types';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Input, Select } from '../../components/ui/Field';
import { AiProgress, EmptyState, ErrorState, SkeletonRows } from '../../components/ui/Feedback';
import { PageHeader } from '../../components/ui/PageHeader';
import { formatDate, isNum } from '../../lib/format';
import { JobDetailDrawer } from './JobDetailDrawer';
import { ExternalJobModal } from './ExternalJobModal';

const uniq = (xs: Array<string | undefined>) =>
  Array.from(new Set(xs.filter((x): x is string => !!x && x.trim() !== ''))).sort((a, b) => a.localeCompare(b));

function JobRow({ job, onOpen }: { job: JobCard; onOpen: () => void }) {
  return (
    <li>
      <button
        type="button"
        onClick={onOpen}
        className="flex w-full flex-col gap-2 rounded-xl border border-border bg-surface p-4 text-left transition-colors hover:border-border-strong hover:bg-surface-2 sm:flex-row sm:items-center sm:justify-between"
      >
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-fg">{job.title ?? 'Untitled job'}</p>
          <p className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-fg-muted">
            {job.company && <span>{job.company}</span>}
            {job.location && (
              <span className="inline-flex items-center gap-1">
                <MapPin className="h-3 w-3" aria-hidden />
                {job.location}
              </span>
            )}
            {job.postedDate && <span>Posted {formatDate(job.postedDate)}</span>}
          </p>
          {job.matchedText && <p className="mt-1 line-clamp-2 text-xs text-fg-subtle">“{job.matchedText}”</p>}
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-1.5">
          {job.workMode && <Badge>{job.workMode}</Badge>}
          {job.employmentType && <Badge>{job.employmentType}</Badge>}
          {job.source && <Badge tone="info">{job.source}</Badge>}
          {isNum(job.relevance) && (
            <Badge tone="primary" title="Semantic similarity between your query and the job">
              Relevance {job.relevance.toFixed(2)}
            </Badge>
          )}
        </div>
      </button>
    </li>
  );
}

export function JobsPage() {
  const [params, setParams] = useSearchParams();
  const jobId = params.get('jobId');
  const [input, setInput] = useState('');
  const [submitted, setSubmitted] = useState('');
  const [location, setLocation] = useState('');
  const [workMode, setWorkMode] = useState('');
  const [source, setSource] = useState('');
  const [external, setExternal] = useState<null | 'text' | 'url'>(null);

  const search = useQuery({
    queryKey: qk.jobSearch(submitted),
    queryFn: () => jobsApi.search(submitted),
    retry: false,
    staleTime: 5 * 60_000,
    refetchOnMount: false,
  });

  const results = useMemo(() => search.data?.results ?? [], [search.data]);
  const locations = useMemo(() => uniq(results.map((r) => r.location)), [results]);
  const modes = useMemo(() => uniq(results.map((r) => r.workMode)), [results]);
  const sources = useMemo(() => uniq(results.map((r) => r.source ?? r.connectorId)), [results]);
  const filtered = results.filter(
    (r) =>
      (!location || r.location === location) && (!workMode || r.workMode === workMode) && (!source || (r.source ?? r.connectorId) === source),
  );

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    setSubmitted(input.trim());
  };
  const openJob = (id: string) => {
    const next = new URLSearchParams(params);
    next.set('jobId', id);
    setParams(next);
  };
  const closeJob = () => {
    const next = new URLSearchParams(params);
    next.delete('jobId');
    setParams(next, { replace: true });
  };

  const semanticRunning = search.isFetching && submitted !== '';

  return (
    <div>
      <PageHeader
        title="Jobs"
        description="Search discovered jobs by meaning, inspect the posting, analyse it and compare it with your resume."
        actions={
          <>
            <Button variant="secondary" icon={<ClipboardPaste className="h-4 w-4" />} onClick={() => setExternal('text')}>
              Paste a job
            </Button>
            <Button variant="secondary" icon={<Link2 className="h-4 w-4" />} onClick={() => setExternal('url')}>
              Analyse a URL
            </Button>
          </>
        }
      />

      <Card className="mb-4 p-4">
        <form onSubmit={onSubmit} className="flex flex-col gap-2 sm:flex-row sm:items-end" role="search">
          <Input
            containerClassName="flex-1"
            label="Search jobs"
            placeholder="e.g. backend engineer with Kafka, remote"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            hint="Leave empty to see the most recently discovered jobs."
          />
          <Button type="submit" icon={<Search className="h-4 w-4" />} disabled={search.isFetching} loading={search.isFetching}>
            Search
          </Button>
        </form>
        {results.length > 0 && (
          <div className="mt-3 grid gap-2 sm:grid-cols-3">
            <Select label="Location" value={location} onChange={(e) => setLocation(e.target.value)} placeholder="All locations" options={locations.map((v) => ({ value: v, label: v }))} />
            <Select label="Work mode" value={workMode} onChange={(e) => setWorkMode(e.target.value)} placeholder="All work modes" options={modes.map((v) => ({ value: v, label: v }))} />
            <Select label="Source" value={source} onChange={(e) => setSource(e.target.value)} placeholder="All sources" options={sources.map((v) => ({ value: v, label: v }))} />
          </div>
        )}
      </Card>

      {semanticRunning && <AiProgress className="mb-4" label="Searching jobs by meaning…" />}
      {search.isLoading && !semanticRunning && <SkeletonRows rows={5} />}
      {search.isError && <ErrorState error={search.error} onRetry={() => search.refetch()} />}

      {search.data && !search.isFetching && (
        <>
          <p className="mb-3 text-xs text-fg-muted" aria-live="polite">
            {search.data.mode === 'semantic' ? `Semantic results for “${submitted}”` : 'Most recently discovered jobs'} · {filtered.length} of{' '}
            {results.length} shown
          </p>
          {filtered.length === 0 ? (
            <EmptyState
              icon={<Briefcase className="h-5 w-5" />}
              title={results.length === 0 ? 'No jobs found' : 'No jobs match these filters'}
              description={
                results.length === 0
                  ? submitted
                    ? 'Try a different query.'
                    : 'No jobs have been discovered yet. Check connector health on the Agents page, or paste a job.'
                  : 'Clear a filter to see more results.'
              }
            />
          ) : (
            <ul className="space-y-2">
              {filtered.map((j) => (
                <JobRow key={j.jobId} job={j} onOpen={() => openJob(j.jobId)} />
              ))}
            </ul>
          )}
        </>
      )}

      <JobDetailDrawer jobId={jobId} onClose={closeJob} />
      {external && <ExternalJobModal open initialMode={external} onClose={() => setExternal(null)} />}
    </div>
  );
}
