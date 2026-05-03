package com.trimlink.common.storage;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.shop.entity.StaffShop;
import com.trimlink.module.shop.repository.StaffShopRepository;
import com.trimlink.module.service.entity.Service;
import com.trimlink.module.service.repository.ServiceRepository;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.UserRepository;
import com.trimlink.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Upload", description = "Image/file upload endpoints")
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {

    private final StorageService storageService;
    private final UserRepository userRepository;
    private final StaffShopRepository shopRepository;
    private final ServiceRepository serviceRepository;

    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    // ─── Upload shop logo (OWNER) ─────────────────────────────────────────────
    @Operation(summary = "Upload shop logo for the authenticated owner's shop")
    @PostMapping(value = "/shop-logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadShopLogo(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file) {

        validateFile(file);
        User owner = getUser(principal.getUserId());

        if (owner.getStaffProfile() == null || owner.getStaffProfile().getShop() == null) {
            throw new RuntimeException("You are not associated with any shop");
        }

        StaffShop shop = owner.getStaffProfile().getShop();

        // Delete old logo if exists
        if (shop.getLogoUrl() != null) storageService.delete(shop.getLogoUrl());

        String url = storageService.upload(file, "shops");
        shop.setLogoUrl(url);
        shopRepository.save(shop);

        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
    }

    // ─── Upload user avatar (any authenticated user) ──────────────────────────
    @Operation(summary = "Upload avatar for the authenticated user")
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAvatar(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file) {

        validateFile(file);
        User user = getUser(principal.getUserId());

        // Delete old avatar if exists
        if (user.getAvatarUrl() != null) storageService.delete(user.getAvatarUrl());

        String url = storageService.upload(file, "avatars");
        user.setAvatarUrl(url);
        userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
    }

    // ─── Upload service image (OWNER/ADMIN) ───────────────────────────────────
    @Operation(summary = "Upload image for a specific service")
    @PostMapping(value = "/service-image/{serviceId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadServiceImage(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID serviceId,
            @RequestParam("file") MultipartFile file) {

        validateFile(file);
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", serviceId));

        // Delete old image if exists
        if (service.getImageUrl() != null) storageService.delete(service.getImageUrl());

        String url = storageService.upload(file, "services");
        service.setImageUrl(url);
        serviceRepository.save(service);

        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("File too large. Maximum size is 5 MB");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Unsupported file type. Use JPEG, PNG, WebP or GIF");
        }
    }

    private User getUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }
}
