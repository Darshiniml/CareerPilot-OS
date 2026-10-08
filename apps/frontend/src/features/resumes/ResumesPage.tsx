import { useEffect, useState, type ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Download, FileText, Loader2, RefreshCw, Star, Trash2, XCircle } from 'lucide-react';
import { resumesApi } from '../../api/endpoints';
import { isProcessing, qk, useResumes } from '../../api/queries';
import type { Resume } from '../../api/types';
import {
  Badge,
  Button,
  Card,
  CardHeader,
  EmptyState,
  ErrorState,
  Modal,
  Notice,
  PageHeader,
  SkeletonRows,
  Tabs,
  type Tone,
} from '../../components/ui';
import { toast } from '../../store/toast';
import { formatBytes, formatDate, formatDateTime } from '../../lib/format';
import { UploadCard } from './UploadCard';
import { KnowledgePanel } from './KnowledgePanel';
import { AtsPanel } from './AtsPanel';
import { TailorPanel } from './TailorPanel';

function statusBadge(r: Resume): { tone: Tone; label: string; icon: ReactNode } {
  switch (r.aiProcessingStatus) {
    case 'READY':
      return { tone: 'success', label: 'Ready', icon: <CheckCircle2 className="h-3 w-3" /> };
    case 'FAILED':
      return { tone: 'danger', label: 'Processing failed', icon: <XCircle className="h-3 w-3" /> };
    case 'PROCESSING':
      return { tone: 'info', label: 'Processing…', icon: <Loader2 className="h-3 w-3 animate-spin" /> };
    case 'PENDING':
      return { tone: 'neutral', label: 'Queued', icon: <Loader2 className="h-3 w-3 animate-spin" /> };
    default:
      return { tone: 'neutral', label: 'Status unknown', icon: null };
  }
}

function useResumeActions() {
  const qc = useQueryClient();
  const invalidate = () => qc.invalidateQueries({ queryKey: qk.resumes });
  const setDefault = useMutation({
    mutationFn: resumesApi.setDefault,
    onSuccess: () => {
      toast.success('Default resume updated');
      invalidate();
    },
    onError: toast.apiError,
  });
  const remove = useMutation({
    mutationFn: resumesApi.remove,
    onSuccess: () => {
      toast.success('Resume deleted');
      invalidate();
    },
    onError: toast.apiError,
  });
  const reprocess = useMutation({
    mutationFn: resumesApi.reprocess,
    onSuccess: (_d, id) => {
      toast.info('Re-processing started', 'The status updates automatically while the AI model works.');
      invalidate();
      qc.removeQueries({ queryKey: qk.resumeKnowledge(id) });
      qc.removeQueries({ queryKey: qk.resumeAts(id) });
    },
    onError: toast.apiError,
  });
  const download = useMutation({
    mutationFn: async (r: Resume) => {
      const blob = await resumesApi.download(r.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = r.originalFilename || r.title;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
    },
    onError: toast.apiError,
  });
  return { setDefault, remove, reprocess, download };
}

type DetailTab = 'knowledge' | 'ats' | 'tailor' | 'versions';

function ResumeDetail({
  resume,
  tab,
  onTab,
  initialJobId,
  actions,
}: {
  resume: Resume;
  tab: DetailTab;
  onTab: (t: DetailTab) => void;
  initialJobId?: string;
  actions: ReturnType<typeof useResumeActions>;
}) {
  const ready = resume.aiProcessingStatus === 'READY';
  const st = statusBadge(resume);
  const versions = [...(resume.versions ?? [])].sort((a, b) => b.versionNumber - a.versionNumber);
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={st.tone} icon={st.icon}>
          {st.label}
        </Badge>
        {resume.isDefault && <Badge tone="primary">Default</Badge>}
        <span className="text-xs text-fg-subtle">
          {resume.originalFilename ?? 'File name not available'} · {formatBytes(resume.fileSize)} · uploaded {formatDate(resume.uploadedAt)}
        </span>
      </div>
      {resume.aiProcessingStatus === 'FAILED' && (
        <Notice tone="danger" title="AI processing failed">
          {resume.processingError ?? 'No reason was reported.'}
        </Notice>
      )}
      {isProcessing(resume) && (
        <Notice tone="info" icon={<Loader2 className="h-4 w-4 animate-spin" />} title="Processing in progress">
          The AI model is parsing this resume. This can take a few minutes on a local model; this page updates automatically.
        </Notice>
      )}
      <div className="flex flex-wrap gap-2">
        <Button
          size="sm"
          variant="secondary"
          icon={<RefreshCw className="h-3.5 w-3.5" />}
          loading={actions.reprocess.isPending}
          disabled={isProcessing(resume) || actions.reprocess.isPending}
          onClick={() => actions.reprocess.mutate(resume.id)}
        >
          Re-process
        </Button>
        <Button
          size="sm"
          variant="secondary"
          icon={<Download className="h-3.5 w-3.5" />}
          loading={actions.download.isPending}
          onClick={() => actions.download.mutate(resume)}
        >
          Download
        </Button>
      </div>
      <Tabs<DetailTab>
        ariaLabel="Resume details"
        value={tab}
        onChange={onTab}
        items={[
          { id: 'knowledge', label: 'Parsed knowledge' },
          { id: 'ats', label: 'ATS' },
          { id: 'tailor', label: 'Tailor to a job' },
          { id: 'versions', label: 'Versions', count: versions.length },
        ]}
      />
      <div role="tabpanel" className="pt-2">
        {tab === 'knowledge' && <KnowledgePanel resumeId={resume.id} ready={ready} />}
        {tab === 'ats' && <AtsPanel resumeId={resume.id} ready={ready} />}
        {tab === 'tailor' && <TailorPanel resumeId={resume.id} ready={ready} initialJobId={initialJobId} />}
        {tab === 'versions' &&
          (versions.length === 0 ? (
            <EmptyState compact title="No versions recorded" />
          ) : (
            <ul className="divide-y divide-border rounded-lg border border-border">
              {versions.map((v) => (
                <li key={v.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 text-sm">
                  <div>
                    <p className="font-medium text-fg">Version {v.versionNumber}</p>
                    <p className="text-xs text-fg-muted">{v.changeReason ?? 'No change reason recorded'}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    {v.generatedByAi && <Badge tone="primary">AI-generated</Badge>}
                    <span className="text-xs text-fg-subtle">{formatDateTime(v.createdAt)}</span>
                  </div>
                </li>
              ))}
            </ul>
          ))}
      </div>
    </div>
  );
}

export function ResumesPage() {
  const q = useResumes();
  const actions = useResumeActions();
  const [params, setParams] = useSearchParams();
  const [toDelete, setToDelete] = useState<Resume | null>(null);

  const selectedId = params.get('resumeId');
  const tabParam = params.get('tab');
  const tab: DetailTab = tabParam === 'ats' || tabParam === 'tailor' || tabParam === 'versions' ? tabParam : 'knowledge';
  const jobIdParam = params.get('jobId') ?? undefined;
  const selected = q.data?.find((r) => r.id === selectedId) ?? null;

  const open = (id: string, t: DetailTab = 'knowledge') => {
    const next = new URLSearchParams(params);
    next.set('resumeId', id);
    next.set('tab', t);
    setParams(next);
  };
  const close = () => {
    const next = new URLSearchParams(params);
    next.delete('resumeId');
    next.delete('tab');
    next.delete('jobId');
    setParams(next);
  };
  const setTab = (t: DetailTab) => {
    const next = new URLSearchParams(params);
    next.set('tab', t);
    setParams(next, { replace: true });
  };

  const resumes = (q.data ?? []).filter((r) => !r.isArchived);

  // `?tailorJobId=` (from a job) opens the Tailor tab on the default (or first) processed resume.
  const tailorJobId = params.get('tailorJobId');
  useEffect(() => {
    if (!tailorJobId || !q.data) return;
    const ready = q.data.filter((r) => !r.isArchived && r.aiProcessingStatus === 'READY');
    const target = ready.find((r) => r.isDefault) ?? ready[0];
    const next = new URLSearchParams(params);
    next.delete('tailorJobId');
    if (target) {
      next.set('resumeId', target.id);
      next.set('tab', 'tailor');
      next.set('jobId', tailorJobId);
    } else {
      toast.info('No processed resume yet', 'Upload a resume and wait for processing to finish before tailoring it to a job.');
    }
    setParams(next, { replace: true });
  }, [tailorJobId, q.data, params, setParams]);

  return (
    <>
      <PageHeader
        title="Resumes"
        description="Upload resumes, see what the AI extracted, check ATS quality and tailor a resume to a specific job."
      />
      <div className="grid gap-6 lg:grid-cols-[minmax(0,360px)_minmax(0,1fr)]">
        <UploadCard />
        <Card>
          <CardHeader
            title="Your resumes"
            description={q.data?.some(isProcessing) ? 'Checking processing status every few seconds…' : undefined}
            icon={<FileText className="h-4 w-4" />}
          />
          <div className="px-5 py-4">
            {q.isLoading && <SkeletonRows rows={3} />}
            {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
            {q.data && resumes.length === 0 && (
              <EmptyState
                icon={<FileText className="h-5 w-5" />}
                title="No resumes yet"
                description="Upload your resume to unlock matching, ATS analysis, interview practice and tailored suggestions."
              />
            )}
            <ul className="space-y-3">
              {resumes.map((r) => {
                const st = statusBadge(r);
                return (
                  <li key={r.id} className="rounded-lg border border-border p-4">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <button type="button" onClick={() => open(r.id)} className="min-w-0 flex-1 text-left">
                        <p className="truncate text-sm font-semibold text-fg hover:text-primary">{r.title}</p>
                        <p className="truncate text-xs text-fg-subtle">
                          {r.originalFilename ?? 'File name not available'} · {formatBytes(r.fileSize)} · {formatDate(r.uploadedAt)}
                        </p>
                      </button>
                      <div className="flex flex-wrap items-center gap-1.5">
                        {r.isDefault && <Badge tone="primary">Default</Badge>}
                        <Badge tone={st.tone} icon={st.icon}>
                          {st.label}
                        </Badge>
                      </div>
                    </div>
                    {r.aiProcessingStatus === 'FAILED' && (
                      <p className="mt-2 text-xs text-danger">{r.processingError ?? 'Processing failed; no reason was reported.'}</p>
                    )}
                    <div className="mt-3 flex flex-wrap gap-2">
                      <Button size="sm" variant="secondary" onClick={() => open(r.id)}>
                        View details
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => open(r.id, 'tailor')} disabled={r.aiProcessingStatus !== 'READY'}>
                        Tailor
                      </Button>
                      {!r.isDefault && (
                        <Button
                          size="sm"
                          variant="ghost"
                          icon={<Star className="h-3.5 w-3.5" />}
                          loading={actions.setDefault.isPending && actions.setDefault.variables === r.id}
                          onClick={() => actions.setDefault.mutate(r.id)}
                        >
                          Set default
                        </Button>
                      )}
                      <Button
                        size="sm"
                        variant="ghost"
                        icon={<Download className="h-3.5 w-3.5" />}
                        loading={actions.download.isPending && actions.download.variables?.id === r.id}
                        onClick={() => actions.download.mutate(r)}
                        aria-label={`Download ${r.title}`}
                      >
                        Download
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        icon={<Trash2 className="h-3.5 w-3.5" />}
                        onClick={() => setToDelete(r)}
                        aria-label={`Delete ${r.title}`}
                      >
                        Delete
                      </Button>
                    </div>
                  </li>
                );
              })}
            </ul>
          </div>
        </Card>
      </div>

      <Modal
        open={Boolean(selectedId)}
        onClose={close}
        variant="drawer"
        size="xl"
        title={selected?.title ?? 'Resume'}
        description="Everything shown here was extracted from your file by the AI and checked against its text."
      >
        {q.isLoading && <SkeletonRows rows={4} />}
        {q.data && !selected && <EmptyState title="Resume not found" description="It may have been deleted." />}
        {selected && <ResumeDetail resume={selected} tab={tab} onTab={setTab} initialJobId={jobIdParam} actions={actions} />}
      </Modal>

      <Modal
        open={toDelete !== null}
        onClose={() => setToDelete(null)}
        title="Delete resume?"
        description={toDelete?.title}
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setToDelete(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={actions.remove.isPending}
              onClick={() =>
                toDelete &&
                actions.remove.mutate(toDelete.id, {
                  onSuccess: () => {
                    if (selectedId === toDelete.id) close();
                    setToDelete(null);
                  },
                })
              }
            >
              Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-fg-muted">The file and its parsed data will be removed. This cannot be undone.</p>
      </Modal>
    </>
  );
}
