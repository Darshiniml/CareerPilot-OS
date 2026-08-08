package com.careerpilot.backend.modules.agent.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentPolicy {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "max_applications_per_day", nullable = false)
    @Builder.Default
    private Integer maxApplicationsPerDay = 5;

    @Column(name = "minimum_match_score", nullable = false)
    @Builder.Default
    private Double minimumMatchScore = 70.0;

    @Column(name = "allowed_employment_types", nullable = false)
    @Builder.Default
    private String allowedEmploymentTypes = "FULL_TIME,PART_TIME,CONTRACT";

    @Column(name = "allowed_locations", nullable = false, length = 500)
    @Builder.Default
    private String allowedLocations = "REMOTE,HYBRID,BANGALORE";

    @Column(name = "allowed_remote_types", nullable = false)
    @Builder.Default
    private String allowedRemoteTypes = "REMOTE,HYBRID,ON_SITE";

    @Column(name = "allowed_companies", columnDefinition = "TEXT")
    private String allowedCompanies;

    @Column(name = "blocked_companies", columnDefinition = "TEXT")
    private String blockedCompanies;

    @Column(name = "require_approval", nullable = false)
    @Builder.Default
    private Boolean requireApproval = true;

    @Column(name = "allow_automatic_submission", nullable = false)
    @Builder.Default
    private Boolean allowAutomaticSubmission = false;

    @Column(name = "allow_reference_research", nullable = false)
    @Builder.Default
    private Boolean allowReferenceResearch = true;

    @Column(name = "allow_external_connectors", nullable = false)
    @Builder.Default
    private Boolean allowExternalConnectors = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
