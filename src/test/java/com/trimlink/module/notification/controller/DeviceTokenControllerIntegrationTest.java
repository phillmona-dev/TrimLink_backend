package com.trimlink.module.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trimlink.common.exception.GlobalExceptionHandler;
import com.trimlink.config.ApiAccessDeniedHandler;
import com.trimlink.config.ApiAuthenticationEntryPoint;
import com.trimlink.config.SecurityConfig;
import com.trimlink.module.notification.dto.DeviceTokenResponse;
import com.trimlink.module.notification.dto.RegisterDeviceTokenRequest;
import com.trimlink.module.notification.entity.DevicePlatform;
import com.trimlink.module.notification.service.DeviceTokenService;
import com.trimlink.security.JwtAuthFilter;
import com.trimlink.security.JwtTokenProvider;
import com.trimlink.security.CustomOAuth2UserService;
import com.trimlink.security.OAuth2AuthenticationSuccessHandler;
import com.trimlink.security.OAuth2AuthenticationFailureHandler;
import com.trimlink.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceTokenController.class)
@ActiveProfiles("test")
@Import({
        SecurityConfig.class,
        TestSecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        JwtAuthFilter.class,
        JwtTokenProvider.class,
        GlobalExceptionHandler.class
})
@TestPropertySource(properties = {
        "trimlink.security.jwt.secret=dGVzdHNlY3JldGtleWZvcnRlc3Rpbmdvbmx5ZG9ub3R1c2VpbnByb2R1Y3Rpb24x",
        "trimlink.security.jwt.access-token-expiry=900000",
        "trimlink.security.jwt.refresh-token-expiry=604800000"
})
@DisplayName("DeviceTokenController Integration Tests")
class DeviceTokenControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private DeviceTokenService deviceTokenService;

    @Test
    @DisplayName("Authenticated customer can register device token")
    void register_authenticated_returnsCreated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        RegisterDeviceTokenRequest request = new RegisterDeviceTokenRequest();
        request.setToken("device-token-1");
        request.setPlatform(DevicePlatform.ANDROID);
        request.setDeviceId("pixel-7");
        request.setAppVersion("1.0.0");

        when(deviceTokenService.register(eq(userId), any(RegisterDeviceTokenRequest.class)))
                .thenReturn(DeviceTokenResponse.builder()
                        .id(tokenId)
                        .platform(DevicePlatform.ANDROID)
                        .deviceId("pixel-7")
                        .appVersion("1.0.0")
                        .active(true)
                        .lastSeenAt(LocalDateTime.of(2026, 5, 1, 9, 0))
                        .createdAt(LocalDateTime.of(2026, 5, 1, 9, 0))
                        .build());

        mockMvc.perform(post("/notifications/devices")
                        .header("Authorization", bearer(userId, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(tokenId.toString()))
                .andExpect(jsonPath("$.data.platform").value("ANDROID"))
                .andExpect(jsonPath("$.data.active").value(true));

        verify(deviceTokenService).register(eq(userId), any(RegisterDeviceTokenRequest.class));
    }

    @Test
    @DisplayName("Unauthenticated user cannot list device tokens")
    void list_unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/notifications/devices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Authenticated user can list their registered devices")
    void list_authenticated_returnsDeviceList() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        when(deviceTokenService.listForUser(userId)).thenReturn(List.of(
                DeviceTokenResponse.builder()
                        .id(tokenId)
                        .platform(DevicePlatform.IOS)
                        .deviceId("iphone-15")
                        .appVersion("2.0.1")
                        .active(true)
                        .lastSeenAt(LocalDateTime.of(2026, 5, 1, 11, 0))
                        .createdAt(LocalDateTime.of(2026, 5, 1, 10, 30))
                        .build()
        ));

        mockMvc.perform(get("/notifications/devices")
                        .header("Authorization", bearer(userId, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(tokenId.toString()))
                .andExpect(jsonPath("$.data[0].platform").value("IOS"));
    }

    @Test
    @DisplayName("Authenticated user can deactivate a device token")
    void deactivate_authenticated_returnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        mockMvc.perform(delete("/notifications/devices/{id}", tokenId)
                        .header("Authorization", bearer(userId, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Device token deactivated"));

        verify(deviceTokenService).deactivate(userId, tokenId);
    }

    private String bearer(UUID userId, String role) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(userId, "+251911111111", role);
    }
}
