package com.careerpilot.shared.dto.ai;

import jakarta.validation.constraints.NotBlank;
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
public class AiTaskRequestDto {
    private UUID taskId;
    
    @NotBlank(message = "Task type is required")
    private String taskType;
    
    private Map<String, Object> payload;
    private Map<String, Object> metadata;
}
