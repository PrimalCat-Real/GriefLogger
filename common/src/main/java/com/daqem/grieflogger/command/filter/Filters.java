package com.daqem.grieflogger.command.filter;

import java.util.List;
import java.util.Map;

public interface Filters {

    IFilter ACTION = new ActionFilter();
    IFilter EXCLUDE = new ExcludeFilter();
    IFilter INCLUDE = new IncludeFilter();
    IFilter RADIUS = new RadiusFilter();
    IFilter TIME = new TimeFilter();
    IFilter USER = new UserFilter();
    IFilter FLAGS = new FlagsFilter();

    List<IFilter> FILTERS = List.of(ACTION, EXCLUDE, INCLUDE, RADIUS, TIME, USER, FLAGS);

    /**
     * Alias mappings for CoreProtect compatibility
     * Maps alternative prefixes to their canonical filter
     */
    Map<String, IFilter> ALIASES = Map.ofEntries(
            Map.entry("u", USER),
            Map.entry("user", USER),
            Map.entry("users", USER),
            Map.entry("p", USER),  

            Map.entry("t", TIME),
            Map.entry("time", TIME),

            Map.entry("r", RADIUS),
            Map.entry("radius", RADIUS),

            Map.entry("a", ACTION),
            Map.entry("action", ACTION),

            Map.entry("i", INCLUDE),
            Map.entry("include", INCLUDE),
            Map.entry("item", INCLUDE),
            Map.entry("items", INCLUDE),
            Map.entry("b", INCLUDE),  
            Map.entry("block", INCLUDE),
            Map.entry("blocks", INCLUDE),

            Map.entry("e", EXCLUDE),
            Map.entry("exclude", EXCLUDE),

            Map.entry("f", FLAGS),
            Map.entry("flag", FLAGS),
            Map.entry("flags", FLAGS)
    );

    /**
     * Find filter by prefix (supports both short and full names)
     * E.g., "u", "user", "users", "p" all map to UserFilter
     */
    static IFilter fromPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return null;
        }

        String lowerPrefix = prefix.toLowerCase();

        IFilter aliasMatch = ALIASES.get(lowerPrefix);
        if (aliasMatch != null) {
            return aliasMatch;
        }

        if (prefix.length() == 1) {
            return FILTERS.stream()
                    .filter(x -> x.getPrefix() == Character.toLowerCase(prefix.charAt(0)))
                    .findFirst()
                    .orElse(null);
        }

        return FILTERS.stream()
                .filter(x -> x.getName().toLowerCase().startsWith(lowerPrefix))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get suggestion strings for available filters
     * Uses short prefix format with colon (e.g., "u:", "t:", "r:")
     */
    static String[] getFilteredSuggestions(List<IFilter> filters, boolean hasItemFilter) {
        return FILTERS.stream()
                .filter(x -> !hasItemFilter || !(x instanceof ItemFilter))
                .filter(x -> filters.stream().noneMatch(y -> y.getClass() == x.getClass()))
                .map(x -> x.getPrefix() + ":")
                .toArray(String[]::new);
    }

    /**
     * Get full suggestion strings (both short and long format)
     */
    static String[] getAllSuggestions(List<IFilter> filters, boolean hasItemFilter) {
        return FILTERS.stream()
                .filter(x -> !hasItemFilter || !(x instanceof ItemFilter))
                .filter(x -> filters.stream().noneMatch(y -> y.getClass() == x.getClass()))
                .flatMap(x -> java.util.stream.Stream.of(
                        x.getPrefix() + ":",
                        x.getName() + ":"
                ))
                .toArray(String[]::new);
    }
}
