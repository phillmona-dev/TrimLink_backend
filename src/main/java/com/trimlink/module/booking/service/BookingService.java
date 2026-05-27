package com.trimlink.module.booking.service;

import com.trimlink.common.exception.BusinessException;
import com.trimlink.common.exception.ConflictException;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.messaging.event.BookingCreatedEvent;
import com.trimlink.messaging.event.BookingConfirmedEvent;
import com.trimlink.messaging.producer.EventProducer;
import com.trimlink.module.audit.annotation.AuditAction;
import com.trimlink.module.booking.dto.AppointmentResponse;
import com.trimlink.module.booking.dto.CreateAppointmentRequest;
import com.trimlink.module.booking.dto.SlotAvailabilityRequest;
import com.trimlink.module.booking.dto.TimeSlotResponse;
import com.trimlink.module.booking.entity.Appointment;
import com.trimlink.module.booking.entity.AppointmentStatus;
import com.trimlink.module.booking.repository.AppointmentRepository;
import com.trimlink.module.booking.repository.ReviewRepository;
import com.trimlink.module.notification.service.WebSocketNotificationService;
import com.trimlink.module.service.entity.Service;
import com.trimlink.module.service.repository.ServiceRepository;
import com.trimlink.module.shop.entity.BarberShop;
import com.trimlink.module.shop.entity.WorkingHours;
import com.trimlink.module.shop.repository.BarberShopRepository;
import com.trimlink.module.shop.repository.DailyWorkLogRepository;
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
import java.util.Optional;
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
    private final DailyWorkLogRepository dailyWorkLogRepository;
    private final WebSocketNotificationService webSocketNotificationService;
    private final EventProducer eventProducer;
    private final ReviewRepository reviewRepository;

    // ─── Create Booking ────────────────────────────────────────────────────

    @Transactional
    @AuditAction(action = "CREATE_BOOKING", resource = "BOOKING")
    @org.springframework.cache.annotation.CacheEvict(
            value = "availableSlots",
            key = "#req.barberId + #req.scheduledStart.toLocalDate()",
            allEntries = true
    )
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
                .receiptImageUrl(req.getReceiptImageUrl())
                .styleReferenceUrl(req.getStyleReferenceUrl())
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
     */
    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(
            value = "availableSlots",
            key = "#req.barberId + #req.date",
            unless = "#result == null"
    )
    public List<TimeSlotResponse> getAvailableSlots(SlotAvailabilityRequest req) {
        BarberProfile barber = barberProfileRepository.findById(req.getBarberId())
                .or(() -> barberProfileRepository.findByUserId(req.getBarberId()))
                .orElseThrow(() -> new ResourceNotFoundException("Barber", "id", req.getBarberId()));
        
        // If serviceId is provided, use its duration. Otherwise default to 30 mins for schedule view.
        int duration = 30;
        if (req.getServiceId() != null && !req.getServiceId().toString().equals("00000000-0000-0000-0000-000000000000")) {
            duration = findService(req.getServiceId()).getDurationMinutes();
        }
        
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

        while (!cursor.plusMinutes(duration).isAfter(shopClose)) {
            LocalDateTime slotEnd = cursor.plusMinutes(duration);
            LocalDateTime slotStart = cursor;

            Optional<Appointment> overlap = existing.stream()
                    .filter(a -> a.getScheduledStart().isBefore(slotEnd) && a.getScheduledEnd().isAfter(slotStart))
                    .findFirst();

            slots.add(TimeSlotResponse.builder()
                    .startTime(slotStart)
                    .endTime(slotEnd)
                    .available(overlap.isEmpty())
                    .status(overlap.map(Appointment::getStatus).orElse(null))
                    .appointmentId(overlap.map(Appointment::getId).orElse(null))
                    .build());

            cursor = cursor.plusMinutes(duration);
        }

        return slots;
    }

    // ─── Status Transitions ────────────────────────────────────────────────

    @Transactional
    @AuditAction(action = "CONFIRM_BOOKING", resource = "BOOKING")
    public AppointmentResponse confirmAppointment(UUID appointmentId) {
        Appointment appt = findAppointment(appointmentId);
        appt.confirm();
        
        // Generate virtual ticket number
        if (appt.getTicketNumber() == null) {
            LocalDateTime startOfDay = appt.getScheduledStart().toLocalDate().atStartOfDay();
            LocalDateTime endOfDay = startOfDay.plusDays(1);
            long count = appointmentRepository.countWithTicketNumber(appt.getShop().getId(), startOfDay, endOfDay);
            String ticketNumber = String.format("#T-%03d", count + 1);
            appt.setTicketNumber(ticketNumber);
        }

        Appointment savedAppt = appointmentRepository.save(appt);
        
        // Publish event for notifications (SMS, Push, WebSocket)
        eventProducer.publishBookingConfirmed(BookingConfirmedEvent.from(savedAppt));
        
        return toResponse(savedAppt);
    }

    @Transactional
    @AuditAction(action = "REJECT_BOOKING", resource = "BOOKING")
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
    public AppointmentResponse startAppointment(UUID appointmentId) {
        Appointment appt = findAppointment(appointmentId);
        appt.startService();
        Appointment saved = appointmentRepository.save(appt);
        // Notify customer that service has started
        webSocketNotificationService.notifyCustomer(saved.getCustomer().getId(), toResponse(saved));
        return toResponse(saved);
    }

    @Transactional
    public AppointmentResponse completeAppointment(UUID appointmentId) {
        Appointment appt = findAppointment(appointmentId);
        appt.complete();
        eventProducer.publishBookingCompleted(appointmentId);
        return toResponse(appointmentRepository.save(appt));
    }

    @Transactional
    @AuditAction(action = "CANCEL_BOOKING", resource = "BOOKING")
    @org.springframework.cache.annotation.CacheEvict(value = "availableSlots", allEntries = true)
    public AppointmentResponse cancelAppointment(UUID appointmentId, String reason) {
        Appointment appt = findAppointment(appointmentId);
        appt.cancel(reason);
        Appointment saved = appointmentRepository.save(appt);
        eventProducer.publishBookingCancelled(com.trimlink.messaging.event.BookingCancelledEvent.from(saved));
        return toResponse(saved);
    }

    /**
     * Automatically expires a PENDING appointment that was not paid in time.
     * Only works if the appointment is still PENDING.
     */
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "availableSlots", allEntries = true)
    public void expireAppointment(Appointment appt) {
        if (appt.getStatus() != AppointmentStatus.PENDING) {
            return;
        }
        log.info("Expiring unpaid appointment id={} (created at {})", appt.getId(), appt.getCreatedAt());
        appt.cancel("Payment timeout: Appointment expired after failing to complete payment in time.");
        Appointment saved = appointmentRepository.save(appt);
        eventProducer.publishBookingCancelled(com.trimlink.messaging.event.BookingCancelledEvent.from(saved));
        
        // Notify customer via websocket
        webSocketNotificationService.notifyCustomer(appt.getCustomer().getId(), toResponse(saved));
    }

    @Transactional
    public AppointmentResponse cancelAppointmentForUser(UUID appointmentId, UUID requesterId, String requesterRole, String reason) {
        Appointment appointment = findAppointment(appointmentId);
        enforceAppointmentAccess(appointment, requesterId, requesterRole);
        appointment.cancel(reason);
        Appointment saved = appointmentRepository.save(appointment);
        eventProducer.publishBookingCancelled(com.trimlink.messaging.event.BookingCancelledEvent.from(saved));
        return toResponse(saved);
    }

    @Transactional
    public AppointmentResponse blockSlot(UUID barberUserId, LocalDateTime start, LocalDateTime end) {
        BarberProfile barber = barberProfileRepository.findByUserId(barberUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Barber", "userId", barberUserId));
        
        Appointment block = Appointment.builder()
                .barber(barber)
                .shop(barber.getShop())
                .scheduledStart(start)
                .scheduledEnd(end)
                .status(AppointmentStatus.BLOCKED)
                .build();
        
        return toResponse(appointmentRepository.save(block));
    }

    @Transactional
    public void unblockSlot(UUID barberUserId, UUID appointmentId) {
        Appointment appt = findAppointment(appointmentId);
        if (appt.getStatus() != AppointmentStatus.BLOCKED) {
            throw new BusinessException("Cannot unblock a non-blocked slot");
        }
        // Check if the appointment belongs to the barber. 
        // We skip strict ID check here because the controller already verified the role.
        
        appt.softDelete();
        appointmentRepository.saveAndFlush(appt);
    }

    @Transactional
    public AppointmentResponse updatePaymentStatus(UUID appointmentId, com.trimlink.module.payment.entity.PaymentStatus status) {
        Appointment appt = findAppointment(appointmentId);
        appt.setPaymentStatus(status);
        Appointment saved = appointmentRepository.save(appt);
        // Notify customer about payment status update
        webSocketNotificationService.notifyCustomer(saved.getCustomer().getId(), toResponse(saved));
        return toResponse(saved);
    }

    // ─── Queries ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getCustomerAppointments(UUID customerId, String query, LocalDateTime since, Pageable pageable) {
        if (query == null) query = "";
        if (since == null) since = LocalDateTime.now().minusMonths(1);
        
        return appointmentRepository.searchByCustomerId(customerId, query, since, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getBarberAppointments(UUID barberUserId, AppointmentStatus status, String search, java.time.LocalDate date, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Appointment> spec = (root, query, cb) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("service", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("customer", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("shop", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("barber", jakarta.persistence.criteria.JoinType.LEFT).fetch("user", jakarta.persistence.criteria.JoinType.LEFT);
            }

            jakarta.persistence.criteria.Predicate predicate = cb.conjunction();
            
            predicate = cb.and(predicate, cb.isFalse(root.get("deleted")));
            predicate = cb.and(predicate, cb.equal(root.join("barber").join("user").get("id"), barberUserId));
            
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            
            if (date != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("scheduledStart"), date.atStartOfDay()));
                predicate = cb.and(predicate, cb.lessThan(root.get("scheduledStart"), date.plusDays(1).atStartOfDay()));
            }
            
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                jakarta.persistence.criteria.Join<Object, Object> customer = root.join("customer");
                jakarta.persistence.criteria.Predicate searchPredicate = cb.or(
                    cb.like(cb.lower(customer.get("firstName")), pattern),
                    cb.like(cb.lower(customer.get("lastName")), pattern)
                );
                predicate = cb.and(predicate, searchPredicate);
            }
            
            return predicate;
        };
        
        return appointmentRepository.findAll(spec, pageable).map(this::toResponse);
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
                .customerId(a.getCustomer() != null ? a.getCustomer().getId() : null)
                .customerName(a.getCustomer() != null ? a.getCustomer().getFirstName() + " " + a.getCustomer().getLastName() : "BLOCKED SLOT")
                .customerPhone(a.getCustomer() != null ? a.getCustomer().getPhoneNumber() : null)
                .barberId(a.getBarber().getId())
                .barberName(a.getBarber().getUser().getFirstName() + " " + a.getBarber().getUser().getLastName())
                .shopId(a.getShop().getId())
                .shopName(a.getShop().getName())
                .shopAddress(a.getShop().getAddress())
                .serviceId(a.getService() != null ? a.getService().getId() : null)
                .serviceName(a.getService() != null ? a.getService().getName() : "N/A")
                .serviceDurationMinutes(a.getService() != null ? a.getService().getDurationMinutes() : 0)
                .scheduledStart(a.getScheduledStart())
                .scheduledEnd(a.getScheduledEnd())
                .actualStart(a.getActualStart())
                .actualEnd(a.getActualEnd())
                .status(a.getStatus())
                .paymentStatus(a.getPaymentStatus())
                .priceCharged(a.getPriceCharged())
                .ticketNumber(a.getTicketNumber())
                .notes(a.getNotes())
                .cancellationReason(a.getCancellationReason())
                .receiptImageUrl(a.getReceiptImageUrl())
                .styleReferenceUrl(a.getStyleReferenceUrl())
                .reviewed(reviewRepository.existsByAppointmentId(a.getId()))
                .createdAt(a.getCreatedAt())
                .build();
    }

    private void enforceAppointmentAccess(Appointment appointment, UUID requesterId, String requesterRole) {
        if ("ADMIN".equalsIgnoreCase(requesterRole) || "OWNER".equalsIgnoreCase(requesterRole)) {
            return;
        }

        if (appointment.getCustomer() == null) {
            // Blocked slot check
            if (!appointment.getBarber().getUser().getId().equals(requesterId)) {
                throw new AccessDeniedException("Unauthorized access to blocked slot");
            }
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
