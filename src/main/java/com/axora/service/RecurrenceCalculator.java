package com.axora.service;

import com.axora.model.RecurringRule.Frequency;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure schedule arithmetic. The n-th occurrence is always computed from the ORIGINAL start date
 * (start + n months), never from the previous occurrence, so a rule starting on Jan 31 yields
 * Jan 31, Feb 28/29, Mar 31, Apr 30 … instead of drifting to the 28th forever.
 */
public final class RecurrenceCalculator {
    private RecurrenceCalculator() {}

    public static LocalDate occurrence(LocalDate start, Frequency f, long n) {
        return switch (f) {
            case DAILY   -> start.plusDays(n);
            case WEEKLY  -> start.plusWeeks(n);
            case MONTHLY -> start.plusMonths(n);   // clamps to month end
            case YEARLY  -> start.plusYears(n);    // Feb 29 -> Feb 28 in non-leap years
        };
    }

    /** All occurrences in [from, to] (inclusive), never after endDate (nullable); at most max entries. */
    public static List<LocalDate> between(LocalDate start, Frequency f, LocalDate from, LocalDate to,
                                          LocalDate endDate, int max) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate upper = (endDate != null && endDate.isBefore(to)) ? endDate : to;
        if (upper.isBefore(from) || upper.isBefore(start)) return out;
        long n = firstIndexOnOrAfter(start, f, from);
        while (out.size() < max) {
            LocalDate d = occurrence(start, f, n++);
            if (d.isAfter(upper)) break;
            out.add(d);
        }
        return out;
    }

    /** First occurrence strictly after the given date, or null if the rule has ended by then. */
    public static LocalDate nextAfter(LocalDate start, Frequency f, LocalDate after, LocalDate endDate) {
        LocalDate d = occurrence(start, f, firstIndexOnOrAfter(start, f, after.plusDays(1)));
        return (endDate != null && d.isAfter(endDate)) ? null : d;
    }

    static long firstIndexOnOrAfter(LocalDate start, Frequency f, LocalDate from) {
        if (!from.isAfter(start)) return 0;
        long est = switch (f) {
            case DAILY   -> ChronoUnit.DAYS.between(start, from);
            case WEEKLY  -> ChronoUnit.WEEKS.between(start, from);
            case MONTHLY -> ChronoUnit.MONTHS.between(start, from);
            case YEARLY  -> ChronoUnit.YEARS.between(start, from);
        };
        long n = Math.max(0, est - 1);
        while (occurrence(start, f, n).isBefore(from)) n++;
        return n;
    }
}
