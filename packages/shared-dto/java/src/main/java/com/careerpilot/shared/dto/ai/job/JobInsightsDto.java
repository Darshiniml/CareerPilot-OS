package com.careerpilot.shared.dto.ai.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobInsightsDto {
    private List<String> engineering;
    private List<String> business;
    private List<String> hiring;
}
