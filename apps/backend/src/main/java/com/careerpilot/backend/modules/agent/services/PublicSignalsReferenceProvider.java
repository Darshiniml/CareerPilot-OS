package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hiring signals derived only from job postings CareerPilot's connectors actually discovered for
 * the company (count, titles, sources, most recent sighting). No scraping, no invented signals:
 * when nothing was discovered the result says so.
 */
@Component
public class PublicSignalsReferenceProvider implements ReferenceSourceProvider {

    private final DiscoveryJobRepository jobRepository;

    public PublicSignalsReferenceProvider(DiscoveryJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public String getProviderName() {
        return "discovered-postings";
    }

    @Override
    public Map<String, Object> queryPublicSignals(UUID userId, String companyName) {
        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("provider", getProviderName());
        if (companyName == null || companyName.isBlank()) {
            signals.put("openPostingsDiscovered", 0);
            signals.put("reason", "Company name unknown");
            return signals;
        }
        String key = normalize(companyName);
        List<DiscoveryJob> postings = jobRepository.findAll().stream()
                .filter(j -> j.getCompany() != null && normalize(j.getCompany()).equals(key))
                .toList();
        signals.put("openPostingsDiscovered", postings.size());
        signals.put("sampleTitles", postings.stream().map(DiscoveryJob::getTitle).filter(Objects::nonNull).distinct().limit(5).toList());
        signals.put("sources", postings.stream().map(DiscoveryJob::getConnectorId).filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new)));
        postings.stream().map(DiscoveryJob::getLastSeenAt).filter(Objects::nonNull).max(Comparator.naturalOrder())
                .ifPresent(last -> signals.put("lastSeenAt", last));
        if (postings.isEmpty()) {
            signals.put("reason", "No postings from this company were discovered by the configured connectors");
        }
        return signals;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
