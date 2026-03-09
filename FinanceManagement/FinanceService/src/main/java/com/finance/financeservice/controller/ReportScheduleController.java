package com.finance.financeservice.controller;

import com.finance.financeservice.mongo.document.ReportSchedule;
import com.finance.financeservice.mongo.repository.ReportScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/report-schedules")
@RequiredArgsConstructor
@Slf4j
public class ReportScheduleController {

    private final ReportScheduleRepository reportScheduleRepository;

    @GetMapping
    public ResponseEntity<List<ReportSchedule>> getUserSchedules(@RequestParam String userId) {
        log.info("📅 Fetching report schedules for user: {}", userId);
        List<ReportSchedule> schedules = reportScheduleRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(schedules);
    }

    @PostMapping
    public ResponseEntity<ReportSchedule> createSchedule(@RequestBody ReportSchedule schedule) {
        log.info("📅 Creating report schedule for user: {}, email: {}, frequency: {}",
                schedule.getUserId(), schedule.getEmail(), schedule.getFrequency());

        LocalDateTime now = LocalDateTime.now();
        schedule.setCreatedAt(now);
        schedule.setUpdatedAt(now);
        schedule.setActive(true);

        ReportSchedule savedSchedule = reportScheduleRepository.save(schedule);
        log.info("✅ Schedule created with ID: {}", savedSchedule.getId());

        return ResponseEntity.ok(savedSchedule);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReportSchedule> updateSchedule(
            @PathVariable String id,
            @RequestBody ReportSchedule schedule) {
        log.info("📅 Updating report schedule: {}", id);

        return reportScheduleRepository.findById(id)
                .map(existing -> {
                    existing.setEmail(schedule.getEmail());
                    existing.setFrequency(schedule.getFrequency());
                    existing.setHour(schedule.getHour());
                    existing.setCronExpression(schedule.getCronExpression()); // Phase 4: Cron support
                    existing.setFilters(schedule.getFilters());
                    existing.setActive(schedule.getActive());
                    existing.setUpdatedAt(LocalDateTime.now());

                    ReportSchedule updated = reportScheduleRepository.save(existing);
                    log.info("✅ Schedule updated: {}", id);
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable String id,
            @RequestParam String userId) {
        log.info("🗑️ Deleting report schedule: {} for user: {}", id, userId);

        reportScheduleRepository.deleteByIdAndUserId(id, userId);
        log.info("✅ Schedule deleted successfully");

        return ResponseEntity.noContent().build();
    }
}
