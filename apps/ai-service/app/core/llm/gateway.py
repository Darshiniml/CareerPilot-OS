"""LLM gateway: provider selection, structured generation, validation and observability."""

from __future__ import annotations

import copy
import json
import logging
import threading
import time
from collections import defaultdict
from typing import Any, TypeVar

from pydantic import BaseModel, ValidationError

from app.core.llm.config import LLMConfig
from app.core.llm.errors import AIError, StructuredOutputError
from app.core.llm.prompting import PromptSpec
from app.core.llm.providers import ChatMessage, LLMProvider, LLMResult, build_llm_provider, parse_json_text

log = logging.getLogger("careerpilot.ai.gateway")

T = TypeVar("T", bound=BaseModel)

# Keywords that some providers' structured-output modes reject; validation is done by pydantic.
_UNSUPPORTED_SCHEMA_KEYS = {
    "title", "default", "examples", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
    "minLength", "maxLength", "pattern", "format", "minItems", "maxItems", "uniqueItems",
}


def model_to_schema(model: type[BaseModel]) -> dict[str, Any]:
    """Pydantic model -> self-contained JSON schema (refs inlined, strict objects)."""
    raw = model.model_json_schema()
    defs = raw.pop("$defs", {})

    def resolve(node: Any) -> Any:
        if isinstance(node, dict):
            if "$ref" in node:
                name = node["$ref"].split("/")[-1]
                return resolve(copy.deepcopy(defs[name]))
            out = {}
            for key, value in node.items():
                if key in _UNSUPPORTED_SCHEMA_KEYS:
                    continue
                if key == "properties" and isinstance(value, dict):
                    # property names are user-defined keys; never strip them
                    out[key] = {prop: resolve(sub) for prop, sub in value.items()}
                else:
                    out[key] = resolve(value)
            if out.get("type") == "object" and "properties" in out:
                out["additionalProperties"] = False
                out["required"] = list(out["properties"].keys())
            return out
        if isinstance(node, list):
            return [resolve(item) for item in node]
        return node

    return resolve(raw)


class _Metrics:
    def __init__(self):
        self._lock = threading.Lock()
        self.calls = 0
        self.failures = 0
        self.repairs = 0
        self.total_latency_ms = 0
        self.input_tokens = 0
        self.output_tokens = 0
        self.by_task: dict[str, dict[str, int]] = defaultdict(lambda: {"calls": 0, "failures": 0})
        self.by_error: dict[str, int] = defaultdict(int)

    def record(self, task: str, result: LLMResult | None, error: AIError | None, repaired: bool):
        with self._lock:
            self.calls += 1
            self.by_task[task]["calls"] += 1
            if repaired:
                self.repairs += 1
            if result is not None:
                self.total_latency_ms += result.latency_ms
                self.input_tokens += result.input_tokens or 0
                self.output_tokens += result.output_tokens or 0
            if error is not None:
                self.failures += 1
                self.by_task[task]["failures"] += 1
                self.by_error[error.code] += 1

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            successes = self.calls - self.failures
            return {
                "llmCalls": self.calls,
                "llmFailures": self.failures,
                "llmRepairs": self.repairs,
                "avgLatencyMs": round(self.total_latency_ms / successes, 1) if successes else None,
                "inputTokens": self.input_tokens,
                "outputTokens": self.output_tokens,
                "byTask": dict(self.by_task),
                "byError": dict(self.by_error),
            }


class GenerationOutcome(BaseModel):
    """Provenance attached to every AI-produced result."""

    provider: str
    model: str
    latencyMs: int
    inputTokens: int | None = None
    outputTokens: int | None = None
    repaired: bool = False


class LLMGateway:
    def __init__(self, config: LLMConfig | None = None, provider: LLMProvider | None = None):
        self._config = config
        self._provider = provider
        self._lock = threading.Lock()
        self.metrics = _Metrics()

    @property
    def config(self) -> LLMConfig:
        if self._config is None:
            self._config = LLMConfig.from_env()
        return self._config

    def provider(self) -> LLMProvider:
        """Build lazily so a misconfigured provider is reported per-request, not at import time."""
        with self._lock:
            if self._provider is None:
                self._provider = build_llm_provider(self.config)
            return self._provider

    def health(self) -> dict[str, Any]:
        try:
            return self.provider().health()
        except AIError as exc:
            return {"provider": self.config.provider, "model": self.config.model, "configured": False,
                    "status": "NOT_CONFIGURED", "error": exc.message}

    def generate(
        self,
        task: str,
        spec: PromptSpec,
        output_model: type[T],
        *,
        max_tokens: int | None = None,
        temperature: float | None = None,
    ) -> tuple[T, GenerationOutcome]:
        schema = model_to_schema(output_model)
        system = spec.system_prompt()
        messages = [ChatMessage("user", spec.user_message())]
        result: LLMResult | None = None
        repaired = False
        try:
            provider = self.provider()
            result = provider.complete_json(system, messages, schema, output_model.__name__,
                                            max_tokens=max_tokens, temperature=temperature)
            try:
                parsed = output_model.model_validate(parse_json_text(result.text))
            except (json.JSONDecodeError, ValidationError) as first_error:
                # One repair round: show the model its own output and the validation error.
                repaired = True
                log.warning("task=%s invalid structured output, attempting repair: %s", task, str(first_error)[:300])
                messages = messages + [
                    ChatMessage("assistant", result.text[:6000]),
                    ChatMessage("user", "Your previous reply did not satisfy the required JSON schema. "
                                        f"Validation error: {str(first_error)[:800]}\n"
                                        "Reply again with ONLY a corrected JSON object."),
                ]
                result = provider.complete_json(system, messages, schema, output_model.__name__,
                                                max_tokens=max_tokens, temperature=temperature)
                try:
                    parsed = output_model.model_validate(parse_json_text(result.text))
                except (json.JSONDecodeError, ValidationError) as second_error:
                    raise StructuredOutputError(
                        f"Model output for {task} failed schema validation",
                        provider=result.provider,
                        details={"error": str(second_error)[:800]},
                    ) from second_error
        except AIError as error:
            self.metrics.record(task, result, error, repaired)
            log.error("task=%s provider=%s failed: %s %s", task, self.config.provider, error.code, error.message)
            raise
        self.metrics.record(task, result, None, repaired)
        outcome = GenerationOutcome(
            provider=result.provider,
            model=result.model,
            latencyMs=result.latency_ms,
            inputTokens=result.input_tokens,
            outputTokens=result.output_tokens,
            repaired=repaired,
        )
        log.info("task=%s provider=%s model=%s latency_ms=%s repaired=%s",
                 task, outcome.provider, outcome.model, outcome.latencyMs, repaired)
        return parsed, outcome


_default_gateway: LLMGateway | None = None
_default_lock = threading.Lock()


def get_gateway() -> LLMGateway:
    global _default_gateway
    with _default_lock:
        if _default_gateway is None:
            _default_gateway = LLMGateway()
        return _default_gateway


def set_gateway(gateway: LLMGateway | None) -> None:
    """Used by tests and by configuration reloads."""
    global _default_gateway
    with _default_lock:
        _default_gateway = gateway
