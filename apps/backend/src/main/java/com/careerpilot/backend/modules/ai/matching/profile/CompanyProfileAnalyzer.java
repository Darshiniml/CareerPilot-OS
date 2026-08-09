package com.careerpilot.backend.modules.ai.matching.profile;

import org.springframework.stereotype.Component;

import java.util.*;

import static com.careerpilot.backend.modules.ai.matching.profile.ProfileNormalizationUtils.*;

@Component
public class CompanyProfileAnalyzer {

    public CompanyProfile analyze(Map<String, Object> knowledge, Map<String, Object> metadata,
                                  Map<String, Object> insights) {
        if (knowledge == null) knowledge = Collections.emptyMap();
        if (metadata == null) metadata = Collections.emptyMap();
        if (insights == null) insights = Collections.emptyMap();
        CompanyProfile.CompanyProfileBuilder builder = CompanyProfile.builder();

        UUID companyId = null;
        Object idRaw = knowledge.get("companyId");
        if (idRaw != null) {
            try {
                companyId = UUID.fromString(idRaw.toString());
            } catch (IllegalArgumentException ignored) {
                // leave null
            }
        }

        Set<String> techStack = normalizeSet(extractStringList(knowledge.get("technologyStack")));
        List<String> industries = extractStringList(knowledge.get("industries")).stream()
                .map(ProfileNormalizationUtils::normalizeToken)
                .filter(s -> !s.isEmpty())
                .toList();

        String culture = extractStringValue(knowledge.get("engineeringCulture"));
        List<String> hiringSignals = extractStringList(knowledge.get("hiringSignals"));
        String size = extractStringValue(knowledge.get("employeeRange"));
        List<String> benefits = extractStringList(knowledge.get("benefits"));

        List<String> growthIndicators = new ArrayList<>();
        List<String> learningOpportunities = new ArrayList<>();
        String remotePolicy = null;

        if (metadata != null) {
            remotePolicy = extractStringValue(metadata.get("remotePolicy"));
            growthIndicators.addAll(extractStringList(metadata.get("growthIndicators")));
        }

        if (insights != null) {
            learningOpportunities.addAll(extractStringList(insights.get("engineering")));
            hiringSignals = mergeLists(hiringSignals, extractStringList(insights.get("hiring")));
        }

        for (String benefit : benefits) {
            String b = normalizeToken(benefit);
            if (b.contains("learning") || b.contains("training") || b.contains("education")
                    || b.contains("conference") || b.contains("tuition")) {
                learningOpportunities.add(benefit);
            }
        }

        return builder.companyId(companyId)
                .technologyStack(techStack)
                .industries(industries)
                .engineeringCulture(culture)
                .hiringSignals(hiringSignals)
                .companySize(size)
                .remotePolicy(remotePolicy)
                .growthIndicators(growthIndicators)
                .benefits(benefits)
                .learningOpportunities(learningOpportunities)
                .build();
    }

    private List<String> mergeLists(List<String> a, List<String> b) {
        List<String> merged = new ArrayList<>(a);
        merged.addAll(b);
        return merged;
    }
}
