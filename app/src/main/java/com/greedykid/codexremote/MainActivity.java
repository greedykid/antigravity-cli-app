package com.greedykid.codexremote;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

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

    // Root Frame & Sidebar Navigation
    private FrameLayout rootFrame;
    private View sidebarScrim;
    private LinearLayout sidebarPanel;
    private TextView sidebarStatusDot;
    private TextView sidebarStatusText;
    private TextView sidebarEngineLabel;
    private boolean isSidebarOpen = false;

    // View Containers (Screen 0: Hub, Screen 1: Chat)
    private LinearLayout mainContentContainer;
    private LinearLayout viewHubContainer;
    private LinearLayout viewChatContainer;

    // Hub View (Screen 1) Components
    private LinearLayout hubReadyList;
    private LinearLayout hubActiveList;
    private LinearLayout hubIdleList;
    private ProgressBar hubLoadingProgress;

    // Chat View (Screen 2) Components
    private TextView chatNavIcon;
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
    private int currentScreen = 1; // Default to Screen 1 (New Chat Session)
    private boolean navigatedFromHub = false;

    // Optimization State to prevent aggressive scroll jumping & redundant re-renders
    private String lastLoadedSessionId = null;
    private int lastLoadedTurnCount = -1;

    private boolean isAutoRefreshActive = false;
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAutoRefreshActive && currentScreen == 1 && activeConversationId != null) {
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
        buildClaudeUiWithSidebar();
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
        } else if (isAutoRefreshActive && activeConversationId != null) {
            startAutoRefresh();
        }
    }

    @Override
    public void onBackPressed() {
        if (isSidebarOpen) {
            closeSidebar();
        } else if (currentScreen == 1 && navigatedFromHub) {
            navigatedFromHub = false;
            showScreen(0);
        } else if (currentScreen == 0) {
            startNewSession();
        } else {
            super.onBackPressed();
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

    private void buildClaudeUiWithSidebar() {
        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(CLAUDE_BG);

        // 1. Main Screens Container (Hub & Chat)
        mainContentContainer = new LinearLayout(this);
        mainContentContainer.setOrientation(LinearLayout.VERTICAL);

        viewHubContainer = new LinearLayout(this);
        viewHubContainer.setOrientation(LinearLayout.VERTICAL);
        buildHubScreen(viewHubContainer);
        mainContentContainer.addView(viewHubContainer, new LinearLayout.LayoutParams(-1, -1));

        viewChatContainer = new LinearLayout(this);
        viewChatContainer.setOrientation(LinearLayout.VERTICAL);
        viewChatContainer.setVisibility(View.GONE);
        buildChatScreen(viewChatContainer);
        mainContentContainer.addView(viewChatContainer, new LinearLayout.LayoutParams(-1, -1));

        rootFrame.addView(mainContentContainer, new FrameLayout.LayoutParams(-1, -1));

        // 2. Sidebar Backdrop Scrim (Dim overlay)
        sidebarScrim = new View(this);
        sidebarScrim.setBackgroundColor(Color.argb(120, 0, 0, 0));
        sidebarScrim.setVisibility(View.GONE);
        sidebarScrim.setAlpha(0f);
        sidebarScrim.setOnClickListener(v -> closeSidebar());
        rootFrame.addView(sidebarScrim, new FrameLayout.LayoutParams(-1, -1));

        // 3. Sidebar Panel (Left Drawer)
        sidebarPanel = new LinearLayout(this);
        sidebarPanel.setOrientation(LinearLayout.VERTICAL);
        sidebarPanel.setBackgroundColor(CLAUDE_SURFACE);
        sidebarPanel.setVisibility(View.GONE);
        buildSidebarContent(sidebarPanel);

        FrameLayout.LayoutParams lpSide = new FrameLayout.LayoutParams(dp(300), -1);
        lpSide.gravity = Gravity.START;
        rootFrame.addView(sidebarPanel, lpSide);

        setContentView(rootFrame);

        // Landing default is SCREEN 1 (New Chat Session) as requested
        startNewSession();
    }

    // ============================================================
    // SMOOTH ANIMATED SIDEBAR NAVIGATION
    // ============================================================
    private void buildSidebarContent(LinearLayout sidebar) {
        sidebar.setPadding(dp(22), dp(24), dp(22), dp(20));

        // App Branding Header
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);

        TextView icon = cText("👾", 28, CLAUDE_TERRACOTTA, true, false);
        brand.addView(icon);

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setPadding(dp(12), 0, 0, 0);

        TextView title = cText("Antigravity Remote", 16, CLAUDE_TEXT_MAIN, true, true);
        brandText.addView(title);
        TextView sub = cText("Claude Code Edition", 12, CLAUDE_TEXT_MUTED, false, false);
        brandText.addView(sub);
        brand.addView(brandText, new LinearLayout.LayoutParams(0, -2, 1));
        sidebar.addView(brand);

        // Status Card
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 14));
        statusCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpSt = new LinearLayout.LayoutParams(-1, -2);
        lpSt.setMargins(0, dp(18), 0, dp(16));

        LinearLayout stRow = new LinearLayout(this);
        stRow.setOrientation(LinearLayout.HORIZONTAL);
        stRow.setGravity(Gravity.CENTER_VERTICAL);
        sidebarStatusDot = cText("●", 11, CLAUDE_GREEN, false, false);
        stRow.addView(sidebarStatusDot);
        sidebarStatusText = cText(" Gateway Online", 12, CLAUDE_TEXT_MAIN, true, false);
        stRow.addView(sidebarStatusText);
        statusCard.addView(stRow);

        sidebarEngineLabel = cText("Active Engine: " + ("antigravity".equalsIgnoreCase(currentEngine) ? "Antigravity CLI" : "Codex CLI"), 11.5f, CLAUDE_TEXT_MUTED, false, false);
        LinearLayout.LayoutParams lpE = new LinearLayout.LayoutParams(-1, -2);
        lpE.setMargins(0, dp(4), 0, 0);
        statusCard.addView(sidebarEngineLabel, lpE);
        sidebar.addView(statusCard, lpSt);

        // Menu Items List
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout menuItems = new LinearLayout(this);
        menuItems.setOrientation(LinearLayout.VERTICAL);

        addSidebarMenuItem(menuItems, "📷", "Scan QR Code Pairing", () -> {
            closeSidebar();
            startQrScanner();
        });

        addSidebarMenuItem(menuItems, "💬", "New Chat Session", () -> {
            closeSidebar();
            startNewSession();
        });

        addSidebarMenuItem(menuItems, "📜", "All Sessions (Code Hub)", () -> {
            closeSidebar();
            showScreen(0);
        });

        addSidebarMenuItem(menuItems, "🚀", "Switch Engine (Agy / Codex)", () -> {
            toggleEngine();
            if (sidebarEngineLabel != null) {
                sidebarEngineLabel.setText("Active Engine: " + ("antigravity".equalsIgnoreCase(currentEngine) ? "Antigravity CLI" : "Codex CLI"));
            }
        });

        addSidebarMenuItem(menuItems, "⚙", "Connection Settings", () -> {
            closeSidebar();
            showConnectionDialog();
        });

        addSidebarMenuItem(menuItems, "🛑", "Interrupt Running Process", () -> {
            closeSidebar();
            stopRunningCliProcess();
        });

        addSidebarMenuItem(menuItems, "⟳", "Test Ping & Health", () -> {
            checkHealth();
        });

        scroll.addView(menuItems);
        sidebar.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // Footer Version info
        TextView ver = cText("v2.6.0 • QR Connect Active", 11.5f, CLAUDE_TEXT_LIGHT, false, false);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(10), 0, 0);
        sidebar.addView(ver);
    }

    private void addSidebarMenuItem(LinearLayout container, String icon, String title, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(12), dp(8), dp(12));
        row.setBackground(cBox(Color.TRANSPARENT, 0, 0, 10));

        TextView ic = cText(icon, 18, CLAUDE_TEXT_MAIN, false, false);
        row.addView(ic);

        TextView label = cText(title, 14, CLAUDE_TEXT_MAIN, false, false);
        label.setPadding(dp(14), 0, 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        row.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(2));
        container.addView(row, lp);
    }

    private void openSidebar() {
        if (isSidebarOpen) return;
        isSidebarOpen = true;

        final int panelWidth = sidebarPanel.getWidth() > 0 ? sidebarPanel.getWidth() : dp(300);

        sidebarScrim.setVisibility(View.VISIBLE);
        sidebarScrim.animate()
                .setListener(null)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        sidebarPanel.setTranslationX(-panelWidth);
        sidebarPanel.setVisibility(View.VISIBLE);
        sidebarPanel.animate()
                .setListener(null)
                .translationX(0f)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void closeSidebar() {
        if (!isSidebarOpen) return;
        isSidebarOpen = false;

        final int panelWidth = sidebarPanel.getWidth() > 0 ? sidebarPanel.getWidth() : dp(300);

        sidebarScrim.animate()
                .setListener(null)
                .alpha(0f)
                .setDuration(180)
                .setInterpolator(new AccelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!isSidebarOpen) {
                            sidebarScrim.setVisibility(View.GONE);
                        }
                    }
                })
                .start();

        sidebarPanel.animate()
                .setListener(null)
                .translationX(-panelWidth)
                .setDuration(200)
                .setInterpolator(new AccelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!isSidebarOpen) {
                            sidebarPanel.setVisibility(View.GONE);
                        }
                    }
                })
                .start();
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
            updateChatNavIcon();
            if (activeConversationId != null) {
                fetchActiveSessionTurns(true);
                startAutoRefresh();
            } else {
                stopAutoRefresh();
                chatMessagesList.removeAllViews();
                showEmptyMascotState(true);
            }
        }
    }

    private void updateChatNavIcon() {
        if (chatNavIcon != null) {
            if (navigatedFromHub) {
                chatNavIcon.setText("〈");
            } else {
                chatNavIcon.setText("☰");
            }
        }
    }

    // ============================================================
    // SCREEN 1: "Code" SESSIONS HUB (Exact Claude Code Style)
    // ============================================================
    private void buildHubScreen(LinearLayout parent) {
        parent.setPadding(dp(20), dp(16), dp(20), dp(16));

        // Top Navigation Bar (Menu, QR Scan, and New Session Button)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, dp(4), 0, dp(14));

        TextView menuIcon = cText("☰", 22, CLAUDE_TEXT_MAIN, false, false);
        menuIcon.setOnClickListener(v -> openSidebar());
        topBar.addView(menuIcon, new LinearLayout.LayoutParams(0, -2, 1));

        TextView qrBtn = cText("📷 QR", 13.5f, CLAUDE_TEXT_MAIN, true, false);
        qrBtn.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 16));
        qrBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        qrBtn.setOnClickListener(v -> startQrScanner());
        LinearLayout.LayoutParams lpQr = new LinearLayout.LayoutParams(-2, -2);
        lpQr.setMargins(0, 0, dp(8), 0);
        topBar.addView(qrBtn, lpQr);

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
            navigatedFromHub = true;
            openSpecificSession(convId, title);
        });

        container.addView(row);
    }

    private void openSpecificSession(String convId, String title) {
        activeConversationId = convId;
        activeSessionTitle = title != null && !title.isEmpty() ? title : "Session";
        lastLoadedSessionId = null;
        lastLoadedTurnCount = -1;

        if (chatMessagesList != null) chatMessagesList.removeAllViews();
        showEmptyMascotState(false);
        showScreen(1);
    }

    private void startNewSession() {
        activeConversationId = null;
        activeSessionTitle = "New session";
        lastLoadedSessionId = null;
        lastLoadedTurnCount = -1;
        navigatedFromHub = false;

        if (chatMessagesList != null) chatMessagesList.removeAllViews();
        showEmptyMascotState(true);
        showScreen(1);
    }

    // ============================================================
    // SCREEN 2: "New session" / CHAT VIEW (Claude Pixel Mascot & Floating Composer)
    // ============================================================
    private void buildChatScreen(LinearLayout parent) {
        parent.setPadding(dp(16), dp(10), dp(16), dp(12));

        // Top App Bar (☰ Menu / 〈 Back, Title, 📷 QR, ⋯ More)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, dp(4), 0, dp(10));

        chatNavIcon = cText("☰", 20, CLAUDE_TEXT_MAIN, true, false);
        chatNavIcon.setPadding(dp(4), dp(4), dp(12), dp(4));
        chatNavIcon.setOnClickListener(v -> {
            if (navigatedFromHub) {
                navigatedFromHub = false;
                showScreen(0);
            } else {
                openSidebar();
            }
        });
        topBar.addView(chatNavIcon);

        chatTopTitle = cText("New session", 15.5f, CLAUDE_TEXT_MAIN, true, false);
        chatTopTitle.setGravity(Gravity.CENTER);
        chatTopTitle.setSingleLine(true);
        topBar.addView(chatTopTitle, new LinearLayout.LayoutParams(0, -2, 1));

        TextView qrTopBtn = cText("📷", 18, CLAUDE_TEXT_MUTED, true, false);
        qrTopBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
        qrTopBtn.setOnClickListener(v -> startQrScanner());
        topBar.addView(qrTopBtn);

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
        String[] options = {"📷 Scan QR Pairing", "🛑 Interrupt / Stop Task", "⚙ Connection Settings", "⟳ Refresh Transcript", "＋ Clear to New Session"};
        new AlertDialog.Builder(this)
                .setTitle("Session Controls")
                .setItems(options, (d, which) -> {
                    if (which == 0) startQrScanner();
                    else if (which == 1) stopRunningCliProcess();
                    else if (which == 2) showConnectionDialog();
                    else if (which == 3) fetchActiveSessionTurns(true);
                    else if (which == 4) startNewSession();
                })
                .show();
    }

    // ============================================================
    // QR CODE SCANNER & PAIRING
    // ============================================================
    private void startQrScanner() {
        try {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setPrompt("Arahkan kamera ke QR Code di terminal");
            integrator.setBeepEnabled(true);
            integrator.setOrientationLocked(false);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            integrator.initiateScan();
        } catch (Exception e) {
            Toast.makeText(this, "QR Scanner error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleQrPayload(String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        String trimmed = raw.trim();

        try {
            // 1. JSON Payload Format: {"url":"...","token":"...","engine":"..."}
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                JSONObject obj = new JSONObject(trimmed);
                String url = obj.optString("url", "").trim();
                String token = obj.optString("token", "").trim();
                String engine = obj.optString("engine", "antigravity").trim();

                if (!url.isEmpty()) {
                    saveConnectionCredentials(url, token, engine);
                    return;
                }
            }

            // 2. URI Format: agy://connect?url=...&token=...
            if (trimmed.startsWith("agy://") || trimmed.startsWith("codex://") || trimmed.startsWith("http")) {
                Uri uri = Uri.parse(trimmed);
                String url = uri.getQueryParameter("url");
                String token = uri.getQueryParameter("token");
                String engine = uri.getQueryParameter("engine");

                if (url == null && (trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
                    url = trimmed;
                }

                if (url != null && !url.isEmpty()) {
                    saveConnectionCredentials(url, token != null ? token : "codex-remote-token-2026", engine != null ? engine : "antigravity");
                    return;
                }
            }

            Toast.makeText(this, "Format QR Code tidak dikenali: " + trimmed, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal memproses QR Code: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveConnectionCredentials(String url, String token, String engine) {
        prefs.edit()
                .putString("url", url)
                .putString("token", token)
                .putString("engine", engine)
                .apply();

        currentEngine = engine;
        updateRepoTag();
        Toast.makeText(this, "🎉 Berhasil terhubung via QR Code!", Toast.LENGTH_LONG).show();
        checkHealth();
        fetchHubSessions();
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
        // Check ZXing QR Scan Result first
        IntentResult qrResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (qrResult != null) {
            if (qrResult.getContents() != null) {
                handleQrPayload(qrResult.getContents());
            }
            return;
        }

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
        addMessageCard("user", displayText, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        promptInput.setText("");

        chatSendProgress = new ProgressBar(this);
        LinearLayout.LayoutParams lpProg = new LinearLayout.LayoutParams(dp(24), dp(24));
        lpProg.setMargins(dp(16), dp(4), 0, dp(10));
        chatMessagesList.addView(chatSendProgress, lpProg);
        
        // Explicit user prompt: scroll to bottom smoothly
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
                    addMessageCard("assistant", responseText, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
                    btnSend.setTag(null);
                    btnSend.setEnabled(true);
                    promptInput.setEnabled(true);
                    chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
                    fetchActiveSessionTurns(false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (chatSendProgress != null) {
                        chatMessagesList.removeView(chatSendProgress);
                        chatSendProgress = null;
                    }
                    addCompactExecutionPill("Notice: " + e.getMessage() + "\nSyncing live outputs from server...", "Gateway Status");
                    btnSend.setTag(null);
                    btnSend.setEnabled(true);
                    promptInput.setEnabled(true);
                    chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
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

        // If in "New session" empty state (activeConversationId == null), DO NOT load previous session turns!
        if (activeConversationId == null) {
            return;
        }

        final String targetConvId = activeConversationId;

        executor.execute(() -> {
            try {
                // Fetch the EXACT requested session transcript by ID!
                String url = endpoint.replace("/api/chat", "/api/session/transcript?id=" + Uri.encode(targetConvId));
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
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

                    mainHandler.post(() -> renderActiveSessionTurns(targetConvId, json, showFeedback));
                }
            } catch (Exception e) {
                if (showFeedback) {
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, "Sync error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void renderActiveSessionTurns(String requestedConvId, JSONObject json, boolean showToast) {
        try {
            // Verify that the user is still on the same session (prevent race conditions)
            if (activeConversationId == null || !activeConversationId.equals(requestedConvId)) {
                return;
            }

            JSONObject session = json.optJSONObject("session");
            if (session != null) {
                activeSessionTitle = session.optString("title", activeSessionTitle);
                chatTopTitle.setText(activeSessionTitle);
            }

            JSONArray turns = json.optJSONArray("turns");
            if (turns == null) {
                turns = json.optJSONArray("messages");
            }

            if (turns != null && btnSend.getTag() == null) {
                int newTurnCount = turns.length();

                // Prevent unnecessary View re-creation if nothing changed
                if (requestedConvId.equals(lastLoadedSessionId) && newTurnCount == lastLoadedTurnCount) {
                    if (showToast) Toast.makeText(this, "Session up to date", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Check if user is currently near the bottom BEFORE re-rendering
                boolean isNearBottom = isScrollNearBottom();
                boolean isInitialSessionLoad = !requestedConvId.equals(lastLoadedSessionId);

                lastLoadedSessionId = requestedConvId;
                lastLoadedTurnCount = newTurnCount;

                chatMessagesList.removeAllViews();
                showEmptyMascotState(turns.length() == 0);

                // Group consecutive tool & thinking turns into a SINGLE compact summary line
                ArrayList<JSONObject> pendingTools = new ArrayList<>();

                for (int i = 0; i < turns.length(); i++) {
                    JSONObject turn = turns.getJSONObject(i);
                    String role = turn.optString("role", "info");
                    String content = turn.optString("content", "");
                    String time = turn.optString("time", "");

                    if ("tool".equalsIgnoreCase(role) || "thinking".equalsIgnoreCase(role)) {
                        pendingTools.add(turn);
                    } else {
                        // Flush any pending tool & thinking executions as one compact modal pill
                        if (!pendingTools.isEmpty()) {
                            addCompactToolsGroupPill(new ArrayList<>(pendingTools));
                            pendingTools.clear();
                        }
                        addMessageCard(role, content, time);
                    }
                }

                if (!pendingTools.isEmpty()) {
                    addCompactToolsGroupPill(pendingTools);
                }

                // ONLY auto-scroll down if user was already at the bottom or if opening session for the first time
                if (isInitialSessionLoad || isNearBottom) {
                    chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
                }
            }

            if (showToast) {
                Toast.makeText(this, "Session turns synchronized", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isScrollNearBottom() {
        if (chatScroll == null || chatMessagesList == null) return true;
        int scrollY = chatScroll.getScrollY();
        int scrollHeight = chatScroll.getHeight();
        int contentHeight = chatMessagesList.getHeight();
        int distanceToBottom = contentHeight - (scrollY + scrollHeight);
        return distanceToBottom <= dp(180); // within 180dp of bottom is considered bottom
    }

    // ============================================================
    // COMPACT TEXT PILL & BOTTOM MODAL SHEET (Expanding Bottom-to-Top)
    // ============================================================
    private void addCompactToolsGroupPill(final ArrayList<JSONObject> toolTurns) {
        int toolCount = 0;
        int thinkCount = 0;
        String latestToolName = "";

        for (JSONObject t : toolTurns) {
            if ("tool".equalsIgnoreCase(t.optString("role"))) {
                toolCount++;
                latestToolName = t.optString("toolName", t.optString("title", "tool"));
            } else if ("thinking".equalsIgnoreCase(t.optString("role"))) {
                thinkCount++;
            }
        }

        String labelText;
        if (toolCount > 0 && thinkCount > 0) {
            labelText = "⚡ Worked on " + (toolCount + thinkCount) + " steps (" + toolCount + " tools, " + thinkCount + " thinking)  ›";
        } else if (toolCount > 0) {
            labelText = "🛠 Executed " + toolCount + " tool" + (toolCount > 1 ? "s" : "") + (latestToolName.isEmpty() ? "" : ": " + latestToolName) + "  ›";
        } else {
            labelText = "💭 Viewed thought process (" + thinkCount + " step" + (thinkCount > 1 ? "s" : "") + ")  ›";
        }

        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 14));
        pill.setPadding(dp(14), dp(8), dp(14), dp(8));

        TextView tv = cText(labelText, 12.5f, CLAUDE_TEXT_MAIN, true, false);
        pill.addView(tv, new LinearLayout.LayoutParams(0, -2, 1));

        TextView doneBadge = cText("✓ Done", 10.5f, CLAUDE_GREEN, true, false);
        doneBadge.setBackground(cBox(CLAUDE_GREEN_BG, CLAUDE_GREEN, 1, 6));
        doneBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
        pill.addView(doneBadge);

        pill.setOnClickListener(v -> openExecutionBottomModal(toolTurns));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(8));
        chatMessagesList.addView(pill, lp);
    }

    private void addCompactExecutionPill(String detail, String title) {
        ArrayList<JSONObject> list = new ArrayList<>();
        try {
            JSONObject o = new JSONObject();
            o.put("role", "tool");
            o.put("title", title);
            o.put("content", detail);
            list.add(o);
        } catch (Exception e) {}
        addCompactToolsGroupPill(list);
    }

    private void openExecutionBottomModal(ArrayList<JSONObject> items) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout modalRoot = new LinearLayout(this);
        modalRoot.setOrientation(LinearLayout.VERTICAL);
        modalRoot.setBackground(cBox(CLAUDE_SURFACE, 0, 0, 24));
        modalRoot.setPadding(dp(20), dp(12), dp(20), dp(18));

        // Top Drag Handle Pill
        View dragHandle = new View(this);
        dragHandle.setBackground(cBox(CLAUDE_BORDER_DARK, 0, 0, 3));
        LinearLayout.LayoutParams lpHandle = new LinearLayout.LayoutParams(dp(42), dp(5));
        lpHandle.gravity = Gravity.CENTER_HORIZONTAL;
        lpHandle.setMargins(0, 0, 0, dp(14));
        modalRoot.addView(dragHandle, lpHandle);

        // Header Title
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = cText("Execution & Thoughts", 18, CLAUDE_TEXT_MAIN, true, true);
        headerRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView closeBtn = cText("✕", 16, CLAUDE_TEXT_MUTED, true, false);
        closeBtn.setPadding(dp(8), dp(4), dp(4), dp(4));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        headerRow.addView(closeBtn);
        modalRoot.addView(headerRow);

        TextView sub = cText(items.size() + " actions executed by agent in this turn", 12.5f, CLAUDE_TEXT_MUTED, false, false);
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(-1, -2);
        lpSub.setMargins(0, dp(2), 0, dp(14));
        modalRoot.addView(sub, lpSub);

        // Scrollable List of Actions
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < items.size(); i++) {
            JSONObject it = items.get(i);
            String role = it.optString("role", "tool");
            String itTitle = it.optString("title", "tool".equalsIgnoreCase(role) ? "Executed Tool" : "Thinking Process");
            String content = it.optString("content", "");
            boolean isTool = "tool".equalsIgnoreCase(role);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 12));
            card.setPadding(dp(12), dp(10), dp(12), dp(10));

            LinearLayout cHead = new LinearLayout(this);
            cHead.setOrientation(LinearLayout.HORIZONTAL);
            cHead.setGravity(Gravity.CENTER_VERTICAL);

            TextView ic = cText(isTool ? "🛠 " : "💭 ", 13, isTool ? CLAUDE_TERRACOTTA : CLAUDE_TEXT_MUTED, true, false);
            cHead.addView(ic);

            TextView tView = cText(itTitle, 13, CLAUDE_TEXT_MAIN, true, false);
            cHead.addView(tView, new LinearLayout.LayoutParams(0, -2, 1));

            TextView bBadge = cText("✓ Done", 10.5f, CLAUDE_GREEN, true, false);
            bBadge.setBackground(cBox(CLAUDE_GREEN_BG, CLAUDE_GREEN, 1, 6));
            bBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
            cHead.addView(bBadge);
            card.addView(cHead);

            TextView body = new TextView(this);
            body.setText(content);
            body.setTextSize(12);
            body.setTextColor(CLAUDE_TEXT_MUTED);
            body.setTypeface(Typeface.MONOSPACE);
            body.setTextIsSelectable(true);
            body.setPadding(0, dp(6), 0, 0);
            card.addView(body);

            LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(-1, -2);
            lpC.setMargins(0, 0, 0, dp(10));
            list.addView(card, lpC);
        }

        scroll.addView(list);
        modalRoot.addView(scroll, new LinearLayout.LayoutParams(-1, dp(340)));

        dialog.setContentView(modalRoot);

        // Configure Bottom Sheet Dialog Window
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.gravity = Gravity.BOTTOM;
            wlp.width = WindowManager.LayoutParams.MATCH_PARENT;
            wlp.windowAnimations = android.R.style.Animation_InputMethod; // Smooth slide from bottom
            window.setAttributes(wlp);
        }

        dialog.show();
    }

    // Standard Message Card (User or Assistant)
    private void addMessageCard(String role, String content, String time) {
        boolean isUser = "user".equalsIgnoreCase(role);

        showEmptyMascotState(false);

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
    // MARKDOWN FORMATTER WITH NATIVE TABLES & CODE BLOCKS
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
                // Parse text lines and group markdown tables
                String text = sections[s];
                String[] lines = text.split("\n");
                ArrayList<String> tableBuffer = new ArrayList<>();

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    String trimmed = line.trim();

                    if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                        tableBuffer.add(trimmed);
                    } else {
                        if (!tableBuffer.isEmpty()) {
                            renderMarkdownTable(container, tableBuffer);
                            tableBuffer.clear();
                        }
                        renderMarkdownLine(container, line);
                    }
                }

                if (!tableBuffer.isEmpty()) {
                    renderMarkdownTable(container, tableBuffer);
                    tableBuffer.clear();
                }
            }
        }
    }

    private void renderMarkdownLine(LinearLayout container, String line) {
        if (line.trim().isEmpty()) return;

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

    private void renderMarkdownTable(LinearLayout container, ArrayList<String> tableLines) {
        if (tableLines.size() < 2) {
            for (String l : tableLines) {
                renderMarkdownLine(container, l);
            }
            return;
        }

        String headerLine = tableLines.get(0);
        String[] headers = splitTableRow(headerLine);
        int colCount = headers.length;
        if (colCount == 0) return;

        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.setHorizontalScrollBarEnabled(true);

        LinearLayout tableLayout = new LinearLayout(this);
        tableLayout.setOrientation(LinearLayout.VERTICAL);
        tableLayout.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_BORDER, 1, 10));
        tableLayout.setPadding(dp(1), dp(1), dp(1), dp(1));

        // Header Row
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setBackgroundColor(CLAUDE_SURFACE_MUTED);
        headerRow.setPadding(dp(8), dp(8), dp(8), dp(8));

        for (int c = 0; c < colCount; c++) {
            TextView cell = cText(headers[c], 12.5f, CLAUDE_TEXT_MAIN, true, false);
            cell.setPadding(dp(8), dp(4), dp(8), dp(4));
            cell.setMinWidth(dp(85));
            headerRow.addView(cell, new LinearLayout.LayoutParams(-2, -2));
        }
        tableLayout.addView(headerRow);

        // Data Rows
        for (int r = 1; r < tableLines.size(); r++) {
            String rowLine = tableLines.get(r);
            // Skip markdown delimiter line (e.g. |---|---|)
            if (rowLine.replace("|", "").replace("-", "").replace(":", "").replace(" ", "").isEmpty()) {
                continue;
            }

            String[] cells = splitTableRow(rowLine);

            View div = new View(this);
            div.setBackgroundColor(CLAUDE_BORDER);
            tableLayout.addView(div, new LinearLayout.LayoutParams(-1, dp(1)));

            LinearLayout dataRow = new LinearLayout(this);
            dataRow.setOrientation(LinearLayout.HORIZONTAL);
            dataRow.setBackgroundColor(r % 2 == 0 ? CLAUDE_SURFACE : Color.rgb(250, 250, 248));
            dataRow.setPadding(dp(8), dp(6), dp(8), dp(6));

            for (int c = 0; c < colCount; c++) {
                String val = c < cells.length ? cells[c] : "";
                SpannableStringBuilder span = parseInlineMarkdown(val);
                TextView cell = new TextView(this);
                cell.setText(span);
                cell.setTextSize(12.5f);
                cell.setTextColor(CLAUDE_TEXT_MAIN);
                cell.setPadding(dp(8), dp(4), dp(8), dp(4));
                cell.setMinWidth(dp(85));
                dataRow.addView(cell, new LinearLayout.LayoutParams(-2, -2));
            }
            tableLayout.addView(dataRow);
        }

        hScroll.addView(tableLayout);
        LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams(-1, -2);
        lpH.setMargins(0, dp(6), 0, dp(8));
        container.addView(hScroll, lpH);
    }

    private String[] splitTableRow(String row) {
        String trimmed = row.trim();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        String[] parts = trimmed.split("\\|");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
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
                mainHandler.post(() -> {
                    if (code == 200) {
                        if (sidebarStatusDot != null) sidebarStatusDot.setTextColor(CLAUDE_GREEN);
                        if (sidebarStatusText != null) sidebarStatusText.setText(" Gateway Online");
                        Toast.makeText(this, "Gateway online & connected!", Toast.LENGTH_SHORT).show();
                    } else {
                        if (sidebarStatusDot != null) sidebarStatusDot.setTextColor(CLAUDE_RED);
                        if (sidebarStatusText != null) sidebarStatusText.setText(" HTTP " + code);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (sidebarStatusDot != null) sidebarStatusDot.setTextColor(CLAUDE_RED);
                    if (sidebarStatusText != null) sidebarStatusText.setText(" Gateway Offline");
                    Toast.makeText(this, "Gateway offline: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showConnectionDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(10), dp(22), dp(10));

        // QR Scan Shortcut Button
        Button scanBtn = new Button(this);
        scanBtn.setText("📷 Scan QR Code dari Terminal");
        scanBtn.setTextColor(Color.WHITE);
        scanBtn.setTextSize(13.5f);
        scanBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        scanBtn.setBackground(cBox(CLAUDE_TERRACOTTA, 0, 0, 12));
        scanBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
        scanBtn.setOnClickListener(v -> startQrScanner());
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(-1, dp(44));
        lpBtn.setMargins(0, 0, 0, dp(16));
        form.addView(scanBtn, lpBtn);

        TextView orLbl = cText("— atau isi manual —", 11.5f, CLAUDE_TEXT_LIGHT, false, false);
        orLbl.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpOr = new LinearLayout.LayoutParams(-1, -2);
        lpOr.setMargins(0, 0, 0, dp(12));
        form.addView(orLbl, lpOr);

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
