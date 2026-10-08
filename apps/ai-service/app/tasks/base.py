"""Task registry and shared helpers for AI tasks executed via ``POST /api/v1/ai/execute``."""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any, Callable

from app.core.llm.errors import InvalidTaskInputError, UnsupportedTaskError
from app.core.llm.gateway import GenerationOutcome, LLMGateway


@dataclass
class TaskContext:
    task_id: str
    task_type: str
    payload: dict[str, Any]
    gateway: LLMGateway
    correlation_id: str | None = None


@dataclass
class TaskResult:
    result: dict[str, Any]
    provider: str
    model: str | None = None
    generation: GenerationOutcome | None = None
    method: str = "llm"  # "llm" | "deterministic" | "vector-search" | "hybrid"


TaskHandler = Callable[[TaskContext], TaskResult]
_REGISTRY: dict[str, TaskHandler] = {}


def task(*names: str):
    def register(fn: TaskHandler) -> TaskHandler:
        for name in names:
            _REGISTRY[name] = fn
        return fn

    return register


def get_handler(task_type: str) -> TaskHandler:
    handler = _REGISTRY.get(task_type)
    if handler is None:
        raise UnsupportedTaskError(
            f"Unsupported taskType '{task_type}'", details={"supported": sorted(_REGISTRY.keys())}
        )
    return handler


def registered_tasks() -> list[str]:
    return sorted(_REGISTRY.keys())


def llm_result(result: dict[str, Any], outcome: GenerationOutcome, method: str = "llm") -> TaskResult:
    return TaskResult(result=result, provider=outcome.provider, model=outcome.model, generation=outcome, method=method)


# ------------------------------------------------------------------------------------------
# Payload helpers
# ------------------------------------------------------------------------------------------

MAX_INPUT_CHARS = 24000

# Paragraphs that carry the facts a posting/page analysis needs, and boilerplate that does not.
_RELEVANT = re.compile(
    r"requirement|qualification|must have|nice to have|preferred|experience|skill|you have|you will|"
    r"you'll|responsibilit|what you|about you|about the role|the role|tech stack|technolog|stack|degree|"
    r"salary|compensation|pay range|\$|€|£|₹|lpa|per year|remote|hybrid|on-?site|location|visa|relocat|"
    r"deadline|apply by|employment type|full[- ]time|part[- ]time|contract|intern", re.IGNORECASE)
_BOILERPLATE = re.compile(
    r"equal opportunity|regardless of (race|gender|age)|reasonable accommodation|e-?verify|privacy (notice|policy)|"
    r"we do not accept unsolicited|recruitment agencies|by submitting your application", re.IGNORECASE)


def focus_text(text: str, limit: int) -> str:
    """Reduce a long document to at most ``limit`` characters without rewriting it.

    Keeps the opening (title, summary), then the paragraphs that mention requirements, skills,
    responsibilities, compensation or location, in their original order; drops legal boilerplate.
    Every kept character is verbatim source text, so grounding checks remain valid.
    """
    if len(text) <= limit:
        return text
    paragraphs = [p.strip() for p in re.split(r"\n\s*\n", text) if p.strip()]
    if len(paragraphs) < 3:
        paragraphs = [p.strip() for p in text.splitlines() if p.strip()]
    head_budget = limit // 4
    kept: list[tuple[int, str]] = []
    used = 0
    for i, para in enumerate(paragraphs):  # opening section
        if used + len(para) > head_budget:
            break
        kept.append((i, para))
        used += len(para) + 2
    head_count = len(kept)
    for i, para in enumerate(paragraphs[head_count:], start=head_count):
        if _BOILERPLATE.search(para) or not _RELEVANT.search(para):
            continue
        piece = para if used + len(para) <= limit else para[: max(0, limit - used)]
        if len(piece) < 40:
            break
        kept.append((i, piece))
        used += len(piece) + 2
        if used >= limit:
            break
    if used < 200:  # nothing recognisable: fall back to the opening of the document
        return text[:limit]
    return "\n\n".join(p for _, p in sorted(kept))


def require_text(payload: dict[str, Any], key: str, label: str | None = None, max_chars: int = MAX_INPUT_CHARS) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value.strip():
        raise InvalidTaskInputError(f"payload.{key} ({label or key}) is required and must be non-empty text")
    if len(value) > max_chars:
        raise InvalidTaskInputError(
            f"payload.{key} is {len(value)} characters; the maximum is {max_chars}. "
            "Split the document or index it for retrieval instead of sending it whole."
        )
    return value


def optional_text(payload: dict[str, Any], key: str, max_chars: int = MAX_INPUT_CHARS) -> str | None:
    value = payload.get(key)
    if value is None:
        return None
    if not isinstance(value, str):
        value = str(value)
    value = value.strip()
    if not value:
        return None
    if len(value) > max_chars:
        raise InvalidTaskInputError(f"payload.{key} exceeds {max_chars} characters")
    return value


def optional_dict(payload: dict[str, Any], key: str) -> dict[str, Any]:
    value = payload.get(key)
    if value is None:
        return {}
    if not isinstance(value, dict):
        raise InvalidTaskInputError(f"payload.{key} must be an object")
    return value


# ------------------------------------------------------------------------------------------
# Grounding helpers: model output is only kept when the source text supports it.
# ------------------------------------------------------------------------------------------

_WS = re.compile(r"\s+")


def normalize(text: str | None) -> str:
    return _WS.sub(" ", (text or "")).strip().lower()


def appears_in(value: str | None, source: str) -> bool:
    """True when ``value`` occurs in ``source`` (case/whitespace-insensitive)."""
    if not value or not str(value).strip():
        return False
    return normalize(str(value)) in normalize(source)


def grounded_or_none(value: str | None, source: str) -> str | None:
    return value.strip() if value and appears_in(value, source) else None


def grounded_list(values: list[str] | None, source: str) -> list[str]:
    seen, out = set(), []
    for v in values or []:
        if isinstance(v, str) and appears_in(v, source) and normalize(v) not in seen:
            seen.add(normalize(v))
            out.append(v.strip())
    return out


def clamp01(value: float | None) -> float | None:
    if value is None:
        return None
    try:
        return max(0.0, min(1.0, float(value)))
    except (TypeError, ValueError):
        return None


def extracted(value: Any, confidence: float | None, source: str = "llm") -> dict[str, Any]:
    """The ``{value, confidence, source}`` shape used by the backend's ExtractedValueDto."""
    if value is None or value == [] or value == "":
        return {"value": None, "confidence": 0.0, "source": "not-found"}
    return {"value": value, "confidence": round(clamp01(confidence) or 0.0, 2), "source": source}
