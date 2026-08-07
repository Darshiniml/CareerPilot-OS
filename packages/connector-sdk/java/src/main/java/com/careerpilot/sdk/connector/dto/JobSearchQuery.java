package com.careerpilot.sdk.connector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSearchQuery {
    private String keywords;
    private String location;
    private int page;
    private int pageSize;
    private Map<String, Object> additionalFilters;
}
