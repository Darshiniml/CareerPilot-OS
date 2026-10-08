import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { jobsApi } from '../../api/endpoints';
import type { JobAnalysisView } from '../../api/types';
import { Button } from '../../components/ui/Button';
import { Input, Textarea } from '../../components/ui/Field';
import { AiProgress, ErrorState } from '../../components/ui/Feedback';
import { Modal } from '../../components/ui/Modal';
import { Tabs } from '../../components/ui/Tabs';
import { AnalysisView } from './AnalysisView';

type Mode = 'text' | 'url';

const wordCount = (s: string) => s.trim().split(/\s+/).filter(Boolean).length;

/** Analyse a job that was not discovered by a connector: paste the text or give a URL. */
export function ExternalJobModal({ open, onClose, initialMode = 'text' }: { open: boolean; onClose: () => void; initialMode?: Mode }) {
  const [mode, setMode] = useState<Mode>(initialMode);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [url, setUrl] = useState('');
  const [touched, setTouched] = useState(false);

  const run = useMutation<JobAnalysisView, unknown, void>({
    mutationFn: () => (mode === 'text' ? jobsApi.processText(content.trim(), title.trim() || undefined) : jobsApi.processUrl(url.trim())),
  });

  const words = wordCount(content);
  const textError = touched && mode === 'text' && words < 15 ? `Paste the full posting (at least 15 words; currently ${words}).` : null;
  let urlError: string | null = null;
  if (touched && mode === 'url') {
    try {
      const u = new URL(url.trim());
      if (u.protocol !== 'http:' && u.protocol !== 'https:') urlError = 'Use an http(s) URL.';
    } catch {
      urlError = 'Enter a full URL, e.g. https://company.com/careers/123';
    }
  }

  const submit = () => {
    setTouched(true);
    if (mode === 'text' && words < 15) return;
    if (mode === 'url') {
      try {
        const u = new URL(url.trim());
        if (u.protocol !== 'http:' && u.protocol !== 'https:') return;
      } catch {
        return;
      }
    }
    run.mutate();
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      dismissible={!run.isPending}
      size="xl"
      title="Analyse an external job"
      description="The AI parses the posting into requirements and insights. Nothing is applied for automatically."
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={run.isPending}>
            Close
          </Button>
          <Button onClick={submit} loading={run.isPending} disabled={run.isPending}>
            Analyse
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Tabs<Mode>
          ariaLabel="Input type"
          value={mode}
          onChange={(m) => {
            setMode(m);
            setTouched(false);
          }}
          items={[
            { id: 'text', label: 'Paste text' },
            { id: 'url', label: 'From URL' },
          ]}
        />
        {mode === 'text' ? (
          <>
            <Input label="Title (optional)" value={title} onChange={(e) => setTitle(e.target.value)} />
            <Textarea
              label="Job description"
              required
              rows={10}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              error={textError}
              hint={`${words} words`}
            />
          </>
        ) : (
          <Input
            label="Job posting URL"
            type="url"
            required
            placeholder="https://"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            error={urlError}
          />
        )}
        {run.isPending && <AiProgress label="Analysing the posting…" />}
        {run.isError && <ErrorState error={run.error} />}
        {run.data && !run.isPending && <AnalysisView analysis={run.data} />}
      </div>
    </Modal>
  );
}
