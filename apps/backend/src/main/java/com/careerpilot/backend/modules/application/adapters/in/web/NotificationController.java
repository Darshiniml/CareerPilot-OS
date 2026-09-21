package com.careerpilot.backend.modules.application.adapters.in.web;

import com.careerpilot.backend.modules.application.domain.PlatformNotification;
import com.careerpilot.backend.modules.application.repositories.PlatformNotificationRepository;
import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Candidate platform notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final PlatformNotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "List candidate notifications")
    public ResponseEntity<List<PlatformNotification>> list(Principal principal) {
        UUID candidateId = getUserId(principal);
        return ResponseEntity.ok(notificationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark single notification as read")
    public ResponseEntity<PlatformNotification> markRead(@PathVariable UUID id, Principal principal) {
        UUID candidateId = getUserId(principal);
        PlatformNotification notif = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        if (!notif.getCandidateId().equals(candidateId)) {
            throw new SecurityException("User does not own notification");
        }

        notif.setRead(true);
        return ResponseEntity.ok(notificationRepository.save(notif));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all candidate notifications as read")
    public ResponseEntity<Map<String, Object>> markAllRead(Principal principal) {
        UUID candidateId = getUserId(principal);
        List<PlatformNotification> list = notificationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        list.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(list);
        return ResponseEntity.ok(Map.of("success", true, "updated", list.size()));
    }

    private UUID getUserId(Principal principal) {
        if (principal == null) {
            throw new SecurityException("Unauthorized");
        }
        return userRepository.findByEmail(principal.getName())
                .map(User::getId)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }
}
