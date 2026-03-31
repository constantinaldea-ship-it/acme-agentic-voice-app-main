package com.voicebanking.agent.nonbanking.domain;

/**
 * Types of travel benefits available through Acme Bank card partnerships.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum TravelBenefitType {
    
    AIRPORT_LOUNGE("Airport Lounge Access", "Flughafen-Lounge-Zugang",
                   "Access to airport lounges worldwide"),
    
    PRIORITY_PASS("Priority Pass", "Priority Pass",
                  "Premium lounge access network"),
    
    MILES_AND_MORE("Miles & More", "Miles & More",
                   "Airline miles rewards program"),
    
    TRAVEL_CONCIERGE("Travel Concierge", "Reise-Concierge",
                     "Personal travel booking assistance"),
    
    FAST_TRACK("Fast Track Security", "Fast Track Sicherheit",
               "Skip the queue at airport security"),
    
    HOTEL_BENEFITS("Hotel Benefits", "Hotel-Vorteile",
                   "Upgrades and perks at partner hotels"),
    
    CAR_RENTAL("Car Rental Benefits", "Mietwagen-Vorteile",
               "Discounts and upgrades at car rentals"),
    
    TRAVEL_ASSISTANCE("Travel Assistance", "Reiseassistenz",
                      "24/7 emergency travel support");

    private final String nameEn;
    private final String nameDe;
    private final String description;

    TravelBenefitType(String nameEn, String nameDe, String description) {
        this.nameEn = nameEn;
        this.nameDe = nameDe;
        this.description = description;
    }

    public String getNameEn() { return nameEn; }
    public String getNameDe() { return nameDe; }
    public String getDescription() { return description; }

    public String getName(String lang) {
        return "de".equalsIgnoreCase(lang) ? nameDe : nameEn;
    }

    /**
     * Parse travel benefit type from string, case-insensitive.
     */
    public static TravelBenefitType fromString(String value) {
        if (value == null) return null;
        
        String upper = value.toUpperCase().replace(" ", "_").replace("-", "_");
        for (TravelBenefitType type : values()) {
            if (type.name().equals(upper)) {
                return type;
            }
        }
        
        // Try partial matching
        String lower = value.toLowerCase();
        if (lower.contains("lounge")) return AIRPORT_LOUNGE;
        if (lower.contains("priority")) return PRIORITY_PASS;
        if (lower.contains("miles")) return MILES_AND_MORE;
        if (lower.contains("concierge")) return TRAVEL_CONCIERGE;
        if (lower.contains("fast") || lower.contains("track")) return FAST_TRACK;
        if (lower.contains("hotel")) return HOTEL_BENEFITS;
        if (lower.contains("car") || lower.contains("rental")) return CAR_RENTAL;
        if (lower.contains("assistance")) return TRAVEL_ASSISTANCE;
        
        return null;
    }
}
