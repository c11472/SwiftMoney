package com.swiftmoney;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryBankRepository implements BankRepository {
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, List<Transaction>> transactions = new ConcurrentHashMap<>();

    @Override
    public synchronized Account createAccount(String accountNumber, String holderName, BigDecimal openingDeposit) {
        if (accounts.containsKey(accountNumber)) {
            throw new IllegalStateException("Account already exists: " + accountNumber);
        }

        Account account = new Account(accountNumber, holderName, openingDeposit);
        accounts.put(accountNumber, account);
        transactions.put(accountNumber, new ArrayList<>());
        if (openingDeposit.compareTo(BigDecimal.ZERO) > 0) {
            addTransaction(accountNumber, "OPENING_DEPOSIT", openingDeposit, "Initial account funding");
        }
        return account;
    }

    @Override
    public synchronized BigDecimal deposit(String accountNumber, BigDecimal amount) {
        Account account = getRequiredAccount(accountNumber);
        BigDecimal updatedBalance = account.balance().add(amount);
        accounts.put(accountNumber, new Account(account.accountNumber(), account.holderName(), updatedBalance));
        addTransaction(accountNumber, "DEPOSIT", amount, "Cash deposit");
        return updatedBalance;
    }

    @Override
    public synchronized BigDecimal withdraw(String accountNumber, BigDecimal amount) {
        Account account = getRequiredAccount(accountNumber);
        if (account.balance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance.");
        }
        BigDecimal updatedBalance = account.balance().subtract(amount);
        accounts.put(accountNumber, new Account(account.accountNumber(), account.holderName(), updatedBalance));
        addTransaction(accountNumber, "WITHDRAWAL", amount, "Cash withdrawal");
        return updatedBalance;
    }

    @Override
    public synchronized void transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new IllegalArgumentException("Source and destination accounts must be different.");
        }
        Account fromAccount = getRequiredAccount(fromAccountNumber);
        Account toAccount = getRequiredAccount(toAccountNumber);
        if (fromAccount.balance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance.");
        }

        accounts.put(fromAccountNumber, new Account(fromAccount.accountNumber(), fromAccount.holderName(), fromAccount.balance().subtract(amount)));
        accounts.put(toAccountNumber, new Account(toAccount.accountNumber(), toAccount.holderName(), toAccount.balance().add(amount)));
        addTransaction(fromAccountNumber, "TRANSFER_OUT", amount, "Transfer to " + toAccountNumber);
        addTransaction(toAccountNumber, "TRANSFER_IN", amount, "Transfer from " + fromAccountNumber);
    }

    @Override
    public synchronized BigDecimal getBalance(String accountNumber) {
        return getRequiredAccount(accountNumber).balance();
    }

    @Override
    public synchronized List<Transaction> getTransactions(String accountNumber) {
        getRequiredAccount(accountNumber);
        return transactions.getOrDefault(accountNumber, List.of()).stream()
                .sorted(Comparator.comparing(Transaction::timestamp))
                .toList();
    }

    private Account getRequiredAccount(String accountNumber) {
        Account account = accounts.get(accountNumber);
        if (account == null) {
            throw new IllegalStateException("Account not found: " + accountNumber);
        }
        return account;
    }

    private void addTransaction(String accountNumber, String type, BigDecimal amount, String description) {
        transactions.computeIfAbsent(accountNumber, ignored -> new ArrayList<>())
                .add(new Transaction(accountNumber, type, amount, description, OffsetDateTime.now(ZoneOffset.UTC)));
    }
}
