"""Qdrant-backed vector store with candidate isolation.

Collections are named per document type *and* embedding model/dimension, so vectors from different
models are never mixed. Point ids are deterministic (owner, document, chunk) so re-indexing a
document replaces its vectors instead of duplicating them.

Isolation rule: document types in ``PRIVATE_DOC_TYPES`` hold candidate data. Indexing them requires
an ``owner_id`` and every search over them is filtered by the caller's ``owner_id`` inside Qdrant.
There is no code path that searches a private collection without an owner filter.
"""

from __future__ import annotations

import contextlib
import os
import re
import threading
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

from qdrant_client import QdrantClient, models

from app.core.embedding_provider import EmbeddingProvider, get_embedding_provider
from app.core.llm.errors import InvalidTaskInputError, ProviderUnavailableError

PRIVATE_DOC_TYPES = {"RESUME", "PROFILE", "COMMUNICATION", "INTERVIEW", "APPLICATION", "LEARNING", "CONVERSATION", "COVER_LETTER"}
SHARED_DOC_TYPES = {"JOB", "COMPANY"}
_NAMESPACE = uuid.UUID("6f1c3c0e-8d7a-4a7c-9a3e-2f6c1b9d4e10")


@dataclass
class SearchHit:
    text: str
    score: float
    document_id: str
    chunk_index: int
    doc_type: str
    metadata: dict[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return {
            "text": self.text,
            "score": round(self.score, 4),
            "documentId": self.document_id,
            "chunkIndex": self.chunk_index,
            "documentType": self.doc_type,
            "metadata": self.metadata,
        }


def _slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.lower()).strip("_")


class VectorStore:
    def __init__(self, client: QdrantClient, embedder: EmbeddingProvider, serialize: bool = False):
        self.client = client
        self.embedder = embedder
        self._known: set[str] = set()
        self._lock = threading.RLock()
        # Embedded (local, SQLite-backed) Qdrant is not thread-safe: concurrent requests corrupt its
        # cursor state ("sqlite3.InterfaceError: bad parameter or other API misuse"). In that mode every
        # client operation is serialized; embeddings are still computed outside the lock.
        self._io = self._lock if serialize else contextlib.nullcontext()

    def collection_name(self, doc_type: str) -> str:
        return f"cp_{_slug(doc_type)}_{_slug(self.embedder.name)}_{_slug(self.embedder.model)}_{self.embedder.dimension}"

    def _ensure_collection(self, doc_type: str) -> str:
        name = self.collection_name(doc_type)
        with self._lock:  # RLock: also the I/O lock in embedded mode
            if name in self._known:
                return name
            if not self.client.collection_exists(name):
                self.client.create_collection(
                    name,
                    vectors_config=models.VectorParams(size=self.embedder.dimension, distance=models.Distance.COSINE),
                )
                if os.getenv("QDRANT_URL"):  # payload indexes only exist in server mode
                    for field in ("owner_id", "document_id"):
                        self.client.create_payload_index(name, field, models.PayloadSchemaType.KEYWORD)
            self._known.add(name)
        return name

    @staticmethod
    def _require_owner(doc_type: str, owner_id: str | None) -> None:
        if doc_type in PRIVATE_DOC_TYPES and not owner_id:
            raise InvalidTaskInputError(f"ownerId is required for {doc_type} documents (candidate isolation)")

    def index_document(
        self,
        doc_type: str,
        document_id: str,
        chunks: list[str],
        owner_id: str | None = None,
        metadata: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        doc_type = doc_type.upper()
        self._require_owner(doc_type, owner_id)
        chunks = [c for c in chunks if c and c.strip()]
        if not chunks:
            raise InvalidTaskInputError("No non-empty chunks to index")
        vectors = self.embedder.embed_documents(chunks)
        collection = self._ensure_collection(doc_type)
        now = datetime.now(timezone.utc).isoformat()
        points = []
        for index, (chunk, vector) in enumerate(zip(chunks, vectors)):
            point_id = str(uuid.uuid5(_NAMESPACE, f"{owner_id or '-'}:{doc_type}:{document_id}:{index}"))
            payload = {
                "owner_id": owner_id,
                "document_id": document_id,
                "doc_type": doc_type,
                "chunk_index": index,
                "text": chunk,
                "embedding_provider": self.embedder.name,
                "embedding_model": self.embedder.model,
                "indexed_at": now,
                "metadata": metadata or {},
            }
            points.append(models.PointStruct(id=point_id, vector=vector, payload=payload))
        with self._io:
            # Replace any previous version of this document (for this owner) first.
            self.delete_document(doc_type, document_id, owner_id)
            self.client.upsert(collection, points=points, wait=True)
        return {
            "collection": collection,
            "documentId": document_id,
            "vectorIds": [p.id for p in points],
            "vectorCount": len(points),
            "dimension": self.embedder.dimension,
            "embeddingProvider": self.embedder.name,
            "embeddingModel": self.embedder.model,
        }

    def delete_document(self, doc_type: str, document_id: str, owner_id: str | None = None) -> None:
        doc_type = doc_type.upper()
        name = self.collection_name(doc_type)
        must = [models.FieldCondition(key="document_id", match=models.MatchValue(value=document_id))]
        if owner_id:
            must.append(models.FieldCondition(key="owner_id", match=models.MatchValue(value=owner_id)))
        with self._io:
            if not self.client.collection_exists(name):
                return
            self.client.delete(name, points_selector=models.FilterSelector(filter=models.Filter(must=must)), wait=True)

    def search(
        self,
        doc_type: str,
        query: str,
        owner_id: str | None = None,
        limit: int = 5,
        document_ids: list[str] | None = None,
        min_score: float | None = None,
    ) -> list[SearchHit]:
        doc_type = doc_type.upper()
        self._require_owner(doc_type, owner_id)
        if not query or not query.strip():
            raise InvalidTaskInputError("Search query must not be empty")
        name = self.collection_name(doc_type)
        must: list[Any] = []
        if doc_type in PRIVATE_DOC_TYPES:
            must.append(models.FieldCondition(key="owner_id", match=models.MatchValue(value=owner_id)))
        if document_ids:
            must.append(models.FieldCondition(key="document_id", match=models.MatchAny(any=list(document_ids))))
        vector = self.embedder.embed_query(query)
        with self._io:
            if not self.client.collection_exists(name):
                return []
            response = self.client.query_points(
                name,
                query=vector,
                query_filter=models.Filter(must=must) if must else None,
                limit=max(1, min(int(limit), 50)),
                with_payload=True,
                score_threshold=min_score,
            )
        hits = []
        for point in response.points:
            payload = point.payload or {}
            # Defence in depth: never return a private chunk that belongs to someone else.
            if doc_type in PRIVATE_DOC_TYPES and payload.get("owner_id") != owner_id:
                continue
            hits.append(SearchHit(
                text=payload.get("text", ""),
                score=float(point.score),
                document_id=str(payload.get("document_id")),
                chunk_index=int(payload.get("chunk_index", 0)),
                doc_type=doc_type,
                metadata=payload.get("metadata") or {},
            ))
        return hits

    def count(self, doc_type: str) -> int:
        name = self.collection_name(doc_type.upper())
        with self._io:
            if not self.client.collection_exists(name):
                return 0
            return self.client.count(name, exact=True).count


def build_qdrant_client() -> QdrantClient:
    url = os.getenv("QDRANT_URL")
    if url:
        return QdrantClient(url=url, api_key=os.getenv("QDRANT_API_KEY") or None, timeout=30)
    path = os.getenv("QDRANT_PATH")
    if path:
        os.makedirs(path, exist_ok=True)
        return QdrantClient(path=path)
    raise ProviderUnavailableError(
        "Vector store is not configured: set QDRANT_URL (Qdrant server) or QDRANT_PATH (embedded local storage)",
        provider="qdrant",
    )


_store: VectorStore | None = None
_store_lock = threading.Lock()


def get_vector_store() -> VectorStore:
    global _store
    with _store_lock:
        if _store is None:
            _store = VectorStore(build_qdrant_client(), get_embedding_provider(),
                                 serialize=not os.getenv("QDRANT_URL"))
        return _store


def set_vector_store(store: VectorStore | None) -> None:
    global _store
    with _store_lock:
        _store = store
