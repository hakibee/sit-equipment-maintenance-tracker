package com.sit.tracker.model;

import java.time.LocalDateTime;

/**
 * A fault-report ticket raised against an asset, with the reporter recorded
 * as a plain name (proper user accounts arrive with the auth layer later).
 */
public class Ticket {
    private final String id;              // e.g. TKT-001
    private final String assetId;
    private final Urgency urgency;
    private final String title;
    private final String description;
    private final String reportedBy;      // plain name for now
    private TicketStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime resolvedAt;     // null until resolved; used for MTTR later

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
    }

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
}
