package com.axora.service;

import com.axora.model.Budget;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Pure date-window logic for budgets. Weeks run Monday–Sunday; months are calendar months. */
public final class BudgetPeriods {
    private BudgetPeriods() {}

    /** Inclusive [from, to] window of the budget that applies on the reference date. */
    public static LocalDate[] window(Budget b, LocalDate asOf) {
        return window(b.period(), b.startDate(), b.endDate(), asOf);
    }

    public static LocalDate[] window(Budget.Period p, LocalDate start, LocalDate end, LocalDate asOf) {
        return switch (p) {
            case WEEKLY -> {
                LocalDate mon = asOf.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new LocalDate[]{mon, mon.plusDays(6)};
            }
            case MONTHLY -> new LocalDate[]{asOf.withDayOfMonth(1), asOf.with(TemporalAdjusters.lastDayOfMonth())};
            case CUSTOM  -> new LocalDate[]{start, end};
        };
    }

    public static boolean overlaps(LocalDate s1, LocalDate e1, LocalDate s2, LocalDate e2) {
        return !s1.isAfter(e2) && !s2.isAfter(e1);
    }
}
