package com.trimlink.module.chat.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.module.chat.entity.ChatMessage;
import com.trimlink.module.chat.repository.ChatMessageRepository;
import com.trimlink.module.notification.service.WebSocketNotificationService;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.UserRepository;
import com.trimlink.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Chat", description = "Real-time private messaging")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final WebSocketNotificationService notificationService;

    @Operation(summary = "Send a private message")
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<ChatMessage>> sendMessage(@RequestBody ChatRequest req) {
        AuthenticatedUser currentUser = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        ChatMessage message = ChatMessage.builder()
                .senderId(currentUser.getUserId())
                .receiverId(req.getReceiverId())
                .content(req.getContent())
                .build();

        message = chatMessageRepository.save(message);

        // Notify receiver via WebSocket
        notificationService.broadcast("/topic/chat/" + req.getReceiverId(), message);
        // Echo to sender's other sessions
        notificationService.broadcast("/topic/chat/" + currentUser.getUserId(), message);

        return ResponseEntity.ok(ApiResponse.ok("Message sent", message));
    }

    @Operation(summary = "Get conversation history with a user")
    @GetMapping("/history/{otherUserId}")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getHistory(@PathVariable UUID otherUserId) {
        AuthenticatedUser currentUser = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<ChatMessage> history = chatMessageRepository.findConversation(currentUser.getUserId(), otherUserId);
        
        // Mark as read
        List<ChatMessage> unread = history.stream()
                .filter(m -> m.getReceiverId().equals(currentUser.getUserId()) && !m.isRead())
                .peek(m -> m.setRead(true))
                .collect(Collectors.toList());
        if (!unread.isEmpty()) {
            chatMessageRepository.saveAll(unread);
        }

        return ResponseEntity.ok(ApiResponse.ok(history));
    }

    @Operation(summary = "List all users the current user has chatted with")
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<UserSummary>>> getConversations() {
        AuthenticatedUser currentUser = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<UUID> connectedIds = chatMessageRepository.findConnectedUserIds(currentUser.getUserId());
        
        List<UserSummary> summaries = userRepository.findAllById(connectedIds).stream()
                .map(u -> {
                    long unreadCount = chatMessageRepository.countBySenderIdAndReceiverIdAndReadFalse(u.getId(), currentUser.getUserId());
                    return new UserSummary(u.getId(), u.getFirstName() + " " + u.getLastName(), u.getUsername(), u.getRole().name(), unreadCount);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    @Operation(summary = "Search users to start a conversation")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<UserSummary>>> searchUsers(@RequestParam String q) {
        List<User> users = userRepository.searchUsers(q);
        List<UserSummary> summaries = users.stream()
                .map(u -> new UserSummary(u.getId(), u.getFirstName() + " " + u.getLastName(), u.getUsername(), u.getRole().name(), 0L))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    @Operation(summary = "Get total unread message count for current user")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount() {
        AuthenticatedUser currentUser = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        long count = chatMessageRepository.countByReceiverIdAndReadFalse(currentUser.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(count));
    }

    @Data
    public static class ChatRequest {
        private UUID receiverId;
        private String content;
    }

    @Data
    @RequiredArgsConstructor
    public static class UserSummary {
        private final UUID id;
        private final String fullName;
        private final String username;
        private final String role;
        private final long unreadCount;
    }
}
