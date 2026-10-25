package com.axora.model;

import java.time.LocalDate;

/** A scheduled-but-not-yet-generated occurrence of a recurring rule. */
public record UpcomingOccurrence(RecurringRule rule, LocalDate date) {}
