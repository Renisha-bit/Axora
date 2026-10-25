package com.axora.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Domain entity for a single financial transaction.
 * Amount is stored internally as integer cents to avoid floating-point issues.
 */
public class Transaction {

    public enum Type { INCOME, EXPENSE }

    private long         id;
    private Type         type;
    private long         amountCents;   // positive integer, stored as paise (₹ × 100)
    private long         categoryId;
    private String       categoryName;  // denormalized for display
    private LocalDate    txDate;
    private String       description;
    private String       merchant;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Transaction() {}

    // --- Constructors for new (unsaved) transactions ---
    public Transaction(Type type, long amountCents, long categoryId,
                       LocalDate txDate, String description, String merchant) {
        this.type        = type;
        this.amountCents = amountCents;
        this.categoryId  = categoryId;
        this.txDate      = txDate;
        this.description = description;
        this.merchant    = merchant;
    }

    // --- Amount helpers ---

    /** Returns the amount as a BigDecimal in rupees. */
    public BigDecimal getAmount() {
        return BigDecimal.valueOf(amountCents, 2);
    }

    /** Sets amount from a rupee-valued BigDecimal. */
    public void setAmount(BigDecimal rupees) {
        this.amountCents = rupees.movePointRight(2).longValueExact();
    }

    // --- Getters / Setters ---

    public long getId()                        { return id; }
    public void setId(long id)                 { this.id = id; }

    public Type getType()                      { return type; }
    public void setType(Type type)             { this.type = type; }

    public long getAmountCents()               { return amountCents; }
    public void setAmountCents(long c)         { this.amountCents = c; }

    public long getCategoryId()                { return categoryId; }
    public void setCategoryId(long id)         { this.categoryId = id; }

    public String getCategoryName()            { return categoryName; }
    public void setCategoryName(String n)      { this.categoryName = n; }

    public LocalDate getTxDate()               { return txDate; }
    public void setTxDate(LocalDate d)         { this.txDate = d; }

    public String getDescription()             { return description; }
    public void setDescription(String s)       { this.description = s; }

    public String getMerchant()                { return merchant; }
    public void setMerchant(String s)          { this.merchant = s; }

    public LocalDateTime getCreatedAt()        { return createdAt; }
    public void setCreatedAt(LocalDateTime dt) { this.createdAt = dt; }

    public LocalDateTime getUpdatedAt()        { return updatedAt; }
    public void setUpdatedAt(LocalDateTime dt) { this.updatedAt = dt; }

    @Override
    public String toString() {
        return "Transaction{id=%d, type=%s, amount=₹%s, date=%s}"
            .formatted(id, type, getAmount().toPlainString(), txDate);
    }
}
