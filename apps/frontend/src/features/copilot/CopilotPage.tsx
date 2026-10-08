import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bot, Info, SendHorizonal, Trash2, User, Wrench } from 'lucide-react';
import { copilotApi } from '../../api/endpoints';
import { qk } from '../../api/queries';
import type { CopilotChatResponse, SuggestedAction } from '../../api/types';
import {
  AiLabel,
  AiProgress,
  Button,
  Card,
  CardBody,
  CardHeader,
  EmptyState,
  ErrorState,
  Modal,
  PageHeader,
  SkeletonRows,
} from '../../components/ui';
import { VerificationNotice } from '../../components/shared';
import { toast } from '../../store/toast';
import { cn, formatDateTime } from '../../lib/format';

const SUGGESTED_PROMPTS = [
  'Which of my applications need attention this week?',
  'What skills am I missing most often in the jobs I look at?',
  'Which jobs match my resume best right now?',
  'How ready am I for interviews?',
];

/** Copilot actions only navigate — the Copilot never performs writes. */
export function actionPath(a: SuggestedAction): string | null {
  const id = a.targetId ? encodeURIComponent(a.targetId) : null;
  switch (a.action) {
    case 'OPEN_JOB':
    case 'ANALYZE_JOB':
      return id ? `/jobs?jobId=${id}` : '/jobs';
    case 'OPEN_APPLICATION':
      return id ? `/applications?id=${id}` : '/applications';
    case 'START_INTERVIEW_PRACTICE':
      return '/interviews';
    case 'DRAFT_FOLLOW_UP':
      return '/follow-ups';
    case 'OPTIMIZE_RESUME':
    case 'UPLOAD_RESUME':
      return '/resumes';
    case 'GENERATE_COVER_LETTER':
      return id ? `/cover-letters?jobId=${id}` : '/cover-letters';
    case 'VIEW_LEARNING_PLAN':
      return '/learning';
    default:
      return null;
  }
}

interface Extras {
  dataGaps: string[];
  toolSelection: CopilotChatResponse['toolSelection'];
  verification: CopilotChatResponse['verification'];
}

function ActionButtons({ actions }: { actions: SuggestedAction[] }) {
  const navigate = useNavigate();
  const usable = actions.map((a) => ({ a, path: actionPath(a) })).filter((x) => x.path);
  if (!usable.length) return null;
  return (
    <div className="mt-3 flex flex-wrap gap-2">
      {usable.map(({ a, path }, i) => (
        <Button key={`${a.action}-${i}`} size="sm" variant="secondary" onClick={() => navigate(path!)}>
          {a.label || a.action}
        </Button>
      ))}
    </div>
  );
}

function AssistantMeta({ citations, extras }: { citations: string[]; extras?: Extras }) {
  return (
    <div className="mt-3 space-y-2 border-t border-border pt-2 text-xs text-fg-muted">
      {citations.length > 0 && (
        <p>
          <span className="font-medium text-fg">Sources:</span>{' '}
          {citations.map((c) => (
            <code key={c} className="mr-1 rounded bg-surface-3 px-1 py-0.5 text-[11px] text-fg">
              {c}
            </code>
          ))}
        </p>
      )}
      {citations.length === 0 && <p>No data tools were used for this answer.</p>}
      {extras && extras.dataGaps.length > 0 && (
        <div>
          <p className="font-medium text-fg">Missing data</p>
          <ul className="ml-4 list-disc">
            {extras.dataGaps.map((g) => (
              <li key={g}>{g}</li>
            ))}
          </ul>
        </div>
      )}
      {extras?.toolSelection === 'FALLBACK' && (
        <p className="italic text-fg-subtle">Tool selection: fallback (the model did not choose tools; a rule-based selection was used).</p>
      )}
    </div>
  );
}

function ToolsPanel() {
  const q = useQuery({ queryKey: qk.copilotTools, queryFn: copilotApi.tools, staleTime: 10 * 60_000 });
  return (
    <Card>
      <CardHeader icon={<Wrench className="h-4 w-4" />} title="What the Copilot can access" description="Read-only tools over your own data." />
      <CardBody>
        {q.isLoading && <SkeletonRows rows={3} />}
        {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} compact />}
        {q.data && q.data.length === 0 && <p className="text-sm text-fg-muted">No tools reported.</p>}
        <ul className="space-y-3">
          {(q.data ?? []).map((t) => (
            <li key={t.name}>
              <code className="text-xs font-semibold text-fg">{t.name}</code>
              <p className="text-xs text-fg-muted">{t.description}</p>
              {t.arguments?.length > 0 && <p className="text-[11px] text-fg-subtle">Arguments: {t.arguments.join(', ')}</p>}
            </li>
          ))}
        </ul>
        <p className="mt-4 flex gap-2 text-xs text-fg-subtle">
          <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          The Copilot never changes anything. Suggested actions only open the relevant page.
        </p>
      </CardBody>
    </Card>
  );
}

export function CopilotPage() {
  const qc = useQueryClient();
  const history = useQuery({ queryKey: qk.copilotHistory, queryFn: copilotApi.history });
  const [input, setInput] = useState('');
  const [pending, setPending] = useState<string | null>(null);
  const [extras, setExtras] = useState<Record<string, Extras>>({});
  const [confirmClear, setConfirmClear] = useState(false);
  const endRef = useRef<HTMLDivElement>(null);

  const chat = useMutation({
    mutationFn: (message: string) => copilotApi.chat(message),
    onMutate: (message) => setPending(message),
    onSuccess: async (res) => {
      setExtras((e) => ({ ...e, [res.answer]: { dataGaps: res.dataGaps ?? [], toolSelection: res.toolSelection, verification: res.verification } }));
      await qc.invalidateQueries({ queryKey: qk.copilotHistory });
      setPending(null);
      setInput('');
    },
    onError: () => setPending(null),
  });

  const clear = useMutation({
    mutationFn: copilotApi.clear,
    onSuccess: () => {
      setConfirmClear(false);
      setExtras({});
      qc.invalidateQueries({ queryKey: qk.copilotHistory });
      toast.success('Conversation cleared');
    },
    onError: toast.apiError,
  });

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' });
  }, [history.data, pending]);

  const send = (text: string) => {
    const msg = text.trim();
    if (!msg || chat.isPending) return;
    chat.mutate(msg);
  };
  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    send(input);
  };

  const items = history.data ?? [];

  return (
    <>
      <PageHeader
        title="Copilot"
        description="Ask about your applications, jobs, resume and progress. Answers are AI-generated from your data."
        actions={
          <Button variant="secondary" icon={<Trash2 className="h-4 w-4" />} onClick={() => setConfirmClear(true)} disabled={items.length === 0}>
            Clear conversation
          </Button>
        }
      />
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <Card className="flex min-h-[60vh] flex-col">
          <div className="flex-1 space-y-4 overflow-y-auto p-4 sm:p-5" aria-live="polite">
            {history.isLoading && <SkeletonRows rows={4} />}
            {history.isError && <ErrorState error={history.error} onRetry={() => history.refetch()} />}
            {history.data && items.length === 0 && !pending && (
              <EmptyState
                icon={<Bot className="h-5 w-5" />}
                title="Start a conversation"
                description="Try one of these questions:"
                action={SUGGESTED_PROMPTS.map((p) => (
                  <Button key={p} size="sm" variant="secondary" onClick={() => send(p)}>
                    {p}
                  </Button>
                ))}
              />
            )}
            {items.map((m) => (
              <div key={m.id} className={cn('flex gap-3', m.role === 'USER' && 'flex-row-reverse')}>
                <div
                  className={cn(
                    'flex h-8 w-8 shrink-0 items-center justify-center rounded-full',
                    m.role === 'USER' ? 'bg-surface-3 text-fg' : 'bg-primary-soft text-primary-soft-fg',
                  )}
                  aria-hidden
                >
                  {m.role === 'USER' ? <User className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
                </div>
                <div
                  className={cn(
                    'min-w-0 max-w-[85%] rounded-xl border px-4 py-3',
                    m.role === 'USER' ? 'border-primary/20 bg-primary-soft' : 'border-border bg-surface-2',
                  )}
                >
                  {m.role === 'ASSISTANT' && (
                    <div className="mb-1.5">
                      <AiLabel />
                    </div>
                  )}
                  <p className="whitespace-pre-wrap break-words text-sm text-fg">{m.content}</p>
                  {m.role === 'ASSISTANT' && (
                    <>
                      <ActionButtons actions={m.suggestedActions ?? []} />
                      <AssistantMeta citations={m.citations ?? []} extras={extras[m.content]} />
                      {extras[m.content]?.verification &&
                        ((extras[m.content]!.verification!.unsupportedNumbers?.length ?? 0) > 0 ||
                          (extras[m.content]!.verification!.unsupportedTechnologies?.length ?? 0) > 0) && (
                          <div className="mt-2">
                            <VerificationNotice verification={extras[m.content]!.verification} />
                          </div>
                        )}
                    </>
                  )}
                  <p className="mt-1.5 text-[11px] text-fg-subtle">{formatDateTime(m.createdAt)}</p>
                </div>
              </div>
            ))}
            {pending && (
              <>
                <div className="flex flex-row-reverse gap-3">
                  <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-surface-3 text-fg" aria-hidden>
                    <User className="h-4 w-4" />
                  </div>
                  <div className="min-w-0 max-w-[85%] rounded-xl border border-primary/20 bg-primary-soft px-4 py-3">
                    <p className="whitespace-pre-wrap break-words text-sm text-fg">{pending}</p>
                  </div>
                </div>
                <AiProgress label="The Copilot is reading your data and writing an answer" />
              </>
            )}
            {chat.isError && <ErrorState error={chat.error} />}
            <div ref={endRef} />
          </div>
          <form onSubmit={onSubmit} className="flex items-end gap-2 border-t border-border p-3">
            <label htmlFor="copilot-input" className="sr-only">
              Message
            </label>
            <textarea
              id="copilot-input"
              rows={2}
              maxLength={4000}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  send(input);
                }
              }}
              placeholder="Ask about your job search… (Enter to send, Shift+Enter for a new line)"
              className="min-h-[44px] flex-1 resize-y rounded-lg border border-border bg-surface px-3 py-2 text-sm text-fg placeholder:text-fg-subtle focus-visible:outline-2 focus-visible:outline-ring"
              disabled={chat.isPending}
            />
            <Button type="submit" loading={chat.isPending} disabled={!input.trim()} icon={<SendHorizonal className="h-4 w-4" />}>
              <span className="hidden sm:inline">Send</span>
            </Button>
          </form>
        </Card>
        <ToolsPanel />
      </div>
      <Modal
        open={confirmClear}
        onClose={() => setConfirmClear(false)}
        title="Clear conversation?"
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmClear(false)}>
              Cancel
            </Button>
            <Button variant="danger" loading={clear.isPending} onClick={() => clear.mutate()}>
              Clear
            </Button>
          </>
        }
      >
        <p className="text-sm text-fg-muted">All Copilot messages will be deleted permanently.</p>
      </Modal>
    </>
  );
}
