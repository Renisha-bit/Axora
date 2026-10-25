package com.axora.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A budget evaluated against actual expenses for one concrete window. */
public record BudgetStatus(Budget budget, LocalDate from, LocalDate to,
                           long spentCents, long remainingCents,
                           BigDecimal percentUsed, Level level) {

    public enum Level { OK, WARNING, EXCEEDED }
}
