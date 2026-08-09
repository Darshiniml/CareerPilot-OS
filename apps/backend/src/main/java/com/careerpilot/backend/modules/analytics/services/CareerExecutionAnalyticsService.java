package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Milestone 19: Career Execution Analytics Service
 * Calculates real application funnel metrics, resume performance, and source performance.
 * This is separate from the existing CareerAnalyticsService to avoid conflicts.
 */
@Service
@Slf4j
public class CareerExecutionAnalyticsService {

    private final ApplicationRecordRepository applicationRepository;
    private final ResumeRepository resumeRepository;

    public CareerExecutionAnalyticsService(ApplicationRecordRepository applicationRepository, ResumeRepository resumeRepository) {
        this.applicationRepository = applicationRepository;
        this.resumeRepository = resumeRepository;
    }

    public CareerExecutionMetrics calculateCareerExecution(UUID candidateId) {
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        int total = apps.size();

        int readyForApproval = (int) apps.stream().filter(a -> a.getWorkflowState() == WorkflowState.READY_FOR_APPROVAL).count();
        int manualAction = (int) apps.stream().filter(a -> a.getWorkflowState() == WorkflowState.MANUAL_ACTION_REQUIRED).count();
        int submitted = (int) apps.stream().filter(a -> a.getWorkflowState() == WorkflowState.SUBMITTED || a.getWorkflowState() == WorkflowState.SUBMITTED_VERIFIED).count();
        int verified = (int) apps.stream().filter(a -> a.getWorkflowState() == WorkflowState.SUBMITTED_VERIFIED).count();
        int underReview = (int) apps.stream().filter(a -> a.getWorkflowState() == WorkflowState.UNDER_REVIEW || a.getWorkflowState() == WorkflowState.ASSESSMENT).count();
        int interviews = (int) apps.stream().filter(a -> a.getWorkflowState() == WorkflowState.INTERVIEW).count();
        int offers = (int) apps.stream().filter(a -> a.getWorkflowState() == WorkflowState.OFFER).count();
        int rejected = (int) apps.stream().filter(a -> a.getWorkflowState() == WorkflowState.REJECTED || a.getWorkflowState() == WorkflowState.REJECTED_BY_COMPANY).count();
        int failed = (int) apps.stream().filter(a -> a.getWorkflowState() == WorkflowState.APPLICATION_FAILED || a.getWorkflowState() == WorkflowState.SUBMISSION_FAILED || a.getWorkflowState() == WorkflowState.FAILED).count();

        double interviewRate = total > 0 ? (double) interviews / total : 0.0;
        double offerRate = interviews > 0 ? (double) offers / interviews : 0.0;

        return CareerExecutionMetrics.builder()
                .candidateId(candidateId)
                .totalApplications(total)
                .readyForApprovalCount(readyForApproval)
                .manualActionRequiredCount(manualAction)
                .submittedCount(submitted)
                .verifiedCount(verified)
                .underReviewCount(underReview)
                .interviewCount(interviews)
                .offerCount(offers)
                .rejectedCount(rejected)
                .failedCount(failed)
                .interviewRate(interviewRate)
                .offerRate(offerRate)
                .build();
    }

    public ResumePerformanceReport calculateResumePerformance(UUID candidateId) {
        List<Resume> activeResumes = resumeRepository.findActiveByUserId(candidateId);
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);

        List<ResumeVersionPerformance> performances = new ArrayList<>();
        for (Resume r : activeResumes) {
            int count = (int) apps.stream().filter(a -> a.getMetadata() != null && r.getId().toString().equals(String.valueOf(a.getMetadata().get("resumeId")))).count();
            int interviews = (int) apps.stream().filter(a -> a.getMetadata() != null && r.getId().toString().equals(String.valueOf(a.getMetadata().get("resumeId"))) && a.getWorkflowState() == WorkflowState.INTERVIEW).count();

            String status = count >= 5 ? "EVALUATED" : "INSUFFICIENT_DATA";
            double rate = count >= 5 ? (double) interviews / count : 0.0;

            performances.add(ResumeVersionPerformance.builder()
                    .resumeId(r.getId())
                    .title(r.getTitle() != null ? r.getTitle() : r.getOriginalFilename())
                    .sampleSize(count)
                    .interviewCount(interviews)
                    .interviewRate(rate)
                    .status(status)
                    .build());
        }

        return ResumePerformanceReport.builder()
                .candidateId(candidateId)
                .resumePerformances(performances)
                .build();
    }

    public SourcePerformanceReport calculateSourcePerformance(UUID candidateId) {
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        Map<String, SourceMetrics> map = new HashMap<>();

        for (ApplicationRecord app : apps) {
            String source = app.getConnectorId() != null ? app.getConnectorId().toLowerCase() : "unknown";
            SourceMetrics sm = map.computeIfAbsent(source, k -> new SourceMetrics(k, 0, 0, 0, 0));
            sm.setApplications(sm.getApplications() + 1);
            if (app.getWorkflowState() == WorkflowState.SUBMITTED_VERIFIED) sm.setVerified(sm.getVerified() + 1);
            if (app.getWorkflowState() == WorkflowState.INTERVIEW) sm.setInterviews(sm.getInterviews() + 1);
            if (app.getWorkflowState() == WorkflowState.OFFER) sm.setOffers(sm.getOffers() + 1);
        }

        return new SourcePerformanceReport(candidateId, new ArrayList<>(map.values()));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CareerExecutionMetrics {
        private UUID candidateId;
        private int totalApplications;
        private int readyForApprovalCount;
        private int manualActionRequiredCount;
        private int submittedCount;
        private int verifiedCount;
        private int underReviewCount;
        private int interviewCount;
        private int offerCount;
        private int rejectedCount;
        private int failedCount;
        private double interviewRate;
        private double offerRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumePerformanceReport {
        private UUID candidateId;
        private List<ResumeVersionPerformance> resumePerformances;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumeVersionPerformance {
        private UUID resumeId;
        private String title;
        private int sampleSize;
        private int interviewCount;
        private double interviewRate;
        private String status;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SourcePerformanceReport {
        private UUID candidateId;
        private List<SourceMetrics> sources;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SourceMetrics {
        private String source;
        private int applications;
        private int verified;
        private int interviews;
        private int offers;
    }
}
