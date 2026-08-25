package com.greedykid.codexremote;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Persists the last transcript per conversation so it survives connectivity loss. */
final class TranscriptCache {
    private final File dir;

    TranscriptCache(Context context) {
        this.dir = new File(context.getCacheDir(), "transcripts");
        if (!dir.exists()) dir.mkdirs();
    }

    void put(String conversationId, JSONObject json) {
        if (conversationId == null || conversationId.isEmpty() || json == null) return;
        try (FileOutputStream out = new FileOutputStream(fileFor(conversationId))) {
            out.write(json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    JSONObject get(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) return null;
        File file = fileFor(conversationId);
        if (!file.exists()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int read = in.read(buffer);
            if (read <= 0) return null;
            return new JSONObject(new String(buffer, 0, read, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private File fileFor(String conversationId) {
        return new File(dir, conversationId.replaceAll("[^A-Za-z0-9_-]", "_") + ".json");
    }
}
