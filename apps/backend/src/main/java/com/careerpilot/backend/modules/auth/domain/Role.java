package com.careerpilot.backend.modules.auth.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;
}
