package com.greedykid.codexremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.speech.RecognizerIntent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    // Claude Warm Ivory & Terracotta Color Palette
    private static final int CLAUDE_BG = Color.rgb(251, 251, 249);             // #FBFBF9 Warm Ivory
    private static final int CLAUDE_SURFACE = Color.rgb(255, 255, 255);        // #FFFFFF Pure White
    private static final int CLAUDE_SURFACE_MUTED = Color.rgb(243, 243, 240);  // #F3F3F0 Light Warm Grey
    private static final int CLAUDE_BORDER = Color.rgb(235, 234, 229);         // #EBEAE5 Subtle Border
    private static final int CLAUDE_BORDER_DARK = Color.rgb(218, 216, 209);    // #DAD8D1
    private static final int CLAUDE_CODE_BG = Color.rgb(24, 25, 28);           // #18191C Dark Code Box

    private static final int CLAUDE_TEXT_MAIN = Color.rgb(26, 25, 24);         // #1A1918 Deep Charcoal
    private static final int CLAUDE_TEXT_MUTED = Color.rgb(112, 111, 108);     // #706F6C Slate Grey
    private static final int CLAUDE_TEXT_LIGHT = Color.rgb(150, 149, 145);     // #969591 Light Slate

    private static final int CLAUDE_TERRACOTTA = Color.rgb(217, 107, 67);      // #D96B43 Claude Terracotta Orange
    private static final int CLAUDE_TERRACOTTA_LIGHT = Color.rgb(250, 235, 229); // #FAECE5 Light Peach
    private static final int CLAUDE_GREEN = Color.rgb(46, 125, 50);            // #2E7D32 Emerald Green
    private static final int CLAUDE_GREEN_BG = Color.rgb(234, 247, 237);       // #EAF7ED Light Mint
    private static final int CLAUDE_RED = Color.rgb(198, 40, 40);              // #C62828 Ruby Red

    private static final int REQ_PICK_FILE = 1001;
    private static final int REQ_VOICE_SPEECH = 1002;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    // View Containers
    private LinearLayout viewHubContainer;
    private LinearLayout viewChatContainer;

    // Hub View (Screen 1) Components
    private LinearLayout hubReadyList;
    private LinearLayout hubActiveList;
    private LinearLayout hubIdleList;
    private ProgressBar hubLoadingProgress;

    // Chat View (Screen 2) Components
    private TextView chatTopTitle;
    private LinearLayout chatMessagesList;
    private ScrollView chatScroll;
    private LinearLayout emptyMascotView;
    private EditText promptInput;
    private Button btnSend;
    private Button btnAttach;
    private Button btnEnginePill;
    private Button btnVoice;
    private TextView repoTagLabel;
    private LinearLayout attachmentChip;
    private TextView attachmentText;
    private ProgressBar chatSendProgress;

    // Active Session State
    private String activeConversationId = null;
    private String activeSessionTitle = "New session";
    private String currentEngine = "antigravity";
    private String attachedServerPath = null;
    private int currentScreen = 0; // 0: Hub ("Code"), 1: Session Chat ("New session")

    private boolean isAutoRefreshActive = false;
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAutoRefreshActive && currentScreen == 1) {
                fetchActiveSessionTurns(false);
                mainHandler.postDelayed(this, 3000);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(CLAUDE_BG);
        getWindow().setNavigationBarColor(CLAUDE_BG);
        prefs = getSharedPreferences("connection", MODE_PRIVATE);
        currentEngine = prefs.getString("engine", "antigravity");
        buildClaudeUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentScreen == 0) {
            fetchHubSessions();
        } else if (isAutoRefreshActive) {
            startAutoRefresh();
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable cBox(int fillColor, int borderColor, int borderWidthDp, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        if (borderWidthDp > 0 && borderColor != 0) {
            d.setStroke(dp(borderWidthDp), borderColor);
        }
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private TextView cText(String text, float sp, int color, boolean bold, boolean isSerif) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (isSerif) {
            v.setTypeface(Typeface.SERIF, bold ? Typeface.BOLD : Typeface.NORMAL);
        } else {
            v.setTypeface(Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL);
        }
        return v;
    }

    private void buildClaudeUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(CLAUDE_BG);

        // Screen 1: "Code" Hub (Session Browser)
        viewHubContainer = new LinearLayout(this);
        viewHubContainer.setOrientation(LinearLayout.VERTICAL);
        buildHubScreen(viewHubContainer);
        root.addView(viewHubContainer, new LinearLayout.LayoutParams(-1, -1));

        // Screen 2: "New session" / Chat View
        viewChatContainer = new LinearLayout(this);
        viewChatContainer.setOrientation(LinearLayout.VERTICAL);
        viewChatContainer.setVisibility(View.GONE);
        buildChatScreen(viewChatContainer);
        root.addView(viewChatContainer, new LinearLayout.LayoutParams(-1, -1));

        setContentView(root);
        showScreen(0);
        fetchHubSessions();
    }

    private void showScreen(int screenIndex) {
        currentScreen = screenIndex;
        viewHubContainer.setVisibility(screenIndex == 0 ? View.VISIBLE : View.GONE);
        viewChatContainer.setVisibility(screenIndex == 1 ? View.VISIBLE : View.GONE);

        if (screenIndex == 0) {
            stopAutoRefresh();
            fetchHubSessions();
        } else {
            chatTopTitle.setText(activeSessionTitle);
            updateRepoTag();
            if (activeConversationId != null) {
                fetchActiveSessionTurns(true);
                startAutoRefresh();
            } else {
                showEmptyMascotState(true);
            }
        }
    }

    // ============================================================
    // SCREEN 1: "Code" SESSIONS HUB (Exact Claude Code Style)
    // ============================================================
    private void buildHubScreen(LinearLayout parent) {
        parent.setPadding(dp(20), dp(16), dp(20), dp(16));

        // Top Navigation Bar (Menu and Settings)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, dp(4), 0, dp(14));

        TextView menuIcon = cText("☰", 22, CLAUDE_TEXT_MAIN, false, false);
        menuIcon.setOnClickListener(v -> showConnectionDialog());
        topBar.addView(menuIcon, new LinearLayout.LayoutParams(0, -2, 1));

        TextView newBtnTop = cText("＋ New", 14, CLAUDE_TERRACOTTA, true, false);
        newBtnTop.setBackground(cBox(CLAUDE_TERRACOTTA_LIGHT, 0, 0, 16));
        newBtnTop.setPadding(dp(12), dp(6), dp(12), dp(6));
        newBtnTop.setOnClickListener(v -> startNewSession());
        topBar.addView(newBtnTop);
        parent.addView(topBar);

        // Header Title: "Code" in Iconic Serif Font
        TextView headerTitle = cText("Code", 36, CLAUDE_TEXT_MAIN, true, true);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(-1, -2);
        lpTitle.setMargins(0, dp(4), 0, dp(20));
        parent.addView(headerTitle, lpTitle);

        // Scrollable Session Categories
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        hubLoadingProgress = new ProgressBar(this);
        content.addView(hubLoadingProgress, new LinearLayout.LayoutParams(dp(28), dp(28)));

        // Category 1: Ready
        content.addView(createSectionHeader("Ready"));
        hubReadyList = new LinearLayout(this);
        hubReadyList.setOrientation(LinearLayout.VERTICAL);
        content.addView(hubReadyList);

        // Category 2: Active / Live Attached
        content.addView(createSectionHeader("Live & Active"));
        hubActiveList = new LinearLayout(this);
        hubActiveList.setOrientation(LinearLayout.VERTICAL);
        content.addView(hubActiveList);

        // Category 3: Idle (Past Sessions)
        content.addView(createSectionHeader("Idle"));
        hubIdleList = new LinearLayout(this);
        hubIdleList.setOrientation(LinearLayout.VERTICAL);
        content.addView(hubIdleList);

        scroll.addView(content);
        parent.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private TextView createSectionHeader(String title) {
        TextView v = cText(title, 13, CLAUDE_TEXT_MUTED, false, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(18), 0, dp(8));
        v.setLayoutParams(lp);
        return v;
    }

    private void fetchHubSessions() {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) return;

        if (hubLoadingProgress != null) hubLoadingProgress.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                String sessionsUrl = endpoint.replace("/api/chat", "/api/sessions");
                HttpURLConnection c = (HttpURLConnection) new URL(sessionsUrl).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(8000);
                String token = prefs.getString("token", "");
                if (!token.isEmpty()) {
                    c.setRequestProperty("Authorization", "Bearer " + token);
                }

                int code = c.getResponseCode();
                if (code == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder b = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) b.append(line);
                    JSONObject json = new JSONObject(b.toString());
                    JSONArray sessions = json.optJSONArray("sessions");

                    mainHandler.post(() -> renderHubSessions(sessions));
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (hubLoadingProgress != null) hubLoadingProgress.setVisibility(View.GONE);
                });
            }
        });
    }

    private void renderHubSessions(JSONArray sessions) {
        if (hubLoadingProgress != null) hubLoadingProgress.setVisibility(View.GONE);
        if (hubReadyList != null) hubReadyList.removeAllViews();
        if (hubActiveList != null) hubActiveList.removeAllViews();
        if (hubIdleList != null) hubIdleList.removeAllViews();

        if (sessions == null || sessions.length() == 0) {
            addSessionItemToHub(hubReadyList, "Start a new coding task", "google/antigravity-cli", null, false);
            return;
        }

        for (int i = 0; i < sessions.length(); i++) {
            try {
                JSONObject s = sessions.getJSONObject(i);
                final String convId = s.optString("conversationId", "");
                String title = s.optString("title", "Session");
                String workspace = s.optString("workspace", "/home/ubuntu");
                String repoTag = workspace.endsWith("codexcli-remote-app") ? "anthropic/claude-code" : "google/antigravity-cli";

                boolean isMostRecent = (i == 0);
                if (isMostRecent) {
                    addSessionItemToHub(hubActiveList, title, repoTag, convId, true);
                } else if (i < 3) {
                    addSessionItemToHub(hubReadyList, title, repoTag, convId, false);
                } else {
                    addSessionItemToHub(hubIdleList, title, repoTag, convId, false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void addSessionItemToHub(LinearLayout container, final String title, String repoTag, final String convId, boolean isOpenBadge) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(10));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = cText(title, 16, CLAUDE_TEXT_MAIN, true, false);
        titleView.setLineSpacing(0, 1.15f);
        topRow.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));

        if (isOpenBadge) {
            TextView openBadge = cText("⢝ Open", 11.5f, CLAUDE_GREEN, true, false);
            openBadge.setBackground(cBox(CLAUDE_GREEN_BG, CLAUDE_GREEN, 1, 6));
            openBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
            topRow.addView(openBadge);
        }

        row.addView(topRow);

        TextView repoView = cText(repoTag, 13, CLAUDE_TEXT_MUTED, false, false);
        LinearLayout.LayoutParams lpR = new LinearLayout.LayoutParams(-1, -2);
        lpR.setMargins(0, dp(2), 0, 0);
        row.addView(repoView, lpR);

        row.setOnClickListener(v -> {
            activeConversationId = convId;
            activeSessionTitle = title;
            showScreen(1);
        });

        container.addView(row);
    }

    private void startNewSession() {
        activeConversationId = null;
        activeSessionTitle = "New session";
        showScreen(1);
    }

    // ============================================================
    // SCREEN 2: "New session" / CHAT VIEW (Claude Pixel Mascot & Floating Composer)
    // ============================================================
    private void buildChatScreen(LinearLayout parent) {
        parent.setPadding(dp(16), dp(10), dp(16), dp(12));

        // Top App Bar (< Back, Title, More)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, dp(4), 0, dp(10));

        TextView backBtn = cText("〈", 18, CLAUDE_TEXT_MAIN, true, false);
        backBtn.setPadding(dp(4), dp(4), dp(12), dp(4));
        backBtn.setOnClickListener(v -> showScreen(0));
        topBar.addView(backBtn);

        chatTopTitle = cText("New session", 15.5f, CLAUDE_TEXT_MAIN, true, false);
        chatTopTitle.setGravity(Gravity.CENTER);
        chatTopTitle.setSingleLine(true);
        topBar.addView(chatTopTitle, new LinearLayout.LayoutParams(0, -2, 1));

        TextView moreBtn = cText("⋯", 18, CLAUDE_TEXT_MUTED, true, false);
        moreBtn.setPadding(dp(8), dp(4), dp(4), dp(4));
        moreBtn.setOnClickListener(v -> showMoreOptionsMenu());
        topBar.addView(moreBtn);
        parent.addView(topBar);

        // Chat Transcript Scroll Area
        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setVerticalScrollBarEnabled(false);

        chatMessagesList = new LinearLayout(this);
        chatMessagesList.setOrientation(LinearLayout.VERTICAL);
        chatMessagesList.setGravity(Gravity.BOTTOM);

        buildEmptyMascotState();
        chatMessagesList.addView(emptyMascotView);

        chatScroll.addView(chatMessagesList);
        parent.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // Attachment Preview Chip
        attachmentChip = createAttachmentChip();
        parent.addView(attachmentChip);

        // Floating Bottom Composer Box (Card #FFFFFF with Pill Tag and Terracotta Send Button)
        LinearLayout composerCard = new LinearLayout(this);
        composerCard.setOrientation(LinearLayout.VERTICAL);
        composerCard.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_BORDER, 1, 22));
        composerCard.setPadding(dp(16), dp(14), dp(14), dp(12));
        composerCard.setElevation(dp(4));

        promptInput = new EditText(this);
        promptInput.setHint("Code anything...");
        promptInput.setHintTextColor(CLAUDE_TEXT_LIGHT);
        promptInput.setTextColor(CLAUDE_TEXT_MAIN);
        promptInput.setTextSize(15);
        promptInput.setBackground(null);
        promptInput.setMinLines(1);
        promptInput.setMaxLines(5);
        promptInput.setSingleLine(false);
        promptInput.setPadding(0, 0, 0, dp(10));
        composerCard.addView(promptInput, new LinearLayout.LayoutParams(-1, -2));

        // Bottom Controls Row: [Repo Pill] ... [+] [Cloud] [Mic] [Send]
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);

        // Repository / Workspace Pill
        repoTagLabel = cText("google/antigravity-cli", 12, CLAUDE_TEXT_MAIN, false, false);
        repoTagLabel.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 14));
        repoTagLabel.setPadding(dp(12), dp(6), dp(12), dp(6));
        repoTagLabel.setOnClickListener(v -> toggleEngine());
        bottomRow.addView(repoTagLabel);

        View spacer = new View(this);
        bottomRow.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1));

        // Plus (+) Attachment Button
        btnAttach = createComposerIconBtn("+", 20);
        btnAttach.setOnClickListener(v -> openFilePicker());
        bottomRow.addView(btnAttach);

        // Cloud Gateway Status Button
        btnEnginePill = createComposerIconBtn("☁", 17);
        btnEnginePill.setOnClickListener(v -> checkHealth());
        bottomRow.addView(btnEnginePill);

        // Voice Microphone Button
        btnVoice = createComposerIconBtn("🎙", 17);
        btnVoice.setOnClickListener(v -> startVoiceRecognition());
        bottomRow.addView(btnVoice);

        // Terracotta Circle Arrow Send Button (Arrow Up)
        btnSend = new Button(this);
        btnSend.setText("↑");
        btnSend.setTextSize(20);
        btnSend.setTextColor(Color.WHITE);
        btnSend.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        btnSend.setBackground(cBox(CLAUDE_TERRACOTTA, 0, 0, 20));
        btnSend.setOnClickListener(v -> sendClaudePrompt());
        LinearLayout.LayoutParams lpSend = new LinearLayout.LayoutParams(dp(40), dp(40));
        lpSend.setMargins(dp(6), 0, 0, 0);
        bottomRow.addView(btnSend, lpSend);

        composerCard.addView(bottomRow);
        parent.addView(composerCard);
    }

    private Button createComposerIconBtn(String symbol, float sp) {
        Button b = new Button(this);
        b.setText(symbol);
        b.setTextSize(sp);
        b.setTextColor(CLAUDE_TEXT_MAIN);
        b.setBackground(null);
        b.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
        lp.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout createAttachmentChip() {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_TERRACOTTA, 1, 14));
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        chip.setVisibility(View.GONE);

        attachmentText = cText("📎 Attached File", 12.5f, CLAUDE_TERRACOTTA, true, false);
        chip.addView(attachmentText, new LinearLayout.LayoutParams(0, -2, 1));

        TextView close = cText("✕", 14, CLAUDE_RED, true, false);
        close.setPadding(dp(8), 0, 0, 0);
        close.setOnClickListener(v -> {
            chip.setVisibility(View.GONE);
            attachedServerPath = null;
        });
        chip.addView(close);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        chip.setLayoutParams(lp);
        return chip;
    }

    private void buildEmptyMascotState() {
        emptyMascotView = new LinearLayout(this);
        emptyMascotView.setOrientation(LinearLayout.VERTICAL);
        emptyMascotView.setGravity(Gravity.CENTER);
        emptyMascotView.setPadding(dp(20), dp(80), dp(20), dp(80));

        // Claw'd Pixel Mascot Art
        TextView mascot = cText("👾", 48, CLAUDE_TERRACOTTA, true, false);
        mascot.setGravity(Gravity.CENTER);
        emptyMascotView.addView(mascot);

        TextView sparkle = cText("✦     ✧     ✦", 12, CLAUDE_TERRACOTTA, false, false);
        sparkle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpSp = new LinearLayout.LayoutParams(-1, -2);
        lpSp.setMargins(0, dp(12), 0, 0);
        emptyMascotView.addView(sparkle, lpSp);
    }

    private void showEmptyMascotState(boolean show) {
        if (emptyMascotView != null) {
            emptyMascotView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void updateRepoTag() {
        if (repoTagLabel != null) {
            boolean isAgy = "antigravity".equalsIgnoreCase(currentEngine);
            repoTagLabel.setText(isAgy ? "google/antigravity-cli" : "openai/codex-cli");
        }
    }

    private void toggleEngine() {
        currentEngine = "antigravity".equalsIgnoreCase(currentEngine) ? "codex" : "antigravity";
        prefs.edit().putString("engine", currentEngine).apply();
        updateRepoTag();
        Toast.makeText(this, "Engine: " + currentEngine, Toast.LENGTH_SHORT).show();
    }

    private void showMoreOptionsMenu() {
        String[] options = {"🛑 Interrupt / Stop Task", "⚙ Connection Settings", "⟳ Refresh Transcript", "＋ Clear to New Session"};
        new AlertDialog.Builder(this)
                .setTitle("Session Controls")
                .setItems(options, (d, which) -> {
                    if (which == 0) stopRunningCliProcess();
                    else if (which == 1) showConnectionDialog();
                    else if (which == 2) fetchActiveSessionTurns(true);
                    else if (which == 3) startNewSession();
                })
                .show();
    }

    // ============================================================
    // ATTACHMENT & VOICE HANDLING
    // ============================================================
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select File or Image to Upload"), REQ_PICK_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show();
        }
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your instruction...");
        try {
            startActivityForResult(intent, REQ_VOICE_SPEECH);
        } catch (Exception e) {
            Toast.makeText(this, "Voice input not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQ_PICK_FILE && data.getData() != null) {
                uploadSelectedFile(data.getData());
            } else if (requestCode == REQ_VOICE_SPEECH) {
                ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) {
                    String spoken = matches.get(0);
                    promptInput.append((promptInput.getText().length() > 0 ? " " : "") + spoken);
                    promptInput.setSelection(promptInput.getText().length());
                }
            }
        }
    }

    private void uploadSelectedFile(final Uri uri) {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) {
            showConnectionDialog();
            return;
        }

        Toast.makeText(this, "Uploading file to server...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                String fileName = getFileNameFromUri(uri);
                InputStream is = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) {
                    bos.write(buf, 0, len);
                }
                is.close();

                byte[] fileBytes = bos.toByteArray();
                String base64Data = Base64.encodeToString(fileBytes, Base64.NO_WRAP);

                String uploadUrl = endpoint.replace("/api/chat", "/api/upload");
                HttpURLConnection c = (HttpURLConnection) new URL(uploadUrl).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(15000);
                c.setReadTimeout(60000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                String token = prefs.getString("token", "");
                if (!token.isEmpty()) {
                    c.setRequestProperty("Authorization", "Bearer " + token);
                }

                JSONObject req = new JSONObject();
                req.put("filename", fileName);
                req.put("data", base64Data);
                c.getOutputStream().write(req.toString().getBytes(StandardCharsets.UTF_8));

                int code = c.getResponseCode();
                if (code == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder out = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) out.append(line);
                    JSONObject res = new JSONObject(out.toString());
                    final String serverPath = res.optString("filePath", "");
                    final String savedName = res.optString("filename", fileName);

                    mainHandler.post(() -> {
                        attachedServerPath = serverPath;
                        attachmentText.setText("📎 " + savedName + " (Attached)");
                        attachmentChip.setVisibility(View.VISIBLE);
                        Toast.makeText(MainActivity.this, "File attached successfully", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Upload error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        result = cursor.getString(idx);
                    }
                }
            } catch (Exception e) {}
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "upload_" + System.currentTimeMillis() + ".bin";
    }

    // ============================================================
    // CHAT EXECUTION & LIVE STREAMING
    // ============================================================
    private void sendClaudePrompt() {
        String text = promptInput.getText().toString().trim();
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) {
            showConnectionDialog();
            return;
        }
        if (text.isEmpty() && attachedServerPath == null) return;
        if (btnSend.getTag() != null) return;

        final String file = attachedServerPath;
        attachedServerPath = null;
        if (attachmentChip != null) attachmentChip.setVisibility(View.GONE);

        showEmptyMascotState(false);

        btnSend.setTag("busy");
        btnSend.setEnabled(false);
        promptInput.setEnabled(false);

        String displayText = (file != null ? "[📎 " + file + "]\n" : "") + text;
        addMessageCard("user", displayText, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), null);
        promptInput.setText("");

        chatSendProgress = new ProgressBar(this);
        LinearLayout.LayoutParams lpProg = new LinearLayout.LayoutParams(dp(24), dp(24));
        lpProg.setMargins(dp(16), dp(4), 0, dp(10));
        chatMessagesList.addView(chatSendProgress, lpProg);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));

        executor.execute(() -> {
            try {
                JSONObject req = new JSONObject();
                req.put("prompt", text);
                req.put("engine", currentEngine);
                req.put("resume", true);
                if (file != null) {
                    req.put("attachedFile", file);
                }
                if (activeConversationId != null && !activeConversationId.isEmpty()) {
                    req.put("conversationId", activeConversationId);
                }

                JSONObject res = executePost(endpoint, prefs.getString("token", ""), req);
                String responseText = res.optString("response", "No output returned.");

                mainHandler.post(() -> {
                    if (chatSendProgress != null) {
                        chatMessagesList.removeView(chatSendProgress);
                        chatSendProgress = null;
                    }
                    addMessageCard("assistant", responseText, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), null);
                    btnSend.setTag(null);
                    btnSend.setEnabled(true);
                    promptInput.setEnabled(true);
                    fetchActiveSessionTurns(false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (chatSendProgress != null) {
                        chatMessagesList.removeView(chatSendProgress);
                        chatSendProgress = null;
                    }
                    addMessageCard("tool", "Notice: " + e.getMessage() + "\nSyncing live outputs from server...", "", "Gateway Notice");
                    btnSend.setTag(null);
                    btnSend.setEnabled(true);
                    promptInput.setEnabled(true);
                    startAutoRefresh();
                });
            }
        });
    }

    private JSONObject executePost(String endpoint, String token, JSONObject req) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(300000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        if (!token.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + token);
        }

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

    private void fetchActiveSessionTurns(final boolean showFeedback) {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) return;

        executor.execute(() -> {
            try {
                String liveUrl = endpoint.replace("/api/chat", "/api/session/live");
                HttpURLConnection c = (HttpURLConnection) new URL(liveUrl).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(8000);
                String token = prefs.getString("token", "");
                if (!token.isEmpty()) {
                    c.setRequestProperty("Authorization", "Bearer " + token);
                }

                int code = c.getResponseCode();
                if (code == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder b = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) b.append(line);
                    JSONObject json = new JSONObject(b.toString());

                    mainHandler.post(() -> renderActiveSessionTurns(json, showFeedback));
                }
            } catch (Exception e) {
                if (showFeedback) {
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, "Sync error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void renderActiveSessionTurns(JSONObject json, boolean showToast) {
        try {
            JSONObject session = json.optJSONObject("session");
            if (session != null) {
                activeConversationId = session.optString("conversationId", activeConversationId);
                activeSessionTitle = session.optString("title", activeSessionTitle);
                chatTopTitle.setText(activeSessionTitle);
            }

            JSONArray turns = json.optJSONArray("turns");
            if (turns != null && btnSend.getTag() == null) {
                chatMessagesList.removeAllViews();
                showEmptyMascotState(turns.length() == 0);
                for (int i = 0; i < turns.length(); i++) {
                    JSONObject turn = turns.getJSONObject(i);
                    String role = turn.optString("role", "info");
                    String title = turn.optString("title", "");
                    String content = turn.optString("content", "");
                    String time = turn.optString("time", "");
                    addMessageCard(role, content, time, title);
                }
                chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
            }

            if (showToast) {
                Toast.makeText(this, "Session turns synchronized", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Claude-style Message Bubble & Dropdown Accordions
    private void addMessageCard(String role, String content, String time, String title) {
        boolean isTool = "tool".equalsIgnoreCase(role);
        boolean isThinking = "thinking".equalsIgnoreCase(role);
        boolean isUser = "user".equalsIgnoreCase(role);

        showEmptyMascotState(false);

        if (isTool || isThinking) {
            // Collapsible Claude Tool/Thinking Dropdown Accordion
            LinearLayout accordion = new LinearLayout(this);
            accordion.setOrientation(LinearLayout.VERTICAL);
            accordion.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_BORDER, 1, 12));
            accordion.setPadding(dp(12), dp(8), dp(12), dp(8));

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            final TextView chevron = cText("▶ ", 12, CLAUDE_TEXT_MUTED, true, false);
            header.addView(chevron);

            String displayTitle = (title != null && !title.isEmpty()) ? title : (isTool ? "Executed tool" : "Thinking process");
            String icon = isTool ? "🛠 " : "💭 ";
            TextView titleView = cText(icon + displayTitle, 13, CLAUDE_TEXT_MAIN, true, false);
            header.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));

            TextView badge = cText("✓ Done", 10.5f, CLAUDE_GREEN, true, false);
            badge.setBackground(cBox(CLAUDE_GREEN_BG, CLAUDE_GREEN, 1, 6));
            badge.setPadding(dp(6), dp(2), dp(6), dp(2));
            header.addView(badge);

            accordion.addView(header);

            final LinearLayout bodyContainer = new LinearLayout(this);
            bodyContainer.setOrientation(LinearLayout.VERTICAL);
            bodyContainer.setVisibility(View.GONE);
            bodyContainer.setBackground(cBox(CLAUDE_CODE_BG, 0, 0, 8));
            bodyContainer.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams lpBody = new LinearLayout.LayoutParams(-1, -2);
            lpBody.setMargins(0, dp(8), 0, 0);

            TextView bodyContent = new TextView(this);
            bodyContent.setText(content);
            bodyContent.setTextSize(12);
            bodyContent.setTextColor(Color.rgb(220, 220, 225));
            bodyContent.setTypeface(Typeface.MONOSPACE);
            bodyContent.setTextIsSelectable(true);
            bodyContainer.addView(bodyContent);

            accordion.addView(bodyContainer, lpBody);

            header.setOnClickListener(v -> {
                boolean isOpen = (bodyContainer.getVisibility() == View.VISIBLE);
                bodyContainer.setVisibility(isOpen ? View.GONE : View.VISIBLE);
                chevron.setText(isOpen ? "▶ " : "▼ ");
            });

            LinearLayout.LayoutParams lpAcc = new LinearLayout.LayoutParams(-1, -2);
            lpAcc.setMargins(0, 0, 0, dp(8));
            chatMessagesList.addView(accordion, lpAcc);
            return;
        }

        // Standard Message Card (User or Assistant)
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        int bgColor = isUser ? CLAUDE_TERRACOTTA_LIGHT : CLAUDE_SURFACE;
        int borderColor = isUser ? CLAUDE_TERRACOTTA : CLAUDE_BORDER;
        card.setBackground(cBox(bgColor, borderColor, 1, 16));
        card.setPadding(dp(14), dp(10), dp(14), dp(12));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        String author = isUser ? "You" : "Claude / Antigravity";
        int authorColor = isUser ? CLAUDE_TERRACOTTA : CLAUDE_TEXT_MAIN;
        TextView authorV = cText(author, 12.5f, authorColor, true, false);
        head.addView(authorV, new LinearLayout.LayoutParams(0, -2, 1));

        String shortTime = time.contains("T") && time.length() >= 16 ? time.substring(11, 16) : time;
        TextView timeV = cText(shortTime, 11, CLAUDE_TEXT_LIGHT, false, false);
        head.addView(timeV);
        card.addView(head);

        renderMarkdownIntoContainer(card, content, isUser);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        chatMessagesList.addView(card, lp);
    }

    // ============================================================
    // MARKDOWN FORMATTER & ACCORDION CODE BLOCKS
    // ============================================================
    private void renderMarkdownIntoContainer(LinearLayout container, String markdown, boolean isUser) {
        if (markdown == null || markdown.isEmpty()) return;

        String[] sections = markdown.split("```");
        for (int s = 0; s < sections.length; s++) {
            if (s % 2 == 1) {
                // Code block section
                String block = sections[s];
                String lang = "CODE";
                String codeContent = block;
                int firstLf = block.indexOf("\n");
                if (firstLf > 0 && firstLf < 20) {
                    lang = block.substring(0, firstLf).trim().toUpperCase(Locale.ROOT);
                    codeContent = block.substring(firstLf + 1);
                }

                LinearLayout codeBox = new LinearLayout(this);
                codeBox.setOrientation(LinearLayout.VERTICAL);
                codeBox.setBackground(cBox(CLAUDE_CODE_BG, 0, 0, 10));
                codeBox.setPadding(dp(12), dp(10), dp(12), dp(10));

                LinearLayout codeHeader = new LinearLayout(this);
                codeHeader.setOrientation(LinearLayout.HORIZONTAL);
                codeHeader.setGravity(Gravity.CENTER_VERTICAL);

                TextView langTag = cText(lang, 11, CLAUDE_TERRACOTTA, true, false);
                codeHeader.addView(langTag, new LinearLayout.LayoutParams(0, -2, 1));

                TextView copyBtn = cText("Copy", 11, CLAUDE_TEXT_LIGHT, true, false);
                final String copyCode = codeContent.trim();
                copyBtn.setOnClickListener(v -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("Code snippet", copyCode));
                    Toast.makeText(MainActivity.this, "Copied code snippet", Toast.LENGTH_SHORT).show();
                });
                codeHeader.addView(copyBtn);
                codeBox.addView(codeHeader);

                TextView codeView = new TextView(this);
                codeView.setText(codeContent.trim());
                codeView.setTextSize(12.5f);
                codeView.setTextColor(Color.rgb(235, 235, 240));
                codeView.setTypeface(Typeface.MONOSPACE);
                codeView.setTextIsSelectable(true);
                codeView.setPadding(0, dp(6), 0, 0);
                codeBox.addView(codeView);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, dp(6), 0, dp(6));
                container.addView(codeBox, lp);
            } else {
                // Markdown text lines
                String text = sections[s];
                String[] lines = text.split("\n");
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;

                    String trimmed = line.trim();
                    if (trimmed.startsWith("### ")) {
                        TextView h3 = cText(trimmed.substring(4), 15, CLAUDE_TEXT_MAIN, true, true);
                        h3.setPadding(0, dp(6), 0, dp(2));
                        container.addView(h3);
                    } else if (trimmed.startsWith("## ")) {
                        TextView h2 = cText(trimmed.substring(3), 17, CLAUDE_TEXT_MAIN, true, true);
                        h2.setPadding(0, dp(8), 0, dp(3));
                        container.addView(h2);
                    } else if (trimmed.startsWith("# ")) {
                        TextView h1 = cText(trimmed.substring(2), 20, CLAUDE_TEXT_MAIN, true, true);
                        h1.setPadding(0, dp(10), 0, dp(4));
                        container.addView(h1);
                    } else if (trimmed.startsWith("---") || trimmed.startsWith("***")) {
                        View divider = new View(this);
                        divider.setBackgroundColor(CLAUDE_BORDER);
                        LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(-1, dp(1));
                        lpDiv.setMargins(0, dp(8), 0, dp(8));
                        container.addView(divider, lpDiv);
                    } else {
                        SpannableStringBuilder span = parseInlineMarkdown(line);
                        TextView p = new TextView(this);
                        p.setText(span);
                        p.setTextSize(14);
                        p.setTextColor(CLAUDE_TEXT_MAIN);
                        p.setLineSpacing(0, 1.25f);
                        p.setTextIsSelectable(true);
                        p.setPadding(0, dp(2), 0, dp(2));

                        final String rawLine = line;
                        p.setOnLongClickListener(v -> {
                            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("Chat text", rawLine));
                            Toast.makeText(MainActivity.this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                            return true;
                        });

                        container.addView(p);
                    }
                }
            }
        }
    }

    private SpannableStringBuilder parseInlineMarkdown(String raw) {
        String line = raw;
        if (line.trim().startsWith("* ") || line.trim().startsWith("- ")) {
            line = "  •  " + line.trim().substring(2);
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder(line);

        // Bold (**bold**)
        Pattern boldPat = Pattern.compile("\\*\\*(.+?)\\*\\*");
        Matcher boldMat = boldPat.matcher(ssb.toString());
        while (boldMat.find()) {
            int start = boldMat.start();
            int end = boldMat.end();
            String inner = boldMat.group(1);
            ssb.replace(start, end, inner);
            ssb.setSpan(new StyleSpan(Typeface.BOLD), start, start + inner.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            boldMat = boldPat.matcher(ssb.toString());
        }

        // Inline code (`code`)
        Pattern codePat = Pattern.compile("`([^`]+)`");
        Matcher codeMat = codePat.matcher(ssb.toString());
        while (codeMat.find()) {
            int start = codeMat.start();
            int end = codeMat.end();
            String inner = codeMat.group(1);
            ssb.replace(start, end, " " + inner + " ");
            int spanEnd = start + inner.length() + 2;
            ssb.setSpan(new TypefaceSpan("monospace"), start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new BackgroundColorSpan(CLAUDE_SURFACE_MUTED), start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(CLAUDE_TERRACOTTA), start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            codeMat = codePat.matcher(ssb.toString());
        }

        return ssb;
    }

    // ============================================================
    // GENERAL SETTINGS & INTERRUPT
    // ============================================================
    private void startAutoRefresh() {
        isAutoRefreshActive = true;
        mainHandler.removeCallbacks(autoRefreshRunnable);
        mainHandler.post(autoRefreshRunnable);
    }

    private void stopAutoRefresh() {
        isAutoRefreshActive = false;
        mainHandler.removeCallbacks(autoRefreshRunnable);
    }

    private void stopRunningCliProcess() {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) return;

        executor.execute(() -> {
            try {
                String ctrlUrl = endpoint.replace("/api/chat", "/api/session/control");
                HttpURLConnection c = (HttpURLConnection) new URL(ctrlUrl).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                String token = prefs.getString("token", "");
                if (!token.isEmpty()) {
                    c.setRequestProperty("Authorization", "Bearer " + token);
                }

                JSONObject req = new JSONObject();
                req.put("action", "stop");
                c.getOutputStream().write(req.toString().getBytes(StandardCharsets.UTF_8));
                int code = c.getResponseCode();

                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, code == 200 ? "Task interrupted" : "HTTP " + code, Toast.LENGTH_SHORT).show();
                    fetchActiveSessionTurns(false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Error stopping: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void checkHealth() {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) {
            showConnectionDialog();
            return;
        }
        executor.execute(() -> {
            try {
                String healthUrl = endpoint.replace("/api/chat", "/health");
                HttpURLConnection c = (HttpURLConnection) new URL(healthUrl).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(5000);
                int code = c.getResponseCode();
                mainHandler.post(() -> Toast.makeText(this, code == 200 ? "Gateway online & connected!" : "Gateway HTTP " + code, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, "Gateway offline: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showConnectionDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(10), dp(22), dp(10));

        TextView urlLbl = cText("Bridge Endpoint URL:", 12.5f, CLAUDE_TEXT_MUTED, true, false);
        form.addView(urlLbl);

        EditText urlInput = new EditText(this);
        urlInput.setHint("https://your-bridge.trycloudflare.com/api/chat");
        urlInput.setText(prefs.getString("url", ""));
        urlInput.setTextColor(CLAUDE_TEXT_MAIN);
        urlInput.setHintTextColor(CLAUDE_TEXT_LIGHT);
        urlInput.setTextSize(14);
        urlInput.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 10));
        urlInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpUrl = new LinearLayout.LayoutParams(-1, dp(48));
        lpUrl.setMargins(0, dp(4), 0, dp(14));
        form.addView(urlInput, lpUrl);

        TextView tokLbl = cText("Bearer Token (Secret):", 12.5f, CLAUDE_TEXT_MUTED, true, false);
        form.addView(tokLbl);

        EditText tokenInput = new EditText(this);
        tokenInput.setHint("codex-remote-token-2026");
        tokenInput.setText(prefs.getString("token", ""));
        tokenInput.setTextColor(CLAUDE_TEXT_MAIN);
        tokenInput.setHintTextColor(CLAUDE_TEXT_LIGHT);
        tokenInput.setTextSize(14);
        tokenInput.setInputType(0x00000081);
        tokenInput.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 10));
        tokenInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpTok = new LinearLayout.LayoutParams(-1, dp(48));
        lpTok.setMargins(0, dp(4), 0, dp(8));
        form.addView(tokenInput, lpTok);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Remote Gateway Setup")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save & Connect", null)
                .create();

        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String u = urlInput.getText().toString().trim();
            String t = tokenInput.getText().toString().trim();
            prefs.edit().putString("url", u).putString("token", t).apply();
            dialog.dismiss();
            checkHealth();
            fetchHubSessions();
        }));
        dialog.show();
    }
}
