package com.trimlink.module.user.repository;

import com.trimlink.module.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByPhoneNumber(String phoneNumber);
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"barberProfile", "barberProfile.shop"})
    org.springframework.data.domain.Page<User> findAll(org.springframework.data.domain.Pageable pageable);

    long countByDeletedFalse();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"barberProfile", "barberProfile.shop"})
    java.util.List<User> findByApprovalStatusAndDeletedFalse(com.trimlink.module.user.entity.ApprovalStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.deleted = false AND (LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%')))")
    java.util.List<User> searchUsers(@org.springframework.data.repository.query.Param("q") String q);
}
