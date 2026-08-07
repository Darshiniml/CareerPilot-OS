package com.careerpilot.backend.modules.discovery.domain;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor public class ConnectorRegistration { private String id,type,name,version; private boolean enabled; }
