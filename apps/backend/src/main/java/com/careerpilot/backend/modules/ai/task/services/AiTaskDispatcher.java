package com.careerpilot.backend.modules.ai.task.services;

import com.careerpilot.backend.modules.ai.task.domain.AiTask;
import org.springframework.stereotype.Service;

@Service
public class AiTaskDispatcher {

    private final TaskExecutor syncExecutor;

    public AiTaskDispatcher(TaskExecutor syncExecutor) {
        this.syncExecutor = syncExecutor;
    }

    public void dispatch(AiTask task) {
        syncExecutor.execute(task);
    }
}
