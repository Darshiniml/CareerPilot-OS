package com.careerpilot.sdk.connector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorMetadata {
    private String id;
    private String name;
    private String version;
    private String status; // e.g. ACTIVE, INACTIVE, DEPRECATED
    private ConnectorCapability capabilities;
}
