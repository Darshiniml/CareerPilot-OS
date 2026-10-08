"""CareerPilot AI service.

Inference/intelligence service called only by the CareerPilot backend. Every non-health endpoint
requires the shared service token (``AI_SERVICE_TOKEN``); if it is not configured the service
refuses requests (fail closed). Browsers never talk to this service directly.
"""

from __future__ import annotations

import base64
import hmac
import io
import logging
import os
import time
import uuid
from typing import Any, Dict, Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse, PlainTextResponse
from pydantic import BaseModel

from app.core.embedding_provider import get_embedding_provider, registered_embedding_providers
from app.core.llm.config import SUPPORTED_LLM_PROVIDERS, EmbeddingConfig, LLMConfig
from app.core.llm.errors import AIError, InvalidTaskInputError
from app.core.llm.gateway import get_gateway
from app.core.vector_store import get_vector_store
from app.tasks import career, communication, job_company, knowledge, resume  # noqa: F401  (register tasks)
from app.tasks.base import TaskContext, get_handler, registered_tasks

logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"),
                    format="%(asctime)s %(levelname)s %(name)s - %(message)s")
log = logging.getLogger("careerpilot.ai")

app = FastAPI(
    title="CareerPilot AI Service",
    description="LLM, embedding, retrieval and document intelligence for the CareerPilot backend.",
    version="2.0.0",
    docs_url="/api/v1/ai/docs",
    openapi_url="/api/v1/ai/openapi.json",
)

_counters = {"requests": 0, "completed": 0, "failed": 0, "latency_ms_total": 0}
MAX_UPLOAD_BYTES = 10 * 1024 * 1024


# ------------------------------------------------------------------------------------------
# Service authentication
# ------------------------------------------------------------------------------------------


def require_service_token(
    authorization: Optional[str] = Header(default=None),
    x_service_token: Optional[str] = Header(default=None),
) -> None:
    expected = os.getenv("AI_SERVICE_TOKEN", "")
    if not expected:
        raise HTTPException(status_code=503, detail={"code": "AI_SERVICE_TOKEN_NOT_CONFIGURED",
                                                     "message": "AI_SERVICE_TOKEN is not configured; refusing requests"})
    supplied = x_service_token
    if not supplied and authorization and authorization.lower().startswith("bearer "):
        supplied = authorization[7:].strip()
    if not supplied or not hmac.compare_digest(supplied.encode(), expected.encode()):
        raise HTTPException(status_code=401, detail={"code": "UNAUTHORIZED", "message": "invalid service token"})


@app.exception_handler(AIError)
async def ai_error_handler(_: Request, exc: AIError):
    return JSONResponse(status_code=exc.http_status, content={"error": exc.to_dict()})


# ------------------------------------------------------------------------------------------
# Task execution
# ------------------------------------------------------------------------------------------


class ExecuteTaskRequest(BaseModel):
    taskId: Optional[str] = None
    taskType: str
    payload: Optional[Dict[str, Any]] = None
    metadata: Optional[Dict[str, Any]] = None


class ExecuteTaskResponse(BaseModel):
    taskId: str
    status: str
    provider: str
    executionTimeMs: int
    result: Dict[str, Any]
    metadata: Dict[str, Any]


@app.post("/api/v1/ai/execute", response_model=ExecuteTaskResponse, dependencies=[Depends(require_service_token)])
def execute_task(request: ExecuteTaskRequest, x_correlation_id: Optional[str] = Header(default=None)):
    started = time.monotonic()
    _counters["requests"] += 1
    task_id = request.taskId or str(uuid.uuid4())
    try:
        handler = get_handler(request.taskType)
        ctx = TaskContext(task_id=task_id, task_type=request.taskType, payload=request.payload or {},
                          gateway=get_gateway(), correlation_id=x_correlation_id)
        outcome = handler(ctx)
    except AIError as exc:
        _counters["failed"] += 1
        log.warning("task=%s id=%s correlation=%s failed code=%s", request.taskType, task_id, x_correlation_id, exc.code)
        raise
    elapsed = int((time.monotonic() - started) * 1000)
    _counters["completed"] += 1
    _counters["latency_ms_total"] += elapsed
    metadata: Dict[str, Any] = {"method": outcome.method, "model": outcome.model, "taskType": request.taskType}
    if outcome.generation is not None:
        metadata["generation"] = outcome.generation.model_dump()
    return ExecuteTaskResponse(taskId=task_id, status="COMPLETED", provider=outcome.provider,
                               executionTimeMs=elapsed, result=outcome.result, metadata=metadata)


# ------------------------------------------------------------------------------------------
# Document text extraction (resume/job uploads)
# ------------------------------------------------------------------------------------------


class ExtractTextRequest(BaseModel):
    fileName: str
    contentType: Optional[str] = None
    contentBase64: str


def extract_text(file_name: str, content_type: str | None, data: bytes) -> tuple[str, str]:
    name = file_name.lower()
    ctype = (content_type or "").lower()
    if name.endswith(".pdf") or "pdf" in ctype:
        from pypdf import PdfReader

        reader = PdfReader(io.BytesIO(data))
        if reader.is_encrypted:
            raise InvalidTaskInputError("The PDF is encrypted and cannot be read")
        text = "\n".join((page.extract_text() or "") for page in reader.pages)
        return text, "pdf"
    if name.endswith(".docx") or "wordprocessingml" in ctype:
        import docx

        document = docx.Document(io.BytesIO(data))
        parts = [p.text for p in document.paragraphs]
        for table in document.tables:
            for row in table.rows:
                parts.append(" | ".join(cell.text for cell in row.cells))
        return "\n".join(parts), "docx"
    if name.endswith((".txt", ".md")) or ctype.startswith("text/"):
        return data.decode("utf-8", errors="replace"), "text"
    raise InvalidTaskInputError(f"Unsupported file type for text extraction: {file_name}")


@app.post("/api/v1/ai/documents/extract-text", dependencies=[Depends(require_service_token)])
def extract_text_endpoint(request: ExtractTextRequest):
    try:
        data = base64.b64decode(request.contentBase64, validate=True)
    except Exception as exc:  # noqa: BLE001
        raise InvalidTaskInputError("contentBase64 is not valid base64") from exc
    if len(data) > MAX_UPLOAD_BYTES:
        raise InvalidTaskInputError("File exceeds 10 MB")
    try:
        text, kind = extract_text(request.fileName, request.contentType, data)
    except AIError:
        raise
    except Exception as exc:  # noqa: BLE001
        raise InvalidTaskInputError(f"Could not read the document: {exc.__class__.__name__}") from exc
    text = "\n".join(line.rstrip() for line in text.splitlines()).strip()
    return {
        "text": text,
        "format": kind,
        "characters": len(text),
        "words": len(text.split()),
        # Scanned PDFs have no text layer; we report it rather than guessing.
        "textLayerFound": len(text.split()) >= 20,
    }


# ------------------------------------------------------------------------------------------
# Health, readiness, providers, metrics
# ------------------------------------------------------------------------------------------


@app.get("/api/v1/ai/health")
def health():
    cfg = LLMConfig.from_env()
    emb = EmbeddingConfig.from_env()
    return {"status": "healthy", "llm_provider": cfg.provider, "llm_model": cfg.model,
            "embedding_provider": emb.provider, "embedding_model": emb.model}


def _vector_store_status() -> dict[str, Any]:
    try:
        store = get_vector_store()
        return {"status": "UP", "mode": "server" if os.getenv("QDRANT_URL") else "embedded",
                "dimension": store.embedder.dimension}
    except AIError as exc:
        return {"status": "DOWN", "error": exc.message}
    except Exception as exc:  # noqa: BLE001
        return {"status": "DOWN", "error": exc.__class__.__name__}


def _embedding_status() -> dict[str, Any]:
    try:
        return get_embedding_provider().health()
    except AIError as exc:
        return {"status": "NOT_CONFIGURED", "error": exc.message}


@app.get("/api/v1/ai/ready")
def ready():
    llm = get_gateway().health()
    embedding = _embedding_status()
    ready_ = llm.get("status") == "UP" and embedding.get("status") == "UP"
    body = {"status": "ready" if ready_ else "not_ready", "llm": llm, "embedding": embedding,
            "serviceAuthConfigured": bool(os.getenv("AI_SERVICE_TOKEN")), "timestamp": time.time()}
    return JSONResponse(status_code=200 if ready_ else 503, content=body)


@app.get("/api/v1/ai/providers", dependencies=[Depends(require_service_token)])
def providers():
    return {
        "llm": get_gateway().health(),
        "embedding": _embedding_status(),
        "vectorStore": _vector_store_status(),
        "supportedLlmProviders": list(SUPPORTED_LLM_PROVIDERS),
        "supportedEmbeddingProviders": registered_embedding_providers(),
        "tasks": registered_tasks(),
    }


@app.get("/api/v1/ai/metrics", dependencies=[Depends(require_service_token)])
def metrics():
    completed = _counters["completed"]
    return {
        "total_requests": _counters["requests"],
        "completed_tasks": completed,
        "failed_tasks": _counters["failed"],
        "avg_latency_ms": round(_counters["latency_ms_total"] / completed, 1) if completed else None,
        "llm": get_gateway().metrics.snapshot(),
    }


@app.get("/api/v1/ai/metrics/prometheus", response_class=PlainTextResponse)
def prometheus_metrics():
    snap = get_gateway().metrics.snapshot()
    lines = [
        "# TYPE careerpilot_ai_requests_total counter",
        f"careerpilot_ai_requests_total {_counters['requests']}",
        "# TYPE careerpilot_ai_tasks_failed_total counter",
        f"careerpilot_ai_tasks_failed_total {_counters['failed']}",
        "# TYPE careerpilot_ai_llm_calls_total counter",
        f"careerpilot_ai_llm_calls_total {snap['llmCalls']}",
        "# TYPE careerpilot_ai_llm_failures_total counter",
        f"careerpilot_ai_llm_failures_total {snap['llmFailures']}",
        "# TYPE careerpilot_ai_llm_tokens_total counter",
        f'careerpilot_ai_llm_tokens_total{{direction="input"}} {snap["inputTokens"]}',
        f'careerpilot_ai_llm_tokens_total{{direction="output"}} {snap["outputTokens"]}',
    ]
    for code, count in snap["byError"].items():
        lines.append(f'careerpilot_ai_llm_errors_total{{code="{code}"}} {count}')
    return "\n".join(lines) + "\n"
