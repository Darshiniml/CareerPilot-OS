"""Real LLM provider implementations.

Each provider sends a real request to its backend and returns the raw text of a JSON response
constrained by a JSON schema. Validation of that JSON against the task's model happens in the
gateway. No provider ever returns canned text: if it cannot produce a real answer it raises an
``AIError`` subclass.
"""

from __future__ import annotations

import json
import logging
import time
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, Callable

import httpx

from app.core.llm.config import SUPPORTED_LLM_PROVIDERS, LLMConfig
from app.core.llm.errors import (
    ProviderConfigurationError,
    ProviderRefusalError,
    ProviderResponseError,
    ProviderTimeoutError,
    ProviderUnavailableError,
)

log = logging.getLogger("careerpilot.ai.llm")

_RETRYABLE_STATUS = {408, 409, 425, 429, 500, 502, 503, 504, 529}


@dataclass
class LLMResult:
    text: str
    provider: str
    model: str
    latency_ms: int
    input_tokens: int | None = None
    output_tokens: int | None = None
    raw_stop_reason: str | None = None
    extra: dict[str, Any] = field(default_factory=dict)


@dataclass
class ChatMessage:
    role: str  # "user" | "assistant"
    content: str


class LLMProvider(ABC):
    name: str = "base"

    def __init__(self, config: LLMConfig):
        self.config = config
        self.model = config.model

    @abstractmethod
    def complete_json(
        self,
        system: str,
        messages: list[ChatMessage],
        schema: dict[str, Any],
        schema_name: str,
        max_tokens: int | None = None,
        temperature: float | None = None,
    ) -> LLMResult:
        """Return model output that is expected to be a JSON document matching ``schema``."""

    @abstractmethod
    def health(self) -> dict[str, Any]:
        """Report whether the provider is configured and reachable. Never raises."""


def _with_retries(provider: str, max_retries: int, call: Callable[[], httpx.Response]) -> httpx.Response:
    """Run an HTTP call, retrying connection errors and retryable status codes with backoff."""
    attempt = 0
    while True:
        attempt += 1
        response: httpx.Response | None = None
        try:
            response = call()
        except httpx.ReadTimeout as exc:
            # The provider accepted the request and is still generating; retrying would only
            # double the wait (a long generation on a CPU model would time out again).
            raise ProviderTimeoutError(f"{provider} did not finish generating in time", provider=provider) from exc
        except httpx.TimeoutException as exc:
            if attempt > max_retries:
                raise ProviderTimeoutError(f"{provider} request timed out after {attempt} attempt(s)", provider=provider) from exc
            log.warning("%s timeout (attempt %s), retrying", provider, attempt)
        except httpx.TransportError as exc:
            if attempt > max_retries:
                raise ProviderUnavailableError(
                    f"{provider} is unreachable: {exc.__class__.__name__}", provider=provider
                ) from exc
            log.warning("%s transport error %s (attempt %s), retrying", provider, exc, attempt)
        else:
            if response.status_code < 400:
                return response
            body = response.text[:500]
            if response.status_code in _RETRYABLE_STATUS and attempt <= max_retries:
                log.warning("%s HTTP %s (attempt %s), retrying", provider, response.status_code, attempt)
            elif response.status_code in (401, 403):
                raise ProviderConfigurationError(
                    f"{provider} rejected the credentials (HTTP {response.status_code})",
                    provider=provider,
                    details={"status": response.status_code},
                )
            elif response.status_code in _RETRYABLE_STATUS:
                raise ProviderUnavailableError(
                    f"{provider} returned HTTP {response.status_code} after {attempt} attempt(s)",
                    provider=provider,
                    details={"status": response.status_code, "body": body},
                )
            else:
                raise ProviderResponseError(
                    f"{provider} returned HTTP {response.status_code}",
                    provider=provider,
                    details={"status": response.status_code, "body": body},
                )
        retry_after = None
        if response is not None:
            try:
                retry_after = float(response.headers.get("retry-after", ""))
            except ValueError:
                pass
        time.sleep(min(retry_after or (1.5 * attempt), 20.0))


# --------------------------------------------------------------------------------------------
# Ollama (local)
# --------------------------------------------------------------------------------------------


class OllamaProvider(LLMProvider):
    name = "ollama"

    def __init__(self, config: LLMConfig):
        super().__init__(config)
        if not config.base_url:
            raise ProviderConfigurationError("OLLAMA_BASE_URL is not configured", provider=self.name)
        if not config.model:
            raise ProviderConfigurationError("LLM_MODEL is not configured for Ollama", provider=self.name)
        self.base_url = config.base_url.rstrip("/")

    def complete_json(self, system, messages, schema, schema_name, max_tokens=None, temperature=None):
        body = {
            "model": self.model,
            "stream": False,
            "format": schema,
            "keep_alive": "30m",
            "options": {
                "temperature": self.config.temperature if temperature is None else temperature,
                "num_predict": max_tokens or self.config.max_output_tokens,
                "num_ctx": self.config.context_window,
            },
            "messages": [{"role": "system", "content": system}]
            + [{"role": m.role, "content": m.content} for m in messages],
        }
        started = time.monotonic()
        with httpx.Client(timeout=self.config.timeout_seconds) as client:
            response = _with_retries(
                self.name, self.config.max_retries, lambda: client.post(f"{self.base_url}/api/chat", json=body)
            )
        data = response.json()
        if "error" in data:
            raise ProviderResponseError(f"Ollama error: {data['error']}", provider=self.name)
        message = data.get("message") or {}
        text = message.get("content")
        if not text:
            raise ProviderResponseError("Ollama returned an empty message", provider=self.name)
        return LLMResult(
            text=text,
            provider=self.name,
            model=data.get("model", self.model),
            latency_ms=int((time.monotonic() - started) * 1000),
            input_tokens=data.get("prompt_eval_count"),
            output_tokens=data.get("eval_count"),
            raw_stop_reason=data.get("done_reason"),
        )

    def health(self):
        try:
            with httpx.Client(timeout=5.0) as client:
                tags = client.get(f"{self.base_url}/api/tags").json()
            names = {m.get("name", "") for m in tags.get("models", [])}
            available = self.model in names or f"{self.model}:latest" in names
            return {
                "provider": self.name,
                "model": self.model,
                "configured": True,
                "reachable": True,
                "modelAvailable": available,
                "status": "UP" if available else "MODEL_NOT_PULLED",
            }
        except Exception as exc:  # noqa: BLE001 - health must never raise
            return {
                "provider": self.name,
                "model": self.model,
                "configured": True,
                "reachable": False,
                "status": "DOWN",
                "error": exc.__class__.__name__,
            }


# --------------------------------------------------------------------------------------------
# OpenAI
# --------------------------------------------------------------------------------------------


class OpenAIProvider(LLMProvider):
    name = "openai"

    def __init__(self, config: LLMConfig):
        super().__init__(config)
        if not config.api_key:
            raise ProviderConfigurationError(
                "OpenAI is selected but OPENAI_API_KEY (or LLM_API_KEY) is not set", provider=self.name
            )
        self.base_url = (config.base_url or "https://api.openai.com/v1").rstrip("/")

    def _headers(self):
        return {"Authorization": f"Bearer {self.config.api_key}", "Content-Type": "application/json"}

    def complete_json(self, system, messages, schema, schema_name, max_tokens=None, temperature=None):
        body = {
            "model": self.model,
            "messages": [{"role": "system", "content": system}]
            + [{"role": m.role, "content": m.content} for m in messages],
            "response_format": {
                "type": "json_schema",
                "json_schema": {"name": schema_name, "schema": schema, "strict": False},
            },
            "max_completion_tokens": max_tokens or self.config.max_output_tokens,
        }
        if temperature is not None or self.config.temperature is not None:
            body["temperature"] = self.config.temperature if temperature is None else temperature
        started = time.monotonic()
        with httpx.Client(timeout=self.config.timeout_seconds) as client:
            response = _with_retries(
                self.name,
                self.config.max_retries,
                lambda: client.post(f"{self.base_url}/chat/completions", headers=self._headers(), json=body),
            )
        data = response.json()
        choice = (data.get("choices") or [{}])[0]
        message = choice.get("message") or {}
        if message.get("refusal"):
            raise ProviderRefusalError(f"OpenAI refused: {message['refusal']}", provider=self.name)
        text = message.get("content")
        if not text:
            raise ProviderResponseError("OpenAI returned an empty message", provider=self.name)
        usage = data.get("usage") or {}
        return LLMResult(
            text=text,
            provider=self.name,
            model=data.get("model", self.model),
            latency_ms=int((time.monotonic() - started) * 1000),
            input_tokens=usage.get("prompt_tokens"),
            output_tokens=usage.get("completion_tokens"),
            raw_stop_reason=choice.get("finish_reason"),
        )

    def health(self):
        try:
            with httpx.Client(timeout=8.0) as client:
                r = client.get(f"{self.base_url}/models/{self.model}", headers=self._headers())
            return {
                "provider": self.name,
                "model": self.model,
                "configured": True,
                "reachable": r.status_code < 500,
                "modelAvailable": r.status_code == 200,
                "status": "UP" if r.status_code == 200 else f"HTTP_{r.status_code}",
            }
        except Exception as exc:  # noqa: BLE001
            return {"provider": self.name, "model": self.model, "configured": True, "reachable": False,
                    "status": "DOWN", "error": exc.__class__.__name__}


# --------------------------------------------------------------------------------------------
# Anthropic (Claude) — official SDK
# --------------------------------------------------------------------------------------------

# Models that accept the server-side refusal fallback (`fallbacks: "default"`).
_ANTHROPIC_FALLBACK_MODELS = ("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5")


class AnthropicProvider(LLMProvider):
    name = "anthropic"

    def __init__(self, config: LLMConfig):
        super().__init__(config)
        if not config.api_key:
            raise ProviderConfigurationError(
                "Anthropic is selected but ANTHROPIC_API_KEY (or LLM_API_KEY) is not set", provider=self.name
            )
        import anthropic  # imported lazily so other providers don't require the SDK

        self._anthropic = anthropic
        kwargs: dict[str, Any] = {
            "api_key": config.api_key,
            "timeout": config.timeout_seconds,
            "max_retries": config.max_retries,
        }
        if config.base_url:
            kwargs["base_url"] = config.base_url
        self.client = anthropic.Anthropic(**kwargs)

    def complete_json(self, system, messages, schema, schema_name, max_tokens=None, temperature=None):
        anthropic = self._anthropic
        output_config: dict[str, Any] = {"format": {"type": "json_schema", "schema": schema}}
        if self.config.effort:
            output_config["effort"] = self.config.effort
        params: dict[str, Any] = {
            "model": self.model,
            "max_tokens": max_tokens or max(self.config.max_output_tokens, 4096),
            "system": system,
            "messages": [{"role": m.role, "content": m.content} for m in messages],
            "output_config": output_config,
        }
        started = time.monotonic()
        try:
            if self.model in _ANTHROPIC_FALLBACK_MODELS:
                response = self.client.beta.messages.create(
                    **params, betas=["server-side-fallback-2026-07-01"], fallbacks="default"
                )
            else:
                response = self.client.messages.create(**params)
        except anthropic.AuthenticationError as exc:
            raise ProviderConfigurationError("Anthropic rejected the API key", provider=self.name) from exc
        except anthropic.PermissionDeniedError as exc:
            raise ProviderConfigurationError("Anthropic API key lacks permission", provider=self.name) from exc
        except anthropic.NotFoundError as exc:
            raise ProviderConfigurationError(f"Anthropic model not found: {self.model}", provider=self.name) from exc
        except anthropic.BadRequestError as exc:
            raise ProviderResponseError(f"Anthropic rejected the request: {exc.message}", provider=self.name) from exc
        except anthropic.RateLimitError as exc:
            raise ProviderUnavailableError("Anthropic rate limit exceeded", provider=self.name) from exc
        except anthropic.APITimeoutError as exc:
            raise ProviderTimeoutError("Anthropic request timed out", provider=self.name) from exc
        except anthropic.APIStatusError as exc:
            raise ProviderUnavailableError(f"Anthropic returned HTTP {exc.status_code}", provider=self.name) from exc
        except anthropic.APIConnectionError as exc:
            raise ProviderUnavailableError("Anthropic is unreachable", provider=self.name) from exc

        if response.stop_reason == "refusal":
            category = getattr(getattr(response, "stop_details", None), "category", None)
            raise ProviderRefusalError("Claude declined the request", provider=self.name,
                                       details={"category": category})
        if response.stop_reason == "max_tokens":
            raise ProviderResponseError("Claude output was truncated (max_tokens)", provider=self.name)
        text = next((b.text for b in response.content if getattr(b, "type", None) == "text"), None)
        if not text:
            raise ProviderResponseError("Claude returned no text content", provider=self.name)
        usage = getattr(response, "usage", None)
        return LLMResult(
            text=text,
            provider=self.name,
            model=getattr(response, "model", self.model),
            latency_ms=int((time.monotonic() - started) * 1000),
            input_tokens=getattr(usage, "input_tokens", None),
            output_tokens=getattr(usage, "output_tokens", None),
            raw_stop_reason=response.stop_reason,
        )

    def health(self):
        try:
            self.client.with_options(timeout=8.0, max_retries=0).models.retrieve(self.model)
            return {"provider": self.name, "model": self.model, "configured": True, "reachable": True,
                    "modelAvailable": True, "status": "UP"}
        except Exception as exc:  # noqa: BLE001
            return {"provider": self.name, "model": self.model, "configured": True, "reachable": False,
                    "status": "DOWN", "error": exc.__class__.__name__}


# --------------------------------------------------------------------------------------------
# Google Gemini
# --------------------------------------------------------------------------------------------


class GeminiProvider(LLMProvider):
    name = "gemini"

    def __init__(self, config: LLMConfig):
        super().__init__(config)
        if not config.api_key:
            raise ProviderConfigurationError(
                "Gemini is selected but GEMINI_API_KEY (or LLM_API_KEY) is not set", provider=self.name
            )
        self.base_url = (config.base_url or "https://generativelanguage.googleapis.com/v1beta").rstrip("/")

    def _headers(self):
        return {"x-goog-api-key": self.config.api_key or "", "Content-Type": "application/json"}

    def complete_json(self, system, messages, schema, schema_name, max_tokens=None, temperature=None):
        body = {
            "systemInstruction": {"parts": [{"text": system}]},
            "contents": [
                {"role": "model" if m.role == "assistant" else "user", "parts": [{"text": m.content}]}
                for m in messages
            ],
            "generationConfig": {
                "responseMimeType": "application/json",
                "responseJsonSchema": schema,
                "temperature": self.config.temperature if temperature is None else temperature,
                "maxOutputTokens": max_tokens or self.config.max_output_tokens,
            },
        }
        url = f"{self.base_url}/models/{self.model}:generateContent"
        started = time.monotonic()
        with httpx.Client(timeout=self.config.timeout_seconds) as client:
            response = _with_retries(
                self.name, self.config.max_retries, lambda: client.post(url, headers=self._headers(), json=body)
            )
        data = response.json()
        if (data.get("promptFeedback") or {}).get("blockReason"):
            raise ProviderRefusalError(f"Gemini blocked the prompt: {data['promptFeedback']['blockReason']}",
                                       provider=self.name)
        candidate = (data.get("candidates") or [{}])[0]
        if candidate.get("finishReason") in ("SAFETY", "RECITATION", "PROHIBITED_CONTENT"):
            raise ProviderRefusalError(f"Gemini stopped: {candidate['finishReason']}", provider=self.name)
        parts = (candidate.get("content") or {}).get("parts") or []
        text = "".join(p.get("text", "") for p in parts)
        if not text:
            raise ProviderResponseError("Gemini returned no text", provider=self.name)
        usage = data.get("usageMetadata") or {}
        return LLMResult(
            text=text,
            provider=self.name,
            model=data.get("modelVersion", self.model),
            latency_ms=int((time.monotonic() - started) * 1000),
            input_tokens=usage.get("promptTokenCount"),
            output_tokens=usage.get("candidatesTokenCount"),
            raw_stop_reason=candidate.get("finishReason"),
        )

    def health(self):
        try:
            with httpx.Client(timeout=8.0) as client:
                r = client.get(f"{self.base_url}/models/{self.model}", headers=self._headers())
            return {"provider": self.name, "model": self.model, "configured": True,
                    "reachable": r.status_code < 500, "modelAvailable": r.status_code == 200,
                    "status": "UP" if r.status_code == 200 else f"HTTP_{r.status_code}"}
        except Exception as exc:  # noqa: BLE001
            return {"provider": self.name, "model": self.model, "configured": True, "reachable": False,
                    "status": "DOWN", "error": exc.__class__.__name__}


_PROVIDERS: dict[str, type[LLMProvider]] = {
    "ollama": OllamaProvider,
    "openai": OpenAIProvider,
    "anthropic": AnthropicProvider,
    "gemini": GeminiProvider,
}


def build_llm_provider(config: LLMConfig) -> LLMProvider:
    cls = _PROVIDERS.get(config.provider)
    if cls is None:
        raise ProviderConfigurationError(
            f"Unknown LLM_PROVIDER '{config.provider}'. Supported: {', '.join(SUPPORTED_LLM_PROVIDERS)}"
        )
    return cls(config)


def parse_json_text(text: str) -> Any:
    """Parse model JSON, tolerating a markdown code fence around it (some models add one)."""
    stripped = text.strip()
    if stripped.startswith("```"):
        stripped = stripped.strip("`")
        if stripped.lower().startswith("json"):
            stripped = stripped[4:]
    return json.loads(stripped)
