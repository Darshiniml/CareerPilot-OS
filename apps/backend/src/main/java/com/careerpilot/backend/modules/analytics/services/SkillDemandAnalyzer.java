package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.ai.job.domain.JobIntelligenceCache;
import com.careerpilot.backend.modules.ai.job.repositories.JobIntelligenceCacheRepository;
import com.careerpilot.backend.modules.analytics.domain.SkillDemandSnapshot;
import com.careerpilot.backend.modules.analytics.repositories.SkillDemandSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SkillDemandAnalyzer {

    private final JobIntelligenceCacheRepository jobCacheRepository;
    private final SkillDemandSnapshotRepository demandSnapshotRepository;

    public Map<String, Object> analyzeSkills(Set<String> candidateSkills) {
        List<JobIntelligenceCache> jobs = jobCacheRepository.findAll();
        Map<String, Integer> skillFrequency = new HashMap<>();
        int totalJobs = jobs.size();

        for (JobIntelligenceCache job : jobs) {
            Map<String, Object> knowledge = job.getStructuredKnowledge();
            if (knowledge != null && knowledge.containsKey("skills")) {
                Object rawSkills = knowledge.get("skills");
                if (rawSkills instanceof List<?> rawList) {
                    for (Object item : rawList) {
                        String skillName = extractSkillName(item);
                        if (skillName != null) {
                            String normalized = skillName.toLowerCase().trim();
                            skillFrequency.put(normalized, skillFrequency.getOrDefault(normalized, 0) + 1);
                        }
                    }
                }
            }
        }

        // Calculate demand percentages
        Map<String, Double> demandPercentages = new HashMap<>();
        skillFrequency.forEach((skill, freq) -> {
            double pct = totalJobs > 0 ? ((double) freq / totalJobs) * 100.0 : 0.0;
            demandPercentages.put(skill, pct);

            // Record snapshot in DB
            demandSnapshotRepository.save(SkillDemandSnapshot.builder()
                    .id(UUID.randomUUID())
                    .skill(skill)
                    .demandPercentage(pct)
                    .build());
        });

        // Current skills
        List<String> current = new ArrayList<>(candidateSkills);

        // Missing skills (in high demand in jobs, but not in candidate skills)
        List<String> missing = demandPercentages.entrySet().stream()
                .filter(e -> !candidateSkills.contains(e.getKey().toLowerCase()))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Frequently requested skills
        List<Map<String, Object>> frequentlyRequested = demandPercentages.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("skill", e.getKey());
                    map.put("demand", e.getValue());
                    return map;
                })
                .collect(Collectors.toList());

        // High value skills (high demand and missing)
        List<String> highValue = missing.stream()
                .filter(skill -> demandPercentages.getOrDefault(skill, 0.0) >= 40.0)
                .collect(Collectors.toList());

        // Underutilized skills (candidate has them, but demand is low < 15%)
        List<String> underutilized = current.stream()
                .filter(skill -> demandPercentages.getOrDefault(skill.toLowerCase(), 0.0) < 15.0)
                .collect(Collectors.toList());

        // Emerging skills (mocked emerging skills or based on recent snapshots, say recent trend is upward)
        List<String> emerging = List.of("kubernetes", "docker", "rust", "go", "llm", "langchain");

        Map<String, Object> result = new HashMap<>();
        result.put("currentSkills", current);
        result.put("missingSkills", missing);
        result.put("frequentlyRequested", frequentlyRequested);
        result.put("highValueSkills", highValue);
        result.put("underutilizedSkills", underutilized);
        result.put("emergingSkills", emerging);
        result.put("demandPercentages", demandPercentages);

        return result;
    }

    private String extractSkillName(Object rawItem) {
        if (rawItem instanceof String str) {
            return str;
        } else if (rawItem instanceof Map<?, ?> map) {
            Object val = map.get("skill");
            if (val == null) val = map.get("name");
            return val != null ? val.toString() : null;
        }
        return null;
    }
}
