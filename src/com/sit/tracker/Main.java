package com.sit.tracker;

import com.sit.tracker.demo.DemoData;
import com.sit.tracker.model.Asset;
import com.sit.tracker.model.AuditLog;
import com.sit.tracker.model.Ticket;
import com.sit.tracker.model.TicketStatus;
import com.sit.tracker.model.Urgency;
import com.sit.tracker.repository.DataStore;
import com.sit.tracker.service.AssetService;
import com.sit.tracker.service.AuditService;
import com.sit.tracker.service.TicketService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

/**
 * Composition root and console front-end: builds the demo store, seeds it,
 * wires the services and shows the menus. All business rules (fault
 * reporting, ticket lifecycle, asset status, audit trail) live in the service
 * layer now — this class only asks for input and prints results.
 */
public class Main {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Scanner in = new Scanner(System.in);

    private static DataStore store;
    private static AssetService assets;
    private static TicketService tickets;
    private static AuditService audit;

    public static void main(String[] args) {
        bootstrap();
        runMenu();
    }

    /** Wires the object graph: store -> services -> menus. */
    private static void bootstrap() {
        store = new DataStore();
        DemoData.seed(store);

        audit = new AuditService(store);
        assets = new AssetService(store, audit);
        tickets = new TicketService(store, audit, assets);

        System.out.println("[DEBUG] assets=" + assets.findAll().size()
                + " tickets=" + tickets.allTickets().size());
    }

    // ------------------------------------------------------------- UI

    private static String ask(String prompt) {
        System.out.print(prompt + " ");
        return in.nextLine().trim();
    }

    private static String askRequired(String prompt) {
        while (true) {
            String v = ask(prompt);
            if (!v.isEmpty()) {
                return v;
            }
            System.out.println("  ! A value is required.");
        }
    }

    private static int askInt(String prompt, int min, int max) {
        while (true) {
            try {
                int v = Integer.parseInt(ask(prompt));
                if (v >= min && v <= max) {
                    return v;
                }
            } catch (NumberFormatException e) {
                // fall through
            }
            System.out.println("  ! Enter a number between " + min + " and " + max + ".");
        }
    }

    private static void runMenu() {
        System.out.println("=================================================================");
        System.out.println("   SIT Equipment Maintenance Tracker - Shaggar Institute of Tech");
        System.out.println("=================================================================");

        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("=== Main Menu ===");
            System.out.println("  1. View all assets");
            System.out.println("  2. Search assets (id / serial / name / room)");
            System.out.println("  3. Report a fault (create ticket)");
            System.out.println("  4. View all tickets");
            System.out.println("  5. Work on a ticket (assign / advance / resolve)");
            System.out.println("  6. View audit log");
            System.out.println("   0. Exit");
            int choice = askInt("Choose:", 0, 6);

            try {
                switch (choice) {
                    case 1 -> viewAllAssets();
                    case 2 -> searchAssets();
                    case 3 -> reportFault();
                    case 4 -> allTickets();
                    case 5 -> workTicket();
                    case 6 -> viewAuditLog();
                    default -> {
                        running = false;
                        System.out.println("Goodbye!");
                    }
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
    }

    private static void viewAllAssets() {
        System.out.println();
        System.out.println("=== Asset Inventory ===");
        printAssetTable(assets.findAll());
    }

    private static void searchAssets() {
        String text = askRequired("Search text:");
        List<Asset> found = assets.search(text);
        System.out.println();
        System.out.println("=== Assets matching '" + text + "' (" + found.size() + ") ===");
        printAssetTable(found);
    }

    private static void reportFault() {
        System.out.println();
        System.out.println("=== Report a Fault ===");
        String name = askRequired("Your name:");
        printAssetTable(assets.findAll());
        String assetId = askRequired("Asset id to report:");
        System.out.println("  1. Low  2. Medium  3. High  4. Critical");
        int u = askInt("Urgency:", 1, 4);
        Urgency urgency = Urgency.values()[u - 1];
        String title = askRequired("Short title:");
        String description = askRequired("Description of the fault:");
        Ticket ticket = tickets.reportFault(name, assetId, urgency, title, description);
        System.out.println("  > Ticket " + ticket.getId() + " created with status: Reported.");
        System.out.println("  > Asset " + ticket.getAssetId() + " was flagged as Faulty.");
    }

    private static void allTickets() {
        System.out.println();
        System.out.println("=== All Fault Tickets ===");
        printTicketTable(tickets.allTickets());
    }

    private static void workTicket() {
        System.out.println();
        System.out.println("=== Work on a Ticket ===");
        String ticketId = askRequired("Ticket id (e.g. TKT-001):");
        Ticket ticket = tickets.viewTicket(ticketId);
        printTicketDetail(ticket);

        List<TicketStatus> moves = List.copyOf(TicketService.allowedTransitions(ticket.getStatus()));
        System.out.println("  1. Assign to technician");
        for (int i = 0; i < moves.size(); i++) {
            System.out.println("  " + (i + 2) + ". Move to: " + moves.get(i).getLabel());
        }
        System.out.println("  " + (moves.size() + 2) + ". Add note");
        System.out.println("   0. Back");
        int last = moves.size() + 2;
        int choice = askInt("Choose:", 0, last);
        if (choice == 0) {
            return;
        }
        if (choice == 1) {
            String tech = askRequired("Technician name:");
            Ticket assigned = tickets.assign(ticket.getId(), tech);
            System.out.println("  > Ticket " + assigned.getId() + " assigned to " + assigned.getAssignedTo() + ".");
        } else if (choice <= moves.size() + 1) {
            TicketStatus target = moves.get(choice - 2);
            String note = askRequired("Note (required):");
            Ticket moved = tickets.transition(ticket.getId(), target, note);
            System.out.println("  > Ticket " + moved.getId() + " is now: " + moved.getStatus().getLabel());
            if (target == TicketStatus.RESOLVED) {
                System.out.println("  > Recorded as resolved at " + moved.getResolvedAt().format(TS) + ".");
            }
        } else {
            String note = askRequired("Note:");
            tickets.addNote(ticket.getId(), "Staff", note);
            System.out.println("  > Note added.");
        }
    }

    private static void viewAuditLog() {
        System.out.println();
        System.out.println("=== Audit Log (latest 25) ===");
        List<AuditLog> entries = audit.recentEntries(25);
        if (entries.isEmpty()) {
            System.out.println("  (empty)");
        }
        System.out.println("-".repeat(78));
        System.out.printf("%-9s %-17s %-12s %-24s %-22s %s%n",
                "ID", "Time", "Actor", "Action", "Entity", "Details");
        System.out.println("-".repeat(78));
        for (AuditLog e : entries) {
            System.out.printf("%-9s %-17s %-12s %-24s %-22s %s%n",
                    e.getId(), e.getTimestamp().format(TS), e.getActorId(),
                    trim(e.getAction(), 24),
                    e.getEntityType() + " " + e.getEntityId(),
                    trim(e.getDetails(), 34));
        }
        System.out.println("-".repeat(78));
    }

    // ------------------------------------------------------------- table printers

    private static void printAssetTable(List<Asset> list) {
        if (list.isEmpty()) {
            System.out.println("  (no assets)");
            return;
        }
        System.out.println("-".repeat(78));
        System.out.printf("%-9s %-17s %-27s %-7s %-18s%n", "ID", "Serial", "Name", "Room", "Status");
        System.out.println("-".repeat(78));
        for (Asset a : list) {
            System.out.printf("%-9s %-17s %-27s %-7s %-18s%n",
                    a.getId(), a.getSerialNumber(), trim(a.getName(), 27),
                    a.getRoomLabel(), a.getStatus().getLabel());
        }
        System.out.println("-".repeat(78));
    }

    private static void printTicketTable(List<Ticket> list) {
        if (list.isEmpty()) {
            System.out.println("  (no tickets)");
            return;
        }
        System.out.println("-".repeat(78));
        System.out.printf("%-9s %-9s %-10s %-15s %-16s %-16s %s%n",
                "ID", "Asset", "Urgency", "Status", "Assigned", "Created", "Title");
        System.out.println("-".repeat(78));
        for (Ticket t : list) {
            System.out.printf("%-9s %-9s %-10s %-15s %-16s %-16s %s%n",
                    t.getId(), t.getAssetId(), t.getUrgency().getLabel(),
                    t.getStatus().getLabel(), t.getAssignedTo() == null ? "-" : t.getAssignedTo(),
                    t.getCreatedAt().format(TS), trim(t.getTitle(), 28));
        }
        System.out.println("-".repeat(78));
    }

    private static void printTicketDetail(Ticket t) {
        System.out.println("-".repeat(78));
        System.out.println("  Ticket   : " + t.getId() + "  [" + t.getStatus().getLabel() + "]");
        System.out.println("  Asset    : " + t.getAssetId());
        System.out.println("  Urgency  : " + t.getUrgency().getLabel()
                + " (target response: " + t.getUrgency().getTargetResponseHours() + "h)");
        System.out.println("  Title    : " + t.getTitle());
        System.out.println("  Reported : " + t.getCreatedAt().format(TS) + " by " + t.getReportedBy());
        System.out.println("  Assigned : " + (t.getAssignedTo() == null ? "-" : t.getAssignedTo()));
        if (t.getResolvedAt() != null) {
            System.out.println("  Resolved : " + t.getResolvedAt().format(TS));
        }
        System.out.println("  Description: " + t.getDescription());
        System.out.println("  History:");
        for (String h : t.getHistory()) {
            System.out.println("    - " + h);
        }
        System.out.println("-".repeat(78));
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
}
