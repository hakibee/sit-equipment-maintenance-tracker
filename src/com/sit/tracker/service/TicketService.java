package com.sit.tracker.service;

import com.sit.tracker.auth.AccessControl;
import com.sit.tracker.auth.AccessDeniedException;
import com.sit.tracker.auth.Session;
import com.sit.tracker.model.AssetStatus;
import com.sit.tracker.model.Permission;
import com.sit.tracker.model.Role;
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
 * (Reported -> Under Review -> In Repair -> Resolved, reopen supported),
 * notes and per-role visibility.
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

    /** File a new fault report (any logged-in role). The asset is flagged FAULTY. */
    public Ticket reportFault(Session session, String assetId, Urgency urgency,
                              String title, String description) throws AccessDeniedException {
        AccessControl.check(session, Permission.REPORT_FAULT);

        var asset = assetService.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("No asset with id " + assetId));
        if (asset.getStatus() == AssetStatus.RETIRED) {
            throw new IllegalArgumentException(asset.getId() + " (" + asset.getName()
                    + ") is retired; a fault report cannot be filed against it.");
        }

        Ticket ticket = new Ticket(store.nextId("TKT"), asset.getId(), urgency,
                title.trim(), description.trim(), session.currentUser().getId(), LocalDateTime.now());
        tickets.save(ticket);

        assetService.markFaultyInternal(session, asset.getId());
        audit.log(session, "TICKET_REPORTED", "TICKET", ticket.getId(),
                asset.getId() + " (" + asset.getName() + "): " + urgency.getLabel() + " - " + ticket.getTitle());
        return ticket;
    }

    /** Tickets reported by the current user. */
    public List<Ticket> myTickets(Session session) throws AccessDeniedException {
        AccessControl.check(session, Permission.VIEW_OWN_TICKETS);
        String me = session.currentUser().getId();
        return tickets.findAll().stream()
                .filter(t -> t.getReportedById().equals(me))
                .toList();
    }

    /** All tickets (technicians and administrators). */
    public List<Ticket> allTickets(Session session) throws AccessDeniedException {
        AccessControl.check(session, Permission.VIEW_ALL_TICKETS);
        return tickets.findAll();
    }

    /** Open (not resolved) tickets, oldest first. */
    public List<Ticket> openTickets(Session session) throws AccessDeniedException {
        AccessControl.check(session, Permission.VIEW_ALL_TICKETS);
        return tickets.findAll().stream()
                .filter(t -> t.getStatus() != TicketStatus.RESOLVED)
                .toList();
    }

    /**
     * Load one ticket with role-based visibility: anyone may see their own
     * tickets; viewing others' requires VIEW_ALL_TICKETS.
     */
    public Ticket viewTicket(Session session, String ticketId) throws AccessDeniedException {
        Ticket ticket = tickets.findById(ticketId.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No ticket with id " + ticketId));

        String me = session.currentUser().getId();
        if (!ticket.getReportedById().equals(me)) {
            AccessControl.check(session, Permission.VIEW_ALL_TICKETS);
        }
        return ticket;
    }

    /** Assign a ticket to a technician (technicians and administrators). */
    public Ticket assign(Session session, String ticketId, String technicianId)
            throws AccessDeniedException {
        AccessControl.check(session, Permission.ASSIGN_TICKETS);

        Ticket ticket = tickets.findById(ticketId.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No ticket with id " + ticketId));
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new IllegalStateException("Ticket " + ticket.getId() + " is already resolved; reopen it first.");
        }

        var technician = store.users().findById(technicianId.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + technicianId));
        if (technician.getRole() != Role.TECHNICIAN) {
            throw new IllegalArgumentException(technician.getDisplayName()
                    + " is not a technician; tickets can only be assigned to technicians.");
        }

        ticket.setAssignedToId(technician.getId());
        ticket.addHistory("Assigned to " + technician.getDisplayName());
        audit.log(session, "TICKET_ASSIGNED", "TICKET", ticket.getId(),
                "Assigned to " + technician.getDisplayName());
        return ticket;
    }

    /**
     * Move a ticket to another status with a mandatory note, enforcing the
     * lifecycle defined in {@link #ALLOWED_TRANSITIONS}.
     */
    public Ticket transition(Session session, String ticketId, TicketStatus target, String note)
            throws AccessDeniedException {
        AccessControl.check(session, Permission.UPDATE_TICKET_STATUS);

        Ticket ticket = tickets.findById(ticketId.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No ticket with id " + ticketId));

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
        audit.log(session, "TICKET_STATUS_CHANGED", "TICKET", ticket.getId(),
                current.getLabel() + " -> " + target.getLabel());
        return ticket;
    }

    /** Add a working note to a ticket (technicians and administrators). */
    public Ticket addNote(Session session, String ticketId, String note) throws AccessDeniedException {
        AccessControl.check(session, Permission.ADD_TICKET_NOTE);

        Ticket ticket = viewTicket(session, ticketId);
        ticket.addHistory("Note (" + session.currentUser().getDisplayName() + "): " + note.trim());
        audit.log(session, "TICKET_NOTE_ADDED", "TICKET", ticket.getId(), note.trim());
        return ticket;
    }
}
