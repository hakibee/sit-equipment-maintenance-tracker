package com.sit.tracker.service;

import com.sit.tracker.model.Asset;
import com.sit.tracker.model.AssetStatus;
import com.sit.tracker.model.MaintenancePlan;
import com.sit.tracker.model.Ticket;
import com.sit.tracker.model.TicketStatus;
import com.sit.tracker.model.Urgency;
import com.sit.tracker.repository.DataStore;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Summary reports: Mean Time To Repair (MTTR), open-ticket workload, asset
 * status overview and overdue maintenance. Read-only; the console UI gates
 * access with VIEW_REPORTS.
 *
 * <p>MTTR = average elapsed time from ticket creation to resolution, over all
 * resolved tickets, expressed in hours.</p>
 */
public class ReportService {

    private final DataStore store;

    public ReportService(DataStore store) {
        this.store = store;
    }

    /** Overall MTTR in hours; empty when no ticket has been resolved yet. */
    public OptionalDouble mttrHoursOverall() {
        return resolvedTickets().stream()
                .mapToLong(this::repairMinutes)
                .average();
    }

    /** MTTR in hours per urgency level; a level with no resolved tickets is absent. */
    public Map<Urgency, Double> mttrHoursByUrgency() {
        Map<Urgency, List<Ticket>> byUrgency = new EnumMap<>(Urgency.class);
        for (Ticket t : resolvedTickets()) {
            byUrgency.computeIfAbsent(t.getUrgency(), u -> new java.util.ArrayList<>()).add(t);
        }

        Map<Urgency, Double> result = new EnumMap<>(Urgency.class);
        byUrgency.forEach((urgency, list) -> {
            double avgHours = list.stream().mapToLong(this::repairMinutes).average().orElse(0) / 60.0;
            result.put(urgency, Math.round(avgHours * 10.0) / 10.0);
        });
        return result;
    }

    /** Number of resolved tickets the MTTR figure is based on. */
    public long resolvedTicketCount() {
        return resolvedTickets().size();
    }

    /** Open (not resolved) tickets, oldest first. */
    public List<Ticket> openTickets() {
        return store.tickets().findAll().stream()
                .filter(t -> t.getStatus() != TicketStatus.RESOLVED)
                .sorted(Comparator.comparing(Ticket::getCreatedAt))
                .toList();
    }

    /** Open tickets grouped by status. */
    public Map<TicketStatus, Integer> openTicketsByStatus() {
        Map<TicketStatus, Integer> counts = new EnumMap<>(TicketStatus.class);
        for (Ticket t : openTickets()) {
            counts.merge(t.getStatus(), 1, Integer::sum);
        }
        return counts;
    }

    /** Open tickets grouped by urgency. */
    public Map<Urgency, Integer> openTicketsByUrgency() {
        Map<Urgency, Integer> counts = new EnumMap<>(Urgency.class);
        for (Ticket t : openTickets()) {
            counts.merge(t.getUrgency(), 1, Integer::sum);
        }
        return counts;
    }

    /** All assets grouped by status. */
    public Map<AssetStatus, Integer> assetsByStatus() {
        Map<AssetStatus, Integer> counts = new EnumMap<>(AssetStatus.class);
        for (Asset a : store.assets().findAll()) {
            counts.merge(a.getStatus(), 1, Integer::sum);
        }
        return counts;
    }

    /** Overdue maintenance plans (next service date already passed). */
    public List<MaintenancePlan> overduePlans() {
        LocalDate today = LocalDate.now();
        return store.plans().findAll().stream()
                .filter(p -> p.isOverdue(today))
                .sorted(Comparator.comparing(MaintenancePlan::getNextDueDate))
                .toList();
    }

    private List<Ticket> resolvedTickets() {
        return store.tickets().findAll().stream()
                .filter(t -> t.getStatus() == TicketStatus.RESOLVED && t.getResolvedAt() != null)
                .toList();
    }

    private long repairMinutes(Ticket t) {
        return Duration.between(t.getCreatedAt(), t.getResolvedAt()).toMinutes();
    }
}
