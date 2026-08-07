package com.careerpilot.backend.modules.ai.matching.profile;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.*;

@Component
public class JobProfileAnalyzer {

    private static final Pattern SALARY_PATTERN = Pattern.compile(
            "(\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)");

    public JobProfile analyze(Map<String, Object> knowledge, Map<String, Object> metadata,
                              Map<String, Object> insights) {
        JobProfile.JobProfileBuilder builder = JobProfile.builder();

        UUID companyId = null;
        Object companyIdRaw = knowledge.get("companyId");
        if (companyIdRaw != null) {
            try {
                companyId = UUID.fromString(companyIdRaw.toString());
            } catch (IllegalArgumentException ignored) {
                // leave null
            }
        }

        Set<String> requiredSkills = extractSkills(knowledge.get("requiredSkills"), "REQUIRED");
        Set<String> preferredSkills = extractSkills(knowledge.get("preferredSkills"), "PREFERRED");
        List<String> responsibilities = extractStringList(knowledge.get("responsibilities"));

        String seniority = coalesce(
                extractStringValue(knowledge.get("inferredSeniority")),
                extractStringValue(knowledge.get("declaredSeniority")),
                extractStringValue(knowledge.get("jobLevel"))
        );

        String salaryRange = extractStringValue(knowledge.get("salaryRange"));
        Double salaryMin = null;
        Double salaryMax = null;
        if (salaryRange != null) {
            List<Double> amounts = parseSalaryAmounts(salaryRange);
            if (!amounts.isEmpty()) {
                salaryMin = amounts.get(0);
                salaryMax = amounts.size() > 1 ? amounts.get(1) : amounts.get(0);
            }
        }

        List<String> locations = extractStringList(knowledge.get("locations"));
        String employmentType = extractStringValue(knowledge.get("employmentType"));
        String workMode = extractStringValue(knowledge.get("workMode"));
        Set<String> certifications = normalizeSet(extractStringList(knowledge.get("certifications")));
        List<String> education = extractStringList(knowledge.get("educationRequirements")).stream()
                .map(ProfileNormalizationUtils::normalizeToken)
                .filter(s -> !s.isEmpty())
                .toList();

        Set<String> techStack = new LinkedHashSet<>(requiredSkills);
        techStack.addAll(preferredSkills);

        String industry = null;
        if (metadata != null) {
            industry = extractStringValue(metadata.get("industry"));
        }
        if (industry == null && insights != null) {
            List<String> business = extractStringList(insights.get("business"));
            if (!business.isEmpty()) {
                industry = business.get(0);
            }
        }

        return builder.companyId(companyId)
                .requiredSkills(requiredSkills)
                .preferredSkills(preferredSkills)
                .responsibilities(responsibilities)
                .seniority(seniority)
                .salaryMin(salaryMin)
                .salaryMax(salaryMax)
                .salaryCurrency(extractStringValue(knowledge.get("currency")))
                .locations(locations)
                .employmentType(employmentType)
                .workMode(workMode)
                .requiredCertifications(certifications)
                .educationRequirements(education)
                .technologyStack(techStack)
                .industry(industry)
                .build();
    }

    private Set<String> extractSkills(Object raw, String defaultImportance) {
        Set<String> skills = new LinkedHashSet<>();
        for (Map<String, Object> skill : extractMapList(raw)) {
            String importance = extractStringValue(skill.get("importance"));
            if (importance == null) {
                importance = defaultImportance;
            }
            if ("REQUIRED".equalsIgnoreCase(defaultImportance)
                    && !"REQUIRED".equalsIgnoreCase(importance)) {
                continue;
            }
            String name = extractStringValue(skill.get("name"));
            if (name != null) {
                skills.add(normalizeToken(name));
            }
        }
        return skills;
    }

    private List<Double> parseSalaryAmounts(String salaryRange) {
        Matcher matcher = SALARY_PATTERN.matcher(salaryRange);
        List<Double> amounts = new ArrayList<>();
        while (matcher.find()) {
            try {
                amounts.add(Double.parseDouble(matcher.group(1).replace(",", "")));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return amounts;
    }

    private String coalesce(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
