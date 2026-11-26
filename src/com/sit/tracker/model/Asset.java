package com.sit.tracker.model;

import com.sit.tracker.repository.Identifiable;

import java.time.LocalDate;

/**
 * A laboratory asset in the institute's inventory.
 *
 * <p>Every asset carries a serial number, a room location, an operational
 * status and a QR payload — the text that is encoded in the QR label stuck on
 * the physical device. Scanning the label with any QR reader yields this
 * payload, which the app uses to look the asset up by id.</p>
 */
public class Asset implements Identifiable<String> {
    private final String id;            // e.g. AST-001
    private final String serialNumber;  // manufacturer serial / institute tag
    private final String name;          // e.g. "Digital Oscilloscope"
    private final String category;      // e.g. "Electronics", "IT", "Mechanical"
    private final String building;      // e.g. "A"
    private final String roomNumber;    // e.g. "204"
    private AssetStatus status;
    private final LocalDate purchaseDate; // may be null when unknown

    public Asset(String id, String serialNumber, String name, String category,
                 String building, String roomNumber, AssetStatus status, LocalDate purchaseDate) {
        this.id = id;
        this.serialNumber = serialNumber;
        this.name = name;
        this.category = category;
        this.building = building;
        this.roomNumber = roomNumber;
        this.status = status;
        this.purchaseDate = purchaseDate;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getBuilding() {
        return building;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public void setStatus(AssetStatus status) {
        this.status = status;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    /** Compact location label, e.g. "A-204" (Building A, Room 204). */
    public String getRoomLabel() {
        return building + "-" + roomNumber;
    }

    /**
     * The text encoded in the asset's printed QR label. Formats:
     * SIT-ASSET|AST-003|SN:ELO-2021-0451|ROOM:A-204|DC Power Supply
     */
    public String getQrPayload() {
        return String.format("SIT-ASSET|%s|SN:%s|ROOM:%s|%s",
                id, serialNumber, getRoomLabel(), name);
    }

    /** Human-readable one-line summary used by tables and ticket screens. */
    public String summaryLine() {
        return String.format("%s | %s | %s | %s", id, name, getRoomLabel(), status.getLabel());
    }
}
