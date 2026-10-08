package com.careerpilot.backend.modules.followup.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** A user's OAuth mailbox connection. The refresh token is only ever stored encrypted. */
@Entity
@Table(name = "email_connections")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailConnection {
    public static final String CONNECTED = "CONNECTED";
    public static final String REVOKED = "REVOKED";
    public static final String ERROR = "ERROR";

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "email_address", length = 320)
    private String emailAddress;

    @JsonIgnore
    @Column(name = "encrypted_refresh_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Column(length = 1000)
    private String scopes;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
