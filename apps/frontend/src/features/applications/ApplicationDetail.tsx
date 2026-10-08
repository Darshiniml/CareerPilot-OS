import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowRight,
  CheckCircle2,
  ExternalLink,
  Mail,
  Mic,
  PenLine,
  Send,
  Sparkles,
  XCircle,
  GitCommitHorizontal,
} from 'lucide-react';
import { applicationsApi, type ApplicationAction } from '../../api/endpoints';
import { qk } from '../../api/queries';
import { toApiError } from '../../api/errors';
import type {
  ApplicationPreparation,
  ApplicationRecord,
  DiscoveryJob,
  PreflightResult,
  SubmissionCapability,
  TimelineEntry,
  WorkflowState,
} from '../../api/types';
import {
  AiLabel,
  AiProgress,
  Badge,
  BulletList,
  Button,
  ButtonLink,
  EmptyState,
  ErrorState,
  ExternalButtonLink,
  Input,
  KeyValue,
  Modal,
  Notice,
  Select,
  SkeletonRows,
  Tabs,
  Textarea,
  Unavailable,
} from '../../components/ui';
import { VerificationNotice } from '../../components/shared';
import {
  MANUAL_STATUS_TARGETS,
  OUTCOME_EXPLANATIONS,
  TERMINAL_STATES,
  jobLabel,
  outcomeTone,
  stateLabel,
  stateTone,
} from '../../lib/domain';
import { cn, formatDateTime, humanize, isNum, parseJson } from '../../lib/format';
import { toast } from '../../store/toast';

type TabId = 'overview' | 'actions' | 'timeline' | 'decision' | 'package' | 'preparation';

function useInvalidateApp(id: string) {
  const qc = useQueryClient();
  return () => {
    qc.invalidateQueries({ queryKey: qk.applications });
    qc.invalidateQueries({ queryKey: qk.application(id) });
    qc.invalidateQueries({ queryKey: qk.applicationTimeline(id) });
    qc.invalidateQueries({ queryKey: qk.applicationStats });
    qc.invalidateQueries({ queryKey: qk.notifications });
  };
}

function applyUrlOf(app: ApplicationRecord, job: DiscoveryJob | undefined): string | null {
  if (job?.sourceUrl) return job.sourceUrl;
  const m = app.metadata?.applyUrl;
  return typeof m === 'string' && m.trim() ? m : null;
}

/* ------------------------------------------------------------- overview */
const EVIDENCE_TYPES = [
  { value: 'CONFIRMATION_EMAIL', label: 'Confirmation email' },
  { value: 'CONFIRMATION_NUMBER', label: 'Confirmation number' },
  { value: 'PORTAL_SCREENSHOT', label: 'Portal screenshot' },
  { value: 'OTHER', label: 'Other' },
];

function VerifyForm({ app }: { app: ApplicationRecord }) {
  const invalidate = useInvalidateApp(app.applicationId);
  const [evidenceType, setEvidenceType] = useState('CONFIRMATION_EMAIL');
  const [reference, setReference] = useState('');
  const [confirmationId, setConfirmationId] = useState('');
  const [touched, setTouched] = useState(false);
  const missing = !reference.trim() && !confirmationId.trim();
  const m = useMutation({
    mutationFn: () =>
      applicationsApi.verify(app.applicationId, {
        evidenceType,
        evidenceReference: reference.trim() || null,
        confirmationId: confirmationId.trim() || null,
      }),
    onSuccess: (r) => {
      toast.success('Application recorded as applied', r.reason ?? 'Recorded as candidate-attested.');
      invalidate();
    },
  });
  return (
    <form
      className="space-y-3"
      onSubmit={(e) => {
        e.preventDefault();
        setTouched(true);
        if (!missing) m.mutate();
      }}
    >
      <Select label="Evidence type" value={evidenceType} onChange={(e) => setEvidenceType(e.target.value)} options={EVIDENCE_TYPES} />
      <div className="grid gap-3 sm:grid-cols-2">
        <Input
          label="Evidence reference"
          placeholder="e.g. subject of the confirmation email"
          value={reference}
          onChange={(e) => setReference(e.target.value)}
          error={touched && missing ? 'Provide a reference or a confirmation ID.' : null}
        />
        <Input label="Confirmation ID" placeholder="e.g. REQ-12345" value={confirmationId} onChange={(e) => setConfirmationId(e.target.value)} />
      </div>
      <p className="text-xs text-fg-subtle">Recorded as candidate-attested, not externally verified.</p>
      {m.isError && <ErrorState error={m.error} compact />}
      <Button type="submit" loading={m.isPending} icon={<CheckCircle2 className="h-4 w-4" />}>
        I&apos;ve applied
      </Button>
    </form>
  );
}

function Overview({ app, job }: { app: ApplicationRecord; job: DiscoveryJob | undefined }) {
  const url = applyUrlOf(app, job);
  const manual = app.workflowState === 'MANUAL_ACTION_REQUIRED';
  return (
    <div className="space-y-5">
      {manual && (
        <div className="rounded-xl border border-warning/30 bg-warning-soft p-4">
          <p className="text-sm font-semibold text-fg">Apply on the official site</p>
          <p className="mt-1 text-sm text-fg-muted">
            This source does not support automatic submission. Apply yourself on the employer&apos;s site, then record it below.
          </p>
          <div className="mt-3">
            {url ? (
              <ExternalButtonLink href={url} variant="primary" icon={<ExternalLink className="h-4 w-4" />}>
                Apply on the official site
              </ExternalButtonLink>
            ) : (
              <Unavailable>No application link was provided by the source.</Unavailable>
            )}
          </div>
        </div>
      )}
      {app.failureReason && (
        <Notice tone="danger" title="Failure reason">
          {app.failureReason}
        </Notice>
      )}
      <KeyValue
        items={[
          { label: 'Status', value: <Badge tone={stateTone(app.workflowState)}>{stateLabel(app.workflowState)}</Badge> },
          { label: 'Job', value: jobLabel(job, app.jobId) },
          { label: 'Location', value: job?.location ?? <Unavailable /> },
          { label: 'Source', value: job?.source ?? app.connectorId ?? <Unavailable /> },
          { label: 'Submission method', value: app.submissionMethod ? humanize(app.submissionMethod) : <Unavailable /> },
          { label: 'Match score', value: isNum(app.matchScore) ? Math.round(app.matchScore) : <Unavailable /> },
          { label: 'Created', value: formatDateTime(app.createdAt) },
          { label: 'Last updated', value: formatDateTime(app.updatedAt) },
          { label: 'Submitted', value: app.submittedAt ? formatDateTime(app.submittedAt) : <Unavailable>Not submitted</Unavailable> },
          { label: 'Retries', value: app.retryCount },
          { label: 'External application ID', value: app.externalApplicationId ?? <Unavailable /> },
        ]}
      />
      {url && !manual && (
        <a href={url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline">
          View the job posting <ExternalLink className="h-3.5 w-3.5" />
        </a>
      )}
      <div className="flex flex-wrap gap-2 border-t border-border pt-4">
        <ButtonLink to="/follow-ups" variant="secondary" size="sm" icon={<Send className="h-3.5 w-3.5" />}>
          Follow-ups
        </ButtonLink>
        <ButtonLink to={`/interviews?applicationId=${app.applicationId}`} variant="secondary" size="sm" icon={<Mic className="h-3.5 w-3.5" />}>
          Practice interview
        </ButtonLink>
        <ButtonLink to={`/cover-letters?applicationId=${app.applicationId}`} variant="secondary" size="sm" icon={<PenLine className="h-3.5 w-3.5" />}>
          Cover letter
        </ButtonLink>
      </div>
      {!TERMINAL_STATES.includes(app.workflowState) && app.workflowState !== 'SUBMITTED' && app.workflowState !== 'SUBMITTED_VERIFIED' && (
        <div className="rounded-xl border border-border p-4">
          <p className="mb-3 text-sm font-semibold text-fg">Already applied yourself?</p>
          <VerifyForm app={app} />
        </div>
      )}
    </div>
  );
}

/* -------------------------------------------------------------- actions */
function ActionButton({
  app,
  action,
  label,
  variant = 'primary',
  reason,
}: {
  app: ApplicationRecord;
  action: ApplicationAction;
  label: string;
  variant?: 'primary' | 'secondary' | 'danger';
  reason?: string;
}) {
  const invalidate = useInvalidateApp(app.applicationId);
  const m = useMutation({
    mutationFn: () => applicationsApi.action(app.applicationId, action, reason),
    onSuccess: (r) => {
      toast.success(`${label}: done`, `Status is now ${stateLabel(r.workflowState)}.`);
      invalidate();
    },
  });
  return (
    <div className="space-y-2">
      <Button variant={variant} loading={m.isPending} onClick={() => m.mutate()}>
        {label}
      </Button>
      {m.isError && <ErrorState error={m.error} compact />}
    </div>
  );
}

function Preflight({ id }: { id: string }) {
  const q = useQuery({ queryKey: qk.applicationPreflight(id), queryFn: () => applicationsApi.preflight(id) });
  if (q.isLoading) return <SkeletonRows rows={2} />;
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} compact />;
  const p = q.data;
  if (!p) return null;
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={p.allowed ? 'success' : 'warning'}>{p.allowed ? 'Submission allowed' : 'Submission not allowed'}</Badge>
        <span className="text-xs text-fg-muted">Suggested next state: {stateLabel(p.suggestedState)}</span>
      </div>
      <ul className="space-y-1.5">
        {p.checks.map((c) => (
          <li key={c.name} className="flex items-start gap-2 text-sm">
            {c.passed ? <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-success" /> : <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-danger" />}
            <span>
              <span className="font-medium text-fg">{humanize(c.name)}</span>
              <span className="text-fg-muted"> — {c.details}</span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function Actions({ app }: { app: ApplicationRecord }) {
  const s = app.workflowState;
  const terminal = TERMINAL_STATES.includes(s);
  const [rejectReason, setRejectReason] = useState('');
  const [target, setTarget] = useState<string>('');
  const [statusReason, setStatusReason] = useState('');
  const invalidate = useInvalidateApp(app.applicationId);
  const status = useMutation({
    mutationFn: () => applicationsApi.updateStatus(app.applicationId, target as WorkflowState, statusReason.trim() || undefined),
    onSuccess: (r) => {
      toast.success('Status updated', `Now ${stateLabel(r.workflowState)}.`);
      setTarget('');
      setStatusReason('');
      invalidate();
    },
  });

  const canApprove = s === 'APPLICATION_READY' || s === 'READY_FOR_APPROVAL';
  const canSubmit = s === 'APPROVED' || s === 'RETRYING';
  const canRetry = s === 'SUBMISSION_FAILED';

  return (
    <div className="space-y-6">
      {terminal && (
        <Notice tone="neutral" title="This application is closed">
          It is in a final state ({stateLabel(s)}); no further actions are possible.
        </Notice>
      )}
      {!terminal && (
        <>
          {(canApprove || canSubmit || canRetry) ? (
            <div className="flex flex-wrap gap-3">
              {canApprove && <ActionButton app={app} action="approve" label="Approve" />}
              {canSubmit && <ActionButton app={app} action="submit" label="Submit" />}
              {canRetry && <ActionButton app={app} action="retry" label="Retry submission" variant="secondary" />}
            </div>
          ) : (
            <p className="text-sm text-fg-muted">No approve, submit or retry action applies in the current status ({stateLabel(s)}).</p>
          )}

          <section className="space-y-3 rounded-xl border border-border p-4">
            <h3 className="text-sm font-semibold text-fg">Update status</h3>
            <p className="text-xs text-fg-muted">Record news you received outside CareerPilot. The server rejects transitions that are not allowed.</p>
            <div className="grid gap-3 sm:grid-cols-2">
              <Select
                label="New status"
                value={target}
                onChange={(e) => setTarget(e.target.value)}
                placeholder="Select…"
                options={MANUAL_STATUS_TARGETS.filter((t) => t !== s).map((t) => ({ value: t, label: stateLabel(t) }))}
              />
              <Input label="Reason (optional)" value={statusReason} onChange={(e) => setStatusReason(e.target.value)} />
            </div>
            {status.isError && <ErrorState error={status.error} compact />}
            <Button variant="secondary" disabled={!target} loading={status.isPending} onClick={() => status.mutate()}>
              Update status
            </Button>
          </section>

          <section className="space-y-3 rounded-xl border border-danger/25 p-4">
            <h3 className="text-sm font-semibold text-fg">Reject application</h3>
            <Textarea label="Reason (optional)" rows={2} value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} />
            <ActionButton app={app} action="reject" label="Reject" variant="danger" reason={rejectReason.trim() || undefined} />
          </section>
        </>
      )}
      <section className="space-y-3">
        <h3 className="text-sm font-semibold text-fg">Submission preflight</h3>
        <Preflight id={app.applicationId} />
      </section>
    </div>
  );
}

/* ------------------------------------------------------------- timeline */
function TimelineItem({ e }: { e: TimelineEntry }) {
  const isComm = e.source === 'COMMUNICATION';
  return (
    <li className="relative pl-8">
      <span
        className={cn(
          'absolute left-0 top-1 flex h-6 w-6 items-center justify-center rounded-full border',
          isComm ? 'border-info/30 bg-info-soft text-info' : 'border-primary/30 bg-primary-soft text-primary',
        )}
        aria-hidden
      >
        {isComm ? <Mail className="h-3.5 w-3.5" /> : <GitCommitHorizontal className="h-3.5 w-3.5" />}
      </span>
      <div className={cn('rounded-lg border p-3', isComm ? 'border-info/25 bg-surface' : 'border-border bg-surface')}>
        <div className="flex flex-wrap items-center gap-1.5">
          <span className="text-sm font-medium text-fg">{isComm ? 'HR email' : 'Status change'}</span>
          {e.actorType && <Badge tone={e.actorType === 'USER' ? 'primary' : e.actorType === 'COMMUNICATION' ? 'info' : 'neutral'}>{humanize(e.actorType)}</Badge>}
          {isComm && e.eventType && <Badge tone="neutral">{humanize(e.eventType)}</Badge>}
          {e.outcome && (isComm || e.outcome !== 'STATE_TRANSITIONED') && <Badge tone={outcomeTone(e.outcome)}>{humanize(e.outcome)}</Badge>}
          <span className="ml-auto text-[11px] text-fg-subtle">{formatDateTime(e.timestamp)}</span>
        </div>
        {(e.fromState || e.toState) && (
          <p className="mt-1.5 flex flex-wrap items-center gap-1.5 text-xs text-fg-muted">
            {e.fromState && <span>{e.fromState === 'INITIAL' ? 'Created' : stateLabel(e.fromState)}</span>}
            {e.fromState && e.toState && <ArrowRight className="h-3 w-3" aria-label="to" />}
            {e.toState && <span className="font-medium text-fg">{stateLabel(e.toState)}</span>}
          </p>
        )}
        {isComm && e.classification && (
          <p className="mt-1.5 text-xs text-fg-muted">
            Classified as <span className="font-medium text-fg">{humanize(e.classification)}</span>
            {isNum(e.classificationConfidence) && <> · confidence {Math.round(e.classificationConfidence * 100)}%</>}
          </p>
        )}
        {e.evidence && <p className="mt-1 text-xs italic text-fg-muted">Evidence: “{e.evidence}”</p>}
        {e.reason && <p className="mt-1 text-xs text-fg-muted">Reason: {e.reason}</p>}
        {isComm && e.outcome && OUTCOME_EXPLANATIONS[e.outcome] && (
          <p className="mt-1.5 text-xs text-fg-subtle">{OUTCOME_EXPLANATIONS[e.outcome]}</p>
        )}
        {isComm && e.communicationId && (
          <Link to={`/inbox?id=${e.communicationId}`} className="mt-1.5 inline-block text-xs font-medium text-primary hover:underline">
            Open in Inbox
          </Link>
        )}
      </div>
    </li>
  );
}

function Timeline({ id }: { id: string }) {
  const q = useQuery({ queryKey: qk.applicationTimeline(id), queryFn: () => applicationsApi.timeline(id) });
  if (q.isLoading) return <SkeletonRows rows={3} />;
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} />;
  if (!q.data || q.data.length === 0) return <EmptyState compact title="No events yet" description="Status changes and HR emails will appear here." />;
  return (
    <div>
      <div className="mb-4 flex flex-wrap gap-3 text-xs text-fg-muted">
        <span className="inline-flex items-center gap-1"><GitCommitHorizontal className="h-3.5 w-3.5 text-primary" /> Status change</span>
        <span className="inline-flex items-center gap-1"><Mail className="h-3.5 w-3.5 text-info" /> HR email</span>
      </div>
      <ol className="space-y-3">
        {q.data.map((e, i) => (
          <TimelineItem key={`${e.timestamp}-${i}`} e={e} />
        ))}
      </ol>
    </div>
  );
}

/* ------------------------------------------------------------- decision */
const RECOMMENDATION_TONE = {
  RECOMMENDED_TO_APPLY: 'success',
  APPLY_WITH_CAUTION: 'warning',
  NOT_RECOMMENDED: 'danger',
  INSUFFICIENT_DATA: 'neutral',
} as const;

function Decision({ id }: { id: string }) {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: qk.applicationDecision(id), queryFn: () => applicationsApi.decision(id) });
  const evaluate = useMutation({
    mutationFn: () => applicationsApi.evaluateDecision(id),
    onSuccess: (d) => qc.setQueryData(qk.applicationDecision(id), d),
  });
  const notFound = q.isError && toApiError(q.error).status === 404;

  if (q.isLoading) return <SkeletonRows rows={2} />;
  const evalButton = (
    <Button loading={evaluate.isPending} onClick={() => evaluate.mutate()} variant={q.data ? 'secondary' : 'primary'}>
      {q.data ? 'Re-evaluate' : 'Evaluate'}
    </Button>
  );
  return (
    <div className="space-y-4">
      {evaluate.isPending && <AiProgress label="Evaluating this application" />}
      {evaluate.isError && <ErrorState error={evaluate.error} />}
      {q.isError && !notFound && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
      {notFound && !evaluate.isPending && (
        <EmptyState compact title="No decision yet" description="Evaluate this application to get an apply / don't-apply recommendation based on your match." action={evalButton} />
      )}
      {q.data && (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={RECOMMENDATION_TONE[q.data.recommendation] ?? 'neutral'}>{humanize(q.data.recommendation)}</Badge>
            <span className="text-xs text-fg-subtle">Evaluated {formatDateTime(q.data.evaluatedAt)}</span>
            <span className="ml-auto">{evalButton}</span>
          </div>
          {q.data.decisionRationale && <p className="text-sm text-fg">{q.data.decisionRationale}</p>}
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-fg-subtle">Strengths</h4>
              <BulletList items={parseJson<string[]>(q.data.strengthsJson)} />
            </div>
            <div>
              <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-fg-subtle">Critical gaps</h4>
              <BulletList items={parseJson<string[]>(q.data.criticalGapsJson)} />
            </div>
          </div>
          <KeyValue
            items={[{ label: 'Recommended resume', value: q.data.recommendedResumeTitle ?? <Unavailable /> }]}
          />
        </>
      )}
    </div>
  );
}

/* -------------------------------------------------------------- package */
function Package({ id }: { id: string }) {
  const q = useQuery({ queryKey: qk.applicationPackage(id), queryFn: () => applicationsApi.package(id) });
  if (q.isLoading) return <SkeletonRows rows={2} />;
  if (q.isError) {
    if (toApiError(q.error).status === 404)
      return <EmptyState compact title="No application package yet" description="A package is generated when the application is prepared." />;
    return <ErrorState error={q.error} onRetry={() => q.refetch()} />;
  }
  const p = q.data;
  if (!p) return null;
  const resume = parseJson<{ resumeId?: string | null; title?: string | null }>(p.selectedResumeJson);
  const jobDetails = parseJson<{ title?: string; company?: string; location?: string; applyUrl?: string }>(p.jobDetailsJson);
  const capability = parseJson<SubmissionCapability>(p.submissionCapabilityJson);
  const preflight = parseJson<PreflightResult>(p.preflightResultJson);
  const match = parseJson<{ overallScore?: number }>(p.matchResultJson);
  return (
    <div className="space-y-4">
      <KeyValue
        items={[
          { label: 'Job', value: jobDetails?.title ? `${jobDetails.title}${jobDetails.company ? ` · ${jobDetails.company}` : ''}` : <Unavailable /> },
          { label: 'Location', value: jobDetails?.location ?? <Unavailable /> },
          { label: 'Selected resume', value: resume?.title ?? <Unavailable>No resume selected</Unavailable> },
          { label: 'Match score', value: isNum(match?.overallScore) ? Math.round(match.overallScore) : <Unavailable /> },
          { label: 'Submission mode', value: capability?.submissionMode ? humanize(capability.submissionMode) : <Unavailable /> },
          { label: 'Preflight', value: preflight ? (preflight.allowed ? 'Allowed' : 'Not allowed') : <Unavailable /> },
          { label: 'Generated', value: formatDateTime(p.generatedAt) },
        ]}
      />
      {capability?.reason && <Notice tone="neutral">{capability.reason}</Notice>}
      {p.officialApplyUrl ? (
        <ExternalButtonLink href={p.officialApplyUrl} variant="secondary" icon={<ExternalLink className="h-4 w-4" />}>
          Official application page
        </ExternalButtonLink>
      ) : (
        <Unavailable>No official application URL in the package.</Unavailable>
      )}
    </div>
  );
}

/* ---------------------------------------------------------- preparation */
function Preparation({ id }: { id: string }) {
  const [result, setResult] = useState<ApplicationPreparation | null>(null);
  const m = useMutation({ mutationFn: () => applicationsApi.preparation(id), onSuccess: setResult });
  const sections: Array<[string, keyof ApplicationPreparation]> = [
    ['Checklist', 'checklist'],
    ['Talking points', 'talkingPoints'],
    ['Tailoring tips', 'tailoringTips'],
    ['Questions to ask', 'questionsToAsk'],
    ['Risks', 'risks'],
  ];
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-fg-muted">Generate a preparation brief grounded in your resume and this job.</p>
        <Button icon={<Sparkles className="h-4 w-4" />} loading={m.isPending} onClick={() => m.mutate()}>
          {result ? 'Regenerate' : 'Generate preparation'}
        </Button>
      </div>
      {m.isPending && <AiProgress label="Preparing your application brief" />}
      {m.isError && <ErrorState error={m.error} />}
      {result && !m.isPending && (
        <div className="space-y-4">
          <AiLabel />
          {sections.map(([label, key]) => (
            <div key={key}>
              <h4 className="mb-2 text-xs font-semibold uppercase tracking-wide text-fg-subtle">{label}</h4>
              <BulletList items={result[key] as string[] | null} />
            </div>
          ))}
          <VerificationNotice verification={result.verification} />
        </div>
      )}
    </div>
  );
}

/* --------------------------------------------------------------- drawer */
export function ApplicationDetail({
  applicationId,
  job,
  onClose,
}: {
  applicationId: string;
  job: DiscoveryJob | undefined;
  onClose: () => void;
}) {
  const [tab, setTab] = useState<TabId>('overview');
  const q = useQuery({ queryKey: qk.application(applicationId), queryFn: () => applicationsApi.get(applicationId) });
  const app = q.data;
  return (
    <Modal
      open
      onClose={onClose}
      variant="drawer"
      size="xl"
      title={app ? jobLabel(job, app.jobId) : 'Application'}
      description={app ? <Badge tone={stateTone(app.workflowState)}>{stateLabel(app.workflowState)}</Badge> : undefined}
    >
      {q.isLoading && <SkeletonRows rows={4} />}
      {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
      {app && (
        <div className="space-y-4">
          <Tabs<TabId>
            ariaLabel="Application sections"
            value={tab}
            onChange={setTab}
            items={[
              { id: 'overview', label: 'Overview' },
              { id: 'actions', label: 'Actions' },
              { id: 'timeline', label: 'Timeline' },
              { id: 'decision', label: 'Decision' },
              { id: 'package', label: 'Package' },
              { id: 'preparation', label: 'AI preparation' },
            ]}
          />
          <div role="tabpanel">
            {tab === 'overview' && <Overview app={app} job={job} />}
            {tab === 'actions' && <Actions app={app} />}
            {tab === 'timeline' && <Timeline id={applicationId} />}
            {tab === 'decision' && <Decision id={applicationId} />}
            {tab === 'package' && <Package id={applicationId} />}
            {tab === 'preparation' && <Preparation id={applicationId} />}
          </div>
        </div>
      )}
    </Modal>
  );
}
