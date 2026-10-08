import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  Bot,
  Briefcase,
  CheckCircle2,
  Circle,
  FileText,
  HeartPulse,
  Mic,
  Send,
  Target,
  User,
} from 'lucide-react';
import { analyticsApi, copilotApi, opportunitiesApi } from '../../api/endpoints';
import { qk, useApplications, useFollowUps, useProfile, useResumes, isProcessing } from '../../api/queries';
import type { CareerHealth, HealthComponentKey, RateMetric, Resume } from '../../api/types';
import {
  Badge,
  ButtonLink,
  Card,
  CardBody,
  CardHeader,
  EmptyState,
  ErrorState,
  PageHeader,
  ScoreRing,
  SkeletonRows,
  Stat,
} from '../../components/ui';
import { formatDate, formatRatio, humanize, isNum } from '../../lib/format';
import { priorityTone, urgencyTone } from '../../lib/domain';
import { useAuthStore } from '../../store/auth';

const HEALTH_LABELS: Record<HealthComponentKey, string> = {
  resumeQuality: 'Resume quality',
  profileCompleteness: 'Profile completeness',
  applicationResponseRate: 'Application response rate',
  interviewReadiness: 'Interview readiness',
  learningProgress: 'Learning progress',
};

function HealthCard() {
  const q = useQuery({ queryKey: qk.health, queryFn: copilotApi.health });
  return (
    <Card>
      <CardHeader
        icon={<HeartPulse className="h-4 w-4" />}
        title="Career health"
        description="Each component is measured only from your own data. Unmeasured parts are not counted."
      />
      <CardBody>
        {q.isLoading && <SkeletonRows rows={3} />}
        {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
        {q.data && <HealthBody data={q.data} />}
      </CardBody>
    </Card>
  );
}

function HealthBody({ data }: { data: CareerHealth }) {
  const keys = Object.keys(HEALTH_LABELS) as HealthComponentKey[];
  return (
    <div className="flex flex-col gap-5 md:flex-row md:items-start">
      <div className="flex shrink-0 flex-col items-center gap-1 md:w-40">
        <ScoreRing
          size={104}
          score={data.available && isNum(data.overallScore) ? data.overallScore * 100 : null}
          label="Overall"
          emptyText="N/A"
        />
        <p className="text-center text-xs text-fg-subtle">
          {data.available
            ? `Based on ${data.measuredComponents} measured component${data.measuredComponents === 1 ? '' : 's'}`
            : 'Not enough data to compute an overall score yet'}
        </p>
      </div>
      <ul className="grid flex-1 gap-3 sm:grid-cols-2">
        {keys.map((k) => {
          const c = data.components[k];
          return (
            <li key={k} className="rounded-lg border border-border bg-surface-2 p-3">
              <div className="flex items-center justify-between gap-2">
                <p className="text-sm font-medium text-fg">{HEALTH_LABELS[k]}</p>
                {c && c.available ? (
                  <span className="text-sm font-semibold tabular-nums text-fg">{Math.round(c.score * 100)}</span>
                ) : (
                  <Badge>Not measured</Badge>
                )}
              </div>
              <p className="mt-1 text-xs text-fg-muted">
                {!c ? 'Not available' : c.available ? c.evidence : c.reason}
              </p>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function rateValue(r: RateMetric | undefined) {
  if (!r || !isNum(r.value)) return null;
  return formatRatio(r.value);
}

function KeyMetrics() {
  const q = useQuery({ queryKey: qk.careerDashboard, queryFn: analyticsApi.careerDashboard });
  if (q.isLoading) return <SkeletonRows rows={2} />;
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} />;
  const d = q.data;
  if (!d) return null;
  const rate = (r: RateMetric) => (isNum(r.value) ? `${r.numerator} of ${r.denominator}` : r.reason ?? undefined);
  return (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
      <Stat label="Applications" value={d.counts.applications} />
      <Stat label="Submitted" value={d.counts.submitted} />
      <Stat label="Interviews" value={d.counts.interviews} />
      <Stat label="Offers" value={d.counts.offers} />
      <Stat label="Response rate" value={rateValue(d.rates.responseRate)} hint={rate(d.rates.responseRate)} />
      <Stat label="Interview rate" value={rateValue(d.rates.interviewRate)} hint={rate(d.rates.interviewRate)} />
      <Stat label="Offer rate" value={rateValue(d.rates.offerRate)} hint={rate(d.rates.offerRate)} />
      <Stat
        label="Avg. response time"
        value={isNum(d.rates.averageResponseTimeDays.value) ? `${d.rates.averageResponseTimeDays.value} days` : null}
        hint={
          isNum(d.rates.averageResponseTimeDays.value)
            ? `${d.rates.averageResponseTimeDays.samples} samples`
            : d.rates.averageResponseTimeDays.reason
        }
      />
    </div>
  );
}

function FollowUpsCard() {
  const q = useFollowUps();
  const items = (q.data ?? []).slice(0, 5);
  return (
    <Card>
      <CardHeader
        icon={<Send className="h-4 w-4" />}
        title="Due follow-ups"
        actions={
          <ButtonLink to="/follow-ups" variant="ghost" size="sm">
            All <ArrowRight className="h-3.5 w-3.5" />
          </ButtonLink>
        }
      />
      <CardBody>
        {q.isLoading && <SkeletonRows rows={3} />}
        {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} compact />}
        {q.data && items.length === 0 && <EmptyState compact title="Nothing to follow up" description="Recommendations appear as your applications progress." />}
        <ul className="divide-y divide-border">
          {items.map((f) => (
            <li key={f.key} className="py-2.5">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-fg">
                    {f.jobTitle ?? 'Application'}
                    {f.company && <span className="text-fg-muted"> · {f.company}</span>}
                  </p>
                  <p className="mt-0.5 text-xs text-fg-muted">{f.reason}</p>
                </div>
                <Badge tone={urgencyTone(f.urgency)}>{humanize(f.urgency)}</Badge>
              </div>
              <p className="mt-1 text-[11px] text-fg-subtle">Recommended: {formatDate(f.recommendedDate)}</p>
            </li>
          ))}
        </ul>
      </CardBody>
    </Card>
  );
}

function OpportunitiesCard() {
  const q = useQuery({ queryKey: qk.opportunities({ dashboard: '1' }), queryFn: () => opportunitiesApi.list({ size: 50 }) });
  const items = [...(q.data?.content ?? [])].sort((a, b) => b.priorityScore - a.priorityScore).slice(0, 5);
  return (
    <Card>
      <CardHeader
        icon={<Target className="h-4 w-4" />}
        title="Top opportunities"
        actions={
          <ButtonLink to="/opportunities" variant="ghost" size="sm">
            All <ArrowRight className="h-3.5 w-3.5" />
          </ButtonLink>
        }
      />
      <CardBody>
        {q.isLoading && <SkeletonRows rows={3} />}
        {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} compact />}
        {q.data && items.length === 0 && (
          <EmptyState compact title="No opportunities yet" description="Opportunities appear once jobs have been discovered." />
        )}
        <ul className="divide-y divide-border">
          {items.map((o) => (
            <li key={o.jobId} className="py-2.5">
              <Link to={`/jobs?jobId=${o.jobId}`} className="flex items-start justify-between gap-2 rounded hover:bg-surface-2">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-fg">{o.title ?? 'Untitled job'}</p>
                  <p className="truncate text-xs text-fg-muted">{[o.company, o.location].filter(Boolean).join(' · ') || 'Company not provided'}</p>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1">
                  <Badge tone={priorityTone(o.priorityLevel)}>{humanize(o.priorityLevel)}</Badge>
                  {o.matchAvailable === true ? (
                    <span className="text-xs tabular-nums text-fg-muted">Match {Math.round(o.matchScore)}</span>
                  ) : (
                    <Badge>Unscored</Badge>
                  )}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </CardBody>
    </Card>
  );
}

function resumeStatus(r: Resume) {
  if (isProcessing(r)) return <Badge tone="info">Processing</Badge>;
  if (r.aiProcessingStatus === 'READY') return <Badge tone="success">Ready</Badge>;
  if (r.aiProcessingStatus === 'FAILED') return <Badge tone="danger">Failed</Badge>;
  return <Badge>{humanize(r.aiProcessingStatus) || 'Unknown'}</Badge>;
}

function ResumeCard() {
  const q = useResumes();
  const def = q.data?.find((r) => r.isDefault) ?? q.data?.[0];
  return (
    <Card>
      <CardHeader icon={<FileText className="h-4 w-4" />} title="Resume" />
      <CardBody>
        {q.isLoading && <SkeletonRows rows={1} />}
        {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} compact />}
        {q.data && !def && (
          <EmptyState compact title="No resume uploaded" action={<ButtonLink to="/resumes" size="sm">Upload resume</ButtonLink>} />
        )}
        {def && (
          <div className="space-y-2">
            <div className="flex items-center justify-between gap-2">
              <p className="truncate text-sm font-medium text-fg">{def.title}</p>
              {resumeStatus(def)}
            </div>
            <p className="text-xs text-fg-muted">
              {def.isDefault ? 'Default resume' : 'Most recent resume'} · uploaded {formatDate(def.uploadedAt)}
            </p>
            {def.aiProcessingStatus === 'FAILED' && def.processingError && <p className="text-xs text-danger">{def.processingError}</p>}
            <ButtonLink to={`/resumes?resumeId=${def.id}`} variant="secondary" size="sm">
              Open resume
            </ButtonLink>
          </div>
        )}
      </CardBody>
    </Card>
  );
}

function Onboarding() {
  const resumes = useResumes();
  const profile = useProfile();
  const apps = useApplications();
  if (!resumes.data || !profile.data || !apps.data) return null;
  const p = profile.data;
  const steps = [
    { done: resumes.data.length > 0, label: 'Upload your resume', to: '/resumes', icon: FileText },
    {
      done: Boolean((p.preferences?.preferredRoles?.length ?? 0) > 0 || (p.experience?.length ?? 0) > 0),
      label: 'Complete your profile and job preferences',
      to: '/profile',
      icon: User,
    },
    { done: apps.data.length > 0, label: 'Track your first application', to: '/jobs', icon: Briefcase },
  ];
  if (steps.every((s) => s.done)) return null;
  return (
    <Card className="mb-6">
      <CardHeader title="Get started" description="A few steps so CareerPilot has real data to work with." />
      <CardBody>
        <ol className="space-y-2">
          {steps.map((s) => (
            <li key={s.label}>
              <Link to={s.to} className="flex items-center gap-3 rounded-lg px-2 py-2 hover:bg-surface-2">
                {s.done ? (
                  <CheckCircle2 className="h-5 w-5 text-success" aria-label="Done" />
                ) : (
                  <Circle className="h-5 w-5 text-fg-subtle" aria-label="To do" />
                )}
                <span className={s.done ? 'text-sm text-fg-muted line-through' : 'text-sm font-medium text-fg'}>{s.label}</span>
                <ArrowRight className="ml-auto h-4 w-4 text-fg-subtle" aria-hidden />
              </Link>
            </li>
          ))}
        </ol>
      </CardBody>
    </Card>
  );
}

const QUICK_LINKS = [
  { to: '/jobs', label: 'Search jobs', icon: Briefcase },
  { to: '/interviews', label: 'Practice interview', icon: Mic },
  { to: '/copilot', label: 'Ask the Copilot', icon: Bot },
  { to: '/follow-ups', label: 'Follow-ups', icon: Send },
];

export function DashboardPage() {
  const user = useAuthStore((s) => s.user);
  return (
    <>
      <PageHeader
        title="Dashboard"
        description={user?.firstName ? `Welcome back, ${user.firstName}. Everything below comes from your own data.` : 'Everything below comes from your own data.'}
      />
      <Onboarding />
      <div className="space-y-6">
        <HealthCard />
        <section aria-label="Key metrics">
          <h2 className="mb-3 text-sm font-semibold text-fg">Key metrics</h2>
          <KeyMetrics />
        </section>
        <div className="grid gap-6 lg:grid-cols-2">
          <FollowUpsCard />
          <OpportunitiesCard />
        </div>
        <div className="grid gap-6 lg:grid-cols-3">
          <ResumeCard />
          <Card className="lg:col-span-2">
            <CardHeader title="Quick links" />
            <CardBody className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              {QUICK_LINKS.map((l) => (
                <Link
                  key={l.to}
                  to={l.to}
                  className="flex flex-col items-center gap-2 rounded-lg border border-border p-4 text-center text-sm font-medium text-fg hover:bg-surface-2"
                >
                  <l.icon className="h-5 w-5 text-primary" aria-hidden />
                  {l.label}
                </Link>
              ))}
            </CardBody>
          </Card>
        </div>
      </div>
    </>
  );
}

