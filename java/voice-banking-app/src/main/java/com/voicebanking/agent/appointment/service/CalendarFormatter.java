package com.voicebanking.agent.appointment.service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Utility service for formatting dates and times for voice responses.
 * Provides natural language date/time formatting suitable for TTS.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class CalendarFormatter {
    
    private static final Locale LOCALE = Locale.US;
    private static final ZoneId DEFAULT_TIMEZONE = ZoneId.of("Europe/Berlin");
    
    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("EEEE, MMMM d", LOCALE);
    private static final DateTimeFormatter DATE_WITH_YEAR_FORMATTER = 
        DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", LOCALE);
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("h:mm a", LOCALE);
    private static final DateTimeFormatter FULL_FORMATTER = 
        DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a", LOCALE);

    /**
     * Format a date for voice output with natural language.
     * Examples: "today", "tomorrow", "this Friday", "next Monday", "March 15th"
     */
    public static String formatDateNatural(LocalDate date) {
        LocalDate today = LocalDate.now(DEFAULT_TIMEZONE);
        long daysDiff = ChronoUnit.DAYS.between(today, date);

        if (daysDiff == 0) {
            return "today";
        } else if (daysDiff == 1) {
            return "tomorrow";
        } else if (daysDiff == -1) {
            return "yesterday";
        } else if (daysDiff > 1 && daysDiff <= 7) {
            // Within next week: "this Friday"
            return "this " + date.getDayOfWeek().getDisplayName(TextStyle.FULL, LOCALE);
        } else if (daysDiff > 7 && daysDiff <= 14) {
            // Next week: "next Monday"
            return "next " + date.getDayOfWeek().getDisplayName(TextStyle.FULL, LOCALE);
        } else if (date.getYear() == today.getYear()) {
            // Same year: "Friday, March 15"
            return date.format(DATE_FORMATTER);
        } else {
            // Different year: include year
            return date.format(DATE_WITH_YEAR_FORMATTER);
        }
    }

    /**
     * Format a time for voice output.
     * Example: "2:30 PM", "10:00 AM"
     */
    public static String formatTime(LocalTime time) {
        return time.format(TIME_FORMATTER);
    }

    /**
     * Format a datetime for voice output.
     * Example: "tomorrow at 2:30 PM"
     */
    public static String formatDateTimeNatural(LocalDateTime dateTime) {
        String dateStr = formatDateNatural(dateTime.toLocalDate());
        String timeStr = formatTime(dateTime.toLocalTime());
        return dateStr + " at " + timeStr;
    }

    /**
     * Format a datetime formally.
     * Example: "Friday, March 15 at 2:30 PM"
     */
    public static String formatDateTimeFormal(LocalDateTime dateTime) {
        return dateTime.format(FULL_FORMATTER);
    }

    /**
     * Format a duration in a natural way.
     * Examples: "30 minutes", "1 hour", "1 hour and 15 minutes"
     */
    public static String formatDuration(int minutes) {
        if (minutes < 60) {
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        } else if (minutes == 60) {
            return "1 hour";
        } else if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return hours + " hour" + (hours == 1 ? "" : "s");
        } else {
            int hours = minutes / 60;
            int mins = minutes % 60;
            return hours + " hour" + (hours == 1 ? "" : "s") + 
                   " and " + mins + " minute" + (mins == 1 ? "" : "s");
        }
    }

    /**
     * Format a relative time description.
     * Examples: "in 2 days", "in 1 week", "in 3 weeks"
     */
    public static String formatRelativeDate(LocalDate targetDate) {
        LocalDate today = LocalDate.now(DEFAULT_TIMEZONE);
        long daysDiff = ChronoUnit.DAYS.between(today, targetDate);

        if (daysDiff == 0) {
            return "today";
        } else if (daysDiff == 1) {
            return "tomorrow";
        } else if (daysDiff < 0) {
            daysDiff = Math.abs(daysDiff);
            if (daysDiff == 1) return "yesterday";
            if (daysDiff < 7) return daysDiff + " days ago";
            if (daysDiff < 14) return "last week";
            return (daysDiff / 7) + " weeks ago";
        } else if (daysDiff < 7) {
            return "in " + daysDiff + " days";
        } else if (daysDiff < 14) {
            return "next week";
        } else {
            long weeks = daysDiff / 7;
            return "in " + weeks + " week" + (weeks == 1 ? "" : "s");
        }
    }

    /**
     * Parse a natural language date reference.
     * Supports: "today", "tomorrow", "next week", "Friday", "March 15", etc.
     * 
     * @param input Natural language date reference
     * @return Parsed LocalDate, or null if cannot parse
     */
    public static LocalDate parseNaturalDate(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.toLowerCase().trim();
        LocalDate today = LocalDate.now(DEFAULT_TIMEZONE);

        // Handle simple relative dates
        switch (normalized) {
            case "today":
                return today;
            case "tomorrow":
                return today.plusDays(1);
            case "next week":
                return today.plusWeeks(1);
            case "next month":
                return today.plusMonths(1);
        }

        // Handle "next <day>" or "this <day>"
        for (DayOfWeek day : DayOfWeek.values()) {
            String dayName = day.getDisplayName(TextStyle.FULL, LOCALE).toLowerCase();
            if (normalized.equals(dayName) || normalized.equals("this " + dayName)) {
                // Find next occurrence of this day
                LocalDate result = today;
                while (result.getDayOfWeek() != day || result.equals(today)) {
                    result = result.plusDays(1);
                }
                return result;
            }
            if (normalized.equals("next " + dayName)) {
                // Find occurrence in next week
                LocalDate result = today.plusWeeks(1);
                while (result.getDayOfWeek() != day) {
                    result = result.plusDays(1);
                }
                return result;
            }
        }

        // Handle "in X days/weeks"
        if (normalized.startsWith("in ")) {
            String rest = normalized.substring(3);
            try {
                if (rest.endsWith(" days") || rest.endsWith(" day")) {
                    int days = Integer.parseInt(rest.split(" ")[0]);
                    return today.plusDays(days);
                }
                if (rest.endsWith(" weeks") || rest.endsWith(" week")) {
                    int weeks = Integer.parseInt(rest.split(" ")[0]);
                    return today.plusWeeks(weeks);
                }
            } catch (NumberFormatException e) {
                // Fall through
            }
        }

        // Try standard date parsing as fallback
        try {
            return LocalDate.parse(input);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the week description for a date.
     * Examples: "this week", "next week", "the week of March 10"
     */
    public static String getWeekDescription(LocalDate date) {
        LocalDate today = LocalDate.now(DEFAULT_TIMEZONE);
        LocalDate startOfThisWeek = today.with(DayOfWeek.MONDAY);
        LocalDate startOfTargetWeek = date.with(DayOfWeek.MONDAY);

        long weeksDiff = ChronoUnit.WEEKS.between(startOfThisWeek, startOfTargetWeek);

        if (weeksDiff == 0) {
            return "this week";
        } else if (weeksDiff == 1) {
            return "next week";
        } else if (weeksDiff == -1) {
            return "last week";
        } else {
            return "the week of " + startOfTargetWeek.format(DateTimeFormatter.ofPattern("MMMM d", LOCALE));
        }
    }
}
