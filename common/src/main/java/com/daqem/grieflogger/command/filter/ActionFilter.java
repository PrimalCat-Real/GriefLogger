package com.daqem.grieflogger.command.filter;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.model.action.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.*;

/**
 * Action filter for lookup/rollback commands.
 * Supports CoreProtect-style format: a:break, a:+block, a:-container, a:kill
 */
public class ActionFilter implements IFilter {

    /**
     * CoreProtect-style action aliases
     */
    private static final Map<String, List<IAction>> ACTION_ALIASES = new LinkedHashMap<>();

    static {
        ACTION_ALIASES.put("block", List.of(BlockAction.BREAK_BLOCK, BlockAction.PLACE_BLOCK));
        ACTION_ALIASES.put("blocks", List.of(BlockAction.BREAK_BLOCK, BlockAction.PLACE_BLOCK));
        ACTION_ALIASES.put("+block", List.of(BlockAction.PLACE_BLOCK));
        ACTION_ALIASES.put("-block", List.of(BlockAction.BREAK_BLOCK));
        ACTION_ALIASES.put("place", List.of(BlockAction.PLACE_BLOCK));
        ACTION_ALIASES.put("placed", List.of(BlockAction.PLACE_BLOCK));
        ACTION_ALIASES.put("break", List.of(BlockAction.BREAK_BLOCK));
        ACTION_ALIASES.put("broke", List.of(BlockAction.BREAK_BLOCK));
        ACTION_ALIASES.put("remove", List.of(BlockAction.BREAK_BLOCK));

        ACTION_ALIASES.put("click", List.of(BlockAction.INTERACT_BLOCK));
        ACTION_ALIASES.put("interact", List.of(BlockAction.INTERACT_BLOCK));

        ACTION_ALIASES.put("kill", List.of(BlockAction.KILL_ENTITY));
        ACTION_ALIASES.put("death", List.of(BlockAction.KILL_ENTITY));

        ACTION_ALIASES.put("container", List.of(ItemAction.REMOVE_ITEM, ItemAction.ADD_ITEM));
        ACTION_ALIASES.put("+container", List.of(ItemAction.ADD_ITEM));
        ACTION_ALIASES.put("-container", List.of(ItemAction.REMOVE_ITEM));
        ACTION_ALIASES.put("chest", List.of(ItemAction.REMOVE_ITEM, ItemAction.ADD_ITEM));

        ACTION_ALIASES.put("item", List.of(ItemAction.DROP_ITEM, ItemAction.PICKUP_ITEM));
        ACTION_ALIASES.put("items", List.of(ItemAction.DROP_ITEM, ItemAction.PICKUP_ITEM));
        ACTION_ALIASES.put("+item", List.of(ItemAction.PICKUP_ITEM));
        ACTION_ALIASES.put("-item", List.of(ItemAction.DROP_ITEM));
        ACTION_ALIASES.put("drop", List.of(ItemAction.DROP_ITEM));
        ACTION_ALIASES.put("pickup", List.of(ItemAction.PICKUP_ITEM));

        ACTION_ALIASES.put("session", List.of(SessionAction.JOIN, SessionAction.QUIT));
        ACTION_ALIASES.put("+session", List.of(SessionAction.JOIN));
        ACTION_ALIASES.put("-session", List.of(SessionAction.QUIT));
        ACTION_ALIASES.put("login", List.of(SessionAction.JOIN));
        ACTION_ALIASES.put("logout", List.of(SessionAction.QUIT));
        ACTION_ALIASES.put("join", List.of(SessionAction.JOIN));
        ACTION_ALIASES.put("quit", List.of(SessionAction.QUIT));
    }

    /**
     * Suggestions shown in autocomplete (most useful actions)
     */
    private static final List<String> SUGGESTION_OPTIONS = List.of(
            "block", "+block", "-block",
            "click", "kill",
            "container", "+container", "-container",
            "item", "+item", "-item",
            "session", "+session", "-session"
    );

    private final List<IAction> actions;

    public ActionFilter() {
        this(new ArrayList<>());
    }

    public ActionFilter(List<IAction> actions) {
        this.actions = actions;
    }

    @Override
    public String getName() {
        return GriefLogger.translate("filter.action").getString();
    }

    @Override
    public List<String> getOptions() {
        return SUGGESTION_OPTIONS;
    }

    @Override
    public String[] listSuggestions(SuggestionsBuilder builder, String prefix, String suffix) {
        String suggestionPrefix = "a:";

        if (suffix.isEmpty()) {
            return SUGGESTION_OPTIONS.stream()
                    .map(s -> suggestionPrefix + s)
                    .toArray(String[]::new);
        }

        return SUGGESTION_OPTIONS.stream()
                .filter(s -> s.toLowerCase().startsWith(suffix.toLowerCase()))
                .map(s -> suggestionPrefix + s)
                .toArray(String[]::new);
    }

    @Override
    public IFilter parse(StringReader reader, String suffix) throws CommandSyntaxException {
        String[] split = suffix.toLowerCase().split(",");
        List<IAction> parsedActions = new ArrayList<>();

        for (String actionStr : split) {
            String trimmed = actionStr.trim();

            List<IAction> aliasActions = ACTION_ALIASES.get(trimmed);
            if (aliasActions != null) {
                parsedActions.addAll(aliasActions);
                continue;
            }

            IAction action = Actions.getAction(trimmed);
            if (action != null) {
                parsedActions.add(action);
                continue;
            }

            action = Actions.getAction(trimmed.replace("-", "_"));
            if (action != null) {
                parsedActions.add(action);
                continue;
            }

            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(reader);
        }

        if (parsedActions.isEmpty()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                    .createWithContext(reader);
        }

        return new ActionFilter(parsedActions);
    }

    @Override
    public String toString() {
        return "ActionFilter{" +
                "actions=" + actions +
                '}';
    }

    public List<IAction> getActions() {
        return actions;
    }
}
