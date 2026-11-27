package com.sit.tracker.service;

import com.sit.tracker.auth.AccessControl;
import com.sit.tracker.auth.AccessDeniedException;
import com.sit.tracker.auth.Session;
import com.sit.tracker.model.Asset;
import com.sit.tracker.model.AssetStatus;
import com.sit.tracker.model.Permission;
import com.sit.tracker.repository.DataStore;
import com.sit.tracker.repository.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Asset inventory management: registration, lookup and status changes.
 */
public class AssetService {

    private final DataStore store;
    private final AuditService audit;
    private final Repository<String, Asset> assets;

    public AssetService(DataStore store, AuditService audit) {
        this.store = store;
        this.audit = audit;
        this.assets = store.assets();
    }

    /** Register a new asset (administrator only). Serial numbers must be unique. */
    public Asset registerAsset(Session session, String serialNumber, String name, String category,
                               String building, String roomNumber, LocalDate purchaseDate)
            throws AccessDeniedException {
        AccessControl.check(session, Permission.ADD_ASSET);

        boolean serialTaken = assets.findAll().stream()
                .anyMatch(a -> a.getSerialNumber().equalsIgnoreCase(serialNumber.trim()));
        if (serialTaken) {
            throw new IllegalArgumentException("Serial number '" + serialNumber + "' is already registered.");
        }

        Asset asset = new Asset(store.nextId("AST"), serialNumber.trim(), name.trim(), category.trim(),
                building.trim().toUpperCase(Locale.ROOT), roomNumber.trim(), AssetStatus.IN_SERVICE, purchaseDate);
        assets.save(asset);
        audit.log(session, "ASSET_REGISTERED", "ASSET", asset.getId(),
                asset.getName() + " (" + asset.getSerialNumber() + ") at " + asset.getRoomLabel());
        return asset;
    }

    /** All assets (any logged-in role). */
    public List<Asset> findAll(Session session) throws AccessDeniedException {
        AccessControl.check(session, Permission.VIEW_ASSETS);
        return assets.findAll();
    }

    /** Free-text search across id, serial number, name, room and category. */
    public List<Asset> search(Session session, String text) throws AccessDeniedException {
        AccessControl.check(session, Permission.VIEW_ASSETS);
        String q = text.trim().toLowerCase(Locale.ROOT);
        return assets.findAll().stream()
                .filter(a -> (a.getId() + " " + a.getSerialNumber() + " " + a.getName() + " "
                        + a.getCategory() + " " + a.getRoomLabel()).toLowerCase(Locale.ROOT)
                        .contains(q))
                .toList();
    }

    public Optional<Asset> findById(String assetId) {
        return assets.findById(assetId.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Change an asset's operational status. Technicians may set the working
     * statuses; retiring an asset is reserved for administrators.
     */
    public Asset updateStatus(Session session, String assetId, AssetStatus newStatus)
            throws AccessDeniedException {
        AccessControl.check(session,
                newStatus == AssetStatus.RETIRED ? Permission.RETIRE_ASSET : Permission.UPDATE_ASSET_STATUS);

        Asset asset = findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("No asset with id " + assetId));

        AssetStatus oldStatus = asset.getStatus();
        if (oldStatus == newStatus) {
            throw new IllegalArgumentException(asset.getId() + " is already " + newStatus.getLabel() + ".");
        }

        asset.setStatus(newStatus);
        audit.log(session, "ASSET_STATUS_CHANGED", "ASSET", asset.getId(),
                asset.getName() + ": " + oldStatus.getLabel() + " -> " + newStatus.getLabel());
        return asset;
    }

    /**
     * Internal helper used by {@link TicketService#reportFault}: flag the
     * asset as FAULTY when a fault report is filed. Not permission-guarded
     * because it is a system consequence of the (already authorized) report,
     * but it is audited like every other change.
     */
    void markFaultyInternal(Session reportingSession, String assetId) {
        findById(assetId).ifPresent(asset -> {
            if (asset.getStatus() == AssetStatus.IN_SERVICE
                    || asset.getStatus() == AssetStatus.UNDER_MAINTENANCE) {
                asset.setStatus(AssetStatus.FAULTY);
                audit.log(reportingSession, "ASSET_STATUS_CHANGED", "ASSET", asset.getId(),
                        asset.getName() + " -> Faulty (auto: fault ticket reported)");
            }
        });
    }
}
