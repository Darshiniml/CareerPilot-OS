import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

export type ThemePreference = 'light' | 'dark' | 'system';

interface ThemeState {
  preference: ThemePreference;
  setPreference: (p: ThemePreference) => void;
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      preference: 'system',
      setPreference: (preference) => set({ preference }),
    }),
    { name: 'careerpilot.theme', storage: createJSONStorage(() => localStorage) },
  ),
);

const media = () =>
  typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

export function resolveTheme(p: ThemePreference): 'light' | 'dark' {
  if (p === 'system') return media()?.matches ? 'dark' : 'light';
  return p;
}

export function applyTheme(p: ThemePreference) {
  document.documentElement.setAttribute('data-theme', resolveTheme(p));
}

/** Applies the theme now, on preference changes, and when the OS scheme changes. */
export function initTheme() {
  applyTheme(useThemeStore.getState().preference);
  useThemeStore.subscribe((s) => applyTheme(s.preference));
  media()?.addEventListener('change', () => {
    if (useThemeStore.getState().preference === 'system') applyTheme('system');
  });
}
