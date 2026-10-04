package com.careerpilot.backend.modules.communication.adapters.in.web;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.application.domain.ApplicationHistory;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.ApplicationTimelineEvent;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationHistoryRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationTimelineEventRepository;
import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.communication.domain.CommunicationClassification;
import com.careerpilot.backend.modules.communication.domain.CommunicationProcessingStatus;
import com.careerpilot.backend.modules.communication.domain.CommunicationProvider;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;
import com.careerpilot.backend.modules.communication.repositories.HrCommunicationRepository;
import com.careerpilot.backend.modules.communication.services.HrCommunicationService;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Milestone 22.4: deterministic processing of M22.3 classifications into
 * application timeline events and canonical workflow-state transitions. Real persistence (H2 test
 * profile), real web layer, real matcher/ingestion; the AI gateway is the project's standard mock
 * test adapter so classification results are controlled without a live provider.
 */
@SpringBootTest(properties = {"careerpilot.discovery.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunicationProcessingControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private HrCommunicationService communicationService;
    @Autowired private HrCommunicationRepository communicationRepository;
    @Autowired private DiscoveryJobRepository jobRepository;
    @Autowired private ApplicationRecordRepository applicationRepository;
    @Autowired private ApplicationHistoryRepository historyRepository;
    @Autowired private ApplicationTimelineEventRepository timelineEventRepository;

    @MockBean private AiGatewayClient aiGatewayClient;

    // ---------- fixtures ----------

    /** Fetch-or-create so the principal named by @WithMockUser resolves to a real user (H2 file DB persists across runs). */
    private UUID ensureUser(String email) {
        return userRepository.findByEmail(email).map(User::getId).orElseGet(() ->
                userRepository.save(User.builder()
                        .id(UUID.randomUUID())
                        .email(email)
                        .passwordHash("hash")
                        .firstName("Timeline")
                        .lastName("Tester")
                        .build()).getId());
    }

    private String uniqueEmail(String label) {
        return "m224." + label + "." + UUID.randomUUID() + "@example.test";
    }

    private String emailOf(UUID userId) {
        return userRepository.findById(userId).orElseThrow().getEmail();
    }

    private DiscoveryJob createJob() {
        return jobRepository.save(DiscoveryJob.builder()
                .id(UUID.randomUUID())
                .externalId("ext-" + UUID.randomUUID())
                .connectorId("test-connector")
                .sourceUrl("https://acme-corp.example/jobs/1")
                .title("Senior Backend Engineer")
                .company("Acme Corp")
                .contentHash(UUID.randomUUID().toString())
                .rawContent("Senior Backend Engineer at Acme Corp")
                .discoveredAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private ApplicationRecord createApplication(UUID candidateId, DiscoveryJob job, WorkflowState state) {
        return applicationRepository.save(ApplicationRecord.builder()
                .applicationId(UUID.randomUUID())
                .candidateId(candidateId)
                .jobId(job.getId())
                .workflowState(state)
                .externalApplicationId("EXT-APP-" + UUID.randomUUID())
                .retryCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    /** Ingests a communication whose body carries the application's external id — deterministic match evidence. */
    private HrCommunication createMatchedCommunication(UUID candidateId, ApplicationRecord application,
                                                       String subject, String body, Instant receivedAt) {
        HrCommunication ingested = communicationService.ingest(
                candidateId,
                CommunicationProvider.GMAIL,
                "msg-" + UUID.randomUUID(),
                "thread-" + UUID.randomUUID(),
                "recruiter@acme-corp.example",
                emailOf(candidateId),
                subject,
                body + " Reference: " + application.getExternalApplicationId(),
                receivedAt);
        assertEquals(application.getApplicationId(), ingested.getMatchedApplicationId(),
                "fixture communication must be matched to the prepared application");
        return ingested;
    }

    private HrCommunication createUnmatchedCommunication(UUID candidateId) {
        return communicationService.ingest(
                candidateId,
                CommunicationProvider.GMAIL,
                "msg-" + UUID.randomUUID(),
                "thread-" + UUID.randomUUID(),
                "newsletter@unrelated-digest.example",
                emailOf(candidateId),
                "Weekly digest",
                "Your weekly roundup of nothing in particular.",
                Instant.now());
    }

    private void setClassification(HrCommunication communication, CommunicationClassification classification,
                                   Double confidence, CommunicationProcessingStatus status) {
        communication.setClassification(classification);
        communication.setClassificationConfidence(confidence);
        communication.setClassificationReason("classified as " + classification + " from evidence phrases: \"test evidence\"");
        communication.setProcessingStatus(status);
        communication.setUpdatedAt(Instant.now());
        communicationRepository.save(communication);
    }

    private void stubAi(String classification, double confidence) {
        Map<String, Object> result = new HashMap<>();
        result.put("classification", classification);
        result.put("confidence", confidence);
        result.put("evidence", "\"invite you to interview\"");
        result.put("reason", "interview scheduling language");
        result.put("signals", List.of("interview"));
        when(aiGatewayClient.executeTask(any(AiTaskRequestDto.class)))
                .thenReturn(AiTaskResponseDto.builder()
                        .taskId(UUID.randomUUID())
                        .status("COMPLETED")
                        .provider("hr-communication-classifier")
                        .result(result)
                        .metadata(new HashMap<>())
                        .build());
    }

    private JsonNode process(MockMvc mvc, UUID communicationId, String expectedOutcome) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/communications/" + communicationId + "/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value(expectedOutcome))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ---------- 1. valid classification -> valid state transition ----------

    @Test
    @WithMockUser(username = "m224-valid@example.test", roles = "USER")
    void validClassificationProducesStateTransitionAndTimelineEvent() throws Exception {
        UUID user = ensureUser("m224-valid@example.test");
        String email = emailOf(user);
        DiscoveryJob job = createJob();
        ApplicationRecord application = createApplication(user, job, WorkflowState.DISCOVERED);
        HrCommunication communication = createMatchedCommunication(user, application,
                "Interview invitation", "We would like to invite you to interview with the team.", Instant.now());
        stubAi("INTERVIEW_INVITATION", 0.92);

        // Real M22.3 classification path.
        mockMvc.perform(post("/api/v1/communications/" + communication.getId() + "/classify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("INTERVIEW_INVITATION"));

        JsonNode response = process(mockMvc, communication.getId(), "STATE_TRANSITIONED");
        assertEquals(application.getApplicationId().toString(), response.get("applicationId").asText());
        assertEquals("INTERVIEW_INVITATION", response.get("eventType").asText());
        assertTrue(response.get("stateChanged").asBoolean());
        assertEquals("DISCOVERED", response.get("previousState").asText());
        assertEquals("INTERVIEW", response.get("newState").asText());
        assertEquals("INTERVIEW", response.get("applicationState").asText());
        assertFalse(response.get("timelineEventId").isNull());

        assertEquals(WorkflowState.INTERVIEW,
                applicationRepository.findById(application.getApplicationId()).orElseThrow().getWorkflowState());

        List<ApplicationHistory> history = historyRepository
                .findByApplicationIdOrderByCreatedAtAsc(application.getApplicationId());
        assertEquals(1, history.size());
        assertEquals(WorkflowState.INTERVIEW, history.get(0).getToState());

        List<ApplicationTimelineEvent> events = timelineEventRepository
                .findByApplicationIdOrderByEventTimestampAscIdAsc(application.getApplicationId());
        assertEquals(1, events.size());
        ApplicationTimelineEvent event = events.get(0);
        assertEquals(communication.getId(), event.getCommunicationId());
        assertEquals(CommunicationClassification.INTERVIEW_INVITATION, event.getClassification());
        assertEquals(0.92, event.getClassificationConfidence());
        assertNotNull(event.getEvidence());
        // Authoritative event time is the communication's received time (H2 test-profile timezone
        // handling shifts absolute Instants, so only presence is asserted here; chronological
        // ordering by received time is asserted in the out-of-order test).
        assertNotNull(event.getEventTimestamp());
    }

    // ---------- 2. valid classification -> timeline event only ----------

    @Test
    @WithMockUser(username = "m224-eventonly@example.test", roles = "USER")
    void eventOnlyClassificationRecordsTimelineWithoutStateChange() throws Exception {
        UUID user = ensureUser("m224-eventonly@example.test");
        DiscoveryJob job = createJob();
        ApplicationRecord application = createApplication(user, job, WorkflowState.INTERVIEW);
        HrCommunication communication = createMatchedCommunication(user, application,
                "Rescheduling", "We need to reschedule our interview to a different time slot.", Instant.now());
        setClassification(communication, CommunicationClassification.INTERVIEW_RESCHEDULED, 0.8,
                CommunicationProcessingStatus.PROCESSED);

        JsonNode response = process(mockMvc, communication.getId(), "EVENT_RECORDED");
        assertFalse(response.get("stateChanged").asBoolean());
        assertTrue(response.get("newState").isNull());
        assertEquals("INTERVIEW", response.get("applicationState").asText());

        assertEquals(WorkflowState.INTERVIEW,
                applicationRepository.findById(application.getApplicationId()).orElseThrow().getWorkflowState());
        assertEquals(1, timelineEventRepository
                .findByApplicationIdOrderByEventTimestampAscIdAsc(application.getApplicationId()).size());
        assertTrue(historyRepository.findByApplicationIdOrderByCreatedAtAsc(application.getApplicationId()).isEmpty());
    }

    // ---------- 3 + 16. invalid transition rejected; classification preserved ----------

    @Test
    @WithMockUser(username = "m224-invalid@example.test", roles = "USER")
    void invalidTransitionIsRejectedWithoutMutationAndPreservesClassification() throws Exception {
        UUID user = ensureUser("m224-invalid@example.test");
        DiscoveryJob job = createJob();
        ApplicationRecord application = createApplication(user, job, WorkflowState.INTERVIEW);
        HrCommunication communication = createMatchedCommunication(user, application,
                "Application update", "Our team is reviewing your application.", Instant.now());
        setClassification(communication, CommunicationClassification.APPLICATION_UNDER_REVIEW, 0.85,
                CommunicationProcessingStatus.PROCESSED);

        JsonNode response = process(mockMvc, communication.getId(), "INVALID_TRANSITION_REJECTED");
        assertFalse(response.get("stateChanged").asBoolean());
        assertEquals("INTERVIEW", response.get("applicationState").asText());

        assertEquals(WorkflowState.INTERVIEW,
                applicationRepository.findById(application.getApplicationId()).orElseThrow().getWorkflowState());
        assertTrue(historyRepository.findByApplicationIdOrderByCreatedAtAsc(application.getApplicationId()).isEmpty());

        // The M22.3 classification remains fully intact after the failed transition.
        mockMvc.perform(get("/api/v1/communications/" + communication.getId() + "/classification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("APPLICATION_UNDER_REVIEW"))
                .andExpect(jsonPath("$.confidence").value(0.85));
        HrCommunication reloaded = communicationRepository.findById(communication.getId()).orElseThrow();
        assertEquals(CommunicationClassification.APPLICATION_UNDER_REVIEW, reloaded.getClassification());
        assertEquals(0.85, reloaded.getClassificationConfidence());
    }

    // ---------- 4 + 5. idempotent repeated processing ----------

    @Test
    @WithMockUser(username = "m224-idempotent@example.test", roles = "USER")
    void repeatedProcessingIsIdempotent() throws Exception {
        UUID user = ensureUser("m224-idempotent@example.test");
        DiscoveryJob job = createJob();
        ApplicationRecord application = createApplication(user, job, WorkflowState.SUBMITTED);
        HrCommunication communication = createMatchedCommunication(user, application,
                "Interview invitation", "We would like to invite you to interview.", Instant.now());
        stubAi("INTERVIEW_INVITATION", 0.9);
        mockMvc.perform(post("/api/v1/communications/" + communication.getId() + "/classify"))
                .andExpect(status().isOk());

        JsonNode first = process(mockMvc, communication.getId(), "STATE_TRANSITIONED");
        JsonNode second = process(mockMvc, communication.getId(), "STATE_TRANSITIONED");
        JsonNode third = process(mockMvc, communication.getId(), "STATE_TRANSITIONED");

        assertEquals(first.get("timelineEventId").asText(), second.get("timelineEventId").asText());
        assertEquals(first.get("timelineEventId").asText(), third.get("timelineEventId").asText());
        assertEquals(1, timelineEventRepository
                .findByApplicationIdOrderByEventTimestampAscIdAsc(application.getApplicationId()).size());
        assertEquals(1, historyRepository
                .findByApplicationIdOrderByCreatedAtAsc(application.getApplicationId()).size());
        assertEquals(WorkflowState.INTERVIEW,
                applicationRepository.findById(application.getApplicationId()).orElseThrow().getWorkflowState());
    }

    // ---------- 6. candidate isolation: processing and timeline ----------

    @Test
    void candidateBCannotProcessOrReadCandidateAData() throws Exception {
        UUID userA = ensureUser(uniqueEmail("cross-a"));
        UUID userB = ensureUser(uniqueEmail("cross-b"));
        String emailB = emailOf(userB);
        DiscoveryJob job = createJob();
        ApplicationRecord application = createApplication(userA, job, WorkflowState.SUBMITTED);
        HrCommunication communication = createMatchedCommunication(userA, application,
                "Interview invitation", "We would like to invite you to interview.", Instant.now());
        setClassification(communication, CommunicationClassification.INTERVIEW_INVITATION, 0.9,
                CommunicationProcessingStatus.PROCESSED);

        mockMvc.perform(post("/api/v1/communications/" + communication.getId() + "/process")
                        .with(user(emailB)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/applications/" + application.getApplicationId() + "/timeline")
                        .with(user(emailB)))
                .andExpect(status().isForbidden());

        assertTrue(timelineEventRepository
                .findByApplicationIdOrderByEventTimestampAscIdAsc(application.getApplicationId()).isEmpty());
        assertEquals(WorkflowState.SUBMITTED,
                applicationRepository.findById(application.getApplicationId()).orElseThrow().getWorkflowState());
    }

    // ---------- 7. unmatched communication ----------

    @Test
    @WithMockUser(username = "m224-unmatched@example.test", roles = "USER")
    void unmatchedCommunicationNeverMutatesAnApplication() throws Exception {
        UUID user = ensureUser("m224-unmatched@example.test");
        DiscoveryJob job = createJob();
        ApplicationRecord application = createApplication(user, job, WorkflowState.SUBMITTED);
        HrCommunication communication = createUnmatchedCommunication(user);
        assertNull(communication.getMatchedApplicationId());

        stubAi("INTERVIEW_INVITATION", 0.95);
        mockMvc.perform(post("/api/v1/communications/" + communication.getId() + "/classify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("INTERVIEW_INVITATION"));

        JsonNode response = process(mockMvc, communication.getId(), "UNMATCHED_NO_APPLICATION");
        assertTrue(response.get("applicationId").isNull());
        assertFalse(response.get("stateChanged").asBoolean());

        assertEquals(WorkflowState.SUBMITTED,
                applicationRepository.findById(application.getApplicationId()).orElseThrow().getWorkflowState());
        assertTrue(timelineEventRepository
                .findByApplicationIdOrderByEventTimestampAscIdAsc(application.getApplicationId()).isEmpty());
    }

    // ---------- 8. low-confidence classification withheld (with boundary) ----------

    @Test
    @WithMockUser(username = "m224-lowconf@example.test", roles = "USER")
    void lowConfidenceClassificationIsWithheldAtBoundary() throws Exception {
        UUID user = ensureUser("m224-lowconf@example.test");
        DiscoveryJob job = createJob();
        ApplicationRecord application = createApplication(user, job, WorkflowState.SUBMITTED);

        HrCommunication weak = createMatchedCommunication(user, application,
                "Maybe interview", "Something about an interview perhaps.", Instant.now());
        setClassification(weak, CommunicationClassification.INTERVIEW_INVITATION, 0.49,
                CommunicationProcessingStatus.PROCESSED);

        JsonNode withheld = process(mockMvc, weak.getId(), "WITHHELD_LOW_CONFIDENCE");
        assertFalse(withheld.get("stateChanged").asBoolean());
        assertEquals(WorkflowState.SUBMITTED,
                applicationRepository.findById(application.getApplicationId()).orElseThrow().getWorkflowState());
        assertTrue(timelineEventRepository
                .findByApplicationIdOrderByEventTimestampAscIdAsc(application.getApplicationId()).isEmpty());
        // Classification itself remains stored.
        assertEquals(CommunicationClassification.INTERVIEW_INVITATION,
                communicationRepository.findById(weak.getId()).orElseThrow().getClassification());

        // Boundary: exactly 0.5 is eligible.
        HrCommunication decisive = createMatchedCommunication(user, application,
                "Interview invitation", "We would like to invite you to interview.", Instant.now());
        setClassification(decisive, CommunicationClassification.INTERVIEW_INVITATION, 0.5,
                CommunicationProcessingStatus.PROCESSED);
        process(mockMvc, decisive.getId(), "STATE_TRANSITIONED");
        assertEquals(WorkflowState.INTERVIEW,
                applicationRepository.findById(application.getApplicationId()).orElseThrow().getWorkflowState());
    }

    // ---------- 9. terminal-state protection ----------

    @Test
    @WithMockUser(username = "m224-terminal@example.test", roles = "USER")
    void terminalStateIsNeverOverwrittenByConflictingEvidence() throws Exception {
        UUID user = ensureUser("m224-terminal@example.test");
        DiscoveryJob job = createJob();
        ApplicationRecord application = createApplication(user, job, WorkflowState.REJECTED);
        HrCommunication communication = createMatchedCommunication(user, application,
                "Offer", "We are pleased to offer you the position.", Instant.now());
        setClassification(communication, CommunicationClassification.OFFER, 0.95,
                CommunicationProcessingStatus.PROCESSED);

        JsonNode response = process(mockMvc, communication.getId(), "TERMINAL_STATE_PROTECTED");
        assertFalse(response.get("stateChanged").asBoolean());
        assertEquals("REJECTED", response.get("applicationState").asText());

        assertEquals(WorkflowState.REJECTED,
                applicationRepository.findById(application.getApplicationId()).orElseThrow().getWorkflowState());
        // Evidence is still preserved on the timeline.
        assertEquals(1, timelineEventRepository
                .findByApplicationIdOrderByEventTimestampAscIdAsc(application.getApplicationId()).size());
    }

    // ---------- 10. already-in-target-state ----------

    @Test
    @WithMockUser(username = "m224-already@example.test", roles = "USER")
    void alreadyInTargetStateRecordsEventWithoutTransition() throws Exception {
        UUID user = ensureUser("m224-already@example.test");
        DiscoveryJob job = createJob();
        ApplicationRecord application = createApplication(user, job, WorkflowState.INTERVIEW);
        HrCommunication communication = createMatchedCommunication(user, application,
                "Interview invitation", "We would like to invite you to interview.", Instant.now());
        setClassification(communication, CommunicationClassification.INTERVIEW_INVITATION, 0.9,
                CommunicationProcessingStatus.PROCESSED);

        JsonNode response = process(mockMvc, communication.getId(), "ALREADY_IN_TARGET_STATE");
        assertFalse(response.get("stateChanged").asBoolean());
        assertEquals("INTERVIEW", response.get("applicationState").asText());
        assertTrue(historyRepository.findByApplicationIdOrderByCreatedAtAsc(application.getApplicationId()).isEmpty());
    }

    // ---------- 11 + 13 + 18. out-of-order communications & deterministic ordering ----------

    @Test
    @WithMockUser(username = "m224-outoforder@example.test", roles = "USER")
    void outOfOrderCommunicationDoesNotRegressStateAndTimelineFollowsEventTime() throws Exception {
        UUID user = ensureUser("m224-outoforder@example.test");
        DiscoveryJob job = createJob();
        ApplicationRecord application = createApplication(user, job, WorkflowState.SUBMITTED);
        Instant later = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant earlier = later.minus(5, ChronoUnit.DAYS);

        HrCommunication interview = createMatchedCommunication(user, application,
                "Interview invitation", "We would like to invite you to interview.", later);
        setClassification(interview, CommunicationClassification.INTERVIEW_INVITATION, 0.9,
                CommunicationProcessingStatus.PROCESSED);
        HrCommunication review = createMatchedCommunication(user, application,
                "Application update", "Our team is reviewing your application.", earlier);
        setClassification(review, CommunicationClassification.APPLICATION_UNDER_REVIEW, 0.85,
                CommunicationProcessingStatus.PROCESSED);

        // Interview (received later) is processed first.
        process(mockMvc, interview.getId(), "STATE_TRANSITIONED");
        // The older UNDER_REVIEW evidence arrives afterwards and must not regress the state.
        process(mockMvc, review.getId(), "INVALID_TRANSITION_REJECTED");
        assertEquals(WorkflowState.INTERVIEW,
                applicationRepository.findById(application.getApplicationId()).orElseThrow().getWorkflowState());

        // Timeline is ordered by authoritative event time: the earlier UNDER_REVIEW event comes first.
        MvcResult result = mockMvc.perform(get("/api/v1/applications/" + application.getApplicationId() + "/timeline"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode timeline = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> communicationEventTypes = new java.util.ArrayList<>();
        List<String> timestamps = new java.util.ArrayList<>();
        for (JsonNode entry : timeline) {
            if ("COMMUNICATION".equals(entry.get("source").asText())) {
                communicationEventTypes.add(entry.get("eventType").asText());
            }
            timestamps.add(entry.get("timestamp").asText());
        }
        assertEquals(List.of("APPLICATION_UNDER_REVIEW", "INTERVIEW_INVITATION"), communicationEventTypes);
        List<String> sorted = new java.util.ArrayList<>(timestamps);
        java.util.Collections.sort(sorted);
        assertEquals(sorted, timestamps, "timeline must be in ascending timestamp order");
    }

    // ---------- 12. conflicting communications ----------

    @Test
    @WithMockUser(username = "m224-conflict@example.test", roles = "USER")
    void conflictingCommunicationsPreserveBothPiecesOfEvidence() throws Exception {
        UUID user = ensureUser("m224-conflict@example.test");
        DiscoveryJob job = createJob();
        ApplicationRecord application = createApplication(user, job, WorkflowState.INTERVIEW);

        HrCommunication offer = createMatchedCommunication(user, application,
                "Offer", "We are pleased to offer you the position.", Instant.now().minus(2, ChronoUnit.HOURS));
        setClassification(offer, CommunicationClassification.OFFER, 0.95, CommunicationProcessingStatus.PROCESSED);
        HrCommunication rejection = createMatchedCommunication(user, application,
                "Candidacy update", "We regret to inform you we will not be moving forward.", Instant.now());
        setClassification(rejection, CommunicationClassification.REJECTION, 0.9, CommunicationProcessingStatus.PROCESSED);

        process(mockMvc, offer.getId(), "STATE_TRANSITIONED");
        process(mockMvc, rejection.getId(), "TERMINAL_STATE_PROTECTED");

        assertEquals(WorkflowState.OFFER,
                applicationRepository.findById(application.getApplicationId()).orElseThrow().getWorkflowState());
        List<ApplicationTimelineEvent> events = timelineEventRepository
                .findByApplicationIdOrderByEventTimestampAscIdAsc(application.getApplicationId());
        assertEquals(2, events.size(), "both conflicting pieces of evidence must be preserved");
    }

    // ---------- 14. timeline provenance & determinism ----------

    @Test
    @WithMockUser(username = "m224-provenance@example.test", roles = "USER")
    void timelineExposesProvenanceAndIsDeterministic() throws Exception {
        UUID user = ensureUser("m224-provenance@example.test");
        DiscoveryJob job = createJob();
        ApplicationRecord application = createApplication(user, job, WorkflowState.SUBMITTED);
        HrCommunication communication = createMatchedCommunication(user, application,
                "Assessment", "Please complete the assessment via the assessment link.", Instant.now());
        setClassification(communication, CommunicationClassification.ASSESSMENT_REQUEST, 0.88,
                CommunicationProcessingStatus.PROCESSED);
        process(mockMvc, communication.getId(), "STATE_TRANSITIONED");

        MvcResult first = mockMvc.perform(get("/api/v1/applications/" + application.getApplicationId() + "/timeline"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult second = mockMvc.perform(get("/api/v1/applications/" + application.getApplicationId() + "/timeline"))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(first.getResponse().getContentAsString(), second.getResponse().getContentAsString(),
                "timeline retrieval must be deterministic");

        JsonNode timeline = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode communicationEntry = null;
        for (JsonNode entry : timeline) {
            if ("COMMUNICATION".equals(entry.get("source").asText())) {
                communicationEntry = entry;
            }
        }
        assertNotNull(communicationEntry, "communication-derived event must appear in the timeline");
        assertEquals("ASSESSMENT_REQUEST", communicationEntry.get("eventType").asText());
        assertEquals("STATE_TRANSITIONED", communicationEntry.get("outcome").asText());
        assertEquals(communication.getId().toString(), communicationEntry.get("communicationId").asText());
        assertEquals("ASSESSMENT_REQUEST", communicationEntry.get("classification").asText());
        assertEquals(0.88, communicationEntry.get("classificationConfidence").asDouble());
        assertFalse(communicationEntry.get("evidence").asText().isBlank());
        assertEquals("SUBMITTED", communicationEntry.get("fromState").asText());
        assertEquals("ASSESSMENT", communicationEntry.get("toState").asText());
        assertTrue(communicationEntry.get("stateChanged").asBoolean());
        assertFalse(communicationEntry.get("timestamp").asText().isBlank(),
                "timeline entry must carry the persisted authoritative event time");
    }

    // ---------- 15. client cannot supply authoritative state ----------

    @Test
    @WithMockUser(username = "m224-inject@example.test", roles = "USER")
    void clientCannotSupplyAuthoritativeState() throws Exception {
        UUID user = ensureUser("m224-inject@example.test");
        UUID otherUser = ensureUser(uniqueEmail("inject-other"));
        DiscoveryJob job = createJob();
        ApplicationRecord application = createApplication(user, job, WorkflowState.INTERVIEW);
        HrCommunication communication = createMatchedCommunication(user, application,
                "Rescheduling", "We need to reschedule our interview.", Instant.now());
        setClassification(communication, CommunicationClassification.INTERVIEW_RESCHEDULED, 0.8,
                CommunicationProcessingStatus.PROCESSED);

        Map<String, Object> hostileBody = new HashMap<>();
        hostileBody.put("newState", "OFFER");
        hostileBody.put("previousState", "DISCOVERED");
        hostileBody.put("candidateId", otherUser.toString());
        hostileBody.put("applicationId", UUID.randomUUID().toString());
        hostileBody.put("eventType", "OFFER");
        hostileBody.put("actorId", otherUser.toString());

        MvcResult result = mockMvc.perform(post("/api/v1/communications/" + communication.getId() + "/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hostileBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("EVENT_RECORDED"))
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(response.get("newState").isNull(), "client-supplied newState must be ignored");
        assertEquals("INTERVIEW", response.get("applicationState").asText());
        assertEquals(application.getApplicationId().toString(), response.get("applicationId").asText());

        assertEquals(WorkflowState.INTERVIEW,
                applicationRepository.findById(application.getApplicationId()).orElseThrow().getWorkflowState());
    }

    // ---------- 17. not-classified / unknown communications ----------

    @Test
    @WithMockUser(username = "m224-unclassified@example.test", roles = "USER")
    void unclassifiedAndUnknownCommunicationsAreHandledDeterministically() throws Exception {
        UUID user = ensureUser("m224-unclassified@example.test");
        DiscoveryJob job = createJob();
        ApplicationRecord application = createApplication(user, job, WorkflowState.SUBMITTED);

        // Matched but never classified (ingestion leaves classification UNKNOWN).
        HrCommunication neverClassified = createMatchedCommunication(user, application,
                "Hello", "Some message with no classification yet.", Instant.now());
        process(mockMvc, neverClassified.getId(), "NO_ACTIONABLE_CLASSIFICATION");

        // Failed classification (M22.3 failure path) must not be processed.
        HrCommunication failed = createMatchedCommunication(user, application,
                "Hello again", "Another message.", Instant.now());
        setClassification(failed, CommunicationClassification.UNKNOWN, null, CommunicationProcessingStatus.FAILED);
        process(mockMvc, failed.getId(), "NOT_CLASSIFIED");

        assertEquals(WorkflowState.SUBMITTED,
                applicationRepository.findById(application.getApplicationId()).orElseThrow().getWorkflowState());
        assertTrue(timelineEventRepository
                .findByApplicationIdOrderByEventTimestampAscIdAsc(application.getApplicationId()).isEmpty());
    }

    // ---------- 404 for unknown communication ----------

    @Test
    @WithMockUser(username = "m224-unknown@example.test", roles = "USER")
    void unknownCommunicationReturnsNotFound() throws Exception {
        ensureUser("m224-unknown@example.test");
        mockMvc.perform(post("/api/v1/communications/" + UUID.randomUUID() + "/process"))
                .andExpect(status().isNotFound());
    }
}
