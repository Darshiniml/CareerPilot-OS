import { useState, type ReactNode } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import { profileApi } from '../../api/endpoints';
import { qk } from '../../api/queries';
import type { ProfileSection } from '../../api/types';
import { Button, Card, CardHeader, Checkbox, EmptyState, Input, Modal, Textarea } from '../../components/ui';
import { toast } from '../../store/toast';
import { blankToNull, isValidUrl } from './validation';

export interface FieldDef {
  key: string;
  label: string;
  type: 'text' | 'date' | 'textarea' | 'url' | 'checkbox';
  required?: boolean;
  placeholder?: string;
  /** Hide this field when another boolean field is true (e.g. endDate when currentJob). */
  hiddenWhen?: string;
  full?: boolean;
}

type Values = Record<string, string | boolean>;

interface SectionListProps<T extends { id?: string | null }> {
  section: ProfileSection;
  title: string;
  description: string;
  icon: ReactNode;
  items: T[];
  fields: FieldDef[];
  /** Pairs of [startKey, endKey] that must be ordered. */
  dateRange?: [string, string];
  renderItem: (item: T) => ReactNode;
  itemLabel: (item: T) => string;
  emptyText: string;
}

function emptyValues(fields: FieldDef[]): Values {
  const v: Values = {};
  for (const f of fields) v[f.key] = f.type === 'checkbox' ? false : '';
  return v;
}

export function SectionList<T extends { id?: string | null }>({
  section,
  title,
  description,
  icon,
  items,
  fields,
  dateRange,
  renderItem,
  itemLabel,
  emptyText,
}: SectionListProps<T>) {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [values, setValues] = useState<Values>(() => emptyValues(fields));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [toDelete, setToDelete] = useState<T | null>(null);

  const add = useMutation({
    mutationFn: (body: Record<string, unknown>) => profileApi.addItem(section, body),
    onSuccess: () => {
      toast.success(`${title}: item added`);
      setOpen(false);
      setValues(emptyValues(fields));
      qc.invalidateQueries({ queryKey: qk.profile });
    },
    onError: toast.apiError,
  });

  const del = useMutation({
    mutationFn: (id: string) => profileApi.deleteItem(section, id),
    onSuccess: () => {
      toast.success('Item removed');
      setToDelete(null);
      qc.invalidateQueries({ queryKey: qk.profile });
    },
    onError: toast.apiError,
  });

  const validate = (): Record<string, string> => {
    const e: Record<string, string> = {};
    for (const f of fields) {
      const v = values[f.key];
      if (f.hiddenWhen && values[f.hiddenWhen] === true) continue;
      if (f.type === 'checkbox') continue;
      const s = String(v ?? '').trim();
      if (f.required && !s) e[f.key] = `${f.label} is required`;
      if (s && f.type === 'url' && !isValidUrl(s)) e[f.key] = 'Enter a full URL starting with http:// or https://';
    }
    if (dateRange) {
      const [a, b] = dateRange;
      const start = String(values[a] ?? '');
      const end = String(values[b] ?? '');
      const endHidden = fields.find((f) => f.key === b)?.hiddenWhen;
      if (start && end && !(endHidden && values[endHidden] === true) && end < start) {
        e[b] = 'End date must be after the start date';
      }
    }
    return e;
  };

  const submit = () => {
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length) return;
    const body: Record<string, unknown> = {};
    for (const f of fields) {
      const v = values[f.key];
      if (f.type === 'checkbox') body[f.key] = Boolean(v);
      else if (f.hiddenWhen && values[f.hiddenWhen] === true) body[f.key] = null;
      else body[f.key] = blankToNull(String(v ?? ''));
    }
    add.mutate(body);
  };

  const set = (k: string, v: string | boolean) => {
    setValues((s) => ({ ...s, [k]: v }));
    if (errors[k]) setErrors((s) => ({ ...s, [k]: '' }));
  };

  return (
    <Card>
      <CardHeader
        title={title}
        description={description}
        icon={icon}
        actions={
          <Button size="sm" variant="secondary" icon={<Plus className="h-3.5 w-3.5" />} onClick={() => setOpen(true)}>
            Add
          </Button>
        }
      />
      <div className="px-5 py-4">
        {items.length === 0 ? (
          <EmptyState compact title={`No ${title.toLowerCase()} yet`} description={emptyText} />
        ) : (
          <ul className="divide-y divide-border">
            {items.map((item, i) => (
              <li key={item.id ?? i} className="flex items-start justify-between gap-3 py-3 first:pt-0 last:pb-0">
                <div className="min-w-0 flex-1">{renderItem(item)}</div>
                {item.id && (
                  <Button
                    size="icon"
                    variant="ghost"
                    aria-label={`Delete ${itemLabel(item)}`}
                    onClick={() => setToDelete(item)}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>

      <Modal
        open={open}
        onClose={() => {
          setOpen(false);
          setErrors({});
        }}
        title={`Add ${title.toLowerCase()}`}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button onClick={submit} loading={add.isPending}>
              Save
            </Button>
          </>
        }
      >
        <form
          className="grid gap-4 sm:grid-cols-2"
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
          noValidate
        >
          {fields.map((f) => {
            if (f.hiddenWhen && values[f.hiddenWhen] === true) return null;
            const cls = f.full || f.type === 'textarea' ? 'sm:col-span-2' : undefined;
            if (f.type === 'checkbox') {
              return (
                <div key={f.key} className="sm:col-span-2">
                  <Checkbox label={f.label} checked={Boolean(values[f.key])} onChange={(v) => set(f.key, v)} />
                </div>
              );
            }
            if (f.type === 'textarea') {
              return (
                <Textarea
                  key={f.key}
                  containerClassName={cls}
                  label={f.label}
                  required={f.required}
                  placeholder={f.placeholder}
                  value={String(values[f.key] ?? '')}
                  error={errors[f.key] || null}
                  onChange={(e) => set(f.key, e.target.value)}
                />
              );
            }
            return (
              <Input
                key={f.key}
                containerClassName={cls}
                label={f.label}
                required={f.required}
                type={f.type === 'url' ? 'url' : f.type === 'date' ? 'date' : 'text'}
                placeholder={f.placeholder}
                value={String(values[f.key] ?? '')}
                error={errors[f.key] || null}
                onChange={(e) => set(f.key, e.target.value)}
              />
            );
          })}
          <button type="submit" className="hidden" />
        </form>
      </Modal>

      <Modal
        open={toDelete !== null}
        onClose={() => setToDelete(null)}
        title="Delete this item?"
        description={toDelete ? itemLabel(toDelete) : undefined}
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setToDelete(null)}>
              Cancel
            </Button>
            <Button variant="danger" loading={del.isPending} onClick={() => toDelete?.id && del.mutate(toDelete.id)}>
              Delete
            </Button>
          </>
        }
      >
        <p className="text-sm text-fg-muted">This permanently removes it from your profile.</p>
      </Modal>
    </Card>
  );
}
