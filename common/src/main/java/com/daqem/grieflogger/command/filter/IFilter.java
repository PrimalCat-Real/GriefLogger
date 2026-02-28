package com.daqem.grieflogger.command.filter;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Arrays;
import java.util.List;

public interface IFilter {

    /**
     * Short prefix character (e.g., 'u' for user, 't' for time)
     */
    default char getPrefix() {
        return getName().charAt(0);
    }

    /**
     * Full name of the filter (e.g., "user", "time", "action")
     */
    String getName();

    /**
     * Available options for autocomplete
     */
    List<String> getOptions();

    /**
     * Get all valid prefixes for this filter (short and full)
     * E.g., for user: ["u", "user", "users", "p"]
     */
    default List<String> getAllPrefixes() {
        return List.of(String.valueOf(getPrefix()), getName());
    }

    default String[] listSuggestions(SuggestionsBuilder builder, String prefix, String suffix) {
        String suggestionPrefix = String.valueOf(getPrefix()) + ":";

        if (suffix.contains(",")) {
            int lastIndexOf = suffix.lastIndexOf(",");
            String[] usedValues = suffix.substring(0, lastIndexOf).split(",");
            String suffixPrefix = suffix.substring(0, lastIndexOf);

            return getOptions().stream()
                    .filter(s -> !Arrays.asList(usedValues).contains(s))
                    .map(s -> suggestionPrefix + suffixPrefix + "," + s)
                    .toArray(String[]::new);
        }

        return getOptions().stream()
                .map(s -> suggestionPrefix + s)
                .toArray(String[]::new);
    }

    IFilter parse(StringReader reader, String suffix) throws CommandSyntaxException;

    default String[] listSuggestions(SuggestionsBuilder builder) {
        String str = builder.getRemaining();
        int colonIndex = str.indexOf(':');
        String prefix;
        String suffix;

        if (colonIndex != -1) {
            prefix = str.substring(0, colonIndex);
            suffix = str.length() > colonIndex + 1 ? str.substring(colonIndex + 1) : "";
        } else {
            int dotIndex = str.indexOf('.');
            if (dotIndex != -1) {
                prefix = str.substring(0, dotIndex);
                suffix = str.length() > dotIndex + 1 ? str.substring(dotIndex + 1) : "";
            } else {
                prefix = str;
                suffix = "";
            }
        }

        return listSuggestions(builder, prefix, suffix);
    }
}
