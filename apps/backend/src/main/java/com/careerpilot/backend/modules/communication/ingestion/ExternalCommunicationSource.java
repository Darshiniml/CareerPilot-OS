package com.careerpilot.backend.modules.communication.ingestion;

import com.careerpilot.backend.modules.communication.domain.CommunicationProvider;

/**
 * An external system that can deliver inbound HR communications to CareerPilot.
 * Each concrete source is responsible for normalizing its raw payload into a
 * {@link NormalizedInboundCommunication}; it never dictates server-owned state.
 */
public interface ExternalCommunicationSource {

    CommunicationProvider getProvider();

    String getSourceId();
}
