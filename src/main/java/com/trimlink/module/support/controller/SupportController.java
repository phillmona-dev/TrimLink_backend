package com.trimlink.module.support.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.module.notification.service.WebSocketNotificationService;
import com.trimlink.module.support.entity.SupportMessage;
import com.trimlink.module.support.repository.SupportMessageRepository;
import com.trimlink.module.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Support", description = "Support chat for deactivated users")
@RestController
@RequestMapping("/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportMessageRepository supportMessageRepository;
    private final UserRepository userRepository;
    private final WebSocketNotificationService notificationService;

    @Operation(summary = "Send a support message")
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<SupportMessage>> sendMessage(@RequestBody SupportRequest req) {
        if (!userRepository.existsByUsername(req.getUsername())) {
            return ResponseEntity.status(404).body(ApiResponse.error(404, "User not found"));
        }

        SupportMessage message = SupportMessage.builder()
                .senderUsername(req.getUsername())
                .content(req.getMessage())
                .fromAdmin(false)
                .build();

        message = supportMessageRepository.save(message);

        notificationService.notifyAdmins(Map.of(
            "type", "SUPPORT_MESSAGE",
            "username", req.getUsername(),
            "message", req.getMessage(),
            "timestamp", System.currentTimeMillis()
        ));

        return ResponseEntity.ok(ApiResponse.ok("Message sent", message));
    }

    @Operation(summary = "Get chat history")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<SupportMessage>>> getHistory(@RequestParam String username) {
        List<SupportMessage> messages = supportMessageRepository.findBySenderUsernameOrderByCreatedAtAsc(username);
        return ResponseEntity.ok(ApiResponse.ok(messages));
    }

    @Operation(summary = "List threads")
    @GetMapping("/admin/threads")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<String>>> getThreads() {
        return ResponseEntity.ok(ApiResponse.ok(supportMessageRepository.findUniqueSenderUsernames()));
    }

    @Operation(summary = "Respond")
    @PostMapping("/admin/respond")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SupportMessage>> respond(@RequestBody SupportResponse req) {
        SupportMessage message = SupportMessage.builder()
                .senderUsername(req.getUsername())
                .content(req.getMessage())
                .fromAdmin(true)
                .build();

        message = supportMessageRepository.save(message);
        notificationService.broadcast("/topic/support/" + req.getUsername(), message);

        return ResponseEntity.ok(ApiResponse.ok("Response sent", message));
    }

    @Data
    public static class SupportRequest {
        private String username;
        private String message;
    }

    @Data
    public static class SupportResponse {
        private String username;
        private String message;
    }
}
