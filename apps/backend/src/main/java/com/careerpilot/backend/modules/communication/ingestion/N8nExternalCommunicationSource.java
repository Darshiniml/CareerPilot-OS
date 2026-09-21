package com.careerpilot.backend.modules.communication.ingestion;

import com.careerpilot.backend.modules.communication.domain.CommunicationProvider;
import org.springframework.stereotype.Component;

/**
 * The n8n webhook ingestion source. This is the only {@link ExternalCommunicationSource} with an
 * implemented adapter in M22.2; GMAIL/OUTLOOK remain reserved provider vocabulary.
 */
@Component
public class N8nExternalCommunicationSource implements ExternalCommunicationSource {

    public static final String SOURCE_ID = "n8n-webhook";

    @Override
    public CommunicationProvider getProvider() {
        return CommunicationProvider.N8N;
    }

    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }
}
