package com.sit.tracker.model;

/**
 * Lifecycle states of a fault-report ticket:
 * Reported -> Under Review -> In Repair -> Resolved (with reopen support).
 * Allowed transitions are defined in
 * {@link com.sit.tracker.service.TicketService}.
 */
public enum TicketStatus {
    REPORTED("Reported"),
    UNDER_REVIEW("Under Review"),
    IN_REPAIR("In Repair"),
    RESOLVED("Resolved");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
