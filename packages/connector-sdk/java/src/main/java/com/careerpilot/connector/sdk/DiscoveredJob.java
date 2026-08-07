package com.careerpilot.connector.sdk;
import lombok.*; import java.time.*; import java.util.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DiscoveredJob {
 private String externalId,connectorId,source,sourceUrl,title,company,location,employmentType,workMode,salary,rawContent;
 private LocalDateTime postedDate; @Builder.Default private Map<String,Object> metadata=new HashMap<>();
 @Builder.Default private LocalDateTime discoveredAt=LocalDateTime.now();
 private String contentHash,normalizedTitle,normalizedCompany; @Builder.Default private List<String> technologies=new ArrayList<>(),skills=new ArrayList<>();
}