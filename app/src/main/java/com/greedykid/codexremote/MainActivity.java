package com.greedykid.codexremote;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.app.AlertDialog;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
    private ProgressBar progress;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(INK);
        getWindow().setNavigationBarColor(INK);
        prefs = getSharedPreferences("connection", MODE_PRIVATE);
        buildUi();
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable bg(int color, float radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView label(String text, float size, int color) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private void pad(View v, int l, int t, int r, int b) { v.setPadding(dp(l), dp(t), dp(r), dp(b)); }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(INK);
        root.setPadding(dp(18), dp(12), dp(18), dp(14));

        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("CODEX REMOTE", 18, TEXT); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button settings = button("Connection", false); settings.setOnClickListener(v -> showConnectionDialog());
        top.addView(settings, new LinearLayout.LayoutParams(dp(112), dp(48)));
        root.addView(top);

        LinearLayout state = new LinearLayout(this); state.setGravity(Gravity.CENTER_VERTICAL); state.setPadding(dp(12), dp(8), dp(12), dp(8)); state.setBackground(bg(ELEVATED, 10));
        TextView dot = label("●", 12, GREEN); state.addView(dot, new LinearLayout.LayoutParams(dp(22), dp(28)));
        status = label(connectionConfigured() ? "Bridge ready" : "Bridge not configured", 13, TEXT);
        state.addView(status, new LinearLayout.LayoutParams(0, dp(28), 1));
        TextView hint = label("Private connection", 12, MUTED); state.addView(hint);
        root.addView(state, new LinearLayout.LayoutParams(-1, dp(44)));

        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setPadding(0, dp(16), 0, dp(12));
        transcript = new LinearLayout(this); transcript.setOrientation(LinearLayout.VERTICAL); transcript.setGravity(Gravity.BOTTOM);
        TextView empty = label("Ready when you are.\nSend a task to the Codex workspace.", 16, MUTED); empty.setGravity(Gravity.CENTER); pad(empty, 24, 24, 24, 24);
        transcript.addView(empty, new LinearLayout.LayoutParams(-1, dp(160)));
        scroll.addView(transcript); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer = new LinearLayout(this); composer.setGravity(Gravity.BOTTOM); composer.setPadding(0, dp(4), 0, 0);
        prompt = new EditText(this); prompt.setHint("Ask Codex to do something..."); prompt.setHintTextColor(MUTED); prompt.setTextColor(TEXT); prompt.setTextSize(15); prompt.setGravity(Gravity.TOP); prompt.setMinLines(1); prompt.setMaxLines(5); prompt.setSingleLine(false); prompt.setBackground(bg(PANEL, 12)); pad(prompt, 14, 12, 14, 12);
        composer.addView(prompt, new LinearLayout.LayoutParams(0, dp(58), 1));
        send = button("Send", true); send.setOnClickListener(v -> sendPrompt()); composer.addView(send, new LinearLayout.LayoutParams(dp(82), dp(58)));
        root.addView(composer);
        setContentView(root);
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(this); b.setText(text); b.setTextSize(13); b.setAllCaps(false); b.setTextColor(primary ? INK : TEXT); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(bg(primary ? AMBER : PANEL, 10)); b.setMinHeight(dp(48)); b.setPadding(dp(8), 0, dp(8), 0); return b;
    }

    private boolean connectionConfigured() { return !prefs.getString("url", "").trim().isEmpty(); }

    private void showConnectionDialog() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20), dp(4), dp(20), 0);
        EditText url = new EditText(this); url.setHint("https://your-bridge.example/api/chat"); url.setText(prefs.getString("url", "")); url.setTextColor(TEXT); url.setHintTextColor(MUTED); form.addView(url, new LinearLayout.LayoutParams(-1, dp(58)));
        EditText token = new EditText(this); token.setHint("Bearer token"); token.setText(prefs.getString("token", "")); token.setTextColor(TEXT); token.setHintTextColor(MUTED); token.setInputType(0x00000081); form.addView(token, new LinearLayout.LayoutParams(-1, dp(58)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Bridge connection").setMessage("Use a private Tailscale or VPN address.").setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> { prefs.edit().putString("url", url.getText().toString().trim()).putString("token", token.getText().toString()).apply(); status.setText(connectionConfigured() ? "Bridge ready" : "Bridge not configured"); dialog.dismiss(); }));
        dialog.show();
    }

    private void sendPrompt() {
        String text = prompt.getText().toString().trim();
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) { showConnectionDialog(); return; }
        if (text.isEmpty() || send.getTag() != null) return;
        send.setTag("busy"); send.setEnabled(false); prompt.setEnabled(false); status.setText("Codex is working");
        addMessage("You", text, true); prompt.setText("");
        progress = new ProgressBar(this); transcript.addView(progress, new LinearLayout.LayoutParams(dp(28), dp(28))); scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        executor.execute(() -> {
            try { String response = request(endpoint, prefs.getString("token", ""), text); main.post(() -> finishResponse(response, false)); }
            catch (Exception e) { main.post(() -> finishResponse(e.getMessage() == null ? "Connection failed" : e.getMessage(), true)); }
        });
    }

    private String request(String endpoint, String token, String promptText) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection(); c.setRequestMethod("POST"); c.setConnectTimeout(10000); c.setReadTimeout(300000); c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
        if (!token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        byte[] body = new JSONObject().put("prompt", promptText).toString().getBytes(StandardCharsets.UTF_8); c.getOutputStream().write(body);
        int code = c.getResponseCode(); BufferedReader reader = new BufferedReader(new InputStreamReader(code >= 400 ? c.getErrorStream() : c.getInputStream(), StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); String line; while ((line = reader.readLine()) != null) out.append(line);
        if (code >= 400) throw new Exception(out.toString().isEmpty() ? "Bridge returned HTTP " + code : out.toString());
        JSONObject json = new JSONObject(out.toString()); return json.optString("response", out.toString());
    }

    private void finishResponse(String response, boolean error) { if (progress != null) { transcript.removeView(progress); progress = null; } addMessage(error ? "Bridge error" : "Codex", response, false); status.setText(error ? "Connection issue" : "Bridge ready"); send.setTag(null); send.setEnabled(true); prompt.setEnabled(true); }
    private void addMessage(String author, String message, boolean user) { TextView v = label(author + "\n" + message, 15, user ? TEXT : (author.equals("Bridge error") ? RED : TEXT)); v.setLineSpacing(0, 1.12f); v.setBackground(bg(user ? AMBER_DARK() : PANEL, 12)); pad(v, 14, 12, 14, 12); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, 0, 0, dp(10)); transcript.addView(v, lp); scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN)); }
    private int AMBER_DARK() { return Color.rgb(51, 43, 26); }
}
