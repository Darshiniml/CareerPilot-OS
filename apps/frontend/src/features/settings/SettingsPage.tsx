import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Mail, Monitor, Moon, Palette, Sun, Trash2, UserCircle } from 'lucide-react';
import { copilotApi, emailApi } from '../../api/endpoints';
import { qk, useEmailConnections } from '../../api/queries';
import type { EmailConnection, EmailProvider } from '../../api/types';
import {
  Badge,
  Button,
  Card,
  CardBody,
  CardHeader,
  ErrorState,
  KeyValue,
  Modal,
  PageHeader,
  SkeletonRows,
} from '../../components/ui';
import { useThemeStore, type ThemePreference } from '../../store/theme';
import { useAuthStore } from '../../store/auth';
import { toast } from '../../store/toast';
import { cn, formatDateTime, fullName, humanize } from '../../lib/format';

const THEMES: Array<{ id: ThemePreference; label: string; icon: typeof Sun }> = [
  { id: 'light', label: 'Light', icon: Sun },
  { id: 'dark', label: 'Dark', icon: Moon },
  { id: 'system', label: 'System', icon: Monitor },
];

function ThemeCard() {
  const pref = useThemeStore((s) => s.preference);
  const setPref = useThemeStore((s) => s.setPreference);
  return (
    <Card>
      <CardHeader icon={<Palette className="h-4 w-4" />} title="Appearance" description="Saved on this device. “System” follows your OS setting." />
      <CardBody>
        <div role="radiogroup" aria-label="Theme" className="grid grid-cols-3 gap-2 sm:max-w-md">
          {THEMES.map((t) => (
            <button
              key={t.id}
              type="button"
              role="radio"
              aria-checked={pref === t.id}
              onClick={() => setPref(t.id)}
              className={cn(
                'flex flex-col items-center gap-1.5 rounded-lg border px-3 py-3 text-sm font-medium',
                pref === t.id ? 'border-primary bg-primary-soft text-primary-soft-fg' : 'border-border text-fg-muted hover:bg-surface-2',
              )}
            >
              <t.icon className="h-4 w-4" aria-hidden />
              {t.label}
            </button>
          ))}
        </div>
      </CardBody>
    </Card>
  );
}

const PROVIDER_LABEL: Record<EmailProvider, string> = { GMAIL: 'Gmail', OUTLOOK: 'Outlook' };

function ConnectionRow({ c }: { c: EmailConnection }) {
  const qc = useQueryClient();
  const [confirm, setConfirm] = useState(false);
  const authorize = useMutation({
    mutationFn: () => emailApi.authorize(c.provider),
    onSuccess: (r) => {
      window.location.assign(r.authorizationUrl);
    },
    onError: toast.apiError,
  });
  const disconnect = useMutation({
    mutationFn: () => emailApi.disconnect(c.provider),
    onSuccess: () => {
      setConfirm(false);
      toast.success(`${PROVIDER_LABEL[c.provider] ?? c.provider} disconnected`);
      qc.invalidateQueries({ queryKey: qk.emailConnections });
    },
    onError: toast.apiError,
  });
  const label = PROVIDER_LABEL[c.provider] ?? humanize(c.provider);
  return (
    <li className="flex flex-col gap-3 py-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-sm font-medium text-fg">{label}</p>
          {!c.providerConfigured ? (
            <Badge>Not configured by the administrator</Badge>
          ) : c.connected ? (
            <Badge tone="success">Connected</Badge>
          ) : (
            <Badge tone="neutral">Not connected</Badge>
          )}
        </div>
        {c.connected && (
          <p className="mt-0.5 text-xs text-fg-muted">
            {c.emailAddress ?? 'Address not reported'}
            {c.connectedAt && <> · since {formatDateTime(c.connectedAt)}</>}
          </p>
        )}
        {!c.providerConfigured && (
          <p className="mt-0.5 text-xs text-fg-subtle">
            The server has no OAuth credentials for {label}, so it cannot be connected.
          </p>
        )}
      </div>
      {c.providerConfigured &&
        (c.connected ? (
          <Button variant="danger" size="sm" onClick={() => setConfirm(true)}>
            Disconnect
          </Button>
        ) : (
          <Button size="sm" loading={authorize.isPending} onClick={() => authorize.mutate()}>
            Connect {label}
          </Button>
        ))}
      <Modal
        open={confirm}
        onClose={() => setConfirm(false)}
        title={`Disconnect ${label}?`}
        description="Approved follow-up drafts cannot be sent until a mailbox is connected again."
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirm(false)}>
              Cancel
            </Button>
            <Button variant="danger" loading={disconnect.isPending} onClick={() => disconnect.mutate()}>
              Disconnect
            </Button>
          </>
        }
      >
        <p className="text-sm text-fg-muted">CareerPilot will delete its stored access token for this mailbox.</p>
      </Modal>
    </li>
  );
}

function EmailCard() {
  const q = useEmailConnections();
  return (
    <Card>
      <CardHeader
        icon={<Mail className="h-4 w-4" />}
        title="Email connections"
        description="Used only to send follow-up emails you have explicitly approved."
      />
      <CardBody>
        {q.isLoading && <SkeletonRows rows={2} />}
        {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
        {q.data && q.data.length === 0 && <p className="text-sm text-fg-muted">The server reported no email providers.</p>}
        <ul className="divide-y divide-border">
          {(q.data ?? []).map((c) => (
            <ConnectionRow key={c.provider} c={c} />
          ))}
        </ul>
      </CardBody>
    </Card>
  );
}

function CopilotCard() {
  const qc = useQueryClient();
  const [confirm, setConfirm] = useState(false);
  const clear = useMutation({
    mutationFn: copilotApi.clear,
    onSuccess: (r) => {
      setConfirm(false);
      toast.success('Copilot history cleared', `${r.deletedMessages} message${r.deletedMessages === 1 ? '' : 's'} deleted.`);
      qc.invalidateQueries({ queryKey: qk.copilotHistory });
    },
    onError: toast.apiError,
  });
  return (
    <Card>
      <CardHeader icon={<Trash2 className="h-4 w-4" />} title="Copilot conversation" description="Delete the stored Copilot chat history for your account." />
      <CardBody>
        <Button variant="danger" onClick={() => setConfirm(true)}>
          Clear Copilot history
        </Button>
        <Modal
          open={confirm}
          onClose={() => setConfirm(false)}
          title="Clear Copilot history?"
          size="sm"
          footer={
            <>
              <Button variant="secondary" onClick={() => setConfirm(false)}>
                Cancel
              </Button>
              <Button variant="danger" loading={clear.isPending} onClick={() => clear.mutate()}>
                Clear history
              </Button>
            </>
          }
        >
          <p className="text-sm text-fg-muted">This permanently deletes all Copilot messages. It cannot be undone.</p>
        </Modal>
      </CardBody>
    </Card>
  );
}

function AccountCard() {
  const user = useAuthStore((s) => s.user);
  return (
    <Card>
      <CardHeader icon={<UserCircle className="h-4 w-4" />} title="Account" description="Edit your name and details on the Profile page." />
      <CardBody>
        {user ? (
          <KeyValue
            items={[
              { label: 'Name', value: fullName(user.firstName, user.lastName) || 'Not set' },
              { label: 'Email', value: user.email },
              { label: 'Roles', value: user.roles?.length ? user.roles.map(humanize).join(', ') : 'None' },
              { label: 'Member since', value: user.createdAt ? formatDateTime(user.createdAt) : 'Not available' },
            ]}
          />
        ) : (
          <p className="text-sm text-fg-muted">Account details are not available.</p>
        )}
      </CardBody>
    </Card>
  );
}

/** Handles `?email=connected|failed|denied&provider=…` after the OAuth redirect. */
function useOAuthResultToast() {
  const [params, setParams] = useSearchParams();
  const qc = useQueryClient();
  const handled = useRef(false);
  useEffect(() => {
    const result = params.get('email');
    if (!result || handled.current) return;
    handled.current = true;
    const provider = params.get('provider');
    const name = provider ? PROVIDER_LABEL[provider.toUpperCase() as EmailProvider] ?? provider : 'Mailbox';
    if (result === 'connected') toast.success(`${name} connected`, 'You can now send approved follow-up emails.');
    else if (result === 'denied') toast.warning(`${name} was not connected`, 'You declined the permission request.');
    else toast.error(`${name} connection failed`, 'The provider did not complete the connection. Please try again.');
    qc.invalidateQueries({ queryKey: qk.emailConnections });
    const next = new URLSearchParams(params);
    next.delete('email');
    next.delete('provider');
    setParams(next, { replace: true });
  }, [params, setParams, qc]);
}

export function SettingsPage() {
  useOAuthResultToast();
  return (
    <>
      <PageHeader title="Settings" description="Appearance, mailbox connections and account information." />
      <div className="grid gap-6 lg:grid-cols-2">
        <ThemeCard />
        <AccountCard />
        <div className="lg:col-span-2">
          <EmailCard />
        </div>
        <CopilotCard />
      </div>
    </>
  );
}
