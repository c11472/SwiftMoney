package com.swiftmoney;

import java.math.BigDecimal;
import java.util.List;

public interface BankRepository {
    Account createAccount(String accountNumber, String holderName, BigDecimal openingDeposit);
    BigDecimal deposit(String accountNumber, BigDecimal amount);
    BigDecimal withdraw(String accountNumber, BigDecimal amount);
    void transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount);
    BigDecimal getBalance(String accountNumber);
    List<Transaction> getTransactions(String accountNumber);
}
