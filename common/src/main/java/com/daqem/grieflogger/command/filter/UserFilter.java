package com.daqem.grieflogger.command.filter;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.cache.Caches;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * User filter for lookup/rollback commands.
 * Supports CoreProtect-style format: u:Steve, u:Steve,Alex, user:Player1
 * Also supports special values: #global
 */
public class UserFilter implements IFilter {

    private final Map<Integer, String> usernames;
    private final boolean isGlobal;

    public UserFilter() {
        this(new HashMap<>(), false);
    }

    public UserFilter(Map<Integer, String> usernames, boolean isGlobal) {
        this.usernames = usernames;
        this.isGlobal = isGlobal;
    }

    @Override
    public String getName() {
        return GriefLogger.translate("filter.user").getString();
    }

    @Override
    public List<String> getAllPrefixes() {
        return List.of("u", "user", "users", "p");
    }

    @Override
    public List<String> getOptions() {
        List<String> options = new ArrayList<>();
        options.add("#global");  
        options.addAll(Caches.USER.getAllUsernames().values());
        return options;
    }

    @Override
    public String[] listSuggestions(SuggestionsBuilder builder, String prefix, String suffix) {
        String suggestionPrefix = "u:";

        List<String> allUsernames = getOptions();

        if (suffix.isEmpty()) {
            return allUsernames.stream()
                    .limit(15)
                    .map(s -> suggestionPrefix + s)
                    .toArray(String[]::new);
        }

        if (suffix.contains(",")) {
            int lastComma = suffix.lastIndexOf(",");
            String[] usedNames = suffix.substring(0, lastComma).split(",");
            Set<String> usedSet = new HashSet<>(Arrays.asList(usedNames));
            String currentPart = suffix.substring(lastComma + 1).toLowerCase();

            return allUsernames.stream()
                    .filter(s -> !usedSet.contains(s))
                    .filter(s -> s.toLowerCase().startsWith(currentPart))
                    .limit(10)
                    .map(s -> suggestionPrefix + suffix.substring(0, lastComma + 1) + s)
                    .toArray(String[]::new);
        }

        return allUsernames.stream()
                .filter(s -> s.toLowerCase().startsWith(suffix.toLowerCase()))
                .limit(10)
                .map(s -> suggestionPrefix + s)
                .toArray(String[]::new);
    }

    @Override
    public IFilter parse(StringReader reader, String suffix) throws CommandSyntaxException {
        String[] split = suffix.split(",");

        if (split.length == 1 && split[0].equalsIgnoreCase("#global")) {
            return new UserFilter(new HashMap<>(), true);
        }

        List<String> usernamesToFind = Arrays.stream(split)
                .filter(s -> !s.startsWith("#"))
                .collect(Collectors.toList());

        Map<Integer, String> usernames = Caches.USER.getAllUsernames().entrySet().stream()
                .filter(entry -> usernamesToFind.stream()
                        .anyMatch(name -> name.equalsIgnoreCase(entry.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (usernamesToFind.size() != usernames.size()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(reader);
        }

        return new UserFilter(usernames, false);
    }

    @Override
    public String toString() {
        return "UserFilter{" +
                "usernames=" + usernames +
                ", isGlobal=" + isGlobal +
                '}';
    }

    public List<Integer> getUserIds() {
        return usernames.keySet().stream().toList();
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    public Map<Integer, String> getUsernames() {
        return usernames;
    }
}
