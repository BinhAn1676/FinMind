package com.finance.financeservice.mongo.repository;

import com.finance.financeservice.mongo.document.Loan;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoanRepository extends MongoRepository<Loan, String> {
    List<Loan> findByUserIdAndLoanType(String userId, Loan.LoanType loanType);
    List<Loan> findByUserId(String userId);
}

