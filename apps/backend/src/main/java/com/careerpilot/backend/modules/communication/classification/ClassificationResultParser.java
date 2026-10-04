package com.careerpilot.backend.modules.communication.classification;

import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server-side parser and validator for the AI classification contract.
 *
 * <p>Nothing from the model is persisted blindly. The classification must map to a real
 * {@link CommunicationClassification} value, the confidence must be a number within [0, 1], and
 * evidence must be present. Any deviation is a safe failure ({@link ClassificationFailedException})
 * rather than a coerced or fabricated value.</p>
 *
 * <p>Server-owned identifiers are never read from the model output, so they cannot be injected even
 * if the model (or a prompt-injection in the email body) attempts to return them.</p>
 */
@Component
public class ClassificationResultParser {

    public ClassificationResult parse(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            throw new ClassificationFailedException("AI classification response was empty");
        }

        CommunicationClassification classification = parseClassification(result.get("classification"));
        double confidence = parseConfidence(result.get("confidence"));
        String evidence = parseEvidence(result.get("evidence"));
        String reason = asText(result.get("reason"));
        List<String> signals = parseSignals(result.get("signals"));

        return new ClassificationResult(classification, confidence, evidence, reason, signals);
    }

    private CommunicationClassification parseClassification(Object raw) {
        String value = asText(raw);
        if (value == null || value.isBlank()) {
            throw new ClassificationFailedException("AI classification response is missing a classification");
        }
        try {
            return CommunicationClassification.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ClassificationFailedException(
                    "AI returned an unsupported classification value: " + value);
        }
    }

    private double parseConfidence(Object raw) {
        if (!(raw instanceof Number number)) {
            throw new ClassificationFailedException("AI classification confidence must be numeric");
        }
        double confidence = number.doubleValue();
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new ClassificationFailedException(
                    "AI classification confidence out of range [0,1]: " + confidence);
        }
        return confidence;
    }

    private String parseEvidence(Object raw) {
        String evidence = asText(raw);
        if (evidence == null || evidence.isBlank()) {
            throw new ClassificationFailedException("AI classification response is missing evidence");
        }
        return evidence.trim();
    }

    private List<String> parseSignals(Object raw) {
        List<String> signals = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String text = asText(item);
                if (text != null && !text.isBlank()) {
                    signals.add(text.trim());
                }
            }
        }
        return signals;
    }

    private static String asText(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }
}
