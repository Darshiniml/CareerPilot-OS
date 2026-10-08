import axios from 'axios';

/** Backend error body: `{timestamp, status, error, code, message, details, correlationId}`. */
export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  code?: string;
  message?: string;
  details?: unknown;
  correlationId?: string;
}

export class ApiError extends Error {
  readonly status: number | null;
  readonly code: string;
  readonly correlationId: string | null;
  readonly details: unknown;
  /** The raw server message (unchanged), useful for CONFLICT messages. */
  readonly serverMessage: string | null;

  constructor(opts: {
    status: number | null;
    code: string;
    message: string;
    correlationId?: string | null;
    details?: unknown;
    serverMessage?: string | null;
  }) {
    super(opts.message);
    this.name = 'ApiError';
    this.status = opts.status;
    this.code = opts.code;
    this.correlationId = opts.correlationId ?? null;
    this.details = opts.details;
    this.serverMessage = opts.serverMessage ?? null;
  }
}

export const AI_UNAVAILABLE_CODES = new Set([
  'AI_PROVIDER_NOT_CONFIGURED',
  'AI_PROVIDER_UNAVAILABLE',
  'AI_SERVICE_UNREACHABLE',
  'AI_TIMEOUT',
]);

/** Friendly headline per error code. The server message is shown underneath where useful. */
export function friendlyTitle(err: ApiError): string {
  if (AI_UNAVAILABLE_CODES.has(err.code)) {
    return 'The AI model is unavailable right now';
  }
  switch (err.code) {
    case 'AI_INVALID_STRUCTURED_OUTPUT':
      return 'The AI model returned an answer that could not be used';
    case 'CONFLICT':
      return 'This action is not possible right now';
    case 'FORBIDDEN':
      return "You don't have access";
    case 'NOT_FOUND':
      return 'Not found';
    case 'DRAFT_HAS_PLACEHOLDERS':
      return 'Fill in the placeholders first';
    case 'RECIPIENT_NOT_ALLOWED':
      return 'Recipient not allowed';
    case 'NO_EMAIL_CONNECTION':
      return 'No mailbox connected';
    case 'EMAIL_PROVIDER_NOT_CONFIGURED':
    case 'EMAIL_NOT_CONFIGURED':
      return 'Email sending is not configured';
    case 'VALIDATION_ERROR':
    case 'BAD_REQUEST':
      return 'Please check your input';
    case 'UNAUTHORIZED':
      return 'Your session has expired';
    case 'NETWORK_ERROR':
      return 'Cannot reach the server';
    case 'CLIENT_TIMEOUT':
      return 'The request took too long';
    default:
      if (err.status && err.status >= 500) return 'Something went wrong on the server';
      return 'Request failed';
  }
}

/** Explanation shown under the headline. */
export function friendlyDetail(err: ApiError): string {
  if (AI_UNAVAILABLE_CODES.has(err.code)) {
    const base =
      'The AI model is unavailable or did not respond in time. Nothing was generated, so no results are shown. ' +
      'Make sure the AI service and local model are running, then try again.';
    return err.serverMessage ? `${base} (${err.serverMessage})` : base;
  }
  switch (err.code) {
    case 'FORBIDDEN':
      return err.serverMessage ?? 'Your account is not allowed to perform this action.';
    case 'NO_EMAIL_CONNECTION':
      return err.serverMessage ?? 'Connect a Gmail or Outlook mailbox in Settings before sending.';
    case 'EMAIL_PROVIDER_NOT_CONFIGURED':
    case 'EMAIL_NOT_CONFIGURED':
      return err.serverMessage ?? 'The administrator has not configured an email provider.';
    case 'NETWORK_ERROR':
      return 'The backend did not respond. Check that it is running and that you are online.';
    case 'CLIENT_TIMEOUT':
      return 'The server did not answer in time. Nothing has been changed on screen.';
    default:
      return err.serverMessage ?? err.message;
  }
}

function isErrorBody(x: unknown): x is ApiErrorBody {
  return typeof x === 'object' && x !== null && ('code' in x || 'message' in x || 'status' in x);
}

/** Normalises anything thrown by axios (or elsewhere) into an ApiError. */
export function toApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e;
  if (axios.isAxiosError(e)) {
    if (e.code === 'ECONNABORTED' || e.code === 'ETIMEDOUT') {
      return new ApiError({ status: null, code: 'CLIENT_TIMEOUT', message: 'The request timed out.' });
    }
    if (!e.response) {
      return new ApiError({ status: null, code: 'NETWORK_ERROR', message: 'Network error.' });
    }
    const status = e.response.status;
    const data: unknown = e.response.data;
    const body = isErrorBody(data) ? data : undefined;
    const fallbackCode =
      status === 401
        ? 'UNAUTHORIZED'
        : status === 403
          ? 'FORBIDDEN'
          : status === 404
            ? 'NOT_FOUND'
            : status === 409
              ? 'CONFLICT'
              : status >= 500
                ? 'SERVER_ERROR'
                : 'BAD_REQUEST';
    const serverMessage = body?.message ?? (typeof data === 'string' && data.length < 500 ? data : null);
    return new ApiError({
      status,
      code: body?.code ?? fallbackCode,
      message: serverMessage ?? `Request failed with status ${status}`,
      correlationId: body?.correlationId ?? null,
      details: body?.details,
      serverMessage,
    });
  }
  if (e instanceof Error) {
    return new ApiError({ status: null, code: 'CLIENT_ERROR', message: e.message, serverMessage: e.message });
  }
  return new ApiError({ status: null, code: 'CLIENT_ERROR', message: 'Unexpected error' });
}

/** One-line message for toasts. */
export function errorToast(e: unknown): { title: string; description: string } {
  const err = toApiError(e);
  return { title: friendlyTitle(err), description: friendlyDetail(err) };
}
