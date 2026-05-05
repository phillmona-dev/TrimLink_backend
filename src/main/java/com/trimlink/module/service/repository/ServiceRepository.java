package com.trimlink.module.service.repository;

import com.trimlink.module.service.entity.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<Service, UUID> {
    Page<Service> findByActiveTrueAndDeletedFalse(Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM Service s WHERE s.active = true AND s.deleted = false AND (s.shopId = :shopId OR s.shopId IS NULL)")
    java.util.List<Service> findActiveByShopIdOrGlobal(java.util.UUID shopId);
}
