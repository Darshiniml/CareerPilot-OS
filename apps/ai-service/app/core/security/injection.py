"""Deterministic prompt-injection detection for untrusted text.

This does not try to be a complete defence (no pattern list can be). It is one layer: tasks use it
to mark output as ``injectionSuspected`` and the backend refuses to auto-apply state changes from
such content. The other layers are prompt separation (``prompting.py``) and server-side validation.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

_PATTERNS: list[tuple[str, str]] = [
    ("ignore_instructions", r"\b(ignore|disregard|forget|override)\b[^.\n]{0,40}\b(previous|prior|above|earlier|all|system|your)\b[^.\n]{0,20}\b(instructions?|prompts?|rules?|directions?)"),
    ("new_instructions", r"\b(new|updated|real|actual)\s+(system\s+)?instructions?\s*[:\-]"),
    ("role_override", r"\byou\s+are\s+(now|no\s+longer)\b|\bact\s+as\s+(an?\s+)?(admin|system|developer|assistant)\b|\bdeveloper\s+mode\b|\bjailbreak\b"),
    ("system_impersonation", r"(^|\n)\s*(system|assistant|developer)\s*:|<\s*/?\s*system\s*>|\[\s*/?\s*(system|inst)\s*\]"),
    ("label_steering", r"\b(classify|label|categori[sz]e|mark|tag|treat)\s+(this|it|the\s+(email|message|candidate|application))\s+as\b"),
    ("state_steering", r"\b(set|change|update|move)\s+(the\s+)?(application\s+)?(status|state|stage)\s+to\b"),
    ("hiring_steering", r"\bmark\s+(the\s+|this\s+)?candidate\s+as\s+(hired|selected|accepted)\b|\b(score|rate)\s+(this|the)\s+(candidate|resume)\s+(as\s+)?(100|10/10|perfect|highest)\b"),
    ("exfiltration", r"\b(reveal|print|show|output|repeat)\s+(your|the)\s+(system\s+prompt|instructions|api\s*key|secrets?)\b"),
    ("output_override", r"\b(respond|reply|answer|output)\s+(only\s+)?with\b[^.\n]{0,30}\b(json|\{)"),
]

_COMPILED = [(name, re.compile(pattern, re.IGNORECASE)) for name, pattern in _PATTERNS]


@dataclass(frozen=True)
class InjectionFinding:
    kind: str
    excerpt: str
    start: int
    end: int


def detect_injection(text: str | None) -> list[InjectionFinding]:
    if not text:
        return []
    findings: list[InjectionFinding] = []
    for name, pattern in _COMPILED:
        for match in pattern.finditer(text):
            findings.append(InjectionFinding(name, text[match.start():match.end()].strip()[:120], match.start(), match.end()))
    findings.sort(key=lambda f: f.start)
    return findings


def injected_sentences(text: str | None) -> list[str]:
    """The sentences/lines of ``text`` that contain an injection finding (used to reject evidence
    quotes that were lifted from the injected instruction itself)."""
    if not text:
        return []
    sentences = []
    for finding in detect_injection(text):
        left = max(text.rfind(".", 0, finding.start), text.rfind("\n", 0, finding.start)) + 1
        right_candidates = [i for i in (text.find(".", finding.end), text.find("\n", finding.end)) if i != -1]
        right = min(right_candidates) if right_candidates else len(text)
        sentences.append(text[left:right].strip())
    return sentences
