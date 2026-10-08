package com.careerpilot.shared.dto.resume;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeDto {
    private UUID id;
    private String title;
    private String fileUrl;
    private String originalFilename;
    private String mimeType;
    private long fileSize;
    private String checksumSha256;
    private String storageKey;
    private String parsingStatus;
    private String aiProcessingStatus;
    /** Explicit reason when AI processing failed (e.g. no text layer, AI provider unavailable). */
    private String processingError;
    @JsonProperty("isDefault")
    private boolean isDefault;
    @JsonProperty("isArchived")
    private boolean isArchived;
    private Instant uploadedAt;
    private UUID uploadedBy;
    private List<ResumeVersionDto> versions;
}
