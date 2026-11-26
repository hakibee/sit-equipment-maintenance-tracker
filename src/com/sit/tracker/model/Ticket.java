package com.sit.tracker.model;

import com.sit.tracker.repository.Identifiable;

import java.time.LocalDateTime;

/**
 * A fault-report ticket raised against an asset, with the reporter recorded
 * as a plain name (proper user accounts arrive with the auth layer later).
 *
 * <p>The ticket keeps its own running history (status changes, assignments and
 * notes) which is displayed when working the ticket; institute-wide audit
 * events are stored separately in {@link AuditLog}.</p>
 */
public class Ticket implements Identifiable<String> {
    private final String id;              // e.g. TKT-001
    private final String assetId;
    private final Urgency urgency;
    private final String title;
    private final String description;
    private final String reportedBy;      // plain name for now
    private String assignedTo;            // plain name, null until assigned
    private TicketStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime resolvedAt;     // null until resolved; used for MTTR
    private final java.util.List<String> history = new java.util.ArrayList<>();

    public Ticket(String id, String assetId, Urgency urgency, String title,
                  String description, String reportedBy, LocalDateTime createdAt) {
        this.id = id;
        this.assetId = assetId;
        this.urgency = urgency;
        this.title = title;
        this.description = description;
        this.reportedBy = reportedBy;
        this.status = TicketStatus.REPORTED;
        this.createdAt = createdAt;
        // build the initial history entry directly (constructors must not call
        // overridable methods)
        history.add(String.format("%s | Ticket created (status: Reported)",
                LocalDateTime.now().withSecond(0).withNano(0)));
    }

    @Override
    public String getId() {
        return id;
    }

    public String getAssetId() {
        return assetId;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    /** Append a timestamped line to the ticket's working history. */
    public void addHistory(String entry) {
        history.add(String.format("%s | %s",
                LocalDateTime.now().withSecond(0).withNano(0), entry));
    }

    /** Immutable view of the ticket history, oldest first. */
    public java.util.List<String> getHistory() {
        return java.util.Collections.unmodifiableList(history);
    }
}
