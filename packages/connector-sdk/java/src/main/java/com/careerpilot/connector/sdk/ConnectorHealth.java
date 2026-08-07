package com.careerpilot.connector.sdk;
import lombok.*; import java.time.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ConnectorHealth { private String connectorId; @Builder.Default private ConnectorHealthStatus status=ConnectorHealthStatus.UNKNOWN; private Instant lastSynchronization,lastSuccess,lastFailure; private long responseTimeMs; private int failureCount; private String message; }
