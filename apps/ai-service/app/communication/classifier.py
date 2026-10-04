"""Deterministic, evidence-based HR communication classifier.

Mirrors the existing rule-based AI convention in this service (see
``app.job.insights.JobInsightsService`` and the ``insights-classifier`` provider):
classification is produced from concrete signals present in the supplied message,
never by obeying instructions embedded inside the message body.

The message body/subject are UNTRUSTED input. They are only ever scanned for
evidence phrases; they are never interpreted as commands. The classifier returns
ONLY ``classification``, ``confidence``, ``evidence``, ``reason`` and ``signals``.
Server-owned identifiers (candidateId, applicationId, matchedApplicationId,
applicationState, actorId) are never produced here and cannot be injected.
"""

from typing import Any, Dict, List, Tuple

# Fixed vocabulary — must match the backend CommunicationClassification enum.
SUPPORTED_CLASSIFICATIONS = [
    "APPLICATION_RECEIVED",
    "APPLICATION_UNDER_REVIEW",
    "ASSESSMENT_REQUEST",
    "INTERVIEW_INVITATION",
    "INTERVIEW_RESCHEDULED",
    "ADDITIONAL_INFORMATION_REQUESTED",
    "REJECTION",
    "OFFER",
    "UNKNOWN",
]

# Weighted evidence phrases per class. Strong phrases carry more weight.
_SIGNALS: Dict[str, List[Tuple[str, float]]] = {
    "OFFER": [
        ("we are pleased to offer", 0.6),
        ("pleased to extend", 0.6),
        ("offer letter", 0.6),
        ("extend an offer", 0.6),
        ("formal offer", 0.5),
        ("job offer", 0.5),
        ("starting salary", 0.45),
        ("compensation package", 0.45),
        ("annual salary", 0.4),
        ("we would like to offer you", 0.6),
        ("offer of employment", 0.6),
        ("acceptance deadline", 0.35),
        ("start date", 0.25),
    ],
    "REJECTION": [
        ("not moving forward", 0.6),
        ("will not be moving forward", 0.6),
        ("we regret to inform", 0.6),
        ("not been selected", 0.55),
        ("decided to move forward with another", 0.6),
        ("pursue other candidates", 0.5),
        ("not a match at this time", 0.5),
        ("unfortunately", 0.3),
        ("we will not be proceeding", 0.6),
        ("thank you for your interest, however", 0.45),
        ("rejected", 0.5),
        ("position has been filled", 0.45),
    ],
    "INTERVIEW_RESCHEDULED": [
        ("reschedule", 0.6),
        ("rescheduling", 0.6),
        ("postpone", 0.5),
        ("move the interview", 0.6),
        ("change the time", 0.45),
        ("new time", 0.4),
        ("different time slot", 0.5),
        ("need to reschedule our interview", 0.7),
    ],
    "INTERVIEW_INVITATION": [
        ("would like to invite you to interview", 0.7),
        ("interview invitation", 0.65),
        ("schedule an interview", 0.6),
        ("book an interview", 0.55),
        ("interview loop", 0.5),
        ("meet the team", 0.45),
        ("your availability", 0.4),
        ("interview", 0.4),
        ("zoom", 0.25),
        ("teams call", 0.3),
        ("on-site interview", 0.5),
        ("onsite", 0.3),
        ("calendar link", 0.35),
        ("select a time slot", 0.5),
    ],
    "ASSESSMENT_REQUEST": [
        ("complete the assessment", 0.65),
        ("technical assessment", 0.6),
        ("coding challenge", 0.6),
        ("take-home assignment", 0.6),
        ("online assessment", 0.6),
        ("psychometric test", 0.55),
        ("hackerrank", 0.5),
        ("codesignal", 0.5),
        ("assessment link", 0.55),
        ("complete a short test", 0.5),
        ("evaluation", 0.3),
        ("assessment", 0.4),
    ],
    "ADDITIONAL_INFORMATION_REQUESTED": [
        ("additional information", 0.6),
        ("please provide", 0.5),
        ("could you send", 0.45),
        ("please share", 0.45),
        ("updated resume", 0.45),
        ("missing documents", 0.55),
        ("outstanding documents", 0.55),
        ("need more information", 0.5),
        ("supporting documents", 0.45),
        ("please submit your transcripts", 0.55),
        ("right to work documentation", 0.5),
    ],
    "APPLICATION_UNDER_REVIEW": [
        ("under review", 0.6),
        ("reviewing your application", 0.6),
        ("our team is reviewing", 0.55),
        ("we are reviewing your application", 0.6),
        ("in review", 0.45),
        ("application is being reviewed", 0.6),
        ("reviewing candidates", 0.4),
    ],
    "APPLICATION_RECEIVED": [
        ("we received your application", 0.65),
        ("application received", 0.65),
        ("thank you for applying", 0.5),
        ("your application has been submitted", 0.6),
        ("thanks for your application", 0.55),
        ("application confirmation", 0.55),
        ("successfully submitted your application", 0.6),
    ],
}

# Minimum accumulated evidence weight to commit to a non-UNKNOWN label.
_COMMIT_THRESHOLD = 0.35
# Margin within which two competing classes are treated as ambiguous.
_AMBIGUITY_MARGIN = 0.05


class HrCommunicationClassifier:
    """Classify an inbound HR communication into the supported vocabulary."""

    def __init__(self, prompt_version: str = "v1"):
        self.prompt_version = prompt_version

    def classify(
        self,
        subject: str = "",
        body: str = "",
        sender: str = "",
        application_context: Dict[str, Any] | None = None,
    ) -> Dict[str, Any]:
        text = f"{subject or ''} {body or ''}".lower()

        scored = self._score(text)
        scored.sort(key=lambda item: item[1], reverse=True)

        if not scored or scored[0][1] < _COMMIT_THRESHOLD:
            return self._result(
                "UNKNOWN",
                0.1,
                [],
                "insufficient evidence to classify this communication",
            )

        best_label, best_score, best_matched = scored[0]
        # Ambiguity guard: if a competing class is effectively tied, stay UNKNOWN.
        if len(scored) > 1 and (best_score - scored[1][1]) < _AMBIGUITY_MARGIN:
            return self._result(
                "UNKNOWN",
                round(min(0.4, best_score), 2),
                best_matched,
                "evidence is ambiguous between "
                f"{best_label} and {scored[1][0]}",
            )

        confidence = round(min(0.97, 0.4 + best_score * 0.6), 2)
        return self._result(best_label, confidence, best_matched, None)

    def _score(self, text: str) -> List[Tuple[str, float, List[str]]]:
        results: List[Tuple[str, float, List[str]]] = []
        for label, phrases in _SIGNALS.items():
            weight = 0.0
            matched: List[str] = []
            for phrase, value in phrases:
                if phrase in text:
                    weight += value
                    matched.append(phrase)
            if weight > 0:
                results.append((label, min(weight, 1.0), matched))
        return results

    def _result(
        self,
        classification: str,
        confidence: float,
        matched: List[str],
        reason_override: str | None,
    ) -> Dict[str, Any]:
        evidence = "; ".join(f'"{m}"' for m in matched) if matched else "no decisive evidence phrases found"
        reason = reason_override or (
            f"classified as {classification} from evidence phrases: {evidence}"
        )
        # Only classification-owned fields are returned. No server-owned identifiers.
        return {
            "classification": classification,
            "confidence": confidence,
            "evidence": evidence,
            "reason": reason,
            "signals": matched,
            "promptVersion": self.prompt_version,
        }
