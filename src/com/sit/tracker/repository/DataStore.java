package com.sit.tracker.repository;

import com.sit.tracker.model.Asset;
import com.sit.tracker.model.AuditLog;
import com.sit.tracker.model.MaintenancePlan;
import com.sit.tracker.model.MaintenanceRecord;
import com.sit.tracker.model.Ticket;

import java.util.HashMap;
import java.util.Map;

/**
 * The demo database: one repository per entity type plus a central,
 * human-readable id generator (AST-001, TKT-002, ...). Services receive the
 * store via constructor injection; only this class knows how entities are
 * stored and how ids are minted.
 */
public class DataStore {

    private final Repository<String, Asset> assets = new InMemoryRepository<>();
    private final Repository<String, Ticket> tickets = new InMemoryRepository<>();
    private final Repository<String, MaintenancePlan> plans = new InMemoryRepository<>();
    private final Repository<String, MaintenanceRecord> maintenanceRecords = new InMemoryRepository<>();
    private final Repository<String, AuditLog> auditLogs = new InMemoryRepository<>();

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

    public Repository<String, MaintenancePlan> plans() {
        return plans;
    }

    public Repository<String, MaintenanceRecord> maintenanceRecords() {
        return maintenanceRecords;
    }

    public Repository<String, AuditLog> auditLogs() {
        return auditLogs;
    }
}
