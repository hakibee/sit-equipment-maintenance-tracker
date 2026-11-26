package com.sit.tracker.ui;

import com.sit.tracker.service.AssetService;
import com.sit.tracker.service.AuditService;
import com.sit.tracker.service.MaintenanceService;
import com.sit.tracker.service.ReportService;
import com.sit.tracker.service.TicketService;

/**
 * Bundle of all services handed to the console UI — the composition root's
 * wiring in one immutable record.
 */
public record AppContext(
        AssetService assets,
        TicketService tickets,
        MaintenanceService maintenance,
        ReportService reports,
        AuditService audit) {
}
