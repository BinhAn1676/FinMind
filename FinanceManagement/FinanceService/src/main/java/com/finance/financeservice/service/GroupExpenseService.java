package com.finance.financeservice.service;

import com.finance.financeservice.mongo.document.GroupExpense;

import java.util.List;

public interface GroupExpenseService {
    GroupExpense create(GroupExpense expense);
    List<GroupExpense> findByGroupId(Long groupId);
    GroupExpense findById(String id);
    GroupExpense update(String id, GroupExpense update);
    void delete(String id);
    GroupExpense settleShare(String expenseId, Long userId);
    GroupExpense unsettleShare(String expenseId, Long userId);
    List<DebtSummary> getDebtSummary(Long groupId);

    record DebtSummary(Long fromUserId, String fromName, Long toUserId, String toName, Double amount) {}
}
