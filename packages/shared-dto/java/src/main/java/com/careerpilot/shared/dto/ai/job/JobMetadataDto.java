package com.careerpilot.shared.dto.ai.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMetadataDto {
    private String primaryLanguage;
    private String cloud;
    private String industry;
    private String experience;
    private Boolean remote;
    private Boolean internship;
}
