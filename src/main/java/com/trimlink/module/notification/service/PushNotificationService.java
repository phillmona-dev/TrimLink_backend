package com.trimlink.module.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.trimlink.module.notification.dto.PushMessage;
import com.trimlink.module.notification.entity.UserDeviceToken;
import com.trimlink.module.notification.repository.UserDeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private static final int MAX_BATCH_SIZE = 500;
    private static final Set<MessagingErrorCode> INVALID_TOKEN_ERRORS = Set.of(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT
    );

    private final UserDeviceTokenRepository userDeviceTokenRepository;
    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    @Value("${trimlink.notification.push.enabled:false}")
    private boolean pushEnabled;

    public void sendToUser(UUID userId, PushMessage message) {
        if (!pushEnabled) {
            return;
        }

        FirebaseMessaging firebaseMessaging = firebaseMessagingProvider.getIfAvailable();
        if (firebaseMessaging == null) {
            log.warn("Push notification skipped because Firebase messaging is not configured.");
            return;
        }

        List<UserDeviceToken> deviceTokens = userDeviceTokenRepository.findByUserIdAndActiveTrueAndDeletedFalse(userId);
        if (deviceTokens.isEmpty()) {
            return;
        }

        for (int start = 0; start < deviceTokens.size(); start += MAX_BATCH_SIZE) {
            List<UserDeviceToken> batch = deviceTokens.subList(start, Math.min(start + MAX_BATCH_SIZE, deviceTokens.size()));
            deliverBatch(firebaseMessaging, batch, message, userId);
        }
    }

    private void deliverBatch(FirebaseMessaging firebaseMessaging,
                              List<UserDeviceToken> batch,
                              PushMessage message,
                              UUID userId) {
        List<String> invalidTokens = new ArrayList<>();

        for (UserDeviceToken deviceToken : batch) {
            try {
                Message firebaseMessage = Message.builder()
                        .setToken(deviceToken.getToken())
                        .setNotification(Notification.builder()
                                .setTitle(truncate(message.getTitle(), 80))
                                .setBody(truncate(message.getBody(), 180))
                                .build())
                        .putAllData(message.getData())
                        .build();

                String messageId = firebaseMessaging.send(firebaseMessage);
                log.debug("Push sent to user {} device {} messageId={}", userId, deviceToken.getId(), messageId);
            } catch (FirebaseMessagingException ex) {
                if (isInvalidToken(ex)) {
                    invalidTokens.add(deviceToken.getToken());
                }
                log.warn("Push delivery failed for user {} device {}: {}", userId, deviceToken.getId(), ex.getMessage());
            }
        }

        if (!invalidTokens.isEmpty()) {
            deactivateInvalidTokens(invalidTokens);
        }
    }

    private boolean isInvalidToken(FirebaseMessagingException exception) {
        return exception.getMessagingErrorCode() != null
                && INVALID_TOKEN_ERRORS.contains(exception.getMessagingErrorCode());
    }

    @Transactional
    protected void deactivateInvalidTokens(List<String> invalidTokens) {
        for (String invalidToken : invalidTokens) {
            userDeviceTokenRepository.findByTokenAndDeletedFalse(invalidToken).ifPresent(deviceToken -> {
                deviceToken.markInactive();
                userDeviceTokenRepository.save(deviceToken);
            });
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
