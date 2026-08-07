package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.WorkflowState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowEngineTest {

    @Test
    void allowsDefinedWorkflowTransitions() {
        ApplicationWorkflowEngine engine = new ApplicationWorkflowEngine();
        assertThat(engine.canTransition(WorkflowState.DISCOVERED, WorkflowState.MATCHED)).isTrue();
        assertThat(engine.canTransition(WorkflowState.MATCHED, WorkflowState.ELIGIBLE)).isTrue();
        assertThat(engine.canTransition(WorkflowState.SUBMITTING, WorkflowState.FAILED)).isTrue();
    }

    @Test
    void rejectsInvalidTransitions() {
        ApplicationWorkflowEngine engine = new ApplicationWorkflowEngine();
        assertThatThrownBy(() -> engine.validate(WorkflowState.DISCOVERED, WorkflowState.SUBMITTED))
                .isInstanceOf(IllegalStateException.class);
    }
}
