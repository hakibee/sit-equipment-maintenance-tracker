package com.sit.tracker;

import com.sit.tracker.model.Asset;
import com.sit.tracker.model.AssetStatus;
import com.sit.tracker.model.Ticket;
import com.sit.tracker.model.TicketStatus;
import com.sit.tracker.model.Urgency;
import com.sit.tracker.repository.DataStore;
import com.sit.tracker.repository.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;

/**
 * Console front-end for the SIT Equipment Maintenance Tracker.
 *
 * All data now lives behind the {@link Repository} abstraction in
 * {@link DataStore} — this class no longer keeps its own lists or mints its
 * own ids; it reads and saves entities through the store like any other
 * client would. Swapping in a real database later means writing one new
 * Repository implementation, not touching this code.
 */
public class Main {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final DataStore store = new DataStore();

    // ------------------------------------------------------------- seed data

    private static void seed() {
        addAsset("SN-ELO-1145", "Digital Oscilloscope", "Electronics", "A", "204",
                AssetStatus.FAULTY, LocalDate.of(2023, 2, 14));
        addAsset("SN-ELO-1207", "Function Generator", "Electronics", "A", "204",
                AssetStatus.FAULTY, LocalDate.of(2022, 9, 30));
        addAsset("SN-ELO-0982", "DC Power Supply", "Electronics", "A", "204",
                AssetStatus.FAULTY, LocalDate.of(2021, 6, 21));
        addAsset("SN-IT-4401", "Desktop Workstation 1", "IT", "B", "105",
                AssetStatus.IN_SERVICE, LocalDate.of(2024, 1, 10));
        addAsset("SN-PHY-3311", "Binocular Microscope", "Physics", "C", "108",
                AssetStatus.IN_SERVICE, LocalDate.of(2022, 3, 19));
        addAsset("SN-MEC-6600", "Lathe Machine", "Mechanical", "D", "001",
                AssetStatus.UNDER_MAINTENANCE, LocalDate.of(2020, 7, 28));

        addTicket("AST-003", Urgency.CRITICAL, "DC power supply has no output",
                "Unit powers on but channel output reads 0 V on both rails.",
                "Hanna Tesfaye", LocalDateTime.now().minusDays(2));
        addTicket("AST-001", Urgency.LOW, "Oscilloscope focus knob loose",
                "Focus knob slips; screen image blurs when students adjust it.",
                "Chala Gudeta", LocalDateTime.now().minusHours(12));
        addTicket("AST-004", Urgency.CRITICAL, "Workstation 1 fails to boot",
                "No POST; power LED blinks three times.",
                "Chala Gudeta", LocalDateTime.now().minusDays(6));
        store.tickets().findAll().get(2).setStatus(TicketStatus.RESOLVED);
        store.tickets().findAll().get(2).setResolvedAt(LocalDateTime.now().minusDays(5));

        System.out.println("[DEBUG] seeded " + store.assets().count() + " assets and "
                + store.tickets().count() + " tickets");
    }

    private static void addAsset(String serial, String name, String category,
                                 String building, String room, AssetStatus status, LocalDate purchased) {
        Asset asset = new Asset(store.nextId("AST"), serial, name, category, building, room,
                status, purchased);
        store.assets().save(asset);
    }

    private static Ticket addTicket(String assetId, Urgency urgency, String title,
                                    String description, String reportedBy, LocalDateTime createdAt) {
        Ticket ticket = new Ticket(store.nextId("TKT"), assetId, urgency, title,
                description, reportedBy, createdAt);
        return store.tickets().save(ticket);
    }

    // ------------------------------------------------------------- UI

    private static final Scanner in = new Scanner(System.in);

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

    public static void main(String[] args) {
        seed();

        System.out.println("=================================================================");
        System.out.println("   SIT Equipment Maintenance Tracker - Shaggar Institute of Tech");
        System.out.println("=================================================================");

        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("=== Main Menu ===");
            System.out.println("  1. View all assets");
            System.out.println("  2. Report a fault (create ticket)");
            System.out.println("  3. View all tickets");
            System.out.println("  4. Resolve a ticket");
            System.out.println("   0. Exit");
            int choice = askInt("Choose:", 0, 4);

            switch (choice) {
                case 1 -> {
                    System.out.println();
                    System.out.println("=== Asset Inventory ===");
                    System.out.printf("%-9s %-27s %-7s %-18s%n", "ID", "Name", "Room", "Status");
                    List<Asset> all = store.assets().findAll();
                    for (Asset a : all) {
                        System.out.printf("%-9s %-27s %-7s %-18s%n",
                                a.getId(), a.getName(), a.getRoomLabel(), a.getStatus().getLabel());
                    }
                }
                case 2 -> {
                    System.out.println();
                    System.out.println("=== Report a Fault ===");
                    String name = askRequired("Your name:");
                    String assetId = askRequired("Asset id (e.g. AST-001):");
                    Asset asset = store.assets().findById(assetId.trim().toUpperCase()).orElse(null);
                    if (asset == null) {
                        System.out.println("  ! No asset with id " + assetId);
                        break;
                    }
                    System.out.println("  1. Low  2. Medium  3. High  4. Critical");
                    int u = askInt("Urgency:", 1, 4);
                    Urgency urgency = Urgency.values()[u - 1];
                    String title = askRequired("Short title:");
                    String description = askRequired("Description of the fault:");
                    Ticket t = addTicket(asset.getId(), urgency, title, description,
                            name, LocalDateTime.now());
                    asset.setStatus(AssetStatus.FAULTY);
                    store.assets().save(asset);
                    System.out.println("  > Ticket " + t.getId() + " created with status: Reported.");
                    System.out.println("  > Asset " + asset.getId() + " was flagged as Faulty.");
                    System.out.println("[DEBUG] tickets in store now: " + store.tickets().count());
                }
                case 3 -> {
                    System.out.println();
                    System.out.println("=== Fault Tickets ===");
                    System.out.printf("%-9s %-9s %-10s %-16s %-16s %s%n",
                            "ID", "Asset", "Urgency", "Status", "Reported by", "Created", "Title");
                    for (Ticket t : store.tickets().findAll()) {
                        System.out.printf("%-9s %-9s %-10s %-16s %-16s %s%n",
                                t.getId(), t.getAssetId(), t.getUrgency().getLabel(),
                                t.getStatus().getLabel(), t.getReportedBy(),
                                t.getCreatedAt().format(TS), t.getTitle());
                    }
                }
                case 4 -> {
                    System.out.println();
                    System.out.println("=== Resolve a Ticket ===");
                    String id = askRequired("Ticket id (e.g. TKT-002):");
                    Ticket t = store.tickets().findById(id.trim().toUpperCase()).orElse(null);
                    if (t == null) {
                        System.out.println("  ! No ticket with id " + id);
                        break;
                    }
                    if (t.getStatus() == TicketStatus.RESOLVED) {
                        System.out.println("  ! Ticket " + t.getId() + " is already resolved.");
                        break;
                    }
                    t.setStatus(TicketStatus.RESOLVED);
                    t.setResolvedAt(LocalDateTime.now());
                    store.tickets().save(t);
                    System.out.println("  > Ticket " + t.getId() + " resolved at "
                            + t.getResolvedAt().format(TS) + ".");
                }
                default -> {
                    running = false;
                    System.out.println("Goodbye!");
                }
            }
        }
    }
}
