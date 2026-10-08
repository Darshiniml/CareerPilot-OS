package com.careerpilot.shared.dto.opportunity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpportunityDto {
    private UUID jobId;
    private String title;
    private String company;
    private String location;
    private String workMode;
    private String source;
    private String sourceUrl;
    private String connectorId;

    private double matchScore;
    /** False when the candidate has no processed resume yet: the job is unscored, not scored 0. */
    private Boolean matchAvailable;
    private List<String> notAssessedFactors;
    private double historicalSuccessScore;
    private String historicalConfidence;

    private double priorityScore;
    private String priorityLevel;

    private String applicationStatus;
    private String submissionMode;

    private String recommendedAction;
    private List<String> reasons;
}
