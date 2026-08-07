package com.careerpilot.connector.sdk;
import lombok.*; import java.time.*; import java.util.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class DiscoveryContext { private Instant since; @Builder.Default private Map<String,Object> parameters=new HashMap<>(); }
