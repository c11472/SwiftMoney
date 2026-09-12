package com.swiftmoney;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record Transaction(String accountNumber, String type, BigDecimal amount, String description, OffsetDateTime timestamp) {
}
