package com.sit.tracker.service;

import com.sit.tracker.auth.AccessControl;
import com.sit.tracker.auth.AccessDeniedException;
import com.sit.tracker.auth.Session;
import com.sit.tracker.model.Asset;
import com.sit.tracker.model.MaintenancePlan;
import com.sit.tracker.model.MaintenanceRecord;
import com.sit.tracker.model.MaintenanceType;
import com.sit.tracker.model.Permission;
import com.sit.tracker.repository.DataStore;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled maintenance cycles and the completed-service history.
 */
public class MaintenanceService {

    private final DataStore store;
    private final AuditService audit;

    public MaintenanceService(DataStore store, AuditService audit) {
        this.store = store;
        this.audit = audit;
    }

    /** Create a recurring maintenance plan for an asset (administrator only). */
    public MaintenancePlan createPlan(Session session, String assetId, String taskDescription,
                                      int intervalDays, LocalDate nextDueDate) throws AccessDeniedException {
        AccessControl.check(session, Permission.SCHEDULE_MAINTENANCE);

        Asset asset = store.assets().findById(assetId.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No asset with id " + assetId));
        if (intervalDays <= 0) {
            throw new IllegalArgumentException("Interval must be a positive number of days.");
        }

        LocalDate lastService = nextDueDate.minusDays(intervalDays);
        MaintenancePlan plan = new MaintenancePlan(store.nextId("PLN"), asset.getId(),
                taskDescription.trim(), intervalDays, lastService, nextDueDate);
        store.plans().save(plan);
        audit.log(session, "PLAN_CREATED", "PLAN", plan.getId(),
                asset.getId() + " (" + asset.getName() + "): " + taskDescription.trim()
                        + " every " + intervalDays + " days");
        return plan;
    }

    /** Pause or resume a plan (administrator only). */
    public MaintenancePlan setActive(Session session, String planId, boolean active)
            throws AccessDeniedException {
        AccessControl.check(session, Permission.SCHEDULE_MAINTENANCE);

        MaintenancePlan plan = store.plans().findById(planId.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No plan with id " + planId));
        plan.setActive(active);
        audit.log(session, active ? "PLAN_RESUMED" : "PLAN_PAUSED", "PLAN", plan.getId(),
                plan.getTaskDescription() + " on " + plan.getAssetId());
        return plan;
    }

    /**
     * Record a completed service action. If the asset has an active plan, the
     * plan's cycle moves forward from the service date.
     */
    public MaintenanceRecord recordService(Session session, String assetId, LocalDate date,
                                           MaintenanceType type, String description,
                                           double durationHours) throws AccessDeniedException {
        AccessControl.check(session, Permission.RECORD_MAINTENANCE);

        Asset asset = store.assets().findById(assetId.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No asset with id " + assetId));
        if (durationHours < 0) {
            throw new IllegalArgumentException("Duration cannot be negative.");
        }

        MaintenanceRecord record = new MaintenanceRecord(store.nextId("REC"), asset.getId(),
                session.currentUser().getId(), date, type, description.trim(), durationHours);
        store.maintenanceRecords().save(record);

        activePlanFor(asset.getId()).ifPresent(plan -> plan.markServiced(date));

        audit.log(session, "MAINTENANCE_RECORDED", "RECORD", record.getId(),
                type.getLabel() + " on " + asset.getId() + " (" + asset.getName() + "), "
                        + durationHours + "h");
        return record;
    }

    /** The active plan for an asset, if any. */
    public Optional<MaintenancePlan> activePlanFor(String assetId) {
        return store.plans().findAll().stream()
                .filter(p -> p.getAssetId().equalsIgnoreCase(assetId) && p.isActive())
                .findFirst();
    }

    /** All plans sorted by next-due date. */
    public List<MaintenancePlan> allPlans(Session session) throws AccessDeniedException {
        AccessControl.check(session, Permission.VIEW_MAINTENANCE_SCHEDULE);
        return store.plans().findAll().stream()
                .sorted(Comparator.comparing(MaintenancePlan::getNextDueDate))
                .toList();
    }

    /** Plans due within the next {@code days} days (or already overdue). */
    public List<MaintenancePlan> dueWithin(Session session, int days) throws AccessDeniedException {
        AccessControl.check(session, Permission.VIEW_MAINTENANCE_SCHEDULE);
        LocalDate horizon = LocalDate.now().plusDays(days);
        return store.plans().findAll().stream()
                .filter(p -> p.isActive() && !p.getNextDueDate().isAfter(horizon))
                .sorted(Comparator.comparing(MaintenancePlan::getNextDueDate))
                .toList();
    }

    /** Overdue plans (next due date in the past). */
    public List<MaintenancePlan> overduePlans(Session session) throws AccessDeniedException {
        AccessControl.check(session, Permission.VIEW_MAINTENANCE_SCHEDULE);
        return store.plans().findAll().stream()
                .filter(p -> p.isOverdue(LocalDate.now()))
                .sorted(Comparator.comparing(MaintenancePlan::getNextDueDate))
                .toList();
    }

    /** Full maintenance history for one asset, newest first. */
    public List<MaintenanceRecord> historyFor(Session session, String assetId)
            throws AccessDeniedException {
        AccessControl.check(session, Permission.VIEW_MAINTENANCE_SCHEDULE);
        return store.maintenanceRecords().findAll().stream()
                .filter(r -> r.getAssetId().equalsIgnoreCase(assetId.trim()))
                .sorted(Comparator.comparing(MaintenanceRecord::getDate).reversed())
                .toList();
    }
}
