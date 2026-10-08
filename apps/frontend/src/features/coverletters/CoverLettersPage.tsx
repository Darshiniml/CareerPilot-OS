import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Copy, FileSignature, Save, Sparkles, Trash2 } from 'lucide-react';
import { coverLettersApi } from '../../api/endpoints';
import { qk, useApplications, useCoverLetters, useJobIndex } from '../../api/queries';
import type { CoverLetter, CoverLetterRequest } from '../../api/types';
import {
  AiLabel,
  AiProgress,
  Badge,
  Button,
  Card,
  CardBody,
  CardHeader,
  EmptyState,
  ErrorState,
  Input,
  Modal,
  Notice,
  PageHeader,
  Select,
  SkeletonRows,
  Spinner,
  Tabs,
  Textarea,
} from '../../components/ui';
import { JobPicker, VerificationNotice } from '../../components/shared';
import { jobLabel, stateLabel } from '../../lib/domain';
import { cn, formatDateTime, formatRelative, humanize } from '../../lib/format';
import { toast } from '../../store/toast';

type TargetMode = 'job' | 'application';
const TONES = ['PROFESSIONAL', 'ENTHUSIASTIC', 'CONCISE', 'FORMAL', 'FRIENDLY'];

export function CoverLettersPage() {
  const [params, setParams] = useSearchParams();
  const letters = useCoverLetters();
  const jobs = useJobIndex();
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const selected = useMemo(() => (letters.data ?? []).find((l) => l.id === selectedId) ?? null, [letters.data, selectedId]);

  // Keep a valid selection when the list changes.
  useEffect(() => {
    if (selectedId && letters.data && !letters.data.some((l) => l.id === selectedId)) setSelectedId(null);
  }, [letters.data, selectedId]);

  return (
    <div>
      <PageHeader
        title="Cover letters"
        description="Generate a cover letter grounded in your resume and the job posting, then edit it before you use it."
      />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,420px)_minmax(0,1fr)]">
        <div className="min-w-0 space-y-6">
          <GenerateForm
            initialJobId={params.get('jobId') ?? ''}
            initialApplicationId={params.get('applicationId') ?? ''}
            onClearParams={() => setParams({}, { replace: true })}
            onGenerated={(l) => setSelectedId(l.id)}
          />
          <Card>
            <CardHeader title="Your cover letters" description={letters.data ? `${letters.data.length} saved` : undefined} />
            <CardBody>
              {letters.isLoading && <SkeletonRows rows={3} />}
              {letters.isError && <ErrorState error={letters.error} onRetry={() => letters.refetch()} />}
              {letters.data && letters.data.length === 0 && (
                <EmptyState
                  compact
                  icon={<FileSignature className="h-5 w-5" />}
                  title="No cover letters yet"
                  description="Generate your first one above."
                />
              )}
              {letters.data && letters.data.length > 0 && (
                <ul className="space-y-2">
                  {[...letters.data]
                    .sort((a, b) => (b.updatedAt ?? '').localeCompare(a.updatedAt ?? ''))
                    .map((l) => (
                      <li key={l.id}>
                        <button
                          type="button"
                          onClick={() => setSelectedId(l.id)}
                          aria-current={l.id === selectedId ? 'true' : undefined}
                          className={cn(
                            'w-full rounded-lg border p-3 text-left transition-colors',
                            l.id === selectedId ? 'border-primary bg-primary-soft' : 'border-border hover:bg-surface-2',
                          )}
                        >
                          <p className="truncate text-sm font-medium text-fg">{jobLabel(jobs.index.get(l.jobId), l.jobId)}</p>
                          <div className="mt-1 flex flex-wrap items-center gap-1.5">
                            {l.tone && <Badge>{humanize(l.tone)}</Badge>}
                            {l.editedByUser ? <Badge tone="info">Edited by you</Badge> : <Badge tone="primary">AI-generated</Badge>}
                            <span className="text-xs text-fg-subtle">Updated {formatRelative(l.updatedAt)}</span>
                          </div>
                        </button>
                      </li>
                    ))}
                </ul>
              )}
            </CardBody>
          </Card>
        </div>
        <div className="min-w-0">
          {selected ? (
            <LetterEditor
              key={selected.id}
              letter={selected}
              title={jobLabel(jobs.index.get(selected.jobId), selected.jobId)}
              onDeleted={() => setSelectedId(null)}
            />
          ) : (
            <EmptyState
              icon={<FileSignature className="h-5 w-5" />}
              title="Select a cover letter"
              description="Pick a letter from the list to read, edit, copy or delete it."
            />
          )}
        </div>
      </div>
    </div>
  );
}

function GenerateForm({
  initialJobId,
  initialApplicationId,
  onClearParams,
  onGenerated,
}: {
  initialJobId: string;
  initialApplicationId: string;
  onClearParams: () => void;
  onGenerated: (l: CoverLetter) => void;
}) {
  const qc = useQueryClient();
  const apps = useApplications();
  const jobs = useJobIndex();
  const [mode, setMode] = useState<TargetMode>(initialApplicationId ? 'application' : 'job');
  const [jobId, setJobId] = useState(initialJobId);
  const [applicationId, setApplicationId] = useState(initialApplicationId);
  const [tone, setTone] = useState('PROFESSIONAL');
  const [notes, setNotes] = useState('');
  const [hiringManager, setHiringManager] = useState('');

  useEffect(() => {
    if (initialJobId) {
      setMode('job');
      setJobId(initialJobId);
    }
    if (initialApplicationId) {
      setMode('application');
      setApplicationId(initialApplicationId);
    }
  }, [initialJobId, initialApplicationId]);

  const appOptions = useMemo(
    () =>
      (apps.data ?? []).map((a) => ({
        value: a.applicationId,
        label: `${jobLabel(jobs.index.get(a.jobId), a.jobId)} — ${stateLabel(a.workflowState)}`,
      })),
    [apps.data, jobs.index],
  );

  const generate = useMutation({
    mutationFn: (b: CoverLetterRequest) => coverLettersApi.generate(b),
    onSuccess: (l) => {
      qc.invalidateQueries({ queryKey: qk.coverLetters });
      toast.success('Cover letter generated', 'Review the verification result before using it.');
      onGenerated(l);
      onClearParams();
    },
  });

  const target = mode === 'job' ? jobId : applicationId;
  const submit = () => {
    if (!target || generate.isPending) return;
    generate.mutate({
      jobId: mode === 'job' ? jobId : null,
      applicationId: mode === 'application' ? applicationId : null,
      tone,
      notes: notes.trim() || null,
      hiringManagerName: hiringManager.trim() || null,
    });
  };

  return (
    <Card>
      <CardHeader title="Generate a cover letter" icon={<Sparkles className="h-4 w-4" />} />
      <CardBody>
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
        >
          <Tabs<TargetMode>
            ariaLabel="Cover letter target"
            value={mode}
            onChange={setMode}
            items={[
              { id: 'job', label: 'For a job' },
              { id: 'application', label: 'For an application' },
            ]}
          />
          {mode === 'job' ? (
            <JobPicker value={jobId} onChange={setJobId} required />
          ) : apps.isLoading ? (
            <Spinner label="Loading applications…" />
          ) : apps.isError ? (
            <ErrorState error={apps.error} onRetry={() => apps.refetch()} compact />
          ) : appOptions.length === 0 ? (
            <Notice tone="neutral" title="No applications yet">
              Track an application from the Jobs or Opportunities page, or generate the letter for a job instead.
            </Notice>
          ) : (
            <Select
              label="Application"
              required
              value={applicationId}
              onChange={(e) => setApplicationId(e.target.value)}
              placeholder="Select an application…"
              options={appOptions}
            />
          )}
          <Select
            label="Tone"
            value={tone}
            onChange={(e) => setTone(e.target.value)}
            options={TONES.map((t) => ({ value: t, label: humanize(t) }))}
          />
          <Input
            label="Hiring manager name (optional)"
            value={hiringManager}
            onChange={(e) => setHiringManager(e.target.value)}
            placeholder="Leave empty if you don't know it"
            hint="Only used if you provide it; the AI is not allowed to guess a name."
          />
          <Textarea
            label="Notes for the AI (optional)"
            rows={3}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="e.g. Emphasise my backend work; I'm relocating to Berlin."
          />
          <Button type="submit" className="w-full" icon={<Sparkles className="h-4 w-4" />} loading={generate.isPending} disabled={!target || generate.isPending}>
            Generate cover letter
          </Button>
          {generate.isPending && <AiProgress label="Writing your cover letter" />}
          {generate.isError && <ErrorState error={generate.error} />}
        </form>
      </CardBody>
    </Card>
  );
}

function LetterEditor({ letter, title, onDeleted }: { letter: CoverLetter; title: string; onDeleted: () => void }) {
  const qc = useQueryClient();
  const [content, setContent] = useState(letter.content ?? '');
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => {
    setContent(letter.content ?? '');
  }, [letter.updatedAt, letter.content]);

  const dirty = content !== (letter.content ?? '');

  const save = useMutation({
    mutationFn: () => coverLettersApi.update(letter.id, content),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.coverLetters });
      toast.success('Cover letter saved');
    },
    onError: toast.apiError,
  });
  const remove = useMutation({
    mutationFn: () => coverLettersApi.remove(letter.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: qk.coverLetters });
      setConfirmDelete(false);
      toast.success('Cover letter deleted');
      onDeleted();
    },
    onError: (e) => {
      setConfirmDelete(false);
      toast.apiError(e);
    },
  });

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(content);
      toast.success('Copied to clipboard');
    } catch {
      toast.error('Could not copy', 'Your browser blocked clipboard access. Select the text and copy it manually.');
    }
  };

  return (
    <Card>
      <CardHeader
        title={title}
        description={`Created ${formatDateTime(letter.createdAt)} · Updated ${formatDateTime(letter.updatedAt)}`}
        actions={
          <>
            <AiLabel />
            {letter.tone && <Badge>{humanize(letter.tone)}</Badge>}
          </>
        }
      />
      <CardBody className="space-y-4">
        <VerificationNotice verification={letter.verificationJson} />
        {letter.editedByUser && (
          <p className="text-xs text-fg-subtle">
            The verification above was computed on the generated text. Your edits are not re-verified.
          </p>
        )}
        <Textarea
          label="Letter"
          rows={18}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          className="font-[inherit]"
        />
        <div className="flex flex-wrap gap-2">
          <Button icon={<Save className="h-4 w-4" />} onClick={() => save.mutate()} loading={save.isPending} disabled={!dirty || save.isPending || !content.trim()}>
            Save changes
          </Button>
          {dirty && (
            <Button variant="ghost" onClick={() => setContent(letter.content ?? '')} disabled={save.isPending}>
              Revert
            </Button>
          )}
          <Button variant="secondary" icon={<Copy className="h-4 w-4" />} onClick={copy} disabled={!content}>
            Copy
          </Button>
          <Button variant="danger" className="sm:ml-auto" icon={<Trash2 className="h-4 w-4" />} onClick={() => setConfirmDelete(true)}>
            Delete
          </Button>
        </div>
      </CardBody>
      <Modal
        open={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        dismissible={!remove.isPending}
        size="sm"
        title="Delete this cover letter?"
        description="This cannot be undone."
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmDelete(false)} disabled={remove.isPending}>
              Cancel
            </Button>
            <Button variant="danger" onClick={() => remove.mutate()} loading={remove.isPending}>
              Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-fg-muted">{title}</p>
      </Modal>
    </Card>
  );
}
