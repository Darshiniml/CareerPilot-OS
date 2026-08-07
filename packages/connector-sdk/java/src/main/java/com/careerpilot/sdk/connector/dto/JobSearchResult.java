package com.careerpilot.sdk.connector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSearchResult {
    private List<ConnectorJobDto> jobs;
    private int totalCount;
    private boolean hasMore;
    private String nextPageToken;
}
