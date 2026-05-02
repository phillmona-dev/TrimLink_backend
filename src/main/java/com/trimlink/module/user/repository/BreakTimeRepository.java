package com.trimlink.module.user.repository;

import com.trimlink.module.user.entity.BreakTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BreakTimeRepository extends JpaRepository<BreakTime, UUID> {
}
