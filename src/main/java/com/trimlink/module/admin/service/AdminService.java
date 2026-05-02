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

    /**
     * Dashboard aggregation — cached for 5 minutes to avoid heavyweight
     * queries on every admin page load during peak hours.
     */
    @Cacheable(value = "admin:dashboard", key = "#root.method.name")
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

        return barberProfileRepository.findAllActiveWithUser(pageable)
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

    private List<QueueStatus> activeQueueStatuses() {
        return List.of(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.IN_SERVICE);
    }
}
