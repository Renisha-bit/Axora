package com.axora;

import com.axora.validation.TransactionValidator;
import com.axora.model.Transaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionValidatorTest {

    @Test
    void validInputProducesNoErrors() {
        List<String> errors = TransactionValidator.validate(
            Transaction.Type.EXPENSE, "1500.00", 1L, LocalDate.now(), "Lunch");
        assertTrue(errors.isEmpty());
    }

    @Test
    void nullTypeIsInvalid() {
        List<String> errors = TransactionValidator.validate(
            null, "100", 1L, LocalDate.now(), "");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("type")));
    }

    @Test
    void zeroAmountIsInvalid() {
        List<String> errors = TransactionValidator.validate(
            Transaction.Type.INCOME, "0", 1L, LocalDate.now(), "");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("greater than zero")));
    }

    @Test
    void negativeAmountIsInvalid() {
        List<String> errors = TransactionValidator.validate(
            Transaction.Type.INCOME, "-50", 1L, LocalDate.now(), "");
        assertFalse(errors.isEmpty());
    }

    @Test
    void fractionalCentAmountIsInvalid() {
        List<String> errors = TransactionValidator.validate(
            Transaction.Type.EXPENSE, "1.001", 1L, LocalDate.now(), "");
        assertTrue(errors.stream().anyMatch(e -> e.contains("fractions of a cent")));
    }

    @Test
    void trailingZeroBeyondCentsIsAllowed() {
        List<String> errors = TransactionValidator.validate(
            Transaction.Type.EXPENSE, "1.000", 1L, LocalDate.now(), "");
        assertTrue(errors.isEmpty());
        assertEquals(100L, TransactionValidator.parseCents("1.000"));
    }

    @Test
    void futureDataIsInvalid() {
        List<String> errors = TransactionValidator.validate(
            Transaction.Type.EXPENSE, "100", 1L, LocalDate.now().plusDays(5), "");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("future")));
    }

    @Test
    void parseCentsCorrect() {
        assertEquals(150000L, TransactionValidator.parseCents("1500.00"));
        assertEquals(100L,    TransactionValidator.parseCents("1.00"));
        assertEquals(1L,      TransactionValidator.parseCents("0.01"));
    }
}
