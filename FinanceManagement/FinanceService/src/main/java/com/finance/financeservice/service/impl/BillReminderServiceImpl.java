package com.finance.financeservice.service.impl;

import com.finance.financeservice.mongo.document.BillReminder;
import com.finance.financeservice.mongo.repository.BillReminderRepository;
import com.finance.financeservice.service.BillReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillReminderServiceImpl implements BillReminderService {

    private final BillReminderRepository repository;

    @Override
    public List<BillReminder> findByUserId(String userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public Optional<BillReminder> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public BillReminder create(BillReminder bill) {
        bill.setCreatedAt(LocalDateTime.now());
        bill.setUpdatedAt(LocalDateTime.now());
        if (bill.getIsActive() == null) bill.setIsActive(true);
        if (bill.getRemindDaysBefore() == null) bill.setRemindDaysBefore(3);
        return repository.save(bill);
    }

    @Override
    public BillReminder update(String id, BillReminder update) {
        return repository.findById(id).map(existing -> {
            if (update.getName() != null) existing.setName(update.getName());
            if (update.getAmount() != null) existing.setAmount(update.getAmount());
            if (update.getCycle() != null) existing.setCycle(update.getCycle());
            if (update.getDayOfMonth() != null) existing.setDayOfMonth(update.getDayOfMonth());
            if (update.getIcon() != null) existing.setIcon(update.getIcon());
            if (update.getColor() != null) existing.setColor(update.getColor());
            if (update.getNotes() != null) existing.setNotes(update.getNotes());
            if (update.getIsActive() != null) existing.setIsActive(update.getIsActive());
            if (update.getRemindDaysBefore() != null) existing.setRemindDaysBefore(update.getRemindDaysBefore());
            existing.setUpdatedAt(LocalDateTime.now());
            return repository.save(existing);
        }).orElseThrow(() -> new IllegalArgumentException("BillReminder not found: " + id));
    }

    @Override
    public void delete(String id) {
        repository.deleteById(id);
    }

    @Override
    public BillReminder markAsPaid(String id, String note) {
        return repository.findById(id).map(bill -> {
            String period = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            boolean alreadyPaid = bill.getPayments().stream()
                    .anyMatch(p -> period.equals(p.getPeriod()));
            if (!alreadyPaid) {
                BillReminder.Payment payment = BillReminder.Payment.builder()
                        .id(UUID.randomUUID().toString())
                        .period(period)
                        .paidAt(LocalDate.now())
                        .note(note)
                        .build();
                bill.getPayments().add(payment);
                bill.setUpdatedAt(LocalDateTime.now());
                repository.save(bill);
            }
            return bill;
        }).orElseThrow(() -> new IllegalArgumentException("BillReminder not found: " + id));
    }

    @Override
    public BillReminder unmarkAsPaid(String id) {
        return repository.findById(id).map(bill -> {
            String period = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            bill.getPayments().removeIf(p -> period.equals(p.getPeriod()));
            bill.setUpdatedAt(LocalDateTime.now());
            return repository.save(bill);
        }).orElseThrow(() -> new IllegalArgumentException("BillReminder not found: " + id));
    }

    @Override
    public boolean isPaidThisPeriod(BillReminder bill) {
        String period = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        return bill.getPayments().stream().anyMatch(p -> period.equals(p.getPeriod()));
    }

    @Override
    public List<BillReminder> findAllActive() {
        return repository.findByIsActiveTrue();
    }
}
