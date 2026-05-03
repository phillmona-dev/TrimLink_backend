package com.trimlink.module.auth.service;

import com.trimlink.common.exception.OtpException;
import com.trimlink.common.exception.ResourceNotFoundException;
import com.trimlink.module.auth.dto.*;
import com.trimlink.module.shop.entity.StaffShop;
import com.trimlink.module.shop.repository.StaffShopRepository;
import com.trimlink.module.user.entity.ApprovalStatus;
import com.trimlink.module.user.entity.StaffProfile;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.StaffProfileRepository;
import com.trimlink.module.user.repository.UserRepository;
import com.trimlink.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Authentication service — Username and Password flow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final StaffShopRepository staffShopRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${trimlink.security.jwt.access-token-expiry:900000}")
    private long accessTokenExpiry;

    // ─── Customer Register ──────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest request) {
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

    // ─── Shop Register ──────────────────────────────────────────────────────

    @Transactional
    public AuthResponse registerShop(ShopRegistrationRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new OtpException("Username already exists.");
        }

        // 1. Create Shop
        StaffShop shop = StaffShop.builder()
                .name(request.getShopName())
                .city(request.getCity())
                .address(request.getAddress())
                .phone(request.getPhoneNumber() != null ? normalizePhone(request.getPhoneNumber()) : null)
                .description(request.getShopDescription())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .active(false) // Shop needs approval too, or relies on owner status
                .build();
        shop = staffShopRepository.save(shop);

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

        // 3. Create StaffProfile linking User to Shop
        StaffProfile profile = StaffProfile.builder()
                .user(user)
                .shop(shop)
                .available(false)
                .build();
        staffProfileRepository.save(profile);

        log.info("Shop registered (pending approval): id={}, username={}, shopName={}", 
                 user.getId(), user.getUsername(), shop.getName());

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

    // ─── Login ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
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

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

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
    }

    // ─── Token Refresh ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.isRefreshTokenValid(refreshToken)) {
            throw new OtpException("Refresh token is invalid or expired. Please log in again.");
        }

        UUID userId = jwtTokenProvider.extractUserId(refreshToken);
        User user   = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (user.getApprovalStatus() == ApprovalStatus.PENDING) {
            throw new OtpException("Your account is pending admin approval.");
        }

        String newAccessToken  = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

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

    // ─── Helpers ───────────────────────────────────────────────────────────

    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        phone = phone.trim().replaceAll("\\s+", "");
        if (phone.startsWith("09") || phone.startsWith("07")) {
            return "+251" + phone.substring(1);
        }
        return phone;
    }
}
