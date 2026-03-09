package com.finance.financeservice.service;

import com.finance.financeservice.mongo.document.Loan;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoanService {
    Optional<Loan> getById(String id);
    
    Loan create(Loan loan);
    
    Loan update(String id, Loan loan);
    
    void delete(String id);
    
    Page<Loan> filter(String userId, Loan.LoanType loanType, String searchTerm,
                     LocalDate startDate, LocalDate endDate,
                     List<Loan.LoanStatus> statuses,
                     LocalDate reconciliationDate,
                     int page, int size);
    
    LoanSummary summary(String userId, Loan.LoanType loanType);
    
    Loan addPayment(String loanId, Loan.LoanPayment payment);
    
    Loan updatePayment(String loanId, String paymentId, Loan.LoanPayment payment);
    
    Loan deletePayment(String loanId, String paymentId);
    
    record LoanSummary(
        Double totalPrincipal,      // Tổng tiền cho vay
        Double totalInterest,        // Tổng tiền lãi
        Double totalCollected,       // Tổng tiền thu được tính đến hiện tại
        Double totalAfterInterest,   // Tổng tiền sau lãi (principal + interest)
        Integer loanCount            // Total number of loans
    ) {}
}

