package com.careerpilot.backend.modules.communication.domain;

public enum CommunicationProvider {
    // N8N is the only provider with an implemented ingestion adapter (M22.2).
    // GMAIL/OUTLOOK are reserved vocabulary for future direct integrations and are not yet wired.
    N8N,
    GMAIL,
    OUTLOOK,
    IMAP,
    MANUAL,
    OTHER
}
