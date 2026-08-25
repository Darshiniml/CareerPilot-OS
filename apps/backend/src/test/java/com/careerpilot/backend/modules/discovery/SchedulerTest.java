package com.careerpilot.backend.modules.discovery;

import com.careerpilot.backend.modules.discovery.services.*;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.agent.services.JobDiscoveryAgent;
import com.careerpilot.backend.modules.opportunity.services.OpportunityPrioritizationService;
import com.careerpilot.backend.modules.application.repositories.PlatformNotificationRepository;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;

class SchedulerTest {
    @Test 
    void manualTriggerDelegates(){
        var service=mock(JobDiscoveryService.class);
        var userRepo=mock(UserRepository.class);
        var jobAgent=mock(JobDiscoveryAgent.class);
        var oppService=mock(OpportunityPrioritizationService.class);
        var notifRepo=mock(PlatformNotificationRepository.class);

        var scheduler=new DefaultDiscoveryScheduler(service, userRepo, jobAgent, oppService, notifRepo);
        scheduler.trigger("x");
        verify(service).discover(eq("x"), any());
    }
}
