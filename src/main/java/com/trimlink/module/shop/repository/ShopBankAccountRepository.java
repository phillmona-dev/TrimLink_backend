package com.trimlink.module.shop.repository;

import com.trimlink.module.shop.entity.ShopBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShopBankAccountRepository extends JpaRepository<ShopBankAccount, UUID> {
    List<ShopBankAccount> findByShopIdAndDeletedFalseOrderByCreatedAtAsc(UUID shopId);
}
