import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CalendarClock, Check, ExternalLink, FilePlus2, MailPlus, Send, X } from 'lucide-react';
import { followUpsApi } from '../../api/endpoints';
import { qk, useDrafts, useEmailConnections, useFollowUps } from '../../api/queries';
import type { FollowUpDraft, FollowUpRecommendation } from '../../api/types';
import {
  Badge,
  Button,
  Card,
  Checkbox,
  EmptyState,
  ErrorState,
  PageHeader,
  Select,
  SkeletonRows,
  Tabs,
  BulletList,
} from '../../components/ui';
import { urgencyTone } from '../../lib/domain';
import { formatDate, formatRelative, humanize, isNum } from '../../lib/format';
import { toast } from '../../store/toast';
import { CreateDraftModal, type DraftSeed } from './CreateDraftModal';
import { DraftEditor, MailboxNotice } from './DraftEditor';
import { draftStatusTone, useApplicationLabels } from './helpers';

type Tab = 'recommendations' | 'drafts';

export function FollowUpsPage() {
  const [params, setParams] = useSearchParams();
  const tab: Tab = params.get('tab') === 'drafts' ? 'drafts' : 'recommendations';
  const openDraftId = params.get('draft');
  const [seed, setSeed] = useState<DraftSeed | null>(null);

  const recs = useFollowUps();
  const drafts = useDrafts();
  const mail = useEmailConnections();
  const connectedCount = (mail.data ?? []).filter((c) => c.connected).length;

  const setParam = (key: string, value: string | null) => {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (value === null) next.delete(key);
        else next.set(key, value);
        return next;
      },
      { replace: true },
    );
  };

  // Deep link: /follow-ups?applicationId=… opens the draft dialog for that application.
  const seedApp = params.get('applicationId');
  useEffect(() => {
    if (seedApp) {
      setSeed({ applicationId: seedApp });
      setParam('applicationId', null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seedApp]);

  const onCreated = (d: FollowUpDraft) => {
    setSeed(null);
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set('tab', 'drafts');
        next.set('draft', d.id);
        return next;
      },
      { replace: true },
    );
  };

  return (
    <div>
      <PageHeader
        title="Follow-ups"
        description="Suggested follow-ups based on your applications and recruiter emails. Drafts are AI-generated and are never sent without your approval."
        actions={
          <Button icon={<FilePlus2 className="h-4 w-4" />} onClick={() => setSeed({})}>
            New draft
          </Button>
        }
      />

      <div className="mb-4">
        <MailboxNotice
          loading={mail.isLoading}
          error={mail.isError ? mail.error : null}
          connectedCount={connectedCount}
          onRetry={() => mail.refetch()}
        />
      </div>

      <Tabs<Tab>
        ariaLabel="Follow-up sections"
        className="mb-5"
        value={tab}
        onChange={(t) => setParam('tab', t === 'recommendations' ? null : t)}
        items={[
          { id: 'recommendations', label: 'Recommendations', count: recs.data?.length ?? null },
          { id: 'drafts', label: 'Drafts', count: drafts.data?.length ?? null },
        ]}
      />

      {tab === 'recommendations' ? (
        <Recommendations
          loading={recs.isLoading}
          error={recs.isError ? recs.error : null}
          onRetry={() => recs.refetch()}
          items={recs.data ?? []}
          onDraft={(r) =>
            setSeed({
              applicationId: r.applicationId,
              draftType: r.suggestedDraftType,
              communicationId: r.relatedCommunicationId,
              recommendationKey: r.key,
              context: `${r.jobTitle ?? 'Application'}${r.company ? ` at ${r.company}` : ''}: ${r.reason}`,
            })
          }
        />
      ) : (
        <DraftsList
          loading={drafts.isLoading}
          error={drafts.isError ? drafts.error : null}
          onRetry={() => drafts.refetch()}
          items={drafts.data ?? []}
          onOpen={(id) => setParam('draft', id)}
        />
      )}

      <CreateDraftModal open={seed !== null} seed={seed} onClose={() => setSeed(null)} onCreated={onCreated} />
      <DraftEditor draftId={openDraftId} onClose={() => setParam('draft', null)} />
    </div>
  );
}

function Recommendations({
  loading,
  error,
  onRetry,
  items,
  onDraft,
}: {
  loading: boolean;
  error: unknown;
  onRetry: () => void;
  items: FollowUpRecommendation[];
  onDraft: (r: FollowUpRecommendation) => void;
}) {
  if (loading) return <SkeletonRows rows={4} />;
  if (error) return <ErrorState error={error} onRetry={onRetry} />;
  if (items.length === 0) {
    return (
      <EmptyState
        icon={<CalendarClock className="h-5 w-5" />}
        title="No follow-ups due"
        description="Recommendations appear when an application has gone quiet, or when a recruiter email needs a reply."
      />
    );
  }
  return (
    <ul className="space-y-3">
      {items.map((r) => (
        <li key={r.key}>
          <RecommendationCard r={r} onDraft={() => onDraft(r)} />
        </li>
      ))}
    </ul>
  );
}

function RecommendationCard({ r, onDraft }: { r: FollowUpRecommendation; onDraft: () => void }) {
  const qc = useQueryClient();
  const [snoozeDays, setSnoozeDays] = useState('3');
  const decide = useMutation({
    mutationFn: (v: { decision: 'DISMISSED' | 'SNOOZED' | 'DONE'; days?: number }) => followUpsApi.decide(r.key, v.decision, v.days),
    onSuccess: (_d, v) => {
      qc.invalidateQueries({ queryKey: qk.followUps });
      toast.success(
        v.decision === 'DONE' ? 'Marked as done' : v.decision === 'SNOOZED' ? `Snoozed for ${v.days} day(s)` : 'Dismissed',
      );
    },
    onError: toast.apiError,
  });
  const pending = decide.isPending ? decide.variables?.decision : null;

  return (
    <Card className="p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={urgencyTone(r.urgency)}>{humanize(r.urgency)} urgency</Badge>
            <Badge>{humanize(r.ruleCode)}</Badge>
          </div>
          <h3 className="mt-2 text-sm font-semibold text-fg">
            {r.jobTitle ?? <span className="italic text-fg-subtle">Job title not available</span>}
            {r.company && <span className="font-normal text-fg-muted"> · {r.company}</span>}
          </h3>
          <p className="mt-1 text-sm text-fg">{r.reason}</p>
          <dl className="mt-3 grid grid-cols-1 gap-x-6 gap-y-1.5 text-xs sm:grid-cols-3">
            <div>
              <dt className="text-fg-subtle">Recommended date</dt>
              <dd className="text-fg">{formatDate(r.recommendedDate)}</dd>
            </div>
            <div>
              <dt className="text-fg-subtle">Channel</dt>
              <dd className="text-fg">{humanize(r.recommendedChannel)}</dd>
            </div>
            <div>
              <dt className="text-fg-subtle">Confidence</dt>
              <dd className="text-fg">{isNum(r.confidence) ? `${Math.round(r.confidence * 100)}%` : 'Not available'}</dd>
            </div>
            {r.suggestedRecipient && (
              <div className="sm:col-span-3">
                <dt className="text-fg-subtle">Suggested recipient</dt>
                <dd className="break-all text-fg">{r.suggestedRecipient}</dd>
              </div>
            )}
          </dl>
          {r.evidence && r.evidence.length > 0 && (
            <div className="mt-3">
              <p className="mb-1 text-xs font-medium text-fg-muted">Evidence</p>
              <BulletList items={r.evidence} />
            </div>
          )}
          <Link
            to={`/applications?id=${r.applicationId}`}
            className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline"
          >
            Open application <ExternalLink className="h-3 w-3" />
          </Link>
        </div>
      </div>
      <div className="mt-4 flex flex-wrap items-end gap-2 border-t border-border pt-3">
        <Button size="sm" icon={<MailPlus className="h-4 w-4" />} onClick={onDraft} disabled={decide.isPending}>
          Draft email
        </Button>
        {r.recommendedChannel === 'APPLICATION_PORTAL' && (
          <span className="text-xs text-fg-muted">Suggested channel is the application portal — an email draft is optional.</span>
        )}
        <div className="ml-auto flex flex-wrap items-end gap-2">
          <Button
            size="sm"
            variant="secondary"
            icon={<Check className="h-4 w-4" />}
            loading={pending === 'DONE'}
            disabled={decide.isPending}
            onClick={() => decide.mutate({ decision: 'DONE' })}
          >
            Done
          </Button>
          <div className="flex items-end gap-1">
            <Select
              aria-label="Snooze duration"
              className="h-8 w-[104px] text-xs"
              value={snoozeDays}
              onChange={(e) => setSnoozeDays(e.target.value)}
              options={[1, 2, 3, 5, 7, 14, 30].map((d) => ({ value: String(d), label: `${d} day${d > 1 ? 's' : ''}` }))}
            />
            <Button
              size="sm"
              variant="secondary"
              loading={pending === 'SNOOZED'}
              disabled={decide.isPending}
              onClick={() => decide.mutate({ decision: 'SNOOZED', days: Number(snoozeDays) })}
            >
              Snooze
            </Button>
          </div>
          <Button
            size="sm"
            variant="ghost"
            icon={<X className="h-4 w-4" />}
            loading={pending === 'DISMISSED'}
            disabled={decide.isPending}
            onClick={() => decide.mutate({ decision: 'DISMISSED' })}
          >
            Dismiss
          </Button>
        </div>
      </div>
    </Card>
  );
}

function DraftsList({
  loading,
  error,
  onRetry,
  items,
  onOpen,
}: {
  loading: boolean;
  error: unknown;
  onRetry: () => void;
  items: FollowUpDraft[];
  onOpen: (id: string) => void;
}) {
  const [showClosed, setShowClosed] = useState(false);
  const { labels } = useApplicationLabels();
  const visible = useMemo(
    () => items.filter((d) => showClosed || (d.status !== 'DISCARDED' && d.status !== 'SENT')),
    [items, showClosed],
  );

  if (loading) return <SkeletonRows rows={4} />;
  if (error) return <ErrorState error={error} onRetry={onRetry} />;
  if (items.length === 0) {
    return (
      <EmptyState
        icon={<Send className="h-5 w-5" />}
        title="No drafts yet"
        description="Create a draft from a recommendation, or use “New draft” to write one for any application."
      />
    );
  }
  return (
    <div className="space-y-3">
      <Checkbox label="Show sent and discarded drafts" checked={showClosed} onChange={setShowClosed} />
      {visible.length === 0 ? (
        <EmptyState compact title="No open drafts" description="All your drafts have been sent or discarded." />
      ) : (
        <ul className="space-y-2">
          {visible.map((d) => (
            <li key={d.id}>
              <button
                type="button"
                onClick={() => onOpen(d.id)}
                className="w-full rounded-xl border border-border bg-surface p-4 text-left transition-colors hover:bg-surface-2"
              >
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone={draftStatusTone(d.status)}>{humanize(d.status)}</Badge>
                  <Badge>{humanize(d.draftType)}</Badge>
                  {d.status === 'DRAFT' && <span className="text-xs text-warning">Draft — review before sending</span>}
                  <span className="ml-auto text-xs text-fg-subtle">Updated {formatRelative(d.updatedAt)}</span>
                </div>
                <p className="mt-2 truncate text-sm font-medium text-fg">{d.subject || 'Untitled draft'}</p>
                <p className="mt-0.5 truncate text-xs text-fg-muted">
                  {labels.get(d.applicationId) ?? 'Application'}
                  {d.recipient ? ` · To ${d.recipient}` : ' · No recipient yet'}
                </p>
                {d.status === 'FAILED' && d.sendError && <p className="mt-1 text-xs text-danger">Send failed: {d.sendError}</p>}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
