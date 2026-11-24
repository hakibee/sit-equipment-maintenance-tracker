package com.sit.tracker.model;

/**
 * Urgency of a fault report, with the institute's target first-response time.
 * The targets are advisory in this demo build (used in reports and prompts),
 * not enforced by a scheduler.
 */
public enum Urgency {
    LOW("Low", 72),
    MEDIUM("Medium", 48),
    HIGH("High", 24),
    CRITICAL("Critical", 8);

    private final String label;
    private final int targetResponseHours;

    Urgency(String label, int targetResponseHours) {
        this.label = label;
        this.targetResponseHours = targetResponseHours;
    }

    public String getLabel() {
        return label;
    }

    public int getTargetResponseHours() {
        return targetResponseHours;
    }
}
