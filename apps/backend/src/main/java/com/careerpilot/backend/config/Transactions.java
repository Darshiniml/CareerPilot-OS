package com.careerpilot.backend.config;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Programmatic transaction boundaries for services that call the AI model.
 *
 * <p>Model calls can take minutes (local CPU models), so they must run outside any transaction;
 * otherwise each in-flight call pins a pooled DB connection and a few concurrent requests exhaust
 * the pool. Such services read what they need, call the model with no transaction open, then
 * persist the result in a short transaction via {@link #run(Supplier)}.
 */
@Component
public class Transactions {

    private final TransactionTemplate template;

    public Transactions(TransactionTemplate template) {
        this.template = template;
    }

    private Transactions() {
        this.template = null;
    }

    /** Runs {@code work} in a new or the current transaction. */
    public <T> T run(Supplier<T> work) {
        return template == null ? work.get() : template.execute(status -> work.get());
    }

    /** For unit tests without a transaction manager: runs work inline. */
    public static Transactions inline() {
        return new Transactions();
    }
}
