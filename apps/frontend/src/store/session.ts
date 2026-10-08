import { queryClient } from '../lib/queryClient';
import { useAuthStore } from './auth';
import { useToastStore } from './toast';
import type { User } from '../api/types';

/** Clears every piece of per-user state: tokens, cached server data, transient UI stores. */
export function clearUserState() {
  queryClient.cancelQueries();
  queryClient.clear();
  useToastStore.getState().clear();
  useAuthStore.getState().clear();
}

/** Called on successful login: makes sure nothing from a previous user survives. */
export function startSession(user: User, accessToken: string, refreshToken: string) {
  queryClient.clear();
  useAuthStore.getState().setSession(user, accessToken, refreshToken);
}
