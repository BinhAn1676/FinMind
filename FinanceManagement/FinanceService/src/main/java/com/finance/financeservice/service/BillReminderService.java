package com.finance.financeservice.service;

import com.finance.financeservice.mongo.document.BillReminder;

import java.util.List;
import java.util.Optional;

public interface BillReminderService {

    List<BillReminder> findByUserId(String userId);

    Optional<BillReminder> findById(String id);

    BillReminder create(BillReminder bill);

    BillReminder update(String id, BillReminder update);

    void delete(String id);

    BillReminder markAsPaid(String id, String note);

    BillReminder unmarkAsPaid(String id);

    boolean isPaidThisPeriod(BillReminder bill);

    List<BillReminder> findAllActive();
}
