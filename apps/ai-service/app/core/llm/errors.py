"""Explicit AI failure types.

Every failure is surfaced to the caller with a stable ``code``. There is no silent fallback to
canned or deterministic output when a model is unavailable: callers must handle the error.
"""


class AIError(Exception):
    """Base class for AI failures. ``http_status`` is what the API layer returns."""

    code = "AI_ERROR"
    http_status = 502

    def __init__(self, message: str, *, provider: str | None = None, details: dict | None = None):
        super().__init__(message)
        self.message = message
        self.provider = provider
        self.details = details or {}

    def to_dict(self) -> dict:
        body = {"code": self.code, "message": self.message}
        if self.provider:
            body["provider"] = self.provider
        if self.details:
            body["details"] = self.details
        return body


class ProviderConfigurationError(AIError):
    """The selected provider is unknown or is missing required configuration (e.g. API key)."""

    code = "AI_PROVIDER_NOT_CONFIGURED"
    http_status = 503


class ProviderUnavailableError(AIError):
    """The provider could not be reached or returned a retryable error after all retries."""

    code = "AI_PROVIDER_UNAVAILABLE"
    http_status = 503


class ProviderTimeoutError(AIError):
    code = "AI_PROVIDER_TIMEOUT"
    http_status = 504


class ProviderResponseError(AIError):
    """The provider rejected the request (4xx) or returned an unusable response."""

    code = "AI_PROVIDER_ERROR"
    http_status = 502


class ProviderRefusalError(AIError):
    """The model declined to answer (e.g. safety refusal)."""

    code = "AI_PROVIDER_REFUSAL"
    http_status = 422


class StructuredOutputError(AIError):
    """The model answered, but the answer did not satisfy the required schema/validation."""

    code = "AI_INVALID_STRUCTURED_OUTPUT"
    http_status = 502


class UnsupportedTaskError(AIError):
    code = "AI_UNSUPPORTED_TASK"
    http_status = 400


class InvalidTaskInputError(AIError):
    code = "AI_INVALID_TASK_INPUT"
    http_status = 400
