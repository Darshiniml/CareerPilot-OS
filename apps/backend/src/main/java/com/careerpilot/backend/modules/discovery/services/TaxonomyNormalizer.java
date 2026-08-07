package com.careerpilot.backend.modules.discovery.services;

import com.careerpilot.connector.sdk.DiscoveredJob;
import org.springframework.stereotype.Service;
import java.util.*;

/** Adapter point for the Knowledge Taxonomy Service. The deterministic fallback keeps discovery available. */
@Service
public class TaxonomyNormalizer {
    public DiscoveredJob normalize(DiscoveredJob job) {
        job.setNormalizedTitle(clean(job.getTitle()).replaceAll("\\b(sr|snr)\\b", "senior").replaceAll("\\bswe\\b", "software engineer"));
        job.setNormalizedCompany(clean(job.getCompany()).replaceAll("\\b(inc|llc|ltd|corp|corporation)\\b\\.?", " ").replaceAll("\\s+", " ").trim());
        job.setLocation(clean(job.getLocation()));
        job.setEmploymentType(enumValue(job.getEmploymentType(), Map.of("full time", "FULL_TIME", "part time", "PART_TIME", "freelance", "CONTRACT")));
        job.setWorkMode(enumValue(job.getWorkMode(), Map.of("onsite", "ON_SITE", "on site", "ON_SITE", "remote", "REMOTE", "hybrid", "HYBRID")));
        job.setSalary(job.getSalary() == null ? null : job.getSalary().strip().replaceAll("\\s+", " "));
        job.setSkills(extract(job.getRawContent(), List.of("java", "python", "javascript", "sql", "spring", "react", "aws", "docker", "kubernetes")));
        job.setTechnologies(new ArrayList<>(job.getSkills()));
        return job;
    }
    private String clean(String value) { return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#., /-]", "").replaceAll("\\s+", " "); }
    private String enumValue(String value, Map<String,String> aliases) { String normalized=clean(value).replace('-', ' '); return aliases.getOrDefault(normalized, normalized.toUpperCase(Locale.ROOT).replace(' ', '_')); }
    private List<String> extract(String content, List<String> terms) { if(content == null) return List.of(); String lower=content.toLowerCase(Locale.ROOT); return terms.stream().filter(lower::contains).toList(); }
}
