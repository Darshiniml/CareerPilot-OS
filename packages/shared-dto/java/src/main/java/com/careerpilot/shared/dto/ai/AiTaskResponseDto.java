package com.careerpilot.shared.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskResponseDto {
    private UUID taskId;
    private String status;
    private String provider;
    private long executionTimeMs;
    private Map<String, Object> result;
    private Map<String, Object> metadata;
}
