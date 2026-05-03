package com.trimlink.module.admin.service;

import com.trimlink.module.admin.dto.StaffPerformanceResponse;
import com.trimlink.module.admin.dto.DashboardStats;
import com.trimlink.module.booking.entity.AppointmentStatus;
import com.trimlink.module.booking.repository.AppointmentRepository;
import com.trimlink.module.queue.entity.QueueStatus;
import com.trimlink.module.queue.repository.QueueEntryRepository;
import com.trimlink.module.shop.entity.StaffShop;
import com.trimlink.module.shop.repository.StaffShopRepository;
import com.trimlink.module.user.entity.StaffProfile;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.StaffProfileRepository;
import com.trimlink.module.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService Unit Tests")
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private StaffProfileRepository staffProfileRepository;
    @Mock private StaffShopRepository staffShopRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private QueueEntryRepository queueEntryRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    @DisplayName("Dashboard includes expanded operational metrics")
    void getDashboardStats_returnsExpandedMetrics() {
        when(userRepository.countByDeletedFalse()).thenReturn(120L);
        when(staffProfileRepository.countByDeletedFalse()).thenReturn(14L);
        when(staffShopRepository.countByDeletedFalse()).thenReturn(8L);
        when(appointmentRepository.countByDeletedFalseAndScheduledStartBetween(any(), any())).thenReturn(12L, 240L);
        when(queueEntryRepository.countByStatusInAndDeletedFalse(
                List.of(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.IN_SERVICE))).thenReturn(17L);
        when(queueEntryRepository.countByStatusInAndDeletedFalse(List.of(QueueStatus.COMPLETED))).thenReturn(23L);
        when(appointmentRepository.countByStatusAndDeletedFalse(AppointmentStatus.PENDING)).thenReturn(9L);
        when(appointmentRepository.sumRevenueByShop(eq(null), any(), any()))
                .thenReturn(new BigDecimal("4500.00"), new BigDecimal("98000.00"));

        DashboardStats stats = adminService.getDashboardStats();

        assertThat(stats.getTotalUsers()).isEqualTo(120L);
        assertThat(stats.getTotalStaffs()).isEqualTo(14L);
        assertThat(stats.getTotalShops()).isEqualTo(8L);
        assertThat(stats.getTotalAppointmentsToday()).isEqualTo(12L);
        assertThat(stats.getTotalAppointmentsThisMonth()).isEqualTo(240L);
        assertThat(stats.getActiveQueueEntries()).isEqualTo(17L);
        assertThat(stats.getCompletedServicesToday()).isEqualTo(23L);
        assertThat(stats.getPendingAppointments()).isEqualTo(9L);
        assertThat(stats.getRevenueToday()).isEqualByComparingTo("4500.00");
        assertThat(stats.getRevenueThisMonth()).isEqualByComparingTo("98000.00");
    }

    @Test
    @DisplayName("Staff performance maps per-staff operational metrics")
    void listStaffPerformance_returnsMappedMetrics() {
        UUID staffId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID shopId = UUID.randomUUID();

        User staffUser = User.builder()
                .phoneNumber("+251911111111")
                .firstName("Dawit")
                .lastName("Haile")
                .role(Role.STAFF)
                .build();
        staffUser.setId(userId);

        StaffShop shop = StaffShop.builder().name("Bole Trim").build();
        shop.setId(shopId);

        StaffProfile staff = StaffProfile.builder()
                .user(staffUser)
                .shop(shop)
                .available(true)
                .averageRating(new BigDecimal("4.75"))
                .totalReviews(11)
                .build();
        staff.setId(staffId);

        when(staffProfileRepository.findAllActiveWithUser(any()))
                .thenReturn(new PageImpl<>(List.of(staff), PageRequest.of(0, 20), 1));
        when(appointmentRepository.countByStaffIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
                eq(staffId), eq(AppointmentStatus.COMPLETED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(4L, 52L);
        when(appointmentRepository.countByStaffIdAndStatusAndDeletedFalse(staffId, AppointmentStatus.PENDING))
                .thenReturn(3L);
        when(queueEntryRepository.countByStaffIdAndStatusInAndDeletedFalse(
                eq(staffId), eq(List.of(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.IN_SERVICE))))
                .thenReturn(5L);
        when(queueEntryRepository.countCompletedSince(eq(staffId), any(LocalDateTime.class))).thenReturn(7L);

        var result = adminService.listStaffPerformance(PageRequest.of(0, 20));
        StaffPerformanceResponse response = result.getContent().get(0);

        assertThat(response.getStaffId()).isEqualTo(staffId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getShopId()).isEqualTo(shopId);
        assertThat(response.getStaffName()).isEqualTo("Dawit Haile");
        assertThat(response.getAverageRating()).isEqualByComparingTo("4.75");
        assertThat(response.getTotalReviews()).isEqualTo(11);
        assertThat(response.getCompletedAppointmentsToday()).isEqualTo(4L);
        assertThat(response.getCompletedAppointmentsThisMonth()).isEqualTo(52L);
        assertThat(response.getPendingAppointments()).isEqualTo(3L);
        assertThat(response.getActiveQueueEntries()).isEqualTo(5L);
        assertThat(response.getCompletedQueueServicesToday()).isEqualTo(7L);
    }
}
