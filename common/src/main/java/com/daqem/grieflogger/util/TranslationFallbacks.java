package com.daqem.grieflogger.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Loads bundled translations used as fallbacks for clients without the mod. */
public final class TranslationFallbacks {

    private static final String DEFAULT_LANGUAGE = "en_us";
    private static final Map<String, Map<String, String>> TRANSLATIONS = new ConcurrentHashMap<>();

    private TranslationFallbacks() {
    }

    public static String get(String language, String key) {
        String normalizedLanguage = normalizeLanguage(language);
        String translation = getTranslations(normalizedLanguage).get(key);
        if (translation == null && !DEFAULT_LANGUAGE.equals(normalizedLanguage)) {
            translation = getTranslations(DEFAULT_LANGUAGE).get(key);
        }
        return translation != null ? translation : key;
    }

    private static String normalizeLanguage(String language) {
        if (language == null) {
            return DEFAULT_LANGUAGE;
        }

        String normalizedLanguage = language.toLowerCase(Locale.ROOT);
        return normalizedLanguage.matches("[a-z0-9_-]+") ? normalizedLanguage : DEFAULT_LANGUAGE;
    }

    private static Map<String, String> getTranslations(String language) {
        return TRANSLATIONS.computeIfAbsent(language, TranslationFallbacks::loadTranslations);
    }

    private static Map<String, String> loadTranslations(String language) {
        String resourcePath = "/assets/grieflogger/lang/" + language + ".json";
        try (InputStream inputStream = TranslationFallbacks.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return Map.of();
            }

            JsonObject translations = JsonParser.parseReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            ).getAsJsonObject();
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : translations.entrySet()) {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                    result.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            return Map.copyOf(result);
        } catch (IOException | RuntimeException exception) {
            return Map.of();
        }
    }
}
