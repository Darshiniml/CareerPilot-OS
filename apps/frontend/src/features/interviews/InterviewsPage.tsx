import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, ArrowRight, CheckCircle2, Clock, History, Mic, Play, ShieldAlert, Target } from 'lucide-react';
import { interviewApi } from '../../api/endpoints';
import { qk, useApplications, useInterviewSessions, useJobIndex } from '../../api/queries';
import type {
  Difficulty,
  InterviewEvaluation,
  InterviewQuestion,
  InterviewSession,
  InterviewSummary,
  QuestionType,
  StartInterviewRequest,
} from '../../api/types';
import {
  AiLabel,
  AiProgress,
  Badge,
  BarList,
  BulletList,
  Button,
  Card,
  CardBody,
  CardHeader,
  Checkbox,
  EmptyState,
  ErrorState,
  Input,
  Notice,
  PageHeader,
  ProgressBar,
  ScoreRing,
  Select,
  SkeletonRows,
  Stat,
  Tabs,
  Textarea,
} from '../../components/ui';
import { JobPicker } from '../../components/shared';
import { toast } from '../../store/toast';
import { jobLabel, stateLabel } from '../../lib/domain';
import { cn, formatDateTime, formatRatio, humanize, isNum, parseJson } from '../../lib/format';

const QUESTION_TYPES: Array<{ id: QuestionType; label: string; description: string }> = [
  { id: 'TECHNICAL', label: 'Technical', description: 'Concepts and technologies from the role' },
  { id: 'BEHAVIORAL', label: 'Behavioral', description: 'Past situations, teamwork, ownership' },
  { id: 'SYSTEM_DESIGN', label: 'System design', description: 'Architecture and trade-offs' },
  { id: 'CODING', label: 'Coding', description: 'Problem solving, explained in words' },
  { id: 'COMPANY', label: 'Company', description: 'Only used when company sources exist' },
];

type Source = 'job' | 'application' | 'role';
type TabId = 'practice' | 'history' | 'readiness';

const answered = (q: InterviewQuestion) => Boolean(q.candidateAnswer);
const sortQuestions = (qs: InterviewQuestion[] | null) =>
  [...(qs ?? [])].sort((a, b) => (a.questionOrder ?? 0) - (b.questionOrder ?? 0));

/* ------------------------------------------------------------------ start */

function StartForm({ onStarted }: { onStarted: (s: InterviewSession) => void }) {
  const [params] = useSearchParams();
  const qc = useQueryClient();
  const initialSource: Source = params.get('applicationId') ? 'application' : params.get('jobId') ? 'job' : 'role';
  const [source, setSource] = useState<Source>(initialSource);
  const [jobId, setJobId] = useState(params.get('jobId') ?? '');
  const [applicationId, setApplicationId] = useState(params.get('applicationId') ?? '');
  const [targetRole, setTargetRole] = useState('');
  const [targetCompany, setTargetCompany] = useState('');
  const [jobDescription, setJobDescription] = useState('');
  const [types, setTypes] = useState<QuestionType[]>(['TECHNICAL', 'BEHAVIORAL']);
  const [difficulty, setDifficulty] = useState<Difficulty>('MEDIUM');
  const [count, setCount] = useState(5);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const apps = useApplications();
  const { index } = useJobIndex();
  const appOptions = useMemo(
    () =>
      (apps.data ?? []).map((a) => ({
        value: a.applicationId,
        label: `${jobLabel(index.get(a.jobId), a.jobId)} — ${stateLabel(a.workflowState)}`,
      })),
    [apps.data, index],
  );

  const start = useMutation({
    mutationFn: (b: StartInterviewRequest) => interviewApi.start(b),
    onSuccess: (s) => {
      qc.setQueryData(qk.interviewSession(s.sessionId), s);
      qc.invalidateQueries({ queryKey: qk.interviewSessions });
      toast.success('Practice session ready', `${s.questions?.length ?? 0} AI-generated questions`);
      onStarted(s);
    },
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (source === 'job' && !jobId) errs.job = 'Choose a job.';
    if (source === 'application' && !applicationId) errs.application = 'Choose an application.';
    if (source === 'role' && !targetRole.trim()) errs.role = 'Enter the role you are preparing for.';
    if (types.length === 0) errs.types = 'Select at least one question type.';
    if (!Number.isInteger(count) || count < 1 || count > 10) errs.count = 'Choose between 1 and 10 questions.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const body: StartInterviewRequest = { questionTypes: types, difficulty, questionCount: count };
    if (source === 'job') body.jobId = jobId;
    if (source === 'application') body.applicationId = applicationId;
    if (source === 'role') {
      body.targetRole = targetRole.trim();
      if (targetCompany.trim()) body.targetCompany = targetCompany.trim();
      if (jobDescription.trim()) body.jobDescription = jobDescription.trim();
    }
    start.mutate(body);
  };

  const busy = start.isPending;
  return (
    <Card>
      <CardHeader
        icon={<Play className="h-4 w-4" />}
        title="Start a practice session"
        description="Questions are generated by the AI model from the job, your application or the role you describe."
      />
      <CardBody>
        <form onSubmit={submit} className="space-y-5" noValidate>
          <fieldset disabled={busy} className="space-y-5">
            <div>
              <p className="mb-2 text-xs font-medium text-fg-muted">Practise for</p>
              <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Practise for">
                {(
                  [
                    ['job', 'A discovered job'],
                    ['application', 'One of my applications'],
                    ['role', 'A role I describe'],
                  ] as Array<[Source, string]>
                ).map(([id, label]) => (
                  <button
                    key={id}
                    type="button"
                    role="radio"
                    aria-checked={source === id}
                    onClick={() => setSource(id)}
                    className={cn(
                      'rounded-lg border px-3 py-2 text-sm font-medium',
                      source === id ? 'border-primary bg-primary-soft text-primary-soft-fg' : 'border-border text-fg-muted hover:bg-surface-2',
                    )}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>

            {source === 'job' && (
              <div>
                <JobPicker value={jobId} onChange={setJobId} required />
                {errors.job && <p className="mt-1 text-xs text-danger">{errors.job}</p>}
              </div>
            )}
            {source === 'application' &&
              (apps.isLoading ? (
                <SkeletonRows rows={1} />
              ) : apps.isError ? (
                <ErrorState error={apps.error} onRetry={() => apps.refetch()} compact />
              ) : appOptions.length === 0 ? (
                <Notice tone="neutral" title="No applications yet">
                  Track a job from the Jobs or Opportunities page first, or practise for a job or role instead.
                </Notice>
              ) : (
                <Select
                  label="Application"
                  required
                  value={applicationId}
                  onChange={(e) => setApplicationId(e.target.value)}
                  placeholder="Select an application…"
                  options={appOptions}
                  error={errors.application}
                />
              ))}
            {source === 'role' && (
              <div className="grid gap-4 sm:grid-cols-2">
                <Input
                  label="Target role"
                  required
                  value={targetRole}
                  onChange={(e) => setTargetRole(e.target.value)}
                  placeholder="e.g. Backend engineer"
                  error={errors.role}
                />
                <Input label="Target company (optional)" value={targetCompany} onChange={(e) => setTargetCompany(e.target.value)} />
                <Textarea
                  containerClassName="sm:col-span-2"
                  label="Job description (optional)"
                  hint="Paste the posting to get questions grounded in its requirements."
                  rows={5}
                  value={jobDescription}
                  onChange={(e) => setJobDescription(e.target.value)}
                />
              </div>
            )}

            <div>
              <p className="mb-2 text-xs font-medium text-fg-muted">Question types</p>
              <div className="grid gap-3 sm:grid-cols-2">
                {QUESTION_TYPES.map((t) => (
                  <Checkbox
                    key={t.id}
                    label={t.label}
                    description={t.description}
                    checked={types.includes(t.id)}
                    onChange={(v) => setTypes((cur) => (v ? [...cur, t.id] : cur.filter((x) => x !== t.id)))}
                  />
                ))}
              </div>
              {errors.types && <p className="mt-1 text-xs text-danger" role="alert">{errors.types}</p>}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <Select
                label="Difficulty"
                value={difficulty}
                onChange={(e) => setDifficulty(e.target.value as Difficulty)}
                options={[
                  { value: 'EASY', label: 'Easy' },
                  { value: 'MEDIUM', label: 'Medium' },
                  { value: 'HARD', label: 'Hard' },
                ]}
              />
              <Input
                label="Number of questions"
                type="number"
                min={1}
                max={10}
                value={count}
                onChange={(e) => setCount(Number(e.target.value))}
                error={errors.count}
              />
            </div>
          </fieldset>

          {busy && <AiProgress label="Generating practice questions…" />}
          {start.isError && <ErrorState error={start.error} />}
          <div className="flex justify-end">
            <Button type="submit" loading={busy} icon={<Play className="h-4 w-4" />}>
              Generate questions
            </Button>
          </div>
        </form>
      </CardBody>
    </Card>
  );
}

/* ---------------------------------------------------------------- scoring */

function pct(v: number | null | undefined) {
  return isNum(v) ? Math.round(v * 100) : null;
}

function RubricScores({ q }: { q: InterviewQuestion }) {
  const rows: Array<[string, number | null]> = [
    ['Technical accuracy', q.technicalAccuracyScore],
    ['Completeness', q.completenessScore],
    ['Clarity', q.clarityScore],
    ['Relevance', q.relevanceScore],
  ];
  return (
    <div className="flex flex-col gap-5 sm:flex-row sm:items-center">
      <ScoreRing score={pct(q.overallScore)} label="Overall" size={84} />
      <ul className="flex-1 space-y-2.5">
        {rows.map(([label, v]) => (
          <li key={label}>
            <div className="mb-1 flex justify-between text-sm">
              <span className="text-fg-muted">{label}</span>
              <span className={cn('font-medium tabular-nums', isNum(v) ? 'text-fg' : 'italic text-fg-subtle')}>
                {formatRatio(v, 'Not available')}
              </span>
            </div>
            {isNum(v) && <ProgressBar value={v * 100} label={label} />}
          </li>
        ))}
      </ul>
    </div>
  );
}

function Evaluation({ q }: { q: InterviewQuestion }) {
  const ev = parseJson<InterviewEvaluation>(q.evaluationJson);
  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <h3 className="text-sm font-semibold text-fg">Evaluation</h3>
        <AiLabel>AI evaluation</AiLabel>
      </div>
      {ev?.gradingManipulationDetected && (
        <Notice tone="warning" icon={<ShieldAlert className="h-4 w-4" />} title="Answer flagged">
          The answer appeared to contain instructions aimed at the grader. Scores may not reflect your real answer quality.
        </Notice>
      )}
      <RubricScores q={q} />
      {(q.evaluationFeedback || ev?.feedback) && (
        <p className="rounded-lg bg-surface-2 px-3 py-2 text-sm text-fg">{q.evaluationFeedback || ev?.feedback}</p>
      )}
      {ev ? (
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-success">Strengths</p>
            <BulletList items={ev.strengths} />
          </div>
          <div>
            <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-warning">Improvements</p>
            <BulletList items={ev.improvements} />
          </div>
          <div>
            <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-fg-muted">Missing points</p>
            <BulletList items={ev.missingPoints} />
          </div>
          <div>
            <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-fg-muted">Model answer outline</p>
            <BulletList items={ev.modelAnswerOutline} />
          </div>
        </div>
      ) : (
        <p className="text-sm italic text-fg-subtle">No detailed evaluation was returned for this answer.</p>
      )}
    </div>
  );
}

function SessionSummary({ session }: { session: InterviewSession }) {
  const s = parseJson<InterviewSummary>(session.summaryJson);
  const answeredCount = (session.questions ?? []).filter(answered).length;
  return (
    <Card>
      <CardHeader
        icon={<CheckCircle2 className="h-4 w-4" />}
        title="Session summary"
        description={session.completedAt ? `Completed ${formatDateTime(session.completedAt)}` : undefined}
        actions={<AiLabel>AI feedback</AiLabel>}
      />
      <CardBody className="space-y-4">
        <div className="flex flex-wrap items-center gap-6">
          <ScoreRing
            score={answeredCount > 0 ? pct(session.overallReadiness) : null}
            label="Session score"
            emptyText="—"
          />
          <p className="text-sm text-fg-muted">
            {answeredCount > 0
              ? `Average of ${answeredCount} answered question${answeredCount === 1 ? '' : 's'}.`
              : 'No answers were submitted in this session.'}
          </p>
        </div>
        {s ? (
          <>
            {s.summary && <p className="text-sm text-fg">{s.summary}</p>}
            <div className="grid gap-4 md:grid-cols-3">
              <div>
                <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-success">Strong areas</p>
                <BulletList items={s.strongAreas} />
              </div>
              <div>
                <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-warning">Weak areas</p>
                <BulletList items={s.weakAreas} />
              </div>
              <div>
                <p className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-fg-muted">Next steps</p>
                <BulletList items={s.nextSteps} />
              </div>
            </div>
          </>
        ) : (
          <p className="text-sm italic text-fg-subtle">No AI summary is available for this session.</p>
        )}
      </CardBody>
    </Card>
  );
}

/* ----------------------------------------------------------------- runner */

function useTimer(running: boolean, resetKey: string) {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => setSeconds(0), [resetKey]);
  useEffect(() => {
    if (!running) return;
    const t = window.setInterval(() => setSeconds((s) => s + 1), 1000);
    return () => window.clearInterval(t);
  }, [running, resetKey]);
  return seconds;
}

function SessionRunner({ sessionId, onExit }: { sessionId: string; onExit: () => void }) {
  const qc = useQueryClient();
  const sq = useQuery({
    queryKey: qk.interviewSession(sessionId),
    queryFn: () => interviewApi.session(sessionId),
    staleTime: Infinity,
  });
  const session = sq.data;
  const questions = useMemo(() => sortQuestions(session?.questions ?? null), [session]);
  const firstUnanswered = questions.findIndex((q) => !answered(q));
  const [idx, setIdx] = useState<number | null>(null);
  const current = idx ?? (firstUnanswered >= 0 ? firstUnanswered : Math.max(0, questions.length - 1));
  const q = questions[current];
  const [answer, setAnswer] = useState('');
  const [answerError, setAnswerError] = useState<string | null>(null);

  const answerMut = useMutation({
    mutationFn: (v: { questionId: string; answer: string; seconds: number }) =>
      interviewApi.answer(sessionId, v.questionId, v.answer, v.seconds),
    onSuccess: (updated) => {
      qc.setQueryData<InterviewSession>(qk.interviewSession(sessionId), (old) =>
        old ? { ...old, questions: (old.questions ?? []).map((x) => (x.questionId === updated.questionId ? updated : x)) } : old,
      );
      qc.invalidateQueries({ queryKey: qk.readiness });
      qc.invalidateQueries({ queryKey: qk.interviewSessions });
      qc.invalidateQueries({ queryKey: qk.careerDashboard });
    },
  });
  const completeMut = useMutation({
    mutationFn: () => interviewApi.complete(sessionId),
    onSuccess: (s) => {
      qc.setQueryData(qk.interviewSession(sessionId), s);
      qc.invalidateQueries({ queryKey: qk.interviewSessions });
      qc.invalidateQueries({ queryKey: qk.readiness });
      toast.success('Session completed');
    },
  });

  const isAnswered = q ? answered(q) : false;
  const completed = session?.status === 'COMPLETED';
  const busy = answerMut.isPending || completeMut.isPending;
  const seconds = useTimer(Boolean(q) && !isAnswered && !completed && !busy, q?.questionId ?? '');

  useEffect(() => {
    setAnswer('');
    setAnswerError(null);
    answerMut.reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q?.questionId]);

  if (sq.isLoading) return <SkeletonRows rows={4} />;
  if (sq.isError || !session) {
    return (
      <div className="space-y-3">
        <ErrorState error={sq.error} onRetry={() => sq.refetch()} />
        <Button variant="secondary" onClick={onExit} icon={<ArrowLeft className="h-4 w-4" />}>
          Back
        </Button>
      </div>
    );
  }

  const answeredCount = questions.filter(answered).length;
  const submit = () => {
    if (!q) return;
    if (!answer.trim()) {
      setAnswerError('Write an answer before submitting.');
      return;
    }
    if (answer.length > 12000) {
      setAnswerError('Answers are limited to 12,000 characters.');
      return;
    }
    answerMut.mutate({ questionId: q.questionId, answer: answer.trim(), seconds });
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button variant="ghost" size="sm" onClick={onExit} icon={<ArrowLeft className="h-4 w-4" />} disabled={busy}>
          New session
        </Button>
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone={completed ? 'success' : 'info'}>{completed ? 'Completed' : 'In progress'}</Badge>
          <Badge>{humanize(session.difficulty)}</Badge>
          {session.targetRole && <Badge tone="primary">{session.targetRole}</Badge>}
          {session.targetCompany && <Badge>{session.targetCompany}</Badge>}
        </div>
      </div>

      <div>
        <div className="mb-1.5 flex justify-between text-xs text-fg-muted">
          <span>
            {answeredCount} of {questions.length} answered
          </span>
          <AiLabel>AI-generated practice questions</AiLabel>
        </div>
        <ProgressBar value={answeredCount} max={Math.max(1, questions.length)} label="Answered questions" />
      </div>

      {questions.length === 0 ? (
        <EmptyState title="No questions in this session" description="The AI did not return any questions. Start a new session." />
      ) : (
        q && (
          <Card>
            <CardHeader
              title={`Question ${current + 1} of ${questions.length}`}
              description={[q.questionType && humanize(q.questionType), q.skillArea, q.difficulty && humanize(q.difficulty)]
                .filter(Boolean)
                .join(' · ')}
              actions={
                !isAnswered && !completed ? (
                  <span className="inline-flex items-center gap-1 font-mono text-xs text-fg-muted" aria-label={`${seconds} seconds elapsed`}>
                    <Clock className="h-3.5 w-3.5" />
                    {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, '0')}
                  </span>
                ) : undefined
              }
            />
            <CardBody className="space-y-4">
              <p className="whitespace-pre-wrap text-base font-medium text-fg">{q.questionText}</p>
              {q.rationale && <p className="text-xs text-fg-muted">Why this question: {q.rationale}</p>}

              {isAnswered ? (
                <>
                  <div>
                    <p className="mb-1 text-xs font-medium text-fg-muted">
                      Your answer{isNum(q.timeTakenSeconds) ? ` · ${q.timeTakenSeconds}s` : ''}
                    </p>
                    <p className="whitespace-pre-wrap rounded-lg border border-border bg-surface-2 px-3 py-2 text-sm text-fg">{q.candidateAnswer}</p>
                  </div>
                  <Evaluation q={q} />
                </>
              ) : completed ? (
                <p className="text-sm italic text-fg-subtle">Not answered — the session was completed.</p>
              ) : (
                <>
                  <Textarea
                    label="Your answer"
                    rows={8}
                    value={answer}
                    onChange={(e) => setAnswer(e.target.value)}
                    disabled={busy}
                    error={answerError}
                    hint={`${answer.length.toLocaleString()} / 12,000 characters`}
                  />
                  {answerMut.isPending && <AiProgress label="Evaluating your answer…" />}
                  {answerMut.isError && <ErrorState error={answerMut.error} />}
                  <div className="flex justify-end">
                    <Button onClick={submit} loading={answerMut.isPending} disabled={busy}>
                      Submit answer
                    </Button>
                  </div>
                </>
              )}
            </CardBody>
          </Card>
        )
      )}

      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex gap-2">
          <Button
            variant="secondary"
            size="sm"
            disabled={current === 0 || busy}
            onClick={() => setIdx(current - 1)}
            icon={<ArrowLeft className="h-4 w-4" />}
          >
            Previous
          </Button>
          <Button
            variant="secondary"
            size="sm"
            disabled={current >= questions.length - 1 || busy}
            onClick={() => setIdx(current + 1)}
          >
            Next <ArrowRight className="h-4 w-4" />
          </Button>
        </div>
        {!completed && (
          <Button
            variant={firstUnanswered === -1 ? 'primary' : 'outline'}
            onClick={() => completeMut.mutate()}
            loading={completeMut.isPending}
            disabled={busy || answeredCount === 0}
            title={answeredCount === 0 ? 'Answer at least one question first' : undefined}
            icon={<CheckCircle2 className="h-4 w-4" />}
          >
            Complete session
          </Button>
        )}
      </div>
      {completeMut.isPending && <AiProgress label="Writing your session summary…" />}
      {completeMut.isError && <ErrorState error={completeMut.error} />}
      {completed && <SessionSummary session={session} />}
    </div>
  );
}

/* ---------------------------------------------------------------- history */

function HistoryList({ onOpen }: { onOpen: (s: InterviewSession) => void }) {
  const q = useInterviewSessions();
  if (q.isLoading) return <SkeletonRows rows={4} />;
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} />;
  const sessions = [...(q.data ?? [])].sort((a, b) => (b.startedAt ?? '').localeCompare(a.startedAt ?? ''));
  if (sessions.length === 0)
    return <EmptyState icon={<History className="h-5 w-5" />} title="No practice sessions yet" description="Start a session to see it here." />;
  return (
    <ul className="space-y-3">
      {sessions.map((s) => {
        const qs = s.questions ?? [];
        const n = qs.filter(answered).length;
        return (
          <li key={s.sessionId}>
            <Card className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold text-fg">
                  {s.targetRole ?? 'Practice session'}
                  {s.targetCompany ? ` · ${s.targetCompany}` : ''}
                </p>
                <p className="mt-0.5 text-xs text-fg-muted">
                  {formatDateTime(s.startedAt)} · {humanize(s.difficulty)} · {n} of {qs.length} answered
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <Badge tone={s.status === 'COMPLETED' ? 'success' : 'info'}>{humanize(s.status)}</Badge>
                <span className="text-sm text-fg">
                  {n > 0 ? `Score ${formatRatio(s.overallReadiness)}` : <span className="italic text-fg-subtle">No answers yet</span>}
                </span>
                <Button size="sm" variant="secondary" onClick={() => onOpen(s)}>
                  {s.status === 'COMPLETED' ? 'Review' : 'Continue'}
                </Button>
              </div>
            </Card>
          </li>
        );
      })}
    </ul>
  );
}

/* -------------------------------------------------------------- readiness */

export function ReadinessPanel() {
  const q = useQuery({ queryKey: qk.readiness, queryFn: interviewApi.readiness });
  if (q.isLoading) return <SkeletonRows rows={3} />;
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} />;
  const r = q.data;
  if (!r) return null;
  if (!r.available) {
    return (
      <EmptyState
        icon={<Target className="h-5 w-5" />}
        title="Not enough data"
        description={`${r.message ?? 'Answer more practice questions to measure readiness.'} (${r.answeredQuestions} answered so far)`}
      />
    );
  }
  const byType = Object.entries(r.byQuestionType ?? {}).map(([k, v]) => ({ label: humanize(k), value: Math.round(v * 100) }));
  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <Card className="flex flex-col items-center justify-center gap-2 p-6">
        <ScoreRing score={Math.round(r.overallReadiness * 100)} size={110} label="Overall readiness" />
        <p className="text-xs text-fg-muted">Based on {r.answeredQuestions} answered questions</p>
      </Card>
      <Card className="lg:col-span-2">
        <CardHeader title="By question type" />
        <CardBody>
          <BarList data={byType} max={100} valueFormatter={(v) => `${v}%`} emptyTitle="No scored question types" />
        </CardBody>
      </Card>
      <Card>
        <CardHeader title="Strongest areas" />
        <CardBody>
          <BarList
            data={(r.strongestAreas ?? []).map((a) => ({ label: a.skillArea, value: Math.round(a.score * 100) }))}
            max={100}
            tone="success"
            valueFormatter={(v) => `${v}%`}
            emptyTitle="No areas yet"
          />
        </CardBody>
      </Card>
      <Card className="lg:col-span-2">
        <CardHeader title="Weakest areas" />
        <CardBody>
          <BarList
            data={(r.weakestAreas ?? []).map((a) => ({ label: a.skillArea, value: Math.round(a.score * 100) }))}
            max={100}
            tone="warning"
            valueFormatter={(v) => `${v}%`}
            emptyTitle="No areas yet"
          />
        </CardBody>
      </Card>
    </div>
  );
}

/* ------------------------------------------------------------------- page */

export function InterviewsPage() {
  const qc = useQueryClient();
  const [tab, setTab] = useState<TabId>('practice');
  const [activeId, setActiveId] = useState<string | null>(null);
  const sessions = useInterviewSessions();

  return (
    <div>
      <PageHeader
        title="Interview coach"
        description="Practise with AI-generated questions and get rubric-based feedback on every answer."
      />
      <Tabs<TabId>
        ariaLabel="Interview coach sections"
        className="mb-5"
        value={tab}
        onChange={setTab}
        items={[
          { id: 'practice', label: 'Practice' },
          { id: 'history', label: 'History', count: sessions.data?.length ?? null },
          { id: 'readiness', label: 'Readiness' },
        ]}
      />
      {tab === 'practice' &&
        (activeId ? (
          <SessionRunner sessionId={activeId} onExit={() => setActiveId(null)} />
        ) : (
          <div className="space-y-4">
            <StartForm onStarted={(s) => setActiveId(s.sessionId)} />
            <Notice tone="neutral" icon={<Mic className="h-4 w-4" />}>
              Answers are scored by the AI on technical accuracy, completeness, clarity and relevance. Scores are guidance, not a
              prediction of a real interview outcome.
            </Notice>
          </div>
        ))}
      {tab === 'history' && (
        <HistoryList
          onOpen={(s) => {
            qc.setQueryData(qk.interviewSession(s.sessionId), s);
            setActiveId(s.sessionId);
            setTab('practice');
          }}
        />
      )}
      {tab === 'readiness' && (
        <div className="space-y-4">
          <ReadinessPanel />
          {sessions.data && (
            <div className="grid gap-4 sm:grid-cols-3">
              <Stat label="Sessions" value={sessions.data.length} />
              <Stat label="Completed sessions" value={sessions.data.filter((s) => s.status === 'COMPLETED').length} />
              <Stat
                label="Answered questions"
                value={sessions.data.reduce((n, s) => n + (s.questions ?? []).filter(answered).length, 0)}
              />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
