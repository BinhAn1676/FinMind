package com.finance.chatservice.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Getter
@Setter
public class BaseEntity {

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;

    @CreatedBy
    @Field("created_by")
    private String createdBy;

    @LastModifiedDate
    @Field("updated_at")
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Field("updated_by")
    private String updatedBy;
}