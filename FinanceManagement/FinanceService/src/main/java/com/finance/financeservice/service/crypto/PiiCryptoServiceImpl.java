package com.finance.financeservice.service.crypto;

import com.finance.financeservice.dto.keys.DecryptRequest;
import com.finance.financeservice.dto.keys.DecryptResponse;
import com.finance.financeservice.dto.keys.EncryptRequest;
import com.finance.financeservice.dto.keys.EncryptResponse;
import com.finance.financeservice.mongo.document.Loan;
import com.finance.financeservice.mongo.document.Transaction;
import com.finance.financeservice.mysql.entity.Account;
import com.finance.financeservice.service.client.KeyManagementServiceClient;
import com.finance.financeservice.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PiiCryptoServiceImpl implements PiiCryptoService {

    private final KeyManagementServiceClient keyManagementServiceClient;

    @Override
    public String decrypt(String userId, String encrypted) {
        if (ObjectUtils.isEmpty(encrypted)) return encrypted;
        try {
            DecryptRequest request = DecryptRequest.builder()
                    .userId(userId)
                    .encryptedData(encrypted)
                    .build();
            DecryptResponse response = keyManagementServiceClient.decrypt(request);
            if (response != null && response.isSuccess()) {
                return response.getDecryptedData();
            }
        } catch (Exception e) {
            log.warn("Decrypt failed for user {}: {}", userId, e.getMessage());
        }
        return encrypted;
    }

    @Override
    public Transaction decryptTransaction(Transaction transaction) {
        if (transaction == null) return null;
        String userId = transaction.getUserId();
        Executor exec = Executors.newFixedThreadPool(2);
        var brandFuture = CompletableFuture.supplyAsync(() -> decrypt(userId, transaction.getBankBrandName()), exec);
        var accountFuture = CompletableFuture.supplyAsync(() -> decrypt(userId, transaction.getAccountNumber()), exec);
        CompletableFuture.allOf(brandFuture, accountFuture).join();
        transaction.setBankBrandName(brandFuture.join());
        transaction.setAccountNumber(accountFuture.join());
        return transaction;
    }

    @Override
    public Account decryptAccount(Account account) {
        if (account == null) return null;
        String userId = account.getUserId();
        Executor exec = Executors.newFixedThreadPool(6);
        var holderFuture = CompletableFuture.supplyAsync(() -> decrypt(userId, account.getAccountHolderName()), exec);
        var accountNumFuture = CompletableFuture.supplyAsync(() -> decrypt(userId, account.getAccountNumber()), exec);
        var shortFuture = CompletableFuture.supplyAsync(() -> decrypt(userId, account.getBankShortName()), exec);
        var fullFuture = CompletableFuture.supplyAsync(() -> decrypt(userId, account.getBankFullName()), exec);
        var binFuture = CompletableFuture.supplyAsync(() -> decrypt(userId, account.getBankBin()), exec);
        var codeFuture = CompletableFuture.supplyAsync(() -> decrypt(userId, account.getBankCode()), exec);
        CompletableFuture.allOf(holderFuture, accountNumFuture, shortFuture, fullFuture, binFuture, codeFuture).join();
        account.setAccountHolderName(holderFuture.join());
        account.setAccountNumber(accountNumFuture.join());
        account.setBankShortName(shortFuture.join());
        account.setBankFullName(fullFuture.join());
        account.setBankBin(binFuture.join());
        account.setBankCode(codeFuture.join());
        return account;
    }

    @Override
    public String encrypt(String userId, String plain) {
        if (ObjectUtils.isEmpty(plain)) return plain;
        try {
            EncryptRequest request = EncryptRequest.builder()
                    .userId(userId)
                    .data(plain)
                    .build();
            EncryptResponse response = keyManagementServiceClient.encrypt(request);
            if (response != null && response.isSuccess()) {
                return response.getEncryptedData();
            }
        } catch (Exception e) {
            log.warn("Encrypt failed for user {}: {}", userId, e.getMessage());
        }
        return plain;
    }

    @Override
    public Loan decryptLoan(Loan loan) {
        if (loan == null || loan.getBorrowers() == null || loan.getBorrowers().isEmpty()) {
            return loan;
        }
        String userId = loan.getUserId();
        if (ObjectUtils.isEmpty(userId)) {
            return loan;
        }
        
        Executor exec = Executors.newFixedThreadPool(4);
        List<Loan.Borrower> borrowers = loan.getBorrowers();
        
        for (Loan.Borrower borrower : borrowers) {
            var fullNameFuture = CompletableFuture.supplyAsync(() -> decrypt(userId, borrower.getFullName()), exec);
            var cccdFuture = CompletableFuture.supplyAsync(() -> decrypt(userId, borrower.getCccd()), exec);
            var phoneFuture = CompletableFuture.supplyAsync(() -> decrypt(userId, borrower.getPhoneNumber()), exec);
            var addressFuture = CompletableFuture.supplyAsync(() -> decrypt(userId, borrower.getAddress()), exec);
            
            CompletableFuture.allOf(fullNameFuture, cccdFuture, phoneFuture, addressFuture).join();
            borrower.setFullName(fullNameFuture.join());
            borrower.setCccd(cccdFuture.join());
            borrower.setPhoneNumber(phoneFuture.join());
            borrower.setAddress(addressFuture.join());
        }
        
        return loan;
    }

    @Override
    public Loan encryptLoan(Loan loan) {
        if (loan == null || loan.getBorrowers() == null || loan.getBorrowers().isEmpty()) {
            return loan;
        }
        String userId = loan.getUserId();
        if (ObjectUtils.isEmpty(userId)) {
            return loan;
        }
        
        Executor exec = Executors.newFixedThreadPool(4);
        List<Loan.Borrower> borrowers = loan.getBorrowers();
        
        for (Loan.Borrower borrower : borrowers) {
            // Store original values for hashing before encryption
            String originalFullName = borrower.getFullName();
            String originalCccd = borrower.getCccd();
            String originalPhone = borrower.getPhoneNumber();
            String originalAddress = borrower.getAddress();
            
            // Encrypt in parallel
            var fullNameFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, originalFullName), exec);
            var cccdFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, originalCccd), exec);
            var phoneFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, originalPhone), exec);
            var addressFuture = CompletableFuture.supplyAsync(() -> encrypt(userId, originalAddress), exec);
            
            // Create hashes in parallel
            var fullNameHashFuture = CompletableFuture.supplyAsync(() -> HashUtils.sha256(originalFullName), exec);
            var cccdHashFuture = CompletableFuture.supplyAsync(() -> HashUtils.sha256(originalCccd), exec);
            var phoneHashFuture = CompletableFuture.supplyAsync(() -> HashUtils.sha256(originalPhone), exec);
            var addressHashFuture = CompletableFuture.supplyAsync(() -> HashUtils.sha256(originalAddress), exec);
            
            CompletableFuture.allOf(fullNameFuture, cccdFuture, phoneFuture, addressFuture,
                    fullNameHashFuture, cccdHashFuture, phoneHashFuture, addressHashFuture).join();
            
            borrower.setFullName(fullNameFuture.join());
            borrower.setCccd(cccdFuture.join());
            borrower.setPhoneNumber(phoneFuture.join());
            borrower.setAddress(addressFuture.join());
            
            // Set hash values
            borrower.setFullNameHash(fullNameHashFuture.join());
            borrower.setCccdHash(cccdHashFuture.join());
            borrower.setPhoneNumberHash(phoneHashFuture.join());
            borrower.setAddressHash(addressHashFuture.join());
        }
        
        return loan;
    }
}


