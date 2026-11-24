package com.sit.tracker.model;

/** Operational status of a laboratory asset in the inventory. */
public enum AssetStatus {
    IN_SERVICE("In Service"),
    UNDER_MAINTENANCE("Under Maintenance"),
    FAULTY("Faulty"),
    RETIRED("Retired");

    private final String label;

    AssetStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
