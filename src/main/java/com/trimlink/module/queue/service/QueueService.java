package com.trimlink.module.queue.service;

import com.trimlink.common.exception.BusinessException;
import com.trimlink.common.exception.ConflictException;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.messaging.event.QueueUpdatedEvent;
import com.trimlink.messaging.producer.EventProducer;
import com.trimlink.module.queue.dto.JoinQueueRequest;
import com.trimlink.module.queue.dto.QueueEntryResponse;
import com.trimlink.module.queue.dto.QueueTicketResponse;
import com.trimlink.module.queue.entity.QueueEntry;
import com.trimlink.module.queue.entity.QueueStatus;
import com.trimlink.module.queue.repository.QueueEntryRepository;
import com.trimlink.module.service.entity.Service;
import com.trimlink.module.service.repository.ServiceRepository;
import com.trimlink.module.shop.entity.BarberShop;
import com.trimlink.module.shop.repository.BarberShopRepository;
import com.trimlink.module.user.entity.BarberProfile;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.BarberProfileRepository;
import com.trimlink.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Queue (Waitlist) Service — implements the FIFO walk-in queue.
 *
 * ── FIFO Algorithm ──────────────────────────────────────────────────────────
 *  Ordering key: (barber_profile_id, joined_at ASC)
 *
 *  For offline sync: when a client submits a join with clientTimestamp,
 *  the server uses that timestamp as the ordering key (capped at ±30 min
 *  from server time to prevent abuse). This allows customers who joined
 *  while offline to retain their fair position when connectivity resumes.
 *
 * ── ETA Calculation ─────────────────────────────────────────────────────────
 *  ETA = SUM(durationMinutes of all WAITING entries ahead) + current service remaining
 *
 *  "Remaining" = service.durationMinutes - minutes already elapsed since serviceStartedAt.
 *  This is O(n) where n = queue depth, acceptable (queues rarely exceed 20 entries).
 */
@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class QueueService {

    private static final int OFFLINE_SYNC_MAX_DRIFT_MINUTES = 30;

    private final QueueEntryRepository queueEntryRepository;
    private final UserRepository userRepository;
    private final BarberProfileRepository barberProfileRepository;
    private final BarberShopRepository barberShopRepository;
    private final ServiceRepository serviceRepository;
    private final EventProducer eventProducer;

    // ─── Join Queue ────────────────────────────────────────────────────────

    @Transactional
    public QueueTicketResponse joinQueue(UUID customerId, JoinQueueRequest req) {
        User customer        = findUser(customerId);
        BarberProfile barber = findBarber(req.getBarberId());
        BarberShop shop      = findShop(req.getShopId());
        Service service      = findService(req.getServiceId());

        // Prevent duplicate active entry for same barber
        boolean alreadyInQueue = queueEntryRepository
                .existsByCustomerIdAndBarberIdAndStatusIn(
                        customerId,
                        barber.getId(),
                        List.of(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.IN_SERVICE)
                );
        if (alreadyInQueue) {
            throw new ConflictException("You are already in this barber's queue.");
        }

        // ── Offline sync: resolve effective join timestamp ─────────────────
        LocalDateTime serverNow = LocalDateTime.now();
        LocalDateTime joinedAt  = resolveJoinTimestamp(req.getClientTimestamp(), serverNow);

        QueueEntry entry = QueueEntry.builder()
                .customer(customer)
                .barber(barber)
                .shop(shop)
                .service(service)
                .joinedAt(joinedAt)
                .clientTimestamp(req.getClientTimestamp())
                .notes(req.getNotes())
                .status(QueueStatus.WAITING)
                .build();

        entry = queueEntryRepository.save(entry);

        // Publish event — notifies barber and updates shop dashboard in real-time
        eventProducer.publishQueueUpdated(QueueUpdatedEvent.joined(entry));

        log.info("Customer {} joined queue for barber {} at position TBD, joinedAt={}",
                customerId, barber.getId(), joinedAt);

        return buildTicketResponse(entry);
    }

    // ─── Get My Ticket (real-time position + ETA) ─────────────────────────

    @Transactional(readOnly = true)
    public QueueTicketResponse getMyTicket(UUID requesterId, String requesterRole, UUID entryId) {
        QueueEntry entry = findEntry(entryId);
        enforceQueueEntryAccess(entry, requesterId, requesterRole);
        return buildTicketResponse(entry);
    }

    // ─── Get Full Queue (barber view) ─────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QueueEntryResponse> getQueueForBarber(UUID barberId) {
        List<QueueEntry> queue = queueEntryRepository.findActiveQueueByBarber(barberId);
        return IntStream.range(0, queue.size())
                .mapToObj(i -> toEntryResponse(queue.get(i), i + 1))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QueueEntryResponse> getQueueForShop(UUID shopId) {
        List<QueueEntry> queue = queueEntryRepository.findActiveQueueByShop(shopId);
        return IntStream.range(0, queue.size())
                .mapToObj(i -> toEntryResponse(queue.get(i), i + 1))
                .toList();
    }

    // ─── Queue Advancement ────────────────────────────────────────────────

    /**
     * Barber calls the next customer in line.
     * Moves current WAITING → CALLED and notifies the customer via Kafka event.
     */
    @Transactional
    public QueueTicketResponse callNext(UUID barberId) {
        List<QueueEntry> active = queueEntryRepository.findActiveQueueByBarber(barberId);

        QueueEntry next = active.stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING)
                .findFirst()
                .orElseThrow(() -> new BusinessException("No customers waiting in queue."));

        next.call();
        queueEntryRepository.save(next);
        eventProducer.publishQueueUpdated(QueueUpdatedEvent.called(next));

        log.info("Barber {} called customer {} (entryId={})",
                barberId, next.getCustomer().getId(), next.getId());

        return buildTicketResponse(next);
    }

    /**
     * Marks current entry as IN_SERVICE (barber starts cutting).
     */
    @Transactional
    public QueueTicketResponse startService(UUID entryId) {
        QueueEntry entry = findEntry(entryId);
        entry.startService();
        queueEntryRepository.save(entry);
        eventProducer.publishQueueUpdated(QueueUpdatedEvent.serviceStarted(entry));
        return buildTicketResponse(entry);
    }

    /**
     * Completes the current entry. Advances the queue.
     * Next WAITING customer is notified they are up.
     */
    @Transactional
    public QueueTicketResponse completeService(UUID entryId) {
        QueueEntry entry = findEntry(entryId);
        entry.complete();
        queueEntryRepository.save(entry);

        // Auto-call next
        List<QueueEntry> remaining = queueEntryRepository
                .findActiveQueueByBarber(entry.getBarber().getId());
        remaining.stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING)
                .findFirst()
                .ifPresent(next -> {
                    next.call();
                    queueEntryRepository.save(next);
                    eventProducer.publishQueueUpdated(QueueUpdatedEvent.called(next));
                });

        eventProducer.publishQueueUpdated(QueueUpdatedEvent.completed(entry));
        log.info("Queue entry {} completed. Queue advanced.", entryId);
        return buildTicketResponse(entry);
    }

    /**
     * Customer or barber cancels a queue entry.
     */
    @Transactional
    public void cancelEntry(UUID requesterId, String requesterRole, UUID entryId) {
        QueueEntry entry = findEntry(entryId);
        enforceQueueEntryAccess(entry, requesterId, requesterRole);
        if (entry.getStatus() == QueueStatus.COMPLETED ||
            entry.getStatus() == QueueStatus.CANCELLED) {
            throw new BusinessException("This queue entry is already " + entry.getStatus());
        }
        entry.cancel();
        queueEntryRepository.save(entry);
        eventProducer.publishQueueUpdated(QueueUpdatedEvent.cancelled(entry));
    }

    /**
     * Skip a customer who did not respond when called.
     */
    @Transactional
    public void skipEntry(UUID entryId) {
        QueueEntry entry = findEntry(entryId);
        entry.skip();
        queueEntryRepository.save(entry);
    }

    // ─── ETA Calculation Algorithm ─────────────────────────────────────────

    /**
     * Computes estimated wait minutes for a given queue entry.
     *
     * Formula:
     *   remaining_for_current  = service.durationMinutes - elapsed_since_service_start
     *                            (0 if nobody is IN_SERVICE)
     *   wait_from_ahead        = SUM(entry.service.durationMinutes for each WAITING ahead)
     *   total_wait             = remaining_for_current + wait_from_ahead
     */
    private int calculateEtaMinutes(QueueEntry myEntry) {
        UUID barberId = myEntry.getBarber().getId();

        // Time remaining for whomever is currently IN_SERVICE
        int remainingForCurrent = queueEntryRepository.findCurrentEntry(barberId)
                .map(current -> {
                    if (current.getServiceStartedAt() == null) {
                        return current.getService().getDurationMinutes();
                    }
                    long elapsed = ChronoUnit.MINUTES.between(
                            current.getServiceStartedAt(), LocalDateTime.now());
                    int remaining = current.getService().getDurationMinutes() - (int) elapsed;
                    return Math.max(remaining, 0);
                })
                .orElse(0);

        // Sum of durations for all WAITING entries that joined BEFORE me
        List<QueueEntry> ahead = queueEntryRepository.findEntriesAheadOf(
                barberId, myEntry.getJoinedAt());
        int waitFromAhead = ahead.stream()
                .mapToInt(e -> e.getService().getDurationMinutes())
                .sum();

        return remainingForCurrent + waitFromAhead;
    }

    // ─── Offline Sync Resolution ───────────────────────────────────────────

    /**
     * Resolves the effective join timestamp for FIFO ordering.
     *
     * If client provided a timestamp within the allowed drift window,
     * use it directly — ensuring offline users retain their correct position.
     * Otherwise fall back to server time.
     */
    private LocalDateTime resolveJoinTimestamp(LocalDateTime clientTs, LocalDateTime serverNow) {
        if (clientTs == null) return serverNow;
        long driftMinutes = Math.abs(ChronoUnit.MINUTES.between(clientTs, serverNow));
        if (driftMinutes <= OFFLINE_SYNC_MAX_DRIFT_MINUTES) {
            return clientTs;
        }
        log.warn("Client timestamp drift {}min exceeds {}min limit. Using server time.",
                driftMinutes, OFFLINE_SYNC_MAX_DRIFT_MINUTES);
        return serverNow;
    }

    // ─── Mappers ───────────────────────────────────────────────────────────

    private QueueTicketResponse buildTicketResponse(QueueEntry entry) {
        int eta      = calculateEtaMinutes(entry);
        int position = computePosition(entry);

        return QueueTicketResponse.builder()
                .entryId(entry.getId())
                .customerId(entry.getCustomer().getId())
                .customerName(entry.getCustomer().getFirstName()
                        + " " + entry.getCustomer().getLastName())
                .customerPhone(entry.getCustomer().getPhoneNumber())
                .barberId(entry.getBarber().getId())
                .barberName(entry.getBarber().getUser().getFirstName()
                        + " " + entry.getBarber().getUser().getLastName())
                .shopId(entry.getShop().getId())
                .shopName(entry.getShop().getName())
                .serviceId(entry.getService().getId())
                .serviceName(entry.getService().getName())
                .serviceDurationMinutes(entry.getService().getDurationMinutes())
                .status(entry.getStatus())
                .position(position)
                .estimatedWaitMinutes(eta)
                .joinedAt(entry.getJoinedAt())
                .calledAt(entry.getCalledAt())
                .serviceStartedAt(entry.getServiceStartedAt())
                .build();
    }

    private int computePosition(QueueEntry entry) {
        if (entry.getStatus() != QueueStatus.WAITING) return 0;
        return (int) queueEntryRepository
                .findEntriesAheadOf(entry.getBarber().getId(), entry.getJoinedAt())
                .stream().count() + 1;
    }

    private QueueEntryResponse toEntryResponse(QueueEntry e, int position) {
        long waited = ChronoUnit.MINUTES.between(e.getJoinedAt(), LocalDateTime.now());
        return QueueEntryResponse.builder()
                .entryId(e.getId())
                .customerName(e.getCustomer().getFirstName() + " " + e.getCustomer().getLastName())
                .customerPhone(e.getCustomer().getPhoneNumber())
                .serviceName(e.getService().getName())
                .durationMinutes(e.getService().getDurationMinutes())
                .status(e.getStatus())
                .position(position)
                .joinedAt(e.getJoinedAt())
                .waitedMinutes((int) waited)
                .build();
    }

    // ─── Lookups ───────────────────────────────────────────────────────────

    private QueueEntry findEntry(UUID id) {
        return queueEntryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QueueEntry", "id", id));
    }

    private void enforceQueueEntryAccess(QueueEntry entry, UUID requesterId, String requesterRole) {
        if ("ADMIN".equalsIgnoreCase(requesterRole) || "OWNER".equalsIgnoreCase(requesterRole)) {
            return;
        }

        boolean isCustomer = entry.getCustomer().getId().equals(requesterId);
        boolean isAssignedBarber = entry.getBarber().getUser().getId().equals(requesterId);
        if (!isCustomer && !isAssignedBarber) {
            throw new AccessDeniedException("You are not allowed to access this queue entry.");
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
}
