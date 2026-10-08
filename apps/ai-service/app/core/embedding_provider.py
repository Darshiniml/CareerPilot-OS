"""Real embedding providers.

Every provider calls a real embedding model. There is no constant-vector fallback: when the
configured provider cannot produce embeddings an ``AIError`` is raised and semantic search reports
itself unavailable instead of returning meaningless matches.
"""

from __future__ import annotations

import threading
import time
from abc import ABC, abstractmethod
from typing import Any

import httpx

from app.core.llm.config import SUPPORTED_EMBEDDING_PROVIDERS, EmbeddingConfig
from app.core.llm.errors import ProviderConfigurationError, ProviderResponseError
from app.core.llm.providers import _with_retries


class EmbeddingProvider(ABC):
    name = "base"

    def __init__(self, config: EmbeddingConfig):
        self.config = config
        self.model = config.model
        self._dimension: int | None = None

    @abstractmethod
    def _embed(self, texts: list[str]) -> list[list[float]]:
        ...

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        vectors = self._embed(texts)
        if len(vectors) != len(texts):
            raise ProviderResponseError(
                f"{self.name} returned {len(vectors)} embeddings for {len(texts)} inputs", provider=self.name
            )
        dims = {len(v) for v in vectors}
        if len(dims) != 1 or 0 in dims:
            raise ProviderResponseError(f"{self.name} returned inconsistent embedding sizes {dims}", provider=self.name)
        dimension = dims.pop()
        if self._dimension is not None and dimension != self._dimension:
            raise ProviderResponseError(
                f"{self.name} embedding dimension changed from {self._dimension} to {dimension}", provider=self.name
            )
        self._dimension = dimension
        return vectors

    def embed_query(self, text: str) -> list[float]:
        return self.embed_documents([text])[0]

    @property
    def dimension(self) -> int:
        if self._dimension is None:
            self.embed_query("dimension probe")
        return self._dimension  # type: ignore[return-value]

    def health(self) -> dict[str, Any]:
        try:
            dim = self.dimension
            return {"provider": self.name, "model": self.model, "configured": True, "status": "UP", "dimension": dim}
        except Exception as exc:  # noqa: BLE001
            return {"provider": self.name, "model": self.model, "configured": True, "status": "DOWN",
                    "error": getattr(exc, "message", exc.__class__.__name__)}


class OllamaEmbeddingProvider(EmbeddingProvider):
    name = "ollama"

    def __init__(self, config: EmbeddingConfig):
        super().__init__(config)
        if not config.base_url:
            raise ProviderConfigurationError("OLLAMA_BASE_URL is not configured", provider=self.name)
        self.base_url = config.base_url.rstrip("/")

    def _embed(self, texts):
        with httpx.Client(timeout=self.config.timeout_seconds) as client:
            response = _with_retries(
                self.name, self.config.max_retries,
                lambda: client.post(f"{self.base_url}/api/embed",
                                    json={"model": self.model, "input": texts, "keep_alive": "30m"}),
            )
        data = response.json()
        if "error" in data:
            raise ProviderResponseError(f"Ollama embedding error: {data['error']}", provider=self.name)
        return data.get("embeddings") or []


class OpenAIEmbeddingProvider(EmbeddingProvider):
    name = "openai"

    def __init__(self, config: EmbeddingConfig):
        super().__init__(config)
        if not config.api_key:
            raise ProviderConfigurationError(
                "OpenAI embeddings selected but OPENAI_API_KEY (or EMBEDDING_API_KEY) is not set", provider=self.name
            )
        self.base_url = (config.base_url or "https://api.openai.com/v1").rstrip("/")

    def _embed(self, texts):
        headers = {"Authorization": f"Bearer {self.config.api_key}"}
        with httpx.Client(timeout=self.config.timeout_seconds) as client:
            response = _with_retries(
                self.name, self.config.max_retries,
                lambda: client.post(f"{self.base_url}/embeddings", headers=headers,
                                    json={"model": self.model, "input": texts}),
            )
        data = sorted(response.json().get("data", []), key=lambda d: d.get("index", 0))
        return [d["embedding"] for d in data]


class GeminiEmbeddingProvider(EmbeddingProvider):
    name = "gemini"

    def __init__(self, config: EmbeddingConfig):
        super().__init__(config)
        if not config.api_key:
            raise ProviderConfigurationError(
                "Gemini embeddings selected but GEMINI_API_KEY (or EMBEDDING_API_KEY) is not set", provider=self.name
            )
        self.base_url = (config.base_url or "https://generativelanguage.googleapis.com/v1beta").rstrip("/")

    def _embed(self, texts):
        model_path = self.model if self.model.startswith("models/") else f"models/{self.model}"
        body = {"requests": [{"model": model_path, "content": {"parts": [{"text": t}]}} for t in texts]}
        headers = {"x-goog-api-key": self.config.api_key or ""}
        with httpx.Client(timeout=self.config.timeout_seconds) as client:
            response = _with_retries(
                self.name, self.config.max_retries,
                lambda: client.post(f"{self.base_url}/{model_path}:batchEmbedContents", headers=headers, json=body),
            )
        return [e["values"] for e in response.json().get("embeddings", [])]


class SentenceTransformersProvider(EmbeddingProvider):
    name = "sentence-transformers"
    _lock = threading.Lock()

    def __init__(self, config: EmbeddingConfig):
        super().__init__(config)
        try:
            import sentence_transformers  # noqa: F401
        except ImportError as exc:
            raise ProviderConfigurationError(
                "EMBEDDING_PROVIDER=sentence-transformers but the 'sentence-transformers' package is not installed",
                provider=self.name,
            ) from exc
        self._model = None

    def _embed(self, texts):
        with self._lock:
            if self._model is None:
                from sentence_transformers import SentenceTransformer

                self._model = SentenceTransformer(self.model)
        return self._model.encode(texts, normalize_embeddings=True).tolist()


_PROVIDERS = {
    "ollama": OllamaEmbeddingProvider,
    "openai": OpenAIEmbeddingProvider,
    "gemini": GeminiEmbeddingProvider,
    "sentence-transformers": SentenceTransformersProvider,
}


def build_embedding_provider(config: EmbeddingConfig) -> EmbeddingProvider:
    cls = _PROVIDERS.get(config.provider)
    if cls is None:
        raise ProviderConfigurationError(
            f"Unknown EMBEDDING_PROVIDER '{config.provider}'. Supported: {', '.join(SUPPORTED_EMBEDDING_PROVIDERS)}"
        )
    return cls(config)


_provider: EmbeddingProvider | None = None
_provider_lock = threading.Lock()


def get_embedding_provider() -> EmbeddingProvider:
    global _provider
    with _provider_lock:
        if _provider is None:
            _provider = build_embedding_provider(EmbeddingConfig.from_env())
        return _provider


def set_embedding_provider(provider: EmbeddingProvider | None) -> None:
    global _provider
    with _provider_lock:
        _provider = provider


def registered_embedding_providers() -> list[str]:
    return list(_PROVIDERS.keys())


def _now_ms() -> int:
    return int(time.time() * 1000)
