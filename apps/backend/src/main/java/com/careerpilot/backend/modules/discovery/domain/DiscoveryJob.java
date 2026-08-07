package com.careerpilot.backend.modules.discovery.domain;
import com.careerpilot.connector.sdk.SynchronizationState; import jakarta.persistence.*; import lombok.*; import java.time.*; import java.util.*;
@Entity @Table(name="discovered_jobs",uniqueConstraints=@UniqueConstraint(columnNames={"connector_id","external_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor public class DiscoveryJob {
 @Id private UUID id; @Column(name="external_id",nullable=false) private String externalId; @Column(name="connector_id",nullable=false) private String connectorId;
 private String source; @Column(name="source_url",length=2000) private String sourceUrl; private String title; private String company; private String location;
 @Column(name="employment_type") private String employmentType; @Column(name="work_mode") private String workMode; private String salary; @Column(name="posted_date") private LocalDateTime postedDate;
 @Column(name="raw_content",columnDefinition="TEXT") private String rawContent; @Column(name="metadata_json",columnDefinition="TEXT") private String metadataJson;
 @Column(name="content_hash",nullable=false) private String contentHash; @Column(name="normalized_title") private String normalizedTitle; @Column(name="normalized_company") private String normalizedCompany;
 @Enumerated(EnumType.STRING) @Column(name="sync_state") private SynchronizationState synchronizationState; @Column(name="discovered_at") private Instant discoveredAt; @Column(name="updated_at") private Instant updatedAt; @Column(name="last_seen_at") private Instant lastSeenAt;
}