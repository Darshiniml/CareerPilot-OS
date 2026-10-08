import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BookOpen, Flag, GraduationCap, Lightbulb, ListChecks, Pencil, Plus, Sparkles, TrendingUp } from 'lucide-react';
import { learningApi } from '../../api/endpoints';
import { qk } from '../../api/queries';
import type { GoalWithProgress, LearningGap, LearningPath, LearningResource } from '../../api/types';
import {
  AiLabel,
  AiProgress,
  Badge,
  Button,
  Card,
  CardBody,
  CardHeader,
  Checkbox,
  EmptyState,
  ErrorState,
  Input,
  Modal,
  Notice,
  PageHeader,
  ProgressBar,
  Select,
  SkeletonRows,
  Tabs,
} from '../../components/ui';
import { toast } from '../../store/toast';
import { priorityTone } from '../../lib/domain';
import { formatDate, formatRelative, humanize, isNum, parseJson } from '../../lib/format';

type TabId = 'recommendations' | 'plans' | 'progress' | 'goals';

const STATUS_OPTIONS = [
  { value: 'NOT_STARTED', label: 'Not started' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'COMPLETED', label: 'Completed' },
];

const SOURCE_LABEL: Record<string, string> = { JOB_DEMAND: 'Job demand', INTERVIEW_PRACTICE: 'Interview practice' };

/* ------------------------------------------------------- recommendations */

function Recommendations({ onPlan, onTrack }: { onPlan: (skill: string) => void; onTrack: (skill: string) => void }) {
  const q = useQuery({ queryKey: qk.learningRecs, queryFn: learningApi.recommendations });
  if (q.isLoading) return <SkeletonRows rows={4} />;
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} />;
  const gaps: LearningGap[] = q.data?.gaps ?? [];
  if (gaps.length === 0) {
    return (
      <EmptyState
        icon={<Lightbulb className="h-5 w-5" />}
        title="No skill gaps found"
        description={q.data?.reason ?? 'Analyse jobs and practise interviews to get evidence-based learning recommendations.'}
      />
    );
  }
  return (
    <ul className="grid gap-3 md:grid-cols-2">
      {gaps.map((g) => (
        <li key={`${g.skill}-${g.source}`}>
          <Card className="flex h-full flex-col gap-3 p-4">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <p className="text-sm font-semibold text-fg">{g.skill}</p>
              <div className="flex flex-wrap gap-1.5">
                <Badge tone={g.source === 'JOB_DEMAND' ? 'info' : 'primary'}>{SOURCE_LABEL[g.source] ?? humanize(g.source)}</Badge>
                <Badge tone={priorityTone(g.priority)}>{humanize(g.priority)} priority</Badge>
              </div>
            </div>
            <p className="flex-1 text-sm text-fg-muted">{g.evidence}</p>
            {isNum(g.demandJobs) && (
              <p className="text-xs text-fg-subtle">
                Required by {g.demandJobs} analysed job{g.demandJobs === 1 ? '' : 's'}
              </p>
            )}
            <div className="flex flex-wrap gap-2">
              <Button size="sm" icon={<Sparkles className="h-3.5 w-3.5" />} onClick={() => onPlan(g.skill)}>
                Plan this skill
              </Button>
              <Button size="sm" variant="secondary" onClick={() => onTrack(g.skill)}>
                Track progress
              </Button>
            </div>
          </Card>
        </li>
      ))}
    </ul>
  );
}

/* ------------------------------------------------------------------ plans */

function PlanView({ plan, onTrack }: { plan: LearningPath; onTrack?: (skill: string) => void }) {
  const steps = [...(plan.learningSequence ?? [])].sort((a, b) => a.sequenceNumber - b.sequenceNumber);
  return (
    <Card>
      <CardHeader
        icon={<BookOpen className="h-4 w-4" />}
        title={`Learning plan: ${plan.skill}`}
        description={[
          plan.currentLevel && plan.targetLevel ? `${humanize(plan.currentLevel)} → ${humanize(plan.targetLevel)}` : null,
          isNum(plan.estimatedHours) ? `${plan.estimatedHours} h estimated` : null,
          `Created ${formatDate(plan.createdAt)}`,
        ]
          .filter(Boolean)
          .join(' · ')}
        actions={
          <>
            <AiLabel>AI-generated plan</AiLabel>
            {plan.priority && <Badge tone={priorityTone(plan.priority)}>{humanize(plan.priority)} priority</Badge>}
          </>
        }
      />
      <CardBody className="space-y-5">
        {plan.demandEvidence && (
          <Notice tone="info" title="Evidence">
            {plan.demandEvidence}
          </Notice>
        )}
        {plan.whyItMatters && (
          <div>
            <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-fg-muted">Why it matters</p>
            <p className="text-sm text-fg">{plan.whyItMatters}</p>
          </div>
        )}
        <div>
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-fg-muted">Steps</p>
          {steps.length === 0 ? (
            <p className="text-sm italic text-fg-subtle">The plan contains no steps.</p>
          ) : (
            <ol className="space-y-3">
              {steps.map((s) => {
                const resources = parseJson<LearningResource[]>(s.resourcesJson) ?? [];
                return (
                  <li key={s.id} className="flex gap-3">
                    <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary-soft text-xs font-semibold text-primary-soft-fg">
                      {s.sequenceNumber}
                    </span>
                    <div className="min-w-0 flex-1 rounded-lg border border-border p-3">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm font-medium text-fg">{s.stepName}</p>
                        {isNum(s.estimatedHours) && <span className="text-xs text-fg-subtle">{s.estimatedHours} h</span>}
                      </div>
                      {s.description && <p className="mt-1 text-sm text-fg-muted">{s.description}</p>}
                      {resources.length > 0 && (
                        <div className="mt-2">
                          <p className="mb-1 text-xs font-medium text-fg-muted">Suggested resources (search for these by name)</p>
                          <ul className="flex flex-wrap gap-1.5">
                            {resources.map((r, i) => (
                              <li key={`${r.name}-${i}`} className="rounded-md border border-border bg-surface-2 px-2 py-0.5 text-xs text-fg">
                                {r.name}
                                {r.type && <span className="ml-1 text-fg-subtle">({humanize(r.type)})</span>}
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}
                    </div>
                  </li>
                );
              })}
            </ol>
          )}
        </div>
        {plan.practiceProject && (
          <div>
            <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-fg-muted">Practice project</p>
            <p className="text-sm text-fg">{plan.practiceProject}</p>
          </div>
        )}
        {onTrack && (
          <Button size="sm" variant="secondary" onClick={() => onTrack(plan.skill)}>
            Track progress for {plan.skill}
          </Button>
        )}
      </CardBody>
    </Card>
  );
}

function PlanGenerator({ initialSkill, onTrack }: { initialSkill: string; onTrack: (skill: string) => void }) {
  const qc = useQueryClient();
  const [skill, setSkill] = useState(initialSkill);
  const [currentLevel, setCurrentLevel] = useState('');
  const [targetLevel, setTargetLevel] = useState('');
  const [weeklyHours, setWeeklyHours] = useState('');
  const [error, setError] = useState<string | null>(null);
  const create = useMutation({
    mutationFn: learningApi.createPath,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.learningPaths });
      toast.success('Learning plan created');
    },
  });
  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!skill.trim()) return setError('Enter a skill.');
    const hours = weeklyHours ? Number(weeklyHours) : undefined;
    if (hours !== undefined && (!Number.isFinite(hours) || hours < 1 || hours > 80)) return setError('Weekly hours must be between 1 and 80.');
    setError(null);
    create.mutate({
      skill: skill.trim(),
      currentLevel: currentLevel || undefined,
      targetLevel: targetLevel || undefined,
      weeklyHours: hours,
    });
  };
  const levels = [
    { value: 'BEGINNER', label: 'Beginner' },
    { value: 'INTERMEDIATE', label: 'Intermediate' },
    { value: 'ADVANCED', label: 'Advanced' },
  ];
  return (
    <div className="space-y-4">
      <Card>
        <CardHeader icon={<Sparkles className="h-4 w-4" />} title="Generate a learning plan" description="The AI builds a step-by-step plan grounded in job demand and your practice results." />
        <CardBody>
          <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" noValidate>
            <Input label="Skill" required value={skill} onChange={(e) => setSkill(e.target.value)} error={error} disabled={create.isPending} />
            <Select label="Current level" value={currentLevel} onChange={(e) => setCurrentLevel(e.target.value)} placeholder="Not specified" options={levels} disabled={create.isPending} />
            <Select
              label="Target level"
              value={targetLevel}
              onChange={(e) => setTargetLevel(e.target.value)}
              placeholder="Not specified"
              options={[...levels, { value: 'JOB_READY', label: 'Job ready' }]}
              disabled={create.isPending}
            />
            <Input label="Hours per week" type="number" min={1} max={80} value={weeklyHours} onChange={(e) => setWeeklyHours(e.target.value)} disabled={create.isPending} />
            <div className="flex justify-end sm:col-span-2 lg:col-span-4">
              <Button type="submit" loading={create.isPending} icon={<Sparkles className="h-4 w-4" />}>
                Generate plan
              </Button>
            </div>
          </form>
          {create.isPending && <AiProgress className="mt-4" label={`Building a plan for ${skill}…`} />}
          {create.isError && <ErrorState className="mt-4" error={create.error} />}
        </CardBody>
      </Card>
      {create.data && <PlanView plan={create.data} onTrack={onTrack} />}
      <SavedPlans onTrack={onTrack} excludeId={create.data?.id} />
    </div>
  );
}

function SavedPlans({ onTrack, excludeId }: { onTrack: (skill: string) => void; excludeId?: string }) {
  const q = useQuery({ queryKey: qk.learningPaths, queryFn: learningApi.paths });
  const [open, setOpen] = useState<string | null>(null);
  const plans = (q.data ?? []).filter((p) => p.id !== excludeId).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return (
    <Card>
      <CardHeader title="Saved plans" />
      <CardBody>
        {q.isLoading ? (
          <SkeletonRows rows={2} />
        ) : q.isError ? (
          <ErrorState error={q.error} onRetry={() => q.refetch()} />
        ) : plans.length === 0 ? (
          <EmptyState compact title="No saved plans" description="Plans you generate are saved here." />
        ) : (
          <ul className="space-y-3">
            {plans.map((p) => (
              <li key={p.id}>
                {open === p.id ? (
                  <div className="space-y-2">
                    <Button size="sm" variant="ghost" onClick={() => setOpen(null)}>
                      Hide plan
                    </Button>
                    <PlanView plan={p} onTrack={onTrack} />
                  </div>
                ) : (
                  <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-3 py-2.5">
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-fg">{p.skill}</p>
                      <p className="text-xs text-fg-muted">
                        {(p.learningSequence ?? []).length} steps
                        {isNum(p.estimatedHours) ? ` · ${p.estimatedHours} h` : ''} · {formatDate(p.createdAt)}
                      </p>
                    </div>
                    <Button size="sm" variant="secondary" onClick={() => setOpen(p.id)}>
                      View
                    </Button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </CardBody>
    </Card>
  );
}

/* --------------------------------------------------------------- progress */

function ProgressSection({ initialSkill }: { initialSkill: string }) {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: qk.learningProgress, queryFn: learningApi.progress });
  const [skill, setSkill] = useState(initialSkill);
  const [pct, setPct] = useState('0');
  const [status, setStatus] = useState('IN_PROGRESS');
  const [error, setError] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: learningApi.saveProgress,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.learningProgress });
      qc.invalidateQueries({ queryKey: qk.careerDashboard });
      qc.invalidateQueries({ queryKey: qk.health });
      toast.success('Progress saved');
    },
    onError: toast.apiError,
  });
  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const n = Number(pct);
    if (!skill.trim()) return setError('Enter a skill.');
    if (!Number.isFinite(n) || n < 0 || n > 100) return setError('Progress must be between 0 and 100.');
    setError(null);
    save.mutate({ skill: skill.trim(), progressPercentage: n, status });
  };
  const items = [...(q.data ?? [])].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
      <Card>
        <CardHeader icon={<TrendingUp className="h-4 w-4" />} title="Record progress" />
        <CardBody>
          <form onSubmit={submit} className="space-y-4" noValidate>
            <Input label="Skill" required value={skill} onChange={(e) => setSkill(e.target.value)} />
            <Input label="Progress (%)" type="number" min={0} max={100} value={pct} onChange={(e) => setPct(e.target.value)} error={error} />
            <Select label="Status" value={status} onChange={(e) => setStatus(e.target.value)} options={STATUS_OPTIONS} />
            <div className="flex justify-end">
              <Button type="submit" loading={save.isPending}>
                Save progress
              </Button>
            </div>
          </form>
        </CardBody>
      </Card>
      <Card>
        <CardHeader title="Your progress" />
        <CardBody>
          {q.isLoading ? (
            <SkeletonRows rows={3} />
          ) : q.isError ? (
            <ErrorState error={q.error} onRetry={() => q.refetch()} />
          ) : items.length === 0 ? (
            <EmptyState compact icon={<ListChecks className="h-5 w-5" />} title="No progress recorded" description="Record progress on a skill to track it here." />
          ) : (
            <ul className="space-y-4">
              {items.map((p) => (
                <li key={p.id}>
                  <div className="mb-1 flex flex-wrap items-center justify-between gap-2 text-sm">
                    <span className="font-medium text-fg">{p.skill}</span>
                    <span className="flex items-center gap-2">
                      <Badge tone={p.status === 'COMPLETED' ? 'success' : p.status === 'IN_PROGRESS' ? 'info' : 'neutral'}>{humanize(p.status)}</Badge>
                      <span className="tabular-nums text-fg">{Math.round(p.progressPercentage)}%</span>
                    </span>
                  </div>
                  <ProgressBar value={p.progressPercentage} label={`${p.skill} progress`} tone={p.status === 'COMPLETED' ? 'success' : 'primary'} />
                  <p className="mt-1 text-xs text-fg-subtle">Updated {formatRelative(p.updatedAt)}</p>
                </li>
              ))}
            </ul>
          )}
        </CardBody>
      </Card>
    </div>
  );
}

/* ------------------------------------------------------------------ goals */

function GoalProgressModal({ item, onClose }: { item: GoalWithProgress; onClose: () => void }) {
  const qc = useQueryClient();
  const [plan, setPlan] = useState(String(item.progress?.learningPlanProgress ?? 0));
  const [overall, setOverall] = useState(String(item.progress?.overallProgress ?? 0));
  const [done, setDone] = useState(item.progress?.completed ?? false);
  const [error, setError] = useState<string | null>(null);
  const update = useMutation({
    mutationFn: () =>
      learningApi.updateGoal(item.goal.id, { learningPlanProgress: Number(plan), overallProgress: Number(overall), isCompleted: done }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.learningGoals });
      toast.success('Goal updated');
      onClose();
    },
  });
  const save = () => {
    const a = Number(plan);
    const b = Number(overall);
    if (![a, b].every((n) => Number.isFinite(n) && n >= 0 && n <= 100)) return setError('Values must be between 0 and 100.');
    setError(null);
    update.mutate();
  };
  return (
    <Modal
      open
      onClose={onClose}
      title={`Update goal: ${item.goal.targetRole}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={save} loading={update.isPending}>
            Save
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Input label="Learning plan progress (%)" type="number" min={0} max={100} value={plan} onChange={(e) => setPlan(e.target.value)} />
        <Input label="Overall progress (%)" type="number" min={0} max={100} value={overall} onChange={(e) => setOverall(e.target.value)} error={error} />
        <Checkbox label="Goal achieved" checked={done} onChange={setDone} />
        {update.isError && <ErrorState error={update.error} compact />}
      </div>
    </Modal>
  );
}

function Goals() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: qk.learningGoals, queryFn: learningApi.goals });
  const [editing, setEditing] = useState<GoalWithProgress | null>(null);
  const [form, setForm] = useState({ targetRole: '', targetIndustry: '', targetSalary: '', targetLocation: '', timelineMonths: '' });
  const [error, setError] = useState<string | null>(null);
  const create = useMutation({
    mutationFn: learningApi.createGoal,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.learningGoals });
      setForm({ targetRole: '', targetIndustry: '', targetSalary: '', targetLocation: '', timelineMonths: '' });
      toast.success('Goal created');
    },
    onError: toast.apiError,
  });
  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.targetRole.trim()) return setError('Enter a target role.');
    const salary = form.targetSalary ? Number(form.targetSalary) : null;
    const months = form.timelineMonths ? Number(form.timelineMonths) : null;
    if (salary !== null && (!Number.isFinite(salary) || salary < 0)) return setError('Salary must be a positive number.');
    if (months !== null && (!Number.isInteger(months) || months < 1 || months > 120)) return setError('Timeline must be 1–120 months.');
    setError(null);
    create.mutate({
      targetRole: form.targetRole.trim(),
      targetIndustry: form.targetIndustry.trim() || null,
      targetSalary: salary,
      targetLocation: form.targetLocation.trim() || null,
      timelineMonths: months,
    });
  };
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });
  const goals = useMemo(() => [...(q.data ?? [])].sort((a, b) => b.goal.createdAt.localeCompare(a.goal.createdAt)), [q.data]);

  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
      <Card>
        <CardHeader icon={<Plus className="h-4 w-4" />} title="New career goal" />
        <CardBody>
          <form onSubmit={submit} className="space-y-4" noValidate>
            <Input label="Target role" required value={form.targetRole} onChange={set('targetRole')} error={error} />
            <Input label="Industry" value={form.targetIndustry} onChange={set('targetIndustry')} />
            <div className="grid gap-4 sm:grid-cols-2">
              <Input label="Target salary" type="number" min={0} value={form.targetSalary} onChange={set('targetSalary')} />
              <Input label="Timeline (months)" type="number" min={1} max={120} value={form.timelineMonths} onChange={set('timelineMonths')} />
            </div>
            <Input label="Location" value={form.targetLocation} onChange={set('targetLocation')} />
            <div className="flex justify-end">
              <Button type="submit" loading={create.isPending}>
                Create goal
              </Button>
            </div>
          </form>
        </CardBody>
      </Card>
      <Card>
        <CardHeader icon={<Flag className="h-4 w-4" />} title="Your goals" />
        <CardBody>
          {q.isLoading ? (
            <SkeletonRows rows={2} />
          ) : q.isError ? (
            <ErrorState error={q.error} onRetry={() => q.refetch()} />
          ) : goals.length === 0 ? (
            <EmptyState compact title="No goals yet" description="Set a target role to track your progress towards it." />
          ) : (
            <ul className="space-y-3">
              {goals.map((g) => (
                <li key={g.goal.id} className="rounded-lg border border-border p-3">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-fg">{g.goal.targetRole}</p>
                      <p className="text-xs text-fg-muted">
                        {[
                          g.goal.targetIndustry,
                          g.goal.targetLocation,
                          isNum(g.goal.targetSalary) ? `Salary ${g.goal.targetSalary.toLocaleString()}` : null,
                          isNum(g.goal.timelineMonths) ? `${g.goal.timelineMonths} months` : null,
                        ]
                          .filter(Boolean)
                          .join(' · ') || 'No further details'}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      {g.progress?.completed && <Badge tone="success">Achieved</Badge>}
                      <Button size="sm" variant="secondary" icon={<Pencil className="h-3.5 w-3.5" />} onClick={() => setEditing(g)}>
                        Update
                      </Button>
                    </div>
                  </div>
                  {g.progress ? (
                    <div className="mt-3 grid gap-3 sm:grid-cols-2">
                      <div>
                        <div className="mb-1 flex justify-between text-xs text-fg-muted">
                          <span>Learning plan</span>
                          <span className="tabular-nums">{Math.round(g.progress.learningPlanProgress)}%</span>
                        </div>
                        <ProgressBar value={g.progress.learningPlanProgress} label="Learning plan progress" />
                      </div>
                      <div>
                        <div className="mb-1 flex justify-between text-xs text-fg-muted">
                          <span>Overall</span>
                          <span className="tabular-nums">{Math.round(g.progress.overallProgress)}%</span>
                        </div>
                        <ProgressBar value={g.progress.overallProgress} label="Overall progress" tone="success" />
                      </div>
                    </div>
                  ) : (
                    <p className="mt-2 text-xs italic text-fg-subtle">No progress recorded</p>
                  )}
                </li>
              ))}
            </ul>
          )}
        </CardBody>
      </Card>
      {editing && <GoalProgressModal item={editing} onClose={() => setEditing(null)} />}
    </div>
  );
}

/* ------------------------------------------------------------------- page */

export function LearningPage() {
  const [tab, setTab] = useState<TabId>('recommendations');
  const [planSkill, setPlanSkill] = useState('');
  const [trackSkill, setTrackSkill] = useState('');
  const [planKey, setPlanKey] = useState(0);
  const [trackKey, setTrackKey] = useState(0);

  const goPlan = (skill: string) => {
    setPlanSkill(skill);
    setPlanKey((k) => k + 1);
    setTab('plans');
  };
  const goTrack = (skill: string) => {
    setTrackSkill(skill);
    setTrackKey((k) => k + 1);
    setTab('progress');
  };

  return (
    <div>
      <PageHeader
        title="Learning"
        description="Close the skill gaps that matter, based on real job demand and your interview practice."
      />
      <Tabs<TabId>
        ariaLabel="Learning sections"
        className="mb-5"
        value={tab}
        onChange={setTab}
        items={[
          { id: 'recommendations', label: 'Recommendations' },
          { id: 'plans', label: 'Plans' },
          { id: 'progress', label: 'Progress' },
          { id: 'goals', label: 'Goals' },
        ]}
      />
      {tab === 'recommendations' && <Recommendations onPlan={goPlan} onTrack={goTrack} />}
      {tab === 'plans' && <PlanGenerator key={planKey} initialSkill={planSkill} onTrack={goTrack} />}
      {tab === 'progress' && <ProgressSection key={trackKey} initialSkill={trackSkill} />}
      {tab === 'goals' && <Goals />}
      {tab === 'recommendations' && (
        <p className="mt-6 flex items-center gap-2 text-xs text-fg-subtle">
          <GraduationCap className="h-3.5 w-3.5" /> Recommendations come from analysed jobs and your practice scores — nothing is guessed.
        </p>
      )}
    </div>
  );
}
