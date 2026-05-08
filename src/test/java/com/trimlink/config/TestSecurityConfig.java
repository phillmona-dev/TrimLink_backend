package com.trimlink.config;

import com.trimlink.security.CustomOAuth2UserService;
import com.trimlink.security.OAuth2AuthenticationFailureHandler;
import com.trimlink.security.OAuth2AuthenticationSuccessHandler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    public CustomOAuth2UserService customOAuth2UserService() {
        return mock(CustomOAuth2UserService.class);
    }

    @Bean
    public OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler() {
        return mock(OAuth2AuthenticationSuccessHandler.class);
    }

    @Bean
    public OAuth2AuthenticationFailureHandler oauth2AuthenticationFailureHandler() {
        return mock(OAuth2AuthenticationFailureHandler.class);
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return mock(ClientRegistrationRepository.class);
    }
}
