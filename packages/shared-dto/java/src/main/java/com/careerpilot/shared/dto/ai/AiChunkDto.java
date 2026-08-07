package com.careerpilot.shared.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChunkDto {
    private UUID id;
    private UUID documentId;
    private int chunkNumber;
    private String text;
    private int tokenCount;
    private Integer pageNumber;
    private String section;
    private String heading;
    private String chunkType;
    private Integer sourceDocumentVersion;
    private Map<String, Object> metadata;
}
