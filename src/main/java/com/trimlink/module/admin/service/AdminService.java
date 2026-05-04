package com.trimlink.module.admin.service;

import com.trimlink.module.admin.dto.DashboardStats;
import com.trimlink.module.admin.dto.BarberPerformanceResponse;
import com.trimlink.module.booking.entity.AppointmentStatus;
import com.trimlink.module.booking.repository.AppointmentRepository;
import com.trimlink.module.queue.entity.QueueStatus;
import com.trimlink.module.queue.repository.QueueEntryRepository;
import com.trimlink.module.shop.repository.BarberShopRepository;
import com.trimlink.module.user.dto.UserResponse;
import com.trimlink.module.user.entity.BarberProfile;
import com.trimlink.module.user.repository.BarberProfileRepository;
import com.trimlink.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository            userRepository;
    private final BarberProfileRepository   barberProfileRepository;
    private final BarberShopRepository      barberShopRepository;
    private final AppointmentRepository     appointmentRepository;
    private final QueueEntryRepository      queueEntryRepository;

    @Transactional(readOnly = true)
    public DashboardStats getDashboardStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd   = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        long totalUsers    = userRepository.countByDeletedFalse();
        long totalBarbers  = barberProfileRepository.countByDeletedFalse();
        long totalShops    = barberShopRepository.countByDeletedFalse();
        long totalAppointmentsToday = appointmentRepository
                .countByDeletedFalseAndScheduledStartBetween(todayStart, todayEnd);
        long totalAppointmentsThisMonth = appointmentRepository
                .countByDeletedFalseAndScheduledStartBetween(monthStart, todayEnd);
        long activeQueueEntries = queueEntryRepository
                .countByStatusInAndDeletedFalse(activeQueueStatuses());
        long completedServicesToday = queueEntryRepository
                .countByStatusInAndDeletedFalse(List.of(QueueStatus.COMPLETED));
        long pendingAppointments = appointmentRepository.countByStatusAndDeletedFalse(AppointmentStatus.PENDING);

        // Revenue queries
        BigDecimal revenueToday = safeDecimal(
            appointmentRepository.sumRevenueByShop(null, todayStart, todayEnd));
        BigDecimal revenueMonth = safeDecimal(
            appointmentRepository.sumRevenueByShop(null, monthStart, todayEnd));

        return DashboardStats.builder()
                .totalUsers(totalUsers)
                .totalBarbers(totalBarbers)
                .totalShops(totalShops)
                .totalAppointmentsToday(totalAppointmentsToday)
                .totalAppointmentsThisMonth(totalAppointmentsThisMonth)
                .activeQueueEntries(activeQueueEntries)
                .revenueToday(revenueToday)
                .revenueThisMonth(revenueMonth)
                .completedServicesToday(completedServicesToday)
                .pendingAppointments(pendingAppointments)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getPendingShops() {
        return userRepository.findByApprovalStatusAndDeletedFalse(com.trimlink.module.user.entity.ApprovalStatus.PENDING)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public void approveUser(java.util.UUID userId) {
        com.trimlink.module.user.entity.User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.trimlink.common.exception.ResourceNotFoundException("User", "id", userId));
        user.setApprovalStatus(com.trimlink.module.user.entity.ApprovalStatus.APPROVED);
        user.setActive(true);
        userRepository.save(user);
        
        // Also activate the shop if it's a shop owner
        if (user.getRole() == com.trimlink.module.user.entity.Role.OWNER && user.getBarberProfile() != null && user.getBarberProfile().getShop() != null) {
            com.trimlink.module.shop.entity.BarberShop shop = user.getBarberProfile().getShop();
            shop.setActive(true);
            barberShopRepository.save(shop);
        }
    }

    @Transactional
    public void rejectUser(java.util.UUID userId) {
        com.trimlink.module.user.entity.User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.trimlink.common.exception.ResourceNotFoundException("User", "id", userId));
        user.setApprovalStatus(com.trimlink.module.user.entity.ApprovalStatus.REJECTED);
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Page<BarberPerformanceResponse> listBarberPerformance(Pageable pageable) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        return barberProfileRepository.searchActiveWithUser(null, pageable)
                .map(barber -> toBarberPerformance(barber, todayStart, monthStart, now));
    }

    private BigDecimal safeDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BarberPerformanceResponse toBarberPerformance(BarberProfile barber,
                                                          LocalDateTime todayStart,
                                                          LocalDateTime monthStart,
                                                          LocalDateTime now) {
        long completedAppointmentsToday = appointmentRepository
                .countByBarberIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
                        barber.getId(), AppointmentStatus.COMPLETED, todayStart, now);
        long completedAppointmentsThisMonth = appointmentRepository
                .countByBarberIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
                        barber.getId(), AppointmentStatus.COMPLETED, monthStart, now);
        long pendingAppointments = appointmentRepository
                .countByBarberIdAndStatusAndDeletedFalse(barber.getId(), AppointmentStatus.PENDING);
        long activeQueueEntries = queueEntryRepository
                .countByBarberIdAndStatusInAndDeletedFalse(barber.getId(), activeQueueStatuses());
        long completedQueueServicesToday = queueEntryRepository.countCompletedSince(barber.getId(), todayStart);

        return BarberPerformanceResponse.builder()
                .barberId(barber.getId())
                .userId(barber.getUser().getId())
                .barberName(barber.getUser().getFirstName() + " " + barber.getUser().getLastName())
                .phoneNumber(barber.getUser().getPhoneNumber())
                .shopId(barber.getShop() != null ? barber.getShop().getId() : null)
                .shopName(barber.getShop() != null ? barber.getShop().getName() : null)
                .available(barber.isAvailable())
                .averageRating(barber.getAverageRating())
                .totalReviews(barber.getTotalReviews())
                .completedAppointmentsToday(completedAppointmentsToday)
                .completedAppointmentsThisMonth(completedAppointmentsThisMonth)
                .pendingAppointments(pendingAppointments)
                .activeQueueEntries(activeQueueEntries)
                .completedQueueServicesToday(completedQueueServicesToday)
                .build();
    }

    @Transactional(readOnly = true)
    public AdminAppointmentStats getAppointmentStats() {
        long approved = appointmentRepository.countByStatusAndDeletedFalse(AppointmentStatus.COMPLETED)
                + appointmentRepository.countByStatusAndDeletedFalse(AppointmentStatus.CONFIRMED);
        long pending  = appointmentRepository.countByStatusAndDeletedFalse(AppointmentStatus.PENDING);
        long rejected = appointmentRepository.countByStatusAndDeletedFalse(AppointmentStatus.REJECTED);
        
        BigDecimal totalRevenue = safeDecimal(appointmentRepository.sumRevenueByShop(null, LocalDateTime.MIN, LocalDateTime.MAX));
        
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        BigDecimal revenueToday = safeDecimal(appointmentRepository.sumRevenueByShop(null, todayStart, todayEnd));
        
        // Admin share (10% of total revenue)
        BigDecimal adminShare = totalRevenue.multiply(new BigDecimal("0.10"));

        List<AdminAppointmentStats.ShopRevenue> shopRevenues = appointmentRepository.sumRevenueGroupByShop().stream()
                .map(row -> AdminAppointmentStats.ShopRevenue.builder()
                        .shopId((java.util.UUID) row[0])
                        .shopName((String) row[1])
                        .revenue(safeDecimal((BigDecimal) row[2]))
                        .build())
                .toList();

        List<AdminAppointmentStats.BarberRevenue> barberRevenues = appointmentRepository.sumRevenueGroupByBarber().stream()
                .map(row -> AdminAppointmentStats.BarberRevenue.builder()
                        .barberId((java.util.UUID) row[0])
                        .barberName(row[1] + " " + row[2])
                        .revenue(safeDecimal((BigDecimal) row[3]))
                        .build())
                .toList();

        return AdminAppointmentStats.builder()
                .totalApproved(approved)
                .totalPending(pending)
                .totalRejected(rejected)
                .totalRevenue(totalRevenue)
                .revenueToday(revenueToday)
                .adminShare(adminShare)
                .shopRevenues(shopRevenues)
                .barberRevenues(barberRevenues)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<com.trimlink.module.booking.dto.AppointmentResponse> listAllAppointments(
            java.util.UUID shopId, 
            java.util.UUID barberId, 
            AppointmentStatus status,
            LocalDate startDate,
            LocalDate endDate,
            String queryStr,
            Pageable pageable) {
        
        // Using Specification for complex filtering
        org.springframework.data.jpa.domain.Specification<com.trimlink.module.booking.entity.Appointment> spec = (root, query, cb) -> {
            jakarta.persistence.criteria.Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.isFalse(root.get("deleted")));
            
            if (shopId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("shop").get("id"), shopId));
            }
            if (barberId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("barber").get("id"), barberId));
            }
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            if (startDate != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("scheduledStart"), startDate.atStartOfDay()));
            }
            if (endDate != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("scheduledStart"), endDate.atTime(LocalTime.MAX)));
            }
            if (queryStr != null && !queryStr.isBlank()) {
                String pattern = "%" + queryStr.toLowerCase() + "%";
                jakarta.persistence.criteria.Predicate searchPredicate = cb.or(
                    cb.like(cb.lower(root.get("customer").get("firstName")), pattern),
                    cb.like(cb.lower(root.get("customer").get("lastName")), pattern),
                    cb.like(cb.lower(root.get("barber").get("user").get("firstName")), pattern),
                    cb.like(cb.lower(root.get("barber").get("user").get("lastName")), pattern),
                    cb.like(cb.lower(root.get("shop").get("name")), pattern)
                );
                predicate = cb.and(predicate, searchPredicate);
            }
            
            // Fetch related entities
            if (query.getResultType() != Long.class) {
                root.fetch("customer", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("shop", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("barber", jakarta.persistence.criteria.JoinType.LEFT).fetch("user", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("service", jakarta.persistence.criteria.JoinType.LEFT);
            }
            
            return predicate;
        };

        // We need a mapper to Response DTO. I'll reuse the one from BookingService if accessible or implement a simple one here.
        // For now, I'll use a local lambda.
        return appointmentRepository.findAll(spec, pageable).map(a -> com.trimlink.module.booking.dto.AppointmentResponse.builder()
                .id(a.getId())
                .customerId(a.getCustomer() != null ? a.getCustomer().getId() : null)
                .customerName(a.getCustomer() != null ? a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName() : "Walk-in")
                .barberId(a.getBarber().getId())
                .barberName(a.getBarber().getUser().getFirstName() + " " + a.getBarber().getUser().getLastName())
                .shopId(a.getShop().getId())
                .shopName(a.getShop().getName())
                .serviceId(a.getService() != null ? a.getService().getId() : null)
                .serviceName(a.getService() != null ? a.getService().getName() : "Custom Service")
                .scheduledStart(a.getScheduledStart())
                .scheduledEnd(a.getScheduledEnd())
                .status(a.getStatus())
                .priceCharged(a.getPriceCharged())
                .build());
    }

    private List<QueueStatus> activeQueueStatuses() {
        return List.of(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.IN_SERVICE);
    }
}
