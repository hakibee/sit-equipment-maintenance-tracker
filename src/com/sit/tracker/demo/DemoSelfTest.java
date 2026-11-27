package com.sit.tracker.demo;

import com.sit.tracker.auth.AccessDeniedException;
import com.sit.tracker.model.AssetStatus;
import com.sit.tracker.model.Permission;
import com.sit.tracker.model.Role;
import com.sit.tracker.model.Ticket;
import com.sit.tracker.model.TicketStatus;
import com.sit.tracker.model.User;
import com.sit.tracker.repository.DataStore;
import com.sit.tracker.repository.InMemoryRepository;
import com.sit.tracker.repository.Identifiable;
import com.sit.tracker.service.AssetService;
import com.sit.tracker.service.AuthService;
import com.sit.tracker.service.MaintenanceService;
import com.sit.tracker.service.TicketService;
import com.sit.tracker.ui.AppContext;

import java.util.List;

/**
 * Built-in verification suite: exercises RBAC denials, the ticket lifecycle
 * guard, assignment rules, MTTR and the audit trail. Run with:
 * {@code java -cp out com.sit.tracker.Main --selftest}
 */
public class DemoSelfTest {

    private final AppContext ctx;
    private int passed = 0;
    private int failed = 0;

    public DemoSelfTest(AppContext ctx) {
        this.ctx = ctx;
    }

    /** @return true when every check passed. */
    public boolean run() {
        check("InMemoryRepository save/find/delete round-trip", this::testRepository);
        check("General user denied: registerAsset", this::testGeneralUserCannotRegisterAsset);
        check("General user allowed: reportFault (asset auto-flagged Faulty)",
                this::testGeneralUserCanReportFault);
        check("Technician denied: createPlan", this::testTechnicianCannotCreatePlan);
        check("Technician allowed: advance ticket status", this::testTechnicianCanAdvanceTicket);
        check("Lifecycle guard: Reported -> Resolved rejected", this::testIllegalTransitionRejected);
        check("Lifecycle: full path Reported->Under Review->In Repair->Resolved",
                this::testFullPathToResolved);
        check("Lifecycle: reopen Resolved -> Under Review allowed", this::testReopenAllowed);
        check("Assignment: only technicians accepted", this::testAssignRules);
        check("Report: MTTR present and positive for seeded data", this::testMttr);
        check("Report: open-ticket count matches store", this::testOpenTicketCount);
        check("Report: overdue plans detected", this::testOverduePlans);
        check("Auth: wrong password denied, correct password logs in", this::testLogin);
        check("Audit: status changes are recorded in the audit trail", this::testAuditTrail);

        System.out.printf("%nResult: %d passed, %d failed%n", passed, failed);
        return failed == 0;
    }

    // ---------------------------------------------------------------- checks

    private void testRepository() {
        InMemoryRepository<String, Row> repo = new InMemoryRepository<>();
        repo.save(new Row("R-1"));
        repo.save(new Row("R-2"));
        expect(repo.count() == 2, "two rows after save");
        expect(repo.findById("R-1").isPresent(), "findById hits");
        expect(repo.findAll().get(0).getId().equals("R-1"), "insertion order kept");
        expect(repo.deleteById("R-1"), "delete returns true");
        expect(repo.count() == 1, "count drops after delete");
    }

    private void testGeneralUserCannotRegisterAsset() throws Exception {
        var session = ctx.auth().login("user1", "user123");
        try {
            ctx.assets().registerAsset(session, "SN-TEST-1", "Ghost Asset", "IT", "Z", "999", null);
            expectFail("registerAsset should have thrown AccessDeniedException");
        } catch (AccessDeniedException e) {
            expect(e.getPermission() == Permission.ADD_ASSET, "denial carries ADD_ASSET");
        }
    }

    private void testGeneralUserCanReportFault() throws Exception {
        var session = ctx.auth().login("user3", "user123");
        Ticket t = ctx.tickets().reportFault(session, "AST-008", com.sit.tracker.model.Urgency.MEDIUM,
                "Multimeter display flickers", "LCD segments fade intermittently");
        expect(t.getStatus() == TicketStatus.REPORTED, "new ticket starts as Reported");
        expect(ctx.assets().findById("AST-008").orElseThrow().getStatus() == AssetStatus.FAULTY,
                "asset auto-flagged Faulty after fault report");
        expect(ctx.tickets().myTickets(session).stream()
                .anyMatch(x -> x.getId().equals(t.getId())), "ticket visible in reporter's list");
    }

    private void testTechnicianCannotCreatePlan() throws Exception {
        var session = ctx.auth().login("tech1", "tech123");
        try {
            ctx.maintenance().createPlan(session, "AST-007", "Yearly optics check", 365,
                    java.time.LocalDate.now().plusDays(365));
            expectFail("createPlan should have thrown AccessDeniedException");
        } catch (AccessDeniedException e) {
            expect(e.getPermission() == Permission.SCHEDULE_MAINTENANCE, "denial carries SCHEDULE_MAINTENANCE");
        }
    }

    private void testTechnicianCanAdvanceTicket() throws Exception {
        var session = ctx.auth().login("tech2", "tech123");
        Ticket t = ctx.tickets().transition(session, "TKT-002", TicketStatus.IN_REPAIR,
                "Confirmed faulty uplink cable; replacing");
        expect(t.getStatus() == TicketStatus.IN_REPAIR, "Under Review -> In Repair works");
    }

    private void testIllegalTransitionRejected() throws Exception {
        var session = ctx.auth().login("admin", "admin123");
        try {
            ctx.tickets().transition(session, "TKT-006", TicketStatus.RESOLVED, "skip review");
            expectFail("Reported -> Resolved should have thrown IllegalStateException");
        } catch (IllegalStateException e) {
            expect(e.getMessage().contains("Illegal transition"), "message explains the guard");
        }
    }

    private void testFullPathToResolved() throws Exception {
        var session = ctx.auth().login("admin", "admin123");
        ctx.tickets().transition(session, "TKT-003", TicketStatus.UNDER_REVIEW, "bench test started");
        ctx.tickets().transition(session, "TKT-003", TicketStatus.IN_REPAIR, "recalibrating output stage");
        Ticket t = ctx.tickets().transition(session, "TKT-003", TicketStatus.RESOLVED, "stable for 1h");
        expect(t.getStatus() == TicketStatus.RESOLVED, "ticket resolved");
        expect(t.getResolvedAt() != null, "resolvedAt stamped for MTTR");
    }

    private void testReopenAllowed() throws Exception {
        var session = ctx.auth().login("admin", "admin123");
        Ticket t = ctx.tickets().transition(session, "TKT-005", TicketStatus.UNDER_REVIEW,
                "fault reappeared after reboot");
        expect(t.getStatus() == TicketStatus.UNDER_REVIEW, "resolved ticket reopened");
    }

    private void testAssignRules() throws Exception {
        var session = ctx.auth().login("admin", "admin123");
        try {
            ctx.tickets().assign(session, "TKT-006", "USR-004"); // general user
            expectFail("assigning to a general user should fail");
        } catch (IllegalArgumentException e) {
            expect(e.getMessage().contains("not a technician"), "message explains role requirement");
        }
        Ticket t = ctx.tickets().assign(session, "TKT-006", "USR-002");
        expect("USR-002".equals(t.getAssignedToId()), "assigned to technician USR-02");
    }

    private void testMttr() {
        var mttr = ctx.reports().mttrHoursOverall();
        expect(mttr.isPresent() && mttr.getAsDouble() > 0, "MTTR computed from resolved tickets");
        System.out.printf("    (MTTR overall = %.1f h)%n", mttr.getAsDouble() / 60.0);
        expect(ctx.reports().resolvedTicketCount() >= 3, "three seeded resolved tickets");
    }

    private void testOpenTicketCount() {
        try {
            var all = ctx.tickets().allTickets(adminSession());
            long total = all.size();
            long resolved = all.stream()
                    .filter(t -> t.getStatus() == TicketStatus.RESOLVED).count();
            expect(ctx.reports().openTickets().size() == total - resolved,
                    "open = total - resolved");
        } catch (AccessDeniedException e) {
            expectFail("allTickets denied unexpectedly: " + e.getMessage());
        }
    }

    private void testOverduePlans() {
        List<com.sit.tracker.model.MaintenancePlan> overdue = ctx.reports().overduePlans();
        expect(overdue.stream().anyMatch(p -> p.getId().equals("PLN-002")), "PLN-002 overdue");
        expect(overdue.stream().anyMatch(p -> p.getId().equals("PLN-004")), "PLN-004 overdue");
    }

    private void testLogin() throws Exception {
        try {
            ctx.auth().login("admin", "wrong");
            expectFail("wrong password should throw AccessDeniedException");
        } catch (AccessDeniedException e) {
            expect(e.getPermission() == null, "auth failure is not a permission denial");
        }
        var session = ctx.auth().login("admin", "admin123");
        expect(session.currentUser().getRole() == Role.FACILITY_ADMIN, "admin role on login");
    }

    private void testAuditTrail() {
        var entries = ctx.audit().recentEntries(100);
        expect(entries.stream().anyMatch(e -> e.getAction().equals("TICKET_STATUS_CHANGED")),
                "status changes audited");
        expect(entries.stream().anyMatch(e -> e.getAction().equals("LOGIN_FAILED")),
                "failed logins audited");
        expect(entries.get(0).getId().startsWith("LOG-"), "audit ids formatted LOG-xxx");
    }

    // ---------------------------------------------------------------- plumbing

    private com.sit.tracker.auth.Session adminSession() throws RuntimeException {
        try {
            return ctx.auth().login("admin", "admin123");
        } catch (AccessDeniedException e) {
            throw new IllegalStateException("admin login failed in self-test", e);
        }
    }

    private void check(String name, TestBody body) {
        System.out.printf("%-4s %s%n", "", name);
        try {
            body.run();
        } catch (Exception | AssertionError e) {
            expectFail("unexpected " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void expect(boolean condition, String what) {
        if (condition) {
            passed++;
            System.out.println("      PASS  " + what);
        } else {
            failed++;
            System.out.println("      FAIL  " + what);
        }
    }

    private void expectFail(String message) {
        failed++;
        System.out.println("      FAIL  " + message);
    }

    private interface TestBody {
        void run() throws Exception;
    }

    /** Minimal entity for repository tests. */
    private static final class Row implements Identifiable<String> {
        private final String id;
        Row(String id) { this.id = id; }
        @Override public String getId() { return id; }
    }
}
