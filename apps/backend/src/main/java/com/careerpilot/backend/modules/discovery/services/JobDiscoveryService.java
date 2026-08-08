package com.careerpilot.backend.modules.discovery.services;

import com.careerpilot.connector.sdk.*;
import com.careerpilot.backend.modules.discovery.domain.*;
import com.careerpilot.backend.modules.discovery.repositories.*;
import com.careerpilot.backend.modules.discovery.events.DiscoveryEvents.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class JobDiscoveryService {

    private final ConnectorRegistry registry;
    private final DiscoveryJobRepository repo;
    private final TaxonomyNormalizer normalizer;
    private final DuplicateDetector dedupe;
    private final ApplicationEventPublisher events;
    private final JobIntelligencePort intelligence;
    private final ObjectMapper json;
    private final DiscoveryMetrics metrics;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public JobDiscoveryService(ConnectorRegistry a, DiscoveryJobRepository b, TaxonomyNormalizer c,
                               DuplicateDetector d, ApplicationEventPublisher e, JobIntelligencePort f,
                               ObjectMapper g, DiscoveryMetrics h) {
        this.registry = a;
        this.repo = b;
        this.normalizer = c;
        this.dedupe = d;
        this.events = e;
        this.intelligence = f;
        this.json = g;
        this.metrics = h;
    }

    @Transactional
    public SynchronizationResult discover(String connectorId, DiscoveryContext context) {
        Connector c = registry.get(connectorId);
        if (!c.isEnabled()) throw new IllegalStateException("Connector is disabled");

        long startMs = System.currentTimeMillis();
        log.info("[JOB-DISCOVERY] connector={} action=discover_start", connectorId);

        Map<SynchronizationState, Integer> counts = new EnumMap<>(SynchronizationState.class);
        for (var s : SynchronizationState.values()) counts.put(s, 0);
        Set<String> seen = new HashSet<>();

        try {
            if (!c.authenticate()) throw new IllegalStateException("Authentication failed");
            List<DiscoveredJob> jobs = Optional.ofNullable(c.discoverJobs(context)).orElse(List.of());
            log.info("[JOB-DISCOVERY] connector={} found={}", connectorId, jobs.size());

            for (DiscoveredJob raw : jobs) {
                try {
                    validate(raw);
                    raw.setConnectorId(c.getConnectorId());
                    DiscoveredJob j = normalizer.normalize(raw);
                    j.setContentHash(dedupe.hash(j));
                    seen.add(j.getExternalId());

                    var old = repo.findByConnectorIdAndExternalId(j.getConnectorId(), j.getExternalId());
                    if (old.isPresent() && old.get().getContentHash().equals(j.getContentHash())) {
                        old.get().setLastSeenAt(Instant.now());
                        old.get().setSynchronizationState(SynchronizationState.UNCHANGED);
                        repo.save(old.get());
                        inc(counts, SynchronizationState.UNCHANGED);
                        continue;
                    }

                    if (old.isEmpty() && dedupe.duplicate(j)) {
                        metrics.duplicates.increment();
                        inc(counts, SynchronizationState.UNCHANGED);
                        log.info("[JOB-DISCOVERY] connector={} duplicate_detected title='{}'", connectorId, j.getTitle());
                        continue;
                    }

                    DiscoveryJob entity = map(j, old.orElse(null));
                    SynchronizationState state = old.isPresent() ? SynchronizationState.UPDATED : SynchronizationState.NEW;
                    entity.setSynchronizationState(state);
                    repo.save(entity);
                    inc(counts, state);

                    if (state == SynchronizationState.NEW) {
                        metrics.discovered.increment();
                        events.publishEvent(new JobDiscoveredEvent(entity));
                    } else {
                        metrics.updated.increment();
                        events.publishEvent(new JobUpdatedEvent(entity));
                    }
                    intelligence.accept(entity);

                } catch (Exception bad) {
                    log.warn("[JOB-DISCOVERY] connector={} job_validation_failed reason={}", connectorId, bad.getMessage());
                    inc(counts, SynchronizationState.FAILED);
                }
            }

            for (DiscoveryJob old : repo.findByConnectorId(connectorId)) {
                if (!seen.contains(old.getExternalId()) && old.getSynchronizationState() != SynchronizationState.REMOVED) {
                    old.setSynchronizationState(SynchronizationState.REMOVED);
                    old.setUpdatedAt(Instant.now());
                    repo.save(old);
                    inc(counts, SynchronizationState.REMOVED);
                    events.publishEvent(new JobRemovedEvent(old));
                }
            }

            long duration = System.currentTimeMillis() - startMs;
            log.info("[JOB-DISCOVERY] connector={} status=SUCCESS duration={}ms new={} updated={}",
                    connectorId, duration, counts.get(SynchronizationState.NEW), counts.get(SynchronizationState.UPDATED));

            SynchronizationResult result = SynchronizationResult.builder().connectorId(connectorId).counts(counts).build();
            events.publishEvent(new ConnectorSynchronizationCompletedEvent(connectorId, result, Instant.now()));
            return result;

        } catch (RuntimeException ex) {
            log.error("[JOB-DISCOVERY] connector={} status=FAILED reason={}", connectorId, ex.getMessage());
            metrics.failures.increment();
            inc(counts, SynchronizationState.FAILED);
            SynchronizationResult fail = SynchronizationResult.builder().connectorId(connectorId).counts(counts).error(ex.getMessage()).build();
            events.publishEvent(new ConnectorSynchronizationCompletedEvent(connectorId, fail, Instant.now()));
            return fail;
        } finally {
            c.disconnect();
        }
    }

    public List<SynchronizationResult> discoverAll() {
        return discoverAll(DiscoveryContext.builder().build());
    }

    public List<SynchronizationResult> discoverAll(DiscoveryContext context) {
        List<Connector> enabledConnectors = registry.enabled();
        log.info("[JOB-DISCOVERY] action=discoverAll_start enabled_connectors={}", enabledConnectors.size());

        List<CompletableFuture<SynchronizationResult>> futures = enabledConnectors.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return discover(c.getConnectorId(), context);
                    } catch (Exception e) {
                        log.warn("[JOB-DISCOVERY] connector={} failed in parallel execution: {}", c.getConnectorId(), e.getMessage());
                        return SynchronizationResult.builder()
                                .connectorId(c.getConnectorId())
                                .error(e.getMessage())
                                .build();
                    }
                }, executor))
                .toList();

        return futures.stream().map(CompletableFuture::join).toList();
    }

    public List<DiscoveryJob> jobs() {
        return repo.findAll();
    }

    public DiscoveryJob job(UUID id) {
        return repo.findById(id).orElseThrow(() -> new NoSuchElementException("Job not found"));
    }

    private void validate(DiscoveredJob j) {
        if (j == null || blank(j.getExternalId()) || blank(j.getSource()) || blank(j.getTitle()) || blank(j.getCompany()) || blank(j.getRawContent())) {
            throw new IllegalArgumentException("externalId, source, title, company and rawContent are required");
        }
    }

    private boolean blank(String x) {
        return x == null || x.isBlank();
    }

    private void inc(Map<SynchronizationState, Integer> m, SynchronizationState s) {
        m.put(s, m.getOrDefault(s, 0) + 1);
    }

    private DiscoveryJob map(DiscoveredJob j, DiscoveryJob e) {
        Instant now = Instant.now();
        if (e == null) e = DiscoveryJob.builder().id(UUID.randomUUID()).discoveredAt(now).build();
        e.setExternalId(j.getExternalId());
        e.setConnectorId(j.getConnectorId());
        e.setSource(j.getSource());
        e.setSourceUrl(j.getSourceUrl());
        e.setTitle(j.getTitle());
        e.setCompany(j.getCompany());
        e.setLocation(j.getLocation());
        e.setEmploymentType(j.getEmploymentType());
        e.setWorkMode(j.getWorkMode());
        e.setSalary(j.getSalary());
        e.setPostedDate(j.getPostedDate());
        e.setRawContent(j.getRawContent());
        try {
            e.setMetadataJson(json.writeValueAsString(j.getMetadata()));
        } catch (Exception x) {
            e.setMetadataJson("{}");
        }
        e.setContentHash(j.getContentHash());
        e.setNormalizedTitle(j.getNormalizedTitle());
        e.setNormalizedCompany(j.getNormalizedCompany());
        e.setUpdatedAt(now);
        e.setLastSeenAt(now);
        return e;
    }
}
