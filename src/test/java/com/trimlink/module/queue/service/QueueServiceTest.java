package com.trimlink.module.queue.service;

import com.trimlink.common.exception.ConflictException;
import com.trimlink.messaging.producer.EventProducer;
import com.trimlink.module.queue.dto.JoinQueueRequest;
import com.trimlink.module.queue.entity.QueueEntry;
import com.trimlink.module.queue.entity.QueueStatus;
import com.trimlink.module.queue.repository.QueueEntryRepository;
import com.trimlink.module.service.entity.Service;
import com.trimlink.module.service.repository.ServiceRepository;
import com.trimlink.module.shop.entity.BarberShop;
import com.trimlink.module.shop.repository.BarberShopRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueService Unit Tests")
class QueueServiceTest {

    @Mock private QueueEntryRepository    queueEntryRepository;
    @Mock private UserRepository          userRepository;
    @Mock private BarberProfileRepository barberProfileRepository;
    @Mock private BarberShopRepository    barberShopRepository;
    @Mock private ServiceRepository       serviceRepository;
    @Mock private EventProducer           eventProducer;

    @InjectMocks
    private QueueService queueService;

    private UUID customerId, barberId, shopId, serviceId;
    private User customer;
    private BarberProfile barber;
    private BarberShop shop;
    private Service service;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        barberId   = UUID.randomUUID();
        shopId     = UUID.randomUUID();
        serviceId  = UUID.randomUUID();

        customer = User.builder()
                .firstName("Tigist").lastName("Alemu")
                .phoneNumber("+251912000001").role(Role.CUSTOMER).build();

        service = Service.builder()
                .name("Shave").basePrice(new BigDecimal("80.00"))
                .durationMinutes(20).active(true).build();

        shop = BarberShop.builder()
                .name("TrimLink Kazanchis").city("Addis Ababa").active(true).build();

        User barberUser = User.builder()
                .firstName("Yonas").lastName("Tadesse")
                .phoneNumber("+251911000002").role(Role.BARBER).build();

        barber = BarberProfile.builder()
                .user(barberUser).shop(shop).available(true).build();
    }

    // ─── joinQueue ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should join queue successfully and return ticket with position 1")
    void joinQueue_success_firstInLine() {
        JoinQueueRequest req = new JoinQueueRequest();
        req.setBarberId(barberId);
        req.setShopId(shopId);
        req.setServiceId(serviceId);
        req.setClientTimestamp(LocalDateTime.now());

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(barberProfileRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(barberShopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(queueEntryRepository.existsByCustomerIdAndBarberIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);

        QueueEntry saved = QueueEntry.builder()
                .customer(customer).barber(barber).shop(shop).service(service)
                .joinedAt(LocalDateTime.now()).status(QueueStatus.WAITING).build();
        when(queueEntryRepository.save(any())).thenReturn(saved);
        when(queueEntryRepository.findEntriesAheadOf(any(), any())).thenReturn(List.of());
        when(queueEntryRepository.findCurrentEntry(any())).thenReturn(Optional.empty());

        var ticket = queueService.joinQueue(customerId, req);

        assertThat(ticket).isNotNull();
        assertThat(ticket.getStatus()).isEqualTo(QueueStatus.WAITING);
        assertThat(ticket.getPosition()).isEqualTo(1);
        assertThat(ticket.getEstimatedWaitMinutes()).isEqualTo(0); // no one ahead
        verify(eventProducer).publishQueueUpdated(any());
    }

    @Test
    @DisplayName("Should throw ConflictException when customer already in queue")
    void joinQueue_throwsConflict_whenAlreadyInQueue() {
        JoinQueueRequest req = new JoinQueueRequest();
        req.setBarberId(barberId);
        req.setShopId(shopId);
        req.setServiceId(serviceId);

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(barberProfileRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(barberShopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(queueEntryRepository.existsByCustomerIdAndBarberIdAndStatusIn(any(), any(), any()))
                .thenReturn(true); // Already in queue

        assertThatThrownBy(() -> queueService.joinQueue(customerId, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already in this barber's queue");

        verify(queueEntryRepository, never()).save(any());
    }

    // ─── ETA Calculation ───────────────────────────────────────────────────

    @Test
    @DisplayName("ETA should equal sum of service durations of entries ahead")
    void eta_calculatesCorrectly_withTwoAhead() {
        // two customers ahead, each 20-min shave = 40 min total ETA
        Service svc20min = Service.builder().durationMinutes(20).name("Shave").build();

        QueueEntry ahead1 = QueueEntry.builder()
                .customer(customer).barber(barber).shop(shop).service(svc20min)
                .joinedAt(LocalDateTime.now().minusMinutes(10))
                .status(QueueStatus.WAITING).build();
        QueueEntry ahead2 = QueueEntry.builder()
                .customer(customer).barber(barber).shop(shop).service(svc20min)
                .joinedAt(LocalDateTime.now().minusMinutes(5))
                .status(QueueStatus.WAITING).build();

        JoinQueueRequest req = new JoinQueueRequest();
        req.setBarberId(barberId); req.setShopId(shopId); req.setServiceId(serviceId);
        req.setClientTimestamp(LocalDateTime.now());

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(barberProfileRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(barberShopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(queueEntryRepository.existsByCustomerIdAndBarberIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);

        QueueEntry mySaved = QueueEntry.builder()
                .customer(customer).barber(barber).shop(shop).service(service)
                .joinedAt(LocalDateTime.now()).status(QueueStatus.WAITING).build();
        when(queueEntryRepository.save(any())).thenReturn(mySaved);
        when(queueEntryRepository.findEntriesAheadOf(any(), any()))
                .thenReturn(List.of(ahead1, ahead2)); // 2 ahead
        when(queueEntryRepository.findCurrentEntry(any())).thenReturn(Optional.empty());

        var ticket = queueService.joinQueue(customerId, req);
        // 2 × 20 min = 40 min
        assertThat(ticket.getEstimatedWaitMinutes()).isEqualTo(40);
        assertThat(ticket.getPosition()).isEqualTo(3); // 2 ahead + myself
    }

    // ─── Offline Sync ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Should use clientTimestamp when within 30-min drift window")
    void joinQueue_usesClientTimestamp_whenWithinDriftWindow() {
        LocalDateTime clientTs = LocalDateTime.now().minusMinutes(10);

        JoinQueueRequest req = new JoinQueueRequest();
        req.setBarberId(barberId); req.setShopId(shopId); req.setServiceId(serviceId);
        req.setClientTimestamp(clientTs);

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(barberProfileRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(barberShopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(queueEntryRepository.existsByCustomerIdAndBarberIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);

        QueueEntry saved = QueueEntry.builder()
                .customer(customer).barber(barber).shop(shop).service(service)
                .joinedAt(clientTs) // should use clientTimestamp
                .status(QueueStatus.WAITING).build();
        when(queueEntryRepository.save(any())).thenReturn(saved);
        when(queueEntryRepository.findEntriesAheadOf(any(), any())).thenReturn(List.of());
        when(queueEntryRepository.findCurrentEntry(any())).thenReturn(Optional.empty());

        var ticket = queueService.joinQueue(customerId, req);

        // Verify the saved entry uses client timestamp
        verify(queueEntryRepository).save(argThat(entry ->
                entry.getJoinedAt().equals(clientTs)));
    }
}
