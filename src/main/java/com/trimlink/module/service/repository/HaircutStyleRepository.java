package com.trimlink.module.service.repository;

import com.trimlink.module.service.entity.HaircutStyle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HaircutStyleRepository extends JpaRepository<HaircutStyle, String> {
    List<HaircutStyle> findByCategory(String category);
}
