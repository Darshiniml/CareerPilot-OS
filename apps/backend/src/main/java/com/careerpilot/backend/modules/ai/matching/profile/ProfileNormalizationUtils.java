package com.careerpilot.backend.modules.ai.matching.profile;

import java.util.*;
import java.util.stream.Collectors;

public final class ProfileNormalizationUtils {

    private ProfileNormalizationUtils() {}

    public static String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+#.\\- ]", "")
                .replaceAll("\\s+", " ");
    }

    public static Set<String> normalizeSet(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .map(ProfileNormalizationUtils::normalizeToken)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @SuppressWarnings("unchecked")
    public static List<String> extractStringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        if (raw instanceof Map<?, ?> map && map.containsKey("value")) {
            return extractStringList(map.get("value"));
        }
        return List.of(raw.toString());
    }

    @SuppressWarnings("unchecked")
    public static String extractStringValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> map && map.containsKey("value")) {
            Object value = map.get("value");
            return value != null ? value.toString() : null;
        }
        return raw.toString();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> extractMapList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof Map)
                    .map(item -> (Map<String, Object>) item)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    public static double clampScore(double score) {
        return Math.max(0.0, Math.min(100.0, score));
    }

    public static double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 100.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return clampScore((intersection.size() * 100.0) / union.size());
    }

    public static Set<String> matchedItems(Set<String> candidate, Set<String> required) {
        Set<String> matched = new HashSet<>();
        for (String req : required) {
            for (String cand : candidate) {
                if (cand.contains(req) || req.contains(cand)) {
                    matched.add(req);
                    break;
                }
            }
        }
        return matched;
    }

    public static Set<String> missingItems(Set<String> candidate, Set<String> required) {
        Set<String> missing = new LinkedHashSet<>();
        for (String req : required) {
            boolean found = false;
            for (String cand : candidate) {
                if (cand.contains(req) || req.contains(cand)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                missing.add(req);
            }
        }
        return missing;
    }
}
