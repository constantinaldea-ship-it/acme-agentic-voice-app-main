package com.voicebanking.bfa.location;

import java.util.List;

/**
 * Domain model for a bank branch location.
 *
 * <p>Loaded from the harvested Deutsche Bank Filialfinder dataset (JSON).
 * This is the internal domain model — the API response uses {@link BranchDto}.</p>
 *
 * @param branchId             Unique identifier from the Filialfinder API
 * @param name                 Descriptive name (e.g., "Deutsche Bank Wandsbeker Marktstraße 1, Hamburg")
 * @param brand                Bank brand: "Deutsche Bank" or "Postbank"
 * @param address              Street address with house number
 * @param city                 City name (normalized, e.g., "Frankfurt am Main")
 * @param postalCode           German postal code (PLZ)
 * @param latitude             GPS latitude
 * @param longitude            GPS longitude
 * @param phone                Phone number (may be null, especially for Postbank)
 * @param openingHours         Human-readable opening hours (nullable — not all branches publish hours)
 * @param wheelchairAccessible Whether the location has wheelchair/barrier-free access
 * @param selfServices         Self-service offerings (e.g., "Bargeldauszahlung", "Kontoauszüge drucken")
 * @param branchServices       In-branch services (e.g., "Wertschließfächer", "Fremde Währungen")
 * @param advisoryAvailable    Whether the branch offers advisory/consultation services
 * @param transitInfo          Public transit directions (nullable)
 * @param parkingInfo          Parking information (nullable)
 *
 * @author Augment Agent
 * @since 2026-02-07
 */
public record Branch(
    String branchId,
    String name,
    String brand,
    String address,
    String city,
    String postalCode,
    double latitude,
    double longitude,
    String phone,
    String openingHours,
    boolean wheelchairAccessible,
    List<String> selfServices,
    List<String> branchServices,
    boolean advisoryAvailable,
    String transitInfo,
    String parkingInfo
) {}
