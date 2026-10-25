package com.axora.dao;

import com.axora.model.Account;
import com.axora.model.Transfer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** DAO for accounts and transfers between them. */
public interface AccountDao {
    List<Account> findAll();
    Optional<Account> findById(long id);
    /** The account used when a transaction has no explicit account. */
    Optional<Account> findDefault();
    long insert(Account a);
    boolean update(Account a);
    boolean delete(long id);

    /** Balance at end of asOf (inclusive); null = all dates. Opening + income - expense +/- transfers. */
    long balanceCents(long accountId, LocalDate asOf);

    long countTransactions(long accountId);
    long countTransfers(long accountId);
    long countRecurringRules(long accountId);

    long insertTransfer(Transfer t);
    boolean updateTransfer(Transfer t);
    boolean deleteTransfer(long id);
    Optional<Transfer> findTransfer(long id);
    /** Transfers touching the account, newest first. */
    List<Transfer> findTransfersForAccount(long accountId);
    List<Transfer> findAllTransfers();
}
