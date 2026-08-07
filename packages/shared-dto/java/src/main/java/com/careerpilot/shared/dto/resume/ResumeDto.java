package com.careerpilot.shared.dto.resume;

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
    private boolean isDefault;
    private boolean isArchived;
    private Instant uploadedAt;
    private UUID uploadedBy;
    private List<ResumeVersionDto> versions;
}
