package com.careerpilot.connector.sdk;
import java.util.List;
public interface Connector {
 String getConnectorId(); String getConnectorType(); String getDisplayName(); String getVersion();
 default boolean authenticate(){return true;}
 List<DiscoveredJob> discoverJobs(DiscoveryContext context);
 DiscoveredJob normalizeJob(Object rawJob);
 default SynchronizationResult synchronize(SynchronizationContext context){return SynchronizationResult.empty(getConnectorId());}
 ConnectorHealth healthCheck();
 default void disconnect(){}
 boolean isEnabled(); void setEnabled(boolean enabled);
}