package com.voicebanking.bfa.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO for service contact information.
 * 
 * <p>Provides contact information for various banking and non-banking services
 * including emergency contacts, customer service, and partner contacts.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-03
 */
@Schema(description = "Service contact information")
public record ServiceContactDto(
    @Schema(description = "List of service contacts")
    List<ServiceContact> contacts,
    
    @Schema(description = "Number of contacts returned", example = "3")
    int count,
    
    @Schema(description = "Emergency contact (always available)")
    EmergencyContact emergencyContact,
    
    @Schema(description = "Voice-friendly response")
    String voiceResponse,
    
    @Schema(description = "Regulatory disclaimer")
    String disclaimer
) {
    
    /**
     * Individual service contact.
     */
    @Schema(description = "Service contact details")
    public record ServiceContact(
        @Schema(description = "Contact type", example = "CARD_SERVICES")
        String contactType,
        
        @Schema(description = "Service name", example = "Card Services")
        String serviceName,
        
        @Schema(description = "Primary phone number", example = "+49 69 910-10000")
        String phone,
        
        @Schema(description = "Toll-free phone if available", example = "+49 800 123-4567")
        String tollFreePhone,
        
        @Schema(description = "International phone", example = "+49 69 910-10000")
        String internationalPhone,
        
        @Schema(description = "Email address", example = "service@acmebank.example")
        String email,
        
        @Schema(description = "Website URL")
        String websiteUrl,
        
        @Schema(description = "Business hours", example = "Mon-Fri 8:00-20:00, Sat 9:00-14:00")
        String businessHours,
        
        @Schema(description = "Available languages")
        List<String> languages,
        
        @Schema(description = "Additional notes")
        String notes
    ) {}
    
    /**
     * Emergency contact (24/7).
     */
    @Schema(description = "24/7 Emergency contact information")
    public record EmergencyContact(
        @Schema(description = "Card blocking hotline", example = "+49 116 116")
        String cardBlockingHotline,
        
        @Schema(description = "Card blocking international", example = "+49 89 411 116 116")
        String cardBlockingInternational,
        
        @Schema(description = "General emergency line", example = "+49 69 910-10099")
        String generalEmergency,
        
        @Schema(description = "Is 24/7 available")
        boolean available24x7,
        
        @Schema(description = "Available languages")
        List<String> languages
    ) {
        public static EmergencyContact acmeBankDefault() {
            return new EmergencyContact(
                "+49 116 116",
                "+49 89 411 116 116",
                "+49 69 910-10099",
                true,
                List.of("German", "English", "French", "Spanish")
            );
        }
    }
    
    /**
     * Contact type enumeration.
     */
    public enum ContactType {
        EMERGENCY("Emergency", "Notfall"),
        CARD_SERVICES("Card Services", "Kartenservice"),
        ACCOUNT_SERVICES("Account Services", "Kontoservice"),
        CONCIERGE("Concierge", "Concierge"),
        INSURANCE_CLAIMS("Insurance Claims", "Versicherungsschäden"),
        TRAVEL_ASSISTANCE("Travel Assistance", "Reise-Assistenz"),
        TECHNICAL_SUPPORT("Technical Support", "Technischer Support"),
        COMPLAINTS("Complaints", "Beschwerden");
        
        private final String nameEn;
        private final String nameDe;
        
        ContactType(String nameEn, String nameDe) {
            this.nameEn = nameEn;
            this.nameDe = nameDe;
        }
        
        public String getName(String language) {
            return "de".equalsIgnoreCase(language) ? nameDe : nameEn;
        }
    }
    
    private static final String DISCLAIMER_EN = "Standard call charges may apply. International calls may incur additional charges.";
    private static final String DISCLAIMER_DE = "Es können Standard-Gesprächsgebühren anfallen. Bei internationalen Anrufen können zusätzliche Gebühren anfallen.";
    
    /**
     * Factory method to create ServiceContactDto.
     */
    public static ServiceContactDto of(
            List<ServiceContact> contacts,
            String language
    ) {
        EmergencyContact emergency = EmergencyContact.acmeBankDefault();
        String voiceResponse = formatVoiceResponse(contacts, emergency, language);
        String disclaimer = "de".equalsIgnoreCase(language) ? DISCLAIMER_DE : DISCLAIMER_EN;
        
        return new ServiceContactDto(
            contacts,
            contacts.size(),
            emergency,
            voiceResponse,
            disclaimer
        );
    }
    
    /**
     * Factory method for emergency contact only.
     */
    public static ServiceContactDto emergencyOnly(String language) {
        EmergencyContact emergency = EmergencyContact.acmeBankDefault();
        String voiceResponse = formatEmergencyVoiceResponse(emergency, language);
        String disclaimer = "de".equalsIgnoreCase(language) ? DISCLAIMER_DE : DISCLAIMER_EN;
        
        return new ServiceContactDto(
            List.of(),
            0,
            emergency,
            voiceResponse,
            disclaimer
        );
    }
    
    /**
     * Factory method for single contact type.
     */
    public static ServiceContactDto ofSingle(
            ServiceContact contact,
            String language
    ) {
        EmergencyContact emergency = EmergencyContact.acmeBankDefault();
        String voiceResponse = formatSingleContactVoiceResponse(contact, language);
        String disclaimer = "de".equalsIgnoreCase(language) ? DISCLAIMER_DE : DISCLAIMER_EN;
        
        return new ServiceContactDto(
            List.of(contact),
            1,
            emergency,
            voiceResponse,
            disclaimer
        );
    }
    
    private static String formatVoiceResponse(
            List<ServiceContact> contacts, EmergencyContact emergency, String language
    ) {
        if ("de".equalsIgnoreCase(language)) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Ich habe %d Kontakte für Sie. ", contacts.size()));
            
            for (ServiceContact contact : contacts) {
                sb.append(String.format("%s: %s. ", contact.serviceName(), contact.phone()));
            }
            
            sb.append(String.format("Für Notfälle rufen Sie %s an, 24 Stunden, 7 Tage die Woche.", 
                emergency.cardBlockingHotline()));
            
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("I have %d contacts for you. ", contacts.size()));
            
            for (ServiceContact contact : contacts) {
                sb.append(String.format("%s: %s. ", contact.serviceName(), contact.phone()));
            }
            
            sb.append(String.format("For emergencies, call %s, available 24/7.", 
                emergency.cardBlockingHotline()));
            
            return sb.toString();
        }
    }
    
    private static String formatEmergencyVoiceResponse(EmergencyContact emergency, String language) {
        if ("de".equalsIgnoreCase(language)) {
            return String.format(
                "Für Notfälle und Kartensperrung rufen Sie %s an. Diese Nummer ist rund um die Uhr erreichbar. Aus dem Ausland wählen Sie %s.",
                emergency.cardBlockingHotline(),
                emergency.cardBlockingInternational()
            );
        } else {
            return String.format(
                "For emergencies and card blocking, call %s. This number is available 24/7. From abroad, dial %s.",
                emergency.cardBlockingHotline(),
                emergency.cardBlockingInternational()
            );
        }
    }
    
    private static String formatSingleContactVoiceResponse(ServiceContact contact, String language) {
        if ("de".equalsIgnoreCase(language)) {
            return String.format(
                "Für %s können Sie %s anrufen. Erreichbar %s. %s",
                contact.serviceName(),
                contact.tollFreePhone() != null ? contact.tollFreePhone() : contact.phone(),
                contact.businessHours(),
                contact.notes() != null ? contact.notes() : ""
            );
        } else {
            return String.format(
                "For %s, you can call %s. Available %s. %s",
                contact.serviceName(),
                contact.tollFreePhone() != null ? contact.tollFreePhone() : contact.phone(),
                contact.businessHours(),
                contact.notes() != null ? contact.notes() : ""
            );
        }
    }
}
