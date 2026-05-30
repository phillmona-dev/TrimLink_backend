package com.trimlink.module.user.service;

import com.trimlink.module.user.dto.CreateCustomerRequest;
import com.trimlink.module.user.entity.ApprovalStatus;
import com.trimlink.module.user.entity.Role;
import com.trimlink.module.user.entity.User;
import com.trimlink.module.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Page<User> getUsers(
            String search,
            Role role,
            Boolean active,
            ApprovalStatus approvalStatus,
            Boolean deleted,
            Pageable pageable
    ) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String term = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), term),
                        cb.like(cb.lower(root.get("lastName")), term),
                        cb.like(cb.lower(root.get("username")), term),
                        cb.like(cb.lower(root.get("email")), term),
                        cb.like(cb.lower(root.get("phoneNumber")), term)
                ));
            }

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (approvalStatus != null) {
                predicates.add(cb.equal(root.get("approvalStatus"), approvalStatus));
            }
            if (deleted != null) {
                predicates.add(cb.equal(root.get("deleted"), deleted));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return userRepository.findAll(spec, pageable);
    }

    @Transactional
    public User createCustomer(CreateCustomerRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already taken");
        }
        if (request.getPhoneNumber() != null && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new RuntimeException("Phone number already taken");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already taken");
        }

        User user = User.builder()
                .username(request.getUsername())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .avatarUrl(request.getAvatarUrl())
                .role(Role.CUSTOMER)
                .approvalStatus(ApprovalStatus.APPROVED)
                .active(true)
                .phoneVerified(false)
                .build();

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return userRepository.save(user);
    }
}
