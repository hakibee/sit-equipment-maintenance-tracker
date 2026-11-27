package com.sit.tracker.ui;

import com.sit.tracker.auth.AccessControl;
import com.sit.tracker.auth.AccessDeniedException;
import com.sit.tracker.auth.Session;
import com.sit.tracker.model.Asset;
import com.sit.tracker.model.AssetStatus;
import com.sit.tracker.model.AuditLog;
import com.sit.tracker.model.MaintenancePlan;
import com.sit.tracker.model.MaintenanceRecord;
import com.sit.tracker.model.MaintenanceType;
import com.sit.tracker.model.Permission;
import com.sit.tracker.model.Role;
import com.sit.tracker.model.Ticket;
import com.sit.tracker.model.TicketStatus;
import com.sit.tracker.model.Urgency;
import com.sit.tracker.model.User;
import com.sit.tracker.service.TicketService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Console front-end. Menus are built dynamically from the RBAC matrix, so a
 * user only ever sees the actions their role allows; the services enforce the
 * same rules again underneath the UI.
 */
public class AppUI {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Console actions may throw checked exceptions (e.g. AccessDeniedException). */
    @FunctionalInterface
    private interface MenuAction {
        void run() throws Exception;
    }

    /** One main-menu entry: label, permission required (null = none), action. */
    private record MenuItem(String label, Permission permission, MenuAction action) {}

    private final ConsoleIO io;
    private final AppContext ctx;
    private final Session session = new Session();

    public AppUI(ConsoleIO io, AppContext ctx) {
        this.io = io;
        this.ctx = ctx;
    }

    // ---------------------------------------------------------------- top level

    public void run() {
        io.line("=================================================================");
        io.line("   SIT Equipment Maintenance Tracker - Shaggar Institute of Tech");
        io.line("=================================================================");

        try {
            while (true) {
                io.title("Login");
                io.line("  1. Log in");
                io.line("  0. Exit");
                int choice = io.askInt("Choose:", 0, 1);
                if (choice == 0) {
                    io.line("Goodbye!");
                    return;
                }
                if (loginScreen()) {
                    mainLoop();
                }
            }
        } catch (ConsoleIO.EndOfInputException e) {
            io.line("");
            io.line("Input closed - exiting. Goodbye!");
        }
    }

    /** @return true when login succeeded and the main loop should start. */
    private boolean loginScreen() {
        String username = io.askRequired("Username:");
        String password = io.askRequired("Password:");
        try {
            Session logged = ctx.auth().login(username, password);
            session.login(logged.currentUser());
            io.ok("Welcome, " + session.currentUser().getFullName()
                    + " [" + session.currentUser().getRole().getLabel() + "]");
            return true;
        } catch (AccessDeniedException e) {
            io.error(e.getMessage());
            return false;
        }
    }

    private void mainLoop() {
        List<MenuItem> menu = buildMenu();
        while (session.isLoggedIn()) {
            io.title("Main Menu - " + session.currentUser().getFullName()
                    + " [" + session.currentUser().getRole().getLabel() + "]");
            List<MenuItem> visible = new ArrayList<>();
            int i = 1;
            for (MenuItem item : menu) {
                if (item.permission() == null || AccessControl.can(session, item.permission())) {
                    io.line(String.format("  %2d. %s", i, item.label()));
                    visible.add(item);
                    i++;
                }
            }
            io.line("   0. Log out");
            int choice = io.askInt("Choose:", 0, visible.size());
            if (choice == 0) {
                ctx.auth().logout(session);
                io.ok("Logged out.");
                return;
            }
            dispatch(visible.get(choice - 1));
        }
    }

    private void dispatch(MenuItem item) {
        try {
            item.action().run();
        } catch (ConsoleIO.EndOfInputException e) {
            throw e; // propagate: stdin closed, shut down cleanly
        } catch (AccessDeniedException | IllegalArgumentException | IllegalStateException e) {
            io.error(e.getMessage());
        } catch (Exception e) {
            io.error("Unexpected error: " + e);
        }
    }

    private List<MenuItem> buildMenu() {
        List<MenuItem> menu = new ArrayList<>();
        menu.add(new MenuItem("View all assets", Permission.VIEW_ASSETS, this::viewAllAssets));
        menu.add(new MenuItem("Search assets (id / serial / name / room)", Permission.VIEW_ASSETS, this::searchAssets));
        menu.add(new MenuItem("Register a new asset", Permission.ADD_ASSET, this::registerAsset));
        menu.add(new MenuItem("Update asset status", Permission.UPDATE_ASSET_STATUS, this::updateAssetStatus));
        menu.add(new MenuItem("Report a fault (create ticket)", Permission.REPORT_FAULT, this::reportFault));
        menu.add(new MenuItem("My fault tickets", Permission.VIEW_OWN_TICKETS, this::myTickets));
        menu.add(new MenuItem("All fault tickets", Permission.VIEW_ALL_TICKETS, this::allTickets));
        menu.add(new MenuItem("Work on a ticket (assign / advance / resolve)", Permission.VIEW_ALL_TICKETS, this::workTicket));
        menu.add(new MenuItem("Maintenance schedule (due & overdue)", Permission.VIEW_MAINTENANCE_SCHEDULE, this::maintenanceSchedule));
        menu.add(new MenuItem("Create maintenance plan", Permission.SCHEDULE_MAINTENANCE, this::createPlan));
        menu.add(new MenuItem("Record maintenance service", Permission.RECORD_MAINTENANCE, this::recordService));
        menu.add(new MenuItem("Summary report (MTTR / open tickets)", Permission.VIEW_REPORTS, this::summaryReport));
        menu.add(new MenuItem("Audit log", Permission.VIEW_AUDIT_LOG, this::viewAuditLog));
        menu.add(new MenuItem("Manage users", Permission.MANAGE_USERS, this::manageUsers));
        return menu;
    }

    // ---------------------------------------------------------------- assets

    private void viewAllAssets() throws AccessDeniedException {
        io.title("Asset Inventory");
        printAssetTable(ctx.assets().findAll(session));
    }

    private void searchAssets() throws AccessDeniedException {
        String text = io.askRequired("Search text:");
        List<Asset> found = ctx.assets().search(session, text);
        io.title("Assets matching '" + text + "' (" + found.size() + ")");
        printAssetTable(found);
    }

    private void registerAsset() throws AccessDeniedException {
        io.title("Register New Asset");
        String serial = io.askRequired("Serial number:");
        String name = io.askRequired("Asset name:");
        String category = io.askRequired("Category (e.g. Electronics, IT, Mechanical):");
        String building = io.askRequired("Building (e.g. A, B, C, D):");
        String room = io.askRequired("Room number:");
        LocalDate purchased = io.askOptionalDate("Purchase date");
        Asset asset = ctx.assets().registerAsset(session, serial, name, category, building, room, purchased);
        io.ok("Registered " + asset.getId() + " - QR payload: " + asset.getQrPayload());
    }

    private void updateAssetStatus() throws AccessDeniedException {
        io.title("Update Asset Status");
        String assetId = io.askRequired("Asset id (e.g. AST-001):");
        Asset asset = ctx.assets().findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("No asset with id " + assetId));
        io.ok("Selected: " + asset.summaryLine());
        AssetStatus newStatus = io.askEnum("New status:", AssetStatus.values());
        Asset updated = ctx.assets().updateStatus(session, assetId, newStatus);
        io.ok(updated.getId() + " is now " + updated.getStatus().getLabel());
    }

    private void printAssetTable(List<Asset> assets) {
        if (assets.isEmpty()) {
            io.line("  (no assets)");
            return;
        }
        io.rule();
        io.line(String.format("%-9s %-17s %-27s %-7s %-18s",
                "ID", "Serial", "Name", "Room", "Status"));
        io.rule();
        for (Asset a : assets) {
            io.line(String.format("%-9s %-17s %-27s %-7s %-18s",
                    a.getId(), a.getSerialNumber(), trim(a.getName(), 27),
                    a.getRoomLabel(), a.getStatus().getLabel()));
        }
        io.rule();
    }

    // ---------------------------------------------------------------- tickets

    private void reportFault() throws AccessDeniedException {
        io.title("Report a Fault");
        printAssetTable(ctx.assets().findAll(session));
        String assetId = io.askRequired("Asset id to report:");
        Urgency urgency = io.askEnum("Urgency:", Urgency.values());
        String title = io.askRequired("Short title:");
        String description = io.askRequired("Description of the fault:");
        Ticket ticket = ctx.tickets().reportFault(session, assetId, urgency, title, description);
        io.ok("Ticket " + ticket.getId() + " created with status: Reported.");
        io.ok("Asset " + ticket.getAssetId() + " was flagged as Faulty.");
    }

    private void myTickets() throws AccessDeniedException {
        io.title("My Fault Tickets");
        printTicketTable(ctx.tickets().myTickets(session));
    }

    private void allTickets() throws AccessDeniedException {
        io.title("All Fault Tickets");
        printTicketTable(ctx.tickets().allTickets(session));
    }

    private void workTicket() throws AccessDeniedException {
        io.title("Work on a Ticket");
        String ticketId = io.askRequired("Ticket id (e.g. TKT-001):");
        Ticket ticket = ctx.tickets().viewTicket(session, ticketId);
        printTicketDetail(ticket);

        List<TicketStatus> moves = new ArrayList<>(TicketService.allowedTransitions(ticket.getStatus()));
        io.blank();
        io.line("  1. Assign to technician");
        for (int i = 0; i < moves.size(); i++) {
            io.line("  " + (i + 2) + ". Move to: " + moves.get(i).getLabel());
        }
        io.line("  " + (moves.size() + 2) + ". Add note");
        io.line("   0. Back");
        int last = moves.size() + 2;
        int choice = io.askInt("Choose:", 0, last);
        if (choice == 0) {
            return;
        }
        if (choice == 1) {
            List<User> technicians = ctx.auth().listTechnicians();
            io.line("  Technicians:");
            for (User t : technicians) {
                io.line("   - " + t.getId() + " " + t.getFullName());
            }
            String techId = io.askRequired("Technician id:");
            Ticket assigned = ctx.tickets().assign(session, ticket.getId(), techId);
            io.ok("Ticket " + assigned.getId() + " assigned.");
        } else if (choice <= moves.size() + 1) {
            TicketStatus target = moves.get(choice - 2);
            String note = io.askRequired("Note (required):");
            Ticket moved = ctx.tickets().transition(session, ticket.getId(), target, note);
            io.ok("Ticket " + moved.getId() + " is now: " + moved.getStatus().getLabel());
            if (target == TicketStatus.RESOLVED) {
                io.ok("Recorded as resolved at "
                        + moved.getResolvedAt().format(TS) + " (counts toward MTTR).");
            }
        } else {
            String note = io.askRequired("Note:");
            ctx.tickets().addNote(session, ticket.getId(), note);
            io.ok("Note added.");
        }
    }

    private void printTicketTable(List<Ticket> tickets) {
        if (tickets.isEmpty()) {
            io.line("  (no tickets)");
            return;
        }
        io.rule();
        io.line(String.format("%-9s %-9s %-10s %-15s %-16s %-16s %s",
                "ID", "Asset", "Urgency", "Status", "Assigned", "Created", "Title"));
        io.rule();
        for (Ticket t : tickets) {
            io.line(String.format("%-9s %-9s %-10s %-15s %-16s %-16s %s",
                    t.getId(), t.getAssetId(), t.getUrgency().getLabel(),
                    t.getStatus().getLabel(), userName(t.getAssignedToId()),
                    t.getCreatedAt().format(TS), trim(t.getTitle(), 28)));
        }
        io.rule();
    }

    private void printTicketDetail(Ticket t) {
        io.rule();
        io.line("  Ticket   : " + t.getId() + "  [" + t.getStatus().getLabel() + "]");
        io.line("  Asset    : " + assetName(t.getAssetId()));
        io.line("  Urgency  : " + t.getUrgency().getLabel()
                + " (target response: " + t.getUrgency().getTargetResponseHours() + "h)");
        io.line("  Title    : " + t.getTitle());
        io.line("  Reported : " + t.getCreatedAt().format(TS) + " by " + userName(t.getReportedById()));
        io.line("  Assigned : " + userName(t.getAssignedToId()));
        if (t.getResolvedAt() != null) {
            io.line("  Resolved : " + t.getResolvedAt().format(TS));
        }
        io.line("  Description: " + t.getDescription());
        io.line("  History:");
        for (String h : t.getHistory()) {
            io.line("    - " + h);
        }
        io.rule();
    }

    // ---------------------------------------------------------------- maintenance

    private void maintenanceSchedule() throws AccessDeniedException {
        io.title("Maintenance Schedule");
        List<MaintenancePlan> plans = ctx.maintenance().allPlans(session);
        if (plans.isEmpty()) {
            io.line("  (no plans)");
        }
        LocalDate today = LocalDate.now();
        io.rule();
        io.line(String.format("%-9s %-9s %-32s %-6s %-12s %-12s %s",
                "Plan", "Asset", "Task", "Every", "Last", "Next due", "State"));
        io.rule();
        for (MaintenancePlan p : plans) {
            long late = p.getNextDueDate().toEpochDay() - today.toEpochDay();
            String state = !p.isActive() ? "Paused"
                    : late < 0 ? "OVERDUE " + (-late) + "d"
                    : late == 0 ? "Due today"
                    : "Due in " + late + "d";
            io.line(String.format("%-9s %-9s %-32s %-6s %-12s %-12s %s",
                    p.getId(), p.getAssetId(), trim(p.getTaskDescription(), 32),
                    p.getIntervalDays() + "d",
                    p.getLastServiceDate() == null ? "-" : p.getLastServiceDate(),
                    p.getNextDueDate(), state));
        }
        io.rule();

        if (AccessControl.can(session, Permission.SCHEDULE_MAINTENANCE) && !plans.isEmpty()) {
            String planId = io.ask("Pause/resume plan id (empty = skip):");
            if (!planId.isEmpty()) {
                MaintenancePlan p = ctx.maintenance().setActive(session, planId, !findPlan(planId).isActive());
                io.ok("Plan " + p.getId() + " is now " + (p.isActive() ? "active" : "paused") + ".");
            }
        }
    }

    private MaintenancePlan findPlan(String planId) throws AccessDeniedException {
        return ctx.maintenance().allPlans(session).stream()
                .filter(p -> p.getId().equalsIgnoreCase(planId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No plan with id " + planId));
    }

    private void createPlan() throws AccessDeniedException {
        io.title("Create Maintenance Plan");
        String assetId = io.askRequired("Asset id (e.g. AST-001):");
        String task = io.askRequired("Task (e.g. Calibration):");
        int interval = io.askInt("Interval in days:", 1, 3650);
        LocalDate due = io.askDate("Next due date:");
        MaintenancePlan plan = ctx.maintenance().createPlan(session, assetId, task, interval, due);
        io.ok("Plan " + plan.getId() + " created; next due " + plan.getNextDueDate() + ".");
    }

    private void recordService() throws AccessDeniedException {
        io.title("Record Maintenance Service");
        String assetId = io.askRequired("Asset id:");
        MaintenanceType type = io.askEnum("Service type:", MaintenanceType.values());
        LocalDate date = io.askDate("Service date:");
        io.line("Duration in hours:");
        double hours = io.askDouble("(e.g. 2.5):");
        String description = io.askRequired("What was done:");
        MaintenanceRecord record = ctx.maintenance().recordService(session, assetId, date, type, description, hours);
        io.ok("Record " + record.getId() + " saved for " + record.getAssetId() + ".");
        ctx.maintenance().activePlanFor(record.getAssetId()).ifPresentOrElse(
                plan -> io.ok("Plan " + plan.getId() + " updated; next due " + plan.getNextDueDate() + "."),
                () -> io.line("  (no active plan for this asset)"));
    }

    // ---------------------------------------------------------------- reports

    private void summaryReport() throws AccessDeniedException {
        io.title("Summary Report");

        io.line("Mean Time to Repair (MTTR)");
        var mttr = ctx.reports().mttrHoursOverall();
        if (mttr.isPresent()) {
            io.line(String.format("  Overall: %.1f h  (based on %d resolved ticket(s))",
                    mttr.getAsDouble() / 60.0, ctx.reports().resolvedTicketCount()));
        } else {
            io.line("  Overall: no resolved tickets yet.");
        }
        Map<Urgency, Double> byUrgency = ctx.reports().mttrHoursByUrgency();
        if (!byUrgency.isEmpty()) {
            io.line("  By urgency:");
            byUrgency.forEach((u, hours) ->
                    io.line(String.format("    %-10s %6.1f h", u.getLabel(), hours)));
        }

        io.blank();
        List<Ticket> open = ctx.reports().openTickets();
        io.line("Open tickets: " + open.size());
        io.line("  By status : " + counts(ctx.reports().openTicketsByStatus()));
        io.line("  By urgency: " + counts(ctx.reports().openTicketsByUrgency()));
        if (!open.isEmpty()) {
            io.line("  Oldest open tickets:");
            open.stream().limit(5).forEach(t ->
                    io.line(String.format("    %-9s %-9s %-9s %-15s %s",
                            t.getId(), t.getAssetId(), t.getUrgency().getLabel(),
                            t.getStatus().getLabel(), trim(t.getTitle(), 30))));
        }

        io.blank();
        io.line("Asset status overview (" + ctx.assets().findAll(session).size() + " assets)");
        io.line("  " + counts(ctx.reports().assetsByStatus()));

        io.blank();
        List<MaintenancePlan> overdue = ctx.reports().overduePlans();
        io.line("Overdue maintenance plans: " + overdue.size());
        for (MaintenancePlan p : overdue) {
            long days = today().toEpochDay() - p.getNextDueDate().toEpochDay();
            io.line("  " + p.getId() + " | " + p.getAssetId() + " | " + p.getTaskDescription()
                    + " | due " + p.getNextDueDate() + " (" + days + " days late)");
        }
    }

    private String counts(Map<?, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        counts.forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(labelOf(k)).append(" ").append(v);
        });
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    private String labelOf(Object value) {
        try {
            return (String) value.getClass().getMethod("getLabel").invoke(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    private LocalDate today() {
        return LocalDate.now();
    }

    // ---------------------------------------------------------------- admin

    private void viewAuditLog() throws AccessDeniedException {
        io.title("Audit Log (latest 25)");
        List<AuditLog> entries = ctx.audit().recentEntries(25);
        if (entries.isEmpty()) {
            io.line("  (empty)");
        }
        io.rule();
        io.line(String.format("%-9s %-17s %-12s %-24s %-22s %s",
                "ID", "Time", "Actor", "Action", "Entity", "Details"));
        io.rule();
        for (AuditLog e : entries) {
            io.line(String.format("%-9s %-17s %-12s %-24s %-22s %s",
                    e.getId(), e.getTimestamp().format(TS), e.getActorId(),
                    trim(e.getAction(), 24),
                    e.getEntityType() + " " + e.getEntityId(),
                    trim(e.getDetails(), 34)));
        }
        io.rule();
    }

    private void manageUsers() throws AccessDeniedException {
        io.title("Manage Users");
        io.line("  1. List users");
        io.line("  2. Create user");
        io.line("  3. Change user role");
        io.line("   0. Back");
        int choice = io.askInt("Choose:", 0, 3);
        if (choice == 0) {
            return;
        }
        if (choice == 1) {
            io.rule();
            io.line(String.format("%-7s %-28s %-18s %-24s", "ID", "Full name", "Username", "Role"));
            io.rule();
            for (User u : ctx.auth().listUsers(session)) {
                io.line(u.toString());
            }
            io.rule();
        } else if (choice == 2) {
            String username = io.askRequired("Username:");
            String password = io.askRequired("Password:");
            String fullName = io.askRequired("Full name:");
            Role role = io.askEnum("Role:", Role.values());
            User u = ctx.auth().createUser(session, username, password, fullName, role);
            io.ok("Created " + u.getId() + " - " + u.getDisplayName()
                    + " [" + u.getRole().getLabel() + "]");
        } else {
            String userId = io.askRequired("User id (e.g. USR-002):");
            Role role = io.askEnum("New role:", Role.values());
            User u = ctx.auth().changeRole(session, userId, role);
            io.ok(u.getDisplayName() + " is now a " + u.getRole().getLabel() + ".");
        }
    }

    // ---------------------------------------------------------------- helpers

    private String userName(String userId) {
        if (userId == null) {
            return "-";
        }
        Optional<User> u = ctx.auth().findById(userId);
        return u.map(User::getFullName).orElse(userId);
    }

    private String assetName(String assetId) {
        return ctx.assets().findById(assetId)
                .map(a -> a.getId() + " (" + a.getName() + ", " + a.getRoomLabel() + ")")
                .orElse(assetId);
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
}
