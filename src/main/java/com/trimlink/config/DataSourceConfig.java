package com.trimlink.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * Custom Data Source configuration to handle cloud-specific URL formats.
 * Specifically converts 'postgresql://' to 'jdbc:postgresql://' if needed.
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        String url = properties.getUrl();
        
        if (StringUtils.hasText(url) && url.startsWith("postgresql://")) {
            log.info("Detected cloud-style database URL. Converting to JDBC format...");
            url = "jdbc:" + url;
        }
        
        return properties.initializeDataSourceBuilder()
                .url(url)
                .build();
    }
}
