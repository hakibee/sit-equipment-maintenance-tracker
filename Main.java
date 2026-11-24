import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * SIT Equipment Maintenance Tracker - first prototype.
 *
 * Everything (data, logic, UI) lives in this one file on purpose: the goal of
 * this spike is to prove the core workflow (view lab assets, report a fault,
 * work the ticket to resolution) before any structure is introduced.
 * Data is hardcoded; nothing persists between runs.
 */
public class Main {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ------------------------------------------------------------- data types

    static class Asset {
        String id;
        String name;
        String room;    // e.g. "A-204"
        String status;  // "In Service", "Faulty", ...

        Asset(String id, String name, String room, String status) {
            this.id = id;
            this.name = name;
            this.room = room;
            this.status = status;
        }
    }

    static class Ticket {
        String id;
        String assetId;
        String urgency;     // "Low", "Medium", "High", "Critical"
        String title;
        String reportedBy;  // plain name for now
        String status;      // "Reported", "Resolved"
        LocalDateTime createdAt;
        LocalDateTime resolvedAt;

        Ticket(String id, String assetId, String urgency, String title,
               String reportedBy, LocalDateTime createdAt) {
            this.id = id;
            this.assetId = assetId;
            this.urgency = urgency;
            this.title = title;
            this.reportedBy = reportedBy;
            this.status = "Reported";
            this.createdAt = createdAt;
        }
    }

    // ------------------------------------------------------------- seed data

    static List<Asset> assets = new ArrayList<>();
    static List<Ticket> tickets = new ArrayList<>();
    static int assetCounter = 0;
    static int ticketCounter = 0;

    static void seed() {
        assets.add(new Asset("AST-001", "Digital Oscilloscope", "A-204", "Faulty"));
        assets.add(new Asset("AST-002", "Function Generator", "A-204", "Faulty"));
        assets.add(new Asset("AST-003", "DC Power Supply", "A-204", "Faulty"));
        assets.add(new Asset("AST-004", "Desktop Workstation 1", "B-105", "In Service"));
        assets.add(new Asset("AST-005", "Binocular Microscope", "C-108", "In Service"));
        assets.add(new Asset("AST-006", "Lathe Machine", "D-001", "Under Maintenance"));
        assetCounter = assets.size();

        tickets.add(new Ticket("TKT-001", "AST-003", "Critical",
                "DC power supply has no output", "Hanna Tesfaye", LocalDateTime.now().minusDays(2)));
        tickets.add(new Ticket("TKT-002", "AST-001", "Low",
                "Oscilloscope focus knob loose", "Chala Gudeta", LocalDateTime.now().minusHours(12)));
        Ticket done1 = new Ticket("TKT-003", "AST-004", "Critical",
                "Workstation 1 fails to boot", "Chala Gudeta", LocalDateTime.now().minusDays(6));
        done1.status = "Resolved";
        done1.resolvedAt = LocalDateTime.now().minusDays(5);
        tickets.add(done1);
        ticketCounter = tickets.size();

        System.out.println("[DEBUG] seeded " + assets.size() + " assets and "
                + tickets.size() + " tickets");
    }

    static Asset findAsset(String id) {
        for (Asset a : assets) {
            if (a.id.equalsIgnoreCase(id)) {
                return a;
            }
        }
        return null;
    }

    static Ticket findTicket(String id) {
        for (Ticket t : tickets) {
            if (t.id.equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }

    // ------------------------------------------------------------- UI

    static Scanner in = new Scanner(System.in);

    static String ask(String prompt) {
        System.out.print(prompt + " ");
        return in.nextLine().trim();
    }

    static int askInt(String prompt, int min, int max) {
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

            if (choice == 1) {
                System.out.println();
                System.out.println("=== Asset Inventory ===");
                System.out.printf("%-9s %-27s %-7s %-18s%n", "ID", "Name", "Room", "Status");
                for (Asset a : assets) {
                    System.out.printf("%-9s %-27s %-7s %-18s%n", a.id, a.name, a.room, a.status);
                }
            } else if (choice == 2) {
                System.out.println();
                System.out.println("=== Report a Fault ===");
                String name = ask("Your name:");
                String assetId = ask("Asset id (e.g. AST-001):");
                Asset asset = findAsset(assetId);
                if (asset == null) {
                    System.out.println("  ! No asset with id " + assetId);
                    continue;
                }
                System.out.println("  1. Low  2. Medium  3. High  4. Critical");
                int u = askInt("Urgency:", 1, 4);
                String urgency = new String[] {"Low", "Medium", "High", "Critical"}[u - 1];
                String title = ask("Short title:");
                ticketCounter++;
                String id = String.format("TKT-%03d", ticketCounter);
                Ticket t = new Ticket(id, asset.id, urgency, title, name, LocalDateTime.now());
                tickets.add(t);
                asset.status = "Faulty";
                System.out.println("  > Ticket " + id + " created; asset " + asset.id
                        + " flagged as Faulty.");
                System.out.println("[DEBUG] open tickets now: " + tickets.size());
            } else if (choice == 3) {
                System.out.println();
                System.out.println("=== Fault Tickets ===");
                System.out.printf("%-9s %-9s %-9s %-10s %-16s %-16s %s%n",
                        "ID", "Asset", "Urgency", "Status", "Reported by", "Created", "Title");
                for (Ticket t : tickets) {
                    System.out.printf("%-9s %-9s %-9s %-10s %-16s %-16s %s%n",
                            t.id, t.assetId, t.urgency, t.status, t.reportedBy,
                            t.createdAt.format(TS), t.title);
                }
            } else if (choice == 4) {
                System.out.println();
                System.out.println("=== Resolve a Ticket ===");
                String id = ask("Ticket id (e.g. TKT-002):");
                Ticket t = findTicket(id);
                if (t == null) {
                    System.out.println("  ! No ticket with id " + id);
                    continue;
                }
                if (t.status.equals("Resolved")) {
                    System.out.println("  ! Ticket " + t.id + " is already resolved.");
                    continue;
                }
                t.status = "Resolved";
                t.resolvedAt = LocalDateTime.now();
                System.out.println("  > Ticket " + t.id + " resolved at " + t.resolvedAt.format(TS) + ".");
            } else {
                running = false;
                System.out.println("Goodbye!");
            }
        }
    }
}
