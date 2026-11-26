package com.sit.tracker.service;

import com.sit.tracker.model.Asset;
import com.sit.tracker.model.AssetStatus;
import com.sit.tracker.model.Ticket;
import com.sit.tracker.model.TicketStatus;
import com.sit.tracker.model.Urgency;
import com.sit.tracker.repository.DataStore;
import com.sit.tracker.repository.Repository;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fault-report ticket workflow: reporting, assignment, status transitions
 * (Reported -> Under Review -> In Repair -> Resolved, reopen supported) and
 * notes. The lifecycle guard lives here so no caller can put a ticket into
 * an illegal state. (Permission checks arrive with the auth layer.)
 */
public class TicketService {

    /**
     * Allowed status transitions. Resolved -> Under Review is the "reopen"
     * path used when a fault recurs after resolution.
     */
    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<TicketStatus, Set<TicketStatus>> m = new EnumMap<>(TicketStatus.class);
        m.put(TicketStatus.REPORTED, EnumSet.of(TicketStatus.UNDER_REVIEW));
        m.put(TicketStatus.UNDER_REVIEW, EnumSet.of(TicketStatus.IN_REPAIR, TicketStatus.REPORTED));
        m.put(TicketStatus.IN_REPAIR, EnumSet.of(TicketStatus.RESOLVED, TicketStatus.UNDER_REVIEW));
        m.put(TicketStatus.RESOLVED, EnumSet.of(TicketStatus.UNDER_REVIEW));
        ALLOWED_TRANSITIONS = java.util.Collections.unmodifiableMap(m);
    }

    /** Valid target statuses from {@code current}, for UI prompts and checks. */
    public static Set<TicketStatus> allowedTransitions(TicketStatus current) {
        return ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
    }

    private final DataStore store;
    private final AuditService audit;
    private final AssetService assetService;
    private final Repository<String, Ticket> tickets;

    public TicketService(DataStore store, AuditService audit, AssetService assetService) {
        this.store = store;
        this.audit = audit;
        this.assetService = assetService;
        this.tickets = store.tickets();
    }

    /** File a new fault report. The asset is flagged FAULTY. */
    public Ticket reportFault(String reporterName, String assetId, Urgency urgency,
                              String title, String description) {
        Asset asset = assetService.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("No asset with id " + assetId));
        if (asset.getStatus() == AssetStatus.RETIRED) {
            throw new IllegalArgumentException(asset.getId() + " (" + asset.getName()
                    + ") is retired; a fault report cannot be filed against it.");
        }

        Ticket ticket = new Ticket(store.nextId("TKT"), asset.getId(), urgency,
                title.trim(), description.trim(), reporterName.trim(), LocalDateTime.now());
        tickets.save(ticket);

        assetService.markFaultyInternal(reporterName, asset.getId());
        audit.log(reporterName, "Staff", "TICKET_REPORTED", "TICKET", ticket.getId(),
                asset.getId() + " (" + asset.getName() + "): " + urgency.getLabel() + " - " + ticket.getTitle());
        return ticket;
    }

    /** All tickets, oldest first. */
    public List<Ticket> allTickets() {
        return tickets.findAll();
    }

    /** Open (not resolved) tickets, oldest first. */
    public List<Ticket> openTickets() {
        return tickets.findAll().stream()
                .filter(t -> t.getStatus() != TicketStatus.RESOLVED)
                .toList();
    }

    /** Load one ticket (case-insensitive id). */
    public Ticket viewTicket(String ticketId) {
        return tickets.findById(ticketId.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No ticket with id " + ticketId));
    }

    /** Assign a ticket to a technician (recorded by name until users exist). */
    public Ticket assign(String ticketId, String technicianName) {
        Ticket ticket = viewTicket(ticketId);
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new IllegalStateException("Ticket " + ticket.getId() + " is already resolved; reopen it first.");
        }

        ticket.setAssignedTo(technicianName.trim());
        ticket.addHistory("Assigned to " + technicianName.trim());
        audit.log("SYSTEM", "Staff", "TICKET_ASSIGNED", "TICKET", ticket.getId(),
                "Assigned to " + technicianName.trim());
        return ticket;
    }

    /**
     * Move a ticket to another status with a mandatory note, enforcing the
     * lifecycle defined in {@link #ALLOWED_TRANSITIONS}.
     */
    public Ticket transition(String ticketId, TicketStatus target, String note) {
        Ticket ticket = viewTicket(ticketId);

        TicketStatus current = ticket.getStatus();
        Set<TicketStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new IllegalStateException(String.format(
                    "Illegal transition %s -> %s. From '%s' you may move to: %s.",
                    current.getLabel(), target.getLabel(), current.getLabel(),
                    allowed.isEmpty() ? "(none)" : allowed.stream()
                            .map(TicketStatus::getLabel).reduce((a, b) -> a + ", " + b).orElse("")));
        }

        ticket.setStatus(target);
        if (target == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(LocalDateTime.now());
        }
        ticket.addHistory(String.format("Status: %s -> %s (%s)",
                current.getLabel(), target.getLabel(), note == null || note.isBlank() ? "no note" : note.trim()));
        audit.log("SYSTEM", "Staff", "TICKET_STATUS_CHANGED", "TICKET", ticket.getId(),
                current.getLabel() + " -> " + target.getLabel());
        return ticket;
    }

    /** Add a working note to a ticket. */
    public Ticket addNote(String ticketId, String authorName, String note) {
        Ticket ticket = viewTicket(ticketId);
        ticket.addHistory("Note (" + authorName.trim() + "): " + note.trim());
        audit.log("SYSTEM", "Staff", "TICKET_NOTE_ADDED", "TICKET", ticket.getId(), note.trim());
        return ticket;
    }
}
