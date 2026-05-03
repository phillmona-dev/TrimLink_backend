package com.trimlink.module.shop.repository;

import com.trimlink.module.shop.entity.StaffShop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StaffShopRepository extends JpaRepository<StaffShop, UUID> {
    Page<StaffShop> findByCityAndActiveTrue(String city, Pageable pageable);
    Page<StaffShop> findByActiveTrue(Pageable pageable);
    long countByDeletedFalse();

    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT s FROM StaffShop s " +
        "LEFT JOIN s.staffs b " +
        "LEFT JOIN b.user u " +
        "WHERE s.active = true AND (" +
        " LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
        " LOWER(s.city) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
        " LOWER(s.address) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
        " LOWER(s.phone) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
        " (u.role = 'OWNER' AND (" +
        "   LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
        "   LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
        "   LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :q, '%'))" +
        " ))" +
        ")"
    )
    Page<StaffShop> search(@org.springframework.data.repository.query.Param("q") String query, Pageable pageable);
}
