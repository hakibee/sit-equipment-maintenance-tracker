package com.sit.tracker.model;

import com.sit.tracker.repository.Identifiable;

import java.time.LocalDateTime;

/**
 * Institute-wide audit trail entry: who did what, to which entity, and when.
 * Written by every service action (login, asset changes, ticket workflow,
 * maintenance, administration). Actor is "SYSTEM" for events without a
 * logged-in user (e.g. failed login attempts on an unknown account).
 */
public class AuditLog implements Identifiable<String> {
    private final String id;          // e.g. LOG-0001
    private final LocalDateTime timestamp;
    private final String actorId;     // User.id or "SYSTEM"
    private final String actorRole;   // role label or "SYSTEM"
    private final String action;      // e.g. "TICKET_STATUS_CHANGED"
    private final String entityType;  // e.g. "TICKET"
    private final String entityId;    // e.g. "TKT-003"
    private final String details;

    public AuditLog(String id, LocalDateTime timestamp, String actorId, String actorRole,
                    String action, String entityType, String entityId, String details) {
        this.id = id;
        this.timestamp = timestamp;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
    }

    @Override
    public String getId() {
        return id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getDetails() {
        return details;
    }
}
