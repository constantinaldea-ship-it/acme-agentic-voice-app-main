package com.voicebanking.agent.nonbanking.service;

import com.voicebanking.agent.nonbanking.catalog.ServicesCatalog;
import com.voicebanking.agent.nonbanking.domain.*;
import com.voicebanking.agent.nonbanking.integration.InsurancePartnerStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for retrieving service contact information.
 * 
 * Provides contact details for services, claim filing, and support.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class ServiceContactService {
    private static final Logger log = LoggerFactory.getLogger(ServiceContactService.class);
    
    private final ServicesCatalog catalog;
    private final InsurancePartnerStub insurancePartner;
    
    public ServiceContactService(ServicesCatalog catalog, InsurancePartnerStub insurancePartner) {
        this.catalog = catalog;
        this.insurancePartner = insurancePartner;
    }
    
    /**
     * Get contact information for a service.
     * @param serviceId the service ID
     * @return contact details or empty if not found
     */
    public Optional<ServiceContact> getContactForService(String serviceId) {
        return catalog.getServiceById(serviceId)
                .map(this::buildContactFromService);
    }
    
    /**
     * Get contact for an insurance type.
     * @param insuranceType the insurance type
     * @return contact details or empty if not found
     */
    public Optional<ServiceContact> getInsuranceContact(InsuranceType insuranceType) {
        return catalog.getInsuranceByType(insuranceType)
                .map(ins -> buildContactFromService(ins));
    }
    
    /**
     * Get contact for a travel benefit type.
     * @param benefitType the travel benefit type
     * @return contact details or empty if not found
     */
    public Optional<ServiceContact> getTravelBenefitContact(TravelBenefitType benefitType) {
        return catalog.getTravelBenefitByType(benefitType)
                .map(tb -> buildContactFromService(tb));
    }
    
    /**
     * Get claim filing contact for insurance.
     * @param insuranceType the type of insurance
     * @param lang language code
     * @return claim contact with instructions
     */
    public ClaimContact getClaimContact(InsuranceType insuranceType, String lang) {
        InsurancePartnerStub.ClaimInstructions instructions = 
                insurancePartner.getClaimInstructions(insuranceType.name(), lang);
        
        return new ClaimContact(
            insuranceType.getName(lang),
            instructions.phone(),
            instructions.email(),
            instructions.steps(),
            instructions.deadlineDays()
        );
    }
    
    /**
     * Get emergency contact for travel services.
     * @param lang language code
     * @return emergency contact details
     */
    public ServiceContact getEmergencyContact(String lang) {
        boolean isGerman = "de".equalsIgnoreCase(lang);
        
        return new ServiceContact(
            isGerman ? "Notfall-Assistance" : "Emergency Assistance",
            "AXA Assistance",
            "+49 800 100-2500",
            null,
            "www.axa-assistance.de",
            isGerman ? "24 Stunden, 7 Tage die Woche" : "24 hours, 7 days a week"
        );
    }
    
    /**
     * Get general Acme Bank card services contact.
     * @param lang language code
     * @return card services contact
     */
    public ServiceContact getCardServicesContact(String lang) {
        boolean isGerman = "de".equalsIgnoreCase(lang);
        
        return new ServiceContact(
            isGerman ? "Acme Bank Kartenservice" : "Acme Bank Card Services",
            "Acme Bank",
            "+49 800 420-4040",
            "cards@acmebank.example",
            "www.acmebank.example/cards",
            isGerman ? "Mo-Fr 8:00-20:00, Sa 9:00-16:00" : "Mon-Fri 8am-8pm, Sat 9am-4pm"
        );
    }
    
    /**
     * Get concierge contact for premium customers.
     * @param lang language code
     * @return concierge contact
     */
    public ServiceContact getConciergeContact(String lang) {
        boolean isGerman = "de".equalsIgnoreCase(lang);
        
        return new ServiceContact(
            isGerman ? "Platinum Concierge" : "Platinum Concierge",
            "Acme Bank Premium Services",
            "+49 800 200-3000",
            "concierge@acmebank.example",
            null,
            isGerman ? "24 Stunden, 7 Tage die Woche" : "24 hours, 7 days a week"
        );
    }
    
    private ServiceContact buildContactFromService(NonBankingService service) {
        return new ServiceContact(
            service.getNameEn(),
            service.getPartnerName(),
            service.getPartnerPhone(),
            service.getPartnerContact(),
            service.getPartnerWebsite(),
            null
        );
    }
    
    /**
     * Format contact for voice output.
     * @param contact the contact details
     * @param lang language code
     * @return voice-friendly contact message
     */
    public String formatContactForVoice(ServiceContact contact, String lang) {
        boolean isGerman = "de".equalsIgnoreCase(lang);
        StringBuilder sb = new StringBuilder();
        
        sb.append(isGerman ? "Für " : "For ").append(contact.serviceName());
        
        if (contact.partnerName() != null) {
            sb.append(isGerman ? ", kontaktieren Sie " : ", contact ");
            sb.append(contact.partnerName());
        }
        
        if (contact.phone() != null) {
            sb.append(isGerman ? " unter " : " at ");
            sb.append(contact.phone());
        }
        
        if (contact.website() != null) {
            sb.append(isGerman ? ", oder besuchen Sie " : ", or visit ");
            sb.append(contact.website());
        }
        
        if (contact.hours() != null) {
            sb.append(". ");
            sb.append(isGerman ? "Erreichbar " : "Available ");
            sb.append(contact.hours());
        }
        
        sb.append(".");
        return sb.toString();
    }
    
    /**
     * Record for service contact details.
     */
    public record ServiceContact(
        String serviceName,
        String partnerName,
        String phone,
        String email,
        String website,
        String hours
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("serviceName", serviceName);
            map.put("partnerName", partnerName);
            map.put("phone", phone);
            map.put("email", email);
            map.put("website", website);
            map.put("hours", hours);
            return map;
        }
    }
    
    /**
     * Record for claim filing contact.
     */
    public record ClaimContact(
        String insuranceName,
        String phone,
        String email,
        List<String> steps,
        int deadlineDays
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("insuranceName", insuranceName);
            map.put("phone", phone);
            map.put("email", email);
            map.put("steps", steps);
            map.put("deadlineDays", deadlineDays);
            return map;
        }
        
        public String formatForVoice(String lang) {
            boolean isGerman = "de".equalsIgnoreCase(lang);
            StringBuilder sb = new StringBuilder();
            
            sb.append(isGerman 
                ? "Um einen Schadensfall für " + insuranceName + " zu melden, rufen Sie an unter " 
                : "To file a claim for " + insuranceName + ", call ");
            sb.append(phone);
            
            sb.append(isGerman 
                ? ". Der Schadenfall muss innerhalb von " + deadlineDays + " Tagen gemeldet werden."
                : ". The claim must be reported within " + deadlineDays + " days.");
            
            return sb.toString();
        }
    }
}
