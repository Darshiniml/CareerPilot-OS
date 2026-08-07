package com.careerpilot.shared.dto.ai.resume;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeValidationReportDto {
    private UUID id;
    private UUID documentId;
    private boolean hasEmail;
    private boolean hasPhone;
    private boolean hasLinkedin;
    private List<String> validationWarnings;
}
