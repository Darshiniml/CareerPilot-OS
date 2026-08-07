package com.careerpilot.sdk.connector;

import com.careerpilot.sdk.connector.dto.*;

public interface Connector {
    
    ConnectorMetadata getMetadata();

    JobSearchResult searchJobs(JobSearchQuery query);

    ConnectorJobDto getJob(String jobId);

    ApplicationPreparationResult prepareApplication(ApplicationPreparationQuery query);

    ApplicationSubmissionResult submitApplication(ApplicationSubmissionQuery query);

    ApplicationTrackingResult trackApplication(String applicationExternalId);
}
