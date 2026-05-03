package com.trimlink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.trimlink.module.shop.entity.WorkingHours;
import com.trimlink.module.shop.repository.StaffShopRepository;
import com.trimlink.module.shop.repository.WorkingHoursRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;

import java.time.DayOfWeek;
import java.time.LocalTime;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class TrimLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrimLinkApplication.class, args);
    }

    @Bean
    public CommandLineRunner initWorkingHours(
            StaffShopRepository shopRepository,
            WorkingHoursRepository workingHoursRepository) {
        return args -> {
            shopRepository.findAll().forEach(shop -> {
                for (DayOfWeek day : DayOfWeek.values()) {
                    boolean exists = workingHoursRepository
                            .findByShopIdAndDayOfWeek(shop.getId(), day).isPresent();
                    if (!exists) {
                        workingHoursRepository.save(WorkingHours.builder()
                                .shop(shop)
                                .dayOfWeek(day)
                                .openTime(LocalTime.of(8, 0))
                                .closeTime(LocalTime.of(20, 0))
                                .closed(false)
                                .build());
                    }
                }
            });
        };
    }
}
