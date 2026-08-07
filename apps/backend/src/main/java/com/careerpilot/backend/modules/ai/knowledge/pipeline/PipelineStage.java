package com.careerpilot.backend.modules.ai.knowledge.pipeline;

import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;

public interface PipelineStage {
    void process(AiDocument document) throws Exception;
}
