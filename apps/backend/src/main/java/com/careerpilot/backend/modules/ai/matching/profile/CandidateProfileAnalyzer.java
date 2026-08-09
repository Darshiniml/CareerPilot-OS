package com.careerpilot.backend.modules.ai.matching.profile;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.*;

@Component
public class CandidateProfileAnalyzer {

    public CandidateProfile analyze(Map<String, Object> knowledge, Map<String, Object> qualityMetrics,
                                    Map<String, Object> preferences) {
        if (knowledge == null) knowledge = Collections.emptyMap();
        if (qualityMetrics == null) qualityMetrics = Collections.emptyMap();
        if (preferences == null) preferences = Collections.emptyMap();
        CandidateProfile.CandidateProfileBuilder builder = CandidateProfile.builder();

        Set<String> skills = new LinkedHashSet<>();
        Set<String> technologies = new LinkedHashSet<>();

        for (Map<String, Object> skill : extractMapList(knowledge.get("skills"))) {
            String name = extractStringValue(skill.get("skill"));
            if (name != null) {
                skills.add(normalizeToken(name));
                String category = extractStringValue(skill.get("category"));
                if (category != null && isTechnologyCategory(category)) {
                    technologies.add(normalizeToken(name));
                }
            }
        }

        double totalYears = 0;
        int roleCount = 0;
        String lastTitle = null;
        String firstTitle = null;

        for (Map<String, Object> exp : extractMapList(knowledge.get("experience"))) {
            LocalDate start = parseDate(exp.get("startDate"));
            LocalDate end = parseDate(exp.get("endDate"));
            boolean current = Boolean.TRUE.equals(exp.get("currentJob"));
            if (start != null) {
                LocalDate effectiveEnd = current || end == null ? LocalDate.now() : end;
                totalYears += ChronoUnit.MONTHS.between(start, effectiveEnd) / 12.0;
            }
            String title = extractStringValue(exp.get("title"));
            if (title != null) {
                if (firstTitle == null) {
                    firstTitle = title;
                }
                lastTitle = title;
            }
            roleCount++;
        }

        List<String> educationLevels = new ArrayList<>();
        for (Map<String, Object> edu : extractMapList(knowledge.get("education"))) {
            String degree = extractStringValue(edu.get("degree"));
            if (degree != null) {
                educationLevels.add(normalizeToken(degree));
            }
        }

        Set<String> certifications = normalizeSet(extractStringListFromMaps(knowledge.get("certifications"), "name"));
        List<String> projectTechs = new ArrayList<>();
        List<String> projectDescs = new ArrayList<>();
        for (Map<String, Object> project : extractMapList(knowledge.get("projects"))) {
            projectDescs.add(extractStringValue(project.get("description")));
            projectTechs.addAll(extractStringList(project.get("technologies")));
        }

        Set<String> languages = normalizeSet(extractStringList(knowledge.get("languages")));

        double atsQuality = 0;
        if (qualityMetrics != null) {
            Object score = qualityMetrics.get("atsScore");
            if (score instanceof Number number) {
                atsQuality = number.doubleValue();
            }
        }

        double careerProgression = computeCareerProgression(firstTitle, lastTitle, roleCount, totalYears);

        builder.skills(skills)
                .technologies(technologies.isEmpty() ? new HashSet<>(skills) : technologies)
                .totalExperienceYears(totalYears)
                .seniorityLevel(inferSeniority(totalYears, lastTitle))
                .educationLevels(educationLevels)
                .certifications(certifications)
                .projectTechnologies(new ArrayList<>(normalizeSet(projectTechs)))
                .projectDescriptions(projectDescs.stream().filter(Objects::nonNull).toList())
                .languages(languages)
                .atsQuality(atsQuality)
                .careerProgressionScore(careerProgression)
                .summary(extractStringValue(knowledge.get("summary")));

        if (preferences != null) {
            builder.preferredWorkStyle(extractStringValue(preferences.get("workStyle")))
                    .salaryMin(parseInteger(preferences.get("salaryMin")))
                    .salaryMax(parseInteger(preferences.get("salaryMax")))
                    .salaryCurrency(extractStringValue(preferences.get("currencyCode")))
                    .preferredEmploymentType(extractStringValue(preferences.get("employmentType")))
                    .preferredLocations(extractStringList(preferences.get("preferredLocations")));
        }

        return builder.build();
    }

    private boolean isTechnologyCategory(String category) {
        String normalized = normalizeToken(category);
        return normalized.contains("backend") || normalized.contains("frontend")
                || normalized.contains("cloud") || normalized.contains("devops")
                || normalized.contains("database") || normalized.contains("programming");
    }

    private double computeCareerProgression(String firstTitle, String lastTitle, int roleCount, double years) {
        if (roleCount <= 1 || years <= 0) {
            return 50.0;
        }
        int firstLevel = seniorityRank(firstTitle);
        int lastLevel = seniorityRank(lastTitle);
        double progression = ((lastLevel - firstLevel) * 20.0) + (roleCount * 5.0);
        return clampScore(50.0 + progression);
    }

    private int seniorityRank(String title) {
        if (title == null) return 1;
        String t = normalizeToken(title);
        if (t.contains("intern") || t.contains("fresher")) return 0;
        if (t.contains("junior") || t.contains("associate")) return 1;
        if (t.contains("senior") || t.contains("sr")) return 3;
        if (t.contains("lead") || t.contains("staff")) return 4;
        if (t.contains("principal") || t.contains("architect")) return 5;
        if (t.contains("director") || t.contains("manager")) return 6;
        return 2;
    }

    private String inferSeniority(double years, String lastTitle) {
        if (lastTitle != null) {
            String t = normalizeToken(lastTitle);
            if (t.contains("senior")) return "SENIOR";
            if (t.contains("lead")) return "LEAD";
            if (t.contains("junior")) return "JUNIOR";
            if (t.contains("intern")) return "INTERN";
        }
        if (years < 1) return "FRESHER";
        if (years < 3) return "JUNIOR";
        if (years < 7) return "MID";
        return "SENIOR";
    }

    private LocalDate parseDate(Object raw) {
        if (raw == null) return null;
        try {
            return LocalDate.parse(raw.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInteger(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<String> extractStringListFromMaps(Object raw, String field) {
        List<String> result = new ArrayList<>();
        for (Map<String, Object> map : extractMapList(raw)) {
            String val = extractStringValue(map.get(field));
            if (val != null) {
                result.add(val);
            }
        }
        return result;
    }
}
