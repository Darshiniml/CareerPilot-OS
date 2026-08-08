package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.analytics.domain.CareerMetric;
import com.careerpilot.backend.modules.analytics.repositories.CareerMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TrendAnalyzer {

    private final CareerMetricRepository metricRepository;

    public Map<String, Object> analyzeTrend(UUID candidateId, String metricType, String period) {
        List<CareerMetric> metrics = metricRepository.findByCandidateIdAndMetricTypeOrderByRecordedAtAsc(candidateId, metricType);
        if (metrics.isEmpty()) {
            return Map.of("metric", metricType, "current", 0.0, "previous", 0.0, "change", 0.0, "history", List.of());
        }

        Instant now = Instant.now();
        Instant currentStart;
        Instant prevStart;

        switch (period.toUpperCase()) {
            case "DAILY":
                currentStart = now.minus(1, ChronoUnit.DAYS);
                prevStart = now.minus(2, ChronoUnit.DAYS);
                break;
            case "WEEKLY":
                currentStart = now.minus(7, ChronoUnit.DAYS);
                prevStart = now.minus(14, ChronoUnit.DAYS);
                break;
            case "QUARTERLY":
                currentStart = now.minus(90, ChronoUnit.DAYS);
                prevStart = now.minus(180, ChronoUnit.DAYS);
                break;
            case "MONTHLY":
            default:
                currentStart = now.minus(30, ChronoUnit.DAYS);
                prevStart = now.minus(60, ChronoUnit.DAYS);
                break;
        }

        double currentSum = 0;
        int currentCount = 0;
        double prevSum = 0;
        int prevCount = 0;

        List<Map<String, Object>> history = new ArrayList<>();

        for (CareerMetric m : metrics) {
            Instant recorded = m.getRecordedAt();
            double val = m.getMetricValue();

            history.add(Map.of(
                    "recordedAt", recorded.toString(),
                    "value", val
            ));

            if (recorded.isAfter(currentStart)) {
                currentSum += val;
                currentCount++;
            } else if (recorded.isAfter(prevStart)) {
                prevSum += val;
                prevCount++;
            }
        }

        double currentAvg = currentCount > 0 ? currentSum / currentCount : 0.0;
        double prevAvg = prevCount > 0 ? prevSum / prevCount : 0.0;
        double change = currentAvg - prevAvg;

        Map<String, Object> result = new HashMap<>();
        result.put("metric", metricType);
        result.put("period", period);
        result.put("current", currentAvg);
        result.put("previous", prevAvg);
        result.put("change", change);
        result.put("history", history);

        return result;
    }
}
