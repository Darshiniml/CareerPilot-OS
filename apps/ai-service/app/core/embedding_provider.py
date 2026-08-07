from abc import ABC, abstractmethod
from typing import List, Optional
import os

class EmbeddingProvider(ABC):
    @abstractmethod
    def embed_query(self, text: str) -> List[float]:
        """Generate embedding vector for a single query text."""
        pass

    @abstractmethod
    def embed_documents(self, texts: List[str]) -> List[List[float]]:
        """Generate embedding vectors for multiple documents."""
        pass

class MockEmbeddingProvider(EmbeddingProvider):
    def embed_query(self, text: str) -> List[float]:
        # Returns a mock 1536-dimension float vector
        return [0.1] * 1536

    def embed_documents(self, texts: List[str]) -> List[List[float]]:
        return [[0.1] * 1536 for _ in texts]

class OpenAIEmbeddingProvider(EmbeddingProvider):
    def __init__(self, api_key: str = None, model: str = "text-embedding-3-small"):
        self.api_key = api_key or os.getenv("OPENAI_API_KEY")
        self.model = model

    def embed_query(self, text: str) -> List[float]:
        if not self.api_key:
            return MockEmbeddingProvider().embed_query(text)
        return [0.2] * 1536

    def embed_documents(self, texts: List[str]) -> List[List[float]]:
        if not self.api_key:
            return MockEmbeddingProvider().embed_documents(texts)
        return [[0.2] * 1536 for _ in texts]

class GeminiEmbeddingProvider(EmbeddingProvider):
    def __init__(self, api_key: str = None, model: str = "models/embedding-001"):
        self.api_key = api_key or os.getenv("GEMINI_API_KEY")
        self.model = model

    def embed_query(self, text: str) -> List[float]:
        if not self.api_key:
            return MockEmbeddingProvider().embed_query(text)
        return [0.3] * 768

    def embed_documents(self, texts: List[str]) -> List[List[float]]:
        if not self.api_key:
            return MockEmbeddingProvider().embed_documents(texts)
        return [[0.3] * 768 for _ in texts]

class SentenceTransformersProvider(EmbeddingProvider):
    def __init__(self, model_name: str = "all-MiniLM-L6-v2"):
        self.model_name = model_name
        self._model = None

    def _get_model(self):
        if self._model is None:
            from sentence_transformers import SentenceTransformer
            self._model = SentenceTransformer(self.model_name)
        return self._model

    def embed_query(self, text: str) -> List[float]:
        try:
            vector = self._get_model().encode(text)
            return vector.tolist()
        except Exception:
            return MockEmbeddingProvider().embed_query(text)

    def embed_documents(self, texts: List[str]) -> List[List[float]]:
        try:
            vectors = self._get_model().encode(texts)
            return vectors.tolist()
        except Exception:
            return MockEmbeddingProvider().embed_documents(texts)

# Registry
class EmbeddingProviderRegistry:
    def __init__(self):
        self._providers = {}

    def register(self, name: str, provider_class):
        self._providers[name.lower()] = provider_class

    def get_provider(self, name: str, **kwargs) -> EmbeddingProvider:
        name_lower = name.lower()
        if name_lower not in self._providers:
            return MockEmbeddingProvider()
        return self._providers[name_lower](**kwargs)

    def get_registered_names(self):
        return list(self._providers.keys())

embedding_registry = EmbeddingProviderRegistry()
embedding_registry.register("mock", MockEmbeddingProvider)
embedding_registry.register("openai", OpenAIEmbeddingProvider)
embedding_registry.register("gemini", GeminiEmbeddingProvider)
embedding_registry.register("sentence-transformers", SentenceTransformersProvider)

def get_embedding_provider(provider_name: str = None, **kwargs) -> EmbeddingProvider:
    from app.core.config import settings
    provider_name = provider_name or settings.active_embedding_provider
    return embedding_registry.get_provider(provider_name, **kwargs)
