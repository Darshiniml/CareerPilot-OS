package com.careerpilot.backend.modules.application.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_verifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationVerificationResult {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "verification_status", nullable = false)
    private String verificationStatus;

    @Column(name = "evidence_type", nullable = false)
    private String evidenceType;

    @Column(name = "evidence_reference", columnDefinition = "TEXT")
    private String evidenceReference;

    @Column(name = "confirmation_id")
    private String confirmationId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
