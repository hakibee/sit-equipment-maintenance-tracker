# SIT Equipment Maintenance Tracker

An object-oriented Java console application for managing laboratory equipment
maintenance at **Shaggar Institute of Technology (SIT)**. It tracks the asset
inventory, handles fault-report tickets through a defined workflow, schedules
recurring maintenance, keeps an audit trail, and produces summary reports such
as Mean Time to Repair (MTTR).

Built with plain Java (JDK 17+, no external libraries) and an **in-memory demo
database** that comes pre-seeded with realistic data, so the application is
fully explorable immediately after startup.

---

## Quick start

Requirements: a JDK, version 17 or newer (records are used).

```bash
# Linux / macOS
./run.sh

# Windows
run.bat
```

or manually:

```bash
javac -d out $(find src -name '*.java')      # Windows: javac -d out @sources.txt
java -cp out com.sit.tracker.Main
```

Run the built-in verification suite (27 checks covering RBAC, the ticket
lifecycle, assignment rules, MTTR and the audit trail):

```bash
java -cp out com.sit.tracker.Main --selftest
```

### Demo accounts

| Username | Password  | Role                    | Person          |
|----------|-----------|-------------------------|-----------------|
| admin    | admin123  | Facility Administrator  | Abebe Kebede    |
| tech1    | tech123   | Technician              | Tigist Hailu    |
| tech2    | tech123   | Technician              | Dawit Alemu     |
| user1    | user123   | General User            | Hanna Tesfaye   |
| user2    | user123   | General User            | Chala Gudeta    |
| user3    | user123   | General User            | Meron Fikru     |

> Log in as different roles to see the main menu change: the menu only lists
> actions the role is allowed to perform, and the service layer enforces the
> same rules again underneath the UI.

---

## Features

- **Role-based access control (RBAC)** for General Users, Technicians and
  Facility Administrators (see the matrix below).
- **Asset inventory**: serial numbers, building/room locations, operational
  status (`In Service`, `Under Maintenance`, `Faulty`, `Retired`), purchase
  date and a QR payload — the text encoded in the QR label stuck on the
  device (`SIT-ASSET|AST-003|SN:ELO-0982|ROOM:A-204|DC Power Supply`).
- **Fault-report tickets** with urgency (`Low`, `Medium`, `High`, `Critical`,
  each with a target first-response time) and the lifecycle
  `Reported -> Under Review -> In Repair -> Resolved`, including reopen.
  Illegal transitions (e.g. `Reported -> Resolved`) are rejected with an
  explanation. Reporting a fault automatically flags the asset as `Faulty`.
- **Scheduled maintenance cycles**: recurring plans per asset (e.g. "calibrate
  every 180 days") with next-due tracking, overdue detection and
  pause/resume. Recording a service pushes the cycle forward automatically.
- **Maintenance history / audit logs**: every service action is written to an
  institute-wide audit trail (who, what, when), and each ticket keeps its own
  working history (status changes, assignments, notes).
- **Summary reports**: MTTR overall and per urgency, open tickets by status
  and by urgency, oldest open tickets, asset status overview, overdue
  maintenance plans.

---

## RBAC matrix

| Capability                                  | General User | Technician | Facility Admin |
|---------------------------------------------|:---:|:---:|:---:|
| View assets / search                        | yes | yes | yes |
| Report a fault (create ticket)              | yes | yes | yes |
| View own tickets                            | yes | yes | yes |
| View all tickets                            | –   | yes | yes |
| Assign tickets to technicians               | –   | yes | yes |
| Advance ticket status / reopen / add notes  | –   | yes | yes |
| Update asset status                         | –   | yes | yes |
| View maintenance schedule                   | –   | yes | yes |
| Record maintenance service                  | –   | yes | yes |
| View summary reports (MTTR, open tickets)   | –   | yes | yes |
| Register new assets                         | –   | –   | yes |
| Retire assets                               | –   | –   | yes |
| Create / pause maintenance plans            | –   | –   | yes |
| View audit log                              | –   | –   | yes |
| Manage users (create, change roles)         | –   | –   | yes |

The policy lives in one place — `auth/AccessControl.java` — as a static
`Role -> Set<Permission>` matrix. Services call `AccessControl.check(...)`
before every protected operation; the UI calls `AccessControl.can(...)` to
filter its menus.

---

## Project structure

```
src/com/sit/tracker/
├── Main.java                 composition root: builds store + services, starts UI
├── model/                    entities and enums (pure data, no I/O)
│   ├── User, Asset, Ticket, MaintenancePlan, MaintenanceRecord, AuditLog
│   └── Role, Permission, AssetStatus, TicketStatus, Urgency, MaintenanceType
├── auth/                     RBAC: AccessControl (policy matrix), Session,
│                             AccessDeniedException
├── repository/               persistence abstraction
│   ├── Repository<K,T>       generic interface (swap-in point for a real DB)
│   ├── InMemoryRepository    thread-safe demo implementation
│   ├── DataStore             demo database + human-readable id generator
│   └── Identifiable          entities expose their id
├── service/                  business logic (all RBAC checks happen here)
│   ├── AuthService           login/logout, user management
│   ├── AssetService          inventory + status transitions + QR payload
│   ├── TicketService         ticket workflow with lifecycle guard
│   ├── MaintenanceService    plans, due/overdue, service records
│   ├── ReportService         MTTR, open tickets, asset overview
│   └── AuditService          audit trail writer/reader
├── ui/                       console front-end
│   ├── AppUI                 role-filtered menus and screens
│   ├── ConsoleIO             validated input helpers
│   └── AppContext            service bundle (wiring record)
└── demo/
    ├── DemoData              seeds users, assets, tickets, plans, history
    └── DemoSelfTest          27 built-in verification checks (--selftest)
```

**Design notes**

- Layered architecture: `model` <- `service` <- `ui`, with `repository` behind
  a generic interface. To move to a real database later, implement
  `Repository<K,T>` with JDBC/JPA and change one line in `Main.bootstrap()` —
  no service or UI code changes.
- All RBAC enforcement is centralized in `AccessControl`; adding a role or
  permission touches one file.
- The ticket lifecycle is a guarded state machine (`ALLOWED_TRANSITIONS` map);
  illegal moves fail fast with a message listing valid options.
- MTTR = average elapsed time from ticket creation to resolution, in hours,
  over all resolved tickets.
- Passwords are stored in plain text **for the demo only** — a production
  deployment would hash them and authenticate against the institute's
  directory service.

---

## Sample session (abbreviated)

```
=== Login ===
  1. Log in
  0. Exit
Choose: 1
Username: admin
Password: admin123
  > Welcome, Abebe Kebede [Facility Administrator]

=== Main Menu - Abebe Kebede [Facility Administrator] ===
   1. View all assets
   ...
  12. Summary report (MTTR / open tickets)
   0. Log out
Choose: 12
=== Summary Report ===
Mean Time to Repair (MTTR)
  Overall: 40.0 h  (based on 3 resolved ticket(s))
  By urgency:
    Critical     24.0 h
    High         48.0 h
Open tickets: 5
  By status : Reported 2 | Under Review 1 | In Repair 2
...
Overdue maintenance plans: 2
  PLN-002 | AST-004 | Preventive cleaning and dust check | due 2026-11-13 (10 days late)
```

The seeded dataset always produces live-looking numbers: tickets and
maintenance dates are relative to the moment the app starts.

---

## Build-up history (6 commits)

The repository tells the design story in six steps. Each commit compiles and
runs on its own, so you can check out any point in the history:

| # | Commit | What changed |
|---|--------|--------------|
| 1 | `Add single-file console prototype (Main.java)` | Everything — data, logic, menus — in one default-package `Main.java` with hardcoded demo data and `[DEBUG]` prints. |
| 2 | `Extract domain model into the com.sit.tracker.model package` | `Asset`, `Ticket` and their enums move into `src/com/sit/tracker/model/` and are imported from the console class; the single root file is deleted; `run.sh` / `run.bat` added. |
| 3 | `Classify storage: move all data behind a repository package` | New `repository/` package (`Repository` interface, thread-safe `InMemoryRepository`, `DataStore` with human-readable ids); entities implement `Identifiable`; Main keeps no lists or id counters of its own. |
| 4 | `Add service layer, audit trail and seeded demo data` | Business rules (fault reporting with auto-flag, guarded ticket lifecycle, assignment, notes) move from the console class into `service/`; every change is audited; `demo/DemoData` replaces hardcoded seed lists. |
| 5 | `Add maintenance scheduling, MTTR reports and the ui package` | Recurring maintenance plans with due/overdue tracking and service history; `ReportService` (MTTR, open-ticket stats); input handling and menus extracted into `ui/` (`ConsoleIO`, `AppUI`, `AppContext`). |
| 6 | `Add login with role-based access control; remove debug leftovers` | `User`/`Role`/`Permission` and the `auth/` RBAC matrix; login screen and role-filtered menus; services enforce permissions again underneath; built-in 27-check self-test; README and VS Code configs; `[DEBUG]` prints and the anonymous open menu removed. |
