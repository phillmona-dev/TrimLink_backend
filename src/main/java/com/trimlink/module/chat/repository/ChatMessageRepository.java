package com.trimlink.module.chat.repository;

import com.trimlink.module.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("SELECT m FROM ChatMessage m WHERE " +
           "(m.senderId = :userA AND m.receiverId = :userB) OR " +
           "(m.senderId = :userB AND m.receiverId = :userA) " +
           "ORDER BY m.createdAt ASC")
    List<ChatMessage> findConversation(@Param("userA") UUID userA, @Param("userB") UUID userB);

    @Query("SELECT DISTINCT CASE WHEN m.senderId = :userId THEN m.receiverId ELSE m.senderId END " +
           "FROM ChatMessage m WHERE m.senderId = :userId OR m.receiverId = :userId")
    List<UUID> findConnectedUserIds(@Param("userId") UUID userId);

    long countByReceiverIdAndReadFalse(UUID receiverId);

    long countBySenderIdAndReceiverIdAndReadFalse(UUID senderId, UUID receiverId);
}
