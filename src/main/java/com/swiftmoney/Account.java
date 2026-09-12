package com.swiftmoney;

import java.math.BigDecimal;

public record Account(String accountNumber, String holderName, BigDecimal balance) {
}
