package com.axora.model;

/** An account together with its balance at some point in time. */
public record AccountBalance(Account account, long balanceCents) {}
