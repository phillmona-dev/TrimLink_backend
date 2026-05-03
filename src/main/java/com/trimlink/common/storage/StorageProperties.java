package com.trimlink.common.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "trimlink.storage.minio")
public class StorageProperties {
    private String endpoint = "http://localhost:9002";
    private String accessKey = "trimlink";
    private String secretKey = "trimlink123";
    private String bucket   = "trimlink-media";
    private String publicUrl = "http://localhost:9002";
}
