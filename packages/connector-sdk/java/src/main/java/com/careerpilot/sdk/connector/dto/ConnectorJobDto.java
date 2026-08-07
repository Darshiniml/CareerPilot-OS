package com.careerpilot.sdk.connector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorJobDto {
    private String id;
    private String title;
    private String companyName;
    private String location;
    private String description;
    private String url;
    private Instant postedAt;
    private List<String> skills;
    private Map<String, Object> rawData;
}
