package com.swiftmoney;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostgresBankRepository implements BankRepository {
    private final String url;
    private final String username;
    private final String password;

    public PostgresBankRepository(String url, String username, String password) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("SWIFTMONEY_DB_URL is required for PostgreSQL mode.");
        }
        this.url = url;
        this.username = username;
        this.password = password;
        initializeSchema();
    }

    public static PostgresBankRepository fromEnvironment(Map<String, String> environment) {
        return new PostgresBankRepository(
                environment.get("SWIFTMONEY_DB_URL"),
                environment.getOrDefault("SWIFTMONEY_DB_USER", "postgres"),
                environment.getOrDefault("SWIFTMONEY_DB_PASSWORD", ""));
    }

    @Override
    public Account createAccount(String accountNumber, String holderName, BigDecimal openingDeposit) {
        String accountSql = "INSERT INTO accounts (account_number, holder_name, balance) VALUES (?, ?, ?)";
        String transactionSql = "INSERT INTO transactions (account_number, transaction_type, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = newConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement accountStatement = connection.prepareStatement(accountSql);
                 PreparedStatement transactionStatement = connection.prepareStatement(transactionSql)) {
                accountStatement.setString(1, accountNumber);
                accountStatement.setString(2, holderName);
                accountStatement.setBigDecimal(3, openingDeposit);
                accountStatement.executeUpdate();

                if (openingDeposit.compareTo(BigDecimal.ZERO) > 0) {
                    transactionStatement.setString(1, accountNumber);
                    transactionStatement.setString(2, "OPENING_DEPOSIT");
                    transactionStatement.setBigDecimal(3, openingDeposit);
                    transactionStatement.setString(4, "Initial account funding");
                    transactionStatement.setTimestamp(5, Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()));
                    transactionStatement.executeUpdate();
                }
                connection.commit();
                return new Account(accountNumber, holderName, openingDeposit);
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to create account.", exception);
        }
    }

    @Override
    public BigDecimal deposit(String accountNumber, BigDecimal amount) {
        return updateBalance(accountNumber, amount, "DEPOSIT", "Cash deposit");
    }

    @Override
    public BigDecimal withdraw(String accountNumber, BigDecimal amount) {
        return updateBalance(accountNumber, amount.negate(), "WITHDRAWAL", "Cash withdrawal");
    }

    @Override
    public void transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount) {
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new IllegalArgumentException("Source and destination accounts must be different.");
        }
        String accountSql = "SELECT balance FROM accounts WHERE account_number = ? FOR UPDATE";
        String updateSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
        String transactionSql = "INSERT INTO transactions (account_number, transaction_type, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = newConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, BigDecimal> lockedBalances = lockBalancesInOrder(connection, accountSql, fromAccountNumber, toAccountNumber);
                BigDecimal fromBalance = lockedBalances.get(fromAccountNumber);
                BigDecimal toBalance = lockedBalances.get(toAccountNumber);
                if (fromBalance.compareTo(amount) < 0) {
                    throw new IllegalStateException("Insufficient balance.");
                }

                try (PreparedStatement updateStatement = connection.prepareStatement(updateSql);
                     PreparedStatement transactionStatement = connection.prepareStatement(transactionSql)) {
                    updateStatement.setBigDecimal(1, fromBalance.subtract(amount));
                    updateStatement.setString(2, fromAccountNumber);
                    updateStatement.executeUpdate();

                    updateStatement.setBigDecimal(1, toBalance.add(amount));
                    updateStatement.setString(2, toAccountNumber);
                    updateStatement.executeUpdate();

                    insertTransaction(transactionStatement, fromAccountNumber, "TRANSFER_OUT", amount, "Transfer to " + toAccountNumber);
                    insertTransaction(transactionStatement, toAccountNumber, "TRANSFER_IN", amount, "Transfer from " + fromAccountNumber);
                }

                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to transfer funds.", exception);
        }
    }

    @Override
    public BigDecimal getBalance(String accountNumber) {
        String sql = "SELECT balance FROM accounts WHERE account_number = ?";
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Account not found: " + accountNumber);
                }
                return resultSet.getBigDecimal("balance");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load account balance.", exception);
        }
    }

    @Override
    public List<Transaction> getTransactions(String accountNumber) {
        String sql = "SELECT account_number, transaction_type, amount, description, created_at FROM transactions WHERE account_number = ? ORDER BY created_at, id";
        List<Transaction> results = new ArrayList<>();
        try (Connection connection = newConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(new Transaction(
                            resultSet.getString("account_number"),
                            resultSet.getString("transaction_type"),
                            resultSet.getBigDecimal("amount"),
                            resultSet.getString("description"),
                            resultSet.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC)));
                }
            }
            return results;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load transaction history.", exception);
        }
    }

    private BigDecimal updateBalance(String accountNumber, BigDecimal delta, String transactionType, String description) {
        String selectSql = "SELECT balance FROM accounts WHERE account_number = ? FOR UPDATE";
        String updateSql = "UPDATE accounts SET balance = ? WHERE account_number = ?";
        String transactionSql = "INSERT INTO transactions (account_number, transaction_type, amount, description, created_at) VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = newConnection()) {
            connection.setAutoCommit(false);
            try {
                BigDecimal currentBalance = lockAndReadBalance(connection, selectSql, accountNumber);
                BigDecimal updatedBalance = currentBalance.add(delta);
                if (updatedBalance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalStateException("Insufficient balance.");
                }

                try (PreparedStatement updateStatement = connection.prepareStatement(updateSql);
                     PreparedStatement transactionStatement = connection.prepareStatement(transactionSql)) {
                    updateStatement.setBigDecimal(1, updatedBalance);
                    updateStatement.setString(2, accountNumber);
                    updateStatement.executeUpdate();

                    insertTransaction(transactionStatement, accountNumber, transactionType, delta.abs(), description);
                }

                connection.commit();
                return updatedBalance;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update account balance.", exception);
        }
    }

    private BigDecimal lockAndReadBalance(Connection connection, String sql, String accountNumber) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Account not found: " + accountNumber);
                }
                return resultSet.getBigDecimal("balance");
            }
        }
    }

    private Map<String, BigDecimal> lockBalancesInOrder(Connection connection, String sql, String firstAccountNumber, String secondAccountNumber) throws SQLException {
        List<String> orderedAccountNumbers = new ArrayList<>(List.of(firstAccountNumber, secondAccountNumber));
        orderedAccountNumbers.sort(String::compareTo);

        Map<String, BigDecimal> balances = new HashMap<>();
        for (String accountNumber : orderedAccountNumbers) {
            balances.put(accountNumber, lockAndReadBalance(connection, sql, accountNumber));
        }
        return balances;
    }

    private void insertTransaction(PreparedStatement statement, String accountNumber, String type, BigDecimal amount, String description) throws SQLException {
        statement.setString(1, accountNumber);
        statement.setString(2, type);
        statement.setBigDecimal(3, amount);
        statement.setString(4, description);
        statement.setTimestamp(5, Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()));
        statement.executeUpdate();
    }

    private void rollback(Connection connection, Exception originalException) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }

    private Connection newConnection() throws SQLException {
        if (username == null || username.isBlank()) {
            return DriverManager.getConnection(url);
        }
        return DriverManager.getConnection(url, username, password);
    }

    private void initializeSchema() {
        try (Connection connection = newConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : loadSchemaSql().split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize PostgreSQL schema.", exception);
        }
    }

    private String loadSchemaSql() {
        try (InputStream stream = PostgresBankRepository.class.getResourceAsStream("/schema.sql")) {
            if (stream == null) {
                throw new IllegalStateException("schema.sql resource is missing.");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read schema.sql.", exception);
        }
    }
}
