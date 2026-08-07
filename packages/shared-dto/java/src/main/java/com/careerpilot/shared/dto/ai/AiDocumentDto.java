package com.careerpilot.shared.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDocumentDto {
    private UUID id;
    private String documentType;
    private String status;
    private UUID ownerId;
    private String title;
    private String source;
    private String mimeType;
    private String language;
    private String content;
    private Map<String, Object> structuredMetadata;
    private Map<String, Object> flexibleMetadata;
    private int version;
    private String checksum;
    private List<AiChunkDto> chunks;
}
