package com.trimlink.module.booking.service;

import com.trimlink.common.exception.BusinessException;
import com.trimlink.common.exception.ConflictException;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.messaging.event.BookingCreatedEvent;
import com.trimlink.messaging.producer.EventProducer;
import com.trimlink.module.booking.dto.*;
import com.trimlink.module.booking.entity.Appointment;
import com.trimlink.module.booking.entity.AppointmentStatus;
import com.trimlink.module.booking.repository.AppointmentRepository;
import com.trimlink.module.service.entity.Service;
import com.trimlink.module.service.repository.ServiceRepository;
import com.trimlink.module.shop.entity.BarberShop;
import com.trimlink.module.shop.entity.WorkingHours;
import com.trimlink.module.shop.repository.BarberShopRepository;
import com.trimlink.module.shop.repository.WorkingHoursRepository;
import com.trimlink.module.user.entity.BarberProfile;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.BarberProfileRepository;
import com.trimlink.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class BookingService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final BarberProfileRepository barberProfileRepository;
    private final BarberShopRepository barberShopRepository;
    private final ServiceRepository serviceRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final EventProducer eventProducer;
    private final com.trimlink.module.notification.service.WebSocketNotificationService webSocketNotificationService;

    // ─── Create Booking ────────────────────────────────────────────────────

    @Transactional
    public AppointmentResponse createAppointment(UUID customerId, CreateAppointmentRequest req) {
        User customer         = findUser(customerId);
        BarberProfile barber  = findBarber(req.getBarberId());
        BarberShop shop       = findShop(req.getShopId());
        Service service       = findService(req.getServiceId());

        LocalDateTime start   = req.getScheduledStart();
        LocalDateTime end     = start.plusMinutes(service.getDurationMinutes());

        // 1. Validate shop is open on that day/time
        validateShopIsOpen(shop, start);

        // 2. Check for overlapping bookings (pessimistic lock inside repo)
        List<Appointment> overlaps = appointmentRepository.findOverlapping(
                barber.getId(), start, end);
        if (!overlaps.isEmpty()) {
            throw new ConflictException(
                    "The selected time slot is already booked. Please choose another.");
        }

        // 3. Determine effective price (barber override or service base)
        java.math.BigDecimal price = barber.getServiceAssignments().stream()
                .filter(a -> a.getService().getId().equals(service.getId()) && a.isActive())
                .map(a -> a.getCustomPrice() != null ? a.getCustomPrice() : service.getBasePrice())
                .findFirst()
                .orElse(service.getBasePrice());

        Appointment appointment = Appointment.builder()
                .customer(customer)
                .barber(barber)
                .shop(shop)
                .service(service)
                .scheduledStart(start)
                .scheduledEnd(end)
                .priceCharged(price)
                .notes(req.getNotes())
                .status(AppointmentStatus.PENDING)
                .build();

        appointment = appointmentRepository.save(appointment);

        // 4. Publish async event for notifications
        eventProducer.publishBookingCreated(BookingCreatedEvent.from(appointment));

        // 5. Notify barber via websocket
        webSocketNotificationService.notifyBarber(barber.getUser().getId(), toResponse(appointment));

        log.info("Appointment created: id={}, customer={}, barber={}, start={}",
                appointment.getId(), customerId, barber.getId(), start);

        return toResponse(appointment);
    }

    // ─── Available Slot Generation ─────────────────────────────────────────

    /**
     * Generates all possible time slots for a barber on a day,
     * marking each as available or taken.
     *
     * Algorithm:
     *  1. Get shop working hours for the day
     *  2. Walk from openTime to closeTime in steps of service.durationMinutes
     *  3. For each slot, check if it overlaps any existing appointment
     *  4. Return slot list with availability flag
     *
     * Time complexity: O(n) where n = number of slots in a working day
     */
    @Transactional(readOnly = true)
    public List<TimeSlotResponse> getAvailableSlots(SlotAvailabilityRequest req) {
        BarberProfile barber = findBarber(req.getBarberId());
        Service service      = findService(req.getServiceId());
        LocalDate date       = req.getDate();

        // Fetch existing appointments for the barber on that day
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd   = date.atTime(LocalTime.MAX);

        List<Appointment> existing = appointmentRepository.findBarberDaySchedule(
                barber.getId(), dayStart, dayEnd);

        // Get shop working hours for the day
        WorkingHours hours = workingHoursRepository
                .findByShopIdAndDayOfWeek(barber.getShop().getId(), date.getDayOfWeek())
                .orElseThrow(() -> new BusinessException("Shop is closed on " + date.getDayOfWeek()));

        if (hours.isClosed()) {
            return List.of();
        }

        List<TimeSlotResponse> slots = new ArrayList<>();
        LocalDateTime cursor = date.atTime(hours.getOpenTime());
        LocalDateTime shopClose = date.atTime(hours.getCloseTime());
        int duration = service.getDurationMinutes();

        while (!cursor.plusMinutes(duration).isAfter(shopClose)) {
            LocalDateTime slotEnd = cursor.plusMinutes(duration);
            LocalDateTime slotStart = cursor;

            boolean taken = existing.stream().anyMatch(a ->
                    a.getScheduledStart().isBefore(slotEnd) &&
                    a.getScheduledEnd().isAfter(slotStart));

            slots.add(TimeSlotResponse.builder()
                    .start(slotStart)
                    .end(slotEnd)
                    .available(!taken)
                    .build());

            cursor = cursor.plusMinutes(duration);
        }

        return slots;
    }

    // ─── Status Transitions ────────────────────────────────────────────────

    @Transactional
    public AppointmentResponse confirmAppointment(UUID appointmentId) {
        Appointment appt = findAppointment(appointmentId);
        appt.confirm();
        Appointment savedAppt = appointmentRepository.save(appt);
        // Notify the customer about confirmation
        webSocketNotificationService.notifyCustomer(savedAppt.getCustomer().getId(), toResponse(savedAppt));
        return toResponse(savedAppt);
    }

    @Transactional
    public AppointmentResponse rejectAppointment(UUID appointmentId, String reason) {
        Appointment appt = findAppointment(appointmentId);
        appt.reject(reason);
        Appointment savedAppt = appointmentRepository.save(appt);
        // Notify the customer about rejection
        webSocketNotificationService.notifyCustomer(savedAppt.getCustomer().getId(), toResponse(savedAppt));
        return toResponse(savedAppt);
    }

    @Transactional
    public AppointmentResponse requestRescheduleAppointment(UUID appointmentId, String reason) {
        Appointment appt = findAppointment(appointmentId);
        appt.requestReschedule(reason);
        Appointment savedAppt = appointmentRepository.save(appt);
        // Notify the customer about reschedule request
        webSocketNotificationService.notifyCustomer(savedAppt.getCustomer().getId(), toResponse(savedAppt));
        return toResponse(savedAppt);
    }

    @Transactional
    public AppointmentResponse completeAppointment(UUID appointmentId) {
        Appointment appt = findAppointment(appointmentId);
        appt.complete();
        eventProducer.publishBookingCompleted(appointmentId);
        return toResponse(appointmentRepository.save(appt));
    }

    @Transactional
    public AppointmentResponse cancelAppointment(UUID appointmentId, String reason) {
        Appointment appt = findAppointment(appointmentId);
        appt.cancel(reason);
        eventProducer.publishBookingCancelled(appointmentId);
        return toResponse(appointmentRepository.save(appt));
    }

    @Transactional
    public AppointmentResponse cancelAppointmentForUser(UUID appointmentId, UUID requesterId, String requesterRole, String reason) {
        Appointment appointment = findAppointment(appointmentId);
        enforceAppointmentAccess(appointment, requesterId, requesterRole);
        appointment.cancel(reason);
        eventProducer.publishBookingCancelled(appointmentId);
        return toResponse(appointmentRepository.save(appointment));
    }

    // ─── Queries ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getCustomerAppointments(UUID customerId, Pageable pageable) {
        return appointmentRepository.findByCustomerId(customerId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getBarberAppointments(UUID barberUserId, AppointmentStatus status, Pageable pageable) {
        return appointmentRepository.findByBarberUserIdAndStatus(barberUserId, status, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getById(UUID id) {
        return toResponse(findAppointment(id));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getByIdForUser(UUID id, UUID requesterId, String requesterRole) {
        Appointment appointment = findAppointment(id);
        enforceAppointmentAccess(appointment, requesterId, requesterRole);
        return toResponse(appointment);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private void validateShopIsOpen(BarberShop shop, LocalDateTime requestedStart) {
        workingHoursRepository
                .findByShopIdAndDayOfWeek(shop.getId(), requestedStart.getDayOfWeek())
                .ifPresentOrElse(hours -> {
                    if (hours.isClosed()) throw new BusinessException("Shop is closed on that day");
                    LocalTime time = requestedStart.toLocalTime();
                    if (time.isBefore(hours.getOpenTime()) || time.isAfter(hours.getCloseTime())) {
                        throw new BusinessException("Requested time is outside shop working hours.");
                    }
                }, () -> {
                    throw new BusinessException("No working hours configured for that day.");
                });
    }

    private AppointmentResponse toResponse(Appointment a) {
        return AppointmentResponse.builder()
                .id(a.getId())
                .customerId(a.getCustomer().getId())
                .customerName(a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName())
                .customerPhone(a.getCustomer().getPhoneNumber())
                .barberId(a.getBarber().getId())
                .barberName(a.getBarber().getUser().getFirstName() + " " + a.getBarber().getUser().getLastName())
                .shopId(a.getShop().getId())
                .shopName(a.getShop().getName())
                .shopAddress(a.getShop().getAddress())
                .serviceId(a.getService().getId())
                .serviceName(a.getService().getName())
                .serviceDurationMinutes(a.getService().getDurationMinutes())
                .scheduledStart(a.getScheduledStart())
                .scheduledEnd(a.getScheduledEnd())
                .actualStart(a.getActualStart())
                .actualEnd(a.getActualEnd())
                .status(a.getStatus())
                .priceCharged(a.getPriceCharged())
                .notes(a.getNotes())
                .cancellationReason(a.getCancellationReason())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private void enforceAppointmentAccess(Appointment appointment, UUID requesterId, String requesterRole) {
        if ("ADMIN".equalsIgnoreCase(requesterRole) || "OWNER".equalsIgnoreCase(requesterRole)) {
            return;
        }

        boolean isCustomer = appointment.getCustomer().getId().equals(requesterId);
        boolean isAssignedBarber = appointment.getBarber().getUser().getId().equals(requesterId);
        if (!isCustomer && !isAssignedBarber) {
            throw new AccessDeniedException("You are not allowed to access this appointment.");
        }
    }

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private BarberProfile findBarber(UUID id) {
        return barberProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BarberProfile", "id", id));
    }

    private BarberShop findShop(UUID id) {
        return barberShopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BarberShop", "id", id));
    }

    private Service findService(UUID id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", id));
    }

    private Appointment findAppointment(UUID id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", id));
    }
}
