package com.axora.service;

import com.axora.dao.AccountDao;
import com.axora.dao.TransactionDao;
import com.axora.dao.TransactionFilter;
import com.axora.dao.sqlite.DatabaseManager;
import com.axora.dao.sqlite.SQLiteAccountDao;
import com.axora.dao.sqlite.SQLiteTransactionDao;
import com.axora.model.*;
import com.axora.util.MoneyParser;

import java.time.LocalDate;
import java.util.*;

/** Accounts, derived balances and transfers. Balances are always computed, never stored. */
public class AccountService {

    private final AccountDao accountDao;
    private final TransactionDao txDao;

    public AccountService() { this(new SQLiteAccountDao(), new SQLiteTransactionDao()); }
    public AccountService(AccountDao accountDao, TransactionDao txDao) {
        this.accountDao = accountDao; this.txDao = txDao;
    }

    // ---- queries
    public List<Account> getAccounts() { return accountDao.findAll(); }

    public List<AccountBalance> getBalances(LocalDate asOf) {
        List<AccountBalance> out = new ArrayList<>();
        for (Account a : accountDao.findAll()) out.add(new AccountBalance(a, accountDao.balanceCents(a.id(), asOf)));
        return out;
    }

    /** Sum of balances grouped by currency (amounts in different currencies are never mixed). */
    public Map<String, Long> totalsByCurrency(LocalDate asOf) {
        Map<String, Long> m = new TreeMap<>();
        for (AccountBalance b : getBalances(asOf)) m.merge(b.account().currency(), b.balanceCents(), Long::sum);
        return m;
    }

    // ---- CRUD
    public long createAccount(String name, Account.Type type, String openingText, String currency, String description) {
        Account a = build(0, name, type, openingText, currency, description);
        ensureNameFree(a.name(), 0);
        return accountDao.insert(a);
    }

    public void updateAccount(long id, String name, Account.Type type, String openingText,
                              String currency, String description) {
        Account existing = accountDao.findById(id).orElseThrow(() -> new IllegalArgumentException("Account not found."));
        Account a = build(id, name, type, openingText, currency, description);
        ensureNameFree(a.name(), id);
        if (!a.currency().equals(existing.currency())
                && (accountDao.countTransfers(id) > 0)) {
            throw new IllegalArgumentException("Currency cannot be changed while the account has transfers.");
        }
        accountDao.update(a);
    }

    /** Deletes only an account with no history, so transactions are never orphaned. */
    public void deleteAccount(long id) {
        Account a = accountDao.findById(id).orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (accountDao.findAll().size() <= 1)
            throw new IllegalArgumentException("You must keep at least one account.");
        long tx = accountDao.countTransactions(id), tr = accountDao.countTransfers(id), rr = accountDao.countRecurringRules(id);
        if (tx + tr + rr > 0) {
            throw new IllegalArgumentException("'%s' cannot be deleted: it has %d transaction(s), %d transfer(s) and %d recurring rule(s). "
                .formatted(a.name(), tx, tr, rr) + "Move or delete those first.");
        }
        accountDao.delete(id);
    }

    private Account build(long id, String name, Account.Type type, String openingText, String currency, String description) {
        List<String> errors = new ArrayList<>();
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) errors.add("Account name is required.");
        if (n.length() > 60) errors.add("Account name must not exceed 60 characters.");
        if (type == null) errors.add("Account type is required.");
        String cur = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        try { Currency.getInstance(cur); } catch (IllegalArgumentException e) { errors.add("Currency must be a valid 3-letter code (e.g. INR)."); }
        if (description != null && description.length() > 500) errors.add("Description must not exceed 500 characters.");
        long opening = 0;
        if (openingText != null && !openingText.isBlank()) {
            try {
                opening = MoneyParser.parseSignedCents(openingText, "Opening balance");
                if (opening < 0 && type != Account.Type.CREDIT)
                    errors.add("Only credit accounts may have a negative opening balance.");
            } catch (IllegalArgumentException e) { errors.add(e.getMessage()); }
        }
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        return new Account(id, n, type, opening, cur, description == null ? "" : description.trim(), false);
    }

    private void ensureNameFree(String name, long selfId) {
        for (Account a : accountDao.findAll())
            if (a.id() != selfId && a.name().equalsIgnoreCase(name))
                throw new IllegalArgumentException("An account named '" + name + "' already exists.");
    }

    // ---- transfers
    public long createTransfer(long fromId, long toId, String amountText, LocalDate date, String description) {
        Transfer t = buildTransfer(0, fromId, toId, amountText, date, description);
        return accountDao.insertTransfer(t);
    }

    public void updateTransfer(long id, long fromId, long toId, String amountText, LocalDate date, String description) {
        accountDao.findTransfer(id).orElseThrow(() -> new IllegalArgumentException("Transfer not found."));
        accountDao.updateTransfer(buildTransfer(id, fromId, toId, amountText, date, description));
    }

    public void deleteTransfer(long id) { accountDao.deleteTransfer(id); }

    public List<Transfer> getTransfers() { return accountDao.findAllTransfers(); }

    private Transfer buildTransfer(long id, long fromId, long toId, String amountText, LocalDate date, String desc) {
        List<String> errors = new ArrayList<>();
        Account from = accountDao.findById(fromId).orElse(null);
        Account to   = accountDao.findById(toId).orElse(null);
        if (from == null) errors.add("Select the account to transfer from.");
        if (to == null)   errors.add("Select the account to transfer to.");
        if (from != null && to != null) {
            if (from.id() == to.id()) errors.add("Source and destination accounts must be different.");
            else if (!from.currency().equals(to.currency()))
                errors.add("Transfers between different currencies are not supported (no exchange rates).");
        }
        long cents = 0;
        try { cents = MoneyParser.parsePositiveCents(amountText, "Amount"); } catch (IllegalArgumentException e) { errors.add(e.getMessage()); }
        if (date == null) errors.add("Date is required.");
        else if (date.isAfter(LocalDate.now().plusDays(1))) errors.add("Date cannot be in the future.");
        if (desc != null && desc.length() > 500) errors.add("Description must not exceed 500 characters.");
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        return new Transfer(id, fromId, from.name(), toId, to.name(), cents, date, desc == null ? "" : desc.trim());
    }

    // ---- history
    /** Transactions and transfers touching the account, newest first. */
    public List<AccountActivity> history(long accountId, int limit) {
        List<AccountActivity> out = new ArrayList<>();
        for (Transaction t : txDao.findByFilter(TransactionFilter.builder().accountId(accountId).build())) {
            boolean inc = t.getType() == Transaction.Type.INCOME;
            out.add(new AccountActivity(t.getTxDate(), inc ? "INCOME" : "EXPENSE", t.getCategoryName(),
                t.getDescription(), inc ? t.getAmountCents() : -t.getAmountCents()));
        }
        for (Transfer t : accountDao.findTransfersForAccount(accountId)) {
            boolean in = t.toAccountId() == accountId;
            out.add(new AccountActivity(t.date(), in ? "TRANSFER_IN" : "TRANSFER_OUT",
                in ? "From " + t.fromAccountName() : "To " + t.toAccountName(),
                t.description(), in ? t.amountCents() : -t.amountCents()));
        }
        out.sort(Comparator.comparing(AccountActivity::date).reversed());
        return out.size() > limit ? out.subList(0, limit) : out;
    }
}
