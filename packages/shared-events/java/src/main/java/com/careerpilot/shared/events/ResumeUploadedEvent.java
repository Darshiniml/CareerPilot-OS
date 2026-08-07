package com.careerpilot.shared.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeUploadedEvent extends BaseEvent {
    private UUID resumeId;
    private UUID userId;
    private String fileName;
    private String fileUrl;
    private String contentType;
    private long fileSize;
}
