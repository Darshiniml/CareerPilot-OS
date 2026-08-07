package com.careerpilot.shared.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingReferenceDto {
    private UUID id;
    private UUID documentId;
    private UUID chunkId;
    private String provider;
    private String collection;
    private String vectorId;
    private String embeddingModel;
    private String embeddingVersion;
    private int vectorDimension;
}
