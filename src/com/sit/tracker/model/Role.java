package com.sit.tracker.model;

/**
 * The three user roles supported by the tracker's role-based access control (RBAC).
 * Each role maps to a set of {@link Permission}s in
 * {@link com.sit.tracker.auth.AccessControl}.
 */
public enum Role {
    GENERAL_USER("General User"),
    TECHNICIAN("Technician"),
    FACILITY_ADMIN("Facility Administrator");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
