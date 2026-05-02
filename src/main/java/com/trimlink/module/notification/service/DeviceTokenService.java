package com.trimlink.module.notification.service;

import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.notification.dto.DeviceTokenResponse;
import com.trimlink.module.notification.dto.RegisterDeviceTokenRequest;
import com.trimlink.module.notification.entity.UserDeviceToken;
import com.trimlink.module.notification.repository.UserDeviceTokenRepository;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final UserDeviceTokenRepository userDeviceTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public DeviceTokenResponse register(UUID userId, RegisterDeviceTokenRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        String normalizedToken = request.getToken().trim();
        UserDeviceToken deviceToken = userDeviceTokenRepository.findByTokenAndDeletedFalse(normalizedToken)
                .orElseGet(UserDeviceToken::new);

        deviceToken.setUser(user);
        deviceToken.setToken(normalizedToken);
        deviceToken.setPlatform(request.getPlatform());
        deviceToken.setDeviceId(blankToNull(request.getDeviceId()));
        deviceToken.setAppVersion(blankToNull(request.getAppVersion()));
        deviceToken.setActive(true);
        deviceToken.setDeleted(false);
        deviceToken.setDeletedAt(null);
        deviceToken.setLastSeenAt(LocalDateTime.now());

        return toResponse(userDeviceTokenRepository.save(deviceToken));
    }

    @Transactional(readOnly = true)
    public List<DeviceTokenResponse> listForUser(UUID userId) {
        return userDeviceTokenRepository.findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deactivate(UUID userId, UUID deviceTokenId) {
        UserDeviceToken deviceToken = userDeviceTokenRepository.findByIdAndUserIdAndDeletedFalse(deviceTokenId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Device token", "id", deviceTokenId));
        deviceToken.markInactive();
        userDeviceTokenRepository.save(deviceToken);
    }

    private DeviceTokenResponse toResponse(UserDeviceToken deviceToken) {
        return DeviceTokenResponse.builder()
                .id(deviceToken.getId())
                .platform(deviceToken.getPlatform())
                .deviceId(deviceToken.getDeviceId())
                .appVersion(deviceToken.getAppVersion())
                .active(deviceToken.isActive())
                .lastSeenAt(deviceToken.getLastSeenAt())
                .createdAt(deviceToken.getCreatedAt())
                .build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
