package com.daqem.grieflogger.model;

import com.daqem.grieflogger.GriefLogger;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Time units for filter parsing.
 * Supports CoreProtect-style time format: y, mo, w, d, h, m, s
 */
public enum TimeUnit {
    SECOND(1000L, "s", GriefLogger.translate("time.seconds")),
    MINUTE(60 * SECOND.getMilliseconds(), "m", GriefLogger.translate("time.minutes")),
    HOUR(60 * MINUTE.getMilliseconds(), "h", GriefLogger.translate("time.hours")),
    DAY(24 * HOUR.getMilliseconds(), "d", GriefLogger.translate("time.days")),
    WEEK(7 * DAY.getMilliseconds(), "w", GriefLogger.translate("time.weeks")),
    MONTH(30 * DAY.getMilliseconds(), "mo", GriefLogger.translate("time.months")),
    YEAR(365 * DAY.getMilliseconds(), "y", GriefLogger.translate("time.years"));

    private final long milliseconds;
    private final String abbreviation;
    private final Component component;

    TimeUnit(long milliseconds, String abbreviation, Component component) {
        this.milliseconds = milliseconds;
        this.abbreviation = abbreviation;
        this.component = component;
    }

    public long getMilliseconds() {
        return milliseconds;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public Component getComponent() {
        return component;
    }

    /**
     * Get all abbreviations in order for parsing
     */
    public static List<String> getAbbreviations() {
        return Arrays.stream(values())
                .map(TimeUnit::getAbbreviation)
                .collect(Collectors.toList());
    }

    /**
     * Find TimeUnit by abbreviation
     */
    public static TimeUnit fromAbbreviation(String abbreviation) {
        if (abbreviation == null || abbreviation.isEmpty()) {
            return null;
        }

        String lower = abbreviation.toLowerCase();

        // Special case: "mo" for months (must check before "m")
        if (lower.equals("mo")) {
            return MONTH;
        }

        for (TimeUnit unit : values()) {
            if (unit.abbreviation.equals(lower)) {
                return unit;
            }
        }
        return null;
    }

    /**
     * Parse a time string like "1d2h30m" into milliseconds
     * Supports CoreProtect format: y, mo, w, d, h, m, s
     * Also supports time ranges: "1w-1d" (from 1 week ago to 1 day ago)
     *
     * @return array of [startTime, endTime] in milliseconds from epoch
     */
    public static long[] parseTimeString(String input) {
        if (input == null || input.isEmpty()) {
            return new long[]{0, 0};
        }

        String lower = input.toLowerCase().trim();

        // Check for time range (e.g., "1w-1d")
        int dashIndex = lower.indexOf('-');
        if (dashIndex > 0 && dashIndex < lower.length() - 1) {
            // Check if this is actually a range and not a negative number
            char beforeDash = lower.charAt(dashIndex - 1);
            if (!Character.isDigit(beforeDash)) {
                // It's part of a number, not a range
                dashIndex = -1;
            }
        }

        if (dashIndex > 0) {
            String startPart = lower.substring(0, dashIndex);
            String endPart = lower.substring(dashIndex + 1);

            long startOffset = parseSingleTime(startPart);
            long endOffset = parseSingleTime(endPart);

            long now = System.currentTimeMillis();

            // Larger offset = further in the past = earlier time
            if (startOffset >= endOffset) {
                return new long[]{now - startOffset, now - endOffset};
            } else {
                return new long[]{now - endOffset, now - startOffset};
            }
        }

        // Single time value
        long offset = parseSingleTime(lower);
        return new long[]{System.currentTimeMillis() - offset, 0};
    }

    /**
     * Parse a single time value like "1d2h30m" into milliseconds offset
     */
    private static long parseSingleTime(String input) {
        if (input == null || input.isEmpty()) {
            return 0;
        }

        long totalMillis = 0;
        StringBuilder numberBuffer = new StringBuilder();

        // Process character by character
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (Character.isDigit(c) || c == '.') {
                numberBuffer.append(c);
            } else {
                // Found a letter - determine the unit
                String unitStr = String.valueOf(c);

                // Check for "mo" (months)
                if (c == 'm' && i + 1 < input.length() && input.charAt(i + 1) == 'o') {
                    unitStr = "mo";
                    i++; // Skip the 'o'
                }

                if (numberBuffer.length() > 0) {
                    try {
                        double value = Double.parseDouble(numberBuffer.toString());
                        TimeUnit unit = fromAbbreviation(unitStr);
                        if (unit != null) {
                            totalMillis += (long) (value * unit.getMilliseconds());
                        }
                    } catch (NumberFormatException ignored) {
                        // Invalid number, skip
                    }
                    numberBuffer.setLength(0);
                }
            }
        }

        return totalMillis;
    }
}
