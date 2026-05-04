package com.trimlink.module.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trimlink.module.auth.dto.SendOtpRequest;
import com.trimlink.module.auth.dto.VerifyOtpRequest;
import com.trimlink.module.user.entity.Role;
import com.trimlink.test.DisabledIfDockerNotAvailable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for the full OTP authentication flow.
 * Uses Testcontainers to spin up real PostgreSQL + Redis instances.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisabledIfDockerNotAvailable
@DisplayName("Auth Integration Tests")
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("trimlink_test")
            .withUsername("test").withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host",     redis::getHost);
        registry.add("spring.data.redis.port",     () -> redis.getMappedPort(6379));
        // Disable Kafka in integration tests
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    private static final String PHONE = "+251912999888";

    @Test
    @DisplayName("Full OTP flow: send → verify → receive JWT tokens")
    void fullOtpAuthFlow() throws Exception {
        // Step 1: Send OTP
        SendOtpRequest sendReq = new SendOtpRequest();
        sendReq.setPhoneNumber(PHONE);

        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(containsString("OTP sent")));

        // Step 2: Extract OTP from Redis (simulates receiving it via SMS)
        String redisKey = "otp:+251912999888";
        String storedValue = redisTemplate.opsForValue().get(redisKey).toString();
        String otp = storedValue.split(":")[0];

        // Step 3: Verify OTP — should auto-register user and return JWT
        VerifyOtpRequest verifyReq = new VerifyOtpRequest();
        verifyReq.setPhoneNumber(PHONE);
        verifyReq.setOtp(otp);
        verifyReq.setFirstName("Integration");
        verifyReq.setLastName("Test");
        verifyReq.setRole(Role.CUSTOMER);

        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.newUser").value(true));
    }

    @Test
    @DisplayName("Returns 401 when OTP is wrong")
    void verifyOtp_wrongCode_returns422() throws Exception {
        SendOtpRequest sendReq = new SendOtpRequest();
        sendReq.setPhoneNumber("+251912888777");

        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk());

        VerifyOtpRequest badOtp = new VerifyOtpRequest();
        badOtp.setPhoneNumber("+251912888777");
        badOtp.setOtp("000000");   // wrong OTP

        mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badOtp)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Invalid OTP")));
    }

    @Test
    @DisplayName("Returns 400 for invalid Ethiopian phone format")
    void sendOtp_invalidPhone_returns400() throws Exception {
        SendOtpRequest badPhone = new SendOtpRequest();
        badPhone.setPhoneNumber("12345");  // not a valid Ethiopian number

        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPhone)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
