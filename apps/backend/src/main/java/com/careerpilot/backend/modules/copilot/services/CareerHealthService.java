package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.ai.candidate.CandidateKnowledgeService;
import com.careerpilot.backend.modules.analytics.domain.LearningProgress;
import com.careerpilot.backend.modules.analytics.repositories.LearningProgressRepository;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.interview.services.InterviewCoachService;
import com.careerpilot.backend.modules.profile.services.ProfileService;
import com.careerpilot.shared.dto.profile.ProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Career health computed only from the candidate's real data. Each component is either measured
 * (with the evidence used) or explicitly reported as unavailable; the overall score is the mean of
 * measured components only. There are no default values.
 */
@Service
@RequiredArgsConstructor
public class CareerHealthService {

    /** States that mean the employer responded to a submitted application. */
    static final Set<WorkflowState> RESPONDED = EnumSet.of(WorkflowState.UNDER_REVIEW, WorkflowState.ASSESSMENT,
            WorkflowState.INTERVIEW, WorkflowState.OFFER, WorkflowState.REJECTED, WorkflowState.REJECTED_BY_COMPANY,
            WorkflowState.COMPLETED);
    /** States that mean an application actually left the candidate's hands. */
    static final Set<WorkflowState> SUBMITTED = EnumSet.of(WorkflowState.SUBMITTED, WorkflowState.SUBMITTED_VERIFIED,
            WorkflowState.VERIFICATION_PENDING, WorkflowState.SUBMISSION_UNVERIFIED, WorkflowState.UNDER_REVIEW,
            WorkflowState.ASSESSMENT, WorkflowState.INTERVIEW, WorkflowState.OFFER, WorkflowState.REJECTED,
            WorkflowState.REJECTED_BY_COMPANY, WorkflowState.COMPLETED, WorkflowState.WITHDRAWN);
    static final int MIN_SUBMITTED_FOR_RATE = 3;

    private final CandidateKnowledgeService candidateKnowledgeService;
    private final ApplicationRecordRepository applicationRepository;
    private final InterviewCoachService interviewCoachService;
    private final LearningProgressRepository learningProgressRepository;
    private final ProfileService profileService;

    @Transactional(readOnly = true)
    public Map<String, Object> health(UUID userId) {
        Map<String, Object> components = new LinkedHashMap<>();

        // 1. Resume quality: ATS score of the processed primary resume.
        candidateKnowledgeService.primaryResume(userId).ifPresentOrElse(r -> {
            Object ats = r.atsMetrics().get("atsScore");
            if (ats instanceof Number n) {
                components.put("resumeQuality", measured(n.doubleValue(), "ATS score of " + r.title() + " v" + r.versionNumber()));
            } else {
                components.put("resumeQuality", unavailable("ATS analysis has no score yet"));
            }
        }, () -> components.put("resumeQuality", unavailable("No processed resume")));

        // 2. Profile completeness: which profile sections are filled in.
        ProfileDto profile = profileService.getProfile(userId);
        if (profile != null) {
            int filled = 0;
            List<String> missing = new ArrayList<>();
            filled += section(profile.getExperience(), "experience", missing);
            filled += section(profile.getEducation(), "education", missing);
            filled += section(profile.getSkills(), "skills", missing);
            filled += section(profile.getProjects(), "projects", missing);
            boolean prefs = profile.getPreferences() != null && profile.getPreferences().getPreferredRoles() != null
                    && !profile.getPreferences().getPreferredRoles().isEmpty();
            if (prefs) filled++; else missing.add("job preferences");
            components.put("profileCompleteness", measured(filled / 5.0,
                    missing.isEmpty() ? "All profile sections filled" : "Missing: " + String.join(", ", missing)));
        }

        // 3. Application response rate (needs a minimum sample).
        List<ApplicationRecord> applications = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(userId);
        long submitted = applications.stream().filter(a -> SUBMITTED.contains(a.getWorkflowState())).count();
        long responded = applications.stream().filter(a -> RESPONDED.contains(a.getWorkflowState())).count();
        if (submitted >= MIN_SUBMITTED_FOR_RATE) {
            components.put("applicationResponseRate", measured((double) responded / submitted,
                    responded + " of " + submitted + " submitted applications got a response"));
        } else {
            components.put("applicationResponseRate", unavailable(
                    "Not enough data: " + submitted + " submitted application(s), at least " + MIN_SUBMITTED_FOR_RATE + " needed"));
        }

        // 4. Interview readiness from answered practice questions.
        Map<String, Object> readiness = interviewCoachService.readiness(userId);
        if (Boolean.TRUE.equals(readiness.get("available")) && readiness.get("overallReadiness") instanceof Number n) {
            components.put("interviewReadiness", measured(n.doubleValue(),
                    readiness.get("answeredQuestions") + " answered practice questions"));
        } else {
            components.put("interviewReadiness", unavailable(String.valueOf(readiness.get("message"))));
        }

        // 5. Learning progress.
        List<LearningProgress> progress = learningProgressRepository.findByCandidateId(userId);
        if (!progress.isEmpty()) {
            double avg = progress.stream().mapToDouble(LearningProgress::getProgressPercentage).average().orElse(0) / 100.0;
            components.put("learningProgress", measured(Math.max(0, Math.min(1, avg)), progress.size() + " skills being learned"));
        } else {
            components.put("learningProgress", unavailable("No learning goals tracked yet"));
        }

        List<Double> values = new ArrayList<>();
        components.values().forEach(c -> {
            Object v = ((Map<?, ?>) c).get("score");
            if (v instanceof Number n) values.add(n.doubleValue());
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", !values.isEmpty());
        result.put("overallScore", values.isEmpty() ? null
                : Math.round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 1000) / 1000.0);
        result.put("measuredComponents", values.size());
        result.put("components", components);
        result.put("method", "mean of measured components only; unavailable components are excluded");
        return result;
    }

    private static int section(Collection<?> items, String name, List<String> missing) {
        if (items != null && !items.isEmpty()) {
            return 1;
        }
        missing.add(name);
        return 0;
    }

    private static Map<String, Object> measured(double score, String evidence) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", true);
        m.put("score", Math.round(score * 1000) / 1000.0);
        m.put("evidence", evidence);
        return m;
    }

    private static Map<String, Object> unavailable(String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", false);
        m.put("reason", reason);
        return m;
    }
}
