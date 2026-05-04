package com.trimlink.module.booking.service;

import com.trimlink.common.exception.ConflictException;
import com.trimlink.messaging.producer.EventProducer;
import com.trimlink.module.booking.dto.CreateAppointmentRequest;
import com.trimlink.module.booking.dto.SlotAvailabilityRequest;
import com.trimlink.module.booking.entity.Appointment;
import com.trimlink.module.booking.entity.AppointmentStatus;
import com.trimlink.module.booking.repository.AppointmentRepository;
import com.trimlink.module.service.entity.Service;
import com.trimlink.module.service.repository.ServiceRepository;
import com.trimlink.module.shop.entity.BarberShop;
import com.trimlink.module.shop.entity.WorkingHours;
import com.trimlink.module.shop.repository.BarberShopRepository;
import com.trimlink.module.shop.repository.DailyWorkLogRepository;
import com.trimlink.module.shop.repository.WorkingHoursRepository;
import com.trimlink.module.notification.service.WebSocketNotificationService;
import com.trimlink.module.booking.repository.ReviewRepository;
import com.trimlink.module.user.entity.BarberProfile;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.BarberProfileRepository;
import com.trimlink.module.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService Unit Tests")
class BookingServiceTest {

    @Mock private AppointmentRepository  appointmentRepository;
    @Mock private UserRepository         userRepository;
    @Mock private BarberProfileRepository barberProfileRepository;
    @Mock private BarberShopRepository   barberShopRepository;
    @Mock private ServiceRepository      serviceRepository;
    @Mock private WorkingHoursRepository workingHoursRepository;
    @Mock private DailyWorkLogRepository dailyWorkLogRepository;
    @Mock private WebSocketNotificationService webSocketNotificationService;
    @Mock private EventProducer          eventProducer;
    @Mock private ReviewRepository       reviewRepository;

    @InjectMocks
    private BookingService bookingService;

    private UUID customerId, barberId, shopId, serviceId;
    private User customer;
    private BarberProfile barber;
    private BarberShop shop;
    private Service service;
    private WorkingHours workingHours;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        barberId   = UUID.randomUUID();
        shopId     = UUID.randomUUID();
        serviceId  = UUID.randomUUID();

        customer = User.builder()
                .firstName("Abebe").lastName("Kebede")
                .phoneNumber("+251912345678")
                .role(Role.CUSTOMER).active(true).build();

        service = Service.builder()
                .name("Haircut").basePrice(new BigDecimal("150.00"))
                .durationMinutes(30).active(true).build();

        shop = BarberShop.builder().name("TrimLink Bole")
                .address("Bole, Addis Ababa").city("Addis Ababa").active(true).build();

        User barberUser = User.builder()
                .firstName("Dawit").lastName("Haile")
                .phoneNumber("+251911111111").role(Role.BARBER).build();

        barber = BarberProfile.builder()
                .user(barberUser).shop(shop)
                .available(true).serviceAssignments(new ArrayList<>()).build();

        // Wednesday shop is open 09:00 – 18:00
        workingHours = WorkingHours.builder()
                .shop(shop).dayOfWeek(DayOfWeek.WEDNESDAY)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .closed(false).build();
    }

    // ─── createAppointment ─────────────────────────────────────────────────

    @Test
    @DisplayName("Should create appointment when slot is available")
    void createAppointment_success() {
        // Arrange
        CreateAppointmentRequest req = new CreateAppointmentRequest();
        req.setBarberId(barberId);
        req.setShopId(shopId);
        req.setServiceId(serviceId);
        // Next Wednesday at 10:00 AM
        LocalDateTime wednesday10am = LocalDate.now()
                .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.WEDNESDAY))
                .atTime(10, 0);
        req.setScheduledStart(wednesday10am);

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(barberProfileRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(barberShopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(workingHoursRepository.findByShopIdAndDayOfWeek(any(), eq(DayOfWeek.WEDNESDAY)))
                .thenReturn(Optional.of(workingHours));
        when(appointmentRepository.findOverlapping(any(), any(), any()))
                .thenReturn(List.of()); // No overlaps

        Appointment saved = Appointment.builder()
                .customer(customer).barber(barber).shop(shop).service(service)
                .scheduledStart(wednesday10am)
                .scheduledEnd(wednesday10am.plusMinutes(30))
                .status(AppointmentStatus.PENDING)
                .priceCharged(service.getBasePrice()).build();
        when(appointmentRepository.save(any())).thenReturn(saved);

        // Act
        var response = bookingService.createAppointment(customerId, req);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(response.getPriceCharged()).isEqualByComparingTo("150.00");
        verify(eventProducer, times(1)).publishBookingCreated(any());
        verify(appointmentRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should throw ConflictException when slot is already booked")
    void createAppointment_conflict() {
        // Arrange
        CreateAppointmentRequest req = new CreateAppointmentRequest();
        req.setBarberId(barberId);
        req.setShopId(shopId);
        req.setServiceId(serviceId);
        req.setScheduledStart(LocalDateTime.now().plusDays(1).with(LocalTime.of(10, 0)));

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(barberProfileRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(barberShopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(workingHoursRepository.findByShopIdAndDayOfWeek(any(), any()))
                .thenReturn(Optional.of(workingHours));
        // Simulate an existing overlapping appointment
        when(appointmentRepository.findOverlapping(any(), any(), any()))
                .thenReturn(List.of(mock(Appointment.class)));

        // Act & Assert
        assertThatThrownBy(() -> bookingService.createAppointment(customerId, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already booked");

        verify(appointmentRepository, never()).save(any());
        verify(eventProducer, never()).publishBookingCreated(any());
    }

    // ─── getAvailableSlots ─────────────────────────────────────────────────

    @Test
    @DisplayName("Should generate 18 slots for a 30-min service in 9-hour window")
    void getAvailableSlots_generatesCorrectCount() {
        // Arrange: shop open 09:00-18:00, service 30 min → 18 slots
        SlotAvailabilityRequest req = new SlotAvailabilityRequest();
        req.setBarberId(barberId);
        req.setServiceId(serviceId);
        req.setDate(LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.WEDNESDAY)));

        when(barberProfileRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(appointmentRepository.findBarberDaySchedule(any(), any(), any()))
                .thenReturn(List.of()); // No existing bookings
        when(workingHoursRepository.findByShopIdAndDayOfWeek(any(), eq(DayOfWeek.WEDNESDAY)))
                .thenReturn(Optional.of(workingHours));

        // Act
        var slots = bookingService.getAvailableSlots(req);

        // Assert: 9 hours × 2 slots/hour = 18 slots, all available
        assertThat(slots).hasSize(18);
        assertThat(slots).allMatch(s -> s.isAvailable());
    }

    @Test
    @DisplayName("Should mark slot as unavailable when appointment overlaps")
    void getAvailableSlots_marksUnavailableCorrectly() {
        LocalDate nextWed = LocalDate.now()
                .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.WEDNESDAY));

        SlotAvailabilityRequest req = new SlotAvailabilityRequest();
        req.setBarberId(barberId);
        req.setServiceId(serviceId);
        req.setDate(nextWed);

        // Existing appointment at 10:00–10:30
        Appointment existing = mock(Appointment.class);
        lenient().when(existing.getScheduledStart()).thenReturn(nextWed.atTime(10, 0));
        lenient().when(existing.getScheduledEnd()).thenReturn(nextWed.atTime(10, 30));
        lenient().when(existing.getStatus()).thenReturn(AppointmentStatus.CONFIRMED);

        when(barberProfileRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(appointmentRepository.findBarberDaySchedule(any(), any(), any()))
                .thenReturn(List.of(existing));
        when(workingHoursRepository.findByShopIdAndDayOfWeek(any(), eq(DayOfWeek.WEDNESDAY)))
                .thenReturn(Optional.of(workingHours));

        var slots = bookingService.getAvailableSlots(req);

        // Slot at 10:00 should be unavailable
        var tenAmSlot = slots.stream()
                .filter(s -> s.getStart().toLocalTime().equals(LocalTime.of(10, 0)))
                .findFirst().orElseThrow();
        assertThat(tenAmSlot.isAvailable()).isFalse();

        // Slot at 09:00 should still be available
        var nineAmSlot = slots.stream()
                .filter(s -> s.getStart().toLocalTime().equals(LocalTime.of(9, 0)))
                .findFirst().orElseThrow();
        assertThat(nineAmSlot.isAvailable()).isTrue();
    }
}
