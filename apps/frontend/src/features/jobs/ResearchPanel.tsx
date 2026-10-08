import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Building2, Plus, X } from 'lucide-react';
import { jobsApi } from '../../api/endpoints';
import { AiLabel } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { BulletList, Chips } from '../../components/ui/Data';
import { Input } from '../../components/ui/Field';
import { AiProgress, ErrorState, Notice } from '../../components/ui/Feedback';

function validUrl(s: string) {
  try {
    const u = new URL(s);
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch {
    return false;
  }
}

export function ResearchPanel({ jobId }: { jobId: string }) {
  const [urls, setUrls] = useState<string[]>([]);
  const research = useMutation({ mutationFn: (list: string[]) => jobsApi.research(jobId, list) });
  const cleaned = urls.map((u) => u.trim()).filter(Boolean);
  const invalid = cleaned.filter((u) => !validUrl(u));
  const r = research.data;

  return (
    <div className="space-y-4">
      <p className="text-sm text-fg-muted">
        Research uses the job posting and, optionally, up to 3 public pages you provide (e.g. the company’s about or engineering blog).
      </p>
      <div className="space-y-2">
        {urls.map((u, i) => (
          <div key={i} className="flex items-end gap-2">
            <Input
              containerClassName="flex-1"
              label={`Company URL ${i + 1}`}
              type="url"
              placeholder="https://"
              value={u}
              error={u.trim() && !validUrl(u.trim()) ? 'Enter a full http(s) URL' : null}
              onChange={(e) => setUrls(urls.map((x, j) => (j === i ? e.target.value : x)))}
            />
            <Button variant="ghost" size="icon" aria-label={`Remove URL ${i + 1}`} onClick={() => setUrls(urls.filter((_, j) => j !== i))}>
              <X className="h-4 w-4" />
            </Button>
          </div>
        ))}
        <div className="flex flex-wrap gap-2">
          {urls.length < 3 && (
            <Button variant="ghost" size="sm" icon={<Plus className="h-3.5 w-3.5" />} onClick={() => setUrls([...urls, ''])}>
              Add URL
            </Button>
          )}
          <Button
            icon={<Building2 className="h-4 w-4" />}
            disabled={research.isPending || invalid.length > 0}
            loading={research.isPending}
            onClick={() => research.mutate(cleaned)}
          >
            Research company
          </Button>
        </div>
      </div>
      {research.isPending && <AiProgress label="Researching the company…" />}
      {research.isError && <ErrorState error={research.error} />}
      {r && (
        <div className="space-y-4 rounded-xl border border-border bg-surface-2 p-4">
          <div className="flex items-center gap-2">
            <AiLabel />
            {r.company && <span className="text-sm font-semibold text-fg">{r.company}</span>}
          </div>
          <p className="whitespace-pre-line text-sm text-fg">{r.summary}</p>
          {r.whatTheyDo && (
            <div>
              <p className="mb-1 text-xs font-medium text-fg-muted">What they do</p>
              <p className="text-sm text-fg">{r.whatTheyDo}</p>
            </div>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <p className="mb-1 text-xs font-medium text-fg-muted">Tech stack</p>
              <Chips items={r.techStack} empty="Not found in sources" />
            </div>
            <div>
              <p className="mb-1 text-xs font-medium text-fg-muted">Culture signals</p>
              <BulletList items={r.cultureSignals} empty="Not found in sources" />
            </div>
            <div>
              <p className="mb-1 text-xs font-medium text-fg-muted">Talking points</p>
              <BulletList items={r.talkingPoints} />
            </div>
            <div>
              <p className="mb-1 text-xs font-medium text-fg-muted">Questions to ask</p>
              <BulletList items={r.questionsToAsk} />
            </div>
          </div>
          {r.unknowns.length > 0 && (
            <Notice tone="neutral" title="Unknown from the sources">
              <BulletList items={r.unknowns} />
            </Notice>
          )}
          <div>
            <p className="mb-1 text-xs font-medium text-fg-muted">Sources</p>
            <BulletList
              items={r.sources.map((s) =>
                /^https?:\/\//.test(s) ? (
                  <a href={s} target="_blank" rel="noopener noreferrer" className="break-all text-primary underline">
                    {s}
                  </a>
                ) : (
                  s
                ),
              )}
              empty="No sources reported"
            />
          </div>
          {r.fetchErrors.length > 0 && (
            <Notice tone="warning" title="Some pages could not be fetched">
              <BulletList items={r.fetchErrors.map((f) => `${f.url}: ${f.error}`)} />
            </Notice>
          )}
        </div>
      )}
    </div>
  );
}
