package com.careerpilot.backend.modules.profile.domain;

import com.careerpilot.backend.modules.auth.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "social_links")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialLinks {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String linkedin;
    private String github;
    private String portfolio;
    private String twitter;
}
