package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.ai.matching.SkillGapService;
import com.careerpilot.backend.modules.analytics.repositories.LearningProgressRepository;
import com.careerpilot.backend.modules.application.domain.ApplicationHistory;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationHistoryRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.backend.modules.interview.services.InterviewCoachService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Career analytics computed only from the candidate's stored data. Every rate needs a minimum
 * sample; below it the value is null with "Not enough data" instead of a misleading percentage.
 */
@Service
@RequiredArgsConstructor
public class CareerDashboardService {

    static final int MIN_SAMPLE = 3;
    static final Set<WorkflowState> SUBMITTED = EnumSet.of(WorkflowState.SUBMITTED, WorkflowState.SUBMITTED_VERIFIED,
            WorkflowState.VERIFICATION_PENDING, WorkflowState.SUBMISSION_UNVERIFIED, WorkflowState.UNDER_REVIEW,
            WorkflowState.ASSESSMENT, WorkflowState.INTERVIEW, WorkflowState.OFFER, WorkflowState.REJECTED,
            WorkflowState.REJECTED_BY_COMPANY, WorkflowState.COMPLETED, WorkflowState.WITHDRAWN);
    static final Set<WorkflowState> RESPONDED = EnumSet.of(WorkflowState.UNDER_REVIEW, WorkflowState.ASSESSMENT,
            WorkflowState.INTERVIEW, WorkflowState.OFFER, WorkflowState.REJECTED, WorkflowState.REJECTED_BY_COMPANY,
            WorkflowState.COMPLETED);

    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationHistoryRepository historyRepository;
    private final HrCommunicationRepository communicationRepository;
    private final DiscoveryJobRepository jobRepository;
    private final SkillGapService skillGapService;
    private final InterviewCoachService interviewCoachService;
    private final LearningProgressRepository learningProgressRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(UUID userId) {
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(userId);
        Map<UUID, Set<WorkflowState>> reached = new HashMap<>();
        for (ApplicationRecord a : apps) {
            Set<WorkflowState> states = EnumSet.noneOf(WorkflowState.class);
            states.add(a.getWorkflowState());
            historyRepository.findByApplicationIdOrderByCreatedAtAsc(a.getApplicationId()).stream()
                    .map(ApplicationHistory::getToState).filter(Objects::nonNull).forEach(states::add);
            reached.put(a.getApplicationId(), states);
        }
        long submitted = apps.stream().filter(a -> reached.get(a.getApplicationId()).stream().anyMatch(SUBMITTED::contains)).count();
        long responded = apps.stream().filter(a -> reached.get(a.getApplicationId()).stream().anyMatch(RESPONDED::contains)).count();
        long interviews = apps.stream().filter(a -> reached.get(a.getApplicationId()).contains(WorkflowState.INTERVIEW)).count();
        long offers = apps.stream().filter(a -> reached.get(a.getApplicationId()).contains(WorkflowState.OFFER)).count();
        long rejections = apps.stream().filter(a -> a.getWorkflowState() == WorkflowState.REJECTED
                || a.getWorkflowState() == WorkflowState.REJECTED_BY_COMPANY).count();

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("applications", apps.size());
        counts.put("submitted", submitted);
        counts.put("responses", responded);
        counts.put("interviews", interviews);
        counts.put("offers", offers);
        counts.put("rejections", rejections);

        Map<String, Object> rates = new LinkedHashMap<>();
        rates.put("responseRate", rate(responded, submitted));
        rates.put("interviewRate", rate(interviews, submitted));
        rates.put("offerRate", rate(offers, submitted));
        rates.put("averageResponseTimeDays", averageResponseTime(userId, apps));

        Map<UUID, DiscoveryJob> jobs = jobRepository.findAllById(apps.stream().map(ApplicationRecord::getJobId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(DiscoveryJob::getId, Function.identity()));
        Map<String, Object> breakdowns = new LinkedHashMap<>();
        breakdowns.put("byState", apps.stream().collect(Collectors.groupingBy(a -> a.getWorkflowState().name(), TreeMap::new, Collectors.counting())));
        breakdowns.put("byCompany", group(apps, a -> Optional.ofNullable(jobs.get(a.getJobId())).map(DiscoveryJob::getCompany).orElse(null)));
        breakdowns.put("byRole", group(apps, a -> Optional.ofNullable(jobs.get(a.getJobId())).map(DiscoveryJob::getTitle).orElse(null)));
        breakdowns.put("bySource", group(apps, ApplicationRecord::getConnectorId));

        SkillGapService.SkillLandscape skills = skillGapService.landscape(userId);
        Map<String, Object> interview = interviewCoachService.readiness(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("counts", counts);
        result.put("rates", rates);
        result.put("breakdowns", breakdowns);
        result.put("skills", skills);
        result.put("weakInterviewAreas", Boolean.TRUE.equals(interview.get("available")) ? interview.get("weakestAreas") : List.of());
        result.put("interviewReadiness", interview);
        result.put("learningProgress", learningProgressRepository.findByCandidateId(userId));
        result.put("minimumSample", MIN_SAMPLE);
        result.put("generatedAt", Instant.now());
        return result;
    }

    private static Map<String, Object> rate(long part, long total) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numerator", part);
        m.put("denominator", total);
        if (total < MIN_SAMPLE) {
            m.put("value", null);
            m.put("reason", "Not enough data (" + total + " submitted, " + MIN_SAMPLE + " needed)");
        } else {
            m.put("value", Math.round(1000.0 * part / total) / 1000.0);
        }
        return m;
    }

    private Map<String, Object> averageResponseTime(UUID userId, List<ApplicationRecord> apps) {
        List<HrCommunication> emails = communicationRepository.findByCandidateIdOrderByReceivedAtDesc(userId);
        List<Double> days = new ArrayList<>();
        for (ApplicationRecord a : apps) {
            if (a.getSubmittedAt() == null) continue;
            emails.stream()
                    .filter(e -> a.getApplicationId().equals(e.getMatchedApplicationId()) && e.getReceivedAt() != null
                            && e.getReceivedAt().isAfter(a.getSubmittedAt()))
                    .map(HrCommunication::getReceivedAt)
                    .min(Comparator.naturalOrder())
                    .ifPresent(first -> days.add(Duration.between(a.getSubmittedAt(), first).toHours() / 24.0));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("samples", days.size());
        if (days.size() < 2) {
            m.put("value", null);
            m.put("reason", "Not enough data (" + days.size() + " applications with a recorded first response)");
        } else {
            m.put("value", Math.round(days.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0);
        }
        return m;
    }

    private static Map<String, Long> group(List<ApplicationRecord> apps, Function<ApplicationRecord, String> key) {
        return apps.stream().map(key).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
    }
}
