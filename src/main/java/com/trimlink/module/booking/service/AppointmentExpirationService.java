package com.trimlink.module.booking.service;

import com.trimlink.module.booking.entity.Appointment;
import com.trimlink.module.booking.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Background service to cancel unpaid appointments.
 * 
 * When a customer creates a booking, it holds the slot in PENDING status.
 * This service ensures that if they don't pay within the timeout window,
 * the booking is cancelled and the slot is freed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentExpirationService {

    private final AppointmentRepository appointmentRepository;
    private final BookingService bookingService;

    @Value("${trimlink.booking.expiration-minutes:20}")
    private int expirationMinutes;

    /**
     * Runs every 5 minutes.
     * Finds PENDING appointments that are older than the expiration threshold.
     */
    @Scheduled(fixedRateString = "${trimlink.booking.expiration-check-rate:300000}")
    public void expireOldPendingAppointments() {
        log.info("Starting appointment expiration job...");

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(expirationMinutes);
        
        List<Appointment> oldPending = appointmentRepository.findPendingOlderThan(cutoff);
        
        if (oldPending.isEmpty()) {
            log.debug("No old pending appointments found for expiration.");
            return;
        }

        log.info("Found {} old pending appointments to expire.", oldPending.size());

        for (Appointment appt : oldPending) {
            try {
                // Double check status before expiring
                bookingService.expireAppointment(appt);
            } catch (Exception e) {
                log.error("Failed to expire appointment id={}: {}", appt.getId(), e.getMessage());
            }
        }

        log.info("Appointment expiration job completed.");
    }
}
