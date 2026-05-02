package com.trimlink.module.notification.repository;

import com.trimlink.module.notification.entity.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, UUID> {
    List<UserDeviceToken> findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(UUID userId);
    List<UserDeviceToken> findByUserIdAndActiveTrueAndDeletedFalse(UUID userId);
    Optional<UserDeviceToken> findByIdAndUserIdAndDeletedFalse(UUID id, UUID userId);
    Optional<UserDeviceToken> findByTokenAndDeletedFalse(String token);
}
