import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { followUpsApi } from '../../api/endpoints';
import { qk } from '../../api/queries';
import { DRAFT_TYPES, type DraftType, type FollowUpDraft } from '../../api/types';
import { AiProgress, Button, ErrorState, Input, Modal, Notice, Select, Spinner, Textarea } from '../../components/ui';
import { humanize } from '../../lib/format';
import { toast } from '../../store/toast';
import { todayIso, useApplicationLabels } from './helpers';

export interface DraftSeed {
  applicationId?: string;
  draftType?: DraftType | null;
  communicationId?: string | null;
  recommendationKey?: string | null;
  context?: string;
}

export function CreateDraftModal({
  open,
  seed,
  onClose,
  onCreated,
}: {
  open: boolean;
  seed: DraftSeed | null;
  onClose: () => void;
  onCreated: (draft: FollowUpDraft) => void;
}) {
  const qc = useQueryClient();
  const { apps, options } = useApplicationLabels();
  const [applicationId, setApplicationId] = useState('');
  const [draftType, setDraftType] = useState<DraftType>('APPLICATION_FOLLOW_UP');
  const [instructions, setInstructions] = useState('');
  const [interviewDate, setInterviewDate] = useState('');
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!open) return;
    setApplicationId(seed?.applicationId ?? '');
    setDraftType(seed?.draftType ?? 'APPLICATION_FOLLOW_UP');
    setInstructions('');
    setInterviewDate('');
    setTouched(false);
  }, [open, seed]);

  const create = useMutation({
    mutationFn: followUpsApi.createDraft,
    onSuccess: (draft) => {
      qc.invalidateQueries({ queryKey: qk.drafts });
      toast.success('Draft created', 'Review and edit it before approving.');
      onCreated(draft);
    },
  });

  useEffect(() => {
    if (open) create.reset();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const needsDate = draftType === 'INTERVIEW_THANK_YOU';
  const today = todayIso();
  const dateError = !needsDate
    ? null
    : !interviewDate
      ? 'Required for a thank-you note: when did the interview take place?'
      : interviewDate > today
        ? 'The interview date cannot be in the future.'
        : null;
  const appError = !applicationId ? 'Choose the application this email is about.' : null;
  const valid = !dateError && !appError;

  const submit = () => {
    setTouched(true);
    if (!valid || create.isPending) return;
    create.mutate({
      applicationId,
      draftType,
      communicationId: seed?.communicationId ?? null,
      recommendationKey: seed?.recommendationKey ?? null,
      userInstructions: instructions.trim() || null,
      interviewCompletedOn: needsDate ? interviewDate : null,
    });
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      dismissible={!create.isPending}
      title="Draft an email"
      description="The AI writes a draft from your real application data. Nothing is sent until you approve and send it."
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={create.isPending}>
            Cancel
          </Button>
          <Button onClick={submit} loading={create.isPending} disabled={create.isPending}>
            Generate draft
          </Button>
        </>
      }
    >
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        {seed?.context && <Notice tone="neutral">{seed.context}</Notice>}
        {seed?.applicationId ? null : apps.isLoading ? (
          <Spinner label="Loading applications…" />
        ) : apps.isError ? (
          <ErrorState error={apps.error} onRetry={() => apps.refetch()} compact />
        ) : options.length === 0 ? (
          <Notice tone="neutral" title="No applications yet">
            Follow-up emails are tied to an application. Track an application first from the Jobs or Opportunities page.
          </Notice>
        ) : (
          <Select
            label="Application"
            required
            value={applicationId}
            onChange={(e) => setApplicationId(e.target.value)}
            placeholder="Select an application…"
            options={options}
            error={touched ? appError : null}
          />
        )}
        <Select
          label="Email type"
          required
          value={draftType}
          onChange={(e) => setDraftType(e.target.value as DraftType)}
          options={DRAFT_TYPES.map((t) => ({ value: t, label: humanize(t) }))}
          hint={seed?.draftType ? `Suggested: ${humanize(seed.draftType)}` : undefined}
        />
        {needsDate && (
          <Input
            type="date"
            label="Interview date"
            required
            max={today}
            value={interviewDate}
            onChange={(e) => setInterviewDate(e.target.value)}
            error={touched || interviewDate ? dateError : null}
          />
        )}
        <Textarea
          label="Instructions for the AI (optional)"
          rows={3}
          maxLength={1000}
          value={instructions}
          onChange={(e) => setInstructions(e.target.value)}
          placeholder="e.g. Keep it short and mention I'm available next week."
          hint={`${instructions.length}/1000`}
        />
        {create.isPending && <AiProgress label="Writing your draft" />}
        {create.isError && <ErrorState error={create.error} />}
      </form>
    </Modal>
  );
}
