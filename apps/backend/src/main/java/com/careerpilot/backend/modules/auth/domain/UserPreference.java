package com.careerpilot.backend.modules.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreference {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private String theme = "dark";

    @Column(name = "email_notifications", nullable = false)
    @Builder.Default
    private boolean emailNotifications = true;

    @Column(name = "auto_apply", nullable = false)
    @Builder.Default
    private boolean autoApply = false;

    @Column(name = "work_style", nullable = false)
    @Builder.Default
    private String workStyle = "REMOTE";

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    @Column(name = "currency_code", length = 3, nullable = false)
    @Builder.Default
    private String currencyCode = "USD";

    @Column(name = "salary_period", length = 20, nullable = false)
    @Builder.Default
    private String salaryPeriod = "YEARLY";

    @Column(name = "employment_type", length = 50, nullable = false)
    @Builder.Default
    private String employmentType = "FULL_TIME";

    @Column(name = "job_alert_settings", nullable = false)
    @Builder.Default
    private boolean jobAlertSettings = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
