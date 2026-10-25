package com.axora.validation;

import com.axora.model.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Validates transaction inputs before persistence. */
public final class TransactionValidator {

    private TransactionValidator() {}

    public static List<String> validate(Transaction.Type type,
                                        String amountText,
                                        Long categoryId,
                                        LocalDate date,
                                        String description) {
        List<String> errors = new ArrayList<>();

        if (type == null) {
            errors.add("Transaction type is required.");
        }

        if (amountText == null || amountText.isBlank()) {
            errors.add("Amount is required.");
        } else {
            try {
                BigDecimal amount = new BigDecimal(amountText.trim());
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add("Amount must be greater than zero.");
                }
                // Max 10 digits before decimal (avoid overflow)
                if (amount.precision() - amount.scale() > 10) {
                    errors.add("Amount is too large.");
                } else {
                    try {
                        amount.movePointRight(2).longValueExact();
                    } catch (ArithmeticException e) {
                        errors.add("Amount cannot include fractions of a cent.");
                    }
                }
            } catch (NumberFormatException e) {
                errors.add("Amount must be a valid number (e.g. 1500.00).");
            }
        }

        if (categoryId == null || categoryId <= 0) {
            errors.add("Please select a category.");
        }

        if (date == null) {
            errors.add("Date is required.");
        } else if (date.isAfter(LocalDate.now().plusDays(1))) {
            errors.add("Date cannot be in the future.");
        }

        if (description != null && description.length() > 500) {
            errors.add("Description must not exceed 500 characters.");
        }

        return errors;
    }

    /** Parses validated amount text to cents. */
    public static long parseCents(String amountText) {
        return new BigDecimal(amountText.trim())
                   .movePointRight(2)
                   .longValueExact();
    }
}
