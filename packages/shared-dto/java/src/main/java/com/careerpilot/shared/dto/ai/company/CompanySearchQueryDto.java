package com.careerpilot.shared.dto.ai.company;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanySearchQueryDto {
    private String query;
    private List<String> industryFilter;
    private List<String> technologyFilter;
    private List<String> locationFilter;
    private List<String> companySizeFilter;
    private List<String> remotePolicyFilter;
    private Integer page;
    private Integer size;
    private Double scoreThreshold;
}
