package com.trimlink.module.service.controller;

import com.trimlink.common.dto.ApiResponse;
import com.trimlink.common.dto.PageResponse;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.service.entity.Service;
import com.trimlink.module.service.repository.ServiceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.trimlink.security.AuthenticatedUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@Tag(name = "Services", description = "Barbershop service catalog management")
@RestController
@RequestMapping("/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final com.trimlink.module.user.repository.UserRepository userRepository;

    // GET /services — public (listed in app)
    @Operation(summary = "List all active services")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<Service>>> listServices(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.from(serviceRepository.findByActiveTrueAndDeletedFalse(pageable))));
    }

    // GET /services/{id}
    @Operation(summary = "Get service by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Service>> getById(@PathVariable UUID id) {
        Service svc = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", id));
        return ResponseEntity.ok(ApiResponse.ok(svc));
    }

    // POST /services — ADMIN only
    @Operation(summary = "Create a new global service (Admin only)")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<Service>> create(
            @Valid @RequestBody ServiceRequest req) {

        Service svc = Service.builder()
                .name(req.getName())
                .description(req.getDescription())
                .basePrice(req.getBasePrice())
                .durationMinutes(req.getDurationMinutes())
                .active(true)
                .build();

        return ResponseEntity.status(201).body(ApiResponse.created(serviceRepository.save(svc)));
    }

    // POST /services/my-shop — OWNER only
    @Operation(summary = "Create a service for my shop")
    @PostMapping("/my-shop")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public ResponseEntity<ApiResponse<Service>> createForShop(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ServiceRequest req) {

        com.trimlink.module.user.entity.User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        
        if (user.getBarberProfile() == null || user.getBarberProfile().getShop() == null) {
            throw new RuntimeException("You must be linked to a shop to create services");
        }

        Service svc = Service.builder()
                .name(req.getName())
                .description(req.getDescription())
                .basePrice(req.getBasePrice())
                .durationMinutes(req.getDurationMinutes())
                .shopId(user.getBarberProfile().getShop().getId())
                .active(true)
                .build();

        return ResponseEntity.status(201).body(ApiResponse.created(serviceRepository.save(svc)));
    }

    // GET /services/my-shop — OWNER only (Get both global and shop-specific)
    @Operation(summary = "Get service catalog for my shop (global + custom)")
    @GetMapping("/my-shop")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<java.util.List<Service>>> getShopCatalog(
            @AuthenticationPrincipal AuthenticatedUser principal) {

        com.trimlink.module.user.entity.User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        
        if (user.getBarberProfile() == null || user.getBarberProfile().getShop() == null) {
            throw new RuntimeException("You must be linked to a shop to view the catalog");
        }

        return ResponseEntity.ok(ApiResponse.ok(
                serviceRepository.findActiveByShopIdOrGlobal(user.getBarberProfile().getShop().getId())));
    }

    // PUT /services/{id} — ADMIN/OWNER only
    @Operation(summary = "Update a service")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Transactional
    public ResponseEntity<ApiResponse<Service>> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @Valid @RequestBody ServiceRequest req) {

        Service svc = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", id));

        // Security check for Owners
        if ("OWNER".equals(principal.getRole())) {
            com.trimlink.module.user.entity.User user = userRepository.findById(principal.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
            
            if (svc.getShopId() == null || !svc.getShopId().equals(user.getBarberProfile().getShop().getId())) {
                throw new RuntimeException("Unauthorized: You can only update your shop's custom services");
            }
        }

        svc.setName(req.getName());
        svc.setDescription(req.getDescription());
        svc.setBasePrice(req.getBasePrice());
        svc.setDurationMinutes(req.getDurationMinutes());
        return ResponseEntity.ok(ApiResponse.ok(serviceRepository.save(svc)));
    }

    // DELETE /services/{id} — soft delete
    @Operation(summary = "Deactivate a service")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id) {
        Service svc = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", id));

        // Security check for Owners
        if ("OWNER".equals(principal.getRole())) {
            com.trimlink.module.user.entity.User user = userRepository.findById(principal.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
            
            if (svc.getShopId() == null || !svc.getShopId().equals(user.getBarberProfile().getShop().getId())) {
                throw new RuntimeException("Unauthorized: You can only deactivate your shop's custom services");
            }
        }

        svc.setActive(false);
        serviceRepository.save(svc);
        return ResponseEntity.ok(ApiResponse.ok("Service deactivated", null));
    }

    // ─── Inner DTO ──────────────────────────────────────────────────────────
    @Data
    public static class ServiceRequest {
        @NotBlank(message = "Name is required")
        @Size(max = 150)
        private String name;

        @Size(max = 400)
        private String description;

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "1.0", message = "Price must be at least 1 ETB")
        private BigDecimal basePrice;

        @NotNull(message = "Duration is required")
        @Min(value = 5, message = "Duration must be at least 5 minutes")
        @Max(value = 480, message = "Duration cannot exceed 8 hours")
        private Integer durationMinutes;
    }
}
