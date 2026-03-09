package com.finance.userservice.dto.properties;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ServiceInfoDto {
    private String name;
    private String description;
    private String version;
}