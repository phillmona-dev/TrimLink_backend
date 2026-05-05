package com.trimlink.security;

import com.trimlink.module.user.entity.ApprovalStatus;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest oAuth2UserRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(oAuth2UserRequest);

        try {
            return processOAuth2User(oAuth2UserRequest, oAuth2User);
        } catch (Exception ex) {
            log.error("Error processing OAuth2 user", ex);
            throw new OAuth2AuthenticationException(ex.getMessage());
        }
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest oAuth2UserRequest, OAuth2User oAuth2User) {
        String provider = oAuth2UserRequest.getClientRegistration().getRegistrationId();
        
        // Extract provider-specific attributes
        String providerId;
        String picture = null;
        
        if (provider.equalsIgnoreCase("facebook")) {
            providerId = oAuth2User.getAttribute("id");
            // Facebook picture is nested: picture -> data -> url
            Object pictureAttr = oAuth2User.getAttribute("picture");
            if (pictureAttr instanceof java.util.Map) {
                java.util.Map<String, Object> pictureMap = (java.util.Map<String, Object>) pictureAttr;
                java.util.Map<String, Object> data = (java.util.Map<String, Object>) pictureMap.get("data");
                if (data != null) {
                    picture = (String) data.get("url");
                }
            }
        } else {
            // Default (Google)
            providerId = oAuth2User.getAttribute("sub");
            picture = oAuth2User.getAttribute("picture");
        }

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        if (!StringUtils.hasText(email)) {
            throw new RuntimeException("Email not found from OAuth2 provider");
        }

        Optional<User> userOptional = userRepository.findByProviderAndProviderId(provider, providerId);
        User user;

        if (userOptional.isPresent()) {
            user = userOptional.get();
            user = updateExistingUser(user, name, picture);
        } else {
            // Check if user exists with the same email
            Optional<User> userByEmail = userRepository.findByEmail(email);
            if (userByEmail.isPresent()) {
                user = userByEmail.get();
                user.setProvider(provider);
                user.setProviderId(providerId);
                user = updateExistingUser(user, name, picture);
            } else {
                user = registerNewUser(provider, providerId, email, name, picture);
            }
        }

        return CustomOAuth2User.create(user, oAuth2User.getAttributes());
    }

    private User registerNewUser(String provider, String providerId, String email, String name, String picture) {
        String[] nameParts = name.split(" ", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : "";

        User user = User.builder()
                .username(email) // Use email as username for OAuth users
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .avatarUrl(picture)
                .provider(provider)
                .providerId(providerId)
                .role(Role.CUSTOMER)
                .approvalStatus(ApprovalStatus.APPROVED)
                .active(true)
                .phoneVerified(false)
                .build();

        return userRepository.save(user);
    }

    private User updateExistingUser(User user, String name, String picture) {
        String[] nameParts = name.split(" ", 2);
        user.setFirstName(nameParts[0]);
        if (nameParts.length > 1) {
            user.setLastName(nameParts[1]);
        }
        user.setAvatarUrl(picture);
        return userRepository.save(user);
    }
}
