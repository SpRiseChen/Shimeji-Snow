package com.group_finity.mascot.dialogue;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.group_finity.mascot.AppHome;

/** Read-only, mod-overridable reminder and bubble configuration. */
final class DialogueConfig {
    private final Properties values = new Properties();

    static DialogueConfig load() {
        DialogueConfig config = new DialogueConfig();
        try (Reader reader = Files.newBufferedReader(AppHome.get("conf", "dialogue.properties"), StandardCharsets.UTF_8)) {
            config.values.load(reader);
        } catch (Exception e) {
            Logger.getLogger(DialogueConfig.class.getName()).log(Level.WARNING, "Using default dialogue timings", e);
        }
        return config;
    }

    int number(String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(values.getProperty(key, ""))));
        } catch (NumberFormatException e) { return fallback; }
    }

    boolean enabled() { return Boolean.parseBoolean(values.getProperty("enabled", "true")); }
    long movementNanos() { return number("movementMinutes", 30, 1, 1440) * 60_000_000_000L; }
    long waterNanos() { return number("waterMinutes", 45, 1, 1440) * 60_000_000_000L; }
    int reminderMillis() { return number("reminderSeconds", 10, 1, 120) * 1000; }
    int goodbyeMillis() { return number("goodbyeSeconds", 3, 1, 15) * 1000; }
    int width() { return number("bubbleWidth", 300, 160, 600); }
    int gap() { return number("bubbleGap", 8, 0, 40); }
}
