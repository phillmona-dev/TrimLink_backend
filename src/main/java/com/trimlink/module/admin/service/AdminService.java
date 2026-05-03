package com.trimlink.module.admin.service;

import com.trimlink.module.admin.dto.DashboardStats;
import com.trimlink.module.admin.dto.StaffPerformanceResponse;
import com.trimlink.module.booking.entity.AppointmentStatus;
import com.trimlink.module.booking.repository.AppointmentRepository;
import com.trimlink.module.queue.entity.QueueStatus;
import com.trimlink.module.queue.repository.QueueEntryRepository;
import com.trimlink.module.shop.repository.StaffShopRepository;
import com.trimlink.module.user.dto.UserResponse;
import com.trimlink.module.user.entity.StaffProfile;
import com.trimlink.module.user.repository.StaffProfileRepository;
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
    private final StaffProfileRepository   staffProfileRepository;
    private final StaffShopRepository      staffShopRepository;
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
        long totalStaffs  = staffProfileRepository.countByDeletedFalse();
        long totalShops    = staffShopRepository.countByDeletedFalse();
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
                .totalStaffs(totalStaffs)
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
        if (user.getRole() == com.trimlink.module.user.entity.Role.OWNER && user.getStaffProfile() != null && user.getStaffProfile().getShop() != null) {
            com.trimlink.module.shop.entity.StaffShop shop = user.getStaffProfile().getShop();
            shop.setActive(true);
            staffShopRepository.save(shop);
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
    public Page<StaffPerformanceResponse> listStaffPerformance(Pageable pageable) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        return staffProfileRepository.findAllActiveWithUser(pageable)
                .map(staff -> toStaffPerformance(staff, todayStart, monthStart, now));
    }

    private BigDecimal safeDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private StaffPerformanceResponse toStaffPerformance(StaffProfile staff,
                                                          LocalDateTime todayStart,
                                                          LocalDateTime monthStart,
                                                          LocalDateTime now) {
        long completedAppointmentsToday = appointmentRepository
                .countByStaffIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
                        staff.getId(), AppointmentStatus.COMPLETED, todayStart, now);
        long completedAppointmentsThisMonth = appointmentRepository
                .countByStaffIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
                        staff.getId(), AppointmentStatus.COMPLETED, monthStart, now);
        long pendingAppointments = appointmentRepository
                .countByStaffIdAndStatusAndDeletedFalse(staff.getId(), AppointmentStatus.PENDING);
        long activeQueueEntries = queueEntryRepository
                .countByStaffIdAndStatusInAndDeletedFalse(staff.getId(), activeQueueStatuses());
        long completedQueueServicesToday = queueEntryRepository.countCompletedSince(staff.getId(), todayStart);

        return StaffPerformanceResponse.builder()
                .staffId(staff.getId())
                .userId(staff.getUser().getId())
                .staffName(staff.getUser().getFirstName() + " " + staff.getUser().getLastName())
                .phoneNumber(staff.getUser().getPhoneNumber())
                .shopId(staff.getShop() != null ? staff.getShop().getId() : null)
                .shopName(staff.getShop() != null ? staff.getShop().getName() : null)
                .available(staff.isAvailable())
                .averageRating(staff.getAverageRating())
                .totalReviews(staff.getTotalReviews())
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
