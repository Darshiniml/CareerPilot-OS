package com.careerpilot.backend.modules.application.domain;

/**
 * Deterministic outcomes of processing a classified HR communication into application intelligence.
 * Distinguishes "communication classified successfully" from "application state transition accepted".
 *
 * <ul>
 *   <li>{@code STATE_TRANSITIONED} — the classification mapped to a valid forward transition; state changed.</li>
 *   <li>{@code EVENT_RECORDED} — the classification is evidence-only (no canonical state exists for it); no state change.</li>
 *   <li>{@code ALREADY_IN_TARGET_STATE} — the application is already in the mapped state; nothing to do.</li>
 *   <li>{@code INVALID_TRANSITION_REJECTED} — the mapped transition would regress or arbitrarily jump the lifecycle; rejected, evidence preserved.</li>
 *   <li>{@code TERMINAL_STATE_PROTECTED} — the application is in a terminal state; conflicting evidence is recorded but never overwrites it.</li>
 *   <li>{@code WITHHELD_LOW_CONFIDENCE} — classification confidence below the documented threshold; nothing persisted, nothing mutated.</li>
 *   <li>{@code UNMATCHED_NO_APPLICATION} — the communication is not matched to an application; no application is guessed or fabricated.</li>
 *   <li>{@code NOT_CLASSIFIED} — the communication has not completed M22.3 classification.</li>
 *   <li>{@code NO_ACTIONABLE_CLASSIFICATION} — classification is UNKNOWN; there is no evidence to act on.</li>
 * </ul>
 */
public enum TimelineEventOutcome {
    STATE_TRANSITIONED,
    EVENT_RECORDED,
    ALREADY_IN_TARGET_STATE,
    INVALID_TRANSITION_REJECTED,
    TERMINAL_STATE_PROTECTED,
    WITHHELD_LOW_CONFIDENCE,
    UNMATCHED_NO_APPLICATION,
    NOT_CLASSIFIED,
    NO_ACTIONABLE_CLASSIFICATION
}
