package com.careerpilot.backend.modules.agent.services;

import com.careerpilot.backend.modules.agent.domain.*;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationVerificationRepository;
import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.shared.events.ApplicationVerifiedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Reports the REAL evidence behind each submitted application. CareerPilot cannot query employers'
 * applicant-tracking systems, so nothing is marked verified without evidence. Evidence levels:
 * <ul>
 *   <li>EMPLOYER_EMAIL — a classified recruiter email about the application exists;</li>
 *   <li>PROVIDER_REFERENCE — the submission connector returned an external application id;</li>
 *   <li>CANDIDATE_ATTESTED — the candidate recorded their own confirmation;</li>
 *   <li>UNVERIFIED — no evidence yet.</li>
 * </ul>
 */
@Service
public class VerificationAgent implements CareerAgent {

    private static final Set<WorkflowState> SUBMITTED = EnumSet.of(WorkflowState.SUBMITTED,
            WorkflowState.SUBMITTED_VERIFIED, WorkflowState.VERIFICATION_PENDING, WorkflowState.SUBMISSION_UNVERIFIED);
    private static final Set<CommunicationClassification> EMPLOYER_SIGNALS = EnumSet.complementOf(
            EnumSet.of(CommunicationClassification.UNKNOWN));

    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationVerificationRepository verificationRepository;
    private final HrCommunicationRepository communicationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public VerificationAgent(ApplicationRecordRepository applicationRepository,
                             ApplicationVerificationRepository verificationRepository,
                             HrCommunicationRepository communicationRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.applicationRepository = applicationRepository;
        this.verificationRepository = verificationRepository;
        this.communicationRepository = communicationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getAgentId() {
        return "verification-agent";
    }

    @Override
    public String getName() {
        return "Submission Evidence Agent";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("EVIDENCE_VERIFICATION", "AUDITING");
    }

    @Override
    public List<String> getSupportedTaskTypes() {
        return List.of("VERIFICATION");
    }

    @Override
    public AgentResult execute(AgentContext context, AgentTask task) {
        UUID userId = context.getUserId();
        try {
            List<HrCommunication> emails = communicationRepository.findByCandidateIdOrderByReceivedAtDesc(userId);
            List<Map<String, Object>> report = new ArrayList<>();
            Map<String, Integer> byLevel = new TreeMap<>();
            for (ApplicationRecord app : applicationRepository.findByCandidateIdOrderByCreatedAtDesc(userId)) {
                if (!SUBMITTED.contains(app.getWorkflowState())) {
                    continue;
                }
                Optional<HrCommunication> employerEmail = emails.stream()
                        .filter(e -> app.getApplicationId().equals(e.getMatchedApplicationId()))
                        .filter(e -> e.getClassification() != null && EMPLOYER_SIGNALS.contains(e.getClassification()))
                        .findFirst();
                String level;
                String evidence;
                if (employerEmail.isPresent()) {
                    level = "EMPLOYER_EMAIL";
                    evidence = "Email \"" + employerEmail.get().getSubject() + "\" classified " + employerEmail.get().getClassification();
                    eventPublisher.publishEvent(ApplicationVerifiedEvent.builder()
                            .eventId(UUID.randomUUID())
                            .timestamp(Instant.now())
                            .correlationId(context.getCorrelationId() != null ? UUID.fromString(context.getCorrelationId()) : null)
                            .userId(userId)
                            .applicationId(app.getApplicationId())
                            .jobId(app.getJobId())
                            .status("EMPLOYER_EMAIL")
                            .build());
                } else if (app.getExternalApplicationId() != null) {
                    level = "PROVIDER_REFERENCE";
                    evidence = "External application id " + app.getExternalApplicationId();
                } else if (!verificationRepository.findByApplicationIdOrderByVerifiedAtDesc(app.getApplicationId()).isEmpty()) {
                    level = "CANDIDATE_ATTESTED";
                    evidence = "Confirmation recorded by the candidate";
                } else {
                    level = "UNVERIFIED";
                    evidence = "No confirmation email or reference yet";
                }
                byLevel.merge(level, 1, Integer::sum);
                Map<String, Object> row = new HashMap<>();
                row.put("applicationId", app.getApplicationId().toString());
                row.put("jobId", app.getJobId().toString());
                row.put("state", app.getWorkflowState().name());
                row.put("evidenceLevel", level);
                row.put("evidence", evidence);
                report.add(row);
            }
            Map<String, Object> outputData = new HashMap<>();
            outputData.put("applications", report);
            outputData.put("byEvidenceLevel", byLevel);
            return AgentResult.builder()
                    .status(AgentResult.Status.SUCCESS)
                    .message("Checked " + report.size() + " submitted applications: "
                            + byLevel.getOrDefault("EMPLOYER_EMAIL", 0) + " confirmed by an employer email, "
                            + byLevel.getOrDefault("UNVERIFIED", 0) + " without any evidence yet.")
                    .outputData(outputData)
                    .build();
        } catch (Exception e) {
            return AgentResult.builder()
                    .status(AgentResult.Status.FAILED)
                    .message("Failed during submission evidence check: " + e.getMessage())
                    .exception(e)
                    .build();
        }
    }

    @Override
    public AgentStatus getHealthStatus() {
        return AgentStatus.HEALTHY;
    }
}
