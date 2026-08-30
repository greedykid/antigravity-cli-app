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
        JSONObject prev = memoryCache.get(conversationId);
        boolean changed = prev == null || !sameContent(prev, json);
        memoryCache.put(conversationId, json);
        // Polling syncs every few seconds with identical data; skip the disk
        // write (whole-transcript stringify) unless the transcript actually
        // changed. Reads fall back to this file when offline.
        if (!changed) return;
        try (FileOutputStream out = new FileOutputStream(fileFor(conversationId))) {
            out.write(json.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    /** Cheap content comparison: turn count + last turn's content hash. */
    private boolean sameContent(JSONObject a, JSONObject b) {
        try {
            if (a.optString("conversationId", "").equals(b.optString("conversationId", "")) == false) return false;
            org.json.JSONArray ta = a.optJSONArray("turns");
            org.json.JSONArray tb = b.optJSONArray("turns");
            if (ta == null || tb == null) return false;
            if (ta.length() != tb.length()) return false;
            if (ta.length() == 0) return true;
            String lastA = ta.optJSONObject(ta.length() - 1).toString();
            String lastB = tb.optJSONObject(tb.length() - 1).toString();
            return lastA.equals(lastB);
        } catch (Exception e) {
            return false;
        }
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
