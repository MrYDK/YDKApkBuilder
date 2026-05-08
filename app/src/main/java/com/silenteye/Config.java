package com.silenteye;

/**
 * Central configuration file for EchoRunner.
 *
 * HOW THE BUILD SYSTEM USES THIS FILE
 * ─────────────────────────────────────
 * Before compiling each unique APK the backend performs a simple string
 * replacement:
 *
 *   sed -i 's/DEFAULT_ID/<actual-client-uuid>/g' Config.java
 *
 * The value of CLIENT_ID is therefore baked into the compiled APK at build
 * time. No activation key, login, or user interaction is ever required.
 *
 * RULES
 * ─────
 * • Do NOT read CLIENT_ID from SharedPreferences or any runtime source.
 * • Do NOT allow the user to change CLIENT_ID.
 * • The placeholder string "DEFAULT_ID" must appear exactly once in this
 *   file so that a single sed/replace call is unambiguous.
 * • Keep this class final so it cannot be sub-classed or instantiated.
 */
public final class Config {

    // -----------------------------------------------------------------------
    // ⚠  BACKEND PLACEHOLDER — replaced before each APK build
    //    Format accepted: any non-empty alphanumeric string, hyphens allowed.
    //    Example replacement value: "client-7f3a9c21-4b8e-41d0-b2f6-9c0ea3d1f8ab"
    // -----------------------------------------------------------------------
    public static final String CLIENT_ID = "DEFAULT_ID";

    // Firebase Realtime Database root path for this client.
    // All reads/writes must go through ClientManager.getClientRef() instead of
    // referencing this path directly, so that the path can evolve without
    // touching every call-site.
    public static final String CLIENT_ROOT = "clients";

    // Private constructor — static-only utility class.
    private Config() {}
}
