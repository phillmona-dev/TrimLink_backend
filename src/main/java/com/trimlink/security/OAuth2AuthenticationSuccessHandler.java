package com.trimlink.security;

import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Value("${trimlink.security.oauth2.redirect-uri}")
    private String authorizedRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        if (response.isCommitted()) {
            log.debug("Response has already been committed. Unable to redirect to " + authorizedRedirectUri);
            return;
        }

        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        
        Optional<User> userOptional = userRepository.findById(oAuth2User.getId());
        if (userOptional.isEmpty()) {
            log.error("User not found in database after OAuth2 authentication: {}", oAuth2User.getId());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "User not found");
            return;
        }

        User user = userOptional.get();

        String accessToken = tokenProvider.generateAccessToken(
                user.getId(), 
                user.getPhoneNumber() != null ? user.getPhoneNumber() : user.getEmail(), 
                user.getRole().name()
        );
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        String targetUrl = UriComponentsBuilder.fromUriString(authorizedRedirectUri)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
