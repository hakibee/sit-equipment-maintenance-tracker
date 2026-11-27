package com.sit.tracker.demo;

import com.sit.tracker.model.Asset;
import com.sit.tracker.model.AssetStatus;
import com.sit.tracker.model.MaintenancePlan;
import com.sit.tracker.model.MaintenanceRecord;
import com.sit.tracker.model.MaintenanceType;
import com.sit.tracker.model.Role;
import com.sit.tracker.model.Ticket;
import com.sit.tracker.model.TicketStatus;
import com.sit.tracker.model.Urgency;
import com.sit.tracker.model.User;
import com.sit.tracker.repository.DataStore;
import com.sit.tracker.service.AuditService;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Seeds the in-memory demo database with a realistic slice of SIT laboratory
 * life: users of all three roles, twelve assets across five lab rooms, eight
 * tickets in various workflow stages, five maintenance cycles and a service
 * history. All timestamps are relative to the moment the app starts, so the
 * reports (MTTR, overdue plans) always show meaningful numbers.
 */
public final class DemoData {

    private DemoData() {
        // static seeder, not instantiable
    }

    public static void seed(DataStore store) {
        seedUsers(store);
        seedAssets(store);
        seedTickets(store);
        seedPlans(store);
        seedMaintenanceHistory(store);

        new AuditService(store).log(null, "DEMO_SEED", "SYSTEM", "-",
                "Seeded demo dataset: 6 users, 12 assets, 8 tickets, 5 plans");
    }

    // ------------------------------------------------------------------ users

    private static void seedUsers(DataStore store) {
        saveUser(store, "admin", "admin123", "Abebe Kebede", Role.FACILITY_ADMIN);
        saveUser(store, "tech1", "tech123", "Tigist Hailu", Role.TECHNICIAN);
        saveUser(store, "tech2", "tech123", "Dawit Alemu", Role.TECHNICIAN);
        saveUser(store, "user1", "user123", "Hanna Tesfaye", Role.GENERAL_USER);
        saveUser(store, "user2", "user123", "Chala Gudeta", Role.GENERAL_USER);
        saveUser(store, "user3", "user123", "Meron Fikru", Role.GENERAL_USER);
    }

    private static void saveUser(DataStore store, String username, String password,
                                 String fullName, Role role) {
        store.users().save(new User(store.nextId("USR"), username, password, fullName, role));
    }

    // ------------------------------------------------------------------ assets

    private static void seedAssets(DataStore store) {
        saveAsset(store, "SN-ELO-1145", "Digital Oscilloscope", "Electronics", "A", "204",
                AssetStatus.FAULTY, LocalDate.of(2023, 2, 14));
        saveAsset(store, "SN-ELO-1207", "Function Generator", "Electronics", "A", "204",
                AssetStatus.FAULTY, LocalDate.of(2022, 9, 30));
        saveAsset(store, "SN-ELO-0982", "DC Power Supply", "Electronics", "A", "204",
                AssetStatus.FAULTY, LocalDate.of(2021, 6, 21));
        saveAsset(store, "SN-IT-4401", "Desktop Workstation 1", "IT", "B", "105",
                AssetStatus.IN_SERVICE, LocalDate.of(2024, 1, 10));
        saveAsset(store, "SN-IT-4402", "Desktop Workstation 2", "IT", "B", "105",
                AssetStatus.IN_SERVICE, LocalDate.of(2024, 1, 10));
        saveAsset(store, "SN-IT-2290", "Network Switch 24-port", "IT", "B", "105",
                AssetStatus.FAULTY, LocalDate.of(2023, 8, 5));
        saveAsset(store, "SN-PHY-3311", "Binocular Microscope", "Physics", "C", "108",
                AssetStatus.IN_SERVICE, LocalDate.of(2022, 3, 19));
        saveAsset(store, "SN-PHY-3345", "Digital Multimeter", "Physics", "C", "108",
                AssetStatus.IN_SERVICE, LocalDate.of(2023, 11, 2));
        saveAsset(store, "SN-CHM-5510", "Benchtop Centrifuge", "Chemistry", "C", "302",
                AssetStatus.IN_SERVICE, LocalDate.of(2021, 12, 15));
        saveAsset(store, "SN-CHM-5567", "UV-Vis Spectrophotometer", "Chemistry", "C", "302",
                AssetStatus.IN_SERVICE, LocalDate.of(2022, 5, 23));
        saveAsset(store, "SN-MEC-6600", "Lathe Machine", "Mechanical", "D", "001",
                AssetStatus.UNDER_MAINTENANCE, LocalDate.of(2020, 7, 28));
        saveAsset(store, "SN-MEC-6633", "FDM 3D Printer", "Mechanical", "D", "001",
                AssetStatus.RETIRED, LocalDate.of(2019, 4, 4));
    }

    private static void saveAsset(DataStore store, String serial, String name, String category,
                                  String building, String room, AssetStatus status, LocalDate purchased) {
        Asset asset = new Asset(store.nextId("AST"), serial, name, category, building, room,
                status, purchased);
        store.assets().save(asset);
    }

    // ------------------------------------------------------------------ tickets

    private static void seedTickets(DataStore store) {
        LocalDateTime now = LocalDateTime.now();

        mkTicket(store, "AST-003", Urgency.CRITICAL, "DC power supply has no output",
                "Unit powers on but channel output reads 0 V on both rails; second-year "
                        + "electronics practical blocked.",
                "USR-004", now.minusDays(2), "USR-002", TicketStatus.IN_REPAIR, null,
                new String[] {
                        "Status: Reported -> Under Review (initial diagnosis)",
                        "Status: Under Review -> In Repair (suspected failed rectifier; waiting for spare)"});

        mkTicket(store, "AST-006", Urgency.HIGH, "Switch port 12 flapping",
                "Lab network drops intermittently during data-logging sessions.",
                "USR-005", now.minusDays(1), "USR-003", TicketStatus.UNDER_REVIEW, null,
                new String[] {
                        "Status: Reported -> Under Review (checking port statistics and cabling)"});

        mkTicket(store, "AST-002", Urgency.MEDIUM, "Generator amplitude unstable",
                "Output amplitude drifts after ~10 minutes of operation.",
                "USR-006", now.minusDays(3), null, TicketStatus.REPORTED, null,
                new String[] {"Ticket created (status: Reported)"});

        mkTicket(store, "AST-009", Urgency.HIGH, "Centrifuge vibration alarm",
                "Strong vibration and imbalance alarm above 4000 rpm.",
                "USR-004", now.minusDays(10), "USR-002", TicketStatus.RESOLVED, now.minusDays(8),
                new String[] {
                        "Status: Reported -> Under Review (rotor imbalance suspected)",
                        "Status: Under Review -> In Repair (rebalanced rotor, replaced damper)",
                        "Status: In Repair -> Resolved (test run at 6000 rpm stable)"});

        mkTicket(store, "AST-004", Urgency.CRITICAL, "Workstation 1 fails to boot",
                "No POST; power LED blinks three times.",
                "USR-005", now.minusDays(6), "USR-003", TicketStatus.RESOLVED, now.minusDays(5),
                new String[] {
                        "Status: Reported -> Under Review (diagnostics)",
                        "Status: Under Review -> In Repair (replaced failed PSU, reseated RAM)",
                        "Status: In Repair -> Resolved (boot verified)"});

        mkTicket(store, "AST-001", Urgency.LOW, "Oscilloscope focus knob loose",
                "Focus knob slips; screen image blurs when students adjust it.",
                "USR-006", now.minusHours(12), null, TicketStatus.REPORTED, null,
                new String[] {"Ticket created (status: Reported)"});

        mkTicket(store, "AST-011", Urgency.MEDIUM, "Lathe chuck misalignment",
                "Chucking runout exceeds 0.05 mm; turning accuracy compromised.",
                "USR-004", now.minusDays(4), "USR-002", TicketStatus.IN_REPAIR, null,
                new String[] {
                        "Status: Reported -> Under Review (measured runout with dial gauge)",
                        "Status: Under Review -> In Repair (realigning scroll chuck)"});

        mkTicket(store, "AST-010", Urgency.HIGH, "Spectrophotometer lamp error",
                "Deuterium lamp intensity error at startup self-test.",
                "USR-005", now.minusDays(20), "USR-003", TicketStatus.RESOLVED, now.minusDays(18),
                new String[] {
                        "Status: Reported -> Under Review (lamp hours exceeded)",
                        "Status: Under Review -> In Repair (lamp replaced)",
                        "Status: In Repair -> Resolved (wavelength recalibrated)"});
    }

    private static void mkTicket(DataStore store, String assetId, Urgency urgency, String title,
                                 String description, String reportedById, LocalDateTime createdAt,
                                 String assignedToId, TicketStatus status, LocalDateTime resolvedAt,
                                 String[] historyLines) {
        Ticket ticket = new Ticket(store.nextId("TKT"), assetId, urgency, title, description,
                reportedById, createdAt);
        if (assignedToId != null) {
            ticket.setAssignedToId(assignedToId);
        }
        if (resolvedAt != null) {
            ticket.setResolvedAt(resolvedAt);
        }
        ticket.setStatus(status);
        ticket.addHistory("Demo seed: " + String.join("; ", historyLines));
        store.tickets().save(ticket);
    }

    // ------------------------------------------------------------------ plans

    private static void seedPlans(DataStore store) {
        LocalDate today = LocalDate.now();
        savePlan(store, "AST-001", "Calibration verification against reference signal",
                180, today.minusDays(30), today.plusDays(150));
        savePlan(store, "AST-004", "Preventive cleaning and dust check",
                90, today.minusDays(100), today.minusDays(10));   // overdue
        savePlan(store, "AST-009", "Rotor inspection and balancing",
                60, today.minusDays(50), today.plusDays(10));
        savePlan(store, "AST-011", "Lubrication of ways and chuck check",
                30, today.minusDays(45), today.minusDays(15));    // overdue
        savePlan(store, "AST-010", "Deuterium lamp intensity check",
                120, today.minusDays(20), today.plusDays(100));
    }

    private static void savePlan(DataStore store, String assetId, String task,
                                 int intervalDays, LocalDate lastService, LocalDate nextDue) {
        store.plans().save(new MaintenancePlan(store.nextId("PLN"), assetId, task,
                intervalDays, lastService, nextDue));
    }

    // ------------------------------------------------------------------ history

    private static void seedMaintenanceHistory(DataStore store) {
        LocalDate today = LocalDate.now();
        saveRecord(store, "AST-004", "USR-003", today.minusDays(5), MaintenanceType.REPAIR,
                "Replaced failed PSU and reseated RAM modules", 3.0);
        saveRecord(store, "AST-009", "USR-002", today.minusDays(8), MaintenanceType.REPAIR,
                "Rebalanced rotor, replaced worn damper", 4.5);
        saveRecord(store, "AST-010", "USR-003", today.minusDays(18), MaintenanceType.REPAIR,
                "Replaced deuterium lamp, recalibrated wavelength", 2.0);
        saveRecord(store, "AST-001", "USR-002", today.minusDays(30), MaintenanceType.CALIBRATION,
                "Annual calibration verification against reference signal", 1.5);
        saveRecord(store, "AST-011", "USR-002", today.minusDays(45), MaintenanceType.PLANNED_SERVICE,
                "Lubrication of ways and chuck", 2.0);
        saveRecord(store, "AST-004", "USR-003", today.minusDays(100), MaintenanceType.PLANNED_SERVICE,
                "Internal cleaning, thermal paste check", 1.0);
        saveRecord(store, "AST-009", "USR-002", today.minusDays(50), MaintenanceType.INSPECTION,
                "Pre-semester safety inspection", 1.0);
        saveRecord(store, "AST-007", "USR-003", today.minusDays(25), MaintenanceType.PLANNED_SERVICE,
                "Optics cleaning and alignment", 0.5);
        saveRecord(store, "AST-008", "USR-002", today.minusDays(60), MaintenanceType.CALIBRATION,
                "Multimeter calibration check", 0.5);
    }

    private static void saveRecord(DataStore store, String assetId, String technicianId,
                                   LocalDate date, MaintenanceType type, String description,
                                   double hours) {
        store.maintenanceRecords().save(new MaintenanceRecord(store.nextId("REC"), assetId,
                technicianId, date, type, description, hours));
    }
}
