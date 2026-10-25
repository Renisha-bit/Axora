package com.axora;

import com.axora.model.RecurringRule.Frequency;
import com.axora.service.RecurrenceCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecurrenceCalculatorTest {

    private static LocalDate d(int y, int m, int day) { return LocalDate.of(y, m, day); }

    @Test
    void monthlyOnThe31stClampsToMonthEndWithoutDrifting() {
        List<LocalDate> r = RecurrenceCalculator.between(d(2026, 1, 31), Frequency.MONTHLY,
            d(2026, 1, 1), d(2026, 5, 31), null, 100);
        assertEquals(List.of(d(2026,1,31), d(2026,2,28), d(2026,3,31), d(2026,4,30), d(2026,5,31)), r);
    }

    @Test
    void monthlyOn31stInLeapYearUsesFeb29() {
        assertEquals(d(2028, 2, 29), RecurrenceCalculator.occurrence(d(2027, 12, 31), Frequency.MONTHLY, 2));
    }

    @Test
    void yearlyOnLeapDayFallsBackToFeb28() {
        assertEquals(d(2025, 2, 28), RecurrenceCalculator.occurrence(d(2024, 2, 29), Frequency.YEARLY, 1));
        assertEquals(d(2028, 2, 29), RecurrenceCalculator.occurrence(d(2024, 2, 29), Frequency.YEARLY, 4));
    }

    @Test
    void weeklyAndDaily() {
        assertEquals(List.of(d(2026,10,1), d(2026,10,8), d(2026,10,15)),
            RecurrenceCalculator.between(d(2026,10,1), Frequency.WEEKLY, d(2026,10,1), d(2026,10,20), null, 100));
        assertEquals(3, RecurrenceCalculator.between(d(2026,10,1), Frequency.DAILY, d(2026,10,5), d(2026,10,7), null, 100).size());
    }

    @Test
    void rangeStartingMidScheduleOnlyReturnsOccurrencesInsideIt() {
        List<LocalDate> r = RecurrenceCalculator.between(d(2026,1,15), Frequency.MONTHLY, d(2026,3,1), d(2026,4,30), null, 100);
        assertEquals(List.of(d(2026,3,15), d(2026,4,15)), r);
    }

    @Test
    void endDateIsInclusiveAndStopsSchedule() {
        List<LocalDate> r = RecurrenceCalculator.between(d(2026,1,10), Frequency.MONTHLY, d(2026,1,1), d(2026,12,31), d(2026,3,10), 100);
        assertEquals(List.of(d(2026,1,10), d(2026,2,10), d(2026,3,10)), r);
    }

    @Test
    void nothingBeforeStartAndEmptyWhenRangeBeforeStart() {
        assertTrue(RecurrenceCalculator.between(d(2026,6,1), Frequency.DAILY, d(2026,1,1), d(2026,5,31), null, 100).isEmpty());
    }

    @Test
    void maxCapsResults() {
        assertEquals(10, RecurrenceCalculator.between(d(2000,1,1), Frequency.DAILY, d(2000,1,1), d(2026,1,1), null, 10).size());
    }

    @Test
    void nextAfterSkipsToFutureAndRespectsEnd() {
        assertEquals(d(2026,11,15), RecurrenceCalculator.nextAfter(d(2026,1,15), Frequency.MONTHLY, d(2026,10,22), null));
        assertNull(RecurrenceCalculator.nextAfter(d(2026,1,15), Frequency.MONTHLY, d(2026,10,22), d(2026,10,31)));
    }
}
