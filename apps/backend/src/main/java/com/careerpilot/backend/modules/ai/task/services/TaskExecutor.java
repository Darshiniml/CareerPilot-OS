package com.careerpilot.backend.modules.ai.task.services;

import com.careerpilot.backend.modules.ai.task.domain.AiTask;

public interface TaskExecutor {
    void execute(AiTask task);
}
