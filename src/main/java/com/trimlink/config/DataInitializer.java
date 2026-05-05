package com.trimlink.config;

import com.trimlink.module.user.entity.ApprovalStatus;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.UserRepository;
import com.trimlink.module.service.repository.ServiceRepository;
import com.trimlink.module.user.repository.BarberServiceAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ServiceRepository serviceRepository;
    private final BarberServiceAssignmentRepository barberServiceAssignmentRepository;

    @Override
    public void run(String... args) {
        initializeAdminUser();
        activateAllServicesAndAssignments();
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
            User admin = adminOpt.get();
            log.info("Admin user 'admin' already exists. Updating password to ensure access.");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setActive(true);
            admin.setApprovalStatus(ApprovalStatus.APPROVED);
            userRepository.save(admin);
            log.info("Admin user 'admin' credentials refreshed.");
        }
    }

    /**
     * Ensures all services and barber service assignments are active.
     * This is a safety net for production environments where records may have been
     * accidentally deactivated. Runs on every startup.
     */
    @Transactional
    private void activateAllServicesAndAssignments() {
        // Activate all non-deleted services
        var inactiveServices = serviceRepository.findAll().stream()
                .filter(s -> !s.isDeleted() && !s.isActive())
                .toList();

        if (!inactiveServices.isEmpty()) {
            inactiveServices.forEach(s -> s.setActive(true));
            serviceRepository.saveAll(inactiveServices);
            log.info("Activated {} inactive service(s) on startup.", inactiveServices.size());
        }

        // Activate all non-deleted barber service assignments
        var inactiveAssignments = barberServiceAssignmentRepository.findAll().stream()
                .filter(a -> !a.isDeleted() && !a.isActive())
                .toList();

        if (!inactiveAssignments.isEmpty()) {
            inactiveAssignments.forEach(a -> a.setActive(true));
            barberServiceAssignmentRepository.saveAll(inactiveAssignments);
            log.info("Activated {} inactive barber service assignment(s) on startup.", inactiveAssignments.size());
        }
    }
}
