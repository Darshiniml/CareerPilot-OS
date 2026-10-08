package com.careerpilot.backend.modules.discovery.services;

import com.careerpilot.connector.sdk.DiscoveredJob;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Deterministic normalisation of connector data. Unknown values stay {@code null} (never empty
 * strings or guesses); display fields keep their original casing; skills are only those literally
 * present in the posting text (word-boundary matches, so "java" does not match "javascript").
 */
@Service
public class TaxonomyNormalizer {

    private static final List<String> SKILL_TERMS = List.of("java", "python", "javascript", "typescript", "go", "golang",
            "kotlin", "scala", "rust", "c++", "c#", ".net", "ruby", "php", "sql", "postgresql", "mysql", "mongodb", "redis",
            "spring", "spring boot", "django", "flask", "fastapi", "node.js", "react", "angular", "vue", "aws", "gcp", "azure",
            "docker", "kubernetes", "terraform", "kafka", "spark", "graphql", "rest", "microservices", "linux", "git");

    public DiscoveredJob normalize(DiscoveredJob job) {
        job.setNormalizedTitle(clean(job.getTitle()).replaceAll("\\b(sr|snr)\\b", "senior").replaceAll("\\bswe\\b", "software engineer"));
        job.setNormalizedCompany(clean(job.getCompany()).replaceAll("\\b(inc|llc|ltd|corp|corporation)\\b\\.?", " ").replaceAll("\\s+", " ").trim());
        job.setLocation(blankToNull(job.getLocation() == null ? null : job.getLocation().strip().replaceAll("\\s+", " ")));
        job.setEmploymentType(enumValue(job.getEmploymentType(), Map.of("full time", "FULL_TIME", "fulltime", "FULL_TIME",
                "part time", "PART_TIME", "parttime", "PART_TIME", "freelance", "CONTRACT", "contractor", "CONTRACT",
                "internship", "INTERN", "intern", "INTERN")));
        job.setWorkMode(enumValue(job.getWorkMode(), Map.of("onsite", "ON_SITE", "on site", "ON_SITE", "in office", "ON_SITE",
                "remote", "REMOTE", "hybrid", "HYBRID")));
        job.setSalary(job.getSalary() == null ? null : blankToNull(job.getSalary().strip().replaceAll("\\s+", " ")));
        job.setSkills(extract(job.getRawContent()));
        job.setTechnologies(new ArrayList<>(job.getSkills()));
        return job;
    }

    private String clean(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}+#., /-]", "").replaceAll("\\s+", " ");
    }

    private String enumValue(String value, Map<String, String> aliases) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = clean(value).replace('-', ' ').replace('_', ' ').trim();
        return aliases.getOrDefault(normalized, normalized.toUpperCase(Locale.ROOT).replace(' ', '_'));
    }

    private List<String> extract(String content) {
        if (content == null) {
            return List.of();
        }
        String lower = content.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String term : SKILL_TERMS) {
            Pattern p = Pattern.compile("(?<![a-z0-9+#.])" + Pattern.quote(term) + "(?![a-z0-9+#])");
            if (p.matcher(lower).find()) {
                found.add(term);
            }
        }
        return found;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
