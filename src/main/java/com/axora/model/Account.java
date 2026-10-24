package com.axora.model;

/** A financial account (cash, bank, savings, credit). Balances are derived, never stored. */
public record Account(long id, String name, Type type, long openingBalanceCents,
                      String currency, String description, boolean isDefault) {

    public enum Type {
        CASH("Cash"), BANK("Bank"), SAVINGS("Savings"), CREDIT("Credit");
        private final String label;
        Type(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    @Override public String toString() { return name; }
}
