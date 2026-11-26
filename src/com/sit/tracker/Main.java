package com.sit.tracker;

import com.sit.tracker.demo.DemoData;
import com.sit.tracker.repository.DataStore;
import com.sit.tracker.service.AssetService;
import com.sit.tracker.service.AuditService;
import com.sit.tracker.service.MaintenanceService;
import com.sit.tracker.service.ReportService;
import com.sit.tracker.service.TicketService;
import com.sit.tracker.ui.AppContext;
import com.sit.tracker.ui.AppUI;
import com.sit.tracker.ui.ConsoleIO;

/**
 * Composition root: builds the in-memory demo database, seeds it, wires the
 * services and starts the console UI. All menus and screens live in
 * {@link AppUI}; this class is only wiring.
 */
public final class Main {

    private Main() {
        // not instantiable
    }

    public static void main(String[] args) {
        new AppUI(new ConsoleIO(), bootstrap()).run();
    }

    /** Wires the object graph: store -> services -> UI context. */
    static AppContext bootstrap() {
        DataStore store = new DataStore();
        DemoData.seed(store);

        AuditService audit = new AuditService(store);
        AssetService assets = new AssetService(store, audit);
        MaintenanceService maintenance = new MaintenanceService(store, audit);
        TicketService tickets = new TicketService(store, audit, assets);
        ReportService reports = new ReportService(store);

        return new AppContext(assets, tickets, maintenance, reports, audit);
    }
}
