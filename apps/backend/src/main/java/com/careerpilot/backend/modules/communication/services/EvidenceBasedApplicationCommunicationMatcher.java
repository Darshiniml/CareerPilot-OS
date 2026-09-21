package com.careerpilot.backend.modules.communication.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Deterministic, evidence-based matcher. Associates an inbound HR communication with one of the
 * candidate's existing applications only when concrete evidence (external application id, sender
 * domain, job title/company text) crosses a confidence threshold. Never fabricates a match: when
 * evidence is weak or ambiguous the communication is reported as unmatched.
 */
@Service
@RequiredArgsConstructor
public class EvidenceBasedApplicationCommunicationMatcher implements ApplicationCommunicationMatcher {

    static final double MATCH_THRESHOLD = 0.6;
    static final double AMBIGUITY_MARGIN = 0.15;

    private final DiscoveryJobRepository discoveryJobRepository;

    @Override
    public CommunicationMatchResult match(HrCommunication communication, List<ApplicationRecord> candidateApplications) {
        if (candidateApplications == null || candidateApplications.isEmpty()) {
            return CommunicationMatchResult.unmatched("candidate has no applications to match against");
        }

        String subject = nullToEmpty(communication.getSubject());
        String body = nullToEmpty(communication.getBody());
        String text = lower(subject + " " + body);
        String senderDomainKey = domainKey(senderDomain(communication.getSender()));

        Scored best = null;
        Scored second = null;

        for (ApplicationRecord application : candidateApplications) {
            Scored scored = score(application, communication, text, senderDomainKey);
            if (best == null || scored.confidence > best.confidence) {
                second = best;
                best = scored;
            } else if (second == null || scored.confidence > second.confidence) {
                second = scored;
            }
        }

        if (best == null || best.confidence < MATCH_THRESHOLD) {
            return CommunicationMatchResult.unmatched(
                    "no application met the evidence threshold (best=" + format(best) + ")");
        }

        if (second != null && second.confidence >= MATCH_THRESHOLD
                && (best.confidence - second.confidence) < AMBIGUITY_MARGIN) {
            return CommunicationMatchResult.unmatched(
                    "match is ambiguous between applications (" + format(best) + " vs " + format(second) + ")");
        }

        return CommunicationMatchResult.matched(best.applicationId, best.confidence, best.evidence());
    }

    private Scored score(ApplicationRecord application, HrCommunication communication, String text, String senderDomainKey) {
        double confidence = 0.0;
        List<String> signals = new ArrayList<>();

        String externalId = application.getExternalApplicationId();
        if (notBlank(externalId) && text.contains(lower(externalId))) {
            confidence += 0.6;
            signals.add("external_application_id present in message");
        }

        Optional<DiscoveryJob> jobOpt = discoveryJobRepository.findById(application.getJobId());
        DiscoveryJob job = jobOpt.orElse(null);

        if (job != null) {
            String sourceDomainKey = domainKey(hostOf(job.getSourceUrl()));
            String companyKey = alnum(lower(nullToEmpty(job.getCompany())));

            if (notBlank(senderDomainKey) && senderDomainKey.equals(sourceDomainKey)) {
                confidence += 0.5;
                signals.add("sender domain matches job source domain");
            } else if (notBlank(senderDomainKey) && notBlank(companyKey)
                    && (senderDomainKey.contains(companyKey) || companyKey.contains(senderDomainKey))) {
                confidence += 0.45;
                signals.add("sender domain matches company name");
            }

            String title = lower(nullToEmpty(job.getTitle()));
            if (notBlank(title) && text.contains(title)) {
                confidence += 0.35;
                signals.add("job title present in message");
            }

            String company = lower(nullToEmpty(job.getCompany()));
            if (notBlank(company) && text.contains(company)) {
                confidence += 0.3;
                signals.add("company name present in message");
            }
        }

        if (confidence > 1.0) {
            confidence = 1.0;
        }

        return new Scored(application.getApplicationId(), confidence, signals);
    }

    private record Scored(java.util.UUID applicationId, double confidence, List<String> signals) {
        String evidence() {
            return String.format(Locale.ROOT, "confidence=%.2f; signals=[%s]", confidence, String.join("; ", signals));
        }
    }

    private static String format(Scored scored) {
        return scored == null ? "none" : String.format(Locale.ROOT, "%.2f", scored.confidence);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String alnum(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String senderDomain(String sender) {
        if (sender == null) {
            return "";
        }
        int at = sender.lastIndexOf('@');
        return at < 0 ? "" : sender.substring(at + 1).trim();
    }

    private static String hostOf(String url) {
        if (!notBlank(url)) {
            return "";
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "" : host;
        } catch (RuntimeException ex) {
            return "";
        }
    }

    /**
     * Reduces a hostname to its registrable domain minus the TLD, alnum-normalised.
     * e.g. "jobs.acme-corp.com" -> "acmecorp".
     */
    private static String domainKey(String host) {
        String value = alnum(lower(host));
        if (value.isEmpty()) {
            return "";
        }
        String domain = lower(host);
        String[] labels = domain.split("\\.");
        String registrable;
        if (labels.length >= 2) {
            registrable = labels[labels.length - 2] + "." + labels[labels.length - 1];
        } else {
            registrable = domain;
        }
        int dot = registrable.indexOf('.');
        String withoutTld = dot > 0 ? registrable.substring(0, dot) : registrable;
        return alnum(withoutTld);
    }
}
