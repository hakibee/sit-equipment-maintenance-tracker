package com.sit.tracker.service;

import com.sit.tracker.model.AuditLog;
import com.sit.tracker.repository.DataStore;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Records and serves the institute-wide audit trail. Every state-changing
 * service action writes an entry here; the log can be viewed from the console
 * UI. (Role-restricted access arrives with the auth layer.)
 */
public class AuditService {

    private final DataStore store;

    public AuditService(DataStore store) {
        this.store = store;
    }

    /**
     * Log an action attributed to an explicit actor. Until proper user
     * accounts exist, actors are plain names and the role is the generic
     * "Staff".
     */
    public AuditLog log(String actorId, String actorRole, String action,
                        String entityType, String entityId, String details) {
        AuditLog entry = new AuditLog(store.nextId("LOG"), LocalDateTime.now(),
                actorId, actorRole, action, entityType, entityId, details);
        store.auditLogs().save(entry);
        return entry;
    }

    /** All entries, oldest first (repositories keep insertion order). */
    public List<AuditLog> allEntries() {
        return store.auditLogs().findAll();
    }

    /** The most recent {@code limit} entries, newest first. */
    public List<AuditLog> recentEntries(int limit) {
        List<AuditLog> all = allEntries();
        int from = Math.max(0, all.size() - limit);
        List<AuditLog> recent = new java.util.ArrayList<>(all.subList(from, all.size()));
        java.util.Collections.reverse(recent);
        return recent;
    }
}
