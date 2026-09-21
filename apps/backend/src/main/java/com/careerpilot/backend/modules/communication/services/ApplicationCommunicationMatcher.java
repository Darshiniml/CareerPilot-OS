package com.careerpilot.backend.modules.communication.services;

import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.communication.domain.HrCommunication;

import java.util.List;

public interface ApplicationCommunicationMatcher {

    CommunicationMatchResult match(HrCommunication communication, List<ApplicationRecord> candidateApplications);
}
