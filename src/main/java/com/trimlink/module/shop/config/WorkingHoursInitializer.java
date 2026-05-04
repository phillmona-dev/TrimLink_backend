package com.trimlink.module.shop.config;

import com.trimlink.module.shop.entity.WorkingHours;
import com.trimlink.module.shop.repository.BarberShopRepository;
import com.trimlink.module.shop.repository.WorkingHoursRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Ensures all shops have working hours defined for every day of the week.
 * Skipped in 'test' profile to avoid breaking slice tests (WebMvcTest).
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class WorkingHoursInitializer implements CommandLineRunner {

    private final BarberShopRepository shopRepository;
    private final WorkingHoursRepository workingHoursRepository;

    @Override
    public void run(String... args) {
        log.info("Checking and initializing missing working hours for shops...");
        shopRepository.findAll().forEach(shop -> {
            for (DayOfWeek day : DayOfWeek.values()) {
                workingHoursRepository.findByShopIdAndDayOfWeek(shop.getId(), day)
                    .ifPresentOrElse(
                        h -> {
                            // Migration: Update old 8 PM cutoff to new 9 PM (3:00 LT Night) cutoff
                            if (h.getCloseTime().equals(LocalTime.of(20, 0))) {
                                log.info("Updating shop {} hours for {} to 21:00", shop.getName(), day);
                                h.setCloseTime(LocalTime.of(21, 0));
                                workingHoursRepository.save(h);
                            }
                        },
                        () -> {
                            // Initialization: Create default hours (8 AM - 9 PM)
                            workingHoursRepository.save(WorkingHours.builder()
                                    .shop(shop)
                                    .dayOfWeek(day)
                                    .openTime(LocalTime.of(8, 0))
                                    .closeTime(LocalTime.of(21, 0))
                                    .closed(false)
                                    .build());
                        }
                    );
            }
        });
    }
}
