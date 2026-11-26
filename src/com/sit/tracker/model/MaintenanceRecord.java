package com.sit.tracker.model;

import com.sit.tracker.repository.Identifiable;

import java.time.LocalDate;

/**
 * One completed maintenance action on an asset (planned service, repair,
 * inspection or calibration). Together these records form the asset's
 * maintenance history.
 */
public class MaintenanceRecord implements Identifiable<String> {
    private final String id;            // e.g. REC-001
    private final String assetId;
    private final String technicianId;  // User.id of who performed the work
    private final LocalDate date;
    private final MaintenanceType type;
    private final String description;
    private final double durationHours;

    public MaintenanceRecord(String id, String assetId, String technicianId, LocalDate date,
                             MaintenanceType type, String description, double durationHours) {
        this.id = id;
        this.assetId = assetId;
        this.technicianId = technicianId;
        this.date = date;
        this.type = type;
        this.description = description;
        this.durationHours = durationHours;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getAssetId() {
        return assetId;
    }

    public String getTechnicianId() {
        return technicianId;
    }

    public LocalDate getDate() {
        return date;
    }

    public MaintenanceType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public double getDurationHours() {
        return durationHours;
    }
}
