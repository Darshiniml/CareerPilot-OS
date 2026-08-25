package com.careerpilot.backend.modules.discovery.services;

import com.careerpilot.backend.modules.agent.services.JobDiscoveryAgent;
import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.domain.JobSearchCriteria;
import com.careerpilot.backend.modules.opportunity.services.OpportunityPrioritizationService;
import com.careerpilot.backend.modules.application.domain.PlatformNotification;
import com.careerpilot.backend.modules.application.repositories.PlatformNotificationRepository;
import com.careerpilot.connector.sdk.DiscoveryContext;
import com.careerpilot.connector.sdk.SynchronizationResult;
import com.careerpilot.shared.dto.opportunity.OpportunityDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class DefaultDiscoveryScheduler implements DiscoveryScheduler {

    private final JobDiscoveryService service;
    private final UserRepository userRepository;
    private final JobDiscoveryAgent jobDiscoveryAgent;
    private final OpportunityPrioritizationService prioritizationService;
    private final PlatformNotificationRepository notificationRepository;

    @Value("${careerpilot.discovery.enabled:true}")
    private boolean enabled;

    @Value("${careerpilot.notification.min-priority:HIGH_PRIORITY}")
    private String minPriorityThreshold;

    public DefaultDiscoveryScheduler(JobDiscoveryService service,
                                     UserRepository userRepository,
                                     JobDiscoveryAgent jobDiscoveryAgent,
                                     OpportunityPrioritizationService prioritizationService,
                                     PlatformNotificationRepository notificationRepository) {
        this.service = service;
        this.userRepository = userRepository;
        this.jobDiscoveryAgent = jobDiscoveryAgent;
        this.prioritizationService = prioritizationService;
        this.notificationRepository = notificationRepository;
    }

    @Override
    public SynchronizationResult trigger(String id) {
        return service.discover(id, DiscoveryContext.builder().build());
    }

    @Override
    public List<SynchronizationResult> triggerAll() {
        return service.discoverAll();
    }

    @Scheduled(fixedDelayString = "${careerpilot.discovery.interval-ms:3600000}")
    public void periodicPolling() {
        if (!enabled) {
            log.info("[JOB-DISCOVERY] Scheduled job discovery is disabled.");
            return;
        }

        log.info("[JOB-DISCOVERY] Starting periodic job discovery polling...");
        triggerAll();

        // Match, Prioritize & Notify loop for all candidates
        List<User> users = userRepository.findAll();
        List<DiscoveryJob> allJobs = service.jobs();

        for (User user : users) {
            try {
                UUID candidateId = user.getId();
                JobSearchCriteria criteria = jobDiscoveryAgent.deriveCriteriaForUser(candidateId);

                List<DiscoveryJob> matchingJobs = allJobs.stream()
                        .filter(job -> matchesCriteria(job, criteria))
                        .toList();

                for (DiscoveryJob job : matchingJobs) {
                    OpportunityDto opp = prioritizationService.prioritize(candidateId, job);

                    if ("HIGH_PRIORITY".equalsIgnoreCase(opp.getPriorityLevel())) {
                        // Check if we already notified for this job and candidate
                        boolean alreadyNotified = notificationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream()
                                .anyMatch(n -> "NEW_HIGH_PRIORITY_JOB".equals(n.getType()) && n.getApplicationId().equals(job.getId()));

                        if (!alreadyNotified) {
                            String msg = String.format("%s at %s\nMatch: %d%%\nHistorical signal: %s\nRecommended action: %s",
                                    opp.getTitle(),
                                    opp.getCompany(),
                                    Math.round(opp.getMatchScore()),
                                    opp.getHistoricalConfidence().equals("SUFFICIENT") ? "Strong" : "Insufficient Data",
                                    opp.getRecommendedAction()
                            );

                            PlatformNotification notif = PlatformNotification.builder()
                                    .id(UUID.randomUUID())
                                    .candidateId(candidateId)
                                    .applicationId(job.getId()) // use jobId as applicationId safely
                                    .type("NEW_HIGH_PRIORITY_JOB")
                                    .title("New high-priority opportunity")
                                    .message(msg)
                                    .read(false)
                                    .createdAt(Instant.now())
                                    .build();
                            notificationRepository.save(notif);
                            log.info("[JOB-DISCOVERY] Created NEW_HIGH_PRIORITY_JOB notification for user={} job={}", candidateId, job.getId());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to process scheduled prioritization for user={}: {}", user.getId(), e.getMessage());
            }
        }
    }

    private boolean matchesCriteria(DiscoveryJob job, JobSearchCriteria criteria) {
        if (job == null) return false;

        String title = job.getTitle() != null ? job.getTitle().toLowerCase() : "";
        String loc = job.getLocation() != null ? job.getLocation().toLowerCase() : "";
        String content = job.getRawContent() != null ? job.getRawContent().toLowerCase() : "";

        boolean roleMatch = criteria.getPreferredRoles().stream()
                .anyMatch(role -> title.contains(role.toLowerCase()) || role.toLowerCase().contains(title));

        boolean locMatch = criteria.getLocations().stream()
                .anyMatch(l -> loc.contains(l.toLowerCase()) || l.toLowerCase().contains(loc));

        boolean keywordMatch = criteria.getKeywords().stream()
                .anyMatch(k -> title.contains(k.toLowerCase()) || content.contains(k.toLowerCase()));

        boolean skillMatch = criteria.getSkills().stream()
                .anyMatch(s -> title.contains(s.toLowerCase()) || content.contains(s.toLowerCase()));

        return roleMatch || locMatch || keywordMatch || skillMatch;
    }
}
