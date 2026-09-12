package com.swiftmoney;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AppTest {
    private final App app = new App(new BankService(new InMemoryBankRepository()));

    @Test
    void opensAccountAndShowsBalance() {
        String result = app.run(new String[]{"open-account", "Alice", "250.00"});

        assertTrue(result.contains("Account created successfully: ACC-"));
        assertTrue(result.contains("Holder: Alice"));
        assertTrue(result.endsWith("Balance: 250.00"));
    }

    @Test
    void supportsTransferAndHistoryCommands() {
        BankService service = new BankService(new InMemoryBankRepository());
        Account first = service.openAccount("Alice", new BigDecimal("100.00"));
        Account second = service.openAccount("Bob", new BigDecimal("25.00"));
        App transferApp = new App(service);

        assertEquals("Transfer successful.", transferApp.run(new String[]{"transfer", first.accountNumber(), second.accountNumber(), "40.00"}));
        assertEquals("Current balance: 60.00", transferApp.run(new String[]{"balance", first.accountNumber()}));
        assertEquals("Current balance: 65.00", transferApp.run(new String[]{"balance", second.accountNumber()}));
        assertTrue(transferApp.run(new String[]{"history", second.accountNumber()}).contains("TRANSFER_IN | 40.00 | Transfer from " + first.accountNumber()));
    }

    @Test
    void rejectsInvalidAmounts() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> app.run(new String[]{"open-account", "Alice", "-5.00"}));

        assertEquals("Amount cannot be negative.", exception.getMessage());
    }

    @Test
    void roundsCliAmountsToTwoDecimals() {
        String result = app.run(new String[]{"open-account", "Alice", "10.005"});

        assertTrue(result.endsWith("Balance: 10.01"));
    }

    @Test
    void roundsDepositAmountsToTwoDecimals() {
        BankService service = new BankService(new InMemoryBankRepository());
        Account account = service.openAccount("Alice", new BigDecimal("5.00"));
        App depositApp = new App(service);

        assertEquals("Deposit successful. Current balance: 15.01",
                depositApp.run(new String[]{"deposit", account.accountNumber(), "10.005"}));
    }
}
