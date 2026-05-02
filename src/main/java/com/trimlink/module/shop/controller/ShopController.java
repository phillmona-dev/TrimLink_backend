package com.trimlink.module.shop.controller;
 
import com.trimlink.common.dto.ApiResponse;
import com.trimlink.common.dto.PageResponse;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.shop.dto.ShopSearchResponse;
import com.trimlink.module.shop.dto.ShopStatsResponse;
import com.trimlink.module.user.dto.BarberResponse;
import com.trimlink.module.shop.entity.BarberShop;
import com.trimlink.module.shop.repository.BarberShopRepository;
import com.trimlink.module.shop.dto.StaffPerformanceResponse;
import com.trimlink.module.shop.dto.WeeklyPerformanceResponse;
import com.trimlink.module.shop.service.ShopService;
import com.trimlink.module.user.dto.UserResponse;
import com.trimlink.module.user.entity.BarberProfile;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.BarberProfileRepository;
import com.trimlink.module.user.repository.UserRepository;
import com.trimlink.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Shops", description = "Barbershop management")
@RestController
@RequestMapping("/shops")
@RequiredArgsConstructor
public class ShopController {

    private final BarberShopRepository   shopRepository;
    private final BarberProfileRepository barberProfileRepository;
    private final UserRepository userRepository;
    private final ShopService shopService;
    private final com.trimlink.module.shop.repository.WorkingHoursRepository workingHoursRepository;

    // GET /shops?q=...  — full-text search across name/city/address
    // GET /shops?city=... — city filter (legacy)
    // GET /shops         — all active shops
    @Operation(summary = "List or search active shops (q=keyword or city=name)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ShopSearchResponse>>> listShops(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @PageableDefault(size = 20) Pageable pageable) {

        var page = shopService.searchShops(q, city, pageable);

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    // GET /shops/{id}
    @Operation(summary = "Get shop details by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BarberShop>> getById(@PathVariable UUID id) {
        BarberShop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BarberShop", "id", id));
        return ResponseEntity.ok(ApiResponse.ok(shop));
    }

    // GET /shops/{id}/barbers — list barbers in this shop
    @Operation(summary = "List available barbers in a shop")
    @GetMapping("/{id}/barbers")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<BarberResponse>>> getBarbers(@PathVariable UUID id) {
        List<BarberProfile> barbers = barberProfileRepository.findByShopIdAndDeletedFalseAndAvailableTrueOrderByAverageRatingDesc(id);
        
        List<BarberResponse> response = barbers.stream()
                .map(BarberResponse::from)
                .toList();
                
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // POST /shops — ADMIN/OWNER
    @Operation(summary = "Create a new barbershop")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Transactional
    public ResponseEntity<ApiResponse<BarberShop>> create(
            @Valid @RequestBody ShopRequest req) {

        BarberShop shop = BarberShop.builder()
                .name(req.getName())
                .phone(req.getPhone())
                .address(req.getAddress())
                .city(req.getCity())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .description(req.getDescription())
                .active(true)
                .build();

        shop = shopRepository.save(shop);

        // Initialize default working hours
        for (java.time.DayOfWeek day : java.time.DayOfWeek.values()) {
            workingHoursRepository.save(com.trimlink.module.shop.entity.WorkingHours.builder()
                    .shop(shop)
                    .dayOfWeek(day)
                    .openTime(java.time.LocalTime.of(8, 0))
                    .closeTime(java.time.LocalTime.of(20, 0))
                    .closed(false)
                    .build());
        }

        return ResponseEntity.status(201).body(ApiResponse.created(shop));
    }

    // PUT /shops/{id}
    @Operation(summary = "Update shop details")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Transactional
    public ResponseEntity<ApiResponse<BarberShop>> update(
            @PathVariable UUID id, @Valid @RequestBody ShopRequest req) {

        BarberShop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BarberShop", "id", id));
        shop.setName(req.getName());
        shop.setPhone(req.getPhone());
        shop.setAddress(req.getAddress());
        shop.setCity(req.getCity());
        shop.setLatitude(req.getLatitude());
        shop.setLongitude(req.getLongitude());
        shop.setDescription(req.getDescription());
        return ResponseEntity.ok(ApiResponse.ok(shopRepository.save(shop)));
    }

    // DELETE /shops/{id}
    @Operation(summary = "Deactivate a shop")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        BarberShop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BarberShop", "id", id));
        shop.setActive(false);
        shopRepository.save(shop);
        return ResponseEntity.ok(ApiResponse.ok("Shop deactivated", null));
    }

    // ─── Owner Staff Management ──────────────────────────────────────────────

    @Operation(summary = "Get shop statistics for owner dashboard")
    @GetMapping("/my-shop/stats")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ShopStatsResponse>> getShopStats(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        
        User owner = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        
        if (owner.getBarberProfile() == null || owner.getBarberProfile().getShop() == null) {
            throw new RuntimeException("Unauthorized: User is not linked to a shop");
        }
        
        UUID shopId = owner.getBarberProfile().getShop().getId();
        ShopStatsResponse stats = shopService.getShopStats(shopId);
        
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    @Operation(summary = "Get performance stats for all staff in my shop")
    @GetMapping("/my-shop/staff")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<List<StaffPerformanceResponse>>> getMyStaffPerformance(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        
        User owner = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        
        if (owner.getBarberProfile() == null || owner.getBarberProfile().getShop() == null) {
            throw new RuntimeException("You are not associated with any shop");
        }

        UUID shopId = owner.getBarberProfile().getShop().getId();
        return ResponseEntity.ok(ApiResponse.ok(shopService.getStaffPerformance(shopId)));
    }

    @Operation(summary = "Get weekly performance report for all staff")
    @GetMapping("/my-shop/staff/weekly-report")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<List<WeeklyPerformanceResponse>>> getWeeklyReport(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        
        User owner = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        
        if (owner.getBarberProfile() == null || owner.getBarberProfile().getShop() == null) {
            throw new RuntimeException("You are not associated with any shop");
        }

        UUID shopId = owner.getBarberProfile().getShop().getId();
        return ResponseEntity.ok(ApiResponse.ok(shopService.getWeeklyReport(shopId)));
    }

    @Operation(summary = "Log daily customer work for a staff member")
    @PostMapping("/my-shop/staff/{barberId}/logs")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Void>> logStaffWork(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID barberId,
            @Valid @RequestBody WorkLogRequest req) {
        
        // Security check: ensure the barber belongs to the owner's shop
        User owner = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        
        BarberProfile barber = barberProfileRepository.findById(barberId)
                .orElseThrow(() -> new ResourceNotFoundException("Barber", "id", barberId));
        
        if (owner.getBarberProfile() == null || barber.getShop() == null || 
            !owner.getBarberProfile().getShop().getId().equals(barber.getShop().getId())) {
            throw new RuntimeException("Unauthorized: Staff does not belong to your shop");
        }

        shopService.logDailyWork(barberId, req.getCount(), req.getNotes());
        return ResponseEntity.ok(ApiResponse.ok("Work logged successfully", null));
    }

    @Operation(summary = "Add a staff member to my shop by phone number")
    @PostMapping("/my-shop/staff")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public ResponseEntity<ApiResponse<UserResponse>> addStaff(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AddStaffRequest req) {
        
        User owner = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        
        if (owner.getBarberProfile() == null || owner.getBarberProfile().getShop() == null) {
            throw new RuntimeException("You are not associated with any shop");
        }

        User staffUser = userRepository.findByPhoneNumber(req.getPhoneNumber())
                .orElseThrow(() -> new ResourceNotFoundException("User", "phone", req.getPhoneNumber()));

        BarberProfile profile = barberProfileRepository.findByUserId(staffUser.getId())
                .orElse(BarberProfile.builder()
                        .user(staffUser)
                        .build());
        
        profile.setShop(owner.getBarberProfile().getShop());
        profile.setAvailable(true);
        barberProfileRepository.save(profile);
        
        // Ensure user has BARBER role if they were a CUSTOMER
        if (staffUser.getRole() == com.trimlink.module.user.entity.Role.CUSTOMER) {
            staffUser.setRole(com.trimlink.module.user.entity.Role.BARBER);
            userRepository.save(staffUser);
        }

        return ResponseEntity.ok(ApiResponse.ok("Staff added successfully", UserResponse.from(staffUser)));
    }

    @Operation(summary = "Toggle staff member availability")
    @PatchMapping("/my-shop/staff/{barberId}/availability")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> toggleAvailability(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID barberId,
            @RequestBody AvailabilityRequest req) {
        
        User owner = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        
        BarberProfile barber = barberProfileRepository.findById(barberId)
                .orElseThrow(() -> new ResourceNotFoundException("Barber", "id", barberId));
        
        if (owner.getBarberProfile() == null || barber.getShop() == null || 
            !owner.getBarberProfile().getShop().getId().equals(barber.getShop().getId())) {
            throw new RuntimeException("Unauthorized: Staff does not belong to your shop");
        }

        barber.setAvailable(req.isAvailable());
        barberProfileRepository.save(barber);
        
        return ResponseEntity.ok(ApiResponse.ok("Availability updated", null));
    }

    @Operation(summary = "Get my shop working hours")
    @GetMapping("/my-shop/hours")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<List<com.trimlink.module.shop.entity.WorkingHours>>> getMyHours(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        
        User owner = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        
        if (owner.getBarberProfile() == null || owner.getBarberProfile().getShop() == null) {
            throw new RuntimeException("You are not associated with any shop");
        }

        return ResponseEntity.ok(ApiResponse.ok(workingHoursRepository.findByShopIdOrderByDayOfWeek(owner.getBarberProfile().getShop().getId())));
    }

    @Operation(summary = "Update shop working hours")
    @PutMapping("/my-shop/hours")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public ResponseEntity<ApiResponse<List<com.trimlink.module.shop.entity.WorkingHours>>> updateMyHours(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody List<WorkingHoursRequest> req) {
        
        User owner = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        
        if (owner.getBarberProfile() == null || owner.getBarberProfile().getShop() == null) {
            throw new RuntimeException("You are not associated with any shop");
        }

        UUID shopId = owner.getBarberProfile().getShop().getId();
        
        for (WorkingHoursRequest hourReq : req) {
            com.trimlink.module.shop.entity.WorkingHours wh = workingHoursRepository
                    .findByShopIdAndDayOfWeek(shopId, hourReq.getDayOfWeek())
                    .orElse(com.trimlink.module.shop.entity.WorkingHours.builder()
                            .shop(owner.getBarberProfile().getShop())
                            .dayOfWeek(hourReq.getDayOfWeek())
                            .build());
            
            wh.setOpenTime(hourReq.getOpenTime());
            wh.setCloseTime(hourReq.getCloseTime());
            wh.setClosed(hourReq.isClosed());
            workingHoursRepository.save(wh);
        }

        return ResponseEntity.ok(ApiResponse.ok("Hours updated", workingHoursRepository.findByShopIdOrderByDayOfWeek(shopId)));
    }

    // ─── Inner DTOs ──────────────────────────────────────────────────────────
    @Data
    public static class AvailabilityRequest {
        private boolean available;
    }
    
    @Data
    public static class AddStaffRequest {
        @NotBlank @Size(min = 10, max = 15) private String phoneNumber;
    }

    @Data
    public static class WorkLogRequest {
        @NotNull private Integer count;
        private String notes;
    }

    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkingHoursRequest {
        private java.time.DayOfWeek dayOfWeek;
        private java.time.LocalTime openTime;
        private java.time.LocalTime closeTime;
        private boolean closed;
    }

    @Data
    public static class ShopRequest {
        @NotBlank @Size(max = 200) private String name;
        @Size(max = 20)            private String phone;
        @NotBlank                  private String address;
        @NotBlank @Size(max = 100) private String city;
        private Double latitude;
        private Double longitude;
        @Size(max = 500)           private String description;
    }
}
