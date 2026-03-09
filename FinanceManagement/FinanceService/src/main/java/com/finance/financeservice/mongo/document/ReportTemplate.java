package com.finance.financeservice.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "report_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTemplate {

    @Id
    private String id;

    private String userId;

    private String name;

    private Map<String, Object> filters;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
