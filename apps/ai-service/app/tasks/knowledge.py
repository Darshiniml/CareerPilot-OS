"""Knowledge tasks: chunking, embedding, indexing, semantic search and RAG answering."""

from __future__ import annotations

import re
from typing import Any

from pydantic import BaseModel, Field

from app.core.embedding_provider import get_embedding_provider
from app.core.llm.errors import InvalidTaskInputError
from app.core.llm.prompting import PromptSpec, RetrievedChunk
from app.core.vector_store import PRIVATE_DOC_TYPES, get_vector_store
from app.tasks.base import TaskContext, TaskResult, llm_result, optional_dict, optional_text, require_text, task

DEFAULT_CHUNK_CHARS = 900
DEFAULT_OVERLAP_CHARS = 150
MAX_DOCUMENT_CHARS = 200_000
RAG_MIN_SCORE = 0.35


def chunk_text(text: str, size: int = DEFAULT_CHUNK_CHARS, overlap: int = DEFAULT_OVERLAP_CHARS) -> list[str]:
    """Paragraph-aware chunking: packs paragraphs/sentences up to ``size`` chars with a small overlap."""
    size = max(200, min(int(size), 4000))
    overlap = max(0, min(int(overlap), size // 2))
    paragraphs = [p.strip() for p in re.split(r"\n\s*\n|\r\n\s*\r\n", text) if p.strip()]
    units: list[str] = []
    for p in paragraphs:
        if len(p) <= size:
            units.append(p)
        else:
            units.extend(s.strip() for s in re.split(r"(?<=[.!?])\s+|\n", p) if s.strip())
    chunks: list[str] = []
    current = ""
    for unit in units:
        while len(unit) > size:  # a single very long sentence/line
            chunks.append(unit[:size])
            unit = unit[size - overlap:]
        if current and len(current) + len(unit) + 1 > size:
            chunks.append(current)
            tail = ""
            if overlap:
                tail = current[-overlap:]
                tail = tail[tail.find(" ") + 1:] if " " in tail else tail  # start the overlap on a word
            current = f"{tail}\n{unit}" if tail else unit
        else:
            current = f"{current}\n{unit}" if current else unit
    if current.strip():
        chunks.append(current)
    return [c.strip() for c in chunks if c.strip()]


def _owner(ctx: TaskContext) -> str | None:
    owner = ctx.payload.get("ownerId")
    return str(owner) if owner else None


@task("CHUNK_DOCUMENT")
def chunk_document(ctx: TaskContext) -> TaskResult:
    text = require_text(ctx.payload, "content", max_chars=MAX_DOCUMENT_CHARS)
    chunks = chunk_text(text, ctx.payload.get("chunkSize") or DEFAULT_CHUNK_CHARS,
                        ctx.payload.get("chunkOverlap") or DEFAULT_OVERLAP_CHARS)
    return TaskResult(result={"chunks": [{"chunkNumber": i + 1, "text": c, "tokenEstimate": len(c.split())}
                                         for i, c in enumerate(chunks)]},
                      provider="chunker", method="deterministic")


@task("GENERATE_EMBEDDINGS")
def generate_embeddings(ctx: TaskContext) -> TaskResult:
    texts = ctx.payload.get("texts")
    if not texts:
        single = optional_text(ctx.payload, "chunkText")
        texts = [single] if single else []
    if not isinstance(texts, list) or not texts or not all(isinstance(t, str) and t.strip() for t in texts):
        raise InvalidTaskInputError("payload.texts (non-empty strings) or payload.chunkText is required")
    embedder = get_embedding_provider()
    vectors = embedder.embed_documents(texts)
    result: dict[str, Any] = {
        "vector_dimension": len(vectors[0]),
        "count": len(vectors),
        "embeddingProvider": embedder.name,
        "embeddingModel": embedder.model,
    }
    if ctx.payload.get("returnVectors"):
        result["vectors"] = vectors
    return TaskResult(result=result, provider=embedder.name, model=embedder.model, method="embedding")


@task("DOCUMENT_INDEX")
def index_document(ctx: TaskContext) -> TaskResult:
    doc_type = str(ctx.payload.get("documentType") or "").upper()
    document_id = str(ctx.payload.get("documentId") or "")
    if not doc_type or not document_id:
        raise InvalidTaskInputError("payload.documentType and payload.documentId are required")
    text = require_text(ctx.payload, "content", max_chars=MAX_DOCUMENT_CHARS)
    chunks = chunk_text(text, ctx.payload.get("chunkSize") or DEFAULT_CHUNK_CHARS,
                        ctx.payload.get("chunkOverlap") or DEFAULT_OVERLAP_CHARS)
    store = get_vector_store()
    info = store.index_document(doc_type, document_id, chunks, owner_id=_owner(ctx),
                                metadata=optional_dict(ctx.payload, "metadata"))
    info["chunks"] = len(chunks)
    info["chunkTexts"] = chunks  # lets the backend persist chunk records that match the stored vectors
    return TaskResult(result=info, provider="qdrant", model=info["embeddingModel"], method="vector-index")


@task("DOCUMENT_DELETE")
def delete_document(ctx: TaskContext) -> TaskResult:
    doc_type = str(ctx.payload.get("documentType") or "").upper()
    document_id = str(ctx.payload.get("documentId") or "")
    if not doc_type or not document_id:
        raise InvalidTaskInputError("payload.documentType and payload.documentId are required")
    owner = _owner(ctx)
    if doc_type in PRIVATE_DOC_TYPES and not owner:
        raise InvalidTaskInputError("ownerId is required to delete private documents")
    get_vector_store().delete_document(doc_type, document_id, owner)
    return TaskResult(result={"deleted": True, "documentId": document_id}, provider="qdrant", method="vector-index")


@task("SEMANTIC_SEARCH", "JOB_SEARCH", "COMPANY_SEARCH", "RESUME_SEARCH")
def semantic_search(ctx: TaskContext) -> TaskResult:
    defaults = {"JOB_SEARCH": "JOB", "COMPANY_SEARCH": "COMPANY", "RESUME_SEARCH": "RESUME"}
    doc_type = str(ctx.payload.get("documentType") or defaults.get(ctx.task_type, "")).upper()
    if not doc_type:
        raise InvalidTaskInputError("payload.documentType is required")
    query = require_text(ctx.payload, "query", max_chars=2000)
    limit = int(ctx.payload.get("limit") or ctx.payload.get("size") or 5)
    hits = get_vector_store().search(doc_type, query, owner_id=_owner(ctx), limit=limit,
                                     document_ids=ctx.payload.get("documentIds"),
                                     min_score=ctx.payload.get("minScore"))
    return TaskResult(result={"results": [h.to_dict() for h in hits], "documentType": doc_type},
                      provider="qdrant", method="vector-search")


class RagAnswerOutput(BaseModel):
    answer: str
    usedSources: list[str] = Field(default_factory=list)
    insufficientContext: bool


@task("RAG_ANSWER")
def rag_answer(ctx: TaskContext) -> TaskResult:
    question = require_text(ctx.payload, "question", max_chars=4000)
    doc_types = [str(t).upper() for t in (ctx.payload.get("documentTypes") or ["RESUME"])]
    owner = _owner(ctx)
    store = get_vector_store()
    hits = []
    for dt in doc_types:
        hits.extend(store.search(dt, question, owner_id=owner, limit=int(ctx.payload.get("limit") or 5),
                                 min_score=RAG_MIN_SCORE))
    hits.sort(key=lambda h: h.score, reverse=True)
    hits = hits[: int(ctx.payload.get("limit") or 6)]
    chunks = [RetrievedChunk(f"{h.doc_type}:{h.document_id}#{h.chunk_index}", h.text, h.score) for h in hits]
    if not chunks:
        return TaskResult(result={"answer": None, "insufficientContext": True, "usedSources": [], "retrieved": []},
                          provider="qdrant", method="rag")
    spec = PromptSpec(
        "Answer the question using only the retrieved data. Name the sources you used (their source labels) in "
        "usedSources. If the retrieved data does not answer the question, set insufficientContext=true and say so.",
        trusted_context=optional_dict(ctx.payload, "context") or None,
        retrieved=chunks,
        user_request=question,
    )
    out, outcome = ctx.gateway.generate("RAG_ANSWER", spec, RagAnswerOutput, max_tokens=1200)
    labels = {c.source for c in chunks}
    result = out.model_dump()
    result["usedSources"] = [s for s in out.usedSources if s in labels]
    result["retrieved"] = [h.to_dict() for h in hits]
    return llm_result(result, outcome, method="rag")
