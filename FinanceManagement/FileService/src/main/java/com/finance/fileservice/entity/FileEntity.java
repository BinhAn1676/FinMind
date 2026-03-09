package com.finance.fileservice.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "files")
@CompoundIndexes({
    @CompoundIndex(name = "userId_purpose_idx", def = "{'userId': 1, 'purpose': 1}", unique = true)
})
@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class FileEntity {
    
    @Id
    private String id;
    
    @Indexed
    private String userId;
    
    @Indexed
    private String purpose; // e.g., "avatar", "chat", "document", etc.
    
    private String fileName;
    private String originalFileName;
    private String fileType;
    private Long fileSize;
    private String fileUrl; // MinIO object key
    private String description;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
