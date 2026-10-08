import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Activity, Cpu, Pause, Play, PlugZap, Save, ShieldCheck, Square, Undo2 } from 'lucide-react';
import { agentsApi, jobsApi } from '../../api/endpoints';
import { qk } from '../../api/queries';
import type { AgentPolicy, AgentWorkflow, ConnectorHealthStatus } from '../../api/types';
import {
  Badge,
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
  SkeletonRows,
  type Tone,
} from '../../components/ui';
import { toast } from '../../store/toast';
import { formatDateTime, formatRelative, humanize } from '../../lib/format';

/* ------------------------------------------------------------------ policy */

function PolicyEditor() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: qk.agentPolicy, queryFn: agentsApi.policy });
  const [draft, setDraft] = useState<AgentPolicy | null>(null);

  useEffect(() => {
    if (q.data) setDraft(q.data);
  }, [q.data]);

  const save = useMutation({
    mutationFn: (p: AgentPolicy) => agentsApi.savePolicy(p),
    onSuccess: (p) => {
      qc.setQueryData(qk.agentPolicy, p);
      toast.success('Automation policy saved');
    },
  });

  const dirty = q.data && draft ? JSON.stringify(q.data) !== JSON.stringify(draft) : false;
  const errors = draft
    ? {
        max: !Number.isInteger(draft.maxApplicationsPerDay) || draft.maxApplicationsPerDay < 0 ? 'Enter a whole number ≥ 0' : null,
        min:
          !Number.isFinite(draft.minimumMatchScore) || draft.minimumMatchScore < 0 || draft.minimumMatchScore > 100
            ? 'Enter a value between 0 and 100'
            : null,
      }
    : { max: null, min: null };
  const valid = !errors.max && !errors.min;

  const set = <K extends keyof AgentPolicy>(k: K, v: AgentPolicy[K]) => setDraft((d) => (d ? { ...d, [k]: v } : d));

  return (
    <Card>
      <CardHeader
        icon={<ShieldCheck className="h-4 w-4" />}
        title="Automation policy"
        description="Limits that every automated workflow must respect. Changes apply only after you press Save."
        actions={
          <>
            <Button variant="ghost" size="sm" icon={<Undo2 className="h-3.5 w-3.5" />} disabled={!dirty || save.isPending} onClick={() => q.data && setDraft(q.data)}>
              Reset
            </Button>
            <Button size="sm" icon={<Save className="h-3.5 w-3.5" />} disabled={!dirty || !valid} loading={save.isPending} onClick={() => draft && save.mutate(draft)}>
              Save
            </Button>
          </>
        }
      />
      <CardBody>
        {q.isLoading && <SkeletonRows rows={4} />}
        {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
        {draft && (
          <div className="space-y-5">
            {dirty && <Notice tone="info">You have unsaved changes.</Notice>}
            {save.isError && <ErrorState error={save.error} compact />}
            <div className="grid gap-3 sm:grid-cols-2">
              <Checkbox label="Automation enabled" checked={draft.enabled} onChange={(v) => set('enabled', v)} />
              <Checkbox
                label="Require my approval before applying"
                checked={draft.requireApproval}
                onChange={(v) => set('requireApproval', v)}
              />
              <Checkbox
                label="Allow automatic submission"
                description="Only for sources that support it; otherwise you apply manually."
                checked={draft.allowAutomaticSubmission}
                onChange={(v) => set('allowAutomaticSubmission', v)}
              />
              <Checkbox label="Allow company research" checked={draft.allowReferenceResearch} onChange={(v) => set('allowReferenceResearch', v)} />
              <Checkbox label="Allow external connectors" checked={draft.allowExternalConnectors} onChange={(v) => set('allowExternalConnectors', v)} />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                label="Max applications per day"
                type="number"
                min={0}
                step={1}
                value={Number.isFinite(draft.maxApplicationsPerDay) ? draft.maxApplicationsPerDay : ''}
                onChange={(e) => set('maxApplicationsPerDay', e.target.value === '' ? NaN : Number(e.target.value))}
                error={errors.max}
              />
              <Input
                label="Minimum match score (0–100)"
                type="number"
                min={0}
                max={100}
                value={Number.isFinite(draft.minimumMatchScore) ? draft.minimumMatchScore : ''}
                onChange={(e) => set('minimumMatchScore', e.target.value === '' ? NaN : Number(e.target.value))}
                error={errors.min}
              />
              <Input
                label="Allowed employment types"
                hint="Comma-separated, e.g. FULL_TIME, CONTRACT"
                value={draft.allowedEmploymentTypes ?? ''}
                onChange={(e) => set('allowedEmploymentTypes', e.target.value)}
              />
              <Input
                label="Allowed locations"
                hint="Comma-separated"
                value={draft.allowedLocations ?? ''}
                onChange={(e) => set('allowedLocations', e.target.value)}
              />
              <Input
                label="Allowed work modes"
                hint="Comma-separated, e.g. REMOTE, HYBRID, ON_SITE"
                value={draft.allowedRemoteTypes ?? ''}
                onChange={(e) => set('allowedRemoteTypes', e.target.value)}
              />
              <Input
                label="Allowed companies"
                hint="Comma-separated; leave empty for any"
                value={draft.allowedCompanies ?? ''}
                onChange={(e) => set('allowedCompanies', e.target.value || null)}
              />
              <Input
                label="Blocked companies"
                hint="Comma-separated"
                value={draft.blockedCompanies ?? ''}
                onChange={(e) => set('blockedCompanies', e.target.value || null)}
              />
            </div>
            {draft.updatedAt && <p className="text-xs text-fg-subtle">Last saved {formatDateTime(draft.updatedAt)}</p>}
          </div>
        )}
      </CardBody>
    </Card>
  );
}

/* ---------------------------------------------------------------- workflow */

const RUNNING = new Set(['PENDING', 'RUNNING']);

function wfTone(s: string): Tone {
  if (s === 'COMPLETED') return 'success';
  if (s === 'FAILED' || s === 'BLOCKED') return 'danger';
  if (s === 'CANCELLED' || s === 'PAUSED') return 'warning';
  if (RUNNING.has(s)) return 'info';
  return 'neutral';
}

function WorkflowStatusPanel({ workflow }: { workflow: AgentWorkflow }) {
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: qk.agentStatus(workflow.id),
    queryFn: () => agentsApi.status(workflow.id),
    // Poll only while the workflow is running.
    refetchInterval: (query) => (query.state.data && !RUNNING.has(query.state.data.status) ? false : 3000),
  });
  const control = useMutation({
    mutationFn: (op: 'pause' | 'resume' | 'cancel') => agentsApi.control(workflow.id, op),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.agentStatus(workflow.id) });
      qc.invalidateQueries({ queryKey: qk.agentActive });
    },
    onError: toast.apiError,
  });
  const s = q.data;
  return (
    <div className="rounded-lg border border-border p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-sm font-medium text-fg">Workflow started {formatRelative(workflow.createdAt)}</p>
          {workflow.correlationId && <p className="font-mono text-[11px] text-fg-subtle">Ref {workflow.correlationId}</p>}
        </div>
        {s && <Badge tone={wfTone(s.status)}>{humanize(s.status)}</Badge>}
      </div>
      {q.isLoading && <SkeletonRows rows={1} className="mt-3" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} compact className="mt-3" />}
      {s && (
        <>
          <div className="mt-3 flex items-center gap-3">
            <ProgressBar value={s.progress} label="Workflow progress" />
            <span className="shrink-0 text-xs tabular-nums text-fg-muted">
              {s.completedTasks}/{s.totalTasks}
            </span>
          </div>
          {s.currentTask && <p className="mt-2 text-xs text-fg-muted">Current step: {humanize(s.currentTask)}</p>}
          <div className="mt-3 flex flex-wrap gap-2">
            {s.status === 'RUNNING' && (
              <Button size="sm" variant="secondary" icon={<Pause className="h-3.5 w-3.5" />} loading={control.isPending} onClick={() => control.mutate('pause')}>
                Pause
              </Button>
            )}
            {s.status === 'PAUSED' && (
              <Button size="sm" variant="secondary" icon={<Play className="h-3.5 w-3.5" />} loading={control.isPending} onClick={() => control.mutate('resume')}>
                Resume
              </Button>
            )}
            {(RUNNING.has(s.status) || s.status === 'PAUSED') && (
              <Button size="sm" variant="danger" icon={<Square className="h-3.5 w-3.5" />} loading={control.isPending} onClick={() => control.mutate('cancel')}>
                Cancel
              </Button>
            )}
          </div>
          {s.tasks.length > 0 && (
            <ol className="mt-4 space-y-1.5">
              {s.tasks.map((t) => (
                <li key={t.id} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                  <span className="text-fg">{humanize(t.taskType)}</span>
                  <span className="flex items-center gap-2">
                    {t.errorMessage && <span className="max-w-[240px] truncate text-xs text-danger" title={t.errorMessage}>{t.errorMessage}</span>}
                    <Badge tone={wfTone(t.status)}>{humanize(t.status)}</Badge>
                  </span>
                </li>
              ))}
            </ol>
          )}
        </>
      )}
    </div>
  );
}

function WorkflowCard() {
  const qc = useQueryClient();
  const active = useQuery({ queryKey: qk.agentActive, queryFn: agentsApi.active });
  const [started, setStarted] = useState<AgentWorkflow | null>(null);
  const start = useMutation({
    mutationFn: agentsApi.start,
    onSuccess: (wf) => {
      setStarted(wf);
      qc.invalidateQueries({ queryKey: qk.agentActive });
      toast.success('Workflow started');
    },
  });
  const list = [...(active.data ?? [])];
  if (started && !list.some((w) => w.id === started.id)) list.unshift(started);
  return (
    <Card>
      <CardHeader
        icon={<Activity className="h-4 w-4" />}
        title="Automation workflow"
        description="Runs discovery → matching → preparation within your policy. Nothing is submitted without your approval unless the policy allows it."
        actions={
          <Button size="sm" icon={<Play className="h-3.5 w-3.5" />} loading={start.isPending} onClick={() => start.mutate()}>
            Start workflow
          </Button>
        }
      />
      <CardBody className="space-y-3">
        {start.isError && <ErrorState error={start.error} compact />}
        {active.isLoading && <SkeletonRows rows={1} />}
        {active.isError && <ErrorState error={active.error} onRetry={() => active.refetch()} compact />}
        {active.data && list.length === 0 && <EmptyState compact title="No workflow running" description="Start a workflow to see its progress here." />}
        {list.map((w) => (
          <WorkflowStatusPanel key={w.id} workflow={w} />
        ))}
      </CardBody>
    </Card>
  );
}

/* -------------------------------------------------------------- connectors */

function healthTone(s: ConnectorHealthStatus): Tone {
  return s === 'HEALTHY' ? 'success' : s === 'DEGRADED' ? 'warning' : s === 'UNHEALTHY' ? 'danger' : 'neutral';
}

function ConnectorsCard() {
  const q = useQuery({ queryKey: qk.connectorsHealth, queryFn: jobsApi.connectorsHealth });
  return (
    <Card>
      <CardHeader icon={<PlugZap className="h-4 w-4" />} title="Job source connectors" description="Health as reported by the server (read-only)." />
      <CardBody>
        {q.isLoading && <SkeletonRows rows={2} />}
        {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
        {q.data && q.data.length === 0 && <EmptyState compact title="No connectors reported" />}
        <ul className="divide-y divide-border">
          {(q.data ?? []).map((c, i) => (
            <li key={c.connectorId ?? i} className="py-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm font-medium text-fg">{c.connectorId ?? 'Unnamed connector'}</p>
                <Badge tone={healthTone(c.status)}>{humanize(c.status)}</Badge>
              </div>
              <p className="mt-1 text-xs text-fg-muted">
                Last success: {c.lastSuccess ? formatRelative(c.lastSuccess) : 'never'} · Last failure:{' '}
                {c.lastFailure ? formatRelative(c.lastFailure) : 'none'}
                {typeof c.failureCount === 'number' && <> · Failures: {c.failureCount}</>}
              </p>
              {c.message && <p className="mt-0.5 text-xs text-fg-subtle">{c.message}</p>}
            </li>
          ))}
        </ul>
      </CardBody>
    </Card>
  );
}

function RegistryCard() {
  const q = useQuery({ queryKey: qk.agentRegistry, queryFn: agentsApi.registry });
  return (
    <Card>
      <CardHeader icon={<Cpu className="h-4 w-4" />} title="Agents" description="Registered agents and their reported health." />
      <CardBody>
        {q.isLoading && <SkeletonRows rows={2} />}
        {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
        {q.data && q.data.length === 0 && <EmptyState compact title="No agents registered" />}
        <ul className="divide-y divide-border">
          {(q.data ?? []).map((a) => (
            <li key={a.agentId} className="py-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm font-medium text-fg">
                  {a.name} <span className="text-xs text-fg-subtle">v{a.version}</span>
                </p>
                <div className="flex gap-1.5">
                  {!a.enabled && <Badge>Disabled</Badge>}
                  <Badge tone={a.health === 'HEALTHY' ? 'success' : 'danger'}>{humanize(a.health)}</Badge>
                </div>
              </div>
              {a.capabilities?.length > 0 && <p className="mt-1 text-xs text-fg-muted">{a.capabilities.map(humanize).join(', ')}</p>}
            </li>
          ))}
        </ul>
      </CardBody>
    </Card>
  );
}

export function AgentsPage() {
  return (
    <>
      <PageHeader title="Agents & automation" description="Control what automation may do on your behalf and watch it run." />
      <div className="grid gap-6 xl:grid-cols-2">
        <div className="space-y-6">
          <PolicyEditor />
        </div>
        <div className="space-y-6">
          <WorkflowCard />
          <ConnectorsCard />
          <RegistryCard />
        </div>
      </div>
    </>
  );
}
