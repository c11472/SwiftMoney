package com.swiftmoney;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BankService {
    private final BankRepository bankRepository;

    public BankService(BankRepository bankRepository) {
        this.bankRepository = bankRepository;
    }

    public Account openAccount(String holderName, BigDecimal openingDeposit) {
        requireText(holderName, "Account holder name is required.");
        BigDecimal normalizedAmount = normalizeAmount(openingDeposit, true);
        String accountNumber = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return bankRepository.createAccount(accountNumber, holderName.trim(), normalizedAmount);
    }

    public BigDecimal deposit(String accountNumber, BigDecimal amount) {
        return bankRepository.deposit(requireText(accountNumber, "Account number is required."), normalizeAmount(amount, false));
    }

    public BigDecimal withdraw(String accountNumber, BigDecimal amount) {
        return bankRepository.withdraw(requireText(accountNumber, "Account number is required."), normalizeAmount(amount, false));
    }

    public void transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        String from = requireText(fromAccountNumber, "Source account number is required.");
        String to = requireText(toAccountNumber, "Destination account number is required.");
        if (from.equals(to)) {
            throw new IllegalArgumentException("Source and destination accounts must be different.");
        }
        bankRepository.transfer(from, to, normalizeAmount(amount, false));
    }

    public BigDecimal getBalance(String accountNumber) {
        return bankRepository.getBalance(requireText(accountNumber, "Account number is required."));
    }

    public List<Transaction> getTransactions(String accountNumber) {
        return bankRepository.getTransactions(requireText(accountNumber, "Account number is required."));
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private BigDecimal normalizeAmount(BigDecimal amount, boolean zeroAllowed) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required.");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (zeroAllowed) {
            if (normalized.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Amount cannot be negative.");
            }
            return normalized;
        }
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        return normalized;
    }
}
