package com.sit.tracker.service;

import com.sit.tracker.auth.Session;
import com.sit.tracker.model.AuditLog;
import com.sit.tracker.model.User;
import com.sit.tracker.repository.DataStore;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Records and serves the institute-wide audit trail. Every state-changing
 * service action writes an entry here; facility administrators can view the
 * log from the console UI.
 */
public class AuditService {

    private final DataStore store;

    public AuditService(DataStore store) {
        this.store = store;
    }

    /** Log an action performed by the session's user (or by "SYSTEM"). */
    public AuditLog log(Session session, String action, String entityType, String entityId, String details) {
        User user = session == null ? null : session.currentUser();
        String actorId = user == null ? "SYSTEM" : user.getId();
        String actorRole = user == null ? "SYSTEM" : user.getRole().getLabel();
        return log(actorId, actorRole, action, entityType, entityId, details);
    }

    /** Log an action attributed to an explicit actor (e.g. failed logins). */
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
