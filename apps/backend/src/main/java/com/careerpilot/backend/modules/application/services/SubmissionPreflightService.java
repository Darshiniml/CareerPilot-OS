package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.agent.domain.AgentPolicy;
import com.careerpilot.backend.modules.agent.repositories.AgentPolicyRepository;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.ApplicationSubmissionCapability;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class SubmissionPreflightService {

    private final ApplicationSubmissionRegistry submissionRegistry;
    private final ResumeRepository resumeRepository;
    private final DiscoveryJobRepository jobRepository;
    private final ApplicationRecordRepository applicationRepository;
    private final AgentPolicyRepository agentPolicyRepository;
    private final UserRepository userRepository;

    public SubmissionPreflightService(
            ApplicationSubmissionRegistry submissionRegistry,
            ResumeRepository resumeRepository,
            DiscoveryJobRepository jobRepository,
            ApplicationRecordRepository applicationRepository,
            AgentPolicyRepository agentPolicyRepository,
            UserRepository userRepository) {
        this.submissionRegistry = submissionRegistry;
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.agentPolicyRepository = agentPolicyRepository;
        this.userRepository = userRepository;
    }

    public PreflightResult evaluatePreflight(ApplicationRecord record, UUID requestingActorId) {
        List<CheckResult> checks = new ArrayList<>();
        boolean overallAllowed = true;
        WorkflowState suggestedState = WorkflowState.SUBMISSION_IN_PROGRESS;

        // 1. Authenticated User Check
        boolean userExists = userRepository.existsById(record.getCandidateId());
        checks.add(new CheckResult("userAuthentication", userExists, userExists ? "User authenticated" : "User not found"));
        if (!userExists) overallAllowed = false;

        // 2. Candidate Resume Check
        List<Resume> resumes = resumeRepository.findActiveByUserId(record.getCandidateId());
        boolean hasResume = !resumes.isEmpty();
        checks.add(new CheckResult("resumeAvailable", hasResume, hasResume ? "Candidate resume uploaded" : "No resume found for candidate"));
        if (!hasResume) overallAllowed = false;

        // 3. Job Existence Check
        boolean jobExists = record.getJobId() != null && jobRepository.existsById(record.getJobId());
        checks.add(new CheckResult("jobExists", jobExists, jobExists ? "Job opportunity verified" : "Job record not found"));
        if (!jobExists) overallAllowed = false;

        // 4. Policy & Daily Limit Check
        Optional<AgentPolicy> policyOpt = agentPolicyRepository.findByUserId(record.getCandidateId());
        int maxPerDay = policyOpt.map(AgentPolicy::getMaxApplicationsPerDay).orElse(5);
        long todayCount = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(record.getCandidateId()).stream()
                .filter(a -> a.getWorkflowState() == WorkflowState.SUBMITTED
                        || a.getWorkflowState() == WorkflowState.SUBMITTED_VERIFIED
                        || a.getWorkflowState() == WorkflowState.SUBMISSION_IN_PROGRESS)
                .count();

        boolean withinDailyLimit = todayCount < maxPerDay;
        checks.add(new CheckResult("dailyLimitCheck", withinDailyLimit, withinDailyLimit
                ? "Within daily submission limit (" + todayCount + "/" + maxPerDay + ")"
                : "Daily application limit reached (" + todayCount + "/" + maxPerDay + ")"));

        if (!withinDailyLimit) {
            overallAllowed = false;
            suggestedState = WorkflowState.APPLICATION_BLOCKED_BY_DAILY_LIMIT;
        }

        // 5. Submission Capability Check
        String connectorId = record.getConnectorId() != null ? record.getConnectorId() : "manual-fallback";
        ApplicationSubmissionCapability capability = submissionRegistry.getSubmissionCapability(connectorId);
        boolean supportsAutomatedSubmission = capability.isSupportsSubmission() && capability.isEnabled();

        checks.add(new CheckResult("submissionCapability", supportsAutomatedSubmission,
                supportsAutomatedSubmission
                        ? "Permitted automated submission connector active"
                        : "Connector supports manual submission fallback (" + capability.getReason() + ")"));

        if (!supportsAutomatedSubmission) {
            overallAllowed = false;
            suggestedState = WorkflowState.MANUAL_ACTION_REQUIRED;
        }

        return PreflightResult.builder()
                .allowed(overallAllowed)
                .suggestedState(suggestedState)
                .checks(checks)
                .timestamp(Instant.now())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreflightResult {
        private boolean allowed;
        private WorkflowState suggestedState;
        private List<CheckResult> checks;
        private Instant timestamp;
    }

    @Data
    @AllArgsConstructor
    public static class CheckResult {
        private String name;
        private boolean passed;
        private String details;
    }
}
