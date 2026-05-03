package com.trimlink.module.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcasts a notification payload to a specific staff's topic.
     * Clients should subscribe to: /topic/staffs/{staffId}/bookings
     */
    public void notifyStaff(UUID staffId, Object payload) {
        String destination = "/topic/staffs/" + staffId + "/bookings";
        log.info("Sending websocket message to {}", destination);
        messagingTemplate.convertAndSend(destination, payload);
    }

    /**
     * Broadcasts a notification payload to a specific customer's topic.
     * Clients should subscribe to: /topic/customers/{customerId}/bookings
     */
    public void notifyCustomer(UUID customerId, Object payload) {
        String destination = "/topic/customers/" + customerId + "/bookings";
        log.info("Sending websocket message to {}", destination);
        messagingTemplate.convertAndSend(destination, payload);
    }
}
