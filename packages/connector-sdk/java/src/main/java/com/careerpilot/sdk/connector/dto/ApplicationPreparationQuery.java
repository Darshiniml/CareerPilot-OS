package com.careerpilot.sdk.connector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationPreparationQuery {
    private UUID userId;
    private String jobId;
    private String resumeUrl;
}
