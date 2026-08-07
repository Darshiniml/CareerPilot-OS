package com.careerpilot.shared.dto.ai.company;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedValueDto<T> {
    private T value;
    private Double confidence;
    private String source;
}
