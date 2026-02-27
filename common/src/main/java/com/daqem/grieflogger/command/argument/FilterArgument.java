package com.daqem.grieflogger.command.argument;

import com.daqem.grieflogger.command.filter.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Argument parser for filters in CoreProtect-style format.
 * Supports both short (u:Steve) and full (user:Steve) prefixes.
 * Also maintains backwards compatibility with old dot format (u.Steve).
 *
 * Uses greedyString() approach - parses all filters from a single string.
 */
public class FilterArgument {

    private static final char SEPARATOR = ':';
    private static final char LEGACY_SEPARATOR = '.';

    /**
     * Parse all filters from a raw string input.
     * Example: "u:Steve t:1d r:10 a:break"
     */
    public static FilterList parseFilters(String raw, CommandSourceStack source) {
        List<IFilter> filters = new ArrayList<>();

        if (raw == null || raw.isBlank()) {
            return new FilterList(filters, source);
        }

        // Split by whitespace
        String[] tokens = raw.trim().split("\\s+");

        for (String token : tokens) {
            try {
                IFilter filter = parseToken(token);
                if (filter != null) {
                    filters.add(filter);
                }
            } catch (CommandSyntaxException e) {
                // Skip invalid tokens, or could log warning
            }
        }

        return new FilterList(filters, source);
    }

    /**
     * Parse a single filter token like "u:Steve" or "t:1d"
     */
    public static IFilter parseToken(String token) throws CommandSyntaxException {
        if (token == null || token.isBlank()) {
            return null;
        }

        String trimmed = token.trim();

        // Find separator (prefer ":" but fallback to "." for backwards compatibility)
        int separatorIndex = trimmed.indexOf(SEPARATOR);

        if (separatorIndex == -1) {
            separatorIndex = trimmed.indexOf(LEGACY_SEPARATOR);
        }

        if (separatorIndex == -1 || separatorIndex == 0) {
            return null; // No valid separator found
        }

        String prefix = trimmed.substring(0, separatorIndex);
        IFilter filter = Filters.fromPrefix(prefix);

        if (filter == null) {
            return null; // Unknown filter prefix
        }

        String suffix = trimmed.substring(separatorIndex + 1);
        if (suffix.isEmpty()) {
            return null; // Empty value
        }

        return filter.parse(new StringReader(trimmed), suffix);
    }

    /**
     * Provide suggestions for greedyString argument.
     * Called by Brigadier's suggests() callback.
     */
    public static CompletableFuture<Suggestions> suggestFilters(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String input = builder.getRemaining();

        // Find the last token being typed
        int lastSpace = input.lastIndexOf(' ');
        String currentToken = lastSpace >= 0 ? input.substring(lastSpace + 1) : input;
        String prefix = lastSpace >= 0 ? input.substring(0, lastSpace + 1) : "";

        // Parse already entered filters
        List<IFilter> existingFilters = new ArrayList<>();
        if (lastSpace > 0) {
            String[] previousTokens = input.substring(0, lastSpace).trim().split("\\s+");
            for (String token : previousTokens) {
                try {
                    IFilter f = parseToken(token);
                    if (f != null) {
                        existingFilters.add(f);
                    }
                } catch (CommandSyntaxException ignored) {
                }
            }
        }

        // Get suggestions based on current token
        String[] suggestions = getSuggestionsForToken(currentToken, existingFilters);

        // Build suggestions with prefix
        SuggestionsBuilder offsetBuilder = builder.createOffset(builder.getStart() + prefix.length());
        return SharedSuggestionProvider.suggest(suggestions, offsetBuilder);
    }

    /**
     * Get suggestions for the current token being typed.
     */
    private static String[] getSuggestionsForToken(String currentToken, List<IFilter> existingFilters) {
        String lower = currentToken.toLowerCase();

        // Extract prefix if separator exists
        int colonIndex = lower.indexOf(':');
        int dotIndex = lower.indexOf('.');

        // If no separator, suggest filter prefixes
        if (colonIndex == -1 && dotIndex == -1) {
            return getAvailableFilterPrefixes(existingFilters, lower);
        }

        // Has separator - suggest values for this filter
        int sepIndex = colonIndex != -1 ? colonIndex : dotIndex;
        String filterPrefix = lower.substring(0, sepIndex);
        String valuePart = lower.substring(sepIndex + 1);

        IFilter filter = Filters.fromPrefix(filterPrefix);
        if (filter == null) {
            return new String[0];
        }

        // Get value suggestions from the filter
        return filter.listSuggestions(null, filterPrefix, valuePart);
    }

    /**
     * Get available filter prefixes that haven't been used yet.
     */
    private static String[] getAvailableFilterPrefixes(List<IFilter> existingFilters, String typedPrefix) {
        List<String> suggestions = new ArrayList<>();

        for (IFilter filter : Filters.FILTERS) {
            // Skip if this filter type is already used
            boolean alreadyUsed = existingFilters.stream()
                    .anyMatch(f -> f.getClass().equals(filter.getClass()));
            if (alreadyUsed) {
                continue;
            }

            String suggestion = filter.getPrefix() + ":";
            if (suggestion.startsWith(typedPrefix)) {
                suggestions.add(suggestion);
            }
        }

        return suggestions.toArray(new String[0]);
    }
}
