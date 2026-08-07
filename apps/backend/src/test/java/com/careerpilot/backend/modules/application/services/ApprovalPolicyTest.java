package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.application.domain.ApprovalMode;
import com.careerpilot.backend.modules.application.domain.ApprovalPolicy;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalPolicyTest {

    @Test
    void policyBasedApprovalAutoApprovesWhenRulesMatch() {
        ApprovalPolicy policy = ApprovalPolicy.builder()
                .mode(ApprovalMode.POLICY_BASED)
                .minimumScore(0.90)
                .requireRemote(true)
                .technology("java")
                .location("bangalore")
                .build();

        ApprovalPolicyEngine engine = new ApprovalPolicyEngine();
        ApprovalPolicyEngine.Context context = new ApprovalPolicyEngine.Context(0.95, true, "java", "bangalore");

        assertThat(engine.shouldAutoApprove(policy, context)).isTrue();
        assertThat(engine.nextState(policy, context)).isEqualTo(WorkflowState.APPROVED);
    }

    @Test
    void manualPoliciesStayInWaitingApproval() {
        ApprovalPolicy policy = ApprovalPolicy.builder().mode(ApprovalMode.MANUAL).build();
        ApprovalPolicyEngine engine = new ApprovalPolicyEngine();
        ApprovalPolicyEngine.Context context = new ApprovalPolicyEngine.Context(0.95, true, "java", "bangalore");

        assertThat(engine.shouldAutoApprove(policy, context)).isFalse();
        assertThat(engine.nextState(policy, context)).isEqualTo(WorkflowState.WAITING_APPROVAL);
    }
}
