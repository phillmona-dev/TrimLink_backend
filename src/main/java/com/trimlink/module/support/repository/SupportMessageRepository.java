package com.trimlink.module.support.repository;

import com.trimlink.module.support.entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {
    List<SupportMessage> findBySenderUsernameOrderByCreatedAtAsc(String senderUsername);
    List<SupportMessage> findByReadFalseAndFromAdminFalse();
    
    @Query("SELECT s.senderUsername FROM SupportMessage s GROUP BY s.senderUsername ORDER BY MAX(s.createdAt) DESC")
    List<String> findUniqueSenderUsernames();
}
