package com.finance.financeservice.service.crypto;

import com.finance.financeservice.mongo.document.Loan;
import com.finance.financeservice.mongo.document.Transaction;
import com.finance.financeservice.mysql.entity.Account;

public interface PiiCryptoService {
    String decrypt(String userId, String encrypted);
    String encrypt(String userId, String plain);
    Transaction decryptTransaction(Transaction transaction);
    Account decryptAccount(Account account);
    Loan decryptLoan(Loan loan);
    Loan encryptLoan(Loan loan);
}


