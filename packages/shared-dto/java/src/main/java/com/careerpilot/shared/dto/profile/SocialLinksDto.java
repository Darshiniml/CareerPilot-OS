package com.careerpilot.shared.dto.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialLinksDto {
    private UUID id;
    private String linkedin;
    private String github;
    private String portfolio;
    private String twitter;
}
