package com.axora.model;

import java.time.LocalDate;

/** A savings target. Progress is derived from the goal's own contribution ledger. */
public record SavingsGoal(long id, String name, long targetCents, LocalDate targetDate,
                          String description, LocalDate startDate) {}
