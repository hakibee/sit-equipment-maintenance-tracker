package com.sit.tracker.model;

import com.sit.tracker.repository.Identifiable;

import java.time.LocalDate;

/**
 * A recurring (scheduled) maintenance cycle for one asset, e.g.
 * "calibrate every 180 days". Completing a service against the plan moves
 * {@code lastServiceDate} forward and pushes {@code nextDueDate} by the
 * interval.
 */
public class MaintenancePlan implements Identifiable<String> {
    private final String id;             // e.g. PLN-001
    private final String assetId;
    private final String taskDescription;
    private final int intervalDays;
    private LocalDate lastServiceDate;   // may be null for a brand-new plan
    private LocalDate nextDueDate;
    private boolean active;

    public MaintenancePlan(String id, String assetId, String taskDescription,
                           int intervalDays, LocalDate lastServiceDate, LocalDate nextDueDate) {
        this.id = id;
        this.assetId = assetId;
        this.taskDescription = taskDescription;
        this.intervalDays = intervalDays;
        this.lastServiceDate = lastServiceDate;
        this.nextDueDate = nextDueDate;
        this.active = true;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getAssetId() {
        return assetId;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public LocalDate getLastServiceDate() {
        return lastServiceDate;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** Called when a service is completed: pushes the cycle forward. */
    public void markServiced(LocalDate serviceDate) {
        this.lastServiceDate = serviceDate;
        this.nextDueDate = serviceDate.plusDays(intervalDays);
    }

    public boolean isOverdue(LocalDate today) {
        return active && nextDueDate != null && nextDueDate.isBefore(today);
    }
}
