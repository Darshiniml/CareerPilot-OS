import { useQuery } from '@tanstack/react-query';
import { Activity, BarChart3, Clock, GraduationCap, Mic, RefreshCw, Sparkles } from 'lucide-react';
import { analyticsApi, applicationsApi } from '../../api/endpoints';
import { qk } from '../../api/queries';
import type { CareerDashboard, RateMetric } from '../../api/types';
import {
  BarList,
  Button,
  Card,
  CardBody,
  CardHeader,
  EmptyState,
  ErrorState,
  PageHeader,
  ScoreRing,
  Skeleton,
  Stat,
  type BarDatum,
} from '../../components/ui';
import { stateLabel } from '../../lib/domain';
import { formatDateTime, formatRatio, humanize, isNum } from '../../lib/format';

function toBars(rec: Record<string, number> | null | undefined, label: (k: string) => string = (k) => k): BarDatum[] {
  return Object.entries(rec ?? {})
    .filter(([, v]) => isNum(v))
    .map(([k, v]) => ({ label: label(k), value: v }))
    .sort((a, b) => b.value - a.value);
}

function RateTile({ label, rate }: { label: string; rate: RateMetric | undefined }) {
  if (!rate) return <Stat label={label} value={null} emptyText="Not available" />;
  return (
    <Stat
      label={label}
      value={isNum(rate.value) ? formatRatio(rate.value) : null}
      hint={isNum(rate.value) ? `${rate.numerator} of ${rate.denominator}` : rate.reason ?? `${rate.numerator} of ${rate.denominator}`}
    />
  );
}

function BreakdownCard({ title, data, emptyDescription }: { title: string; data: BarDatum[]; emptyDescription: string }) {
  return (
    <Card>
      <CardHeader title={title} />
      <CardBody>
        <BarList data={data.slice(0, 12)} ariaLabel={title} emptyTitle="No data yet" emptyDescription={emptyDescription} />
        {data.length > 12 && <p className="mt-3 text-xs text-fg-subtle">Showing the top 12 of {data.length}.</p>}
      </CardBody>
    </Card>
  );
}

function Dashboard({ d }: { d: CareerDashboard }) {
  const c = d.counts;
  const readiness = d.interviewReadiness;
  const skills = d.skills;
  return (
    <div className="space-y-6">
      <section aria-label="Counts" className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <Stat label="Applications" value={c.applications} />
        <Stat label="Submitted" value={c.submitted} />
        <Stat label="Responses" value={c.responses} />
        <Stat label="Interviews" value={c.interviews} />
        <Stat label="Offers" value={c.offers} />
        <Stat label="Rejections" value={c.rejections} />
      </section>

      <section aria-label="Rates" className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <RateTile label="Response rate" rate={d.rates.responseRate} />
        <RateTile label="Interview rate" rate={d.rates.interviewRate} />
        <RateTile label="Offer rate" rate={d.rates.offerRate} />
        <Stat
          label="Avg. response time"
          icon={<Clock className="h-4 w-4" />}
          value={isNum(d.rates.averageResponseTimeDays?.value) ? `${d.rates.averageResponseTimeDays.value} days` : null}
          hint={
            isNum(d.rates.averageResponseTimeDays?.value)
              ? `${d.rates.averageResponseTimeDays.samples} responses measured`
              : d.rates.averageResponseTimeDays?.reason
          }
        />
      </section>

      <section className="grid gap-4 lg:grid-cols-2" aria-label="Breakdowns">
        <BreakdownCard
          title="Applications by status"
          data={toBars(d.breakdowns.byState, stateLabel)}
          emptyDescription="Track an application to see its status here."
        />
        <BreakdownCard title="By company" data={toBars(d.breakdowns.byCompany)} emptyDescription="No applications with a known company yet." />
        <BreakdownCard title="By role" data={toBars(d.breakdowns.byRole)} emptyDescription="No applications with a known role yet." />
        <BreakdownCard title="By source" data={toBars(d.breakdowns.bySource)} emptyDescription="No applications with a known source yet." />
      </section>

      <Card>
        <CardHeader
          icon={<Sparkles className="h-4 w-4" />}
          title="Skill landscape"
          description={
            skills.available
              ? `Across ${skills.analysedJobs} analysed job${skills.analysedJobs === 1 ? '' : 's'}`
              : undefined
          }
        />
        <CardBody>
          {!skills.available ? (
            <EmptyState
              compact
              title="Not enough data"
              description={skills.reason ?? 'Analyse some jobs to compare their requirements with your resume.'}
            />
          ) : (
            <div className="grid gap-6 md:grid-cols-2">
              <div>
                <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-danger">Missing skills (jobs requiring)</h3>
                <BarList
                  data={(skills.missingSkills ?? []).slice(0, 15).map((s) => ({ label: s.skill, value: s.jobs }))}
                  tone="danger"
                  emptyTitle="No missing skills found"
                  valueFormatter={(v) => `${v} job${v === 1 ? '' : 's'}`}
                />
              </div>
              <div>
                <h3 className="mb-3 text-xs font-semibold uppercase tracking-wide text-success">Matched skills (jobs requiring)</h3>
                <BarList
                  data={(skills.matchedSkills ?? []).slice(0, 15).map((s) => ({ label: s.skill, value: s.jobs }))}
                  tone="success"
                  emptyTitle="No matched skills yet"
                  valueFormatter={(v) => `${v} job${v === 1 ? '' : 's'}`}
                />
              </div>
            </div>
          )}
        </CardBody>
      </Card>

      <section className="grid gap-4 lg:grid-cols-3">
        <Card>
          <CardHeader icon={<Mic className="h-4 w-4" />} title="Interview readiness" />
          <CardBody className="flex flex-col items-center gap-2 text-center">
            {readiness && readiness.available ? (
              <>
                <ScoreRing score={Math.round(readiness.overallReadiness * 100)} size={96} label="Overall" />
                <p className="text-xs text-fg-muted">{readiness.answeredQuestions} answered practice questions</p>
              </>
            ) : (
              <EmptyState
                compact
                className="w-full"
                title="Not enough data"
                description={
                  readiness
                    ? `${readiness.message ?? 'Answer more practice questions.'} (${readiness.answeredQuestions} answered)`
                    : 'Readiness is not available.'
                }
              />
            )}
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="Weak interview areas" />
          <CardBody>
            <BarList
              data={(d.weakInterviewAreas ?? []).map((a) => ({ label: a.skillArea, value: Math.round(a.score * 100) }))}
              max={100}
              tone="warning"
              valueFormatter={(v) => `${v}%`}
              emptyTitle="No weak areas measured"
              emptyDescription="Practise interviews to identify areas to improve."
            />
          </CardBody>
        </Card>
        <Card>
          <CardHeader icon={<GraduationCap className="h-4 w-4" />} title="Learning progress" />
          <CardBody>
            <BarList
              data={(d.learningProgress ?? []).map((p) => ({ label: p.skill, value: Math.round(p.progressPercentage), hint: humanize(p.status) }))}
              max={100}
              tone="success"
              valueFormatter={(v) => `${v}%`}
              emptyTitle="No learning progress recorded"
              emptyDescription="Record progress on the Learning page."
            />
          </CardBody>
        </Card>
      </section>
      {d.generatedAt && <p className="text-xs text-fg-subtle">Generated {formatDateTime(d.generatedAt)}</p>}
    </div>
  );
}

function ApplicationStats() {
  const q = useQuery({ queryKey: qk.applicationStats, queryFn: applicationsApi.statistics });
  if (q.isLoading) return <Skeleton className="h-24 w-full" />;
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} compact />;
  const s = q.data;
  if (!s) return null;
  // Percentages 0..100; null when there are no applications.
  const p = (v: number | null) => (isNum(v) ? `${Math.round(v)}%` : null);
  return (
    <Card>
      <CardHeader icon={<Activity className="h-4 w-4" />} title="Submission pipeline" description="Rates over all of your tracked applications." />
      <CardBody className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <Stat label="Submitted" value={s.applicationsSubmitted} />
        <Stat label="Approval rate" value={p(s.approvalRate)} />
        <Stat label="Submission success" value={p(s.submissionSuccessRate)} />
        <Stat label="Failure rate" value={p(s.failureRate)} />
        <Stat label="Interview rate" value={p(s.interviewRate)} />
        <Stat label="Offer rate" value={p(s.offerRate)} />
      </CardBody>
    </Card>
  );
}

export function AnalyticsPage() {
  const q = useQuery({ queryKey: qk.careerDashboard, queryFn: analyticsApi.careerDashboard });
  return (
    <div>
      <PageHeader
        title="Analytics"
        description="Your real job-search numbers. Rates appear only once there is enough data to be meaningful."
        actions={
          <Button variant="secondary" size="sm" onClick={() => q.refetch()} loading={q.isFetching} icon={<RefreshCw className="h-3.5 w-3.5" />}>
            Refresh
          </Button>
        }
      />
      {q.isLoading ? (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-24" />
            ))}
          </div>
          <Skeleton className="h-64 w-full" />
        </div>
      ) : q.isError ? (
        <ErrorState error={q.error} onRetry={() => q.refetch()} />
      ) : q.data ? (
        <div className="space-y-6">
          <Dashboard d={q.data} />
          <ApplicationStats />
        </div>
      ) : (
        <EmptyState icon={<BarChart3 className="h-5 w-5" />} title="No analytics available" />
      )}
    </div>
  );
}
