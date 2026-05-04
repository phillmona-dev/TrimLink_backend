package com.trimlink.module.admin.repository;

import com.trimlink.module.admin.entity.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, Long> {
    Optional<PlatformSetting> findByKey(String key);
}
