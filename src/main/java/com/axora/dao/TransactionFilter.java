package com.axora.dao;

import com.axora.model.Transaction;

import java.time.LocalDate;

/**
 * Immutable filter for transaction queries.
 */
public final class TransactionFilter {

    private final LocalDate        from;
    private final LocalDate        to;
    private final Transaction.Type type;    // null = both
    private final Long             categoryId;
    private final String           keyword;
    private final Long             minAmountCents;
    private final Long             maxAmountCents;
    private final Long             accountId;

    private TransactionFilter(Builder b) {
        this.from           = b.from;
        this.to             = b.to;
        this.type           = b.type;
        this.categoryId     = b.categoryId;
        this.keyword        = b.keyword;
        this.minAmountCents = b.minAmountCents;
        this.maxAmountCents = b.maxAmountCents;
        this.accountId      = b.accountId;
    }

    public LocalDate        getFrom()           { return from; }
    public LocalDate        getTo()             { return to; }
    public Transaction.Type getType()           { return type; }
    public Long             getCategoryId()     { return categoryId; }
    public String           getKeyword()        { return keyword; }
    public Long             getMinAmountCents() { return minAmountCents; }
    public Long             getMaxAmountCents() { return maxAmountCents; }
    public Long             getAccountId()      { return accountId; }

    public boolean isEmpty() {
        return from == null && to == null && type == null
            && categoryId == null && (keyword == null || keyword.isBlank())
            && minAmountCents == null && maxAmountCents == null && accountId == null;
    }

    public static Builder builder()            { return new Builder(); }

    public static final class Builder {
        private LocalDate        from, to;
        private Transaction.Type type;
        private Long             categoryId;
        private String           keyword;
        private Long             minAmountCents, maxAmountCents;
        private Long             accountId;

        public Builder from(LocalDate d)            { this.from = d; return this; }
        public Builder to(LocalDate d)              { this.to = d; return this; }
        public Builder type(Transaction.Type t)     { this.type = t; return this; }
        public Builder categoryId(Long id)          { this.categoryId = id; return this; }
        public Builder keyword(String kw)           { this.keyword = kw; return this; }
        public Builder minAmountCents(Long v)       { this.minAmountCents = v; return this; }
        public Builder maxAmountCents(Long v)       { this.maxAmountCents = v; return this; }
        public Builder accountId(Long id)           { this.accountId = id; return this; }
        public TransactionFilter build()            { return new TransactionFilter(this); }
    }
}
