package com.careerpilot.sdk.connector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorCapability {
    private boolean supportsJobSearch;
    private boolean supportsGetJob;
    private boolean supportsPrepareApplication;
    private boolean supportsSubmitApplication;
    private boolean supportsTrackApplication;
    private boolean supportsCompanyResearch;
}
