package com.trimlink.module.user.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.UserRepository;
import com.trimlink.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Users", description = "User profile management")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    // GET /users/me — get my profile
    @Operation(summary = "Get my user profile")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<User>> getMyProfile(
            @AuthenticationPrincipal AuthenticatedUser principal) {

        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    // PUT /users/me — update my profile
    @Operation(summary = "Update my profile")
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<ApiResponse<User>> updateMyProfile(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateProfileRequest req) {

        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        
        if (req.getEmail() != null) user.setEmail(req.getEmail());
        if (req.getAvatarUrl() != null) user.setAvatarUrl(req.getAvatarUrl());
        
        // Update username if provided and not taken
        if (req.getUsername() != null && !req.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(req.getUsername())) {
                throw new RuntimeException("Username already taken");
            }
            user.setUsername(req.getUsername());
        }
        
        // Update password if provided
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.getPassword()));
        }
        
        return ResponseEntity.ok(ApiResponse.ok(userRepository.save(user)));
    }

    // GET /users/{id} — admin or same user
    @Operation(summary = "Get user by ID (Admin or self)")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.userId")
    public ResponseEntity<ApiResponse<User>> getById(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    // DELETE /users/{id} — soft delete (admin only)
    @Operation(summary = "Deactivate user account (admin)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setActive(false);
        user.softDelete();
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.ok("User deactivated", null));
    }

    // ─── Inner DTO ───────────────────────────────────────────────────────────
    @Data
    public static class UpdateProfileRequest {
        @NotBlank @Size(max = 100) private String firstName;
        @NotBlank @Size(max = 100) private String lastName;
        @Email    @Size(max = 150) private String email;
        private String username;
        private String password;
        private String avatarUrl;
    }
}
