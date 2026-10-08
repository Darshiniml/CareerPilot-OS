import { useRef, useState, type DragEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { FileUp, UploadCloud, X } from 'lucide-react';
import { resumesApi } from '../../api/endpoints';
import { qk } from '../../api/queries';
import { Button, Card, CardHeader, Input, ProgressBar } from '../../components/ui';
import { toast } from '../../store/toast';
import { cn, formatBytes } from '../../lib/format';

const MAX_BYTES = 10 * 1024 * 1024;
const ALLOWED_EXT = ['pdf', 'docx', 'txt'];
const ALLOWED_MIME = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/plain',
];

export function validateResumeFile(f: File): string | null {
  const ext = f.name.split('.').pop()?.toLowerCase() ?? '';
  if (!ALLOWED_EXT.includes(ext) && !ALLOWED_MIME.includes(f.type)) {
    return 'Only PDF, DOCX or TXT files are supported.';
  }
  if (f.size === 0) return 'The file is empty.';
  if (f.size > MAX_BYTES) return `The file is ${formatBytes(f.size)}; the maximum is 10 MB.`;
  return null;
}

const titleFromName = (name: string) => name.replace(/\.[^.]+$/, '').replace(/[_-]+/g, ' ').trim();

export function UploadCard() {
  const qc = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState('');
  const [fileError, setFileError] = useState<string | null>(null);
  const [titleError, setTitleError] = useState<string | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [progress, setProgress] = useState(0);

  const upload = useMutation({
    mutationFn: ({ f, t }: { f: File; t: string }) => resumesApi.upload(f, t, setProgress),
    onSuccess: () => {
      toast.success('Resume uploaded', 'AI processing has started. The status updates automatically.');
      setFile(null);
      setTitle('');
      setProgress(0);
      if (inputRef.current) inputRef.current.value = '';
      qc.invalidateQueries({ queryKey: qk.resumes });
    },
    onError: (e) => {
      setProgress(0);
      toast.apiError(e);
    },
  });

  const pick = (f: File | undefined | null) => {
    if (!f) return;
    const err = validateResumeFile(f);
    setFileError(err);
    if (err) {
      setFile(null);
      return;
    }
    setFile(f);
    if (!title.trim()) setTitle(titleFromName(f.name));
  };

  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setDragOver(false);
    pick(e.dataTransfer.files?.[0]);
  };

  const submit = () => {
    if (!file) {
      setFileError('Choose a file first.');
      return;
    }
    if (!title.trim()) {
      setTitleError('Title is required');
      return;
    }
    setTitleError(null);
    upload.mutate({ f: file, t: title.trim() });
  };

  return (
    <Card>
      <CardHeader
        title="Upload a resume"
        description="PDF, DOCX or TXT, up to 10 MB. Parsing runs on the AI model and can take a few minutes."
        icon={<UploadCloud className="h-4 w-4" />}
      />
      <div className="space-y-4 px-5 py-4">
        <div
          role="button"
          tabIndex={0}
          aria-describedby="resume-drop-help"
          onClick={() => inputRef.current?.click()}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              inputRef.current?.click();
            }
          }}
          onDragOver={(e) => {
            e.preventDefault();
            setDragOver(true);
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={onDrop}
          className={cn(
            'flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed px-4 py-8 text-center transition-colors',
            dragOver ? 'border-primary bg-primary-soft' : 'border-border hover:bg-surface-2',
            fileError && 'border-danger',
          )}
        >
          <FileUp className="mb-2 h-6 w-6 text-fg-subtle" aria-hidden />
          <p className="text-sm font-medium text-fg">Drag & drop your resume here, or click to browse</p>
          <p id="resume-drop-help" className="mt-1 text-xs text-fg-subtle">
            PDF, DOCX or TXT · max 10 MB
          </p>
          <input
            ref={inputRef}
            type="file"
            className="sr-only"
            accept=".pdf,.docx,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
            onChange={(e) => pick(e.target.files?.[0])}
            aria-label="Resume file"
            tabIndex={-1}
          />
        </div>
        {fileError && (
          <p className="text-xs text-danger" role="alert">
            {fileError}
          </p>
        )}
        {file && (
          <div className="flex items-center justify-between gap-2 rounded-lg border border-border bg-surface-2 px-3 py-2 text-sm">
            <span className="min-w-0 truncate text-fg">
              {file.name} <span className="text-fg-subtle">({formatBytes(file.size)})</span>
            </span>
            <button
              type="button"
              disabled={upload.isPending}
              onClick={() => {
                setFile(null);
                if (inputRef.current) inputRef.current.value = '';
              }}
              className="rounded p-1 text-fg-subtle hover:text-fg disabled:opacity-40"
              aria-label="Remove selected file"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )}
        <Input
          label="Title"
          required
          value={title}
          error={titleError}
          maxLength={200}
          onChange={(e) => {
            setTitle(e.target.value);
            if (titleError) setTitleError(null);
          }}
          placeholder="e.g. Backend resume 2026"
        />
        {upload.isPending && (
          <div className="space-y-1">
            <ProgressBar value={progress} label="Upload progress" />
            <p className="text-xs text-fg-muted">{progress < 100 ? `Uploading… ${progress}%` : 'Upload complete, saving…'}</p>
          </div>
        )}
        <div className="flex justify-end">
          <Button onClick={submit} loading={upload.isPending} disabled={!file} icon={<UploadCloud className="h-4 w-4" />}>
            Upload
          </Button>
        </div>
      </div>
    </Card>
  );
}
