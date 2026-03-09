package com.finance.financeservice.controller;

import com.finance.financeservice.mongo.document.ReportTemplate;
import com.finance.financeservice.mongo.repository.ReportTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/report-templates")
@RequiredArgsConstructor
@Slf4j
public class ReportTemplateController {

    private final ReportTemplateRepository reportTemplateRepository;

    @GetMapping
    public ResponseEntity<List<ReportTemplate>> getUserTemplates(@RequestParam String userId) {
        log.info("📋 Fetching report templates for user: {}", userId);
        List<ReportTemplate> templates = reportTemplateRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(templates);
    }

    @PostMapping
    public ResponseEntity<ReportTemplate> saveTemplate(@RequestBody ReportTemplate template) {
        log.info("💾 Saving report template: {} for user: {}", template.getName(), template.getUserId());

        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        template.setCreatedAt(now);
        template.setUpdatedAt(now);

        ReportTemplate savedTemplate = reportTemplateRepository.save(template);
        log.info("✅ Template saved with ID: {}", savedTemplate.getId());

        return ResponseEntity.ok(savedTemplate);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable String id,
            @RequestParam String userId) {
        log.info("🗑️ Deleting report template: {} for user: {}", id, userId);

        reportTemplateRepository.deleteByIdAndUserId(id, userId);
        log.info("✅ Template deleted successfully");

        return ResponseEntity.noContent().build();
    }
}
