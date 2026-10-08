"""LLM / embedding configuration, read from the environment.

Nothing here has a "mock" default. If the configured provider cannot work (unknown name, missing
key) the error is raised when the provider is built, and reported by ``/api/v1/ai/providers``.
"""

import os
from dataclasses import dataclass, field

SUPPORTED_LLM_PROVIDERS = ("ollama", "openai", "anthropic", "gemini")
SUPPORTED_EMBEDDING_PROVIDERS = ("ollama", "openai", "gemini", "sentence-transformers")

DEFAULT_LLM_MODELS = {
    "ollama": "llama3.2",
    "openai": "gpt-4o-mini",
    "anthropic": "claude-opus-5-5",
    "gemini": "gemini-2.5-flash",
}

DEFAULT_EMBEDDING_MODELS = {
    "ollama": "nomic-embed-text",
    "openai": "text-embedding-3-small",
    "gemini": "gemini-embedding-001",
    "sentence-transformers": "all-MiniLM-L6-v2",
}

_API_KEY_ENV = {
    "openai": "OPENAI_API_KEY",
    "anthropic": "ANTHROPIC_API_KEY",
    "gemini": "GEMINI_API_KEY",
}


def _env(name: str, default: str | None = None) -> str | None:
    value = os.getenv(name)
    if value is None or value.strip() == "":
        return default
    return value.strip()


def _float(name: str, default: float) -> float:
    raw = _env(name)
    return float(raw) if raw is not None else default


def _int(name: str, default: int) -> int:
    raw = _env(name)
    return int(raw) if raw is not None else default


@dataclass(frozen=True)
class LLMConfig:
    provider: str
    model: str
    api_key: str | None = field(repr=False, default=None)
    base_url: str | None = None
    timeout_seconds: float = 120.0
    max_retries: int = 2
    temperature: float = 0.1
    max_output_tokens: int = 2048
    context_window: int = 8192
    # Anthropic only: output_config.effort (low|medium|high|xhigh|max).
    effort: str | None = None
    # Longest document text sent whole to the model. Long postings/pages are reduced to their
    # relevant sections beyond this (local CPU models process only ~30 prompt tokens/s).
    max_document_chars: int = 24000

    @staticmethod
    def from_env() -> "LLMConfig":
        provider = (_env("LLM_PROVIDER") or _env("AI_PROVIDER") or "ollama").lower()
        model = _env("LLM_MODEL") or _env("AI_MODEL") or DEFAULT_LLM_MODELS.get(provider, "")
        api_key = _env("LLM_API_KEY") or _env("AI_API_KEY") or (
            _env(_API_KEY_ENV[provider]) if provider in _API_KEY_ENV else None
        )
        base_url = _env("LLM_BASE_URL")
        if provider == "ollama" and not base_url:
            base_url = _env("OLLAMA_BASE_URL", "http://localhost:11434")
        # Local CPU inference is slow (measured ~30 prompt tok/s, ~5 output tok/s on a laptop CPU);
        # cloud calls should fail faster.
        default_timeout = 600.0 if provider == "ollama" else 120.0
        return LLMConfig(
            provider=provider,
            model=model,
            api_key=api_key,
            base_url=base_url,
            timeout_seconds=_float("LLM_TIMEOUT_SECONDS", default_timeout),
            max_retries=_int("LLM_MAX_RETRIES", 2),
            temperature=_float("LLM_TEMPERATURE", 0.1),
            max_output_tokens=_int("LLM_MAX_OUTPUT_TOKENS", 2048),
            context_window=_int("LLM_CONTEXT_WINDOW", 8192),
            effort=_env("LLM_EFFORT"),
            max_document_chars=_int("LLM_MAX_DOCUMENT_CHARS", 9000 if provider == "ollama" else 24000),
        )


@dataclass(frozen=True)
class EmbeddingConfig:
    provider: str
    model: str
    api_key: str | None = field(repr=False, default=None)
    base_url: str | None = None
    timeout_seconds: float = 60.0
    max_retries: int = 2

    @staticmethod
    def from_env() -> "EmbeddingConfig":
        provider = (_env("EMBEDDING_PROVIDER") or "ollama").lower()
        model = _env("EMBEDDING_MODEL") or DEFAULT_EMBEDDING_MODELS.get(provider, "")
        api_key = _env("EMBEDDING_API_KEY") or (
            _env(_API_KEY_ENV[provider]) if provider in _API_KEY_ENV else None
        )
        base_url = _env("EMBEDDING_BASE_URL")
        if provider == "ollama" and not base_url:
            base_url = _env("OLLAMA_BASE_URL", "http://localhost:11434")
        return EmbeddingConfig(
            provider=provider,
            model=model,
            api_key=api_key,
            base_url=base_url,
            timeout_seconds=_float("EMBEDDING_TIMEOUT_SECONDS", 60.0),
            max_retries=_int("EMBEDDING_MAX_RETRIES", 2),
        )
