package com.greedykid.codexremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    // Impeccable Design System Tokens
    private static final int BG_BASE = Color.rgb(13, 17, 23);         // #0D1117 Obsidian
    private static final int BG_CARD = Color.rgb(22, 27, 34);         // #161B22 Panel
    private static final int BG_ELEVATED = Color.rgb(33, 38, 45);     // #21262D Surface
    private static final int BORDER_LINE = Color.rgb(48, 54, 61);     // #30363D Border
    private static final int TEXT_PRIMARY = Color.rgb(240, 246, 252); // #F0F6FC
    private static final int TEXT_SECONDARY = Color.rgb(139, 148, 158); // #8B949E
    private static final int TEXT_MUTED = Color.rgb(110, 118, 129);   // #6E7681

    private static final int CYAN_PRIMARY = Color.rgb(88, 166, 255);  // #58A6FF Antigravity
    private static final int CYAN_CONTAINER = Color.rgb(18, 36, 56);  // Cyan dark
    private static final int AMBER_PRIMARY = Color.rgb(227, 179, 65); // #E3B341 Codex
    private static final int AMBER_CONTAINER = Color.rgb(39, 33, 21); // Amber dark
    private static final int GREEN_SUCCESS = Color.rgb(63, 185, 80);  // #3FB950
    private static final int RED_ERROR = Color.rgb(248, 81, 73);      // #F85149

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private LinearLayout transcript;
    private ScrollView scroll;
    private EditText promptInput;
    private Button sendButton;
    private TextView statusDot;
    private TextView statusText;
    private Button btnAgy;
    private Button btnCodex;
    private LinearLayout emptyStateView;
    private ProgressBar sendProgress;

    private String currentEngine = "antigravity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG_BASE);
        getWindow().setNavigationBarColor(BG_BASE);
        prefs = getSharedPreferences("connection", MODE_PRIVATE);
        currentEngine = prefs.getString("engine", "antigravity");
        buildUi();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable createBox(int fillColor, int borderColor, int borderWidthDp, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        if (borderWidthDp > 0 && borderColor != 0) {
            d.setStroke(dp(borderWidthDp), borderColor);
        }
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private TextView createText(String text, float sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) {
            v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return v;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG_BASE);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        // 1. Top App Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, dp(2), 0, dp(8));

        LinearLayout brandBlock = new LinearLayout(this);
        brandBlock.setOrientation(LinearLayout.VERTICAL);
        TextView title = createText("AI CLI REMOTE", 16, TEXT_PRIMARY, true);
        title.setLetterSpacing(0.04f);
        brandBlock.addView(title);
        TextView subTitle = createText("Antigravity & Codex Gateway", 11, TEXT_MUTED, false);
        brandBlock.addView(subTitle);
        topBar.addView(brandBlock, new LinearLayout.LayoutParams(0, -2, 1));

        Button clearBtn = createSmallButton("Clear", false);
        clearBtn.setOnClickListener(v -> clearTranscript());
        LinearLayout.LayoutParams lpClear = new LinearLayout.LayoutParams(dp(62), dp(36));
        lpClear.setMargins(0, 0, dp(8), 0);
        topBar.addView(clearBtn, lpClear);

        Button settingsBtn = createSmallButton("Connect", true);
        settingsBtn.setOnClickListener(v -> showConnectionDialog());
        topBar.addView(settingsBtn, new LinearLayout.LayoutParams(dp(76), dp(36)));
        root.addView(topBar);

        // 2. Engine Switcher Pills
        LinearLayout engineBar = new LinearLayout(this);
        engineBar.setOrientation(LinearLayout.HORIZONTAL);
        engineBar.setBackground(createBox(BG_CARD, BORDER_LINE, 1, 10));
        engineBar.setPadding(dp(4), dp(4), dp(4), dp(4));

        btnAgy = createEngineTab("⚡ Antigravity");
        btnAgy.setOnClickListener(v -> setEngine("antigravity"));
        LinearLayout.LayoutParams lpA = new LinearLayout.LayoutParams(0, dp(38), 1);
        lpA.setMargins(0, 0, dp(2), 0);
        engineBar.addView(btnAgy, lpA);

        btnCodex = createEngineTab("🚀 OpenAI Codex");
        btnCodex.setOnClickListener(v -> setEngine("codex"));
        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(0, dp(38), 1);
        lpC.setMargins(dp(2), 0, 0, 0);
        engineBar.addView(btnCodex, lpC);
        root.addView(engineBar);

        // 3. Status & Telemetry Bar
        LinearLayout statusBar = new LinearLayout(this);
        statusBar.setOrientation(LinearLayout.HORIZONTAL);
        statusBar.setGravity(Gravity.CENTER_VERTICAL);
        statusBar.setPadding(dp(12), dp(6), dp(12), dp(6));
        statusBar.setBackground(createBox(BG_CARD, BORDER_LINE, 1, 8));
        LinearLayout.LayoutParams lpStatus = new LinearLayout.LayoutParams(-1, -2);
        lpStatus.setMargins(0, dp(8), 0, dp(8));

        statusDot = createText("●", 12, GREEN_SUCCESS, false);
        statusBar.addView(statusDot, new LinearLayout.LayoutParams(dp(18), -2));

        statusText = createText("", 12, TEXT_SECONDARY, false);
        statusBar.addView(statusText, new LinearLayout.LayoutParams(0, -2, 1));

        TextView pingBtn = createText("Test", 11, TEXT_MUTED, true);
        pingBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
        pingBtn.setOnClickListener(v -> checkHealth());
        statusBar.addView(pingBtn);
        root.addView(statusBar, lpStatus);

        // 4. Scrollable Chat Transcript
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setPadding(0, dp(4), 0, dp(4));
        scroll.setVerticalScrollBarEnabled(false);

        transcript = new LinearLayout(this);
        transcript.setOrientation(LinearLayout.VERTICAL);
        transcript.setGravity(Gravity.BOTTOM);

        buildEmptyState();
        transcript.addView(emptyStateView);

        scroll.addView(transcript);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // 5. Suggestion Chips Bar
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setPadding(0, dp(4), 0, dp(6));
        LinearLayout chipContainer = new LinearLayout(this);
        chipContainer.setOrientation(LinearLayout.HORIZONTAL);

        addChip(chipContainer, "📁 List files", "List all project files in current directory");
        addChip(chipContainer, "🔍 Git status", "Check git branch status and recent commits");
        addChip(chipContainer, "💡 Explain code", "Summarize what this repository does");
        addChip(chipContainer, "🧪 Run tests", "Run test suites and report any errors");
        chipScroll.addView(chipContainer);
        root.addView(chipScroll);

        // 6. Composer (Input + Send Button)
        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(0, dp(2), 0, 0);

        promptInput = new EditText(this);
        promptInput.setHintTextColor(TEXT_MUTED);
        promptInput.setTextColor(TEXT_PRIMARY);
        promptInput.setTextSize(14.5f);
        promptInput.setGravity(Gravity.TOP | Gravity.START);
        promptInput.setMinLines(1);
        promptInput.setMaxLines(4);
        promptInput.setSingleLine(false);
        promptInput.setBackground(createBox(BG_CARD, BORDER_LINE, 1, 12));
        promptInput.setPadding(dp(14), dp(10), dp(14), dp(10));
        composer.addView(promptInput, new LinearLayout.LayoutParams(0, dp(54), 1));

        sendButton = new Button(this);
        sendButton.setText("Send ➔");
        sendButton.setTextSize(13);
        sendButton.setAllCaps(false);
        sendButton.setTextColor(BG_BASE);
        sendButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sendButton.setBackground(createBox(CYAN_PRIMARY, 0, 0, 10));
        sendButton.setOnClickListener(v -> sendPrompt());
        LinearLayout.LayoutParams lpSend = new LinearLayout.LayoutParams(dp(86), dp(54));
        lpSend.setMargins(dp(8), 0, 0, 0);
        composer.addView(sendButton, lpSend);

        root.addView(composer);

        setContentView(root);
        updateEngineUi();
    }

    private Button createSmallButton(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTextColor(primary ? BG_BASE : TEXT_SECONDARY);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(createBox(primary ? CYAN_PRIMARY : BG_CARD, primary ? 0 : BORDER_LINE, 1, 8));
        b.setMinHeight(dp(36));
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    private Button createEngineTab(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(12.5f);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(36));
        b.setPadding(dp(4), 0, dp(4), 0);
        return b;
    }

    private void addChip(LinearLayout container, String label, String fullPrompt) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setTextColor(TEXT_SECONDARY);
        chip.setBackground(createBox(BG_CARD, BORDER_LINE, 1, 14));
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        chip.setOnClickListener(v -> {
            promptInput.setText(fullPrompt);
            promptInput.setSelection(fullPrompt.length());
            promptInput.requestFocus();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(8), 0);
        container.addView(chip, lp);
    }

    private void buildEmptyState() {
        emptyStateView = new LinearLayout(this);
        emptyStateView.setOrientation(LinearLayout.VERTICAL);
        emptyStateView.setGravity(Gravity.CENTER);
        emptyStateView.setPadding(dp(24), dp(40), dp(24), dp(40));

        TextView icon = createText("⌘", 32, CYAN_PRIMARY, true);
        icon.setGravity(Gravity.CENTER);
        emptyStateView.addView(icon);

        TextView title = createText("Workspace Ready", 16, TEXT_PRIMARY, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams(-1, -2);
        lpT.setMargins(0, dp(10), 0, dp(4));
        emptyStateView.addView(title, lpT);

        TextView desc = createText("Send tasks or code queries to your remote CLI agent. Tap suggestions below or type a prompt.", 13, TEXT_SECONDARY, false);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(0, 1.15f);
        emptyStateView.addView(desc);
    }

    private void setEngine(String engine) {
        currentEngine = engine;
        prefs.edit().putString("engine", engine).apply();
        updateEngineUi();
    }

    private void updateEngineUi() {
        boolean isAgy = "antigravity".equalsIgnoreCase(currentEngine);
        int activeAccent = isAgy ? CYAN_PRIMARY : AMBER_PRIMARY;

        btnAgy.setBackground(createBox(isAgy ? CYAN_PRIMARY : Color.TRANSPARENT, 0, 0, 8));
        btnAgy.setTextColor(isAgy ? BG_BASE : TEXT_SECONDARY);

        btnCodex.setBackground(createBox(!isAgy ? AMBER_PRIMARY : Color.TRANSPARENT, 0, 0, 8));
        btnCodex.setTextColor(!isAgy ? BG_BASE : TEXT_SECONDARY);

        sendButton.setBackground(createBox(activeAccent, 0, 0, 10));

        String engineName = isAgy ? "Antigravity" : "Codex";
        promptInput.setHint("Ask " + engineName + " CLI to do something...");
        boolean configured = isConfigured();
        statusDot.setTextColor(configured ? GREEN_SUCCESS : RED_ERROR);
        statusText.setText(configured ? ("Ready (" + engineName + ")") : "Bridge not configured");
    }

    private boolean isConfigured() {
        return !prefs.getString("url", "").trim().isEmpty();
    }

    private void clearTranscript() {
        transcript.removeAllViews();
        transcript.addView(emptyStateView);
        Toast.makeText(this, "Transcript cleared", Toast.LENGTH_SHORT).show();
    }

    private void checkHealth() {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) {
            showConnectionDialog();
            return;
        }
        statusText.setText("Checking connection...");
        executor.execute(() -> {
            try {
                String healthUrl = endpoint.replace("/api/chat", "/health");
                HttpURLConnection c = (HttpURLConnection) new URL(healthUrl).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                int code = c.getResponseCode();
                mainHandler.post(() -> {
                    if (code == 200) {
                        statusDot.setTextColor(GREEN_SUCCESS);
                        statusText.setText("Bridge online (" + ("antigravity".equalsIgnoreCase(currentEngine) ? "Antigravity" : "Codex") + ")");
                        Toast.makeText(this, "Bridge is online and responsive!", Toast.LENGTH_SHORT).show();
                    } else {
                        statusDot.setTextColor(RED_ERROR);
                        statusText.setText("HTTP " + code);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    statusDot.setTextColor(RED_ERROR);
                    statusText.setText("Offline: " + e.getMessage());
                });
            }
        });
    }

    private void showConnectionDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(8), dp(22), dp(8));

        TextView urlLbl = createText("Bridge Endpoint URL:", 13, TEXT_SECONDARY, true);
        form.addView(urlLbl);

        EditText urlInput = new EditText(this);
        urlInput.setHint("https://your-bridge.trycloudflare.com/api/chat");
        urlInput.setText(prefs.getString("url", ""));
        urlInput.setTextColor(TEXT_PRIMARY);
        urlInput.setHintTextColor(TEXT_MUTED);
        urlInput.setTextSize(14);
        urlInput.setBackground(createBox(BG_BASE, BORDER_LINE, 1, 8));
        urlInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpUrl = new LinearLayout.LayoutParams(-1, dp(48));
        lpUrl.setMargins(0, dp(4), 0, dp(14));
        form.addView(urlInput, lpUrl);

        TextView tokLbl = createText("Bearer Token (Secret):", 13, TEXT_SECONDARY, true);
        form.addView(tokLbl);

        EditText tokenInput = new EditText(this);
        tokenInput.setHint("codex-remote-token-2026");
        tokenInput.setText(prefs.getString("token", ""));
        tokenInput.setTextColor(TEXT_PRIMARY);
        tokenInput.setHintTextColor(TEXT_MUTED);
        tokenInput.setTextSize(14);
        tokenInput.setInputType(0x00000081);
        tokenInput.setBackground(createBox(BG_BASE, BORDER_LINE, 1, 8));
        tokenInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpTok = new LinearLayout.LayoutParams(-1, dp(48));
        lpTok.setMargins(0, dp(4), 0, dp(8));
        form.addView(tokenInput, lpTok);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Remote Gateway Setup")
                .setMessage("Connect via Cloudflare Tunnel or Tailscale:")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save & Connect", null)
                .create();

        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String u = urlInput.getText().toString().trim();
            String t = tokenInput.getText().toString().trim();
            prefs.edit().putString("url", u).putString("token", t).apply();
            updateEngineUi();
            dialog.dismiss();
            checkHealth();
        }));
        dialog.show();
    }

    private void sendPrompt() {
        String text = promptInput.getText().toString().trim();
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) {
            showConnectionDialog();
            return;
        }
        if (text.isEmpty() || sendButton.getTag() != null) return;

        final String engine = currentEngine;
        final boolean isAgy = "antigravity".equalsIgnoreCase(engine);
        final String engineLabel = isAgy ? "Antigravity" : "Codex";

        if (emptyStateView.getParent() != null) {
            transcript.removeView(emptyStateView);
        }

        sendButton.setTag("busy");
        sendButton.setEnabled(false);
        promptInput.setEnabled(false);
        statusDot.setTextColor(isAgy ? CYAN_PRIMARY : AMBER_PRIMARY);
        statusText.setText(engineLabel + " is processing...");

        addMessage("You", text, true, null);
        promptInput.setText("");

        sendProgress = new ProgressBar(this);
        LinearLayout.LayoutParams lpProg = new LinearLayout.LayoutParams(dp(24), dp(24));
        lpProg.setMargins(dp(14), dp(4), 0, dp(10));
        transcript.addView(sendProgress, lpProg);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));

        executor.execute(() -> {
            try {
                JSONObject res = request(endpoint, prefs.getString("token", ""), text, engine);
                String responseText = res.optString("response", "No output returned.");
                String resEngine = res.optString("engine", engine);
                mainHandler.post(() -> finishResponse(responseText, false, resEngine));
            } catch (Exception e) {
                String errMsg = e.getMessage() == null ? "Bridge communication error" : e.getMessage();
                mainHandler.post(() -> finishResponse(errMsg, true, engine));
            }
        });
    }

    private JSONObject request(String endpoint, String token, String promptText, String engine) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
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
            out.append(line).append("\n");
        }

        if (code >= 400) {
            throw new Exception(out.toString().isEmpty() ? "HTTP " + code : out.toString().trim());
        }
        return new JSONObject(out.toString());
    }

    private void finishResponse(String response, boolean error, String engine) {
        if (sendProgress != null) {
            transcript.removeView(sendProgress);
            sendProgress = null;
        }

        String author;
        if (error) {
            author = "Bridge Error";
        } else if ("antigravity".equalsIgnoreCase(engine)) {
            author = "⚡ Antigravity CLI";
        } else {
            author = "🚀 Codex CLI";
        }

        addMessage(author, response, false, engine);
        statusDot.setTextColor(error ? RED_ERROR : GREEN_SUCCESS);
        statusText.setText(error ? "Error occurred" : ("Ready (" + ("antigravity".equalsIgnoreCase(currentEngine) ? "Antigravity" : "Codex") + ")"));

        sendButton.setTag(null);
        sendButton.setEnabled(true);
        promptInput.setEnabled(true);
    }

    private void addMessage(String author, String message, boolean isUser, String engine) {
        LinearLayout bubbleCard = new LinearLayout(this);
        bubbleCard.setOrientation(LinearLayout.VERTICAL);

        int bgColor;
        int borderColor;
        if (isUser) {
            bgColor = AMBER_CONTAINER;
            borderColor = AMBER_PRIMARY;
        } else if (author.contains("Error")) {
            bgColor = BG_CARD;
            borderColor = RED_ERROR;
        } else if ("antigravity".equalsIgnoreCase(engine)) {
            bgColor = CYAN_CONTAINER;
            borderColor = CYAN_PRIMARY;
        } else {
            bgColor = BG_CARD;
            borderColor = BORDER_LINE;
        }

        bubbleCard.setBackground(createBox(bgColor, borderColor, 1, 12));
        bubbleCard.setPadding(dp(14), dp(10), dp(14), dp(12));

        // Header Row: Author + Time + Copy Button
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        int headerColor = isUser ? AMBER_PRIMARY : (author.contains("Error") ? RED_ERROR : ("antigravity".equalsIgnoreCase(engine) ? CYAN_PRIMARY : AMBER_PRIMARY));
        TextView authorView = createText(author, 12.5f, headerColor, true);
        headerRow.addView(authorView, new LinearLayout.LayoutParams(0, -2, 1));

        String timeStr = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        TextView timeView = createText(timeStr, 11, TEXT_MUTED, false);
        headerRow.addView(timeView);

        bubbleCard.addView(headerRow);

        // Message Content Body
        TextView bodyView = new TextView(this);
        bodyView.setText(message);
        bodyView.setTextSize(14.5f);
        bodyView.setTextColor(author.contains("Error") ? RED_ERROR : TEXT_PRIMARY);
        bodyView.setLineSpacing(0, 1.2f);
        bodyView.setTextIsSelectable(true);
        bodyView.setTypeface(message.contains("\n") && (message.contains("    ") || message.contains("\t") || message.contains("{")) ? Typeface.MONOSPACE : Typeface.DEFAULT);
        bodyView.setPadding(0, dp(6), 0, 0);

        // Long press to copy full message
        bodyView.setOnLongClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData cd = ClipData.newPlainText("CLI Response", message);
            cm.setPrimaryClip(cd);
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            return true;
        });

        bubbleCard.addView(bodyView);

        LinearLayout.LayoutParams lpBubble = new LinearLayout.LayoutParams(-1, -2);
        lpBubble.setMargins(0, 0, 0, dp(12));
        transcript.addView(bubbleCard, lpBubble);

        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
}

