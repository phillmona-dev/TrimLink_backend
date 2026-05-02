package com.trimlink.module.payment.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.trimlink.common.exception.PaymentException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapaClientTest {

    private WireMockServer wireMockServer;
    private ChapaClient chapaClient;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();

        chapaClient = new ChapaClient(WebClient.builder());
        ReflectionTestUtils.setField(chapaClient, "baseUrl", wireMockServer.baseUrl());
        ReflectionTestUtils.setField(chapaClient, "secretKey", "test-secret");
        ReflectionTestUtils.setField(chapaClient, "callbackUrl", "https://trimlink.test/callback");
        ReflectionTestUtils.setField(chapaClient, "returnUrl", "https://trimlink.test/return");
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void initiatePaymentShouldSendExpectedRequestAndParseCheckoutUrl() {
        wireMockServer.stubFor(post(urlEqualTo("/transaction/initialize"))
                .willReturn(okJson("""
                        {
                          "status": "success",
                          "message": "Initialized",
                          "data": {
                            "checkout_url": "https://checkout.chapa.test/session-1"
                          }
                        }
                        """)));

        ChapaClient.ChapaInitRequest request = new ChapaClient.ChapaInitRequest();
        request.setAmount("175.00");
        request.setTxRef("TRIM-CHAPA-1");
        request.setPhoneNumber("+251911111111");
        request.setFirstName("Liya");
        request.setLastName("Bekele");
        request.setEmail("liya@trimlink.et");
        request.setCallbackUrl("https://trimlink.test/callback");
        request.setReturnUrl("https://trimlink.test/return");

        ChapaClient.ChapaInitResponse response = chapaClient.initiatePayment(request);

        assertThat(response.getData().getCheckoutUrl()).isEqualTo("https://checkout.chapa.test/session-1");
        wireMockServer.verify(postRequestedFor(urlEqualTo("/transaction/initialize"))
                .withHeader("Authorization", equalTo("Bearer test-secret"))
                .withRequestBody(matchingJsonPath("$.tx_ref", equalTo("TRIM-CHAPA-1")))
                .withRequestBody(matchingJsonPath("$.amount", equalTo("175.00"))));
    }

    @Test
    void verifyTransactionShouldThrowWhenGatewayReturnsError() {
        wireMockServer.stubFor(get(urlEqualTo("/transaction/verify/TRIM-CHAPA-FAIL"))
                .willReturn(aResponse().withStatus(502).withBody("bad gateway")));

        assertThatThrownBy(() -> chapaClient.verifyTransaction("TRIM-CHAPA-FAIL"))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Chapa verification failed");
    }
}
