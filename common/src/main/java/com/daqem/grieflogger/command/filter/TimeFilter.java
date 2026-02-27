package com.daqem.grieflogger.command.filter;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.model.TimeUnit;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Time filter for lookup/rollback commands.
 * Supports CoreProtect-style format: t:1d2h30m, t:1w, t:2mo, t:1y
 * Also supports time ranges: t:1w-1d (from 1 week ago to 1 day ago)
 */
public class TimeFilter implements IFilter {

    private static final List<Integer> NUMBERS = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 24, 30);
    private static final List<String> TIME_UNITS = List.of("s", "m", "h", "d", "w", "mo", "y");

    private final long startTime;  // Start of time range (earlier)
    private final long endTime;    // End of time range (later, 0 = now)

    public TimeFilter() {
        this.startTime = 0;
        this.endTime = 0;
    }

    public TimeFilter(long startTime, long endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @Override
    public String getName() {
        return GriefLogger.translate("filter.time").getString();
    }

    @Override
    public List<String> getOptions() {
        return new ArrayList<>();
    }

    @Override
    public String[] listSuggestions(SuggestionsBuilder builder, String prefix, String suffix) {
        String suggestionPrefix = "t:";

        if (suffix.isEmpty()) {
            // Suggest common time values
            return NUMBERS.stream()
                    .map(n -> suggestionPrefix + n)
                    .toArray(String[]::new);
        }

        // Extract the last numeric part for suggesting time units
        String lastPart = extractLastNumericPart(suffix);

        if (!lastPart.isEmpty()) {
            // User typed a number, suggest time units
            String basePart = suffix.substring(0, suffix.length() - lastPart.length());
            return TIME_UNITS.stream()
                    .map(unit -> suggestionPrefix + basePart + lastPart + unit)
                    .toArray(String[]::new);
        }

        // Check if suffix ends with a complete time unit
        if (endsWithTimeUnit(suffix)) {
            // Suggest adding more time or completing
            List<String> suggestions = new ArrayList<>();
            suggestions.add(suggestionPrefix + suffix);  // Current value is valid

            // Suggest adding more numbers
            for (int n : List.of(1, 2, 5, 10)) {
                suggestions.add(suggestionPrefix + suffix + n);
            }
            return suggestions.toArray(String[]::new);
        }

        return new String[]{suggestionPrefix + suffix};
    }

    private String extractLastNumericPart(String input) {
        StringBuilder result = new StringBuilder();
        for (int i = input.length() - 1; i >= 0; i--) {
            char c = input.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                result.insert(0, c);
            } else {
                break;
            }
        }
        return result.toString();
    }

    private boolean endsWithTimeUnit(String input) {
        String lower = input.toLowerCase();
        for (String unit : TIME_UNITS) {
            if (lower.endsWith(unit)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IFilter parse(StringReader reader, String suffix) {
        long[] times = TimeUnit.parseTimeString(suffix);
        return new TimeFilter(times[0], times[1]);
    }

    @Override
    public String toString() {
        return "TimeFilter{" +
                "startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }

    /**
     * Get the start time (earlier time in the range)
     * For single time values, this is the calculated time
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Get the end time (later time in the range)
     * Returns 0 if no end time specified (means "until now")
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * For backwards compatibility - returns start time
     */
    public long getTime() {
        return startTime;
    }

    /**
     * Check if this is a time range filter
     */
    public boolean isRange() {
        return endTime > 0;
    }
}
