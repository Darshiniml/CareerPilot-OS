package com.careerpilot.backend.modules.application.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_state_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationHistory {

    public static final String ACTOR_USER = "USER";
    public static final String ACTOR_SYSTEM = "SYSTEM";
    public static final String ACTOR_COMMUNICATION = "COMMUNICATION";

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state")
    private WorkflowState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false)
    private WorkflowState toState;

    private String reason;

    @Column(name = "actor_id")
    private UUID actorId;

    /** USER, SYSTEM or COMMUNICATION. */
    @Column(name = "actor_type", length = 20)
    private String actorType;

    /** HR communication whose evidence caused this change (COMMUNICATION actor only). */
    @Column(name = "source_communication_id")
    private UUID sourceCommunicationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
