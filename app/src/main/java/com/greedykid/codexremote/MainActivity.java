package com.greedykid.codexremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
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

import org.json.JSONArray;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    // Google Material Design 3 (M3) Dark Theme Color Tokens
    private static final int M3_SURFACE = Color.rgb(17, 19, 24);                // #111318
    private static final int M3_SURFACE_CONTAINER_LOW = Color.rgb(25, 28, 32);  // #191C20
    private static final int M3_SURFACE_CONTAINER = Color.rgb(29, 32, 36);      // #1D2024
    private static final int M3_SURFACE_CONTAINER_HIGH = Color.rgb(40, 42, 47); // #282A2F
    private static final int M3_SURFACE_CONTAINER_HIGHEST = Color.rgb(51, 53, 58); // #33353A

    private static final int M3_PRIMARY = Color.rgb(168, 199, 250);             // #A8C7FA Google Blue
    private static final int M3_ON_PRIMARY = Color.rgb(6, 46, 111);             // #062E6F
    private static final int M3_PRIMARY_CONTAINER = Color.rgb(8, 66, 160);      // #0842A0
    private static final int M3_ON_PRIMARY_CONTAINER = Color.rgb(211, 227, 253);// #D3E3FD

    private static final int M3_SECONDARY = Color.rgb(194, 231, 255);           // #C2E7FF
    private static final int M3_SECONDARY_CONTAINER = Color.rgb(0, 74, 119);    // #004A77
    private static final int M3_ON_SECONDARY_CONTAINER = Color.rgb(194, 231, 255);

    private static final int M3_TERTIARY = Color.rgb(245, 184, 75);             // #F5B84B Codex Amber
    private static final int M3_TERTIARY_CONTAINER = Color.rgb(67, 44, 0);      // #432C00
    private static final int M3_ON_TERTIARY_CONTAINER = Color.rgb(255, 223, 158);

    private static final int M3_OUTLINE = Color.rgb(140, 145, 153);             // #8C9199
    private static final int M3_OUTLINE_VARIANT = Color.rgb(68, 71, 78);        // #44474E
    private static final int M3_ON_SURFACE = Color.rgb(226, 226, 233);          // #E2E2E9
    private static final int M3_ON_SURFACE_VARIANT = Color.rgb(196, 199, 208);  // #C4C7D0
    private static final int M3_TEXT_MUTED = Color.rgb(142, 145, 153);        // #8E9199

    private static final int M3_GREEN = Color.rgb(109, 213, 140);               // #6DD58C
    private static final int M3_RED = Color.rgb(242, 184, 181);                 // #F2B8B5

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    // UI Tab Views
    private LinearLayout tabChatContainer;
    private LinearLayout tabMonitorContainer;
    private LinearLayout tabHistoryContainer;

    // Top Navigation buttons
    private Button navBtnChat;
    private Button navBtnMonitor;
    private Button navBtnHistory;

    // Chat Tab Components
    private LinearLayout transcript;
    private ScrollView scrollChat;
    private EditText promptInput;
    private Button sendButton;
    private Button btnEngineAgy;
    private Button btnEngineCodex;
    private LinearLayout emptyStateView;
    private ProgressBar sendProgress;

    // Monitor Tab Components
    private TextView monitorStatusDot;
    private TextView monitorStatusText;
    private TextView monitorPidText;
    private TextView monitorCpuText;
    private TextView monitorMemText;
    private TextView monitorUptimeText;
    private TextView monitorSessionTitle;
    private TextView monitorSessionId;
    private LinearLayout monitorTurnsList;
    private ScrollView monitorScroll;
    private Button monitorAutoRefreshBtn;
    private Button monitorStopBtn;
    private EditText monitorPromptInput;
    private Button monitorSendBtn;
    private ProgressBar monitorSendProgress;
    private String activeConversationId = null;

    private boolean isAutoRefreshActive = false;
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAutoRefreshActive) {
                fetchLiveMonitorData(false);
                mainHandler.postDelayed(this, 3000);
            }
        }
    };

    // History Tab Components
    private LinearLayout historyListContainer;
    private ProgressBar historyLoadingProgress;

    // Telemetry & Global State
    private TextView statusDot;
    private TextView statusText;
    private String currentEngine = "antigravity";
    private int currentTabIndex = 0; // 0: Chat, 1: Monitor, 2: History

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(M3_SURFACE);
        getWindow().setNavigationBarColor(M3_SURFACE);
        prefs = getSharedPreferences("connection", MODE_PRIVATE);
        currentEngine = prefs.getString("engine", "antigravity");
        buildM3Layout();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentTabIndex == 1 && isAutoRefreshActive) {
            startAutoRefresh();
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable m3Box(int fillColor, int borderColor, int borderWidthDp, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        if (borderWidthDp > 0 && borderColor != 0) {
            d.setStroke(dp(borderWidthDp), borderColor);
        }
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private TextView m3Text(String text, float sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) {
            v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return v;
    }

    private void buildM3Layout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(M3_SURFACE);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));

        // 1. M3 Top App Bar (Remote Control Header)
        LinearLayout topAppBar = new LinearLayout(this);
        topAppBar.setOrientation(LinearLayout.HORIZONTAL);
        topAppBar.setGravity(Gravity.CENTER_VERTICAL);
        topAppBar.setPadding(0, dp(4), 0, dp(10));

        LinearLayout brandCol = new LinearLayout(this);
        brandCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = m3Text("Antigravity Remote", 17, M3_ON_SURFACE, true);
        brandCol.addView(title);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = m3Text("●", 10, M3_GREEN, false);
        statusRow.addView(statusDot);
        statusText = m3Text(" Live Remote Control", 11, M3_ON_SURFACE_VARIANT, false);
        statusRow.addView(statusText);
        brandCol.addView(statusRow);
        topAppBar.addView(brandCol, new LinearLayout.LayoutParams(0, -2, 1));

        Button refreshBtn = createM3IconBtn("⟳");
        refreshBtn.setOnClickListener(v -> handleGlobalRefresh());
        LinearLayout.LayoutParams lpRef = new LinearLayout.LayoutParams(dp(40), dp(40));
        lpRef.setMargins(0, 0, dp(8), 0);
        topAppBar.addView(refreshBtn, lpRef);

        Button settingsBtn = createM3IconBtn("⚙");
        settingsBtn.setOnClickListener(v -> showConnectionDialog());
        topAppBar.addView(settingsBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(topAppBar);

        // 2. M3 Segmented Navigation Bar (Tabs: Chat | Live Monitor | History)
        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setBackground(m3Box(M3_SURFACE_CONTAINER, M3_OUTLINE_VARIANT, 1, 24));
        navBar.setPadding(dp(4), dp(4), dp(4), dp(4));

        navBtnChat = createM3NavTab("💬 Chat", 0);
        navBtnMonitor = createM3NavTab("⚡ Monitor & Control", 1);
        navBtnHistory = createM3NavTab("📜 History", 2);

        navBar.addView(navBtnChat, new LinearLayout.LayoutParams(0, dp(38), 1));
        navBar.addView(navBtnMonitor, new LinearLayout.LayoutParams(0, dp(38), 1));
        navBar.addView(navBtnHistory, new LinearLayout.LayoutParams(0, dp(38), 1));
        root.addView(navBar);

        // 3. Tab Containers (Chat, Monitor, History)
        tabChatContainer = new LinearLayout(this);
        tabChatContainer.setOrientation(LinearLayout.VERTICAL);
        buildChatTab(tabChatContainer);
        root.addView(tabChatContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        tabMonitorContainer = new LinearLayout(this);
        tabMonitorContainer.setOrientation(LinearLayout.VERTICAL);
        tabMonitorContainer.setVisibility(View.GONE);
        buildMonitorTab(tabMonitorContainer);
        root.addView(tabMonitorContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        tabHistoryContainer = new LinearLayout(this);
        tabHistoryContainer.setOrientation(LinearLayout.VERTICAL);
        tabHistoryContainer.setVisibility(View.GONE);
        buildHistoryTab(tabHistoryContainer);
        root.addView(tabHistoryContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        switchTab(0);
        updateEngineUi();
    }

    private Button createM3IconBtn(String symbol) {
        Button b = new Button(this);
        b.setText(symbol);
        b.setTextSize(16);
        b.setTextColor(M3_PRIMARY);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(m3Box(M3_SURFACE_CONTAINER_HIGH, M3_OUTLINE_VARIANT, 1, 20));
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private Button createM3NavTab(String title, final int index) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(12.5f);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setOnClickListener(v -> switchTab(index));
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private void switchTab(int index) {
        currentTabIndex = index;
        navBtnChat.setBackground(m3Box(index == 0 ? M3_PRIMARY_CONTAINER : Color.TRANSPARENT, 0, 0, 20));
        navBtnChat.setTextColor(index == 0 ? M3_ON_PRIMARY_CONTAINER : M3_ON_SURFACE_VARIANT);

        navBtnMonitor.setBackground(m3Box(index == 1 ? M3_PRIMARY_CONTAINER : Color.TRANSPARENT, 0, 0, 20));
        navBtnMonitor.setTextColor(index == 1 ? M3_ON_PRIMARY_CONTAINER : M3_ON_SURFACE_VARIANT);

        navBtnHistory.setBackground(m3Box(index == 2 ? M3_PRIMARY_CONTAINER : Color.TRANSPARENT, 0, 0, 20));
        navBtnHistory.setTextColor(index == 2 ? M3_ON_PRIMARY_CONTAINER : M3_ON_SURFACE_VARIANT);

        tabChatContainer.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        tabMonitorContainer.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        tabHistoryContainer.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        if (index == 1) {
            fetchLiveMonitorData(true);
        } else if (index == 2) {
            stopAutoRefresh();
            fetchHistorySessions();
        } else {
            stopAutoRefresh();
        }
    }

    // ==========================================
    // TAB 1: PROMPT CHAT
    // ==========================================
    private void buildChatTab(LinearLayout parent) {
        // Engine Selector Pill (Antigravity | Codex)
        LinearLayout enginePill = new LinearLayout(this);
        enginePill.setOrientation(LinearLayout.HORIZONTAL);
        enginePill.setBackground(m3Box(M3_SURFACE_CONTAINER_LOW, M3_OUTLINE_VARIANT, 1, 14));
        enginePill.setPadding(dp(3), dp(3), dp(3), dp(3));
        LinearLayout.LayoutParams lpEng = new LinearLayout.LayoutParams(-1, -2);
        lpEng.setMargins(0, dp(10), 0, dp(6));

        btnEngineAgy = createEngineToggle("⚡ Antigravity");
        btnEngineAgy.setOnClickListener(v -> setEngine("antigravity"));
        enginePill.addView(btnEngineAgy, new LinearLayout.LayoutParams(0, dp(34), 1));

        btnEngineCodex = createEngineToggle("🚀 OpenAI Codex");
        btnEngineCodex.setOnClickListener(v -> setEngine("codex"));
        enginePill.addView(btnEngineCodex, new LinearLayout.LayoutParams(0, dp(34), 1));
        parent.addView(enginePill, lpEng);

        // Chat Transcript Scroll Area
        scrollChat = new ScrollView(this);
        scrollChat.setFillViewport(true);
        scrollChat.setVerticalScrollBarEnabled(false);

        transcript = new LinearLayout(this);
        transcript.setOrientation(LinearLayout.VERTICAL);
        transcript.setGravity(Gravity.BOTTOM);

        buildEmptyState();
        transcript.addView(emptyStateView);

        scrollChat.addView(transcript);
        parent.addView(scrollChat, new LinearLayout.LayoutParams(-1, 0, 1));

        // Quick Suggestion Chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setPadding(0, dp(4), 0, dp(6));
        LinearLayout chipBox = new LinearLayout(this);
        chipBox.setOrientation(LinearLayout.HORIZONTAL);

        addM3Chip(chipBox, "📁 List project files", "List all project files in the current workspace");
        addM3Chip(chipBox, "🔍 Check git diff", "Show current git status and recent changes");
        addM3Chip(chipBox, "⚡ Build status", "Check active build and test status");
        addM3Chip(chipBox, "💡 Code review", "Review recent commits in this repository");
        chipScroll.addView(chipBox);
        parent.addView(chipScroll);

        // M3 Pill Composer
        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setBackground(m3Box(M3_SURFACE_CONTAINER_HIGH, M3_OUTLINE_VARIANT, 1, 28));
        composer.setPadding(dp(14), dp(4), dp(6), dp(4));

        promptInput = new EditText(this);
        promptInput.setHint("Ask Antigravity CLI...");
        promptInput.setHintTextColor(M3_TEXT_MUTED);
        promptInput.setTextColor(M3_ON_SURFACE);
        promptInput.setTextSize(14);
        promptInput.setBackground(null);
        promptInput.setMinLines(1);
        promptInput.setMaxLines(4);
        promptInput.setSingleLine(false);
        composer.addView(promptInput, new LinearLayout.LayoutParams(0, -2, 1));

        sendButton = new Button(this);
        sendButton.setText("➔");
        sendButton.setTextSize(18);
        sendButton.setTextColor(M3_ON_PRIMARY);
        sendButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sendButton.setBackground(m3Box(M3_PRIMARY, 0, 0, 22));
        sendButton.setOnClickListener(v -> sendPrompt());
        composer.addView(sendButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        parent.addView(composer);
    }

    private Button createEngineToggle(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private void addM3Chip(LinearLayout container, String label, final String prompt) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setTextColor(M3_ON_SURFACE_VARIANT);
        chip.setBackground(m3Box(M3_SURFACE_CONTAINER, M3_OUTLINE_VARIANT, 1, 16));
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        chip.setOnClickListener(v -> {
            promptInput.setText(prompt);
            promptInput.setSelection(prompt.length());
            promptInput.requestFocus();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(6), 0);
        container.addView(chip, lp);
    }

    private void buildEmptyState() {
        emptyStateView = new LinearLayout(this);
        emptyStateView.setOrientation(LinearLayout.VERTICAL);
        emptyStateView.setGravity(Gravity.CENTER);
        emptyStateView.setPadding(dp(20), dp(36), dp(20), dp(36));

        TextView icon = m3Text("✨", 34, M3_PRIMARY, true);
        icon.setGravity(Gravity.CENTER);
        emptyStateView.addView(icon);

        TextView title = m3Text("Antigravity & Codex Console", 16, M3_ON_SURFACE, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams(-1, -2);
        lpT.setMargins(0, dp(8), 0, dp(4));
        emptyStateView.addView(title, lpT);

        TextView desc = m3Text("Send instructions to your remote CLI agent, or open the Live Monitor tab to inspect active execution and continue chatting.", 13, M3_ON_SURFACE_VARIANT, false);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(0, 1.15f);
        emptyStateView.addView(desc);
    }

    // ==========================================
    // TAB 2: LIVE MONITOR & ACTIVE REMOTE CONTROL
    // ==========================================
    private void buildMonitorTab(LinearLayout parent) {
        monitorScroll = new ScrollView(this);
        monitorScroll.setFillViewport(true);
        monitorScroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(10), 0, dp(10));

        // 1. Process Telemetry M3 Card
        LinearLayout procCard = new LinearLayout(this);
        procCard.setOrientation(LinearLayout.VERTICAL);
        procCard.setBackground(m3Box(M3_SURFACE_CONTAINER, M3_OUTLINE_VARIANT, 1, 16));
        procCard.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout procHeader = new LinearLayout(this);
        procHeader.setOrientation(LinearLayout.HORIZONTAL);
        procHeader.setGravity(Gravity.CENTER_VERTICAL);

        monitorStatusDot = m3Text("●", 12, M3_GREEN, false);
        procHeader.addView(monitorStatusDot);

        monitorStatusText = m3Text(" Antigravity Process Running", 13.5f, M3_ON_SURFACE, true);
        procHeader.addView(monitorStatusText, new LinearLayout.LayoutParams(0, -2, 1));

        monitorAutoRefreshBtn = new Button(this);
        monitorAutoRefreshBtn.setText("Live Auto: OFF");
        monitorAutoRefreshBtn.setTextSize(11);
        monitorAutoRefreshBtn.setAllCaps(false);
        monitorAutoRefreshBtn.setTextColor(M3_ON_SURFACE_VARIANT);
        monitorAutoRefreshBtn.setBackground(m3Box(M3_SURFACE_CONTAINER_HIGH, M3_OUTLINE_VARIANT, 1, 12));
        monitorAutoRefreshBtn.setPadding(dp(8), dp(2), dp(8), dp(2));
        monitorAutoRefreshBtn.setOnClickListener(v -> toggleAutoRefresh());
        procHeader.addView(monitorAutoRefreshBtn, new LinearLayout.LayoutParams(-2, dp(32)));
        procCard.addView(procHeader);

        // Metrics Grid (PID, CPU, MEM, Uptime)
        LinearLayout metricsRow = new LinearLayout(this);
        metricsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpMet = new LinearLayout.LayoutParams(-1, -2);
        lpMet.setMargins(0, dp(10), 0, 0);

        monitorPidText = addMetricPill(metricsRow, "PID", "--");
        monitorCpuText = addMetricPill(metricsRow, "CPU", "--");
        monitorMemText = addMetricPill(metricsRow, "MEM", "--");
        monitorUptimeText = addMetricPill(metricsRow, "TIME", "--");
        procCard.addView(metricsRow, lpMet);
        content.addView(procCard);

        // 2. Active Conversation Session Card with Remote Control Actions
        LinearLayout sessionCard = new LinearLayout(this);
        sessionCard.setOrientation(LinearLayout.VERTICAL);
        sessionCard.setBackground(m3Box(M3_SURFACE_CONTAINER_LOW, M3_OUTLINE_VARIANT, 1, 16));
        sessionCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lpSess = new LinearLayout.LayoutParams(-1, -2);
        lpSess.setMargins(0, dp(10), 0, dp(10));

        LinearLayout sessTop = new LinearLayout(this);
        sessTop.setOrientation(LinearLayout.HORIZONTAL);
        sessTop.setGravity(Gravity.CENTER_VERTICAL);

        TextView sessHeader = m3Text("CURRENT SESSION CONTEXT", 11.5f, M3_PRIMARY, true);
        sessHeader.setLetterSpacing(0.04f);
        sessTop.addView(sessHeader, new LinearLayout.LayoutParams(0, -2, 1));

        monitorStopBtn = new Button(this);
        monitorStopBtn.setText("🛑 Stop CLI");
        monitorStopBtn.setTextSize(11);
        monitorStopBtn.setAllCaps(false);
        monitorStopBtn.setTextColor(M3_RED);
        monitorStopBtn.setBackground(m3Box(M3_SURFACE_CONTAINER_HIGH, M3_RED, 1, 10));
        monitorStopBtn.setPadding(dp(6), 0, dp(6), 0);
        monitorStopBtn.setOnClickListener(v -> stopRunningCliProcess());
        sessTop.addView(monitorStopBtn, new LinearLayout.LayoutParams(-2, dp(28)));

        sessionCard.addView(sessTop);

        monitorSessionTitle = m3Text("Loading latest session...", 14, M3_ON_SURFACE, true);
        LinearLayout.LayoutParams lpST = new LinearLayout.LayoutParams(-1, -2);
        lpST.setMargins(0, dp(4), 0, dp(4));
        sessionCard.addView(monitorSessionTitle, lpST);

        monitorSessionId = m3Text("ID: --", 11.5f, M3_ON_SURFACE_VARIANT, false);
        sessionCard.addView(monitorSessionId);
        content.addView(sessionCard, lpSess);

        // 3. Live Turn Stream Header
        LinearLayout turnsHeader = new LinearLayout(this);
        turnsHeader.setOrientation(LinearLayout.HORIZONTAL);
        turnsHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView liveTitle = m3Text("Live Conversation Transcript", 14, M3_ON_SURFACE, true);
        turnsHeader.addView(liveTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView refreshLink = m3Text("Refresh Now ➔", 12, M3_PRIMARY, true);
        refreshLink.setOnClickListener(v -> fetchLiveMonitorData(true));
        turnsHeader.addView(refreshLink);
        content.addView(turnsHeader);

        // 4. Live Turns List
        monitorTurnsList = new LinearLayout(this);
        monitorTurnsList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpTL = new LinearLayout.LayoutParams(-1, -2);
        lpTL.setMargins(0, dp(8), 0, dp(12));
        content.addView(monitorTurnsList, lpTL);

        monitorScroll.addView(content);
        parent.addView(monitorScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // 5. Active Session Remote Chat Bar (Continue Chat in Active Session)
        LinearLayout activeComposer = new LinearLayout(this);
        activeComposer.setOrientation(LinearLayout.HORIZONTAL);
        activeComposer.setGravity(Gravity.CENTER_VERTICAL);
        activeComposer.setBackground(m3Box(M3_SURFACE_CONTAINER_HIGH, M3_PRIMARY, 1, 28));
        activeComposer.setPadding(dp(14), dp(4), dp(6), dp(4));

        monitorPromptInput = new EditText(this);
        monitorPromptInput.setHint("Reply to active Antigravity session...");
        monitorPromptInput.setHintTextColor(M3_TEXT_MUTED);
        monitorPromptInput.setTextColor(M3_ON_SURFACE);
        monitorPromptInput.setTextSize(14);
        monitorPromptInput.setBackground(null);
        monitorPromptInput.setMinLines(1);
        monitorPromptInput.setMaxLines(4);
        monitorPromptInput.setSingleLine(false);
        activeComposer.addView(monitorPromptInput, new LinearLayout.LayoutParams(0, -2, 1));

        monitorSendBtn = new Button(this);
        monitorSendBtn.setText("➔");
        monitorSendBtn.setTextSize(18);
        monitorSendBtn.setTextColor(M3_ON_PRIMARY);
        monitorSendBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        monitorSendBtn.setBackground(m3Box(M3_PRIMARY, 0, 0, 22));
        monitorSendBtn.setOnClickListener(v -> sendActiveSessionPrompt());
        activeComposer.addView(monitorSendBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));

        parent.addView(activeComposer);
    }

    private TextView addMetricPill(LinearLayout row, String label, String value) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setBackground(m3Box(M3_SURFACE_CONTAINER_HIGH, 0, 0, 10));
        pill.setPadding(dp(8), dp(6), dp(8), dp(6));
        pill.setGravity(Gravity.CENTER);

        TextView lbl = m3Text(label, 10, M3_ON_SURFACE_VARIANT, true);
        pill.addView(lbl);
        TextView val = m3Text(value, 12, M3_PRIMARY, true);
        pill.addView(val);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(2), 0, dp(2), 0);
        row.addView(pill, lp);
        return val;
    }

    private void toggleAutoRefresh() {
        if (isAutoRefreshActive) {
            stopAutoRefresh();
            Toast.makeText(this, "Live auto-refresh paused", Toast.LENGTH_SHORT).show();
        } else {
            startAutoRefresh();
            Toast.makeText(this, "Live auto-refresh enabled (3s)", Toast.LENGTH_SHORT).show();
        }
    }

    private void startAutoRefresh() {
        isAutoRefreshActive = true;
        if (monitorAutoRefreshBtn != null) {
            monitorAutoRefreshBtn.setText("Live Auto: ON");
            monitorAutoRefreshBtn.setTextColor(M3_ON_PRIMARY_CONTAINER);
            monitorAutoRefreshBtn.setBackground(m3Box(M3_PRIMARY_CONTAINER, 0, 0, 12));
        }
        mainHandler.removeCallbacks(autoRefreshRunnable);
        mainHandler.post(autoRefreshRunnable);
    }

    private void stopAutoRefresh() {
        isAutoRefreshActive = false;
        if (monitorAutoRefreshBtn != null) {
            monitorAutoRefreshBtn.setText("Live Auto: OFF");
            monitorAutoRefreshBtn.setTextColor(M3_ON_SURFACE_VARIANT);
            monitorAutoRefreshBtn.setBackground(m3Box(M3_SURFACE_CONTAINER_HIGH, M3_OUTLINE_VARIANT, 1, 12));
        }
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
                    Toast.makeText(MainActivity.this, code == 200 ? "Stop signal sent" : "HTTP " + code, Toast.LENGTH_SHORT).show();
                    fetchLiveMonitorData(false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Error stopping: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void sendActiveSessionPrompt() {
        String text = monitorPromptInput.getText().toString().trim();
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) {
            showConnectionDialog();
            return;
        }
        if (text.isEmpty() || monitorSendBtn.getTag() != null) return;

        monitorSendBtn.setTag("busy");
        monitorSendBtn.setEnabled(false);
        monitorPromptInput.setEnabled(false);

        addTurnItemToMonitor("user", text, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        monitorPromptInput.setText("");

        monitorSendProgress = new ProgressBar(this);
        LinearLayout.LayoutParams lpProg = new LinearLayout.LayoutParams(dp(24), dp(24));
        lpProg.setMargins(dp(14), dp(4), 0, dp(10));
        monitorTurnsList.addView(monitorSendProgress, lpProg);
        monitorScroll.post(() -> monitorScroll.fullScroll(View.FOCUS_DOWN));

        executor.execute(() -> {
            try {
                JSONObject req = new JSONObject();
                req.put("prompt", text);
                req.put("engine", "antigravity");
                req.put("resume", true);
                if (activeConversationId != null && !activeConversationId.isEmpty()) {
                    req.put("conversationId", activeConversationId);
                }

                JSONObject res = executePost(endpoint, prefs.getString("token", ""), req);
                String responseText = res.optString("response", "No output returned.");

                mainHandler.post(() -> {
                    if (monitorSendProgress != null) {
                        monitorTurnsList.removeView(monitorSendProgress);
                        monitorSendProgress = null;
                    }
                    addTurnItemToMonitor("assistant", responseText, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
                    monitorSendBtn.setTag(null);
                    monitorSendBtn.setEnabled(true);
                    monitorPromptInput.setEnabled(true);
                    fetchLiveMonitorData(false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (monitorSendProgress != null) {
                        monitorTurnsList.removeView(monitorSendProgress);
                        monitorSendProgress = null;
                    }
                    addTurnItemToMonitor("tool", "Error: " + e.getMessage(), "");
                    monitorSendBtn.setTag(null);
                    monitorSendBtn.setEnabled(true);
                    monitorPromptInput.setEnabled(true);
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

    private void fetchLiveMonitorData(final boolean showFeedback) {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) return;

        executor.execute(() -> {
            try {
                String liveUrl = endpoint.replace("/api/chat", "/api/session/live");
                HttpURLConnection c = (HttpURLConnection) new URL(liveUrl).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
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

                    mainHandler.post(() -> renderLiveMonitor(json, showFeedback));
                }
            } catch (Exception e) {
                if (showFeedback) {
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, "Monitor fetch error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void renderLiveMonitor(JSONObject json, boolean showToast) {
        try {
            JSONObject proc = json.optJSONObject("process");
            boolean running = proc != null && proc.optBoolean("running", false);

            monitorStatusDot.setTextColor(running ? M3_GREEN : M3_RED);
            monitorStatusText.setText(running ? " Antigravity Process Running" : " Antigravity CLI Idle");

            if (proc != null && running) {
                monitorPidText.setText(proc.optString("pid", "--"));
                monitorCpuText.setText(proc.optString("cpu", "--"));
                monitorMemText.setText(proc.optString("mem", "--"));
                monitorUptimeText.setText(proc.optString("uptime", "--"));
            } else {
                monitorPidText.setText("--");
                monitorCpuText.setText("0%");
                monitorMemText.setText("0%");
                monitorUptimeText.setText("Idle");
            }

            JSONObject session = json.optJSONObject("session");
            if (session != null) {
                activeConversationId = session.optString("conversationId", "");
                monitorSessionTitle.setText(session.optString("title", "Active Task"));
                monitorSessionId.setText("ID: " + activeConversationId + "  •  " + session.optString("workspace", "/home/ubuntu"));
            }

            JSONArray turns = json.optJSONArray("turns");
            if (turns != null && monitorSendBtn.getTag() == null) {
                monitorTurnsList.removeAllViews();
                for (int i = 0; i < turns.length(); i++) {
                    JSONObject turn = turns.getJSONObject(i);
                    String role = turn.optString("role", "info");
                    String content = turn.optString("content", "");
                    String time = turn.optString("time", "");
                    addTurnItemToMonitor(role, content, time);
                }
            }

            if (showToast) {
                Toast.makeText(this, "Telemetry refreshed", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addTurnItemToMonitor(String role, String content, String time) {
        LinearLayout turnCard = new LinearLayout(this);
        turnCard.setOrientation(LinearLayout.VERTICAL);

        int bgColor;
        int borderColor;
        int accentColor;
        String roleLabel;

        if ("user".equalsIgnoreCase(role)) {
            bgColor = M3_PRIMARY_CONTAINER;
            borderColor = M3_PRIMARY;
            accentColor = M3_ON_PRIMARY_CONTAINER;
            roleLabel = "👤 You (Live Session)";
        } else if ("tool".equalsIgnoreCase(role)) {
            bgColor = M3_SURFACE_CONTAINER;
            borderColor = M3_OUTLINE_VARIANT;
            accentColor = M3_SECONDARY;
            roleLabel = "🛠 CLI Execution";
        } else {
            bgColor = M3_SURFACE_CONTAINER_LOW;
            borderColor = M3_OUTLINE_VARIANT;
            accentColor = M3_TERTIARY;
            roleLabel = "⚡ Antigravity Output";
        }

        turnCard.setBackground(m3Box(bgColor, borderColor, 1, 14));
        turnCard.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView rView = m3Text(roleLabel, 12, accentColor, true);
        head.addView(rView, new LinearLayout.LayoutParams(0, -2, 1));

        String shortTime = time.contains("T") && time.length() >= 16 ? time.substring(11, 16) : time;
        TextView tView = m3Text(shortTime, 11, M3_TEXT_MUTED, false);
        head.addView(tView);
        turnCard.addView(head);

        LinearLayout markdownBody = new LinearLayout(this);
        markdownBody.setOrientation(LinearLayout.VERTICAL);
        markdownBody.setPadding(0, dp(4), 0, 0);
        renderMarkdownBlocks(markdownBody, content, M3_ON_SURFACE);
        turnCard.addView(markdownBody);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        monitorTurnsList.addView(turnCard, lp);
    }

    // ==========================================
    // TAB 3: SESSION HISTORY
    // ==========================================
    private void buildHistoryTab(LinearLayout parent) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(10), 0, dp(10));

        LinearLayout histHead = new LinearLayout(this);
        histHead.setOrientation(LinearLayout.HORIZONTAL);
        histHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView hTitle = m3Text("Conversation History", 15, M3_ON_SURFACE, true);
        histHead.addView(hTitle, new LinearLayout.LayoutParams(0, -2, 1));

        TextView refreshLink = m3Text("Refresh ⟳", 13, M3_PRIMARY, true);
        refreshLink.setOnClickListener(v -> fetchHistorySessions());
        histHead.addView(refreshLink);
        content.addView(histHead);

        historyLoadingProgress = new ProgressBar(this);
        content.addView(historyLoadingProgress, new LinearLayout.LayoutParams(dp(32), dp(32)));

        historyListContainer = new LinearLayout(this);
        historyListContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpHC = new LinearLayout.LayoutParams(-1, -2);
        lpHC.setMargins(0, dp(8), 0, dp(16));
        content.addView(historyListContainer, lpHC);

        scroll.addView(content);
        parent.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    private void fetchHistorySessions() {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) return;

        if (historyLoadingProgress != null) historyLoadingProgress.setVisibility(View.VISIBLE);
        if (historyListContainer != null) historyListContainer.removeAllViews();

        executor.execute(() -> {
            try {
                String sessionsUrl = endpoint.replace("/api/chat", "/api/sessions");
                HttpURLConnection c = (HttpURLConnection) new URL(sessionsUrl).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
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

                    mainHandler.post(() -> renderHistorySessions(sessions));
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (historyLoadingProgress != null) historyLoadingProgress.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Failed to load history: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void renderHistorySessions(JSONArray sessions) {
        if (historyLoadingProgress != null) historyLoadingProgress.setVisibility(View.GONE);
        if (historyListContainer == null) return;
        historyListContainer.removeAllViews();

        if (sessions == null || sessions.length() == 0) {
            TextView empty = m3Text("No saved conversation sessions found.", 13.5f, M3_ON_SURFACE_VARIANT, false);
            empty.setPadding(0, dp(24), 0, 0);
            historyListContainer.addView(empty);
            return;
        }

        SimpleDateFormat fmt = new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault());

        for (int i = 0; i < sessions.length(); i++) {
            try {
                JSONObject s = sessions.getJSONObject(i);
                final String convId = s.optString("conversationId", "");
                String title = s.optString("title", "Session");
                long ts = s.optLong("timestamp", System.currentTimeMillis());
                String timeStr = fmt.format(new Date(ts));

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(m3Box(M3_SURFACE_CONTAINER, M3_OUTLINE_VARIANT, 1, 14));
                card.setPadding(dp(14), dp(12), dp(14), dp(12));

                LinearLayout top = new LinearLayout(this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);

                TextView tag = m3Text("SESSION #" + (i + 1), 11, M3_PRIMARY, true);
                top.addView(tag, new LinearLayout.LayoutParams(0, -2, 1));

                TextView time = m3Text(timeStr, 11, M3_TEXT_MUTED, false);
                top.addView(time);
                card.addView(top);

                TextView titleV = m3Text(title, 14, M3_ON_SURFACE, true);
                LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams(-1, -2);
                lpT.setMargins(0, dp(4), 0, dp(6));
                card.addView(titleV, lpT);

                TextView idV = m3Text("ID: " + convId.substring(0, Math.min(16, convId.length())) + "...", 11.5f, M3_ON_SURFACE_VARIANT, false);
                card.addView(idV);

                card.setOnClickListener(v -> openSessionTranscriptDialog(convId, title));

                LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(-1, -2);
                lpCard.setMargins(0, 0, 0, dp(10));
                historyListContainer.addView(card, lpCard);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void openSessionTranscriptDialog(final String convId, String sessionTitle) {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) return;

        final AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setTitle("Loading Transcript...")
                .setMessage("Fetching full session chat history...")
                .create();
        loadingDialog.show();

        executor.execute(() -> {
            try {
                String transcriptUrl = endpoint.replace("/api/chat", "/api/session/transcript?id=" + convId);
                HttpURLConnection c = (HttpURLConnection) new URL(transcriptUrl).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
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
                    JSONArray msgs = json.optJSONArray("messages");

                    mainHandler.post(() -> {
                        loadingDialog.dismiss();
                        showTranscriptViewerModal(convId, sessionTitle, msgs);
                    });
                } else {
                    mainHandler.post(() -> {
                        loadingDialog.dismiss();
                        Toast.makeText(MainActivity.this, "HTTP error: " + code, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    loadingDialog.dismiss();
                    Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showTranscriptViewerModal(final String convId, String title, JSONArray msgs) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setPadding(dp(16), dp(10), dp(16), dp(10));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        if (msgs == null || msgs.length() == 0) {
            TextView empty = m3Text("No messages recorded in this session.", 13, M3_ON_SURFACE_VARIANT, false);
            container.addView(empty);
        } else {
            for (int i = 0; i < msgs.length(); i++) {
                try {
                    JSONObject m = msgs.getJSONObject(i);
                    String role = m.optString("role", "assistant");
                    String content = m.optString("content", "");
                    String time = m.optString("time", "");

                    LinearLayout card = new LinearLayout(this);
                    card.setOrientation(LinearLayout.VERTICAL);

                    boolean isUser = "user".equalsIgnoreCase(role);
                    boolean isTool = "tool".equalsIgnoreCase(role);

                    int bg = isUser ? M3_PRIMARY_CONTAINER : (isTool ? M3_SURFACE_CONTAINER_LOW : M3_SURFACE_CONTAINER);
                    int border = isUser ? M3_PRIMARY : M3_OUTLINE_VARIANT;
                    card.setBackground(m3Box(bg, border, 1, 12));
                    card.setPadding(dp(12), dp(8), dp(12), dp(8));

                    String label = isUser ? "You" : (isTool ? "Execution Tool" : "Antigravity CLI");
                    int color = isUser ? M3_ON_PRIMARY_CONTAINER : (isTool ? M3_SECONDARY : M3_PRIMARY);

                    TextView authorV = m3Text(label + "  •  " + (time.length() >= 16 ? time.substring(11, 16) : time), 11.5f, color, true);
                    card.addView(authorV);

                    LinearLayout mdBody = new LinearLayout(this);
                    mdBody.setOrientation(LinearLayout.VERTICAL);
                    mdBody.setPadding(0, dp(4), 0, 0);
                    renderMarkdownBlocks(mdBody, content, isUser ? M3_ON_PRIMARY_CONTAINER : M3_ON_SURFACE);
                    card.addView(mdBody);

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                    lp.setMargins(0, 0, 0, dp(8));
                    container.addView(card, lp);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        scroll.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton("Close", null)
                .setPositiveButton("💬 Continue Session ➔", null)
                .create();

        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            activeConversationId = convId;
            dialog.dismiss();
            switchTab(1);
            fetchLiveMonitorData(true);
            Toast.makeText(MainActivity.this, "Switched to session: " + convId.substring(0, 8), Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    // ==========================================
    // MARKDOWN RENDERING ENGINE (MATERIAL 3)
    // ==========================================
    private SpannableStringBuilder formatInlineSpans(String input, int defaultTextColor) {
        if (input == null) return new SpannableStringBuilder("");
        SpannableStringBuilder ssb = new SpannableStringBuilder(input);

        // 1. Links [label](url)
        Pattern linkPattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]+)\\)");
        Matcher linkMatcher = linkPattern.matcher(ssb.toString());
        while (linkMatcher.find()) {
            int start = linkMatcher.start();
            int end = linkMatcher.end();
            String label = linkMatcher.group(1);
            String url = linkMatcher.group(2);
            ssb.replace(start, end, label);
            ssb.setSpan(new URLSpan(url), start, start + label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(M3_PRIMARY), start, start + label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            linkMatcher = linkPattern.matcher(ssb.toString());
        }

        // 2. Inline Code `code`
        Pattern codePattern = Pattern.compile("`([^`]+)`");
        Matcher codeMatcher = codePattern.matcher(ssb.toString());
        while (codeMatcher.find()) {
            int start = codeMatcher.start();
            int end = codeMatcher.end();
            String codeText = codeMatcher.group(1);
            ssb.replace(start, end, " " + codeText + " ");
            int newEnd = start + codeText.length() + 2;
            ssb.setSpan(new TypefaceSpan("monospace"), start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new BackgroundColorSpan(M3_SURFACE_CONTAINER_HIGH), start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(M3_PRIMARY), start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new RelativeSizeSpan(0.92f), start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            codeMatcher = codePattern.matcher(ssb.toString());
        }

        // 3. Bold **bold**
        Pattern boldPattern = Pattern.compile("\\*\\*([^\\*]+)\\*\\*");
        Matcher boldMatcher = boldPattern.matcher(ssb.toString());
        while (boldMatcher.find()) {
            int start = boldMatcher.start();
            int end = boldMatcher.end();
            String boldText = boldMatcher.group(1);
            ssb.replace(start, end, boldText);
            ssb.setSpan(new StyleSpan(Typeface.BOLD), start, start + boldText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            boldMatcher = boldPattern.matcher(ssb.toString());
        }

        // 4. Italic *italic*
        Pattern italicPattern = Pattern.compile("(?<!\\*)\\*([^\\*]+)\\*(?!\\*)");
        Matcher italicMatcher = italicPattern.matcher(ssb.toString());
        while (italicMatcher.find()) {
            int start = italicMatcher.start();
            int end = italicMatcher.end();
            String italicText = italicMatcher.group(1);
            ssb.replace(start, end, italicText);
            ssb.setSpan(new StyleSpan(Typeface.ITALIC), start, start + italicText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            italicMatcher = italicPattern.matcher(ssb.toString());
        }

        return ssb;
    }

    private void renderMarkdownBlocks(LinearLayout targetLayout, String rawMarkdown, int defaultTextColor) {
        if (rawMarkdown == null || rawMarkdown.isEmpty()) return;

        String[] lines = rawMarkdown.split("\n");
        boolean inCode = false;
        String codeLang = "";
        StringBuilder codeBuffer = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (line.trim().startsWith("```")) {
                if (inCode) {
                    addCodeBlockView(targetLayout, codeLang, codeBuffer.toString().trim());
                    inCode = false;
                    codeLang = "";
                    codeBuffer.setLength(0);
                } else {
                    inCode = true;
                    codeLang = line.trim().length() > 3 ? line.trim().substring(3).trim() : "CODE";
                    codeBuffer.setLength(0);
                }
                continue;
            }

            if (inCode) {
                codeBuffer.append(line).append("\n");
                continue;
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("#")) {
                if (trimmed.startsWith("### ")) {
                    TextView h3 = new TextView(this);
                    h3.setText(formatInlineSpans(trimmed.substring(4), defaultTextColor));
                    h3.setTextSize(14.5f);
                    h3.setTextColor(M3_ON_SURFACE);
                    h3.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                    LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams(-1, -2);
                    lpH.setMargins(0, dp(6), 0, dp(2));
                    targetLayout.addView(h3, lpH);
                    continue;
                } else if (trimmed.startsWith("## ")) {
                    TextView h2 = new TextView(this);
                    h2.setText(formatInlineSpans(trimmed.substring(3), defaultTextColor));
                    h2.setTextSize(15.5f);
                    h2.setTextColor(M3_PRIMARY);
                    h2.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                    LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams(-1, -2);
                    lpH.setMargins(0, dp(8), 0, dp(4));
                    targetLayout.addView(h2, lpH);
                    continue;
                } else if (trimmed.startsWith("# ")) {
                    TextView h1 = new TextView(this);
                    h1.setText(formatInlineSpans(trimmed.substring(2), defaultTextColor));
                    h1.setTextSize(17f);
                    h1.setTextColor(M3_PRIMARY);
                    h1.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                    LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams(-1, -2);
                    lpH.setMargins(0, dp(10), 0, dp(4));
                    targetLayout.addView(h1, lpH);
                    continue;
                }
            }

            if (trimmed.equals("---") || trimmed.equals("***") || trimmed.equals("___")) {
                View divider = new View(this);
                divider.setBackgroundColor(M3_OUTLINE_VARIANT);
                LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(-1, dp(1));
                lpDiv.setMargins(0, dp(8), 0, dp(8));
                targetLayout.addView(divider, lpDiv);
                continue;
            }

            if (trimmed.startsWith(">")) {
                LinearLayout quoteBox = new LinearLayout(this);
                quoteBox.setOrientation(LinearLayout.HORIZONTAL);
                quoteBox.setBackground(m3Box(M3_SURFACE_CONTAINER_LOW, 0, 0, 8));
                quoteBox.setPadding(dp(10), dp(6), dp(10), dp(6));

                View bar = new View(this);
                bar.setBackgroundColor(M3_PRIMARY);
                quoteBox.addView(bar, new LinearLayout.LayoutParams(dp(3), -1));

                TextView qText = new TextView(this);
                qText.setText(formatInlineSpans(trimmed.substring(1).trim(), M3_ON_SURFACE_VARIANT));
                qText.setTextSize(13.5f);
                qText.setTextColor(M3_ON_SURFACE_VARIANT);
                qText.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
                qText.setPadding(dp(8), 0, 0, 0);
                qText.setTextIsSelectable(true);
                quoteBox.addView(qText, new LinearLayout.LayoutParams(0, -2, 1));

                LinearLayout.LayoutParams lpQ = new LinearLayout.LayoutParams(-1, -2);
                lpQ.setMargins(0, dp(4), 0, dp(4));
                targetLayout.addView(quoteBox, lpQ);
                continue;
            }

            if (trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ")) {
                LinearLayout bulletRow = new LinearLayout(this);
                bulletRow.setOrientation(LinearLayout.HORIZONTAL);

                TextView dot = m3Text("•", 14, M3_PRIMARY, true);
                bulletRow.addView(dot, new LinearLayout.LayoutParams(dp(14), -2));

                TextView itemText = new TextView(this);
                itemText.setText(formatInlineSpans(trimmed.substring(2).trim(), defaultTextColor));
                itemText.setTextSize(14);
                itemText.setTextColor(defaultTextColor);
                itemText.setLineSpacing(0, 1.2f);
                itemText.setTextIsSelectable(true);
                itemText.setMovementMethod(LinkMovementMethod.getInstance());
                bulletRow.addView(itemText, new LinearLayout.LayoutParams(0, -2, 1));

                LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(-1, -2);
                lpB.setMargins(dp(6), dp(2), 0, dp(2));
                targetLayout.addView(bulletRow, lpB);
                continue;
            }

            TextView para = new TextView(this);
            para.setText(formatInlineSpans(line, defaultTextColor));
            para.setTextSize(14);
            para.setTextColor(defaultTextColor);
            para.setLineSpacing(0, 1.2f);
            para.setTextIsSelectable(true);
            para.setMovementMethod(LinkMovementMethod.getInstance());

            LinearLayout.LayoutParams lpP = new LinearLayout.LayoutParams(-1, -2);
            lpP.setMargins(0, dp(2), 0, dp(4));
            targetLayout.addView(para, lpP);
        }

        if (inCode && codeBuffer.length() > 0) {
            addCodeBlockView(targetLayout, codeLang, codeBuffer.toString().trim());
        }
    }

    private void addCodeBlockView(LinearLayout parent, String lang, final String code) {
        LinearLayout codeCard = new LinearLayout(this);
        codeCard.setOrientation(LinearLayout.VERTICAL);
        codeCard.setBackground(m3Box(M3_SURFACE_CONTAINER_LOW, M3_OUTLINE_VARIANT, 1, 12));
        codeCard.setPadding(0, 0, 0, 0);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(m3Box(M3_SURFACE_CONTAINER_HIGH, 0, 0, 12));
        header.setPadding(dp(12), dp(4), dp(8), dp(4));

        String displayLang = lang.isEmpty() ? "CODE" : lang.toUpperCase();
        TextView langLabel = m3Text(displayLang, 11, M3_ON_SURFACE_VARIANT, true);
        header.addView(langLabel, new LinearLayout.LayoutParams(0, -2, 1));

        Button copyBtn = new Button(this);
        copyBtn.setText("📋 Copy");
        copyBtn.setTextSize(11);
        copyBtn.setAllCaps(false);
        copyBtn.setTextColor(M3_PRIMARY);
        copyBtn.setBackground(null);
        copyBtn.setPadding(dp(6), 0, dp(6), 0);
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData cd = ClipData.newPlainText("Code", code);
            cm.setPrimaryClip(cd);
            Toast.makeText(MainActivity.this, "Code copied to clipboard", Toast.LENGTH_SHORT).show();
        });
        header.addView(copyBtn, new LinearLayout.LayoutParams(-2, dp(30)));
        codeCard.addView(header);

        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.setPadding(dp(12), dp(8), dp(12), dp(10));
        hScroll.setHorizontalScrollBarEnabled(false);

        TextView codeView = new TextView(this);
        codeView.setText(code);
        codeView.setTextSize(12.5f);
        codeView.setTextColor(M3_PRIMARY);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setLineSpacing(0, 1.15f);
        codeView.setTextIsSelectable(true);
        hScroll.addView(codeView);

        codeCard.addView(hScroll);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(6));
        parent.addView(codeCard, lp);
    }

    // ==========================================
    // ACTIONS & COMMUNICATIONS
    // ==========================================
    private void setEngine(String engine) {
        currentEngine = engine;
        prefs.edit().putString("engine", engine).apply();
        updateEngineUi();
    }

    private void updateEngineUi() {
        boolean isAgy = "antigravity".equalsIgnoreCase(currentEngine);
        btnEngineAgy.setBackground(m3Box(isAgy ? M3_PRIMARY : Color.TRANSPARENT, 0, 0, 12));
        btnEngineAgy.setTextColor(isAgy ? M3_ON_PRIMARY : M3_ON_SURFACE_VARIANT);

        btnEngineCodex.setBackground(m3Box(!isAgy ? M3_TERTIARY : Color.TRANSPARENT, 0, 0, 12));
        btnEngineCodex.setTextColor(!isAgy ? M3_ON_TERTIARY_CONTAINER : M3_ON_SURFACE_VARIANT);

        promptInput.setHint("Ask " + (isAgy ? "Antigravity" : "Codex") + " CLI...");
        boolean configured = isConfigured();
        statusDot.setTextColor(configured ? M3_GREEN : M3_RED);
        statusText.setText(configured ? (" Ready (" + (isAgy ? "Antigravity" : "Codex") + ")") : " Offline");
    }

    private boolean isConfigured() {
        return !prefs.getString("url", "").trim().isEmpty();
    }

    private void handleGlobalRefresh() {
        if (currentTabIndex == 0) {
            checkHealth();
        } else if (currentTabIndex == 1) {
            fetchLiveMonitorData(true);
        } else {
            fetchHistorySessions();
        }
    }

    private void checkHealth() {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) {
            showConnectionDialog();
            return;
        }
        statusText.setText(" Checking gateway...");
        executor.execute(() -> {
            try {
                String healthUrl = endpoint.replace("/api/chat", "/health");
                HttpURLConnection c = (HttpURLConnection) new URL(healthUrl).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(5000);
                int code = c.getResponseCode();
                mainHandler.post(() -> {
                    if (code == 200) {
                        statusDot.setTextColor(M3_GREEN);
                        statusText.setText(" Live (" + ("antigravity".equalsIgnoreCase(currentEngine) ? "Antigravity" : "Codex") + ")");
                        Toast.makeText(this, "Gateway online & healthy!", Toast.LENGTH_SHORT).show();
                    } else {
                        statusDot.setTextColor(M3_RED);
                        statusText.setText(" HTTP " + code);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    statusDot.setTextColor(M3_RED);
                    statusText.setText(" Offline");
                });
            }
        });
    }

    private void showConnectionDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(10), dp(22), dp(10));

        TextView urlLbl = m3Text("Bridge Endpoint URL:", 12.5f, M3_ON_SURFACE_VARIANT, true);
        form.addView(urlLbl);

        EditText urlInput = new EditText(this);
        urlInput.setHint("https://your-bridge.trycloudflare.com/api/chat");
        urlInput.setText(prefs.getString("url", ""));
        urlInput.setTextColor(M3_ON_SURFACE);
        urlInput.setHintTextColor(M3_TEXT_MUTED);
        urlInput.setTextSize(14);
        urlInput.setBackground(m3Box(M3_SURFACE_CONTAINER_HIGH, M3_OUTLINE_VARIANT, 1, 10));
        urlInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpUrl = new LinearLayout.LayoutParams(-1, dp(48));
        lpUrl.setMargins(0, dp(4), 0, dp(14));
        form.addView(urlInput, lpUrl);

        TextView tokLbl = m3Text("Bearer Token (Secret):", 12.5f, M3_ON_SURFACE_VARIANT, true);
        form.addView(tokLbl);

        EditText tokenInput = new EditText(this);
        tokenInput.setHint("codex-remote-token-2026");
        tokenInput.setText(prefs.getString("token", ""));
        tokenInput.setTextColor(M3_ON_SURFACE);
        tokenInput.setHintTextColor(M3_TEXT_MUTED);
        tokenInput.setTextSize(14);
        tokenInput.setInputType(0x00000081);
        tokenInput.setBackground(m3Box(M3_SURFACE_CONTAINER_HIGH, M3_OUTLINE_VARIANT, 1, 10));
        tokenInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpTok = new LinearLayout.LayoutParams(-1, dp(48));
        lpTok.setMargins(0, dp(4), 0, dp(12));
        form.addView(tokenInput, lpTok);

        TextView updateLbl = m3Text("App Version & Updates:", 12.5f, M3_ON_SURFACE_VARIANT, true);
        form.addView(updateLbl);

        Button updateBtn = new Button(this);
        updateBtn.setText("📥 Check & Download Latest APK Update");
        updateBtn.setTextSize(12);
        updateBtn.setAllCaps(false);
        updateBtn.setTextColor(M3_PRIMARY);
        updateBtn.setBackground(m3Box(M3_SURFACE_CONTAINER_HIGH, M3_OUTLINE_VARIANT, 1, 10));
        updateBtn.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/greedykid/codexcli-remote-app/actions"));
            startActivity(browserIntent);
        });
        LinearLayout.LayoutParams lpUp = new LinearLayout.LayoutParams(-1, dp(42));
        lpUp.setMargins(0, dp(4), 0, dp(6));
        form.addView(updateBtn, lpUp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Remote Gateway Setup")
                .setMessage("Enter Cloudflare HTTPS URL or Tailscale address:")
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
        statusDot.setTextColor(isAgy ? M3_PRIMARY : M3_TERTIARY);
        statusText.setText(" " + engineLabel + " processing...");

        addMessage("You", text, true, null);
        promptInput.setText("");

        sendProgress = new ProgressBar(this);
        LinearLayout.LayoutParams lpProg = new LinearLayout.LayoutParams(dp(24), dp(24));
        lpProg.setMargins(dp(14), dp(4), 0, dp(10));
        transcript.addView(sendProgress, lpProg);
        scrollChat.post(() -> scrollChat.fullScroll(View.FOCUS_DOWN));

        executor.execute(() -> {
            try {
                JSONObject req = new JSONObject();
                req.put("prompt", text);
                req.put("engine", engine);
                req.put("resume", true);

                JSONObject res = executePost(endpoint, prefs.getString("token", ""), req);
                String responseText = res.optString("response", "No output returned.");
                String resEngine = res.optString("engine", engine);
                mainHandler.post(() -> finishResponse(responseText, false, resEngine));
            } catch (Exception e) {
                String errMsg = e.getMessage() == null ? "Bridge communication error" : e.getMessage();
                mainHandler.post(() -> finishResponse(errMsg, true, engine));
            }
        });
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
        statusDot.setTextColor(error ? M3_RED : M3_GREEN);
        statusText.setText(error ? " Error" : (" Ready (" + ("antigravity".equalsIgnoreCase(currentEngine) ? "Antigravity" : "Codex") + ")"));

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
            bgColor = M3_PRIMARY_CONTAINER;
            borderColor = M3_PRIMARY;
        } else if (author.contains("Error")) {
            bgColor = M3_SURFACE_CONTAINER;
            borderColor = M3_RED;
        } else if ("antigravity".equalsIgnoreCase(engine)) {
            bgColor = M3_SURFACE_CONTAINER;
            borderColor = M3_PRIMARY;
        } else {
            bgColor = M3_SURFACE_CONTAINER;
            borderColor = M3_TERTIARY;
        }

        bubbleCard.setBackground(m3Box(bgColor, borderColor, 1, 16));
        bubbleCard.setPadding(dp(14), dp(10), dp(14), dp(12));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        int headerColor = isUser ? M3_ON_PRIMARY_CONTAINER : (author.contains("Error") ? M3_RED : ("antigravity".equalsIgnoreCase(engine) ? M3_PRIMARY : M3_TERTIARY));
        TextView authorView = m3Text(author, 12.5f, headerColor, true);
        headerRow.addView(authorView, new LinearLayout.LayoutParams(0, -2, 1));

        String timeStr = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        TextView timeView = m3Text(timeStr, 11, M3_TEXT_MUTED, false);
        headerRow.addView(timeView);

        bubbleCard.addView(headerRow);

        LinearLayout markdownContent = new LinearLayout(this);
        markdownContent.setOrientation(LinearLayout.VERTICAL);
        markdownContent.setPadding(0, dp(4), 0, 0);
        renderMarkdownBlocks(markdownContent, message, isUser ? M3_ON_PRIMARY_CONTAINER : (author.contains("Error") ? M3_RED : M3_ON_SURFACE));
        bubbleCard.addView(markdownContent);

        LinearLayout.LayoutParams lpBubble = new LinearLayout.LayoutParams(-1, -2);
        lpBubble.setMargins(0, 0, 0, dp(10));
        transcript.addView(bubbleCard, lpBubble);

        scrollChat.post(() -> scrollChat.fullScroll(View.FOCUS_DOWN));
    }
}
