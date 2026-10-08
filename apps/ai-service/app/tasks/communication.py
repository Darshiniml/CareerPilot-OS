"""HR communication intelligence: classification and summarisation.

Classification = real LLM call + deterministic guards. The model is never the source of truth:

1. Output must be one of the fixed labels (schema enum + pydantic Literal).
2. Evidence quotes are kept only if they occur verbatim in the email and are not lifted from an
   injected instruction.
3. The rule-based phrase classifier (``app.communication.classifier``) runs on the email with
   injected sentences removed. If it confidently disagrees with the model, the result is UNKNOWN.
4. Any detected injection attempt caps confidence below the backend's auto-transition threshold.

The response keeps the M22.3 contract consumed by ``ClassificationResultParser``:
``classification, confidence, evidence (string), reason, signals`` and never contains server-owned
identifiers.
"""

from __future__ import annotations

from typing import Any, Literal, Optional

from pydantic import BaseModel, Field

from app.communication.classifier import HrCommunicationClassifier
from app.core.llm.prompting import PromptSpec, UntrustedPart
from app.core.prompt_manager import PromptManager
from app.core.security.injection import detect_injection, injected_sentences
from app.tasks.base import TaskContext, TaskResult, appears_in, llm_result, optional_dict, task

PROMPT_VERSION = "v2"
# Must stay below the backend's auto-transition threshold (0.5) so suspicious emails never move state.
INJECTION_CONFIDENCE_CAP = 0.45
RULE_CONFLICT_MIN_CONFIDENCE = 0.6

Label = Literal[
    "APPLICATION_RECEIVED", "APPLICATION_UNDER_REVIEW", "ASSESSMENT_REQUEST", "INTERVIEW_INVITATION",
    "INTERVIEW_RESCHEDULED", "ADDITIONAL_INFORMATION_REQUESTED", "REJECTION", "OFFER", "UNKNOWN",
]

_FORBIDDEN_KEYS = {"candidateId", "applicationId", "matchedApplicationId", "applicationState", "actorId"}


class ClassificationOutput(BaseModel):
    evidence: list[str] = Field(default_factory=list)
    reason: str
    injectionAttemptDetected: bool
    classification: Label
    confidence: float


def _load_instructions() -> str:
    return PromptManager().load_prompt_asset("communication", PROMPT_VERSION, "system.txt").template


def _strip_sentences(text: str, sentences: list[str]) -> str:
    for s in sentences:
        if s:
            text = text.replace(s, " ")
    return text


def guard_classification(
    model_out: ClassificationOutput, subject: str, body: str
) -> dict[str, Any]:
    """Apply the deterministic guards to a model classification. Pure function (unit-tested)."""
    message = f"{subject}\n{body}"
    findings = detect_injection(message)
    bad_sentences = injected_sentences(message)
    injection = bool(findings) or model_out.injectionAttemptDetected

    quotes = []
    for q in model_out.evidence:
        if not q or not appears_in(q, message):
            continue
        if any(appears_in(q, s) or appears_in(s, q) for s in bad_sentences):
            continue
        quotes.append(q.strip())

    clean_text = _strip_sentences(message, bad_sentences)
    rules = HrCommunicationClassifier(prompt_version="rules").classify(subject="", body=clean_text)
    rule_label, rule_conf = rules["classification"], float(rules["confidence"])

    label = model_out.classification
    confidence = max(0.0, min(1.0, float(model_out.confidence)))
    reason = model_out.reason.strip()
    notes: list[str] = []

    if label != "UNKNOWN" and not quotes:
        notes.append("no verifiable evidence quote from the email")
        label, confidence = "UNKNOWN", min(confidence, 0.2)
    elif label != "UNKNOWN" and rule_label != "UNKNOWN" and rule_label != label and rule_conf >= RULE_CONFLICT_MIN_CONFIDENCE:
        notes.append(f"model label {label} conflicts with evidence signals ({rule_label})")
        label, confidence = "UNKNOWN", min(confidence, 0.3)
    elif label != "UNKNOWN" and rule_label == label:
        confidence = max(confidence, rule_conf)
    confidence = min(confidence, 0.97)

    if injection:
        notes.append("possible prompt-injection content detected; result cannot drive automatic state changes")
        confidence = min(confidence, INJECTION_CONFIDENCE_CAP)

    if notes:
        reason = f"{reason} [guard: {'; '.join(notes)}]" if reason else f"[guard: {'; '.join(notes)}]"
    return {
        "classification": label,
        "confidence": round(confidence, 2),
        "evidence": "; ".join(f'"{q}"' for q in quotes) if quotes else "no verifiable evidence quoted from the message",
        "reason": reason,
        "signals": rules.get("signals", []),
        "evidenceQuotes": quotes,
        "modelClassification": model_out.classification,
        "modelConfidence": round(max(0.0, min(1.0, float(model_out.confidence))), 2),
        "ruleClassification": rule_label,
        "injectionSuspected": injection,
        "injectionFindings": [f.kind for f in findings],
        "promptVersion": PROMPT_VERSION,
    }


@task("HR_COMMUNICATION_CLASSIFY")
def classify(ctx: TaskContext) -> TaskResult:
    subject = str(ctx.payload.get("subject") or "")
    body = str(ctx.payload.get("body") or "")
    sender = str(ctx.payload.get("sender") or "")
    if not subject.strip() and not body.strip():
        from app.core.llm.errors import InvalidTaskInputError

        raise InvalidTaskInputError("subject or body is required")
    app_context = optional_dict(ctx.payload, "applicationContext")
    trusted = {k: v for k, v in app_context.items() if k not in _FORBIDDEN_KEYS} if app_context else None
    spec = PromptSpec(
        _load_instructions(),
        trusted_context={"applicationContext": trusted} if trusted else None,
        untrusted=[UntrustedPart("email", f"From: {sender}\nSubject: {subject}\n\n{body}")],
    )
    out, outcome = ctx.gateway.generate("HR_COMMUNICATION_CLASSIFY", spec, ClassificationOutput,
                                        max_tokens=600, temperature=0.0)
    result = guard_classification(out, subject, body)
    for key in _FORBIDDEN_KEYS:
        result.pop(key, None)
    return llm_result(result, outcome, method="llm+guards")


class _Date(BaseModel):
    quote: str
    meaning: str


class SummaryOutput(BaseModel):
    summary: str
    keyPoints: list[str] = Field(default_factory=list)
    actionItems: list[str] = Field(default_factory=list)
    mentionedDates: list[_Date] = Field(default_factory=list)
    requestedDocuments: list[str] = Field(default_factory=list)
    contactName: Optional[str] = None
    injectionAttemptDetected: bool = False


@task("HR_COMMUNICATION_SUMMARIZE")
def summarize(ctx: TaskContext) -> TaskResult:
    subject = str(ctx.payload.get("subject") or "")
    body = str(ctx.payload.get("body") or "")
    sender = str(ctx.payload.get("sender") or "")
    if not body.strip():
        from app.core.llm.errors import InvalidTaskInputError

        raise InvalidTaskInputError("body is required")
    spec = PromptSpec(
        "Summarise one recruiting email for the candidate who received it. List what the candidate is "
        "asked to do (actionItems), any dates/times exactly as written (mentionedDates.quote) with what "
        "they refer to, and any documents requested. contactName only if the email is signed by a named "
        "person. Never invent dates, names or requests that are not in the email.",
        untrusted=[UntrustedPart("email", f"From: {sender}\nSubject: {subject}\n\n{body}")],
    )
    out, outcome = ctx.gateway.generate("HR_COMMUNICATION_SUMMARIZE", spec, SummaryOutput, max_tokens=900)
    message = f"{subject}\n{body}"
    result = out.model_dump()
    result["mentionedDates"] = [d.model_dump() for d in out.mentionedDates if appears_in(d.quote, message)]
    result["contactName"] = out.contactName if out.contactName and appears_in(out.contactName, message) else None
    result["injectionAttemptDetected"] = out.injectionAttemptDetected or bool(detect_injection(message))
    return llm_result(result, outcome)
