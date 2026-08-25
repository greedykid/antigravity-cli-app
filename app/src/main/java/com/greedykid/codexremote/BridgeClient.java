package com.greedykid.codexremote;

import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Thin HTTP layer over the bridge server. The paired endpoint is stored as the
 * /api/chat URL, so every other route is derived from it in one place instead
 * of being string-replaced at each call site.
 */
public class BridgeClient {

    private final SharedPreferences prefs;

    public BridgeClient(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public String endpoint() {
        return prefs.getString("url", "").trim();
    }

    public String token() {
        return prefs.getString("token", "");
    }

    public boolean isPaired() {
        return !endpoint().isEmpty();
    }

    /** Turns the stored /api/chat endpoint into any other bridge route. */
    public String url(String apiPath) {
        String base = endpoint();
        if (base.isEmpty()) return "";
        int at = base.indexOf("/api/");
        String root = at > 0 ? base.substring(0, at) : base;
        if (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        return root + (apiPath.startsWith("/") ? apiPath : "/" + apiPath);
    }

    public static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private void applyAuth(HttpURLConnection c) {
        String token = token();
        if (!token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        String deviceId = prefs.getString("device_id", "");
        if (!deviceId.isEmpty()) {
            c.setRequestProperty("X-Codex-Device-Id", deviceId);
            c.setRequestProperty("X-Codex-Device-Name", prefs.getString("device_name", "Android"));
        }
    }

    public HttpURLConnection open(String apiPath, String method, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url(apiPath)).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        applyAuth(c);
        return c;
    }

    public JSONObject get(String apiPath) throws Exception {
        return get(apiPath, 15000);
    }

    public JSONObject get(String apiPath, int timeoutMs) throws Exception {
        HttpURLConnection c = open(apiPath, "GET", timeoutMs);
        try {
            return readJson(c);
        } finally {
            c.disconnect();
        }
    }

    public JSONObject post(String apiPath, JSONObject body) throws Exception {
        return post(apiPath, body, 30000);
    }

    public JSONObject post(String apiPath, JSONObject body, int timeoutMs) throws Exception {
        HttpURLConnection c = open(apiPath, "POST", timeoutMs);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        try {
            byte[] payload = (body == null ? "{}" : body.toString()).getBytes(StandardCharsets.UTF_8);
            OutputStream os = c.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();
            return readJson(c);
        } finally {
            c.disconnect();
        }
    }

    private JSONObject readJson(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream stream = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (stream == null) return new JSONObject().put("ok", false).put("status", code);

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        String raw = sb.toString().trim();
        JSONObject json = raw.startsWith("{") ? new JSONObject(raw) : new JSONObject();
        json.put("code", code);
        json.put("status", code);
        if (!json.has("ok")) json.put("ok", code < 400);
        return json;
    }
}
