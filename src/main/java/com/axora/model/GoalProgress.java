package com.axora.model;

import java.math.BigDecimal;

/** A goal evaluated at a date. percent is uncapped (may exceed 100 when over-saved). */
public record GoalProgress(SavingsGoal goal, long savedCents, long remainingCents,
                           BigDecimal percent, Status status) {
    public enum Status { ON_TRACK, BEHIND, COMPLETED, OVERDUE }
}
