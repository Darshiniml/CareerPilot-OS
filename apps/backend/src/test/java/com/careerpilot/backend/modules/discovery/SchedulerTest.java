package com.careerpilot.backend.modules.discovery;
import com.careerpilot.backend.modules.discovery.services.*; import org.junit.jupiter.api.*; import static org.mockito.Mockito.*;
class SchedulerTest {@Test void manualTriggerDelegates(){var service=mock(JobDiscoveryService.class);var scheduler=new DefaultDiscoveryScheduler(service);scheduler.trigger("x");verify(service).discover(eq("x"),any());}}
