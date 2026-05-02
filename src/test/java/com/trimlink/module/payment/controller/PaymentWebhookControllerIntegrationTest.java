package com.trimlink.module.payment.controller;

import com.trimlink.common.exception.GlobalExceptionHandler;
import com.trimlink.config.ApiAccessDeniedHandler;
import com.trimlink.config.ApiAuthenticationEntryPoint;
import com.trimlink.config.SecurityConfig;
import com.trimlink.module.payment.service.PaymentService;
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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentWebhookController.class)
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
@DisplayName("PaymentWebhookController Integration Tests")
class PaymentWebhookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Test
    @DisplayName("Chapa webhook is public and forwards payload plus signature")
    void handleChapaWebhook_public_returnsOk() throws Exception {
        String rawPayload = "{\"tx_ref\":\"TRIM-CHAPA-1\"}";

        mockMvc.perform(post("/webhooks/payment/chapa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawPayload)
                        .header("X-Chapa-Signature", "sig-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(paymentService).handleChapaWebhook(rawPayload, "sig-1");
    }

    @Test
    @DisplayName("Telebirr webhook is public and always returns OK even on processing failure")
    void handleTelebirrWebhook_failureStillReturnsOk() throws Exception {
        String rawPayload = "{\"biz_content\":{\"out_trade_no\":\"TRIM-TB-1\"}}";
        doThrow(new RuntimeException("boom")).when(paymentService)
                .handleTelebirrWebhook(rawPayload, "sig-2");

        mockMvc.perform(post("/webhooks/payment/telebirr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawPayload)
                        .header("X-Telebirr-Signature", "sig-2"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(paymentService).handleTelebirrWebhook(rawPayload, "sig-2");
    }
}
