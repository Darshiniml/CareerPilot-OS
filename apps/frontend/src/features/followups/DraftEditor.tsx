import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, Mail, Send, ShieldCheck, Trash2 } from 'lucide-react';
import { followUpsApi } from '../../api/endpoints';
import { qk, useEmailConnections } from '../../api/queries';
import type { EmailProvider, FollowUpDraft } from '../../api/types';
import {
  Badge,
  Button,
  ErrorState,
  Input,
  Modal,
  Notice,
  Select,
  SkeletonRows,
  Textarea,
} from '../../components/ui';
import { VerificationNotice } from '../../components/shared';
import { cn, formatDateTime, humanize, parseJson } from '../../lib/format';
import { toast } from '../../store/toast';
import { bareAddress, draftStatusTone, EDITABLE_STATUSES, findPlaceholders, HighlightedText } from './helpers';

type Confirm = 'approve' | 'send' | 'discard' | null;

export function DraftEditor({ draftId, onClose }: { draftId: string | null; onClose: () => void }) {
  const qc = useQueryClient();
  const open = Boolean(draftId);
  const q = useQuery({
    queryKey: qk.draft(draftId ?? ''),
    queryFn: () => followUpsApi.draft(draftId as string),
    enabled: open,
  });
  const mail = useEmailConnections();
  const draft = q.data?.draft;
  const allowed = q.data?.allowedRecipients ?? [];
  const events = q.data?.events ?? [];

  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [recipient, setRecipient] = useState('');
  const [confirm, setConfirm] = useState<Confirm>(null);
  const [provider, setProvider] = useState<EmailProvider | ''>('');
  const [actionError, setActionError] = useState<unknown>(null);

  // Load server values whenever a new version of the draft arrives.
  useEffect(() => {
    if (!draft) return;
    setSubject(draft.subject ?? '');
    setBody(draft.body ?? '');
    setRecipient(draft.recipient ?? '');
  }, [draft?.id, draft?.updatedAt]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    setActionError(null);
    setConfirm(null);
  }, [draftId]);

  const connected = (mail.data ?? []).filter((c) => c.connected);
  useEffect(() => {
    if (!provider && connected.length > 0) setProvider(connected[0].provider);
  }, [connected, provider]);

  const refresh = (updated?: FollowUpDraft) => {
    if (draftId) qc.invalidateQueries({ queryKey: qk.draft(draftId) });
    qc.invalidateQueries({ queryKey: qk.drafts });
    if (updated) setActionError(null);
  };
  const onErr = (e: unknown) => {
    setActionError(e);
    setConfirm(null);
  };

  const save = useMutation({
    mutationFn: () => followUpsApi.updateDraft(draftId as string, { subject, body, recipient }),
    onSuccess: (d) => {
      refresh(d);
      toast.success('Draft saved', d.status === 'DRAFT' ? 'Approve it again before sending.' : undefined);
    },
    onError: onErr,
  });
  const approve = useMutation({
    mutationFn: () => followUpsApi.approve(draftId as string),
    onSuccess: (d) => {
      refresh(d);
      setConfirm(null);
      toast.success('Draft approved', 'You can now send it.');
    },
    onError: onErr,
  });
  const send = useMutation({
    mutationFn: () => followUpsApi.send(draftId as string, provider || undefined),
    onSuccess: (d) => {
      refresh(d);
      setConfirm(null);
      if (d.status === 'FAILED') toast.error('Sending failed', d.sendError ?? 'The mail provider rejected the message.');
      else toast.success(d.status === 'SENT' ? 'Email sent' : `Status: ${humanize(d.status)}`);
    },
    onError: onErr,
  });
  const discard = useMutation({
    mutationFn: () => followUpsApi.discard(draftId as string),
    onSuccess: (d) => {
      refresh(d);
      setConfirm(null);
      toast.info('Draft discarded');
    },
    onError: onErr,
  });

  const storedPlaceholders = useMemo(() => parseJson<string[]>(draft?.placeholdersJson) ?? [], [draft?.placeholdersJson]);
  const livePlaceholders = useMemo(() => findPlaceholders(subject, body), [subject, body]);
  const filledPlaceholders = storedPlaceholders.filter((p) => !livePlaceholders.includes(p));

  if (!open) return null;

  const status = draft?.status ?? 'DRAFT';
  const editable = EDITABLE_STATUSES.includes(status);
  const dirty =
    !!draft && (subject !== (draft.subject ?? '') || body !== (draft.body ?? '') || recipient !== (draft.recipient ?? ''));
  const recipientAllowed = !!recipient && allowed.includes(bareAddress(recipient));
  const busy = save.isPending || approve.isPending || send.isPending || discard.isPending;

  const approveBlockers: string[] = [];
  if (dirty) approveBlockers.push('Save your changes first.');
  if (livePlaceholders.length > 0) approveBlockers.push('Fill in every placeholder.');
  if (!recipient) approveBlockers.push('Choose a recipient.');
  else if (!recipientAllowed) approveBlockers.push('The recipient must be one of the allowed addresses.');
  const canApprove = editable && (status === 'DRAFT' || status === 'FAILED') && approveBlockers.length === 0;

  const recipientOptions = allowed.map((a) => ({ value: a, label: a }));
  if (recipient && !allowed.includes(recipient)) {
    recipientOptions.unshift({ value: recipient, label: `${recipient}${recipientAllowed ? '' : ' (not allowed)'}` });
  }

  return (
    <>
      <Modal
        open={open}
        onClose={onClose}
        variant="drawer"
        size="xl"
        dismissible={!busy}
        title={draft ? draft.subject || 'Untitled draft' : 'Draft'}
        description="Draft — review before sending. Nothing is sent without your explicit approval."
        footer={
          draft && (
            <>
              {status !== 'SENT' && status !== 'DISCARDED' && status !== 'SENDING' && (
                <Button variant="danger" icon={<Trash2 className="h-4 w-4" />} onClick={() => setConfirm('discard')} disabled={busy}>
                  Discard
                </Button>
              )}
              {editable && (
                <Button variant="secondary" onClick={() => save.mutate()} loading={save.isPending} disabled={!dirty || busy}>
                  Save changes
                </Button>
              )}
              {(status === 'DRAFT' || status === 'FAILED') && (
                <Button
                  icon={<ShieldCheck className="h-4 w-4" />}
                  onClick={() => setConfirm('approve')}
                  disabled={!canApprove || busy}
                  title={approveBlockers.join(' ')}
                >
                  Approve
                </Button>
              )}
              {status === 'APPROVED' && (
                <Button
                  icon={<Send className="h-4 w-4" />}
                  onClick={() => setConfirm('send')}
                  disabled={busy || dirty || connected.length === 0}
                  title={connected.length === 0 ? 'Connect a mailbox in Settings first' : dirty ? 'Save (and re-approve) your changes first' : undefined}
                >
                  Send
                </Button>
              )}
            </>
          )
        }
      >
        {q.isLoading && <SkeletonRows rows={5} />}
        {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
        {draft && (
          <div className="space-y-5">
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone={draftStatusTone(status)}>{humanize(status)}</Badge>
              <Badge tone="primary">AI-generated draft</Badge>
              <Badge>{humanize(draft.draftType)}</Badge>
              {draft.editedByUser && <Badge tone="info">Edited by you</Badge>}
              {draft.approvedAt && <span className="text-xs text-fg-subtle">Approved {formatDateTime(draft.approvedAt)}</span>}
              {draft.sentAt && <span className="text-xs text-fg-subtle">Sent {formatDateTime(draft.sentAt)}</span>}
            </div>

            {actionError !== null && <ErrorState error={actionError} />}

            {status === 'FAILED' && (
              <Notice tone="danger" title="Sending failed">
                {draft.sendError ?? 'The provider did not report a reason.'} You can edit, re-approve and try again.
              </Notice>
            )}
            {status === 'APPROVED' && (
              <Notice tone="info" title="Approved — ready to send">
                Editing and saving will return the draft to DRAFT and require approval again.
              </Notice>
            )}

            <MailboxNotice
              loading={mail.isLoading}
              error={mail.isError ? mail.error : null}
              connectedCount={connected.length}
              onRetry={() => mail.refetch()}
            />

            <div className="space-y-4">
              {allowed.length === 0 ? (
                <Notice tone="warning" title="No allowed recipient yet">
                  For safety, a follow-up can only be sent to someone who emailed you about this application. Add their email
                  in the <Link className="font-medium text-primary underline" to="/inbox">Inbox</Link> and it will appear here.
                </Notice>
              ) : (
                <Select
                  label="Recipient"
                  required
                  disabled={!editable || busy}
                  value={recipient}
                  onChange={(e) => setRecipient(e.target.value)}
                  placeholder="Select a recipient…"
                  options={recipientOptions}
                  hint="Only people who emailed you about this application can receive it."
                  error={recipient && !recipientAllowed ? 'This address is not in the allowed list.' : null}
                />
              )}
              <Input
                label="Subject"
                value={subject}
                disabled={!editable || busy}
                onChange={(e) => setSubject(e.target.value)}
              />
              <Textarea label="Body" rows={12} value={body} disabled={!editable || busy} onChange={(e) => setBody(e.target.value)} />
            </div>

            <section aria-labelledby="ph-title" className="rounded-lg border border-border p-4">
              <h3 id="ph-title" className="text-sm font-semibold text-fg">
                Placeholders
              </h3>
              {livePlaceholders.length === 0 && filledPlaceholders.length === 0 && (
                <p className="mt-1 text-sm text-fg-muted">This draft has no placeholders.</p>
              )}
              {livePlaceholders.length > 0 && (
                <>
                  <p className="mt-1 text-sm text-fg-muted">
                    Replace each of these with real information before approving:
                  </p>
                  <ul className="mt-2 flex flex-wrap gap-1.5">
                    {livePlaceholders.map((p) => (
                      <li key={p} className="rounded-md border border-warning/30 bg-warning-soft px-2 py-0.5 text-xs font-medium text-warning">
                        {p}
                      </li>
                    ))}
                  </ul>
                </>
              )}
              {filledPlaceholders.length > 0 && (
                <ul className="mt-2 flex flex-wrap gap-1.5" aria-label="Filled placeholders">
                  {filledPlaceholders.map((p) => (
                    <li key={p} className="inline-flex items-center gap-1 rounded-md border border-success/25 bg-success-soft px-2 py-0.5 text-xs text-success">
                      <CheckCircle2 className="h-3 w-3" /> {p}
                    </li>
                  ))}
                </ul>
              )}
              {livePlaceholders.length > 0 && (
                <div className="mt-3 max-h-56 overflow-y-auto whitespace-pre-wrap rounded-md bg-surface-2 p-3 text-sm text-fg">
                  <HighlightedText text={body} />
                </div>
              )}
            </section>

            <VerificationNotice verification={draft.verificationJson} />

            <section aria-labelledby="ev-title">
              <h3 id="ev-title" className="mb-2 text-sm font-semibold text-fg">
                Activity &amp; audit log
              </h3>
              {events.length === 0 ? (
                <p className="text-sm text-fg-muted">No approval or send events yet.</p>
              ) : (
                <ol className="space-y-3 border-l border-border pl-4">
                  {events.map((ev) => (
                    <li key={ev.id} className="relative">
                      <span
                        className={cn(
                          'absolute -left-[21px] top-1.5 h-2.5 w-2.5 rounded-full border-2 border-surface',
                          ev.eventType === 'SENT' ? 'bg-success' : ev.eventType === 'FAILED' ? 'bg-danger' : 'bg-primary',
                        )}
                        aria-hidden
                      />
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-medium text-fg">{humanize(ev.eventType)}</span>
                        {ev.provider && <Badge>{ev.provider}</Badge>}
                        <span className="text-xs text-fg-subtle">{formatDateTime(ev.createdAt)}</span>
                      </div>
                      {ev.recipient && <p className="text-xs text-fg-muted">To: {ev.recipient}</p>}
                      {ev.detail && <p className="text-xs text-fg-muted">{ev.detail}</p>}
                      {ev.correlationId && (
                        <p className="text-[11px] text-fg-subtle">
                          Ref: <span className="font-mono">{ev.correlationId}</span>
                        </p>
                      )}
                    </li>
                  ))}
                </ol>
              )}
            </section>
          </div>
        )}
      </Modal>

      {/* Explicit confirmations */}
      <Modal
        open={confirm === 'approve'}
        onClose={() => setConfirm(null)}
        dismissible={!approve.isPending}
        size="sm"
        title="Approve this draft?"
        description="Approving confirms you have reviewed the text and the recipient."
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirm(null)} disabled={approve.isPending}>
              Cancel
            </Button>
            <Button onClick={() => approve.mutate()} loading={approve.isPending}>
              Approve
            </Button>
          </>
        }
      >
        <p className="text-sm text-fg">
          To: <span className="font-medium">{recipient}</span>
        </p>
        <p className="mt-1 text-sm text-fg">
          Subject: <span className="font-medium">{subject}</span>
        </p>
      </Modal>

      <Modal
        open={confirm === 'send'}
        onClose={() => setConfirm(null)}
        dismissible={!send.isPending}
        size="sm"
        title="Send this email now?"
        description="It will be sent from your connected mailbox and cannot be recalled."
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirm(null)} disabled={send.isPending}>
              Cancel
            </Button>
            <Button icon={<Mail className="h-4 w-4" />} onClick={() => send.mutate()} loading={send.isPending}>
              Send email
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          <p className="text-sm text-fg">
            To: <span className="font-medium">{draft?.recipient ?? recipient}</span>
          </p>
          <p className="text-sm text-fg">
            Subject: <span className="font-medium">{draft?.subject}</span>
          </p>
          {connected.length > 1 && (
            <Select
              label="Send from"
              value={provider}
              onChange={(e) => setProvider(e.target.value as EmailProvider)}
              options={connected.map((c) => ({ value: c.provider, label: `${humanize(c.provider)}${c.emailAddress ? ` — ${c.emailAddress}` : ''}` }))}
            />
          )}
          {connected.length === 1 && (
            <p className="text-sm text-fg-muted">
              From: {humanize(connected[0].provider)}
              {connected[0].emailAddress ? ` — ${connected[0].emailAddress}` : ''}
            </p>
          )}
        </div>
      </Modal>

      <Modal
        open={confirm === 'discard'}
        onClose={() => setConfirm(null)}
        dismissible={!discard.isPending}
        size="sm"
        title="Discard this draft?"
        description="The draft will be kept for the audit log but can no longer be sent."
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirm(null)} disabled={discard.isPending}>
              Cancel
            </Button>
            <Button variant="danger" onClick={() => discard.mutate()} loading={discard.isPending}>
              Discard
            </Button>
          </>
        }
      >
        <p className="text-sm text-fg-muted">You can create a new draft at any time.</p>
      </Modal>
    </>
  );
}

export function MailboxNotice({
  loading,
  error,
  connectedCount,
  onRetry,
}: {
  loading: boolean;
  error: unknown;
  connectedCount: number;
  onRetry: () => void;
}) {
  if (loading) return null;
  if (error) return <ErrorState error={error} onRetry={onRetry} compact />;
  if (connectedCount > 0) return null;
  return (
    <Notice tone="warning" title="No mailbox connected">
      You can write and approve drafts, but sending requires a connected Gmail or Outlook mailbox.{' '}
      <Link to="/settings" className="font-medium text-primary underline">
        Connect one in Settings
      </Link>
      .
    </Notice>
  );
}
