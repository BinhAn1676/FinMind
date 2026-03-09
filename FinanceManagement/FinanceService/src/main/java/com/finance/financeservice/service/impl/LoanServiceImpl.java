package com.finance.financeservice.service.impl;

import com.finance.financeservice.mongo.document.Loan;
import com.finance.financeservice.mongo.repository.LoanRepository;
import com.finance.financeservice.service.LoanService;
import com.finance.financeservice.service.crypto.PiiCryptoService;
import com.finance.financeservice.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanServiceImpl implements LoanService {
    private final LoanRepository loanRepository;
    private final MongoTemplate mongoTemplate;
    private final PiiCryptoService piiCryptoService;

    @Override
    public Optional<Loan> getById(String id) {
        return loanRepository.findById(id)
            .map(this::recalculateLoan)
            .map(piiCryptoService::decryptLoan);
    }

    @Override
    public Loan create(Loan loan) {
        // Encrypt borrower PII before saving
        piiCryptoService.encryptLoan(loan);
        
        // Calculate interest amount
        if (loan.getPrincipalAmount() != null && loan.getInterestRate() != null && loan.getTermDays() != null) {
            double interest = calculateInterest(loan.getPrincipalAmount(), loan.getInterestRate(), loan.getTermDays());
            loan.setInterestAmount(interest);
        }

        // Calculate daily payment amount
        if (loan.getPrincipalAmount() != null && loan.getInterestAmount() != null && loan.getTermDays() != null && loan.getTermDays() > 0) {
            double dailyPayment = (loan.getPrincipalAmount() + loan.getInterestAmount()) / loan.getTermDays();
            loan.setDailyPaymentAmount(dailyPayment);
        }

        // Initialize payment list if null
        if (loan.getPayments() == null) {
            loan.setPayments(new java.util.ArrayList<>());
        }
        if (loan.getTotalPaid() == null) {
            loan.setTotalPaid(0.0);
        }

        Loan saved = loanRepository.save(loan);
        Loan recalculated = recalculateLoan(saved);
        return piiCryptoService.decryptLoan(recalculated);
    }

    @Override
    public Loan update(String id, Loan update) {
        return loanRepository.findById(id).map(existing -> {
            // Decrypt existing loan to get plain text borrowers for comparison
            Loan decryptedExisting = piiCryptoService.decryptLoan(existing);
            
            // Update basic fields
            if (update.getBorrowers() != null && !update.getBorrowers().isEmpty()) {
                // Encrypt the new borrowers before setting
                Loan tempLoan = new Loan();
                tempLoan.setUserId(existing.getUserId());
                tempLoan.setBorrowers(update.getBorrowers());
                piiCryptoService.encryptLoan(tempLoan);
                existing.setBorrowers(tempLoan.getBorrowers());
            }
            if (update.getLoanType() != null) existing.setLoanType(update.getLoanType());
            if (update.getStatus() != null) existing.setStatus(update.getStatus());
            if (update.getPrincipalAmount() != null) existing.setPrincipalAmount(update.getPrincipalAmount());
            if (update.getInterestRate() != null) existing.setInterestRate(update.getInterestRate());
            if (update.getStartDate() != null) existing.setStartDate(update.getStartDate());
            if (update.getTermDays() != null) existing.setTermDays(update.getTermDays());
            if (update.getEndDate() != null) existing.setEndDate(update.getEndDate());
            if (update.getNotes() != null) existing.setNotes(update.getNotes());

            // Recalculate interest and daily payment
            if (existing.getPrincipalAmount() != null && existing.getInterestRate() != null && existing.getTermDays() != null) {
                double interest = calculateInterest(existing.getPrincipalAmount(), existing.getInterestRate(), existing.getTermDays());
                existing.setInterestAmount(interest);
                
                if (existing.getTermDays() > 0) {
                    double dailyPayment = (existing.getPrincipalAmount() + interest) / existing.getTermDays();
                    existing.setDailyPaymentAmount(dailyPayment);
                }
            }

            existing.setUpdatedAt(java.time.LocalDateTime.now());
            Loan saved = loanRepository.save(existing);
            Loan recalculated = recalculateLoan(saved);
            return piiCryptoService.decryptLoan(recalculated);
        }).orElseThrow(() -> new IllegalArgumentException("Loan not found: " + id));
    }

    @Override
    public void delete(String id) {
        loanRepository.deleteById(id);
    }

    @Override
    public Page<Loan> filter(String userId, Loan.LoanType loanType, String searchTerm,
                             LocalDate startDate, LocalDate endDate,
                             List<Loan.LoanStatus> statuses,
                             LocalDate reconciliationDate,
                             int page, int size) {
        Query query = new Query();
        
        if (userId != null && !userId.isEmpty()) {
            query.addCriteria(Criteria.where("user_id").is(userId));
        }
        if (loanType != null) {
            query.addCriteria(Criteria.where("loan_type").is(loanType));
        }
        if (searchTerm != null && !searchTerm.isEmpty()) {
            // Hash the search term for encrypted fields
            String searchHash = HashUtils.sha256(searchTerm);
            
            // Search in hash fields (for encrypted borrower data) and notes (not encrypted)
            Criteria searchCriteria = new Criteria().orOperator(
                Criteria.where("borrowers.full_name_hash").is(searchHash),
                Criteria.where("borrowers.phone_number_hash").is(searchHash),
                Criteria.where("borrowers.cccd_hash").is(searchHash),
                Criteria.where("borrowers.address_hash").is(searchHash),
                Criteria.where("notes").regex(searchTerm, "i")
            );
            query.addCriteria(searchCriteria);
        }
        if (startDate != null) {
            query.addCriteria(Criteria.where("start_date").gte(startDate));
        }
        if (endDate != null) {
            query.addCriteria(Criteria.where("end_date").lte(endDate));
        }

        long total = mongoTemplate.count(query, Loan.class);

        Pageable pageable = PageRequest.of(page, size);
        query.with(pageable);
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "start_date"));

        List<Loan> content = mongoTemplate.find(query, Loan.class);
        List<Loan> recalculated = content.stream()
            .map(loan -> recalculateLoan(loan, reconciliationDate))
            .map(piiCryptoService::decryptLoan)
            .collect(Collectors.toList());
        
        // Filter by status after recalculation (since status is calculated)
        if (statuses != null && !statuses.isEmpty()) {
            recalculated = recalculated.stream()
                .filter(loan -> statuses.contains(loan.getStatus()))
                .collect(Collectors.toList());
            total = recalculated.size();
        }
        
        return new PageImpl<>(recalculated, pageable, total);
    }

    @Override
    public LoanSummary summary(String userId, Loan.LoanType loanType) {
        Query query = new Query();
        if (userId != null && !userId.isEmpty()) {
            query.addCriteria(Criteria.where("user_id").is(userId));
        }
        if (loanType != null) {
            query.addCriteria(Criteria.where("loan_type").is(loanType));
        }

        List<Loan> loans = mongoTemplate.find(query, Loan.class);
        loans = loans.stream()
            .map(this::recalculateLoan)
            .map(piiCryptoService::decryptLoan)
            .collect(Collectors.toList());

        double totalPrincipal = 0.0;
        double totalInterest = 0.0;
        double totalCollected = 0.0;

        for (Loan loan : loans) {
            if (loan.getPrincipalAmount() != null) {
                totalPrincipal += loan.getPrincipalAmount();
            }
            if (loan.getInterestAmount() != null) {
                totalInterest += loan.getInterestAmount();
            }
            if (loan.getTotalPaid() != null) {
                totalCollected += loan.getTotalPaid();
            }
        }

        double totalAfterInterest = totalPrincipal + totalInterest;
        int loanCount = loans.size();

        return new LoanSummary(totalPrincipal, totalInterest, totalCollected, totalAfterInterest, loanCount);
    }

    @Override
    public Loan addPayment(String loanId, Loan.LoanPayment payment) {
        return loanRepository.findById(loanId).map(loan -> {
            if (loan.getPayments() == null) {
                loan.setPayments(new java.util.ArrayList<>());
            }
            if (payment.getId() == null || payment.getId().isEmpty()) {
                payment.setId(UUID.randomUUID().toString());
            }
            if (payment.getCreatedAt() == null) {
                payment.setCreatedAt(java.time.LocalDateTime.now());
            }
            
            loan.getPayments().add(payment);
            
            // Update total paid
            double totalPaid = loan.getPayments().stream()
                .mapToDouble(p -> p.getAmount() != null ? p.getAmount() : 0.0)
                .sum();
            loan.setTotalPaid(totalPaid);
            
            loan.setUpdatedAt(java.time.LocalDateTime.now());
            Loan saved = loanRepository.save(loan);
            Loan recalculated = recalculateLoan(saved);
            return piiCryptoService.decryptLoan(recalculated);
        }).orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
    }

    @Override
    public Loan updatePayment(String loanId, String paymentId, Loan.LoanPayment payment) {
        return loanRepository.findById(loanId).map(loan -> {
            if (loan.getPayments() == null) {
                throw new IllegalArgumentException("Loan has no payments");
            }
            
            boolean found = false;
            for (int i = 0; i < loan.getPayments().size(); i++) {
                if (paymentId.equals(loan.getPayments().get(i).getId())) {
                    payment.setId(paymentId); // Ensure ID is preserved
                    if (payment.getCreatedAt() == null) {
                        payment.setCreatedAt(loan.getPayments().get(i).getCreatedAt());
                    }
                    loan.getPayments().set(i, payment);
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                throw new IllegalArgumentException("Payment not found: " + paymentId);
            }
            
            // Recalculate total paid
            double totalPaid = loan.getPayments().stream()
                .mapToDouble(p -> p.getAmount() != null ? p.getAmount() : 0.0)
                .sum();
            loan.setTotalPaid(totalPaid);
            
            loan.setUpdatedAt(java.time.LocalDateTime.now());
            Loan saved = loanRepository.save(loan);
            Loan recalculated = recalculateLoan(saved);
            return piiCryptoService.decryptLoan(recalculated);
        }).orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
    }

    @Override
    public Loan deletePayment(String loanId, String paymentId) {
        return loanRepository.findById(loanId).map(loan -> {
            if (loan.getPayments() == null) {
                throw new IllegalArgumentException("Loan has no payments");
            }
            
            boolean removed = loan.getPayments().removeIf(p -> paymentId.equals(p.getId()));
            if (!removed) {
                throw new IllegalArgumentException("Payment not found: " + paymentId);
            }
            
            // Recalculate total paid
            double totalPaid = loan.getPayments().stream()
                .mapToDouble(p -> p.getAmount() != null ? p.getAmount() : 0.0)
                .sum();
            loan.setTotalPaid(totalPaid);
            
            loan.setUpdatedAt(java.time.LocalDateTime.now());
            Loan saved = loanRepository.save(loan);
            Loan recalculated = recalculateLoan(saved);
            return piiCryptoService.decryptLoan(recalculated);
        }).orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
    }

    /**
     * Recalculate all computed fields for a loan based on current date and payments
     */
    private Loan recalculateLoan(Loan loan) {
        return recalculateLoan(loan, null);
    }
    
    private Loan recalculateLoan(Loan loan, LocalDate reconciliationDate) {
        if (loan == null) return null;
        
        LocalDate today = LocalDate.now();
        // Use provided reconciliationDate, or default to today
        if (reconciliationDate == null) {
            reconciliationDate = today;
        }
        
        // Calculate cumulative amounts up to reconciliation date
        if (loan.getStartDate() != null && loan.getDailyPaymentAmount() != null) {
            long daysElapsed = ChronoUnit.DAYS.between(loan.getStartDate(), reconciliationDate);
            if (daysElapsed < 0) daysElapsed = 0;
            if (loan.getTermDays() != null && daysElapsed > loan.getTermDays()) {
                daysElapsed = loan.getTermDays();
            }
            
            double totalExpected = loan.getDailyPaymentAmount() * daysElapsed;
            
            // Calculate principal and interest portions
            double principalPortion = 0.0;
            double interestPortion = 0.0;
            if (loan.getPrincipalAmount() != null && loan.getInterestAmount() != null) {
                double totalLoan = loan.getPrincipalAmount() + loan.getInterestAmount();
                if (totalLoan > 0) {
                    principalPortion = (loan.getPrincipalAmount() / totalLoan) * totalExpected;
                    interestPortion = (loan.getInterestAmount() / totalLoan) * totalExpected;
                }
            }
            
            loan.setCumulativePrincipalCollected(principalPortion);
            loan.setCumulativeInterestCollected(interestPortion);
            loan.setCumulativeTotalCollected(totalExpected);
            
            // Calculate amount due (expected - actual paid)
            double totalPaid = loan.getTotalPaid() != null ? loan.getTotalPaid() : 0.0;
            loan.setAmountDue(totalExpected - totalPaid);
        }
        
        // Calculate outstanding debt
        if (loan.getPrincipalAmount() != null && loan.getInterestAmount() != null) {
            double totalLoan = loan.getPrincipalAmount() + loan.getInterestAmount();
            double totalPaid = loan.getTotalPaid() != null ? loan.getTotalPaid() : 0.0;
            
            // Outstanding debt = total loan - total paid
            loan.setOutstandingDebt(totalLoan - totalPaid);
        }
        
        // Calculate status
        loan.setStatus(calculateStatus(loan));
        
        return loan;
    }

    /**
     * Calculate loan status based on payment and date
     */
    private Loan.LoanStatus calculateStatus(Loan loan) {
        if (loan.getPrincipalAmount() == null || loan.getInterestAmount() == null) {
            return Loan.LoanStatus.ON_GOING;
        }
        
        double totalLoan = loan.getPrincipalAmount() + loan.getInterestAmount();
        double totalPaid = loan.getTotalPaid() != null ? loan.getTotalPaid() : 0.0;
        LocalDate today = LocalDate.now();
        LocalDate endDate = loan.getEndDate();
        
        // PAID: totalPaid >= totalLoan
        if (totalPaid >= totalLoan) {
            return Loan.LoanStatus.PAID;
        }
        
        // OUTDATE: not paid fully and endDate < today
        if (endDate != null && endDate.isBefore(today)) {
            return Loan.LoanStatus.OUTDATE;
        }
        
        // ON_GOING: all other cases
        return Loan.LoanStatus.ON_GOING;
    }

    /**
     * Calculate interest amount based on principal, rate, and term
     * Interest rate is monthly (per 30 days)
     * Formula: principal * (interestRate / 100) * (termDays / 30)
     * Example: 50,000,000 * 0.8% * (90 / 30) = 1,200,000
     */
    private double calculateInterest(double principal, double interestRate, int termDays) {
        // Monthly interest calculation: principal * rate * (termDays / 30)
        return principal * (interestRate / 100.0) * (termDays / 30.0);
    }
}

