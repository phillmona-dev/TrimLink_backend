package com.trimlink.module.shop.service;

import com.trimlink.module.booking.entity.AppointmentStatus;
import com.trimlink.module.booking.repository.AppointmentRepository;
import com.trimlink.module.queue.entity.QueueStatus;
import com.trimlink.module.queue.repository.QueueEntryRepository;
import com.trimlink.module.shop.dto.StaffPerformanceResponse;
import com.trimlink.module.shop.dto.WeeklyPerformanceResponse;
import com.trimlink.module.shop.entity.DailyWorkLog;
import com.trimlink.module.shop.repository.DailyWorkLogRepository;
import com.trimlink.module.shop.dto.ShopSearchResponse;
import com.trimlink.module.shop.dto.ShopStatsResponse;
import com.trimlink.module.shop.entity.StaffShop;
import com.trimlink.module.shop.repository.StaffShopRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import com.trimlink.module.user.dto.UserResponse;
import com.trimlink.module.user.entity.StaffProfile;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.repository.StaffProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final StaffShopRepository shopRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final DailyWorkLogRepository dailyWorkLogRepository;
    private final AppointmentRepository appointmentRepository;
    private final QueueEntryRepository queueEntryRepository;

    @Transactional(readOnly = true)
    public Page<ShopSearchResponse> searchShops(String q, String city, Pageable pageable) {
        Page<StaffShop> shops = (q != null && !q.isBlank())
                ? shopRepository.search(q.trim(), pageable)
                : (city != null && !city.isBlank())
                    ? shopRepository.findByCityAndActiveTrue(city, pageable)
                    : shopRepository.findByActiveTrue(pageable);

        return shops.map(shop -> {
            // Find the owner in the staffs list (which might be lazily loaded but we are in a transaction)
            String ownerName = "Unknown";
            String ownerPhone = "";
            
            // We can't rely on shop.getStaffs() because of @JsonIgnore and LAZY loading issues if not handled carefully
            // But here we are in @Transactional(readOnly = true), so we can access it if we want, 
            // OR we can query the staffProfileRepository specifically for the owner of this shop.
            
            List<StaffProfile> owners = staffProfileRepository.findByShopIdAndDeletedFalse(shop.getId(), Pageable.unpaged())
                    .getContent().stream()
                    .filter(b -> b.getUser().getRole() == Role.OWNER)
                    .toList();
            
            if (!owners.isEmpty()) {
                StaffProfile owner = owners.get(0);
                ownerName = owner.getUser().getFirstName() + " " + owner.getUser().getLastName();
                ownerPhone = owner.getUser().getPhoneNumber();
            }
            
            return ShopSearchResponse.from(shop, ownerName, ownerPhone);
        });
    }

    @Transactional(readOnly = true)
    public List<StaffPerformanceResponse> getStaffPerformance(UUID shopId) {
        List<StaffProfile> staffs = staffProfileRepository.findByShopIdAndDeletedFalse(shopId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        return staffs.stream().map(staff -> {
            int manualLogs = dailyWorkLogRepository.findByStaffIdAndLogDate(staff.getId(), today)
                    .map(DailyWorkLog::getCustomerCount)
                    .orElse(0);

            long appointments = appointmentRepository.countByStaffIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
                    staff.getId(), AppointmentStatus.COMPLETED, startOfDay, endOfDay);
            
            long queueEntries = queueEntryRepository.countCompletedSince(staff.getId(), startOfDay);

            int totalToday = manualLogs + (int)appointments + (int)queueEntries;

            return StaffPerformanceResponse.builder()
                    .user(UserResponse.from(staff.getUser()))
                    .staffId(staff.getId())
                    .available(staff.isAvailable())
                    .customersToday(totalToday)
                    .manualLogsToday(manualLogs)
                    .appBookingsToday((int)appointments + (int)queueEntries)
                    .totalReviews(staff.getTotalReviews())
                    .averageRating(staff.getAverageRating() != null ? staff.getAverageRating().doubleValue() : 0.0)
                    .weeklyAverage(0.0) // For now
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<WeeklyPerformanceResponse> getWeeklyReport(UUID shopId) {
        List<StaffProfile> staffs = staffProfileRepository.findByShopIdAndDeletedFalse(shopId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(6);

        return staffs.stream().map(staff -> {
            List<DailyWorkLog> logs = dailyWorkLogRepository.findByStaffIdAndLogDateBetween(staff.getId(), sevenDaysAgo, today);
            
            // For simplicity, we aggregate total for the week
            int manualTotal = logs.stream().mapToInt(DailyWorkLog::getCustomerCount).sum();
            
            LocalDateTime startOfWeek = sevenDaysAgo.atStartOfDay();
            LocalDateTime endOfWeek = today.plusDays(1).atStartOfDay();

            long appTotal = appointmentRepository.countByStaffIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
                    staff.getId(), AppointmentStatus.COMPLETED, startOfWeek, endOfWeek);
            
            long queueTotal = queueEntryRepository.countCompletedSince(staff.getId(), startOfWeek);

            return WeeklyPerformanceResponse.builder()
                    .staffName(staff.getUser().getFirstName() + " " + staff.getUser().getLastName())
                    .staffId(staff.getId())
                    .totalCustomers((int) (manualTotal + appTotal + queueTotal))
                    .appBookings((int) (appTotal + queueTotal))
                    .manualEntries(manualTotal)
                    .dailyAverage((double) (manualTotal + appTotal + queueTotal) / 7.0)
                    .build();
        }).toList();
    }

    @Transactional
    public void logDailyWork(UUID staffId, int count, String notes) {
        LocalDate today = LocalDate.now();
        DailyWorkLog log = dailyWorkLogRepository.findByStaffIdAndLogDate(staffId, today)
                .orElse(DailyWorkLog.builder()
                        .staffId(staffId)
                        .logDate(today)
                        .build());
        
        log.setCustomerCount(count);
        log.setNotes(notes);
        dailyWorkLogRepository.save(log);
    }
    @Transactional(readOnly = true)
    public ShopStatsResponse getShopStats(UUID shopId) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime endOfToday = today.plusDays(1).atStartOfDay();
        
        LocalDate yesterday = today.minusDays(1);
        LocalDateTime startOfYesterday = yesterday.atStartOfDay();
        LocalDateTime endOfYesterday = yesterday.plusDays(1).atStartOfDay();

        // 1. Revenue
        BigDecimal revToday = appointmentRepository.sumRevenueByShop(shopId, startOfToday, endOfToday);
        if (revToday == null) revToday = BigDecimal.ZERO;
        
        BigDecimal revYesterday = appointmentRepository.sumRevenueByShop(shopId, startOfYesterday, endOfYesterday);
        if (revYesterday == null) revYesterday = BigDecimal.ZERO;
        
        double revGrowth = 0;
        if (revYesterday.compareTo(BigDecimal.ZERO) > 0) {
            revGrowth = (revToday.subtract(revYesterday))
                    .divide(revYesterday, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
        }

        // 2. Appointments
        long apptsToday = appointmentRepository.countByShopIdAndScheduledStartBetweenAndDeletedFalse(shopId, startOfToday, endOfToday);
        
        // 3. Queue Traffic (Active waiting)
        long queueTraffic = queueEntryRepository.countByShopIdAndStatusInAndDeletedFalse(shopId, List.of(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.IN_SERVICE));

        // 4. Trend Data (last 7 days)
        List<ShopStatsResponse.ChartDataPoint> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            BigDecimal dayRev = appointmentRepository.sumRevenueByShop(shopId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
            if (dayRev == null) dayRev = BigDecimal.ZERO;
            trend.add(ShopStatsResponse.ChartDataPoint.builder()
                    .label(date.getDayOfWeek().name().substring(0, 3))
                    .value(dayRev.doubleValue())
                    .build());
        }

        return ShopStatsResponse.builder()
                .revenueToday(String.format("%,.0f ETB", revToday.doubleValue()))
                .revenueHelper(String.format("%+d%% vs yesterday", (int)revGrowth))
                .appointmentsToday((int)apptsToday)
                .appointmentsHelper("Peak starts at 4 PM")
                .queueTraffic((int)queueTraffic)
                .queueHelper("Highest at lunch")
                .repeatCustomerRate("68%") // Placeholder
                .repeatHelper("Strong retention")
                .revenueTrend(trend)
                .build();
    }
}
