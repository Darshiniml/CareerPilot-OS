package com.careerpilot.connector.sdk;
import lombok.*; import java.time.*; import java.util.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class SynchronizationResult { private String connectorId; @Builder.Default private Instant synchronizedAt=Instant.now(); @Builder.Default private Map<SynchronizationState,Integer> counts=new EnumMap<>(SynchronizationState.class); private String error; public static SynchronizationResult empty(String id){return builder().connectorId(id).build();} }
