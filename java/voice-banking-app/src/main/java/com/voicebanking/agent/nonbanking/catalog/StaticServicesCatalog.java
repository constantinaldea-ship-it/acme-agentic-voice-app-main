package com.voicebanking.agent.nonbanking.catalog;

import com.voicebanking.agent.nonbanking.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Static implementation of the non-banking services catalog.
 * 
 * Contains comprehensive catalog of Acme Bank non-banking services
 * including insurance, travel benefits, lifestyle perks, and partner offers.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Component
public class StaticServicesCatalog implements ServicesCatalog {
    private static final Logger log = LoggerFactory.getLogger(StaticServicesCatalog.class);

    private final Map<String, NonBankingService> services = new LinkedHashMap<>();
    private final List<InsuranceCoverage> insurances = new ArrayList<>();
    private final List<TravelBenefit> travelBenefits = new ArrayList<>();
    private final List<PartnerOffer> partnerOffers = new ArrayList<>();

    @PostConstruct
    public void init() {
        initializeInsuranceServices();
        initializeTravelBenefits();
        initializePartnerOffers();
        initializeDigitalServices();
        initializeLifestyleServices();
        
        log.info("Initialized non-banking services catalog with {} services", services.size());
    }

    private void initializeInsuranceServices() {
        // Travel Medical Insurance
        InsuranceCoverage travelMedical = InsuranceCoverage.insuranceBuilder()
                .serviceId("ins-travel-medical")
                .nameEn("Travel Medical Insurance")
                .nameDe("Auslandskrankenversicherung")
                .insuranceType(InsuranceType.TRAVEL_MEDICAL)
                .descriptionShortEn("Emergency medical coverage when traveling abroad")
                .descriptionShortDe("Medizinische Notfallversorgung im Ausland")
                .descriptionLongEn("Comprehensive medical coverage for emergency treatment, hospital stays, and medical evacuation when traveling outside your home country.")
                .descriptionLongDe("Umfassende medizinische Versorgung für Notfallbehandlungen, Krankenhausaufenthalte und medizinische Evakuierung bei Reisen außerhalb Ihres Heimatlandes.")
                .eligibility(ServiceEligibility.forGoldAndAbove())
                .coverageLimit(100000)
                .coverageLimitFormatted("€100,000")
                .coverageSummaryEn("Covers emergency medical expenses up to €100,000 when traveling abroad for up to 45 days per trip.")
                .coverageSummaryDe("Deckt medizinische Notfallkosten bis zu €100.000 bei Auslandsreisen von bis zu 45 Tagen pro Reise.")
                .exclusionsEn(List.of("Pre-existing conditions", "Extreme sports without additional coverage", "Travel against medical advice"))
                .exclusionsDe(List.of("Vorerkrankungen", "Extremsport ohne Zusatzversicherung", "Reisen gegen ärztlichen Rat"))
                .claimProcessEn("To file a claim, contact AXA Assistance within 48 hours of treatment.")
                .claimProcessDe("Für Schadensmeldungen kontaktieren Sie AXA Assistance innerhalb von 48 Stunden nach der Behandlung.")
                .claimPhone("+49 800 100-2500")
                .claimEmail("claims@axa-assistance.de")
                .deductibleAmount(0)
                .validityPeriod("45 days per trip")
                .partnerName("AXA Assistance")
                .partnerPhone("+49 800 100-2500")
                .partnerWebsite("www.axa-assistance.de")
                .termsUrl("https://acmebank.example/terms/travel-insurance")
                .active(true)
                .build();
        addInsurance(travelMedical);

        // Trip Cancellation Insurance
        InsuranceCoverage tripCancellation = InsuranceCoverage.insuranceBuilder()
                .serviceId("ins-trip-cancellation")
                .nameEn("Trip Cancellation Insurance")
                .nameDe("Reiserücktrittsversicherung")
                .insuranceType(InsuranceType.TRIP_CANCELLATION)
                .descriptionShortEn("Get reimbursed for cancelled trips")
                .descriptionShortDe("Erstattung bei Reisestornierung")
                .descriptionLongEn("Reimburses prepaid, non-refundable travel expenses if you need to cancel your trip due to covered reasons such as illness, injury, or death in the family.")
                .descriptionLongDe("Erstattet vorausbezahlte, nicht erstattungsfähige Reisekosten, wenn Sie Ihre Reise aus versicherten Gründen wie Krankheit, Verletzung oder Tod in der Familie stornieren müssen.")
                .eligibility(ServiceEligibility.forPlatinumAndAbove())
                .coverageLimit(10000)
                .coverageLimitFormatted("€10,000 per trip")
                .coverageSummaryEn("Up to €10,000 reimbursement for cancelled trips when paid with your Platinum card.")
                .coverageSummaryDe("Bis zu €10.000 Erstattung für stornierte Reisen, wenn mit Ihrer Platinum-Karte bezahlt.")
                .claimProcessEn("Submit claim with booking confirmation and cancellation documentation within 30 days.")
                .claimProcessDe("Reichen Sie den Antrag mit Buchungsbestätigung und Stornierungsunterlagen innerhalb von 30 Tagen ein.")
                .claimPhone("+49 800 100-2501")
                .claimEmail("trip-claims@axa-assistance.de")
                .deductibleAmount(100)
                .partnerName("AXA Assistance")
                .partnerPhone("+49 800 100-2501")
                .partnerWebsite("www.axa-assistance.de")
                .termsUrl("https://acmebank.example/terms/trip-cancellation")
                .active(true)
                .build();
        addInsurance(tripCancellation);

        // Purchase Protection
        InsuranceCoverage purchaseProtection = InsuranceCoverage.insuranceBuilder()
                .serviceId("ins-purchase-protection")
                .nameEn("Purchase Protection")
                .nameDe("Einkaufsversicherung")
                .insuranceType(InsuranceType.PURCHASE_PROTECTION)
                .descriptionShortEn("Protection for purchases made with your card")
                .descriptionShortDe("Schutz für Einkäufe mit Ihrer Karte")
                .descriptionLongEn("Protects eligible purchases made with your card against theft and accidental damage for 90 days from purchase date.")
                .descriptionLongDe("Schützt berechtigte Einkäufe mit Ihrer Karte 90 Tage ab Kaufdatum vor Diebstahl und versehentlicher Beschädigung.")
                .eligibility(ServiceEligibility.forGoldAndAbove())
                .coverageLimit(5000)
                .coverageLimitFormatted("€5,000 per claim, €25,000 per year")
                .coverageSummaryEn("Items purchased with your Gold or Platinum card are protected against theft and accidental damage for 90 days.")
                .coverageSummaryDe("Mit Ihrer Gold- oder Platinum-Karte gekaufte Artikel sind 90 Tage lang gegen Diebstahl und versehentliche Beschädigung geschützt.")
                .claimProcessEn("File claim online or call within 30 days of incident with proof of purchase.")
                .claimProcessDe("Reichen Sie den Antrag online oder telefonisch innerhalb von 30 Tagen nach dem Vorfall mit Kaufbeleg ein.")
                .claimPhone("+49 800 100-2502")
                .deductibleAmount(50)
                .partnerName("Allianz Protection")
                .partnerPhone("+49 800 100-2502")
                .partnerWebsite("www.allianz.de/cardprotection")
                .termsUrl("https://acmebank.example/terms/purchase-protection")
                .active(true)
                .build();
        addInsurance(purchaseProtection);

        // Rental Car CDW
        InsuranceCoverage rentalCdw = InsuranceCoverage.insuranceBuilder()
                .serviceId("ins-rental-cdw")
                .nameEn("Rental Car Collision Damage Waiver")
                .nameDe("Mietwagen-Kaskoversicherung")
                .insuranceType(InsuranceType.RENTAL_CAR_CDW)
                .descriptionShortEn("Collision damage waiver for rental cars")
                .descriptionShortDe("Kaskoversicherung für Mietwagen")
                .descriptionLongEn("Provides collision damage waiver coverage for rental cars when you decline the rental company's CDW and pay with your eligible card.")
                .descriptionLongDe("Bietet Kaskoversicherungsschutz für Mietwagen, wenn Sie die Kaskoversicherung der Mietwagenfirma ablehnen und mit Ihrer berechtigten Karte bezahlen.")
                .eligibility(ServiceEligibility.forAll())
                .coverageLimit(50000)
                .coverageLimitFormatted("Up to €50,000")
                .coverageSummaryEn("Covers collision damage to rental cars up to €50,000 when you pay with your MasterCard.")
                .coverageSummaryDe("Deckt Kollisionsschäden an Mietwagen bis zu €50.000, wenn Sie mit Ihrer MasterCard bezahlen.")
                .claimProcessEn("Report damage to rental company and file claim within 15 days with rental agreement and damage report.")
                .claimProcessDe("Melden Sie den Schaden der Mietwagenfirma und reichen Sie den Antrag innerhalb von 15 Tagen mit Mietvertrag und Schadensbericht ein.")
                .claimPhone("+49 800 100-2503")
                .deductibleAmount(250)
                .partnerName("MasterCard Benefits")
                .partnerPhone("+49 800 100-2503")
                .termsUrl("https://acmebank.example/terms/rental-cdw")
                .active(true)
                .build();
        addInsurance(rentalCdw);
    }

    private void initializeTravelBenefits() {
        // Priority Pass Lounge Access
        TravelBenefit priorityPass = TravelBenefit.travelBuilder()
                .serviceId("travel-priority-pass")
                .nameEn("Priority Pass Lounge Access")
                .nameDe("Priority Pass Lounge-Zugang")
                .benefitType(TravelBenefitType.PRIORITY_PASS)
                .descriptionShortEn("Access to 1,300+ airport lounges worldwide")
                .descriptionShortDe("Zugang zu über 1.300 Flughafen-Lounges weltweit")
                .descriptionLongEn("Enjoy complimentary access to over 1,300 airport lounges in more than 600 cities worldwide. Relax before your flight with comfortable seating, refreshments, WiFi, and more.")
                .descriptionLongDe("Genießen Sie kostenlosen Zugang zu über 1.300 Flughafen-Lounges in mehr als 600 Städten weltweit. Entspannen Sie vor Ihrem Flug mit bequemen Sitzen, Erfrischungen, WLAN und mehr.")
                .eligibility(ServiceEligibility.forPlatinumAndAbove())
                .accessInstructionsEn("Show your Priority Pass card or digital membership at the lounge entrance. Your card was mailed separately after card activation.")
                .accessInstructionsDe("Zeigen Sie Ihre Priority Pass Karte oder digitale Mitgliedschaft am Lounge-Eingang. Ihre Karte wurde nach der Kartenaktivierung separat zugeschickt.")
                .annualAccessLimit(-1) // Unlimited
                .companionRulesEn("Guests can be added for €32 per person per visit.")
                .companionRulesDe("Gäste können für €32 pro Person und Besuch hinzugefügt werden.")
                .locationInfo("1,300+ lounges in 600+ cities")
                .partnerName("Priority Pass")
                .partnerWebsite("www.prioritypass.com")
                .partnerAppName("Priority Pass")
                .partnerAppDeepLink("prioritypass://")
                .membershipIdRequired("Priority Pass membership card")
                .termsUrl("https://acmebank.example/terms/priority-pass")
                .active(true)
                .build();
        addTravelBenefit(priorityPass);

        // Miles & More
        TravelBenefit milesAndMore = TravelBenefit.travelBuilder()
                .serviceId("travel-miles-more")
                .nameEn("Miles & More")
                .nameDe("Miles & More")
                .benefitType(TravelBenefitType.MILES_AND_MORE)
                .descriptionShortEn("Earn miles on every purchase")
                .descriptionShortDe("Meilen bei jedem Einkauf sammeln")
                .descriptionLongEn("Earn award miles on every purchase with your Miles & More card. Use miles for flights, upgrades, hotel stays, and more with Star Alliance partners.")
                .descriptionLongDe("Sammeln Sie Prämienmeilen bei jedem Einkauf mit Ihrer Miles & More Karte. Lösen Sie Meilen für Flüge, Upgrades, Hotelaufenthalte und mehr bei Star Alliance Partnern ein.")
                .eligibility(ServiceEligibility.forGoldAndAbove())
                .accessInstructionsEn("Miles are automatically credited to your Miles & More account linked to your card. Check your balance in the Miles & More app.")
                .accessInstructionsDe("Meilen werden automatisch Ihrem mit der Karte verknüpften Miles & More Konto gutgeschrieben. Überprüfen Sie Ihren Kontostand in der Miles & More App.")
                .annualAccessLimit(-1)
                .partnerName("Miles & More")
                .partnerWebsite("www.miles-and-more.com")
                .partnerAppName("Miles & More")
                .partnerAppDeepLink("milesandmore://")
                .membershipIdRequired("Miles & More membership number")
                .termsUrl("https://acmebank.example/terms/miles-more")
                .active(true)
                .build();
        addTravelBenefit(milesAndMore);

        // Travel Concierge
        TravelBenefit concierge = TravelBenefit.travelBuilder()
                .serviceId("travel-concierge")
                .nameEn("Travel Concierge Service")
                .nameDe("Reise-Concierge-Service")
                .benefitType(TravelBenefitType.TRAVEL_CONCIERGE)
                .descriptionShortEn("Personal travel booking assistance")
                .descriptionShortDe("Persönliche Reisebuchungsassistenz")
                .descriptionLongEn("Access 24/7 travel concierge service for booking flights, hotels, restaurants, and experiences. Our experts handle complex itineraries and last-minute changes.")
                .descriptionLongDe("Zugang zu 24/7 Reise-Concierge-Service für Buchung von Flügen, Hotels, Restaurants und Erlebnissen. Unsere Experten kümmern sich um komplexe Reisepläne und kurzfristige Änderungen.")
                .eligibility(ServiceEligibility.forPlatinumAndAbove())
                .accessInstructionsEn("Call the Platinum Concierge line at +49 800 200-3000 or use the Acme Bank app to request assistance.")
                .accessInstructionsDe("Rufen Sie die Platinum Concierge-Hotline unter +49 800 200-3000 an oder nutzen Sie die Acme Bank App, um Unterstützung anzufordern.")
                .annualAccessLimit(-1)
                .partnerName("Acme Bank Concierge")
                .partnerPhone("+49 800 200-3000")
                .termsUrl("https://acmebank.example/terms/concierge")
                .active(true)
                .build();
        addTravelBenefit(concierge);

        // Fast Track Security
        TravelBenefit fastTrack = TravelBenefit.travelBuilder()
                .serviceId("travel-fast-track")
                .nameEn("Airport Fast Track")
                .nameDe("Flughafen Fast Track")
                .benefitType(TravelBenefitType.FAST_TRACK)
                .descriptionShortEn("Skip the queue at airport security")
                .descriptionShortDe("Warteschlange bei Sicherheitskontrolle überspringen")
                .descriptionLongEn("Skip the regular security lines at participating airports with Fast Track access. Available at major German airports including Frankfurt, Munich, and Berlin.")
                .descriptionLongDe("Überspringen Sie die regulären Sicherheitslinien an teilnehmenden Flughäfen mit Fast Track-Zugang. Verfügbar an großen deutschen Flughäfen wie Frankfurt, München und Berlin.")
                .eligibility(ServiceEligibility.forPlatinumAndAbove())
                .accessInstructionsEn("Show your Platinum card at the Fast Track entrance before security. Available at Frankfurt (Terminal 1), Munich, and Berlin airports.")
                .accessInstructionsDe("Zeigen Sie Ihre Platinum-Karte am Fast Track-Eingang vor der Sicherheitskontrolle. Verfügbar an den Flughäfen Frankfurt (Terminal 1), München und Berlin.")
                .annualAccessLimit(-1)
                .locationInfo("Frankfurt, Munich, Berlin airports")
                .partnerName("Airport Services")
                .termsUrl("https://acmebank.example/terms/fast-track")
                .active(true)
                .build();
        addTravelBenefit(fastTrack);

        // Hotel Benefits
        TravelBenefit hotelBenefits = TravelBenefit.travelBuilder()
                .serviceId("travel-hotel-benefits")
                .nameEn("Hotel Luxury Collection Benefits")
                .nameDe("Hotel Luxury Collection Vorteile")
                .benefitType(TravelBenefitType.HOTEL_BENEFITS)
                .descriptionShortEn("Room upgrades and perks at partner hotels")
                .descriptionShortDe("Zimmer-Upgrades und Vorteile bei Partnerhotels")
                .descriptionLongEn("Enjoy room upgrades, late checkout, complimentary breakfast, and exclusive amenities at over 500 Luxury Collection partner hotels worldwide.")
                .descriptionLongDe("Genießen Sie Zimmer-Upgrades, späten Checkout, kostenloses Frühstück und exklusive Annehmlichkeiten in über 500 Luxury Collection Partnerhotels weltweit.")
                .eligibility(ServiceEligibility.forPlatinumAndAbove())
                .accessInstructionsEn("Book through the Acme Bank Travel Portal or mention your Platinum membership when booking directly with the hotel.")
                .accessInstructionsDe("Buchen Sie über das Acme Bank Reiseportal oder erwähnen Sie Ihre Platinum-Mitgliedschaft bei direkter Buchung beim Hotel.")
                .annualAccessLimit(-1)
                .locationInfo("500+ partner hotels worldwide")
                .partnerName("Luxury Collection Hotels")
                .partnerWebsite("www.luxurycollection.com")
                .termsUrl("https://acmebank.example/terms/hotel-benefits")
                .active(true)
                .build();
        addTravelBenefit(hotelBenefits);

        // Car Rental Benefits
        TravelBenefit carRental = TravelBenefit.travelBuilder()
                .serviceId("travel-car-rental")
                .nameEn("Car Rental Discounts")
                .nameDe("Mietwagen-Rabatte")
                .benefitType(TravelBenefitType.CAR_RENTAL)
                .descriptionShortEn("Up to 20% off at partner car rentals")
                .descriptionShortDe("Bis zu 20% Rabatt bei Partner-Mietwagenfirmen")
                .descriptionLongEn("Enjoy up to 20% discount and free upgrades at Hertz, Sixt, and Europcar when booking with your Acme Bank card.")
                .descriptionLongDe("Genießen Sie bis zu 20% Rabatt und kostenlose Upgrades bei Hertz, Sixt und Europcar, wenn Sie mit Ihrer Acme Bank Karte buchen.")
                .eligibility(ServiceEligibility.forGoldAndAbove())
                .accessInstructionsEn("Use code DBGOLD or DBPLAT when booking online or mention Acme Bank benefits when booking by phone.")
                .accessInstructionsDe("Verwenden Sie den Code DBGOLD oder DBPLAT bei Online-Buchung oder erwähnen Sie die Acme Bank Vorteile bei telefonischer Buchung.")
                .annualAccessLimit(-1)
                .locationInfo("Hertz, Sixt, Europcar worldwide")
                .partnerName("Hertz, Sixt, Europcar")
                .partnerWebsite("www.acmebank.example/car-rental")
                .termsUrl("https://acmebank.example/terms/car-rental")
                .active(true)
                .build();
        addTravelBenefit(carRental);
    }

    private void initializePartnerOffers() {
        // Zalando Discount
        PartnerOffer zalando = PartnerOffer.offerBuilder()
                .serviceId("offer-zalando")
                .nameEn("Zalando Discount")
                .nameDe("Zalando Rabatt")
                .offerType(OfferType.DISCOUNT)
                .descriptionShortEn("10% off your purchase at Zalando")
                .descriptionShortDe("10% Rabatt auf Ihren Einkauf bei Zalando")
                .eligibility(ServiceEligibility.forAll())
                .discountAmount("10%")
                .discountPercentage(10)
                .redemptionCode("DBVIP10")
                .redemptionInstructionsEn("Enter code DBVIP10 at checkout on zalando.de")
                .redemptionInstructionsDe("Geben Sie den Code DBVIP10 an der Kasse auf zalando.de ein")
                .validFrom(LocalDate.of(2026, 1, 1))
                .validUntil(LocalDate.of(2026, 3, 31))
                .partnerName("Zalando")
                .partnerWebsite("www.zalando.de")
                .onlineUrl("https://www.zalando.de/?partner=db")
                .onlineOnly(true)
                .termsUrl("https://acmebank.example/offers/zalando-terms")
                .active(true)
                .build();
        addPartnerOffer(zalando);

        // MediaMarkt Cashback
        PartnerOffer mediamarkt = PartnerOffer.offerBuilder()
                .serviceId("offer-mediamarkt")
                .nameEn("MediaMarkt Cashback")
                .nameDe("MediaMarkt Cashback")
                .offerType(OfferType.CASHBACK)
                .descriptionShortEn("5% cashback on electronics")
                .descriptionShortDe("5% Cashback auf Elektronik")
                .eligibility(ServiceEligibility.forAll())
                .discountAmount("5% cashback")
                .discountPercentage(5)
                .redemptionInstructionsEn("Pay with your Acme Bank card and cashback is automatically credited within 30 days")
                .redemptionInstructionsDe("Zahlen Sie mit Ihrer Acme Bank Karte und Cashback wird automatisch innerhalb von 30 Tagen gutgeschrieben")
                .validFrom(LocalDate.of(2026, 1, 1))
                .validUntil(LocalDate.of(2026, 6, 30))
                .partnerName("MediaMarkt")
                .partnerWebsite("www.mediamarkt.de")
                .onlineOnly(false)
                .storeOnly(false)
                .termsUrl("https://acmebank.example/offers/mediamarkt-terms")
                .active(true)
                .build();
        addPartnerOffer(mediamarkt);

        // Booking.com Discount
        PartnerOffer booking = PartnerOffer.offerBuilder()
                .serviceId("offer-booking")
                .nameEn("Booking.com Discount")
                .nameDe("Booking.com Rabatt")
                .offerType(OfferType.DISCOUNT)
                .descriptionShortEn("Up to 15% off hotel bookings")
                .descriptionShortDe("Bis zu 15% Rabatt auf Hotelbuchungen")
                .eligibility(ServiceEligibility.forGoldAndAbove())
                .discountAmount("Up to 15%")
                .discountPercentage(15)
                .redemptionInstructionsEn("Book through the Acme Bank travel portal to receive automatic discount")
                .redemptionInstructionsDe("Buchen Sie über das Acme Bank Reiseportal für automatischen Rabatt")
                .validFrom(LocalDate.of(2026, 1, 1))
                .validUntil(LocalDate.of(2026, 12, 31))
                .partnerName("Booking.com")
                .partnerWebsite("www.booking.com")
                .onlineUrl("https://acmebank.example/travel-portal")
                .onlineOnly(true)
                .termsUrl("https://acmebank.example/offers/booking-terms")
                .active(true)
                .build();
        addPartnerOffer(booking);

        // Lufthansa Shop
        PartnerOffer lufthansaShop = PartnerOffer.offerBuilder()
                .serviceId("offer-lufthansa-shop")
                .nameEn("Lufthansa WorldShop")
                .nameDe("Lufthansa WorldShop")
                .offerType(OfferType.BONUS_POINTS)
                .descriptionShortEn("Double miles on WorldShop purchases")
                .descriptionShortDe("Doppelte Meilen bei WorldShop-Einkäufen")
                .eligibility(ServiceEligibility.forGoldAndAbove())
                .discountAmount("2x Miles")
                .redemptionInstructionsEn("Shop at worldshop.eu and pay with your Miles & More card to earn double miles")
                .redemptionInstructionsDe("Kaufen Sie auf worldshop.eu ein und zahlen Sie mit Ihrer Miles & More Karte, um doppelte Meilen zu sammeln")
                .validFrom(LocalDate.of(2026, 1, 1))
                .validUntil(LocalDate.of(2026, 12, 31))
                .partnerName("Lufthansa WorldShop")
                .partnerWebsite("www.worldshop.eu")
                .onlineOnly(true)
                .termsUrl("https://acmebank.example/offers/worldshop-terms")
                .active(true)
                .build();
        addPartnerOffer(lufthansaShop);

        // Limousine Service
        PartnerOffer limousine = PartnerOffer.offerBuilder()
                .serviceId("offer-limousine")
                .nameEn("Airport Limousine Service")
                .nameDe("Flughafen-Limousinenservice")
                .offerType(OfferType.DISCOUNT)
                .descriptionShortEn("20% off airport transfers")
                .descriptionShortDe("20% Rabatt auf Flughafentransfers")
                .eligibility(ServiceEligibility.forPlatinumAndAbove())
                .discountAmount("20%")
                .discountPercentage(20)
                .redemptionCode("DBPLAT20")
                .redemptionInstructionsEn("Book through the Concierge or use code DBPLAT20 on blacklane.com")
                .redemptionInstructionsDe("Buchen Sie über den Concierge oder verwenden Sie den Code DBPLAT20 auf blacklane.com")
                .validFrom(LocalDate.of(2026, 1, 1))
                .validUntil(LocalDate.of(2026, 12, 31))
                .partnerName("Blacklane")
                .partnerWebsite("www.blacklane.com")
                .onlineOnly(false)
                .termsUrl("https://acmebank.example/offers/blacklane-terms")
                .active(true)
                .build();
        addPartnerOffer(limousine);

        // Douglas Beauty
        PartnerOffer douglas = PartnerOffer.offerBuilder()
                .serviceId("offer-douglas")
                .nameEn("Douglas Beauty Discount")
                .nameDe("Douglas Beauty Rabatt")
                .offerType(OfferType.DISCOUNT)
                .descriptionShortEn("15% off at Douglas")
                .descriptionShortDe("15% Rabatt bei Douglas")
                .eligibility(ServiceEligibility.forAll())
                .discountAmount("15%")
                .discountPercentage(15)
                .redemptionCode("DBBEAUTY15")
                .redemptionInstructionsEn("Use code DBBEAUTY15 online or show your card in store")
                .redemptionInstructionsDe("Verwenden Sie den Code DBBEAUTY15 online oder zeigen Sie Ihre Karte im Geschäft")
                .validFrom(LocalDate.of(2026, 1, 1))
                .validUntil(LocalDate.of(2026, 4, 30))
                .partnerName("Douglas")
                .partnerWebsite("www.douglas.de")
                .onlineOnly(false)
                .termsUrl("https://acmebank.example/offers/douglas-terms")
                .active(true)
                .build();
        addPartnerOffer(douglas);

        // ADAC Membership
        PartnerOffer adac = PartnerOffer.offerBuilder()
                .serviceId("offer-adac")
                .nameEn("ADAC Membership Discount")
                .nameDe("ADAC Mitgliedschaftsrabatt")
                .offerType(OfferType.DISCOUNT)
                .descriptionShortEn("€20 off first year ADAC membership")
                .descriptionShortDe("€20 Rabatt auf das erste Jahr ADAC-Mitgliedschaft")
                .eligibility(ServiceEligibility.forAll())
                .discountAmount("€20")
                .redemptionInstructionsEn("Apply through the Acme Bank benefits portal to receive the discount")
                .redemptionInstructionsDe("Bewerben Sie sich über das Acme Bank Vorteile-Portal, um den Rabatt zu erhalten")
                .validFrom(LocalDate.of(2026, 1, 1))
                .validUntil(LocalDate.of(2026, 12, 31))
                .partnerName("ADAC")
                .partnerWebsite("www.adac.de")
                .termsUrl("https://acmebank.example/offers/adac-terms")
                .active(true)
                .build();
        addPartnerOffer(adac);
    }

    private void initializeDigitalServices() {
        NonBankingService identityProtection = NonBankingService.builder()
                .serviceId("digital-identity-protection")
                .nameEn("Identity Protection Service")
                .nameDe("Identitätsschutz-Service")
                .category(ServiceCategory.DIGITAL)
                .descriptionShortEn("Monitor and protect your identity online")
                .descriptionShortDe("Überwachen und schützen Sie Ihre Identität online")
                .descriptionLongEn("24/7 monitoring of your personal information on the dark web, credit report monitoring, and identity theft insurance up to €25,000.")
                .descriptionLongDe("24/7-Überwachung Ihrer persönlichen Daten im Darknet, Kreditberichtsüberwachung und Identitätsdiebstahlversicherung bis zu €25.000.")
                .eligibility(ServiceEligibility.forPlatinumAndAbove())
                .howToAccessEn("Activate through the Acme Bank app or online banking. Go to Security > Identity Protection.")
                .howToAccessDe("Aktivieren Sie über die Acme Bank App oder Online-Banking. Gehen Sie zu Sicherheit > Identitätsschutz.")
                .partnerName("Experian")
                .partnerPhone("+49 800 300-4000")
                .partnerWebsite("www.experian.de")
                .termsUrl("https://acmebank.example/terms/identity-protection")
                .active(true)
                .build();
        services.put(identityProtection.getServiceId(), identityProtection);

        NonBankingService cyberSecurity = NonBankingService.builder()
                .serviceId("digital-cyber-security")
                .nameEn("Cyber Security Package")
                .nameDe("Cyber-Sicherheitspaket")
                .category(ServiceCategory.DIGITAL)
                .descriptionShortEn("Antivirus and VPN for all devices")
                .descriptionShortDe("Antivirus und VPN für alle Geräte")
                .descriptionLongEn("Norton 360 Deluxe subscription including antivirus, VPN, password manager, and parental controls for up to 5 devices.")
                .descriptionLongDe("Norton 360 Deluxe-Abonnement mit Antivirus, VPN, Passwort-Manager und Kindersicherung für bis zu 5 Geräte.")
                .eligibility(ServiceEligibility.forPlatinumAndAbove())
                .howToAccessEn("Download Norton 360 and register with your Acme Bank email to activate the complimentary subscription.")
                .howToAccessDe("Laden Sie Norton 360 herunter und registrieren Sie sich mit Ihrer Acme Bank E-Mail, um das kostenlose Abonnement zu aktivieren.")
                .partnerName("Norton")
                .partnerWebsite("www.norton.com")
                .termsUrl("https://acmebank.example/terms/cyber-security")
                .active(true)
                .build();
        services.put(cyberSecurity.getServiceId(), cyberSecurity);
    }

    private void initializeLifestyleServices() {
        NonBankingService eventAccess = NonBankingService.builder()
                .serviceId("lifestyle-events")
                .nameEn("Exclusive Event Access")
                .nameDe("Exklusiver Event-Zugang")
                .category(ServiceCategory.LIFESTYLE)
                .descriptionShortEn("Priority access to concerts, sports, and cultural events")
                .descriptionShortDe("Prioritätszugang zu Konzerten, Sport- und Kulturveranstaltungen")
                .descriptionLongEn("Get early access and reserved allocations for sold-out concerts, premier sporting events, and exclusive cultural experiences across Germany.")
                .descriptionLongDe("Erhalten Sie frühzeitigen Zugang und reservierte Kontingente für ausverkaufte Konzerte, erstklassige Sportveranstaltungen und exklusive kulturelle Erlebnisse in ganz Deutschland.")
                .eligibility(ServiceEligibility.forPlatinumAndAbove())
                .howToAccessEn("Check the Acme Bank Benefits Portal for current events or contact the Concierge.")
                .howToAccessDe("Überprüfen Sie das Acme Bank Vorteile-Portal für aktuelle Events oder kontaktieren Sie den Concierge.")
                .partnerName("Acme Bank Experiences")
                .partnerPhone("+49 800 200-3000")
                .termsUrl("https://acmebank.example/terms/events")
                .active(true)
                .build();
        services.put(eventAccess.getServiceId(), eventAccess);

        NonBankingService personalShopper = NonBankingService.builder()
                .serviceId("lifestyle-personal-shopper")
                .nameEn("Personal Shopping Service")
                .nameDe("Persönlicher Einkaufsservice")
                .category(ServiceCategory.LIFESTYLE)
                .descriptionShortEn("Dedicated personal shopping assistance")
                .descriptionShortDe("Dedizierte persönliche Einkaufsassistenz")
                .descriptionLongEn("Access to personal shopping experts at premium department stores including KaDeWe, Oberpollinger, and Alsterhaus.")
                .descriptionLongDe("Zugang zu persönlichen Einkaufsexperten in Premium-Kaufhäusern wie KaDeWe, Oberpollinger und Alsterhaus.")
                .eligibility(ServiceEligibility.forPlatinumAndAbove())
                .howToAccessEn("Book through the Concierge at least 48 hours in advance.")
                .howToAccessDe("Buchen Sie mindestens 48 Stunden im Voraus über den Concierge.")
                .partnerName("KaDeWe Group")
                .partnerPhone("+49 30 21210")
                .termsUrl("https://acmebank.example/terms/personal-shopper")
                .active(true)
                .build();
        services.put(personalShopper.getServiceId(), personalShopper);

        NonBankingService golfAccess = NonBankingService.builder()
                .serviceId("lifestyle-golf")
                .nameEn("Golf Course Access")
                .nameDe("Golfplatz-Zugang")
                .category(ServiceCategory.LIFESTYLE)
                .descriptionShortEn("Green fee discounts at premium golf courses")
                .descriptionShortDe("Green-Fee-Rabatte auf Premium-Golfplätzen")
                .descriptionLongEn("Enjoy 20% off green fees at over 100 premium golf courses across Germany and Europe.")
                .descriptionLongDe("Genießen Sie 20% Rabatt auf Green Fees auf über 100 Premium-Golfplätzen in Deutschland und Europa.")
                .eligibility(ServiceEligibility.forGoldAndAbove())
                .howToAccessEn("Book through the Benefits Portal or mention your Acme Bank Gold/Platinum card when booking directly.")
                .howToAccessDe("Buchen Sie über das Vorteile-Portal oder erwähnen Sie Ihre Acme Bank Gold/Platinum-Karte bei direkter Buchung.")
                .partnerName("Golf Alliance")
                .partnerWebsite("www.golf-alliance.de")
                .termsUrl("https://acmebank.example/terms/golf")
                .active(true)
                .build();
        services.put(golfAccess.getServiceId(), golfAccess);
    }

    private void addInsurance(InsuranceCoverage insurance) {
        insurances.add(insurance);
        services.put(insurance.getServiceId(), insurance);
    }

    private void addTravelBenefit(TravelBenefit benefit) {
        travelBenefits.add(benefit);
        services.put(benefit.getServiceId(), benefit);
    }

    private void addPartnerOffer(PartnerOffer offer) {
        partnerOffers.add(offer);
        services.put(offer.getServiceId(), offer);
    }

    @Override
    public List<NonBankingService> getAllServices() {
        return services.values().stream()
                .filter(NonBankingService::isActive)
                .collect(Collectors.toList());
    }

    @Override
    public List<NonBankingService> getServicesByCategory(ServiceCategory category) {
        return services.values().stream()
                .filter(s -> s.isActive() && s.getCategory() == category)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<NonBankingService> getServiceById(String serviceId) {
        return Optional.ofNullable(services.get(serviceId));
    }

    @Override
    public List<InsuranceCoverage> getInsuranceCoverages() {
        return insurances.stream()
                .filter(InsuranceCoverage::isActive)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<InsuranceCoverage> getInsuranceByType(InsuranceType type) {
        return insurances.stream()
                .filter(i -> i.isActive() && i.getInsuranceType() == type)
                .findFirst();
    }

    @Override
    public List<TravelBenefit> getTravelBenefits() {
        return travelBenefits.stream()
                .filter(TravelBenefit::isActive)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<TravelBenefit> getTravelBenefitByType(TravelBenefitType type) {
        return travelBenefits.stream()
                .filter(t -> t.isActive() && t.getBenefitType() == type)
                .findFirst();
    }

    @Override
    public List<PartnerOffer> getPartnerOffers() {
        return partnerOffers.stream()
                .filter(PartnerOffer::isActive)
                .collect(Collectors.toList());
    }

    @Override
    public List<PartnerOffer> getValidPartnerOffers() {
        return partnerOffers.stream()
                .filter(o -> o.isActive() && o.isCurrentlyValid())
                .collect(Collectors.toList());
    }

    @Override
    public List<PartnerOffer> getPartnerOffersByType(OfferType type) {
        return partnerOffers.stream()
                .filter(o -> o.isActive() && o.getOfferType() == type)
                .collect(Collectors.toList());
    }

    @Override
    public List<NonBankingService> getEligibleServices(CardTier cardTier) {
        return services.values().stream()
                .filter(NonBankingService::isActive)
                .filter(s -> {
                    ServiceEligibility elig = s.getEligibility();
                    if (elig == null || elig.isAllCustomers()) return true;
                    return cardTier != null && cardTier.meetsRequirement(elig.getMinimumCardTier());
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<NonBankingService> searchServices(String query) {
        if (query == null || query.isBlank()) {
            return getAllServices();
        }
        
        String lower = query.toLowerCase();
        return services.values().stream()
                .filter(NonBankingService::isActive)
                .filter(s -> matchesSearch(s, lower))
                .collect(Collectors.toList());
    }

    private boolean matchesSearch(NonBankingService service, String query) {
        return service.getNameEn().toLowerCase().contains(query)
                || service.getNameDe().toLowerCase().contains(query)
                || (service.getDescriptionShortEn() != null && service.getDescriptionShortEn().toLowerCase().contains(query))
                || (service.getPartnerName() != null && service.getPartnerName().toLowerCase().contains(query));
    }

    @Override
    public int getServiceCount() {
        return (int) services.values().stream().filter(NonBankingService::isActive).count();
    }

    @Override
    public int getServiceCountByCategory(ServiceCategory category) {
        return (int) services.values().stream()
                .filter(s -> s.isActive() && s.getCategory() == category)
                .count();
    }
}
