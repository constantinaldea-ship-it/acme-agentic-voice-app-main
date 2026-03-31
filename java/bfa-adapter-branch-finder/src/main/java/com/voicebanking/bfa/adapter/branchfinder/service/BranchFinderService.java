package com.voicebanking.bfa.adapter.branchfinder.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Branch search domain service.
 *
 * <p>Encapsulates all branch-finding business logic.  In production this
 * would call the on-prem Branch/Location Glue API via the Token Broker
 * Service.  In this demo it queries hard-coded seed data.</p>
 *
 * @author Copilot
 * @since 2026-03-01
 */
@Service
public class BranchFinderService {

    private static final Logger log = LoggerFactory.getLogger(BranchFinderService.class);

    /** Hard-coded demo branch data keyed by city (lower-case). */
    private static final Map<String, List<Map<String, Object>>> BRANCH_DATA = buildBranchData();

    /**
     * Search branches by city.
     *
     * @param city          city name (required, case-insensitive)
     * @param limit         max results (1-50, default 5)
     * @param correlationId for tracing
     * @return normalised result map
     */
    public Map<String, Object> search(String city, int limit, String correlationId) {
        log.debug("[{}] Branch search: city='{}' limit={}", correlationId, city, limit);

        List<Map<String, Object>> matches = BRANCH_DATA.getOrDefault(
                city.toLowerCase(), List.of());

        List<Map<String, Object>> truncated = matches.stream()
                .limit(limit)
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("city", city);
        result.put("totalMatches", matches.size());
        result.put("count", truncated.size());
        result.put("branches", truncated);
        return result;
    }

    // ── demo seed data ──────────────────────────────────────────────

    private static Map<String, List<Map<String, Object>>> buildBranchData() {
        Map<String, List<Map<String, Object>>> data = new HashMap<>();

        data.put("frankfurt", List.of(
                branch("FRA-001", "Frankfurt Hauptwache", "Große Gallusstraße 10-14",
                        "60311", "Frankfurt am Main", 50.1109, 8.6821,
                        "DE89 3704 0044 0532 0130 00"),
                branch("FRA-002", "Frankfurt Sachsenhausen", "Schweizer Straße 24",
                        "60594", "Frankfurt am Main", 50.1001, 8.6850,
                        "DE89 3704 0044 0532 0131 00"),
                branch("FRA-003", "Frankfurt Bockenheim", "Leipziger Straße 55",
                        "60487", "Frankfurt am Main", 50.1187, 8.6383,
                        "DE89 3704 0044 0532 0132 00")
        ));

        data.put("berlin", List.of(
                branch("BER-001", "Berlin Mitte", "Unter den Linden 13-15",
                        "10117", "Berlin", 52.5170, 13.3889,
                        "DE89 3704 0044 0532 0140 00"),
                branch("BER-002", "Berlin Charlottenburg", "Kurfürstendamm 22",
                        "10719", "Berlin", 52.5026, 13.3275,
                        "DE89 3704 0044 0532 0141 00")
        ));

        data.put("hamburg", List.of(
                branch("HAM-001", "Hamburg Jungfernstieg", "Jungfernstieg 22",
                        "20354", "Hamburg", 53.5533, 9.9925,
                        "DE89 3704 0044 0532 0150 00")
        ));

        data.put("munich", List.of(
                branch("MUC-001", "Munich Marienplatz", "Marienplatz 1",
                        "80331", "München", 48.1374, 11.5755,
                        "DE89 3704 0044 0532 0160 00"),
                branch("MUC-002", "Munich Schwabing", "Leopoldstraße 80",
                        "80802", "München", 48.1627, 11.5858,
                        "DE89 3704 0044 0532 0161 00")
        ));

        return Map.copyOf(data);
    }

    private static Map<String, Object> branch(String id, String name, String address,
                                               String postalCode, String city,
                                               double lat, double lon,
                                               String accountNumber) {
        return Map.of(
                "branchId", id,
                "name", name,
                "address", address,
                "postalCode", postalCode,
                "city", city,
                "latitude", lat,
                "longitude", lon,
                "accountNumber", accountNumber
        );
    }
}
