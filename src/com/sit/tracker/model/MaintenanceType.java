package com.sit.tracker.model;

/** Category of a completed maintenance action, used in the maintenance history. */
public enum MaintenanceType {
    PLANNED_SERVICE("Planned Service"),
    REPAIR("Repair"),
    INSPECTION("Inspection"),
    CALIBRATION("Calibration");

    private final String label;

    MaintenanceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
