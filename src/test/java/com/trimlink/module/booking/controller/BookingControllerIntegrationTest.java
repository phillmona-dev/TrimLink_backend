package com.trimlink.module.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trimlink.common.exception.GlobalExceptionHandler;
import com.trimlink.config.ApiAccessDeniedHandler;
import com.trimlink.config.ApiAuthenticationEntryPoint;
import com.trimlink.config.SecurityConfig;
import com.trimlink.module.booking.dto.AppointmentResponse;
import com.trimlink.module.booking.dto.CreateAppointmentRequest;
import com.trimlink.module.booking.service.BookingService;
import com.trimlink.security.JwtAuthFilter;
import com.trimlink.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@Import({
        SecurityConfig.class,
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
@DisplayName("BookingController Integration Tests")
class BookingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private BookingService bookingService;

    @Test
    @DisplayName("Customer can create appointment")
    void createAppointment_customer_returnsCreated() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID shopId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);

        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setStaffId(staffId);
        request.setShopId(shopId);
        request.setServiceId(serviceId);
        request.setScheduledStart(start);
        request.setNotes("Window seat if possible");

        when(bookingService.createAppointment(eq(customerId), any(CreateAppointmentRequest.class)))
                .thenReturn(AppointmentResponse.builder()
                        .id(appointmentId)
                        .customerId(customerId)
                        .staffId(staffId)
                        .shopId(shopId)
                        .serviceId(serviceId)
                        .scheduledStart(start)
                        .scheduledEnd(start.plusMinutes(45))
                        .priceCharged(new BigDecimal("200.00"))
                        .notes("Window seat if possible")
                        .build());

        mockMvc.perform(post("/bookings")
                        .header("Authorization", bearer(customerId, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.data.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.data.priceCharged").value(200.00));

        verify(bookingService).createAppointment(eq(customerId), any(CreateAppointmentRequest.class));
    }

    @Test
    @DisplayName("Unauthenticated request cannot create appointment")
    void createAppointment_unauthenticated_returnsUnauthorized() throws Exception {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setStaffId(UUID.randomUUID());
        request.setShopId(UUID.randomUUID());
        request.setServiceId(UUID.randomUUID());
        request.setScheduledStart(LocalDateTime.now().plusDays(1));

        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Non-customer role cannot create appointment")
    void createAppointment_staffRole_returnsForbidden() throws Exception {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setStaffId(UUID.randomUUID());
        request.setShopId(UUID.randomUUID());
        request.setServiceId(UUID.randomUUID());
        request.setScheduledStart(LocalDateTime.now().plusDays(1));

        mockMvc.perform(post("/bookings")
                        .header("Authorization", bearer(UUID.randomUUID(), "STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    private String bearer(UUID userId, String role) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(userId, "+251922222222", role);
    }
}
