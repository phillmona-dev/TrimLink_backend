package com.trimlink.module.auth.service;

import com.trimlink.common.exception.OtpException;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.audit.annotation.AuditAction;
import com.trimlink.module.auth.dto.*;
import com.trimlink.module.shop.entity.BarberShop;
import com.trimlink.module.shop.repository.BarberShopRepository;
import com.trimlink.module.user.entity.ApprovalStatus;
import com.trimlink.module.user.entity.BarberProfile;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.BarberProfileRepository;
import com.trimlink.module.user.repository.UserRepository;
import com.trimlink.module.notification.service.WebSocketNotificationService;
import com.trimlink.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Authentication service — Username and Password flow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BarberShopRepository barberShopRepository;
    private final BarberProfileRepository barberProfileRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final WebSocketNotificationService notificationService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final LoginAttemptService loginAttemptService;
    private final com.trimlink.security.TokenRotationService tokenRotationService;
    private final com.trimlink.common.ratelimit.RateLimiterService rateLimiterService;

    @Value("${trimlink.security.jwt.access-token-expiry:900000}")
    private long accessTokenExpiry;

    @Value("${trimlink.security.jwt.refresh-token-expiry:2592000000}")
    private long refreshTokenExpiry;

    // --- Customer Register ---

    @Transactional
    @AuditAction(action = "USER_REGISTER", resource = "USER")
    public AuthResponse register(RegisterRequest request) {
        String clientIp = com.trimlink.common.utils.RequestUtils.getClientIp();
        rateLimiterService.check("rate:register:" + clientIp, 3, 3600); // 3 per hour

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new OtpException("Username already exists.");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber() != null ? normalizePhone(request.getPhoneNumber()) : null)
                .role(Role.CUSTOMER) // Force customer role
                .approvalStatus(ApprovalStatus.APPROVED) // Auto-approve customers
                .active(true)
                .phoneVerified(false)
                .build();

        user = userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        
        // Register refresh token for rotation
        tokenRotationService.registerToken(user.getId().toString(), jwtTokenProvider.extractTokenId(refreshToken), refreshTokenExpiry);

        log.info("Customer registered: id={}, username={}", user.getId(), user.getUsername());

        return AuthResponse.builder()
                .userId(user.getId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(accessTokenExpiry / 1000)
                .phone(user.getUsername()) // Maintain compatibility
                .role(user.getRole().name())
                .newUser(true)
                .build();
    }

    // --- Shop Register ---

    @Transactional
    @AuditAction(action = "SHOP_REGISTER", resource = "SHOP")
    public AuthResponse registerShop(ShopRegistrationRequest request) {
        String clientIp = com.trimlink.common.utils.RequestUtils.getClientIp();
        rateLimiterService.check("rate:register_shop:" + clientIp, 2, 3600); // 2 per hour

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new OtpException("Username already exists.");
        }

        // 1. Create Shop
        BarberShop shop = BarberShop.builder()
                .name(request.getShopName())
                .city(request.getCity())
                .address(request.getAddress())
                .phone(request.getPhoneNumber() != null ? normalizePhone(request.getPhoneNumber()) : null)
                .description(request.getShopDescription())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .platform(request.getPlatform() != null && request.getPlatform().equalsIgnoreCase("GLOWLINK") ? 
                          com.trimlink.module.shop.entity.ShopPlatform.GLOWLINK : 
                          com.trimlink.module.shop.entity.ShopPlatform.TRIMLINK)
                .active(false) // Shop needs approval too, or relies on owner status
                .build();
        shop = barberShopRepository.save(shop);

        // 2. Create User (Owner)
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber() != null ? normalizePhone(request.getPhoneNumber()) : null)
                .role(Role.OWNER)
                .approvalStatus(ApprovalStatus.PENDING) // Pending admin approval
                .active(false)
                .phoneVerified(false)
                .build();
        user = userRepository.save(user);

        // 3. Create BarberProfile linking User to Shop
        BarberProfile profile = BarberProfile.builder()
                .user(user)
                .shop(shop)
                .available(false)
                .build();
        barberProfileRepository.save(profile);

        log.info("Shop registered (pending approval): id={}, username={}, shopName={}", 
                 user.getId(), user.getUsername(), shop.getName());

        // 4. Notify Admins
        try {
            notificationService.notifyAdmins(Map.of(
                "type", "SHOP_REGISTRATION",
                "id", user.getId().toString(),
                "shopName", shop.getName(),
                "ownerName", user.getFirstName() + " " + user.getLastName(),
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Failed to send websocket notification to admins", e);
        }

        // Do not issue JWT token since login is blocked until approved
        return AuthResponse.builder()
                .userId(user.getId())
                .accessToken(null)
                .refreshToken(null)
                .accessTokenExpiresIn(0L)
                .phone(user.getUsername())
                .role(user.getRole().name())
                .newUser(true)
                .build();
    }

    // --- Complete Shop Registration (for logged in users like Google users) ---

    @Transactional
    @AuditAction(action = "COMPLETE_SHOP_REGISTRATION", resource = "SHOP")
    public AuthResponse completeShopRegistration(UUID userId, CompleteShopRegistrationRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // 1. Create Shop
        BarberShop shop = BarberShop.builder()
                .name(request.getShopName())
                .city(request.getCity())
                .address(request.getAddress())
                .description(request.getShopDescription())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .active(false)
                .build();
        shop = barberShopRepository.save(shop);

        // 2. Update User to OWNER
        user.setRole(Role.OWNER);
        user.setApprovalStatus(ApprovalStatus.PENDING);
        user.setActive(true); // Keep active so they can see their profile and pending status
        user = userRepository.save(user);

        // 3. Create BarberProfile
        BarberProfile profile = BarberProfile.builder()
                .user(user)
                .shop(shop)
                .available(false)
                .build();
        barberProfileRepository.save(profile);

        log.info("User {} upgraded to Owner (pending approval) for shop {}", userId, shop.getName());

        // 4. Notify Admins
        try {
            notificationService.notifyAdmins(Map.of(
                "type", "SHOP_REGISTRATION",
                "id", user.getId().toString(),
                "shopName", shop.getName(),
                "ownerName", user.getFirstName() + " " + user.getLastName(),
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Failed to notify admins about shop completion", e);
        }

        return AuthResponse.builder()
                .userId(user.getId())
                .accessToken(null)
                .refreshToken(null)
                .accessTokenExpiresIn(0L)
                .phone(user.getUsername())
                .role(user.getRole().name())
                .newUser(false)
                .build();
    }

    // --- Login ---

    @Transactional(readOnly = true)
    @AuditAction(action = "LOGIN", resource = "USER")
    public AuthResponse login(LoginRequest request) {
        String clientIp = com.trimlink.common.utils.RequestUtils.getClientIp();
        
        // General Rate Limit per IP
        rateLimiterService.checkApiRequest(clientIp, 50); // 50 per minute

        // Check if IP or Username is blocked
        if (loginAttemptService.isBlocked(clientIp)) {
            throw new OtpException("Too many failed attempts from this IP. Please try again in 15 minutes.");
        }
        if (loginAttemptService.isBlocked(request.getUsername())) {
            throw new OtpException("Too many failed attempts for this account. Please try again in 15 minutes.");
        }

        try {
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new OtpException("Invalid username or password."));

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new OtpException("Invalid username or password.");
            }

            if (user.getApprovalStatus() == ApprovalStatus.PENDING) {
                throw new OtpException("Your account is pending admin approval.");
            }
            
            if (user.getApprovalStatus() == ApprovalStatus.REJECTED) {
                throw new OtpException("Your account registration was rejected.");
            }

            if (!user.isActive() && user.getApprovalStatus() == ApprovalStatus.APPROVED) {
                throw new OtpException("Your account has been deactivated. Contact support.");
            }

            // Check if shop is active for Barbers and Owners
            if ((user.getRole() == Role.BARBER || user.getRole() == Role.OWNER) && 
                user.getBarberProfile() != null && user.getBarberProfile().getShop() != null && 
                !user.getBarberProfile().getShop().isActive()) {
                throw new OtpException("Your shop has been deactivated. Please contact the system admin.");
            }

            // SUCCESS: Reset counters
            loginAttemptService.loginSucceeded(clientIp);
            loginAttemptService.loginSucceeded(request.getUsername());

            String accessToken = jwtTokenProvider.generateAccessToken(
                    user.getId(), user.getUsername(), user.getRole().name());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

            // Register refresh token for rotation
            tokenRotationService.registerToken(user.getId().toString(), jwtTokenProvider.extractTokenId(refreshToken), refreshTokenExpiry);

            log.info("User logged in: id={}, username={}", user.getId(), user.getUsername());

            return AuthResponse.builder()
                    .userId(user.getId())
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .accessTokenExpiresIn(accessTokenExpiry / 1000)
                    .phone(user.getUsername()) 
                    .role(user.getRole().name())
                    .newUser(false)
                    .build();

        } catch (OtpException e) {
            // FAILURE: Increment counters
            loginAttemptService.loginFailed(clientIp);
            loginAttemptService.loginFailed(request.getUsername());
            throw e;
        }
    }

    // --- Token Refresh ---

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String clientIp = com.trimlink.common.utils.RequestUtils.getClientIp();
        rateLimiterService.checkApiRequest(clientIp, 60); // 60 per minute

        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.isRefreshTokenValid(refreshToken)) {
            throw new OtpException("Refresh token is invalid or expired. Please log in again.");
        }

        UUID userId = jwtTokenProvider.extractUserId(refreshToken);
        String jti  = jwtTokenProvider.extractTokenId(refreshToken);

        // ROTATION CHECK
        if (!tokenRotationService.validateAndRotate(userId.toString(), jti)) {
            throw new OtpException("Security breach detected or token expired. Please log in again.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // ... existing checks ...
        if (user.getApprovalStatus() == ApprovalStatus.REJECTED) {
            throw new OtpException("Your account registration was rejected.");
        }

        String newAccessToken  = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // Register the NEW refresh token
        tokenRotationService.registerToken(user.getId().toString(), jwtTokenProvider.extractTokenId(newRefreshToken), refreshTokenExpiry);

        return AuthResponse.builder()
                .userId(user.getId())
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .accessTokenExpiresIn(accessTokenExpiry / 1000)
                .phone(user.getUsername())
                .role(user.getRole().name())
                .newUser(false)
                .build();
    }

    // --- Helpers ---

    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        phone = phone.trim().replaceAll("\\s+", "");
        if (phone.startsWith("09") || phone.startsWith("07")) {
            return "+251" + phone.substring(1);
        }
        return phone;
    }
}
