package com.sit.tracker;

import com.sit.tracker.demo.DemoData;
import com.sit.tracker.demo.DemoSelfTest;
import com.sit.tracker.service.AssetService;
import com.sit.tracker.service.AuditService;
import com.sit.tracker.service.AuthService;
import com.sit.tracker.service.MaintenanceService;
import com.sit.tracker.service.ReportService;
import com.sit.tracker.service.TicketService;
import com.sit.tracker.repository.DataStore;
import com.sit.tracker.ui.AppContext;
import com.sit.tracker.ui.AppUI;
import com.sit.tracker.ui.ConsoleIO;

/**
 * Composition root: builds the in-memory demo database, seeds it and starts
 * the console UI. Run with {@code --selftest} to execute the built-in
 * verification suite instead of the interactive UI.
 */
public final class Main {

    private Main() {
        // not instantiable
    }

    public static void main(String[] args) {
        AppContext ctx = bootstrap();

        if (args.length > 0 && "--selftest".equals(args[0])) {
            boolean ok = new DemoSelfTest(ctx).run();
            System.exit(ok ? 0 : 1);
            return;
        }

        new AppUI(new ConsoleIO(), ctx).run();
    }

    /** Wires the object graph: store -> services -> UI context. */
    static AppContext bootstrap() {
        DataStore store = new DataStore();
        DemoData.seed(store);

        AuditService audit = new AuditService(store);
        AuthService auth = new AuthService(store, audit);
        AssetService assets = new AssetService(store, audit);
        MaintenanceService maintenance = new MaintenanceService(store, audit);
        TicketService tickets = new TicketService(store, audit, assets);
        ReportService reports = new ReportService(store);

        return new AppContext(auth, assets, tickets, maintenance, reports, audit);
    }
}
