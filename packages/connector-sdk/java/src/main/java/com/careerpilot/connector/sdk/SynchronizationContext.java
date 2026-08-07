package com.careerpilot.connector.sdk;
import lombok.*; import java.time.*; import java.util.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class SynchronizationContext { private Instant lastSynchronizedAt; @Builder.Default private Map<String,Object> parameters=new HashMap<>(); }
