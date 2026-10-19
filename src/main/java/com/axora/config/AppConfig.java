package com.axora.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Centralised application configuration.
 * Data directory: %APPDATA%\Axora
 */
public final class AppConfig {

    public static final String APP_NAME    = "Axora";
    public static final String APP_TAGLINE = "Value in every choice. Balance in every step.";
    public static final String DB_FILENAME = "axora.db";

    private AppConfig() {}

    /** Returns the application data directory, creating it if necessary. */
    public static Path dataDirectory() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            appData = System.getProperty("user.home");
        }
        Path dir = Paths.get(appData, APP_NAME);
        dir.toFile().mkdirs();
        return dir;
    }

    public static Path databasePath() {
        return dataDirectory().resolve(DB_FILENAME);
    }
}
