package com.finance.userservice.dto.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
@ConfigurationProperties(prefix = "info.app")
public class ServiceInfoProperties {
    private String name;
    private String description;
    private String version;
}
