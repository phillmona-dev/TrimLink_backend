package com.trimlink.module.auth.controller;

import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fix-admin")
@RequiredArgsConstructor
public class FixController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public String fixAdmin() {
        User admin = userRepository.findByUsername("admin")
                .orElseGet(() -> {
                    User u = new User();
                    u.setUsername("admin");
                    u.setFirstName("Admin");
                    u.setLastName("TrimLink");
                    u.setRole(com.trimlink.module.user.entity.Role.ADMIN);
                    u.setActive(true);
                    u.setPhoneVerified(true);
                    u.setApprovalStatus(com.trimlink.module.user.entity.ApprovalStatus.APPROVED);
                    u.setPhoneNumber("+251900000000");
                    return u;
                });
        
        admin.setPassword(passwordEncoder.encode("admin123"));
        userRepository.save(admin);
        
        return "Admin user has been reset to: username=admin, password=admin123";
    }
}
