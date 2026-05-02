package com.trimlink.module.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.trimlink.common.exception.PaymentException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelebirrClientTest {

    private WireMockServer wireMockServer;
    private TelebirrClient telebirrClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();

        objectMapper = new ObjectMapper();
        telebirrClient = new TelebirrClient(WebClient.builder(), objectMapper);
        ReflectionTestUtils.setField(telebirrClient, "baseUrl", wireMockServer.baseUrl());
        ReflectionTestUtils.setField(telebirrClient, "appId", "telebirr-app");
        ReflectionTestUtils.setField(telebirrClient, "appKey", "telebirr-secret");
        ReflectionTestUtils.setField(telebirrClient, "publicKey", "telebirr-secret");
        ReflectionTestUtils.setField(telebirrClient, "notifyUrl", "https://trimlink.test/webhooks/payment/telebirr");
        ReflectionTestUtils.setField(telebirrClient, "returnUrl", "https://trimlink.test/payments/return");
        ReflectionTestUtils.setField(telebirrClient, "shortCode", "1000");
        ReflectionTestUtils.setField(telebirrClient, "initiatePath", "/merchant/v1/payment/preorder");
        ReflectionTestUtils.setField(telebirrClient, "verifyPath", "/merchant/v1/payment/query");
        ReflectionTestUtils.setField(telebirrClient, "signatureAlgorithm", "HMAC-SHA256");
        ReflectionTestUtils.setField(telebirrClient, "timeoutSeconds", 900);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void initiatePaymentShouldParseCheckoutUrlFromProviderResponse() {
        wireMockServer.stubFor(post(urlEqualTo("/merchant/v1/payment/preorder"))
                .willReturn(okJson("""
                        {
                          "status": "SUCCESS",
                          "data": {
                            "to_pay_url": "https://telebirr.test/pay/checkout-1"
                          }
                        }
                        """)));

        TelebirrClient.TelebirrInitResponse response = telebirrClient.initiatePayment(
                TelebirrClient.TelebirrInitRequest.builder()
                        .txRef("TRIM-TB-1")
                        .amount(new BigDecimal("210.00"))
                        .phoneNumber("+251922222222")
                        .title("TrimLink Payment")
                        .description("Barbershop service payment")
                        .build());

        assertThat(response.getCheckoutUrl()).isEqualTo("https://telebirr.test/pay/checkout-1");
        wireMockServer.verify(postRequestedFor(urlEqualTo("/merchant/v1/payment/preorder"))
                .withRequestBody(matchingJsonPath("$.appid", equalTo("telebirr-app")))
                .withRequestBody(matchingJsonPath("$.sign"))
                .withRequestBody(matchingJsonPath("$.biz_content.out_trade_no", equalTo("TRIM-TB-1"))));
    }

    @Test
    void verifyTransactionShouldParseSuccessfulGatewayResult() {
        wireMockServer.stubFor(post(urlEqualTo("/merchant/v1/payment/query"))
                .willReturn(okJson("""
                        {
                          "code": "200",
                          "data": {
                            "out_trade_no": "TRIM-TB-VERIFY",
                            "trade_no": "TB-TXN-1",
                            "total_amount": "210.00",
                            "trans_currency": "ETB",
                            "trade_status": "SUCCESS"
                          }
                        }
                        """)));

        TelebirrClient.TelebirrVerifyResponse response = telebirrClient.verifyTransaction("TRIM-TB-VERIFY");

        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.getTxRef()).isEqualTo("TRIM-TB-VERIFY");
        assertThat(response.getProviderTxId()).isEqualTo("TB-TXN-1");
        assertThat(response.getAmount()).isEqualByComparingTo("210.00");
    }

    @Test
    void parseAndVerifyWebhookShouldAcceptValidSignature() throws Exception {
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", "TRIM-TB-WEBHOOK");
        bizContent.put("trade_no", "TB-TXN-2");
        bizContent.put("trade_status", "SUCCESS");
        bizContent.put("total_amount", "210.00");
        bizContent.put("trans_currency", "ETB");

        String signedContent = objectMapper.writeValueAsString(bizContent);
        String signature = hmacSha256("telebirr-secret", signedContent);
        String payload = """
                {
                  "biz_content": %s,
                  "sign": "%s"
                }
                """.formatted(signedContent, signature);

        TelebirrClient.TelebirrWebhookPayload response = telebirrClient.parseAndVerifyWebhook(payload, "");

        assertThat(response.getTxRef()).isEqualTo("TRIM-TB-WEBHOOK");
        assertThat(response.getProviderTxId()).isEqualTo("TB-TXN-2");
        assertThat(response.isSuccessful()).isTrue();
    }

    @Test
    void parseAndVerifyWebhookShouldRejectInvalidSignature() {
        String payload = """
                {
                  "biz_content": {
                    "out_trade_no": "TRIM-TB-WEBHOOK",
                    "trade_status": "SUCCESS"
                  },
                  "sign": "bad-signature"
                }
                """;

        assertThatThrownBy(() -> telebirrClient.parseAndVerifyWebhook(payload, ""))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Invalid Telebirr webhook signature");
    }

    private String hmacSha256(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
