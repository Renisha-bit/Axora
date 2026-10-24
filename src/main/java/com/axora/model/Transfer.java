package com.axora.model;

import java.time.LocalDate;

/** Movement of money between two of the user's own accounts. Never counted as income/expense. */
public record Transfer(long id, long fromAccountId, String fromAccountName,
                       long toAccountId, String toAccountName,
                       long amountCents, LocalDate date, String description) {}
