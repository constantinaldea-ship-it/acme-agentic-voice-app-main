package com.voicebanking.agent.nonbanking.domain;

/**
 * Types of insurance coverage available through Acme Bank card partnerships.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum InsuranceType {
    
    TRAVEL_MEDICAL("Travel Medical Insurance", "Auslandskrankenversicherung",
                   "Emergency medical coverage when traveling abroad"),
    
    TRIP_CANCELLATION("Trip Cancellation Insurance", "Reiserücktrittsversicherung",
                      "Reimburse cancelled trip costs"),
    
    PURCHASE_PROTECTION("Purchase Protection", "Einkaufsversicherung",
                        "Protection for purchases made with the card"),
    
    RENTAL_CAR_CDW("Rental Car CDW", "Mietwagen-Kasko",
                   "Collision damage waiver for rental cars"),
    
    LIABILITY("Liability Insurance", "Haftpflichtversicherung",
              "Personal liability coverage"),
    
    LUGGAGE("Luggage Insurance", "Gepäckversicherung",
            "Coverage for lost or damaged luggage"),
    
    FLIGHT_DELAY("Flight Delay Insurance", "Flugverspätungsversicherung",
                 "Compensation for significant flight delays");

    private final String nameEn;
    private final String nameDe;
    private final String description;

    InsuranceType(String nameEn, String nameDe, String description) {
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
     * Parse insurance type from string, case-insensitive.
     */
    public static InsuranceType fromString(String value) {
        if (value == null) return null;
        
        String upper = value.toUpperCase().replace(" ", "_").replace("-", "_");
        for (InsuranceType type : values()) {
            if (type.name().equals(upper)) {
                return type;
            }
        }
        
        // Try partial matching
        String lower = value.toLowerCase();
        if (lower.contains("travel") && lower.contains("medical")) return TRAVEL_MEDICAL;
        if (lower.contains("cancellation")) return TRIP_CANCELLATION;
        if (lower.contains("purchase")) return PURCHASE_PROTECTION;
        if (lower.contains("rental") || lower.contains("cdw")) return RENTAL_CAR_CDW;
        if (lower.contains("liability")) return LIABILITY;
        if (lower.contains("luggage") || lower.contains("baggage")) return LUGGAGE;
        if (lower.contains("flight") && lower.contains("delay")) return FLIGHT_DELAY;
        
        return null;
    }
}
