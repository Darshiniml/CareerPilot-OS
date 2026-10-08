import { create } from 'zustand';
import { errorToast } from '../api/errors';

export type ToastTone = 'success' | 'error' | 'info' | 'warning';

export interface Toast {
  id: number;
  tone: ToastTone;
  title: string;
  description?: string;
}

interface ToastState {
  toasts: Toast[];
  push: (t: Omit<Toast, 'id'>, durationMs?: number) => void;
  dismiss: (id: number) => void;
  clear: () => void;
}

let nextId = 1;

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],
  push: (t, durationMs) => {
    const id = nextId++;
    set({ toasts: [...get().toasts, { ...t, id }].slice(-5) });
    const ms = durationMs ?? (t.tone === 'error' ? 9000 : 5000);
    window.setTimeout(() => get().dismiss(id), ms);
  },
  dismiss: (id) => set({ toasts: get().toasts.filter((x) => x.id !== id) }),
  clear: () => set({ toasts: [] }),
}));

export const toast = {
  success: (title: string, description?: string) => useToastStore.getState().push({ tone: 'success', title, description }),
  info: (title: string, description?: string) => useToastStore.getState().push({ tone: 'info', title, description }),
  warning: (title: string, description?: string) => useToastStore.getState().push({ tone: 'warning', title, description }),
  error: (title: string, description?: string) => useToastStore.getState().push({ tone: 'error', title, description }),
  /** Shows an API error honestly (friendly title + server message). */
  apiError: (e: unknown) => useToastStore.getState().push({ tone: 'error', ...errorToast(e) }),
};
