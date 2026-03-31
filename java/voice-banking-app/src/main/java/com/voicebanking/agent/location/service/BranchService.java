package com.voicebanking.agent.location.service;

import com.voicebanking.agent.location.domain.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Branch Location Service
 * 
 * <p>Provides branch and ATM location data with distance-based search.
 * Uses mock data for Acme Bank locations in Germany.</p>
 */
@Service
public class BranchService {

    private static final Logger log = LoggerFactory.getLogger(BranchService.class);

    private final List<Branch> branches;

    public BranchService() {
        this.branches = seedBranches();
        log.info("BranchService initialized with {} locations", branches.size());
    }

    /**
     * Find branches near a location.
     *
     * @param latitude User's latitude
     * @param longitude User's longitude
     * @param radiusKm Search radius in kilometers
     * @param limit Maximum results to return
     * @param type Filter by type ("branch", "atm", "flagship", or null for all)
     * @return List of branches sorted by distance
     */
    public List<Branch> findNearby(
            double latitude,
            double longitude,
            double radiusKm,
            int limit,
            String type) {

        log.debug("Finding branches near ({}, {}) within {} km, type={}", 
            latitude, longitude, radiusKm, type);

        return branches.stream()
            // Calculate distance for each branch
            .map(b -> new Branch(
                b.branchId(),
                b.name(),
                b.type(),
                b.address(),
                b.city(),
                b.postalCode(),
                b.latitude(),
                b.longitude(),
                calculateDistance(latitude, longitude, b.latitude(), b.longitude()),
                b.openingHours(),
                b.phone(),
                b.wheelchairAccessible()
            ))
            // Filter by radius
            .filter(b -> b.distanceKm() <= radiusKm)
            // Filter by type if specified
            .filter(b -> type == null || "all".equalsIgnoreCase(type) || 
                         b.type().equalsIgnoreCase(type))
            // Sort by distance (closest first)
            .sorted(Comparator.comparingDouble(Branch::distanceKm))
            // Limit results
            .limit(limit)
            .toList();
    }

    /**
     * Get all branches (for testing/admin).
     */
    public List<Branch> getAllBranches() {
        return branches;
    }

    /**
     * Calculate distance between two points using Haversine formula.
     *
     * @return Distance in kilometers
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371; // Earth's radius in km

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return Math.round(R * c * 100.0) / 100.0; // Round to 2 decimal places
    }

    /**
     * Seed mock branch data (Acme Bank locations in Germany).
     */
    private List<Branch> seedBranches() {
        return List.of(
            // Frankfurt (Main financial hub)
            new Branch(
                "branch-fra-001",
                "Acme Bank Filiale Frankfurt Zentrum",
                "flagship",
                "Taunusanlage 12",
                "Frankfurt am Main",
                "60325",
                50.1109,
                8.6821,
                0,
                "Mo-Fr 09:00-18:00",
                "+49 69 910-00",
                true
            ),
            new Branch(
                "branch-fra-002",
                "Acme Bank Filiale Sachsenhausen",
                "branch",
                "Schweizer Straße 45",
                "Frankfurt am Main",
                "60594",
                50.1001,
                8.6833,
                0,
                "Mo-Fr 09:00-17:00",
                "+49 69 910-1001",
                true
            ),
            new Branch(
                "atm-fra-001",
                "Acme Bank Geldautomat Hauptbahnhof",
                "atm",
                "Am Hauptbahnhof 1",
                "Frankfurt am Main",
                "60329",
                50.1068,
                8.6627,
                0,
                "24/7",
                null,
                true
            ),
            new Branch(
                "atm-fra-002",
                "Acme Bank Geldautomat Zeil",
                "atm",
                "Zeil 112",
                "Frankfurt am Main",
                "60313",
                50.1140,
                8.6850,
                0,
                "24/7",
                null,
                true
            ),

            // Munich
            new Branch(
                "branch-muc-001",
                "Acme Bank Filiale München Marienplatz",
                "flagship",
                "Marienplatz 22",
                "München",
                "80331",
                48.1371,
                11.5754,
                0,
                "Mo-Fr 09:00-18:00, Sa 09:00-13:00",
                "+49 89 2180-0",
                true
            ),
            new Branch(
                "atm-muc-001",
                "Acme Bank Geldautomat Hauptbahnhof",
                "atm",
                "Bayerstraße 10",
                "München",
                "80335",
                48.1402,
                11.5600,
                0,
                "24/7",
                null,
                true
            ),

            // Berlin
            new Branch(
                "branch-ber-001",
                "Acme Bank Filiale Berlin Unter den Linden",
                "flagship",
                "Unter den Linden 13-15",
                "Berlin",
                "10117",
                52.5170,
                13.3888,
                0,
                "Mo-Fr 09:00-18:00",
                "+49 30 3407-0",
                true
            ),
            new Branch(
                "branch-ber-002",
                "Acme Bank Filiale Charlottenburg",
                "branch",
                "Kurfürstendamm 185",
                "Berlin",
                "10707",
                52.5008,
                13.3108,
                0,
                "Mo-Fr 09:00-17:00",
                "+49 30 3407-1001",
                true
            ),
            new Branch(
                "atm-ber-001",
                "Acme Bank Geldautomat Alexanderplatz",
                "atm",
                "Alexanderplatz 3",
                "Berlin",
                "10178",
                52.5219,
                13.4132,
                0,
                "24/7",
                null,
                false
            ),

            // Hamburg
            new Branch(
                "branch-ham-001",
                "Acme Bank Filiale Hamburg Jungfernstieg",
                "flagship",
                "Jungfernstieg 22",
                "Hamburg",
                "20354",
                53.5530,
                9.9930,
                0,
                "Mo-Fr 09:00-18:00",
                "+49 40 3692-0",
                true
            )
        );
    }
}
