package com.greedykid.codexremote;

import android.content.Context;
import android.util.LruCache;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Persists the last transcript per conversation in persistent storage so it survives connectivity loss and app restarts. */
final class TranscriptCache {
    private final File dir;
    private final LruCache<String, JSONObject> memoryCache = new LruCache<>(30);

    TranscriptCache(Context context) {
        this.dir = new File(context.getFilesDir(), "transcripts_store");
        if (!dir.exists()) dir.mkdirs();
    }

    void put(String conversationId, JSONObject json) {
        if (conversationId == null || conversationId.isEmpty() || json == null) return;
        memoryCache.put(conversationId, json);
        try (FileOutputStream out = new FileOutputStream(fileFor(conversationId))) {
            out.write(json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    JSONObject get(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) return null;
        JSONObject mem = memoryCache.get(conversationId);
        if (mem != null) return mem;

        File file = fileFor(conversationId);
        if (!file.exists()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int read = in.read(buffer);
            if (read <= 0) return null;
            JSONObject disk = new JSONObject(new String(buffer, 0, read, StandardCharsets.UTF_8));
            memoryCache.put(conversationId, disk);
            return disk;
        } catch (Exception e) {
            return null;
        }
    }

    private File fileFor(String conversationId) {
        return new File(dir, conversationId.replaceAll("[^A-Za-z0-9_-]", "_") + ".json");
    }
}
