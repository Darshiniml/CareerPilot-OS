import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowRight, Inbox, Mail, Plus, Sparkles, Workflow } from 'lucide-react';
import { communicationsApi } from '../../api/endpoints';
import { qk } from '../../api/queries';
import type { ClassificationResponse, Communication, CommunicationProcessingResponse } from '../../api/types';
import {
  AiLabel,
  AiProgress,
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  Input,
  Modal,
  Notice,
  PageHeader,
  SkeletonRows,
  Textarea,
  Unavailable,
} from '../../components/ui';
import { OUTCOME_EXPLANATIONS, outcomeTone, stateLabel } from '../../lib/domain';
import { cn, formatDateTime, formatRelative, humanize, isNum } from '../../lib/format';
import { toast } from '../../store/toast';

const EMAIL_RE = /^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+$/;

function classificationBadge(c: Communication['classification']) {
  if (!c || c === 'UNKNOWN') return <Badge tone="neutral">Not classified</Badge>;
  const tone = c === 'OFFER' || c === 'INTERVIEW_INVITATION' ? 'success' : c === 'REJECTION' ? 'danger' : 'info';
  return <Badge tone={tone}>{humanize(c)}</Badge>;
}

function nowLocalInput() {
  const d = new Date();
  d.setSeconds(0, 0);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

function AddEmailModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient();
  const [sender, setSender] = useState('');
  const [recipient, setRecipient] = useState('');
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [receivedAt, setReceivedAt] = useState(nowLocalInput);
  const [touched, setTouched] = useState(false);

  const errors = {
    sender: !sender.trim() ? 'Sender is required.' : !EMAIL_RE.test(sender.trim()) ? 'Enter a valid email address.' : null,
    recipient: recipient.trim() && !EMAIL_RE.test(recipient.trim()) ? 'Enter a valid email address.' : null,
    content: !subject.trim() && !body.trim() ? 'Provide a subject or a body.' : null,
    receivedAt: !receivedAt || Number.isNaN(new Date(receivedAt).getTime()) ? 'Enter when the email was received.' : null,
  };
  const valid = !Object.values(errors).some(Boolean);

  const reset = () => {
    setSender('');
    setRecipient('');
    setSubject('');
    setBody('');
    setReceivedAt(nowLocalInput());
    setTouched(false);
  };

  const m = useMutation({
    mutationFn: () =>
      communicationsApi.create({
        provider: 'MANUAL',
        externalMessageId: `manual-${crypto.randomUUID()}`,
        sender: sender.trim(),
        recipient: recipient.trim() || null,
        subject: subject.trim() || null,
        body: body.trim() || null,
        receivedAt: new Date(receivedAt).toISOString(),
      }),
    onSuccess: () => {
      toast.success('Email added', 'Classify it to detect what it means for your application.');
      qc.invalidateQueries({ queryKey: qk.communications });
      reset();
      onClose();
    },
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Add an HR email"
      description="Paste an email you received from a recruiter or company."
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button
            loading={m.isPending}
            onClick={() => {
              setTouched(true);
              if (valid) m.mutate();
            }}
          >
            Add email
          </Button>
        </>
      }
    >
      <form
        className="space-y-3"
        onSubmit={(e) => {
          e.preventDefault();
          setTouched(true);
          if (valid) m.mutate();
        }}
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <Input label="From (sender email)" required type="email" value={sender} onChange={(e) => setSender(e.target.value)} error={touched ? errors.sender : null} />
          <Input label="To (optional)" type="email" value={recipient} onChange={(e) => setRecipient(e.target.value)} error={touched ? errors.recipient : null} />
        </div>
        <Input label="Received at" required type="datetime-local" value={receivedAt} onChange={(e) => setReceivedAt(e.target.value)} error={touched ? errors.receivedAt : null} />
        <Input label="Subject" value={subject} onChange={(e) => setSubject(e.target.value)} />
        <Textarea label="Body" rows={8} value={body} onChange={(e) => setBody(e.target.value)} error={touched ? errors.content : null} />
        {m.isError && <ErrorState error={m.error} compact />}
        <button type="submit" className="hidden" aria-hidden tabIndex={-1} />
      </form>
    </Modal>
  );
}

function Detail({ c }: { c: Communication }) {
  const qc = useQueryClient();
  const [classification, setClassification] = useState<ClassificationResponse | null>(null);
  const [processed, setProcessed] = useState<CommunicationProcessingResponse | null>(null);

  const classify = useMutation({
    mutationFn: () => communicationsApi.classify(c.id),
    onSuccess: (r) => {
      setClassification(r);
      qc.invalidateQueries({ queryKey: qk.communications });
    },
  });
  const process = useMutation({
    mutationFn: () => communicationsApi.process(c.id),
    onSuccess: (r) => {
      setProcessed(r);
      qc.invalidateQueries({ queryKey: qk.communications });
      qc.invalidateQueries({ queryKey: qk.applications });
      qc.invalidateQueries({ queryKey: qk.notifications });
      if (r.applicationId) {
        qc.invalidateQueries({ queryKey: qk.application(r.applicationId) });
        qc.invalidateQueries({ queryKey: qk.applicationTimeline(r.applicationId) });
      }
    },
  });

  const cls = classification?.classification ?? c.classification;
  const confidence = classification ? classification.confidence : c.classificationConfidence;
  const evidence = classification?.evidence ?? c.classificationReason;
  const classified = cls && cls !== 'UNKNOWN';
  const busy = classify.isPending || process.isPending;

  return (
    <div className="space-y-5">
      <div>
        <h2 className="break-words text-base font-semibold text-fg">{c.subject || <Unavailable>(no subject)</Unavailable>}</h2>
        <p className="mt-1 break-words text-xs text-fg-muted">
          From {c.sender}
          {c.recipient && <> · to {c.recipient}</>} · {formatDateTime(c.receivedAt)} · {humanize(c.provider)}
        </p>
      </div>
      <div className="max-h-80 overflow-y-auto whitespace-pre-wrap break-words rounded-lg border border-border bg-surface-2 p-3 text-sm text-fg">
        {c.body || <Unavailable>(no body)</Unavailable>}
      </div>

      <section className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="text-sm font-semibold text-fg">Classification</h3>
          {classificationBadge(cls)}
          {classified && <AiLabel />}
          {classified && isNum(confidence) && <span className="text-xs text-fg-muted">Confidence {Math.round(confidence * 100)}%</span>}
        </div>
        {classified && evidence && <p className="break-words text-xs text-fg-muted">Evidence: {evidence}</p>}
        {classify.isPending && <AiProgress label="Classifying this email" />}
        {classify.isError && <ErrorState error={classify.error} compact />}
        <Button size="sm" variant="secondary" icon={<Sparkles className="h-3.5 w-3.5" />} loading={classify.isPending} disabled={busy} onClick={() => classify.mutate()}>
          {classified ? 'Re-classify' : 'Classify with AI'}
        </Button>
      </section>

      <section className="space-y-2">
        <h3 className="text-sm font-semibold text-fg">Application</h3>
        {c.matchedApplicationId ? (
          <p className="text-sm text-fg">
            Linked to{' '}
            <Link to={`/applications?id=${c.matchedApplicationId}`} className="font-medium text-primary hover:underline">
              this application
            </Link>
            {isNum(c.matchConfidence) && <span className="text-xs text-fg-muted"> · match confidence {Math.round(c.matchConfidence * 100)}%</span>}
          </p>
        ) : (
          <Unavailable>Not linked to an application yet. Processing tries to match it.</Unavailable>
        )}
        {c.matchEvidence && <p className="break-words text-xs text-fg-muted">Match evidence: {c.matchEvidence}</p>}
      </section>

      <section className="space-y-2">
        <h3 className="text-sm font-semibold text-fg">Apply to application status</h3>
        <p className="text-xs text-fg-muted">
          Processing applies deterministic rules to the classification. It never reopens closed applications or moves a status backwards.
        </p>
        {!classified && <Notice tone="neutral">Classify the email first; unclassified emails cannot change a status.</Notice>}
        {process.isError && <ErrorState error={process.error} compact />}
        <Button size="sm" icon={<Workflow className="h-3.5 w-3.5" />} loading={process.isPending} disabled={busy} onClick={() => process.mutate()}>
          Process email
        </Button>
        {processed && (
          <div className="space-y-2 rounded-lg border border-border p-3" role="status">
            <Badge tone={outcomeTone(processed.outcome)}>{humanize(processed.outcome)}</Badge>
            <p className="text-sm text-fg-muted">{OUTCOME_EXPLANATIONS[processed.outcome] ?? 'Processed.'}</p>
            {(processed.previousState || processed.newState) && (
              <p className="flex flex-wrap items-center gap-1.5 text-sm">
                <span className="text-fg-muted">{processed.previousState ? stateLabel(processed.previousState) : 'Unknown'}</span>
                <ArrowRight className="h-3.5 w-3.5 text-fg-subtle" aria-label="to" />
                <span className="font-medium text-fg">
                  {processed.newState ? stateLabel(processed.newState) : processed.applicationState ? stateLabel(processed.applicationState) : 'Unchanged'}
                </span>
              </p>
            )}
            {processed.applicationId && (
              <Link to={`/applications?id=${processed.applicationId}`} className="inline-block text-sm font-medium text-primary hover:underline">
                Open application
              </Link>
            )}
          </div>
        )}
      </section>
    </div>
  );
}

function useIsDesktop() {
  const query = '(min-width: 1024px)';
  const [match, setMatch] = useState(() => typeof window !== 'undefined' && window.matchMedia(query).matches);
  useEffect(() => {
    const mql = window.matchMedia(query);
    const on = () => setMatch(mql.matches);
    mql.addEventListener('change', on);
    return () => mql.removeEventListener('change', on);
  }, []);
  return match;
}

export function InboxPage() {
  const isDesktop = useIsDesktop();
  const q = useQuery({ queryKey: qk.communications, queryFn: communicationsApi.list });
  const [adding, setAdding] = useState(false);
  const [params, setParams] = useSearchParams();
  const selectedId = params.get('id');
  const setSelectedId = (id: string | null) => {
    const next = new URLSearchParams(params);
    if (id) next.set('id', id);
    else next.delete('id');
    setParams(next, { replace: true });
  };
  const selected = useMemo(() => q.data?.find((c) => c.id === selectedId) ?? null, [q.data, selectedId]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Inbox"
        description="HR emails about your applications. Classify them with AI and apply them to your application status."
        actions={
          <Button icon={<Plus className="h-4 w-4" />} onClick={() => setAdding(true)}>
            Add email
          </Button>
        }
      />
      <AddEmailModal open={adding} onClose={() => setAdding(false)} />

      {q.isLoading && <SkeletonRows rows={4} />}
      {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
      {q.data && q.data.length === 0 && (
        <EmptyState
          icon={<Inbox className="h-5 w-5" />}
          title="No HR emails yet"
          description="Emails ingested from a connected mailbox appear here. You can also paste one manually."
          action={<Button onClick={() => setAdding(true)}>Add email</Button>}
        />
      )}

      {q.data && q.data.length > 0 && (
        <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
          <Card className="overflow-hidden">
            <ul className="divide-y divide-border" aria-label="Emails">
              {q.data.map((c) => (
                <li key={c.id}>
                  <button
                    type="button"
                    onClick={() => setSelectedId(c.id)}
                    aria-current={c.id === selectedId || undefined}
                    className={cn('w-full px-4 py-3 text-left hover:bg-surface-2', c.id === selectedId && 'bg-primary-soft/50')}
                  >
                    <div className="flex items-start gap-2">
                      <Mail className="mt-0.5 h-4 w-4 shrink-0 text-fg-subtle" aria-hidden />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-fg">{c.subject || '(no subject)'}</p>
                        <p className="truncate text-xs text-fg-muted">{c.sender}</p>
                        <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                          {classificationBadge(c.classification)}
                          {c.classification !== 'UNKNOWN' && isNum(c.classificationConfidence) && (
                            <span className="text-[11px] text-fg-subtle">{Math.round(c.classificationConfidence * 100)}%</span>
                          )}
                          <Badge tone={c.processingStatus === 'FAILED' ? 'danger' : c.processingStatus === 'PROCESSED' ? 'success' : 'neutral'}>
                            {humanize(c.processingStatus)}
                          </Badge>
                          {c.matchedApplicationId && <Badge tone="primary">Linked</Badge>}
                        </div>
                      </div>
                      <span className="shrink-0 text-[11px] text-fg-subtle">{formatRelative(c.receivedAt)}</span>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          </Card>
          {isDesktop ? (
            <Card className="p-5">
              {selected ? <Detail key={selected.id} c={selected} /> : <EmptyState compact title="Select an email" description="Choose an email to read, classify and process it." />}
            </Card>
          ) : (
            selected && (
              <Modal open onClose={() => setSelectedId(null)} variant="drawer" title="Email">
                <Detail key={selected.id} c={selected} />
              </Modal>
            )
          )}
        </div>
      )}
    </div>
  );
}
