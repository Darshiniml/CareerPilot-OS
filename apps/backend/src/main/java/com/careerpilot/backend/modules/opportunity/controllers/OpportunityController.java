package com.careerpilot.backend.modules.opportunity.controllers;

import com.careerpilot.backend.modules.agent.services.JobDiscoveryAgent;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.services.ApplicationOrchestratorService;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.domain.JobSearchCriteria;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.backend.modules.opportunity.services.OpportunityPrioritizationService;
import com.careerpilot.shared.dto.opportunity.OpportunityDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/opportunities")
@Tag(name = "Opportunities Prioritization Queue", description = "Endpoints for candidate-scoped prioritized job discovery feed")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Slf4j
public class OpportunityController {

    private final DiscoveryJobRepository jobRepository;
    private final UserRepository userRepository;
    private final JobDiscoveryAgent jobDiscoveryAgent;
    private final OpportunityPrioritizationService prioritizationService;
    private final ApplicationOrchestratorService applicationOrchestratorService;

    @GetMapping
    @Operation(summary = "Get candidate-scoped personalized opportunity queue")
    public ResponseEntity<Page<OpportunityDto>> getOpportunities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String workMode,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Double minMatchScore,
            @RequestParam(required = false) String applicationStatus,
            Principal principal) {

        UUID candidateId = getUserId(principal);

        // 1. Derive JobSearchCriteria dynamically from candidate profile & resume
        JobSearchCriteria criteria = jobDiscoveryAgent.deriveCriteriaForUser(candidateId);

        // 2. Fetch all discovered job records
        List<DiscoveryJob> allJobs = jobRepository.findAll();

        // 3. Filter only jobs matching the candidate's personalized search criteria
        List<DiscoveryJob> personalizedJobs = allJobs.stream()
                .filter(job -> matchesCriteria(job, criteria))
                .toList();

        // 4. Calculate prioritization scores & levels for each matching job
        List<OpportunityDto> opportunities = new ArrayList<>();
        for (DiscoveryJob job : personalizedJobs) {
            try {
                OpportunityDto opp = prioritizationService.prioritize(candidateId, job);
                opportunities.add(opp);
            } catch (Exception e) {
                log.warn("Failed to prioritize job ID={} for candidate ID={}: {}", job.getId(), candidateId, e.getMessage());
            }
        }

        // 5. Apply filters
        List<OpportunityDto> filtered = opportunities.stream()
                .filter(opp -> {
                    if (search != null && !search.isBlank()) {
                        String cleanSearch = search.toLowerCase();
                        boolean match = (opp.getTitle() != null && opp.getTitle().toLowerCase().contains(cleanSearch))
                                || (opp.getCompany() != null && opp.getCompany().toLowerCase().contains(cleanSearch))
                                || (opp.getLocation() != null && opp.getLocation().toLowerCase().contains(cleanSearch));
                        if (!match) return false;
                    }
                    if (priority != null && !priority.isBlank()) {
                        if (!priority.equalsIgnoreCase(opp.getPriorityLevel())) return false;
                    }
                    if (role != null && !role.isBlank()) {
                        if (opp.getTitle() == null || !opp.getTitle().toLowerCase().contains(role.toLowerCase())) return false;
                    }
                    if (location != null && !location.isBlank()) {
                        if (opp.getLocation() == null || !opp.getLocation().toLowerCase().contains(location.toLowerCase())) return false;
                    }
                    if (workMode != null && !workMode.isBlank()) {
                        if (opp.getWorkMode() == null || !opp.getWorkMode().equalsIgnoreCase(workMode)) return false;
                    }
                    if (source != null && !source.isBlank()) {
                        String jobSource = opp.getSource();
                        String jobConnector = opp.getConnectorId();
                        boolean sourceMatch = (jobSource != null && jobSource.equalsIgnoreCase(source))
                                || (jobConnector != null && jobConnector.equalsIgnoreCase(source));
                        if (!sourceMatch) return false;
                    }
                    if (minMatchScore != null) {
                        if (opp.getMatchScore() < minMatchScore) return false;
                    }
                    if (applicationStatus != null && !applicationStatus.isBlank()) {
                        if (!applicationStatus.equalsIgnoreCase(opp.getApplicationStatus())) return false;
                    }
                    return true;
                })
                .sorted(Comparator.comparingDouble(OpportunityDto::getPriorityScore).reversed())
                .collect(Collectors.toList());

        // 6. Pagination
        int start = page * size;
        if (start > filtered.size()) {
            return ResponseEntity.ok(new PageImpl<>(List.of(), PageRequest.of(page, size), filtered.size()));
        }
        int end = Math.min(start + size, filtered.size());
        List<OpportunityDto> content = filtered.subList(start, end);

        return ResponseEntity.ok(new PageImpl<>(content, PageRequest.of(page, size), filtered.size()));
    }

    @PostMapping("/{jobId}/applications")
    @Operation(summary = "Create a candidate-scoped application from an opportunity")
    public ResponseEntity<ApplicationRecord> createApplication(@PathVariable UUID jobId, Principal principal) {
        UUID candidateId = getUserId(principal);
        DiscoveryJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Opportunity not found"));
        ApplicationRecord application = applicationOrchestratorService.createApplication(
                candidateId,
                null,
                job.getId(),
                job.getConnectorId(),
                java.util.Map.of("source", "opportunity-workspace", "applyUrl", job.getSourceUrl() == null ? "" : job.getSourceUrl()));
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(application);
    }

    private boolean matchesCriteria(DiscoveryJob job, JobSearchCriteria criteria) {
        if (job == null) return false;

        String title = job.getTitle() != null ? job.getTitle().toLowerCase() : "";
        String loc = job.getLocation() != null ? job.getLocation().toLowerCase() : "";
        String content = job.getRawContent() != null ? job.getRawContent().toLowerCase() : "";

        // 1. Check title/role match
        if (criteria == null) return false;
        boolean roleMatch = criteria.getPreferredRoles().stream()
                .anyMatch(role -> title.contains(role.toLowerCase()) || role.toLowerCase().contains(title));

        // 2. Check location match
        boolean locMatch = criteria.getLocations().stream()
                .anyMatch(l -> loc.contains(l.toLowerCase()) || l.toLowerCase().contains(loc));

        // 3. Check keywords/skills match
        boolean keywordMatch = criteria.getKeywords().stream()
                .anyMatch(k -> title.contains(k.toLowerCase()) || content.contains(k.toLowerCase()));

        boolean skillMatch = criteria.getSkills().stream()
                .anyMatch(s -> title.contains(s.toLowerCase()) || content.contains(s.toLowerCase()));

        return roleMatch || locMatch || keywordMatch || skillMatch;
    }

    private UUID getUserId(Principal principal) {
        if (principal == null) throw new SecurityException("Unauthorized");
        return userRepository.findByEmail(principal.getName())
                .map(User::getId)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }
}
