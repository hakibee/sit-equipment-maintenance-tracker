package com.sit.tracker.service;

import com.sit.tracker.model.Asset;
import com.sit.tracker.model.AssetStatus;
import com.sit.tracker.repository.DataStore;
import com.sit.tracker.repository.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Asset inventory management: registration, lookup and status changes.
 * (Permission checks arrive with the auth layer; for now every menu action
 * is available to whoever runs the app.)
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

    /** Register a new asset. Serial numbers must be unique. */
    public Asset registerAsset(String serialNumber, String name, String category,
                               String building, String roomNumber, LocalDate purchaseDate) {
        boolean serialTaken = assets.findAll().stream()
                .anyMatch(a -> a.getSerialNumber().equalsIgnoreCase(serialNumber.trim()));
        if (serialTaken) {
            throw new IllegalArgumentException("Serial number '" + serialNumber + "' is already registered.");
        }

        Asset asset = new Asset(store.nextId("AST"), serialNumber.trim(), name.trim(), category.trim(),
                building.trim().toUpperCase(Locale.ROOT), roomNumber.trim(), AssetStatus.IN_SERVICE, purchaseDate);
        assets.save(asset);
        audit.log("SYSTEM", "Staff", "ASSET_REGISTERED", "ASSET", asset.getId(),
                asset.getName() + " (" + asset.getSerialNumber() + ") at " + asset.getRoomLabel());
        return asset;
    }

    /** All assets. */
    public List<Asset> findAll() {
        return assets.findAll();
    }

    /** Free-text search across id, serial number, name, room and category. */
    public List<Asset> search(String text) {
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

    /** Change an asset's operational status. */
    public Asset updateStatus(String assetId, AssetStatus newStatus) {
        Asset asset = findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("No asset with id " + assetId));

        AssetStatus oldStatus = asset.getStatus();
        if (oldStatus == newStatus) {
            throw new IllegalArgumentException(asset.getId() + " is already " + newStatus.getLabel() + ".");
        }

        asset.setStatus(newStatus);
        audit.log("SYSTEM", "Staff", "ASSET_STATUS_CHANGED", "ASSET", asset.getId(),
                asset.getName() + ": " + oldStatus.getLabel() + " -> " + newStatus.getLabel());
        return asset;
    }

    /**
     * Internal helper used by {@link TicketService#reportFault}: flag the
     * asset as FAULTY when a fault report is filed. Audited like every other
     * change.
     */
    void markFaultyInternal(String reportingActor, String assetId) {
        findById(assetId).ifPresent(asset -> {
            if (asset.getStatus() == AssetStatus.IN_SERVICE
                    || asset.getStatus() == AssetStatus.UNDER_MAINTENANCE) {
                asset.setStatus(AssetStatus.FAULTY);
                audit.log(reportingActor, "Staff", "ASSET_STATUS_CHANGED", "ASSET", asset.getId(),
                        asset.getName() + " -> Faulty (auto: fault ticket reported)");
            }
        });
    }
}
