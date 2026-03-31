package com.voicebanking.mcp.location;

import java.util.List;

/**
 * Domain model for a bank branch location.
 *
 * <p>Loaded from the harvested Deutsche Bank Filialfinder dataset (JSON).
 * Copied from {@code bfa-service-resource} for spike isolation.</p>
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
    String transitInfo,
    String parkingInfo
) {}
