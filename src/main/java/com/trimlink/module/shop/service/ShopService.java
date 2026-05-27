package com.trimlink.module.shop.service;

import com.trimlink.module.audit.annotation.AuditAction;
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
import com.trimlink.module.shop.entity.BarberShop;
import com.trimlink.module.shop.repository.BarberShopRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import com.trimlink.module.user.dto.UserResponse;
import com.trimlink.module.user.entity.BarberProfile;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.repository.BarberProfileRepository;
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

    private final BarberShopRepository shopRepository;
    private final BarberProfileRepository barberProfileRepository;
    private final DailyWorkLogRepository dailyWorkLogRepository;
    private final AppointmentRepository appointmentRepository;
    private final QueueEntryRepository queueEntryRepository;

    @Transactional(readOnly = true)
    public Page<ShopSearchResponse> searchShops(String q, String city, String platform, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<BarberShop> spec = (root, query, cb) -> {
            jakarta.persistence.criteria.Predicate predicate = cb.conjunction();
            
            // Only active shops
            predicate = cb.and(predicate, cb.isTrue(root.get("active")));

            if (platform != null && !platform.isBlank()) {
                try {
                    com.trimlink.module.shop.entity.ShopPlatform enumPlatform = com.trimlink.module.shop.entity.ShopPlatform.valueOf(platform.toUpperCase());
                    predicate = cb.and(predicate, cb.equal(root.get("platform"), enumPlatform));
                } catch (IllegalArgumentException e) {
                    // ignore invalid platform
                }
            }
            
            if (q != null && !q.isBlank()) {
                String searchPattern = "%" + q.trim().toLowerCase() + "%";
                
                // Join for owner search
                jakarta.persistence.criteria.Join<BarberShop, com.trimlink.module.user.entity.BarberProfile> barbers = root.join("barbers", jakarta.persistence.criteria.JoinType.LEFT);
                jakarta.persistence.criteria.Join<com.trimlink.module.user.entity.BarberProfile, com.trimlink.module.user.entity.User> user = barbers.join("user", jakarta.persistence.criteria.JoinType.LEFT);
                
                jakarta.persistence.criteria.Predicate searchPredicate = cb.or(
                    cb.like(cb.lower(root.get("name")), searchPattern),
                    cb.like(cb.lower(root.get("city")), searchPattern),
                    cb.like(cb.lower(root.get("address")), searchPattern),
                    cb.like(cb.lower(root.get("phone")), searchPattern),
                    cb.and(
                        cb.equal(user.get("role"), com.trimlink.module.user.entity.Role.OWNER),
                        cb.or(
                            cb.like(cb.lower(user.get("firstName")), searchPattern),
                            cb.like(cb.lower(user.get("lastName")), searchPattern),
                            cb.like(cb.lower(user.get("phoneNumber")), searchPattern)
                        )
                    )
                );
                predicate = cb.and(predicate, searchPredicate);
                
                if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                    query.distinct(true);
                }
            } else if (city != null && !city.isBlank()) {
                predicate = cb.and(predicate, cb.equal(cb.lower(root.get("city")), city.trim().toLowerCase()));
            }
            
            return predicate;
        };

        Page<BarberShop> shops = shopRepository.findAll(spec, pageable);
        return shops.map(this::mapToSearchResponse);
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "shops", key = "#pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<ShopSearchResponse> listAllShops(Pageable pageable) {
        Page<BarberShop> shops = shopRepository.findAll(pageable);
        return shops.map(this::mapToSearchResponse);
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "shopDetails", key = "#id")
    public ShopSearchResponse getShopById(UUID id) {
        BarberShop shop = shopRepository.findById(id)
                .orElseThrow(() -> new com.trimlink.common.exception.ResourceNotFoundException("BarberShop", "id", id));
        return mapToSearchResponse(shop);
    }

    private ShopSearchResponse mapToSearchResponse(BarberShop shop) {
        String ownerName = "Unknown";
        String ownerPhone = "";
        
        List<BarberProfile> owners = barberProfileRepository.findByShopIdAndDeletedFalse(shop.getId(), Pageable.unpaged())
                .getContent().stream()
                .filter(b -> b.getUser().getRole() == Role.OWNER)
                .toList();
        
        if (!owners.isEmpty()) {
            BarberProfile owner = owners.get(0);
            ownerName = owner.getUser().getFirstName() + " " + owner.getUser().getLastName();
            ownerPhone = owner.getUser().getPhoneNumber();
        }
        
        long activeQueueCount = queueEntryRepository.countByShopIdAndStatusInAndDeletedFalse(
                shop.getId(), List.of(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.IN_SERVICE));
        
        long averageWaitMinutes = activeQueueCount * 15L;

        ShopSearchResponse response = ShopSearchResponse.from(shop, ownerName, ownerPhone);
        response.setActiveQueueCount(activeQueueCount);
        response.setAverageWaitMinutes(averageWaitMinutes);
        return response;
    }

    @Transactional(readOnly = true)
    public List<StaffPerformanceResponse> getStaffPerformance(UUID shopId) {
        List<BarberProfile> barbers = barberProfileRepository.findByShopIdAndDeletedFalse(shopId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        return barbers.stream().map(barber -> {
            int manualLogs = dailyWorkLogRepository.findByBarberIdAndLogDate(barber.getId(), today)
                    .map(DailyWorkLog::getCustomerCount)
                    .orElse(0);

            long appointments = appointmentRepository.countByBarberIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
                    barber.getId(), AppointmentStatus.COMPLETED, startOfDay, endOfDay);
            
            long queueEntries = queueEntryRepository.countCompletedSince(barber.getId(), startOfDay);

            int totalToday = manualLogs + (int)appointments + (int)queueEntries;

            return StaffPerformanceResponse.builder()
                    .user(UserResponse.from(barber.getUser()))
                    .barberId(barber.getId())
                    .available(barber.isAvailable())
                    .customersToday(totalToday)
                    .manualLogsToday(manualLogs)
                    .appBookingsToday((int)appointments + (int)queueEntries)
                    .totalReviews(barber.getTotalReviews())
                    .averageRating(barber.getAverageRating() != null ? barber.getAverageRating().doubleValue() : 0.0)
                    .weeklyAverage(0.0) // For now
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<WeeklyPerformanceResponse> getWeeklyReport(UUID shopId) {
        List<BarberProfile> barbers = barberProfileRepository.findByShopIdAndDeletedFalse(shopId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(6);

        return barbers.stream().map(barber -> {
            List<DailyWorkLog> logs = dailyWorkLogRepository.findByBarberIdAndLogDateBetween(barber.getId(), sevenDaysAgo, today);
            
            // For simplicity, we aggregate total for the week
            int manualTotal = logs.stream().mapToInt(DailyWorkLog::getCustomerCount).sum();
            
            LocalDateTime startOfWeek = sevenDaysAgo.atStartOfDay();
            LocalDateTime endOfWeek = today.plusDays(1).atStartOfDay();

            long appTotal = appointmentRepository.countByBarberIdAndStatusAndScheduledStartBetweenAndDeletedFalse(
                    barber.getId(), AppointmentStatus.COMPLETED, startOfWeek, endOfWeek);
            
            long queueTotal = queueEntryRepository.countCompletedSince(barber.getId(), startOfWeek);

            return WeeklyPerformanceResponse.builder()
                    .barberName(barber.getUser().getFirstName() + " " + barber.getUser().getLastName())
                    .barberId(barber.getId())
                    .totalCustomers((int) (manualTotal + appTotal + queueTotal))
                    .appBookings((int) (appTotal + queueTotal))
                    .manualEntries(manualTotal)
                    .dailyAverage((double) (manualTotal + appTotal + queueTotal) / 7.0)
                    .build();
        }).toList();
    }

    @Transactional
    @AuditAction(action = "LOG_DAILY_WORK", resource = "SHOP")
    public void logDailyWork(UUID barberId, int count, String notes) {
        LocalDate today = LocalDate.now();
        DailyWorkLog log = dailyWorkLogRepository.findByBarberIdAndLogDate(barberId, today)
                .orElse(DailyWorkLog.builder()
                        .barberId(barberId)
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
