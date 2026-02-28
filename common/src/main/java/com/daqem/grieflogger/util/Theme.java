package com.daqem.grieflogger.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

/**
 * Centralized color palette for GriefLogger using Kyori Adventure.
 * Supports hex colors, gradients, and MiniMessage formatting.
 *
 * Only 7 colors in palette:
 * - PRIMARY: headers, mod name
 * - SECONDARY: values, descriptions
 * - ERROR: errors, warnings
 * - SUCCESS: confirmations
 * - ACCENT: highlights, important values
 * - MUTED: timestamps, coordinates
 * - INFO: informational messages
 */
public final class Theme {

    private Theme() {}

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final GsonComponentSerializer GSON_SERIALIZER = GsonComponentSerializer.gson();
    private static final Gson GSON = new GsonBuilder().create();


    public static final TextColor PRIMARY = TextColor.fromHexString("#55FFFF");     
    public static final TextColor SECONDARY = TextColor.fromHexString("#FFFFFF");   
    public static final TextColor ERROR = TextColor.fromHexString("#FF5555");       
    public static final TextColor SUCCESS = TextColor.fromHexString("#55FF55");     
    public static final TextColor ACCENT = TextColor.fromHexString("#FFAA00");      
    public static final TextColor MUTED = TextColor.fromHexString("#AAAAAA");       
    public static final TextColor INFO = TextColor.fromHexString("#5555FF");        


    /**
     * Create primary colored text
     */
    public static Component primary(String text) {
        return Component.text(text).color(PRIMARY);
    }

    /**
     * Create secondary colored text
     */
    public static Component secondary(String text) {
        return Component.text(text).color(SECONDARY);
    }

    /**
     * Create error colored text
     */
    public static Component error(String text) {
        return Component.text(text).color(ERROR);
    }

    /**
     * Create success colored text
     */
    public static Component success(String text) {
        return Component.text(text).color(SUCCESS);
    }

    /**
     * Create accent colored text
     */
    public static Component accent(String text) {
        return Component.text(text).color(ACCENT);
    }

    /**
     * Create muted colored text
     */
    public static Component muted(String text) {
        return Component.text(text).color(MUTED);
    }

    /**
     * Create info colored text
     */
    public static Component info(String text) {
        return Component.text(text).color(INFO);
    }


    /**
     * Create gradient text using MiniMessage format
     * Example: gradient("#FF0000", "#00FF00", "Hello") -> red to green gradient
     */
    public static Component gradient(String startHex, String endHex, String text) {
        String miniMessageFormat = "<gradient:" + startHex + ":" + endHex + ">" + text + "</gradient>";
        return MINI_MESSAGE.deserialize(miniMessageFormat);
    }

    /**
     * Create gradient with multiple colors
     * Example: gradient(List.of("#FF0000", "#00FF00", "#0000FF"), "Rainbow")
     */
    public static Component gradient(java.util.List<String> hexColors, String text) {
        String colors = String.join(":", hexColors);
        String miniMessageFormat = "<gradient:" + colors + ">" + text + "</gradient>";
        return MINI_MESSAGE.deserialize(miniMessageFormat);
    }

    /**
     * Parse MiniMessage format directly
     * Supports: <red>, <#FF0000>, <gradient:red:blue>, <bold>, etc.
     */
    public static Component parse(String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage);
    }


    /**
     * Create header: "----- Title -----"
     */
    public static Component header(String title) {
        return Component.empty()
                .append(secondary("----- "))
                .append(primary(title))
                .append(secondary(" -----"));
    }

    /**
     * Create labeled value: "Label: value"
     */
    public static Component labelValue(String label, String value) {
        return Component.empty()
                .append(primary(label + ": "))
                .append(secondary(value));
    }

    /**
     * Create labeled value with custom color
     */
    public static Component labelValue(String label, String value, TextColor valueColor) {
        return Component.empty()
                .append(primary(label + ": "))
                .append(Component.text(value).color(valueColor));
    }

    /**
     * Create mod prefix message: "GriefLogger - message"
     */
    public static Component message(String text) {
        return Component.empty()
                .append(primary("GriefLogger"))
                .append(secondary(" - "))
                .append(secondary(text));
    }

    /**
     * Create error message with prefix
     */
    public static Component errorMessage(String text) {
        return Component.empty()
                .append(primary("GriefLogger"))
                .append(secondary(" - "))
                .append(error(text));
    }

    /**
     * Create success message with prefix
     */
    public static Component successMessage(String text) {
        return Component.empty()
                .append(primary("GriefLogger"))
                .append(secondary(" - "))
                .append(success(text));
    }


    /**
     * Convert Kyori Component to Minecraft Component
     */
    public static net.minecraft.network.chat.Component toMinecraft(Component kyoriComponent) {
        String json = GSON_SERIALIZER.serialize(kyoriComponent);
        return net.minecraft.network.chat.Component.Serializer.fromJson(json, net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.empty()));
    }

    /**
     * Convert Minecraft Component to Kyori Component
     */
    public static Component fromMinecraft(net.minecraft.network.chat.Component minecraftComponent) {
        String json = net.minecraft.network.chat.Component.Serializer.toJson(minecraftComponent, net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.empty()));
        return GSON_SERIALIZER.deserialize(json);
    }
}
