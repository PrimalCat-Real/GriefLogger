package com.daqem.grieflogger.command.filter;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Filter for handling flags like f:count, f:preview, f:verbose.
 * These are flags with the "f:" prefix.
 *
 * Usage: /gl lookup u:Steve t:1d f:count
 */
public class FlagsFilter implements IFilter {

    public static final String FLAG_COUNT = "count";
    public static final String FLAG_PREVIEW = "preview";
    public static final String FLAG_VERBOSE = "verbose";
    public static final String FLAG_CONTAINER = "container";

    private static final List<String> ALL_FLAGS = List.of(
            FLAG_COUNT,
            FLAG_PREVIEW,
            FLAG_VERBOSE,
            FLAG_CONTAINER
    );

    private final Set<String> activeFlags = new HashSet<>();

    public FlagsFilter() {
    }

    public FlagsFilter(String flag) {
        activeFlags.add(flag.toLowerCase());
    }

    @Override
    public char getPrefix() {
        return 'f';
    }

    @Override
    public String getName() {
        return "flag";
    }

    @Override
    public List<String> getAllPrefixes() {
        return List.of("f", "flag");
    }

    @Override
    public List<String> getOptions() {
        return List.of(FLAG_COUNT, FLAG_PREVIEW, FLAG_VERBOSE, FLAG_CONTAINER);
    }

    @Override
    public String[] listSuggestions(SuggestionsBuilder builder, String prefix, String suffix) {
        return ALL_FLAGS.stream()
                .filter(f -> f.startsWith(suffix.toLowerCase()))
                .map(f -> "f:" + f)
                .toArray(String[]::new);
    }

    @Override
    public IFilter parse(StringReader reader, String suffix) throws CommandSyntaxException {
        String flag = suffix.toLowerCase();
        if (!getOptions().contains(flag)) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException()
                    .createWithContext(reader, "Unknown flag: f:" + suffix + ". Valid flags: f:count, f:preview, f:verbose, f:container");
        }
        return new FlagsFilter(flag);
    }

    public boolean hasFlag(String flag) {
        return activeFlags.contains(flag.toLowerCase());
    }

    public boolean isCount() {
        return hasFlag(FLAG_COUNT);
    }

    public boolean isPreview() {
        return hasFlag(FLAG_PREVIEW);
    }

    public boolean isVerbose() {
        return hasFlag(FLAG_VERBOSE);
    }

    public boolean isContainer() {
        return hasFlag(FLAG_CONTAINER);
    }

    public Set<String> getActiveFlags() {
        return activeFlags;
    }

    public void addFlag(String flag) {
        activeFlags.add(flag.toLowerCase());
    }
}
