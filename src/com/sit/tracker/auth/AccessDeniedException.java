package com.sit.tracker.auth;

import com.sit.tracker.model.Permission;

/**
 * Thrown when a session attempts an action its role is not allowed to
 * perform, or when authentication fails. Caught by the console UI and shown
 * as a friendly message.
 */
public class AccessDeniedException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Null when the exception signals an authentication failure, not an RBAC denial. */
    private final Permission permission;

    public AccessDeniedException(Permission permission, String message) {
        super(message);
        this.permission = permission;
    }

    /** Convenience constructor for authentication failures (bad credentials). */
    public AccessDeniedException(String message) {
        this(null, message);
    }

    public Permission getPermission() {
        return permission;
    }
}
