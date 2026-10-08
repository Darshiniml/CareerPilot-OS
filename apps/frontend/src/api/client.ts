import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '../store/auth';
import { clearUserState } from '../store/session';
import { ApiError, toApiError, type ApiErrorBody } from './errors';
import type { LoginResponse } from './types';

export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || '/api/v1';

/** Default timeout for ordinary calls. */
export const DEFAULT_TIMEOUT_MS = 60_000;
/** AI calls run on a local CPU model and can take several minutes. */
export const AI_TIMEOUT_MS = 15 * 60_000;

/** Pass as axios config for any request that triggers AI work. */
export const aiRequest: AxiosRequestConfig = { timeout: AI_TIMEOUT_MS };

export const api = axios.create({ baseURL: API_BASE_URL, timeout: DEFAULT_TIMEOUT_MS });

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

/** Like toApiError, but also reads JSON error bodies returned for blob downloads. */
async function normalizeError(error: AxiosError): Promise<ApiError> {
  const blob = error.response?.data;
  if (blob instanceof Blob && blob.type.includes('json')) {
    try {
      const body = JSON.parse(await blob.text()) as ApiErrorBody;
      return new ApiError({
        status: error.response?.status ?? null,
        code: body.code ?? 'REQUEST_FAILED',
        message: body.message ?? 'Request failed',
        correlationId: body.correlationId ?? null,
        serverMessage: body.message ?? null,
      });
    } catch {
      /* fall through */
    }
  }
  return toApiError(error);
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

let refreshInFlight: Promise<string> | null = null;

/** Exchanges the refresh token once; concurrent 401s wait on the same promise. */
function refreshAccessToken(): Promise<string> {
  if (!refreshInFlight) {
    const refreshToken = useAuthStore.getState().refreshToken;
    refreshInFlight = (async () => {
      if (!refreshToken) throw new Error('No refresh token');
      const { data } = await axios.post<LoginResponse>(
        `${API_BASE_URL}/auth/refresh`,
        { refreshToken },
        { timeout: DEFAULT_TIMEOUT_MS },
      );
      useAuthStore.getState().setTokens(data.accessToken, data.refreshToken, data.user ?? null);
      return data.accessToken;
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

function isAuthEndpoint(url: string | undefined) {
  return !!url && /\/auth\/(login|register|refresh)/.test(url);
}

api.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined;
    const status = error.response?.status;
    // 401 → refresh once and replay. 403 is a permission problem and must NOT log the user out.
    if (status === 401 && original && !original._retried && !isAuthEndpoint(original.url)) {
      original._retried = true;
      try {
        const token = await refreshAccessToken();
        original.headers.Authorization = `Bearer ${token}`;
        return api(original);
      } catch {
        clearUserState();
        return Promise.reject(await normalizeError(error));
      }
    }
    if (status === 401 && original?._retried) {
      clearUserState();
    }
    return Promise.reject(await normalizeError(error));
  },
);
