package com.greedykid.codexremote;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

final class PromptLibrary {
    private final SharedPreferences prefs;

    PromptLibrary(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    JSONArray all() {
        try {
            return new JSONArray(prefs.getString("prompt_library", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    void add(String title, String prompt) {
        if (title == null || prompt == null || title.trim().isEmpty() || prompt.trim().isEmpty()) return;
        try {
            JSONArray prompts = all();
            JSONObject item = new JSONObject();
            item.put("id", Long.toString(System.currentTimeMillis(), 36));
            item.put("title", title.trim());
            item.put("prompt", prompt.trim());
            JSONArray next = new JSONArray();
            next.put(item);
            for (int index = 0; index < prompts.length() && index < 49; index++) next.put(prompts.get(index));
            prefs.edit().putString("prompt_library", next.toString()).apply();
        } catch (Exception ignored) {}
    }

    void delete(String id) {
        try {
            JSONArray prompts = all();
            JSONArray next = new JSONArray();
            for (int index = 0; index < prompts.length(); index++) {
                JSONObject item = prompts.getJSONObject(index);
                if (!id.equals(item.optString("id"))) next.put(item);
            }
            prefs.edit().putString("prompt_library", next.toString()).apply();
        } catch (Exception ignored) {}
    }
}
