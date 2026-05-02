package com.trimlink.config;

import com.trimlink.module.user.entity.ApprovalStatus;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initializeAdminUser();
    }

    private void initializeAdminUser() {
        Optional<User> adminOpt = userRepository.findByUsername("admin");
        
        if (adminOpt.isEmpty()) {
            log.info("Initializing default admin user...");
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .firstName("TrimLink")
                    .lastName("Admin")
                    .phoneNumber("+251911000000")
                    .role(Role.ADMIN)
                    .active(true)
                    .phoneVerified(true)
                    .approvalStatus(ApprovalStatus.APPROVED)
                    .build();
            userRepository.save(admin);
            log.info("Admin user created successfully with username 'admin' and password 'admin123'");
        } else {
            // Force reset the password on startup to ensure user can log in
            User admin = adminOpt.get();
            log.info("Admin user found. Force resetting password to 'admin123' for rescue...");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setActive(true);
            admin.setApprovalStatus(ApprovalStatus.APPROVED);
            userRepository.save(admin);
        }
    }
}
