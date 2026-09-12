package com.swiftmoney;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class App {
    private final BankService bankService;

    public App(BankService bankService) {
        this.bankService = bankService;
    }

    public static void main(String[] args) {
        try {
            BankRepository repository = createRepository(System.getenv());
            String output = new App(new BankService(repository)).run(args);
            System.out.println(output);
        } catch (Exception exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        }
    }

    static BankRepository createRepository(Map<String, String> environment) {
        String storage = environment.getOrDefault("SWIFTMONEY_STORAGE", environment.containsKey("SWIFTMONEY_DB_URL") ? "postgres" : "memory");
        if ("postgres".equalsIgnoreCase(storage)) {
            return PostgresBankRepository.fromEnvironment(environment);
        }
        return new InMemoryBankRepository();
    }

    public String run(String[] args) {
        if (args.length == 0 || isHelp(args[0])) {
            return helpText();
        }

        String command = args[0].toLowerCase(Locale.ROOT);
        return switch (command) {
            case "open-account" -> openAccount(args);
            case "deposit" -> deposit(args);
            case "withdraw" -> withdraw(args);
            case "transfer" -> transfer(args);
            case "balance" -> balance(args);
            case "history" -> history(args);
            default -> throw new IllegalArgumentException("Unknown command: " + args[0] + System.lineSeparator() + helpText());
        };
    }

    private boolean isHelp(String command) {
        return "help".equalsIgnoreCase(command) || "--help".equalsIgnoreCase(command) || "-h".equalsIgnoreCase(command);
    }

    private String openAccount(String[] args) {
        requireArgs(args, 2, 3, "open-account <holderName> [openingDeposit]");
        BigDecimal openingDeposit = args.length == 3 ? parseAmount(args[2]) : BigDecimal.ZERO;
        Account account = bankService.openAccount(args[1], openingDeposit);
        return "Account created successfully: " + account.accountNumber()
                + " | Holder: " + account.holderName()
                + " | Balance: " + account.balance().toPlainString();
    }

    private String deposit(String[] args) {
        requireArgs(args, 3, 3, "deposit <accountNumber> <amount>");
        BigDecimal balance = bankService.deposit(args[1], parseAmount(args[2]));
        return "Deposit successful. Current balance: " + balance.toPlainString();
    }

    private String withdraw(String[] args) {
        requireArgs(args, 3, 3, "withdraw <accountNumber> <amount>");
        BigDecimal balance = bankService.withdraw(args[1], parseAmount(args[2]));
        return "Withdrawal successful. Current balance: " + balance.toPlainString();
    }

    private String transfer(String[] args) {
        requireArgs(args, 4, 4, "transfer <fromAccount> <toAccount> <amount>");
        bankService.transfer(args[1], args[2], parseAmount(args[3]));
        return "Transfer successful.";
    }

    private String balance(String[] args) {
        requireArgs(args, 2, 2, "balance <accountNumber>");
        return "Current balance: " + bankService.getBalance(args[1]).toPlainString();
    }

    private String history(String[] args) {
        requireArgs(args, 2, 2, "history <accountNumber>");
        List<Transaction> transactions = bankService.getTransactions(args[1]);
        if (transactions.isEmpty()) {
            return "No transactions found for account " + args[1];
        }

        StringBuilder builder = new StringBuilder("Transaction history for ").append(args[1]).append(":");
        for (Transaction transaction : transactions) {
            builder.append(System.lineSeparator())
                    .append(transaction.timestamp())
                    .append(" | ")
                    .append(transaction.type())
                    .append(" | ")
                    .append(transaction.amount().toPlainString())
                    .append(" | ")
                    .append(transaction.description());
        }
        return builder.toString();
    }

    private void requireArgs(String[] args, int min, int max, String usage) {
        if (args.length < min || args.length > max) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private BigDecimal parseAmount(String rawAmount) {
        try {
            return new BigDecimal(rawAmount).setScale(2, RoundingMode.HALF_UP);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid amount: " + rawAmount, exception);
        }
    }

    private String helpText() {
        return String.join(System.lineSeparator(),
                "SwiftMoney Bank CLI",
                "Commands:",
                "  open-account <holderName> [openingDeposit]",
                "  deposit <accountNumber> <amount>",
                "  withdraw <accountNumber> <amount>",
                "  transfer <fromAccount> <toAccount> <amount>",
                "  balance <accountNumber>",
                "  history <accountNumber>",
                "",
                "Set SWIFTMONEY_STORAGE=postgres and SWIFTMONEY_DB_URL for PostgreSQL mode.",
                "Without those variables the CLI uses in-memory storage for local runs and tests.");
    }
}
