package com.trimlink.module.shop.repository;

import com.trimlink.module.shop.entity.ShopBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShopBankAccountRepository extends JpaRepository<ShopBankAccount, UUID> {
    List<ShopBankAccount> findByShopIdAndDeletedFalseOrderByCreatedAtAsc(UUID shopId);

    /**
     * Deletes all bank accounts for a shop using a JPQL query.
     * clearAutomatically = true ensures Hibernate purges these entities from its
     * first-level cache immediately, preventing the cascade on shopRepository.save()
     * from re-inserting stale data and overwriting the new accounts.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM ShopBankAccount a WHERE a.shop.id = :shopId")
    void deleteByShopId(@Param("shopId") UUID shopId);
}
