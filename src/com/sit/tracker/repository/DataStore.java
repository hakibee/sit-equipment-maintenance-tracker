package com.sit.tracker.repository;

import com.sit.tracker.model.Asset;
import com.sit.tracker.model.Ticket;

import java.util.HashMap;
import java.util.Map;

/**
 * The demo database: one repository per entity type plus a central,
 * human-readable id generator (AST-001, TKT-002, ...). Only this class knows
 * how entities are stored and how ids are minted; the console front-end
 * talks to entities through the {@link Repository} interface exclusively.
 */
public class DataStore {

    private final Repository<String, Asset> assets = new InMemoryRepository<>();
    private final Repository<String, Ticket> tickets = new InMemoryRepository<>();

    private final Map<String, Integer> idCounters = new HashMap<>();

    /** Sequential, zero-padded ids per prefix: nextId("AST") -> "AST-001". */
    public synchronized String nextId(String prefix) {
        int n = idCounters.merge(prefix, 1, Integer::sum);
        return String.format("%s-%03d", prefix, n);
    }

    public Repository<String, Asset> assets() {
        return assets;
    }

    public Repository<String, Ticket> tickets() {
        return tickets;
    }
}
