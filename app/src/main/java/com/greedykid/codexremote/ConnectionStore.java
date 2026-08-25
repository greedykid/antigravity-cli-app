package com.greedykid.codexremote;

import android.content.SharedPreferences;

final class ConnectionStore {
    private final SharedPreferences prefs;

    ConnectionStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    String endpoint() {
        return prefs.getString("endpoint", "");
    }

    String token() {
        return prefs.getString("token", "");
    }

    String engine() {
        return prefs.getString("engine", "antigravity");
    }

    boolean isPaired() {
        return !endpoint().trim().isEmpty() && !token().trim().isEmpty();
    }

    void save(String endpoint, String token, String engine) {
        prefs.edit()
                .putString("endpoint", endpoint.trim())
                .putString("token", token.trim())
                .putString("engine", engine)
                .apply();
    }
}
