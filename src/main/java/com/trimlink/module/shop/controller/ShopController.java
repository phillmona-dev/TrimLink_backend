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
    private final com.trimlink.module.booking.repository.AppointmentRepository appointmentRepository;
    private final com.trimlink.module.shop.repository.ShopBankAccountRepository bankAccountRepository;

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

    @Operation(summary = "List all shops (Admin only)")
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ShopSearchResponse>>> listAll(
            @PageableDefault(size = 50) Pageable pageable) {
        var page = shopService.listAllShops(pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    // GET /shops/{id}
    @Operation(summary = "Get shop details by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShopSearchResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(shopService.getShopById(id)));
    }

    @GetMapping("/{id}/barbers")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<BarberResponse>>> getBarbers(@PathVariable UUID id) {
        List<BarberProfile> barbers = barberProfileRepository.findByShopIdAndDeletedFalseAndAvailableTrueOrderByAverageRatingDesc(id);
        
        List<BarberResponse> response = barbers.stream()
                .map(b -> {
                    boolean isBusy = appointmentRepository.existsByBarberIdAndStatusAndDeletedFalse(
                            b.getId(), com.trimlink.module.booking.entity.AppointmentStatus.IN_PROGRESS);
                    return BarberResponse.from(b, isBusy ? "BUSY" : "IDLE");
                })
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

        if (req.getBankAccounts() != null) {
            shop.setBankAccounts(req.getBankAccounts().stream()
                    .map(acc -> com.trimlink.module.shop.entity.ShopBankAccount.builder()
                            .shop(shop)
                            .bankName(acc.getBankName())
                            .accountNumber(acc.getAccountNumber())
                            .accountHolder(acc.getAccountHolder())
                            .build())
                    .collect(java.util.stream.Collectors.toList()));
        }

        BarberShop savedShop = shopRepository.save(shop);

        // Initialize default working hours
        for (java.time.DayOfWeek day : java.time.DayOfWeek.values()) {
            workingHoursRepository.save(com.trimlink.module.shop.entity.WorkingHours.builder()
                    .shop(shop)
                    .dayOfWeek(day)
                    .openTime(java.time.LocalTime.of(8, 0))
                    .closeTime(java.time.LocalTime.of(21, 0))
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
        
        // Save basic shop fields first (without touching bankAccounts)
        UUID shopId = shop.getId();
        shopRepository.saveAndFlush(shop);

        // Delete all existing bank accounts.
        // clearAutomatically=true evicts them from the persistence context,
        // preventing cascade from re-inserting stale data.
        bankAccountRepository.deleteByShopId(shopId);

        if (req.getBankAccounts() != null && !req.getBankAccounts().isEmpty()) {
            // After clearAutomatically, 'shop' is detached. Use getReferenceById to
            // get a fresh managed proxy for the FK reference.
            BarberShop shopRef = shopRepository.getReferenceById(shopId);
            List<com.trimlink.module.shop.entity.ShopBankAccount> newAccounts = req.getBankAccounts().stream()
                .filter(acc -> acc.getBankName() != null && !acc.getBankName().isBlank()
                            && acc.getAccountNumber() != null && !acc.getAccountNumber().isBlank()
                            && acc.getAccountHolder() != null && !acc.getAccountHolder().isBlank())
                .map(acc -> com.trimlink.module.shop.entity.ShopBankAccount.builder()
                    .shop(shopRef)
                    .bankName(acc.getBankName())
                    .accountNumber(acc.getAccountNumber())
                    .accountHolder(acc.getAccountHolder())
                    .build())
                .collect(java.util.stream.Collectors.toList());
            bankAccountRepository.saveAll(newAccounts);
        }

        // Reload a fresh snapshot from DB for the response
        BarberShop savedShop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("BarberShop", "id", shopId));
        return ResponseEntity.ok(ApiResponse.ok(savedShop));
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

    @Operation(summary = "Activate a shop")
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable UUID id) {
        BarberShop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BarberShop", "id", id));
        shop.setActive(true);
        shopRepository.save(shop);
        return ResponseEntity.ok(ApiResponse.ok("Shop activated", null));
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

    @Operation(summary = "Get my shop details")
    @GetMapping("/my-shop")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<ShopSearchResponse>> getMyShop(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        User owner = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        
        if (owner.getBarberProfile() == null || owner.getBarberProfile().getShop() == null) {
            throw new ResourceNotFoundException("BarberShop", "ownerId", principal.getUserId());
        }
        
        BarberShop shop = owner.getBarberProfile().getShop();
        String ownerName = owner.getFirstName() + " " + owner.getLastName();
        String ownerPhone = owner.getPhoneNumber();
        
        return ResponseEntity.ok(ApiResponse.ok(ShopSearchResponse.from(shop, ownerName, ownerPhone)));
    }

    @Operation(summary = "Update my shop details")
    @PutMapping("/my-shop")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public ResponseEntity<ApiResponse<ShopSearchResponse>> updateMyShop(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ShopRequest req) {
        User owner = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        
        if (owner.getBarberProfile() == null || owner.getBarberProfile().getShop() == null) {
            throw new ResourceNotFoundException("BarberShop", "ownerId", principal.getUserId());
        }
        
        BarberShop shop = owner.getBarberProfile().getShop();
        shop.setName(req.getName());
        shop.setPhone(req.getPhone());
        shop.setAddress(req.getAddress());
        shop.setCity(req.getCity());
        shop.setLatitude(req.getLatitude());
        shop.setLongitude(req.getLongitude());
        shop.setDescription(req.getDescription());
        
        // Save basic shop fields first (without touching bankAccounts)
        UUID shopId = shop.getId();
        shopRepository.saveAndFlush(shop);

        // Delete all existing bank accounts.
        // clearAutomatically=true evicts them from the persistence context,
        // preventing cascade from re-inserting stale data.
        bankAccountRepository.deleteByShopId(shopId);

        if (req.getBankAccounts() != null && !req.getBankAccounts().isEmpty()) {
            // After clearAutomatically, 'shop' is detached. Use getReferenceById to
            // get a fresh managed proxy for the FK reference.
            BarberShop shopRef = shopRepository.getReferenceById(shopId);
            List<com.trimlink.module.shop.entity.ShopBankAccount> newAccounts = req.getBankAccounts().stream()
                .filter(acc -> acc.getBankName() != null && !acc.getBankName().isBlank()
                            && acc.getAccountNumber() != null && !acc.getAccountNumber().isBlank())
                .map(acc -> com.trimlink.module.shop.entity.ShopBankAccount.builder()
                    .shop(shopRef)
                    .bankName(acc.getBankName())
                    .accountNumber(acc.getAccountNumber())
                    .accountHolder(acc.getAccountHolder())
                    .build())
                .collect(java.util.stream.Collectors.toList());
            bankAccountRepository.saveAll(newAccounts);
        }

        // Reload a fresh snapshot from DB for the response
        BarberShop savedShop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("BarberShop", "id", shopId));
        String ownerName = owner.getFirstName() + " " + owner.getLastName();
        String ownerPhone = owner.getPhoneNumber();
        return ResponseEntity.ok(ApiResponse.ok(ShopSearchResponse.from(savedShop, ownerName, ownerPhone)));
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

    @Operation(summary = "Get all appointments for my shop (owner)")
    @GetMapping("/my-shop/appointments")
    @PreAuthorize("hasRole('OWNER')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<com.trimlink.module.booking.dto.AppointmentResponse>>> getMyShopAppointments(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) com.trimlink.module.booking.entity.AppointmentStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @PageableDefault(size = 20, sort = "scheduledStart", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {

        User owner = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));

        if (owner.getBarberProfile() == null || owner.getBarberProfile().getShop() == null) {
            throw new RuntimeException("You are not associated with any shop");
        }

        UUID shopId = owner.getBarberProfile().getShop().getId();

        java.time.LocalDateTime from = startDate != null ? startDate.atStartOfDay() : java.time.LocalDateTime.of(2000, 1, 1, 0, 0);
        java.time.LocalDateTime to = endDate != null ? endDate.atTime(23, 59, 59) : java.time.LocalDateTime.of(2100, 1, 1, 0, 0);

        org.springframework.data.domain.Page<com.trimlink.module.booking.entity.Appointment> page =
                appointmentRepository.findByShopIdWithFilters(shopId, status, from, to, query, pageable);

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page.map(a -> {
            String custName = "Walk-in";
            if (a.getCustomer() != null) {
                custName = a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName();
            }
            String svcName = a.getService() != null ? a.getService().getName() : "N/A";
            String barberName = "";
            if (a.getBarber() != null && a.getBarber().getUser() != null) {
                barberName = a.getBarber().getUser().getFirstName() + " " + a.getBarber().getUser().getLastName();
            }
            return com.trimlink.module.booking.dto.AppointmentResponse.builder()
                    .id(a.getId())
                    .shopName(a.getShop().getName())
                    .barberName(barberName)
                    .customerName(custName)
                    .serviceName(svcName)
                    .scheduledStart(a.getScheduledStart())
                    .scheduledEnd(a.getScheduledEnd())
                    .status(a.getStatus())
                    .priceCharged(a.getPriceCharged())
                    .build();
        }))));
    }

    @Operation(summary = "Get financial summary for my shop (owner)")
    @GetMapping("/my-shop/finance")
    @PreAuthorize("hasRole('OWNER')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getMyShopFinance(
            @AuthenticationPrincipal AuthenticatedUser principal) {

        User owner = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));

        if (owner.getBarberProfile() == null || owner.getBarberProfile().getShop() == null) {
            throw new RuntimeException("You are not associated with any shop");
        }

        UUID shopId = owner.getBarberProfile().getShop().getId();
        String shopName = owner.getBarberProfile().getShop().getName();

        java.time.LocalDateTime farPast = java.time.LocalDateTime.of(2000, 1, 1, 0, 0);
        java.time.LocalDateTime farFuture = java.time.LocalDateTime.of(2100, 1, 1, 0, 0);
        java.time.LocalDateTime todayStart = java.time.LocalDate.now().atStartOfDay();
        java.time.LocalDateTime todayEnd = java.time.LocalDate.now().atTime(java.time.LocalTime.MAX);

        java.math.BigDecimal totalRevenue = appointmentRepository.sumRevenueByShop(shopId, farPast, farFuture);
        if (totalRevenue == null) totalRevenue = java.math.BigDecimal.ZERO;

        java.math.BigDecimal revenueToday = appointmentRepository.sumRevenueByShop(shopId, todayStart, todayEnd);
        if (revenueToday == null) revenueToday = java.math.BigDecimal.ZERO;

        long totalApproved = appointmentRepository.countByShopIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
                shopId, com.trimlink.module.booking.entity.AppointmentStatus.COMPLETED, farPast, farFuture)
                + appointmentRepository.countByShopIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
                shopId, com.trimlink.module.booking.entity.AppointmentStatus.CONFIRMED, farPast, farFuture);

        long totalPending = appointmentRepository.countByShopIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
                shopId, com.trimlink.module.booking.entity.AppointmentStatus.PENDING, farPast, farFuture);

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("shopId", shopId.toString());
        result.put("shopName", shopName);
        result.put("totalRevenue", totalRevenue);
        result.put("revenueToday", revenueToday);
        result.put("totalApproved", totalApproved);
        result.put("totalPending", totalPending);

        return ResponseEntity.ok(ApiResponse.ok(result));
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
        private List<BankAccountRequest> bankAccounts;

        @Data
        public static class BankAccountRequest {
            private String bankName;
            private String accountNumber;
            private String accountHolder;
        }
    }
}
