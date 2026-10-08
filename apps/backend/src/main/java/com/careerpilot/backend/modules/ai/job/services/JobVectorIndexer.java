package com.careerpilot.backend.modules.ai.job.services;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.gateway.exceptions.AiServiceException;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.events.DiscoveryEvents;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps the shared JOB vector collection in sync with discovered jobs (embeddings only, no LLM),
 * so semantic job search reflects real postings. Failures are logged; they never block discovery.
 *
 * <p>A single sync can return thousands of postings. Indexing runs on one dedicated background
 * thread with a bounded queue, after the discovery transaction commits, so it never floods the
 * embedding model or competes with interactive requests for the shared async pool. When the queue
 * is full, further jobs are skipped (and counted); they are indexed again on their next update.
 */
@Component
@Slf4j
public class JobVectorIndexer {

    private final AiGatewayClient gatewayClient;
    private final JobContextService jobContextService;
    private final boolean enabled;
    private final ThreadPoolExecutor executor;
    private final AtomicLong skipped = new AtomicLong();

    public JobVectorIndexer(AiGatewayClient gatewayClient,
                            JobContextService jobContextService,
                            @Value("${careerpilot.discovery.vector-index.enabled:true}") boolean enabled,
                            @Value("${careerpilot.discovery.vector-index.queue-capacity:5000}") int queueCapacity) {
        this.gatewayClient = gatewayClient;
        this.jobContextService = jobContextService;
        this.enabled = enabled;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(Math.max(1, queueCapacity)),
                runnable -> {
                    Thread thread = new Thread(runnable, "job-vector-indexer");
                    thread.setDaemon(true);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                },
                (runnable, pool) -> {
                    long total = skipped.incrementAndGet();
                    if (total == 1 || total % 500 == 0) {
                        log.warn("Job vector index queue is full; {} job(s) skipped so far", total);
                    }
                });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDiscovered(DiscoveryEvents.JobDiscoveredEvent event) {
        submit(() -> index(event.job()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUpdated(DiscoveryEvents.JobUpdatedEvent event) {
        submit(() -> index(event.job()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRemoved(DiscoveryEvents.JobRemovedEvent event) {
        submit(() -> {
            try {
                gatewayClient.run("DOCUMENT_DELETE", Map.of("documentType", "JOB", "documentId", event.job().getId().toString()));
            } catch (AiServiceException e) {
                log.warn("Could not remove job {} from vector index: {}", event.job().getId(), e.getCode());
            }
        });
    }

    /** Jobs waiting to be indexed (for health/diagnostics). */
    public int pending() {
        return executor.getQueue().size();
    }

    private void submit(Runnable work) {
        if (enabled) {
            executor.execute(work);
        }
    }

    void index(DiscoveryJob job) {
        if (!enabled || job == null || job.getId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("documentType", "JOB");
            payload.put("documentId", job.getId().toString());
            payload.put("content", jobContextService.jobText(job));
            payload.put("metadata", Map.of(
                    "title", String.valueOf(job.getTitle()),
                    "company", String.valueOf(job.getCompany()),
                    "connectorId", String.valueOf(job.getConnectorId())));
            gatewayClient.run("DOCUMENT_INDEX", payload);
        } catch (AiServiceException e) {
            log.warn("Could not index job {} for semantic search: {}", job.getId(), e.getCode());
        } catch (RuntimeException e) {
            log.warn("Could not index job {} for semantic search: {}", job.getId(), e.getMessage());
        }
    }
}
