"""Test fixtures.

``ScriptedProvider`` is a TEST DOUBLE: it returns pre-scripted JSON so unit tests can exercise the
gateway, guards and grounding logic deterministically. It is defined only here and is never
registered in the application. Live tests against a real model are in ``test_live_ai.py``.
"""

import hashlib
import json
import math
import os
import re
from collections import deque

import pytest

from app.core.embedding_provider import EmbeddingProvider, set_embedding_provider
from app.core.llm.config import EmbeddingConfig, LLMConfig
from app.core.llm.gateway import LLMGateway, set_gateway
from app.core.llm.providers import LLMProvider, LLMResult
from app.core.vector_store import VectorStore, set_vector_store

SERVICE_TOKEN = "test-service-token"


class ScriptedProvider(LLMProvider):
    name = "scripted-test-double"

    def __init__(self, responses=None):
        super().__init__(LLMConfig(provider="scripted", model="test-model"))
        self.responses = deque(responses or [])
        self.calls = []

    def push(self, *responses):
        for r in responses:
            self.responses.append(r)

    def complete_json(self, system, messages, schema, schema_name, max_tokens=None, temperature=None):
        self.calls.append({"system": system, "messages": messages, "schema": schema, "schema_name": schema_name})
        if not self.responses:
            raise AssertionError(f"ScriptedProvider has no response queued for {schema_name}")
        item = self.responses.popleft()
        if isinstance(item, Exception):
            raise item
        text = item if isinstance(item, str) else json.dumps(item)
        return LLMResult(text=text, provider=self.name, model=self.model, latency_ms=1, input_tokens=10, output_tokens=5)

    def health(self):
        return {"provider": self.name, "model": self.model, "configured": True, "status": "UP"}


class HashingEmbedder(EmbeddingProvider):
    """Deterministic bag-of-words embedder for isolation tests (TEST DOUBLE)."""

    name = "hashing-test-double"

    def __init__(self, dim=256):
        super().__init__(EmbeddingConfig(provider="hashing", model="bow"))
        self.dim = dim

    def _embed(self, texts):
        out = []
        for t in texts:
            v = [0.0] * self.dim
            for tok in re.findall(r"[a-z0-9]+", t.lower()):
                v[int(hashlib.md5(tok.encode()).hexdigest(), 16) % self.dim] += 1.0
            norm = math.sqrt(sum(x * x for x in v)) or 1.0
            out.append([x / norm for x in v])
        return out


@pytest.fixture
def scripted():
    provider = ScriptedProvider()
    set_gateway(LLMGateway(config=LLMConfig(provider="scripted", model="test-model"), provider=provider))
    yield provider
    set_gateway(None)


@pytest.fixture
def memory_store():
    from qdrant_client import QdrantClient

    embedder = HashingEmbedder()
    store = VectorStore(QdrantClient(":memory:"), embedder)
    set_embedding_provider(embedder)
    set_vector_store(store)
    yield store
    set_vector_store(None)
    set_embedding_provider(None)


@pytest.fixture
def client(monkeypatch):
    from fastapi.testclient import TestClient

    from app.main import app

    monkeypatch.setenv("AI_SERVICE_TOKEN", SERVICE_TOKEN)
    return TestClient(app)


@pytest.fixture
def auth():
    return {"Authorization": f"Bearer {SERVICE_TOKEN}"}


def pytest_collection_modifyitems(config, items):
    if os.getenv("CAREERPILOT_LIVE_AI") == "1":
        return
    skip = pytest.mark.skip(reason="live AI tests run only with CAREERPILOT_LIVE_AI=1")
    for item in items:
        if "live" in item.keywords:
            item.add_marker(skip)
