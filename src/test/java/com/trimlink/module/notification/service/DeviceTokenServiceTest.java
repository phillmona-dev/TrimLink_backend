package com.trimlink.module.notification.service;

import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.notification.dto.RegisterDeviceTokenRequest;
import com.trimlink.module.notification.entity.DevicePlatform;
import com.trimlink.module.notification.entity.UserDeviceToken;
import com.trimlink.module.notification.repository.UserDeviceTokenRepository;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private UserDeviceTokenRepository userDeviceTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DeviceTokenService deviceTokenService;

    @Test
    void registerShouldCreateOrRefreshDeviceToken() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .phoneNumber("+251911111111")
                .firstName("Abel")
                .lastName("Kebede")
                .role(Role.CUSTOMER)
                .build();
        user.setId(userId);

        RegisterDeviceTokenRequest request = new RegisterDeviceTokenRequest();
        request.setToken(" token-value ");
        request.setPlatform(DevicePlatform.ANDROID);
        request.setDeviceId("pixel-7");
        request.setAppVersion("1.0.0");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userDeviceTokenRepository.findByTokenAndDeletedFalse("token-value")).thenReturn(Optional.empty());
        when(userDeviceTokenRepository.save(any(UserDeviceToken.class))).thenAnswer(invocation -> {
            UserDeviceToken saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });

        var response = deviceTokenService.register(userId, request);

        ArgumentCaptor<UserDeviceToken> captor = ArgumentCaptor.forClass(UserDeviceToken.class);
        verify(userDeviceTokenRepository).save(captor.capture());
        UserDeviceToken saved = captor.getValue();

        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getToken()).isEqualTo("token-value");
        assertThat(saved.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getLastSeenAt()).isNotNull();
        assertThat(response.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void deactivateShouldRejectUnknownDeviceForUser() {
        UUID userId = UUID.randomUUID();
        UUID deviceTokenId = UUID.randomUUID();
        when(userDeviceTokenRepository.findByIdAndUserIdAndDeletedFalse(deviceTokenId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceTokenService.deactivate(userId, deviceTokenId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Device token");
    }
}
