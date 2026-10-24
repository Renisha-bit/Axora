package com.axora.model;

import java.time.LocalDate;

/**
 * One line of an account's history. signedCents is the effect on this account's balance.
 * kind is INCOME, EXPENSE, TRANSFER_IN or TRANSFER_OUT.
 */
public record AccountActivity(LocalDate date, String kind, String label,
                              String description, long signedCents) {}
