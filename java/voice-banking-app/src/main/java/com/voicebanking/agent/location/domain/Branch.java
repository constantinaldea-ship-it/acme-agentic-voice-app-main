package com.voicebanking.agent.location.domain;

/**
 * Represents a bank branch or ATM location.
 * 
 * @param branchId Unique identifier for the branch
 * @param name Display name of the branch
 * @param type Type of location: "branch", "atm", or "flagship"
 * @param address Street address
 * @param city City name
 * @param postalCode Postal/ZIP code
 * @param latitude GPS latitude coordinate
 * @param longitude GPS longitude coordinate
 * @param distanceKm Distance from search location in kilometers
 * @param openingHours Opening hours description
 * @param phone Phone number (null for ATMs)
 * @param wheelchairAccessible Whether location is wheelchair accessible
 */
public record Branch(
    String branchId,
    String name,
    String type,
    String address,
    String city,
    String postalCode,
    double latitude,
    double longitude,
    double distanceKm,
    String openingHours,
    String phone,
    boolean wheelchairAccessible
) {}
