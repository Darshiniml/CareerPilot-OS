package com.careerpilot.backend.modules.writing;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cover_letters")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoverLetter {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "resume_id")
    private UUID resumeId;

    @Column(name = "resume_version")
    private Integer resumeVersion;

    @Column(length = 40)
    private String tone;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Result of the fact check (unsupported numbers/technologies), from generation time. */
    @Column(name = "verification_json", columnDefinition = "TEXT")
    private String verificationJson;

    @Column(name = "edited_by_user", nullable = false)
    private boolean editedByUser;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
