package com.careerpilot.backend.modules.copilot.services;

import java.util.UUID;

/** Read-only access to follow-up recommendations for the Copilot (implemented by the follow-up module). */
public interface FollowUpToolBridge {
    Object recommendations(UUID userId);
}
