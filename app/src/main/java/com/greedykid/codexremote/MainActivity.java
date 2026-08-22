package com.greedykid.codexremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int INK = Color.rgb(16, 20, 23);
    private static final int PANEL = Color.rgb(23, 32, 38);
    private static final int ELEVATED = Color.rgb(32, 43, 50);
    private static final int LINE = Color.rgb(53, 67, 75);
    private static final int TEXT = Color.rgb(233, 237, 240);
    private static final int MUTED = Color.rgb(154, 167, 175);
    private static final int AMBER = Color.rgb(245, 184, 75);
    private static final int CYAN = Color.rgb(96, 205, 255);
    private static final int GREEN = Color.rgb(116, 211, 155);
    private static final int RED = Color.rgb(242, 139, 130);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout transcript;
    private ScrollView scroll;
    private EditText prompt;
    private Button send;
    private TextView status;
    private Button btnAgy;
    private Button btnCodex;
    private ProgressBar progress;
    private String currentEngine = "antigravity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(INK);
        getWindow().setNavigationBarColor(INK);
        prefs = getSharedPreferences("connection", MODE_PRIVATE);
        currentEngine = prefs.getString("engine", "antigravity");
        buildUi();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable bg(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView label(String text, float size, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    private void pad(View v, int l, int t, int r, int b) {
        v.setPadding(dp(l), dp(t), dp(r), dp(b));
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(INK);
        root.setPadding(dp(18), dp(12), dp(18), dp(14));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("AI CLI REMOTE", 18, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button settings = button("Connection", false, false);
        settings.setOnClickListener(v -> showConnectionDialog());
        top.addView(settings, new LinearLayout.LayoutParams(dp(108), dp(42)));
        root.addView(top);

        LinearLayout engineBar = new LinearLayout(this);
        engineBar.setOrientation(LinearLayout.HORIZONTAL);
        engineBar.setPadding(0, dp(4), 0, dp(8));

        btnAgy = button("⚡ Antigravity", true, false);
        btnAgy.setOnClickListener(v -> setEngine("antigravity"));
        LinearLayout.LayoutParams lpAgy = new LinearLayout.LayoutParams(0, dp(40), 1);
        lpAgy.setMargins(0, 0, dp(4), 0);
        engineBar.addView(btnAgy, lpAgy);

        btnCodex = button("🚀 Codex", false, false);
        btnCodex.setOnClickListener(v -> setEngine("codex"));
        LinearLayout.LayoutParams lpCodex = new LinearLayout.LayoutParams(0, dp(40), 1);
        lpCodex.setMargins(dp(4), 0, 0, 0);
        engineBar.addView(btnCodex, lpCodex);

        root.addView(engineBar);

        LinearLayout state = new LinearLayout(this);
        state.setGravity(Gravity.CENTER_VERTICAL);
        state.setPadding(dp(12), dp(8), dp(12), dp(8));
        state.setBackground(bg(ELEVATED, 10));
        TextView dot = label("●", 12, GREEN);
        state.addView(dot, new LinearLayout.LayoutParams(dp(22), dp(28)));
        status = label("", 13, TEXT);
        state.addView(status, new LinearLayout.LayoutParams(0, dp(28), 1));
        TextView hint = label("Bridge Active", 12, MUTED);
        state.addView(hint);
        root.addView(state, new LinearLayout.LayoutParams(-1, dp(44)));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setPadding(0, dp(12), 0, dp(8));
        transcript = new LinearLayout(this);
        transcript.setOrientation(LinearLayout.VERTICAL);
        transcript.setGravity(Gravity.BOTTOM);
        TextView empty = label("Ready when you are.\nSwitch between Antigravity or Codex and send your task.", 15, MUTED);
        empty.setGravity(Gravity.CENTER);
        pad(empty, 20, 20, 20, 20);
        transcript.addView(empty, new LinearLayout.LayoutParams(-1, dp(140)));
        scroll.addView(transcript);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(0, dp(4), 0, 0);
        prompt = new EditText(this);
        prompt.setHintTextColor(MUTED);
        prompt.setTextColor(TEXT);
        prompt.setTextSize(15);
        prompt.setGravity(Gravity.TOP);
        prompt.setMinLines(1);
        prompt.setMaxLines(5);
        prompt.setSingleLine(false);
        prompt.setBackground(bg(PANEL, 12));
        pad(prompt, 14, 12, 14, 12);
        composer.addView(prompt, new LinearLayout.LayoutParams(0, dp(58), 1));

        send = button("Send", true, true);
        send.setOnClickListener(v -> sendPrompt());
        composer.addView(send, new LinearLayout.LayoutParams(dp(82), dp(58)));
        root.addView(composer);

        setContentView(root);
        updateEngineUi();
    }

    private Button button(String text, boolean primary, boolean accent) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(primary ? INK : TEXT);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(bg(primary ? (accent ? AMBER : CYAN) : PANEL, 10));
        b.setMinHeight(dp(40));
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    private void setEngine(String engine) {
        currentEngine = engine;
        prefs.edit().putString("engine", engine).apply();
        updateEngineUi();
    }

    private void updateEngineUi() {
        boolean isAgy = "antigravity".equalsIgnoreCase(currentEngine);
        btnAgy.setBackground(bg(isAgy ? CYAN : PANEL, 10));
        btnAgy.setTextColor(isAgy ? INK : TEXT);
        btnCodex.setBackground(bg(!isAgy ? AMBER : PANEL, 10));
        btnCodex.setTextColor(!isAgy ? INK : TEXT);

        String engineName = isAgy ? "Antigravity" : "Codex";
        prompt.setHint("Ask " + engineName + " to do something...");
        status.setText(connectionConfigured() ? ("Bridge ready (" + engineName + ")") : "Bridge not configured");
    }

    private boolean connectionConfigured() {
        return !prefs.getString("url", "").trim().isEmpty();
    }

    private void showConnectionDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), 0);

        EditText url = new EditText(this);
        url.setHint("https://your-bridge.example/api/chat");
        url.setText(prefs.getString("url", ""));
        url.setTextColor(TEXT);
        url.setHintTextColor(MUTED);
        form.addView(url, new LinearLayout.LayoutParams(-1, dp(58)));

        EditText token = new EditText(this);
        token.setHint("Bearer token");
        token.setText(prefs.getString("token", ""));
        token.setTextColor(TEXT);
        token.setHintTextColor(MUTED);
        token.setInputType(0x00000081);
        form.addView(token, new LinearLayout.LayoutParams(-1, dp(58)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Bridge Connection")
                .setMessage("Enter the bridge URL and secret token:")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            prefs.edit()
                    .putString("url", url.getText().toString().trim())
                    .putString("token", token.getText().toString())
                    .apply();
            updateEngineUi();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void sendPrompt() {
        String text = prompt.getText().toString().trim();
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) {
            showConnectionDialog();
            return;
        }
        if (text.isEmpty() || send.getTag() != null) return;

        final String engine = currentEngine;
        final String engineName = "antigravity".equalsIgnoreCase(engine) ? "Antigravity" : "Codex";

        send.setTag("busy");
        send.setEnabled(false);
        prompt.setEnabled(false);
        status.setText(engineName + " is working...");
        addMessage("You", text, true, null);
        prompt.setText("");

        progress = new ProgressBar(this);
        transcript.addView(progress, new LinearLayout.LayoutParams(dp(28), dp(28)));
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));

        executor.execute(() -> {
            try {
                JSONObject res = request(endpoint, prefs.getString("token", ""), text, engine);
                String responseText = res.optString("response", "No response");
                String resEngine = res.optString("engine", engine);
                main.post(() -> finishResponse(responseText, false, resEngine));
            } catch (Exception e) {
                String errMsg = e.getMessage() == null ? "Connection failed" : e.getMessage();
                main.post(() -> finishResponse(errMsg, true, engine));
            }
        });
    }

    private JSONObject request(String endpoint, String token, String promptText, String engine) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(10000);
        c.setReadTimeout(300000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        if (!token.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + token);
        }

        JSONObject req = new JSONObject();
        req.put("prompt", promptText);
        req.put("engine", engine);
        byte[] body = req.toString().getBytes(StandardCharsets.UTF_8);
        c.getOutputStream().write(body);

        int code = c.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? c.getErrorStream() : c.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            out.append(line);
        }

        if (code >= 400) {
            throw new Exception(out.toString().isEmpty() ? "Bridge returned HTTP " + code : out.toString());
        }
        return new JSONObject(out.toString());
    }

    private void finishResponse(String response, boolean error, String engine) {
        if (progress != null) {
            transcript.removeView(progress);
            progress = null;
        }
        String author;
        if (error) {
            author = "Bridge error";
        } else if ("antigravity".equalsIgnoreCase(engine)) {
            author = "Antigravity";
        } else {
            author = "Codex";
        }

        addMessage(author, response, false, engine);
        status.setText(error ? "Connection issue" : ("Bridge ready (" + ("antigravity".equalsIgnoreCase(currentEngine) ? "Antigravity" : "Codex") + ")"));
        send.setTag(null);
        send.setEnabled(true);
        prompt.setEnabled(true);
    }

    private void addMessage(String author, String message, boolean user, String engine) {
        TextView v = label(author + "\n" + message, 15, user ? TEXT : (author.equals("Bridge error") ? RED : TEXT));
        v.setLineSpacing(0, 1.15f);

        int bgColor;
        if (user) {
            bgColor = AMBER_DARK();
        } else if (author.equals("Bridge error")) {
            bgColor = PANEL;
        } else if ("antigravity".equalsIgnoreCase(engine)) {
            bgColor = CYAN_DARK();
        } else {
            bgColor = PANEL;
        }

        v.setBackground(bg(bgColor, 12));
        pad(v, 14, 12, 14, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        transcript.addView(v, lp);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private int AMBER_DARK() {
        return Color.rgb(51, 43, 26);
    }

    private int CYAN_DARK() {
        return Color.rgb(20, 48, 58);
    }
}
