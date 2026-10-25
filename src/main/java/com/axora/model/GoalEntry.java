package com.axora.model;

import java.time.LocalDate;

/** One immutable ledger line of a savings goal. */
public record GoalEntry(long id, long goalId, Type type, long amountCents,
                        LocalDate date, String note) {
    public enum Type { CONTRIBUTION, WITHDRAWAL }
    public long signedCents() { return type == Type.CONTRIBUTION ? amountCents : -amountCents; }
}
