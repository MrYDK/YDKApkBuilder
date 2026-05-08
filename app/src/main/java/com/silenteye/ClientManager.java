package com.silenteye;

import android.content.Context;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * ClientManager — single source of truth for all Firebase path construction.
 *
 * Every Activity, Service, BroadcastReceiver, and background task must obtain
 * its Firebase reference through this class so that:
 *
 *  1. All data is namespaced under  clients/{CLIENT_ID}/
 *  2. If the path structure ever changes, only this file needs updating.
 *  3. The CLIENT_ID from Config.java (injected by the backend at build time)
 *     is always used — no runtime lookup, no SharedPreferences dependency.
 *
 * Usage examples
 * ──────────────
 *  // Root reference for this client:
 *  DatabaseReference root = ClientManager.getClientRef();
 *
 *  // Scoped child reference:
 *  DatabaseReference msgRef = ClientManager.getChildRef("messages");
 *
 *  // Reading the CLIENT_ID string directly (e.g. for logging):
 *  String id = ClientManager.getClientId();
 */
public final class ClientManager {

    private static final String TAG = "ClientManager";

    /** Cached root reference — created once, reused across the app. */
    private static DatabaseReference clientRootRef;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns the baked-in CLIENT_ID constant from {@link Config}.
     * This value is replaced by the backend before each APK build.
     */
    public static String getClientId() {
        return Config.CLIENT_ID;
    }

    /**
     * Returns the Firebase Realtime Database reference rooted at
     * {@code clients/{CLIENT_ID}}.
     *
     * Thread-safe lazy initialisation — safe to call from any thread.
     */
    public static synchronized DatabaseReference getClientRef() {
        if (clientRootRef == null) {
            validateClientId();
            clientRootRef = FirebaseDatabase.getInstance()
                    .getReference(Config.CLIENT_ROOT)   // "clients"
                    .child(Config.CLIENT_ID);           // "{CLIENT_ID}"
            Log.d(TAG, "Firebase root initialised at: "
                    + Config.CLIENT_ROOT + "/" + Config.CLIENT_ID);
        }
        return clientRootRef;
    }

    /**
     * Convenience method — returns {@code clients/{CLIENT_ID}/{child}}.
     *
     * @param child The child node name (e.g. "messages", "locations").
     */
    public static DatabaseReference getChildRef(String child) {
        return getClientRef().child(child);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Warns loudly (but does NOT crash) if the APK was built without the
     * backend replacing the placeholder. Useful during development.
     */
    private static void validateClientId() {
        if ("DEFAULT_ID".equals(Config.CLIENT_ID) || Config.CLIENT_ID.isEmpty()) {
            Log.w(TAG, "⚠ CLIENT_ID is still the default placeholder. "
                    + "Ensure the backend has replaced it before distributing this APK.");
        }
    }

    /** Static-only utility — never instantiated. */
    private ClientManager() {}
}
