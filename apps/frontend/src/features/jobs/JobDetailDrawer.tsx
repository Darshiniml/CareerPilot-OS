import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ExternalLink, FileText, Mic, PenLine, PlusCircle, Sparkles } from 'lucide-react';
import { applicationsApi, jobsApi } from '../../api/endpoints';
import { qk } from '../../api/queries';
import { Badge } from '../../components/ui/Badge';
import { Button, ButtonLink, ExternalButtonLink } from '../../components/ui/Button';
import { KeyValue } from '../../components/ui/Data';
import { AiProgress, ErrorState, Notice, Skeleton } from '../../components/ui/Feedback';
import { Modal } from '../../components/ui/Modal';
import { Tabs } from '../../components/ui/Tabs';
import { toast } from '../../store/toast';
import { formatDate, formatDateTime } from '../../lib/format';
import { AnalysisView } from './AnalysisView';
import { MatchPanel } from './MatchPanel';
import { ResearchPanel } from './ResearchPanel';

type Tab = 'overview' | 'analysis' | 'match' | 'research';

export function JobDetailDrawer({ jobId, onClose }: { jobId: string | null; onClose: () => void }) {
  return (
    <Modal open={!!jobId} onClose={onClose} variant="drawer" size="xl" title="Job details">
      {jobId && <JobDetail key={jobId} jobId={jobId} />}
    </Modal>
  );
}

function JobDetail({ jobId }: { jobId: string }) {
  const qc = useQueryClient();
  const [tab, setTab] = useState<Tab>('overview');
  const [createdId, setCreatedId] = useState<string | null>(null);
  const q = useQuery({ queryKey: qk.job(jobId), queryFn: () => jobsApi.get(jobId) });

  const analyze = useMutation({
    mutationFn: () => jobsApi.analyze(jobId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.job(jobId) });
      toast.success('Job analysed');
      setTab('analysis');
    },
  });
  const track = useMutation({
    mutationFn: () => applicationsApi.create(jobId),
    onSuccess: (app) => {
      qc.invalidateQueries({ queryKey: qk.applications });
      qc.invalidateQueries({ queryKey: ['opportunities'] });
      setCreatedId(app.applicationId);
      toast.success(
        app.workflowState === 'ALREADY_APPLIED' ? 'You already have an application for this job' : 'Application created',
        'Open Applications to follow its progress.',
      );
    },
    onError: toast.apiError,
  });

  if (q.isLoading) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-7 w-2/3" />
        <Skeleton className="h-4 w-1/3" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} />;
  const job = q.data!;
  const facts = [
    job.company && { label: 'Company', value: job.company },
    job.location && { label: 'Location', value: job.location },
    job.workMode && { label: 'Work mode', value: job.workMode },
    job.employmentType && { label: 'Employment type', value: job.employmentType },
    job.salary && { label: 'Salary (as posted)', value: job.salary },
    job.postedDate && { label: 'Posted', value: formatDate(job.postedDate) },
    job.source && { label: 'Source', value: job.source },
    job.discoveredAt && { label: 'Discovered', value: formatDateTime(job.discoveredAt) },
  ].filter(Boolean) as Array<{ label: string; value: string }>;

  return (
    <div className="space-y-5">
      <div>
        <h3 className="text-lg font-semibold text-fg">{job.title ?? 'Untitled job'}</h3>
        <div className="mt-1 flex flex-wrap items-center gap-2">
          {job.provenance && <Badge title="Where this data came from">{job.provenance}</Badge>}
          {job.analysis ? <Badge tone="success">Analysed</Badge> : <Badge>Not analysed</Badge>}
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        <Button icon={<PlusCircle className="h-4 w-4" />} onClick={() => track.mutate()} loading={track.isPending}>
          Track application
        </Button>
        {job.sourceUrl && (
          <ExternalButtonLink href={job.sourceUrl} icon={<ExternalLink className="h-4 w-4" />}>
            Open official posting
          </ExternalButtonLink>
        )}
        <ButtonLink variant="ghost" to={`/interviews?jobId=${jobId}`} icon={<Mic className="h-4 w-4" />}>
          Practice interview
        </ButtonLink>
        <ButtonLink variant="ghost" to={`/cover-letters?jobId=${jobId}`} icon={<PenLine className="h-4 w-4" />}>
          Cover letter
        </ButtonLink>
        <ButtonLink variant="ghost" to={`/resumes?tailorJobId=${jobId}`} icon={<FileText className="h-4 w-4" />}>
          Tailor resume
        </ButtonLink>
      </div>
      {createdId && (
        <Notice tone="success" title="Application tracked">
          <Link className="text-primary underline" to={`/applications?id=${createdId}`}>
            Open the application
          </Link>
        </Notice>
      )}

      <Tabs<Tab>
        ariaLabel="Job sections"
        value={tab}
        onChange={setTab}
        items={[
          { id: 'overview', label: 'Overview' },
          { id: 'analysis', label: 'Analysis' },
          { id: 'match', label: 'Match' },
          { id: 'research', label: 'Company research' },
        ]}
      />

      {tab === 'overview' && (
        <div className="space-y-4">
          <p className="text-xs text-fg-subtle">Facts below come directly from the job source; fields the source did not provide are omitted.</p>
          {facts.length > 0 ? <KeyValue items={facts} /> : <p className="text-sm italic text-fg-subtle">The source provided no details.</p>}
        </div>
      )}

      {tab === 'analysis' && (
        <div className="space-y-4">
          <Button
            variant={job.analysis ? 'secondary' : 'primary'}
            icon={<Sparkles className="h-4 w-4" />}
            onClick={() => analyze.mutate()}
            disabled={analyze.isPending}
            loading={analyze.isPending}
          >
            {job.analysis ? 'Re-analyse job' : 'Analyse job'}
          </Button>
          {analyze.isPending && <AiProgress label="Analysing the job posting…" />}
          {analyze.isError && <ErrorState error={analyze.error} />}
          {job.analysis ? (
            <AnalysisView analysis={job.analysis} />
          ) : (
            !analyze.isPending && (
              <Notice tone="neutral" title="Not analysed yet">
                Run the analysis to extract requirements, skills and insights with evidence from the posting.
              </Notice>
            )
          )}
        </div>
      )}

      {tab === 'match' && <MatchPanel jobId={jobId} onAnalyzed={() => qc.invalidateQueries({ queryKey: qk.job(jobId) })} />}
      {tab === 'research' && <ResearchPanel jobId={jobId} />}
    </div>
  );
}
