package com.sit.tracker.model;

/**
 * Fine-grained permissions enforced by the service layer.
 * Roles are mapped to permission sets in {@link com.sit.tracker.auth.AccessControl};
 * the console UI also uses them to hide menu entries a role cannot use.
 */
public enum Permission {
    // ---- Asset inventory ----
    VIEW_ASSETS,
    ADD_ASSET,
    EDIT_ASSET,
    UPDATE_ASSET_STATUS,
    RETIRE_ASSET,

    // ---- Fault reporting tickets ----
    REPORT_FAULT,
    VIEW_OWN_TICKETS,
    VIEW_ALL_TICKETS,
    ASSIGN_TICKETS,
    UPDATE_TICKET_STATUS,
    ADD_TICKET_NOTE,

    // ---- Scheduled maintenance ----
    VIEW_MAINTENANCE_SCHEDULE,
    SCHEDULE_MAINTENANCE,
    RECORD_MAINTENANCE,

    // ---- Reporting and administration ----
    VIEW_REPORTS,
    VIEW_AUDIT_LOG,
    MANAGE_USERS
}
