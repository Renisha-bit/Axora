package com.axora.dao;

/** Monthly income+expense totals for bar chart. */
public record MonthlyTotal(int year, int month, long incomeCents, long expenseCents) {}
