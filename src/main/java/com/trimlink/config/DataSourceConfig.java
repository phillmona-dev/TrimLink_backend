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
            try {
                log.info("Detected cloud-style database URL. Parsing and converting to standard JDBC format...");
                java.net.URI uri = new java.net.URI(url);
                String userInfo = uri.getUserInfo();
                String host = uri.getHost();
                int port = uri.getPort();
                String path = uri.getPath();
                
                // Reconstruct JDBC URL: jdbc:postgresql://host:port/database
                String jdbcUrl = "jdbc:postgresql://" + host + (port != -1 ? ":" + port : ":5432") + path;
                
                var builder = properties.initializeDataSourceBuilder().url(jdbcUrl);
                
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":");
                    builder.username(parts[0]);
                    builder.password(parts[1]);
                }
                
                return builder.build();
            } catch (Exception e) {
                log.error("Failed to parse database URL: {}. Falling back to default initialization.", url, e);
            }
        }
        
        return properties.initializeDataSourceBuilder().build();
    }
}
