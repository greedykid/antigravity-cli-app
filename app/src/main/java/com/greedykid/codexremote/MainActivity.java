package com.greedykid.codexremote;

import android.Manifest;
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
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.speech.RecognizerIntent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final int CLAUDE_QUOTE_BG = Color.rgb(249, 248, 245);       // #F9F8F5 Warm Quote

    private static final int CLAUDE_TEXT_MAIN = Color.rgb(26, 25, 24);         // #1A1918 Deep Charcoal
    private static final int CLAUDE_TEXT_MUTED = Color.rgb(112, 111, 108);     // #706F6C Slate Grey
    private static final int CLAUDE_TEXT_LIGHT = Color.rgb(150, 149, 145);     // #969591 Light Slate

    private static final int CLAUDE_TERRACOTTA = Color.rgb(217, 107, 67);      // #D96B43 Claude Terracotta Orange
    private static final int CLAUDE_TERRACOTTA_LIGHT = Color.rgb(250, 235, 229); // #FAECE5 Light Peach
    private static final int CLAUDE_GREEN = Color.rgb(46, 125, 50);            // #2E7D32 Emerald Green
    private static final int CLAUDE_GREEN_BG = Color.rgb(234, 247, 237);       // #EAF7ED Light Mint
    private static final int CLAUDE_AMBER = Color.rgb(230, 124, 0);            // #E67C00 Amber
    private static final int CLAUDE_AMBER_BG = Color.rgb(254, 243, 224);       // #FEF3E0 Light Amber
    private static final int CLAUDE_RED = Color.rgb(198, 40, 40);              // #C62828 Ruby Red

    private static final int REQ_PICK_FILE = 1001;
    private static final int REQ_VOICE_SPEECH = 1002;
    private static final int REQ_CAMERA_PERMISSION = 2001;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    // Image Bitmap Memory Cache
    private final ConcurrentHashMap<String, Bitmap> imageCache = new ConcurrentHashMap<>();

    // Root Frame & Sidebar Navigation
    private FrameLayout rootFrame;
    private View sidebarScrim;
    private LinearLayout sidebarPanel;
    private View sidebarStatusDot;
    private TextView sidebarStatusText;
    private TextView sidebarEngineLabel;
    private boolean isSidebarOpen = false;

    // View Containers (Screen 0: Hub, Screen 1: Chat)
    private LinearLayout mainContentContainer;
    private LinearLayout viewHubContainer;
    private FrameLayout viewChatContainer;

    // Hub View Components
    private LinearLayout hubReadyList;
    private LinearLayout hubActiveList;
    private LinearLayout hubIdleList;
    private ProgressBar hubLoadingProgress;

    // Chat View Components
    private ImageView chatNavIcon;
    private TextView chatTopTitle;
    private LinearLayout chatMessagesList;
    private ScrollView chatScroll;
    private LinearLayout emptyMascotView;
    private EditText promptInput;
    private FrameLayout btnSend;
    private ImageView btnAttach;
    private ImageView btnEnginePill;
    private ImageView btnVoice;
    private TextView repoTagLabel;
    private LinearLayout attachmentChip;
    private ImageView attachmentThumb;
    private TextView attachmentText;

    // Active Session State
    private String activeConversationId = null;
    private String activeSessionTitle = "New session";
    private String currentEngine = "antigravity";
    private String attachedServerPath = null;
    private Bitmap attachedLocalBitmap = null;
    private int currentScreen = 1;
    private boolean navigatedFromHub = false;

    // Live Execution & Real-time Sync State
    private volatile boolean isLiveTaskRunning = false;
    private String lastLoadedSessionId = null;
    private int lastLoadedTurnCount = -1;
    private boolean lastRenderedWasRunning = false;

    // Live Bottom Sheet Modal State (Real-time updates while open)
    private Dialog activeBottomSheetDialog = null;
    private LinearLayout activeBottomSheetList = null;
    private TextView activeBottomSheetSubtitle = null;

    private boolean isAutoRefreshActive = false;
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentScreen == 1 && (isLiveTaskRunning || isAutoRefreshActive)) {
                syncLiveExecution();
                int delay = isLiveTaskRunning ? 1000 : 3000;
                mainHandler.postDelayed(this, delay);
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
        } else if (activeConversationId != null || isLiveTaskRunning) {
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

    private ImageView cIconButton(int resId, int iconSizeDp, int touchTargetDp, int tintColor) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(resId);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (tintColor != 0) {
            iv.setColorFilter(tintColor);
        }
        int padding = dp(Math.max(0, (touchTargetDp - iconSizeDp) / 2f));
        iv.setPadding(padding, padding, padding, padding);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(touchTargetDp), dp(touchTargetDp));
        iv.setLayoutParams(lp);
        return iv;
    }

    private ImageView cIcon(int resId, int sizeDp, int tintColor) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(resId);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (tintColor != 0) {
            iv.setColorFilter(tintColor);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        iv.setLayoutParams(lp);
        return iv;
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

        viewChatContainer = new FrameLayout(this);
        viewChatContainer.setVisibility(View.GONE);
        buildChatScreen(viewChatContainer);
        mainContentContainer.addView(viewChatContainer, new LinearLayout.LayoutParams(-1, -1));

        rootFrame.addView(mainContentContainer, new FrameLayout.LayoutParams(-1, -1));

        // 2. Sidebar Backdrop Scrim
        sidebarScrim = new View(this);
        sidebarScrim.setBackgroundColor(Color.argb(120, 0, 0, 0));
        sidebarScrim.setVisibility(View.GONE);
        sidebarScrim.setAlpha(0f);
        sidebarScrim.setOnClickListener(v -> closeSidebar());
        rootFrame.addView(sidebarScrim, new FrameLayout.LayoutParams(-1, -1));

        // 3. Sidebar Panel
        sidebarPanel = new LinearLayout(this);
        sidebarPanel.setOrientation(LinearLayout.VERTICAL);
        sidebarPanel.setBackgroundColor(CLAUDE_SURFACE);
        sidebarPanel.setVisibility(View.GONE);
        buildSidebarContent(sidebarPanel);

        FrameLayout.LayoutParams lpSide = new FrameLayout.LayoutParams(dp(300), -1);
        lpSide.gravity = Gravity.START;
        rootFrame.addView(sidebarPanel, lpSide);

        setContentView(rootFrame);

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

        ImageView sparkLogo = cIcon(R.drawable.ic_spark, 30, CLAUDE_TERRACOTTA);
        brand.addView(sparkLogo);

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

        sidebarStatusDot = new View(this);
        sidebarStatusDot.setBackground(cBox(CLAUDE_GREEN, 0, 0, 4));
        LinearLayout.LayoutParams lpDot = new LinearLayout.LayoutParams(dp(8), dp(8));
        sidebarStatusDot.setLayoutParams(lpDot);
        stRow.addView(sidebarStatusDot);

        sidebarStatusText = cText("  Gateway Online", 12, CLAUDE_TEXT_MAIN, true, false);
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

        addSidebarMenuItem(menuItems, R.drawable.ic_qr_code, "Scan QR Code Pairing", () -> {
            closeSidebar();
            startQrScanner();
        });

        addSidebarMenuItem(menuItems, R.drawable.ic_content_paste, "Paste Pairing from Clipboard", () -> {
            closeSidebar();
            pasteFromClipboard();
        });

        addSidebarMenuItem(menuItems, R.drawable.ic_chat, "New Chat Session", () -> {
            closeSidebar();
            startNewSession();
        });

        addSidebarMenuItem(menuItems, R.drawable.ic_history, "All Sessions (Code Hub)", () -> {
            closeSidebar();
            showScreen(0);
        });

        addSidebarMenuItem(menuItems, R.drawable.ic_swap, "Switch Engine (Agy / Codex)", () -> {
            toggleEngine();
            if (sidebarEngineLabel != null) {
                sidebarEngineLabel.setText("Active Engine: " + ("antigravity".equalsIgnoreCase(currentEngine) ? "Antigravity CLI" : "Codex CLI"));
            }
        });

        addSidebarMenuItem(menuItems, R.drawable.ic_settings, "Connection Settings", () -> {
            closeSidebar();
            showConnectionDialog();
        });

        addSidebarMenuItem(menuItems, R.drawable.ic_stop, "Interrupt Running Process", () -> {
            closeSidebar();
            stopRunningCliProcess();
        });

        addSidebarMenuItem(menuItems, R.drawable.ic_refresh, "Test Ping & Health", () -> {
            checkHealth();
        });

        scroll.addView(menuItems);
        sidebar.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // Footer Version info
        TextView ver = cText("v2.9.5 • Claude Floating Composer & Rich Markdown", 11.5f, CLAUDE_TEXT_LIGHT, false, false);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(10), 0, 0);
        sidebar.addView(ver);
    }

    private void addSidebarMenuItem(LinearLayout container, int iconRes, String title, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(11), dp(8), dp(11));
        row.setBackground(cBox(Color.TRANSPARENT, 0, 0, 10));

        ImageView ic = cIcon(iconRes, 22, CLAUDE_TEXT_MAIN);
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
            if (activeConversationId != null || isLiveTaskRunning) {
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
                chatNavIcon.setImageResource(R.drawable.ic_arrow_back);
            } else {
                chatNavIcon.setImageResource(R.drawable.ic_menu);
            }
            chatNavIcon.setColorFilter(CLAUDE_TEXT_MAIN);
        }
    }

    // ============================================================
    // SCREEN 1: "Code" SESSIONS HUB
    // ============================================================
    private void buildHubScreen(LinearLayout parent) {
        parent.setPadding(dp(18), dp(14), dp(18), dp(16));

        // Top Navigation Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, dp(4), 0, dp(12));

        ImageView menuIcon = cIconButton(R.drawable.ic_menu, 24, 40, CLAUDE_TEXT_MAIN);
        menuIcon.setOnClickListener(v -> openSidebar());
        topBar.addView(menuIcon);

        View spacer = new View(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1));

        // QR Button
        LinearLayout qrBtn = new LinearLayout(this);
        qrBtn.setOrientation(LinearLayout.HORIZONTAL);
        qrBtn.setGravity(Gravity.CENTER_VERTICAL);
        qrBtn.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 18));
        qrBtn.setPadding(dp(12), dp(8), dp(14), dp(8));
        qrBtn.addView(cIcon(R.drawable.ic_qr_code, 18, CLAUDE_TEXT_MAIN));
        TextView qrLabel = cText(" QR", 13.5f, CLAUDE_TEXT_MAIN, true, false);
        qrBtn.addView(qrLabel);
        qrBtn.setOnClickListener(v -> startQrScanner());
        LinearLayout.LayoutParams lpQr = new LinearLayout.LayoutParams(-2, -2);
        lpQr.setMargins(0, 0, dp(10), 0);
        topBar.addView(qrBtn, lpQr);

        // New Session Button
        LinearLayout newBtnTop = new LinearLayout(this);
        newBtnTop.setOrientation(LinearLayout.HORIZONTAL);
        newBtnTop.setGravity(Gravity.CENTER_VERTICAL);
        newBtnTop.setBackground(cBox(CLAUDE_TERRACOTTA_LIGHT, 0, 0, 18));
        newBtnTop.setPadding(dp(12), dp(8), dp(14), dp(8));
        newBtnTop.addView(cIcon(R.drawable.ic_add, 18, CLAUDE_TERRACOTTA));
        TextView newLabel = cText(" New", 13.5f, CLAUDE_TERRACOTTA, true, false);
        newBtnTop.addView(newLabel);
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

        content.addView(createSectionHeader("Ready"));
        hubReadyList = new LinearLayout(this);
        hubReadyList.setOrientation(LinearLayout.VERTICAL);
        content.addView(hubReadyList);

        content.addView(createSectionHeader("Live & Active"));
        hubActiveList = new LinearLayout(this);
        hubActiveList.setOrientation(LinearLayout.VERTICAL);
        content.addView(hubActiveList);

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
            TextView openBadge = cText("Open", 11.5f, CLAUDE_GREEN, true, false);
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
        lastRenderedWasRunning = false;

        if (chatMessagesList != null) chatMessagesList.removeAllViews();
        showEmptyMascotState(false);
        showScreen(1);
    }

    private void startNewSession() {
        activeConversationId = null;
        activeSessionTitle = "New session";
        lastLoadedSessionId = null;
        lastLoadedTurnCount = -1;
        lastRenderedWasRunning = false;
        isLiveTaskRunning = false;
        navigatedFromHub = false;

        if (chatMessagesList != null) chatMessagesList.removeAllViews();
        showEmptyMascotState(true);
        showScreen(1);
    }

    // ============================================================
    // SCREEN 2: CHAT VIEW (Floating Composer & Smooth Scroll Underlay)
    // ============================================================
    private void buildChatScreen(FrameLayout root) {
        // 1. Full-Height Content Area (Top Bar + ScrollView)
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dp(16), dp(8), dp(16), 0);

        // Top App Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, dp(2), 0, dp(8));

        chatNavIcon = cIconButton(R.drawable.ic_menu, 24, 40, CLAUDE_TEXT_MAIN);
        chatNavIcon.setOnClickListener(v -> {
            if (navigatedFromHub) {
                navigatedFromHub = false;
                showScreen(0);
            } else {
                openSidebar();
            }
        });
        topBar.addView(chatNavIcon);

        chatTopTitle = cText("New session", 16f, CLAUDE_TEXT_MAIN, true, false);
        chatTopTitle.setGravity(Gravity.CENTER);
        chatTopTitle.setSingleLine(true);
        topBar.addView(chatTopTitle, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView qrTopBtn = cIconButton(R.drawable.ic_qr_code, 22, 40, CLAUDE_TEXT_MAIN);
        qrTopBtn.setOnClickListener(v -> startQrScanner());
        topBar.addView(qrTopBtn);

        final ImageView moreBtn = cIconButton(R.drawable.ic_more_vert, 24, 40, CLAUDE_TEXT_MAIN);
        moreBtn.setOnClickListener(v -> showMoreDropdownMenu(moreBtn));
        topBar.addView(moreBtn);
        contentLayout.addView(topBar);

        // ScrollView with generous bottom padding so messages glide behind the floating composer
        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setVerticalScrollBarEnabled(false);
        chatScroll.setClipToPadding(false);
        chatScroll.setPadding(0, 0, 0, dp(120));

        chatMessagesList = new LinearLayout(this);
        chatMessagesList.setOrientation(LinearLayout.VERTICAL);
        chatMessagesList.setGravity(Gravity.BOTTOM);

        buildEmptyMascotState();
        chatMessagesList.addView(emptyMascotView);

        chatScroll.addView(chatMessagesList);
        contentLayout.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(contentLayout, new FrameLayout.LayoutParams(-1, -1));

        // 2. Floating Bottom Composer Card (Pure White with shadow & transparent surrounding margins)
        LinearLayout floatingWrapper = new LinearLayout(this);
        floatingWrapper.setOrientation(LinearLayout.VERTICAL);
        floatingWrapper.setBackgroundColor(Color.TRANSPARENT);

        // Attachment Preview Chip (Floats right above composer)
        attachmentChip = createAttachmentChip();
        floatingWrapper.addView(attachmentChip);

        LinearLayout composerCard = new LinearLayout(this);
        composerCard.setOrientation(LinearLayout.VERTICAL);
        composerCard.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_BORDER, 1, 24));
        composerCard.setPadding(dp(16), dp(12), dp(12), dp(10));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            composerCard.setElevation(dp(8));
        }

        promptInput = new EditText(this);
        promptInput.setHint("Code anything...");
        promptInput.setHintTextColor(CLAUDE_TEXT_LIGHT);
        promptInput.setTextColor(CLAUDE_TEXT_MAIN);
        promptInput.setTextSize(15);
        promptInput.setBackground(null);
        promptInput.setMinLines(1);
        promptInput.setMaxLines(5);
        promptInput.setSingleLine(false);
        promptInput.setPadding(0, 0, 0, dp(8));
        composerCard.addView(promptInput, new LinearLayout.LayoutParams(-1, -2));

        // Bottom Controls Row: [Repo Pill] ... [+] [Cloud] [Mic] [Send]
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);

        repoTagLabel = cText("google/antigravity-cli", 12, CLAUDE_TEXT_MAIN, false, false);
        repoTagLabel.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 14));
        repoTagLabel.setPadding(dp(12), dp(6), dp(12), dp(6));
        repoTagLabel.setOnClickListener(v -> toggleEngine());
        bottomRow.addView(repoTagLabel);

        View spacer = new View(this);
        bottomRow.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1));

        btnAttach = cIconButton(R.drawable.ic_add, 22, 38, CLAUDE_TEXT_MAIN);
        btnAttach.setOnClickListener(v -> openFilePicker());
        bottomRow.addView(btnAttach);

        btnEnginePill = cIconButton(R.drawable.ic_cloud, 22, 38, CLAUDE_TEXT_MAIN);
        btnEnginePill.setOnClickListener(v -> checkHealth());
        bottomRow.addView(btnEnginePill);

        btnVoice = cIconButton(R.drawable.ic_mic, 22, 38, CLAUDE_TEXT_MAIN);
        btnVoice.setOnClickListener(v -> startVoiceRecognition());
        bottomRow.addView(btnVoice);

        btnSend = new FrameLayout(this);
        btnSend.setBackground(cBox(CLAUDE_TERRACOTTA, 0, 0, 21));
        ImageView sendIcon = cIcon(R.drawable.ic_send, 20, Color.WHITE);
        FrameLayout.LayoutParams lpSendIcon = new FrameLayout.LayoutParams(dp(20), dp(20));
        lpSendIcon.gravity = Gravity.CENTER;
        btnSend.addView(sendIcon, lpSendIcon);
        btnSend.setOnClickListener(v -> sendClaudePrompt());

        LinearLayout.LayoutParams lpSend = new LinearLayout.LayoutParams(dp(42), dp(42));
        lpSend.setMargins(dp(6), 0, 0, 0);
        bottomRow.addView(btnSend, lpSend);

        composerCard.addView(bottomRow);
        floatingWrapper.addView(composerCard);

        FrameLayout.LayoutParams lpFloat = new FrameLayout.LayoutParams(-1, -2);
        lpFloat.gravity = Gravity.BOTTOM;
        lpFloat.setMargins(dp(14), 0, dp(14), dp(10));
        root.addView(floatingWrapper, lpFloat);
    }

    // Attachment Chip with Image Thumbnail Preview
    private LinearLayout createAttachmentChip() {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_TERRACOTTA, 1, 16));
        chip.setPadding(dp(8), dp(6), dp(8), dp(6));
        chip.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            chip.setElevation(dp(4));
        }

        attachmentThumb = new ImageView(this);
        attachmentThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        attachmentThumb.setBackground(cBox(CLAUDE_SURFACE_MUTED, 0, 0, 8));
        LinearLayout.LayoutParams lpTh = new LinearLayout.LayoutParams(dp(36), dp(36));
        attachmentThumb.setLayoutParams(lpTh);
        chip.addView(attachmentThumb);

        attachmentText = cText(" File Ready", 12.5f, CLAUDE_TERRACOTTA, true, false);
        attachmentText.setPadding(dp(8), 0, dp(8), 0);
        attachmentText.setSingleLine(true);
        chip.addView(attachmentText, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView close = cIconButton(R.drawable.ic_close, 18, 28, CLAUDE_RED);
        close.setOnClickListener(v -> {
            chip.setVisibility(View.GONE);
            attachedServerPath = null;
            attachedLocalBitmap = null;
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

        ImageView spark = cIcon(R.drawable.ic_spark, 56, CLAUDE_TERRACOTTA);
        emptyMascotView.addView(spark);

        TextView brandName = cText("Antigravity Code", 16, CLAUDE_TEXT_MAIN, true, true);
        brandName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpBn = new LinearLayout.LayoutParams(-1, -2);
        lpBn.setMargins(0, dp(14), 0, 0);
        emptyMascotView.addView(brandName, lpBn);
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

    // ============================================================
    // POPUP DROPDOWN MENU (3-DOTS)
    // ============================================================
    private void showMoreDropdownMenu(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenu().add(0, 1, 0, "Scan QR Code Pairing");
        popup.getMenu().add(0, 2, 0, "Paste from Clipboard");
        popup.getMenu().add(0, 3, 0, "Interrupt / Stop Task");
        popup.getMenu().add(0, 4, 0, "Connection Settings");
        popup.getMenu().add(0, 5, 0, "Refresh Transcript");
        popup.getMenu().add(0, 6, 0, "Clear to New Session");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) startQrScanner();
            else if (id == 2) pasteFromClipboard();
            else if (id == 3) stopRunningCliProcess();
            else if (id == 4) showConnectionDialog();
            else if (id == 5) fetchActiveSessionTurns(true);
            else if (id == 6) startNewSession();
            return true;
        });
        popup.show();
    }

    // ============================================================
    // TRUE-ASPECT-RATIO CRASH-PROOF QR SCANNER
    // ============================================================
    private void startQrScanner() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
                return;
            }
        }
        showNativeQrScannerModal();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showNativeQrScannerModal();
            } else {
                Toast.makeText(this, "Izin kamera ditolak. Gunakan opsi Paste Clipboard.", Toast.LENGTH_LONG).show();
                pasteFromClipboard();
            }
        }
    }

    private void showNativeQrScannerModal() {
        try {
            final Dialog dialog = new Dialog(this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(cBox(CLAUDE_SURFACE, 0, 0, 24));
            root.setPadding(dp(20), dp(16), dp(20), dp(20));

            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);

            ImageView qrIcon = cIcon(R.drawable.ic_qr_code, 22, CLAUDE_TERRACOTTA);
            head.addView(qrIcon);

            TextView title = cText(" Scan QR Pairing", 17, CLAUDE_TEXT_MAIN, true, true);
            head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

            ImageView close = cIconButton(R.drawable.ic_close, 20, 36, CLAUDE_TEXT_MUTED);
            close.setOnClickListener(v -> dialog.dismiss());
            head.addView(close);
            root.addView(head);

            TextView sub = cText("Arahkan kamera ke QR Code di terminal (agy-pair)", 12.5f, CLAUDE_TEXT_MUTED, false, false);
            LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(-1, -2);
            lpSub.setMargins(0, dp(4), 0, dp(14));
            root.addView(sub, lpSub);

            FrameLayout frame = new FrameLayout(this);
            frame.setBackground(cBox(Color.BLACK, CLAUDE_TERRACOTTA, 2, 16));
            frame.setClipChildren(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                frame.setClipToOutline(true);
            }

            final QrCameraScannerView scannerView = new QrCameraScannerView(this, text -> {
                dialog.dismiss();
                handleQrPayload(text);
            });
            FrameLayout.LayoutParams lpScan = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER);
            frame.addView(scannerView, lpScan);

            View guide = new View(this);
            guide.setBackground(cBox(Color.TRANSPARENT, Color.WHITE, 2, 12));
            FrameLayout.LayoutParams lpG = new FrameLayout.LayoutParams(dp(180), dp(180));
            lpG.gravity = Gravity.CENTER;
            frame.addView(guide, lpG);

            LinearLayout.LayoutParams lpFr = new LinearLayout.LayoutParams(-1, dp(260));
            lpFr.setMargins(0, 0, 0, dp(16));
            root.addView(frame, lpFr);

            LinearLayout clipBtn = new LinearLayout(this);
            clipBtn.setOrientation(LinearLayout.HORIZONTAL);
            clipBtn.setGravity(Gravity.CENTER);
            clipBtn.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 12));
            clipBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
            clipBtn.addView(cIcon(R.drawable.ic_content_paste, 18, CLAUDE_TEXT_MAIN));
            TextView clipLbl = cText("  Tempel dari Clipboard", 13, CLAUDE_TEXT_MAIN, true, false);
            clipBtn.addView(clipLbl);
            clipBtn.setOnClickListener(v -> {
                dialog.dismiss();
                pasteFromClipboard();
            });
            root.addView(clipBtn, new LinearLayout.LayoutParams(-1, dp(44)));

            dialog.setContentView(root);
            dialog.setOnDismissListener(d -> scannerView.stopCamera());

            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setLayout(dp(340), -2);
            }
            dialog.show();
        } catch (Throwable t) {
            Toast.makeText(this, "Tidak dapat membuka kamera: " + t.getMessage(), Toast.LENGTH_LONG).show();
            pasteFromClipboard();
        }
    }

    public interface QrScanResultListener {
        void onQrDecoded(String text);
    }

    @SuppressWarnings("deprecation")
    public static class QrCameraScannerView extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
        private Camera camera;
        private final MultiFormatReader reader;
        private final QrScanResultListener listener;
        private boolean isScanning = true;
        private int previewWidth = 0;
        private int previewHeight = 0;

        public QrCameraScannerView(Context context, QrScanResultListener listener) {
            super(context);
            this.listener = listener;
            reader = new MultiFormatReader();
            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            reader.setHints(hints);
            getHolder().addCallback(this);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                camera = Camera.open();
                camera.setDisplayOrientation(90);
                Camera.Parameters params = camera.getParameters();

                List<Camera.Size> supportedSizes = params.getSupportedPreviewSizes();
                if (supportedSizes != null && !supportedSizes.isEmpty()) {
                    Camera.Size bestSize = getOptimalPreviewSize(supportedSizes, 1280, 720);
                    if (bestSize != null) {
                        params.setPreviewSize(bestSize.width, bestSize.height);
                        previewWidth = bestSize.width;
                        previewHeight = bestSize.height;
                    }
                }

                List<String> focusModes = params.getSupportedFocusModes();
                if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                }
                camera.setParameters(params);
                camera.setPreviewDisplay(holder);
                camera.setPreviewCallback(this);
                camera.startPreview();
                requestLayout();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int targetWidth, int targetHeight) {
            if (sizes == null) return null;
            double targetRatio = (double) targetWidth / targetHeight;
            Camera.Size optimalSize = null;
            double minDiff = Double.MAX_VALUE;

            for (Camera.Size size : sizes) {
                double ratio = (double) size.width / size.height;
                if (Math.abs(ratio - targetRatio) > 0.18) continue;
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }

            if (optimalSize == null) {
                minDiff = Double.MAX_VALUE;
                for (Camera.Size size : sizes) {
                    if (Math.abs(size.height - targetHeight) < minDiff) {
                        optimalSize = size;
                        minDiff = Math.abs(size.height - targetHeight);
                    }
                }
            }
            return optimalSize != null ? optimalSize : sizes.get(0);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);

            if (previewWidth > 0 && previewHeight > 0) {
                float cameraPortraitRatio = (float) previewHeight / (float) previewWidth;
                float viewportRatio = (float) width / (float) height;

                if (viewportRatio > cameraPortraitRatio) {
                    height = (int) (width / cameraPortraitRatio);
                } else {
                    width = (int) (height * cameraPortraitRatio);
                }
            }
            setMeasuredDimension(width, height);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (camera != null) {
                try {
                    camera.startPreview();
                } catch (Throwable ignored) {}
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            stopCamera();
        }

        public void stopCamera() {
            if (camera != null) {
                try {
                    camera.setPreviewCallback(null);
                    camera.stopPreview();
                    camera.release();
                } catch (Throwable ignored) {}
                camera = null;
            }
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (!isScanning || camera == null) return;
            try {
                Camera.Size size = camera.getParameters().getPreviewSize();
                byte[] rotated = rotateYuv90(data, size.width, size.height);
                PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                        rotated, size.height, size.width, 0, 0, size.height, size.width, false);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                Result result = null;
                try {
                    result = reader.decodeWithState(bitmap);
                } catch (Throwable ignored) {}

                if (result == null) {
                    PlanarYUVLuminanceSource rawSource = new PlanarYUVLuminanceSource(
                            data, size.width, size.height, 0, 0, size.width, size.height, false);
                    try {
                        result = reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(rawSource)));
                    } catch (Throwable ignored) {}
                }

                if (result != null && result.getText() != null && !result.getText().isEmpty()) {
                    isScanning = false;
                    final String text = result.getText();
                    post(() -> {
                        if (listener != null) {
                            listener.onQrDecoded(text);
                        }
                    });
                }
            } catch (Throwable ignored) {
            } finally {
                reader.reset();
            }
        }

        private byte[] rotateYuv90(byte[] data, int imageWidth, int imageHeight) {
            byte[] yuv = new byte[data.length];
            int i = 0;
            for (int x = 0; x < imageWidth; x++) {
                for (int y = imageHeight - 1; y >= 0; y--) {
                    yuv[i] = data[y * imageWidth + x];
                    i++;
                }
            }
            return yuv;
        }
    }

    private void pasteFromClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (text != null && text.length() > 0) {
                    handleQrPayload(text.toString().trim());
                    return;
                }
            }
            Toast.makeText(this, "Clipboard kosong. Salin URL/kode pairing terlebih dahulu.", Toast.LENGTH_SHORT).show();
            showConnectionDialog();
        } catch (Exception e) {
            Toast.makeText(this, "Clipboard error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            showConnectionDialog();
        }
    }

    private void handleQrPayload(String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        String trimmed = raw.trim();

        try {
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

            if (trimmed.startsWith("agy://") || trimmed.startsWith("codex://") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
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

            Toast.makeText(this, "Format pairing tidak dikenali: " + trimmed, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal memproses pairing: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, "Berhasil terhubung ke Server!", Toast.LENGTH_LONG).show();
        checkHealth();
        fetchHubSessions();
    }

    // ============================================================
    // ATTACHMENT & IMAGE PREVIEW HANDLING
    // ============================================================
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select Image or File to Upload"), REQ_PICK_FILE);
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

                Bitmap thumbBmp = null;
                try {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 4;
                    thumbBmp = BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.length, opts);
                } catch (Exception ignored) {}

                final Bitmap finalThumb = thumbBmp;

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

                    if (finalThumb != null) {
                        imageCache.put(serverPath, finalThumb);
                        imageCache.put(savedName, finalThumb);
                    }

                    mainHandler.post(() -> {
                        attachedServerPath = serverPath;
                        attachedLocalBitmap = finalThumb;
                        attachmentText.setText(" " + savedName);
                        if (finalThumb != null) {
                            attachmentThumb.setImageBitmap(finalThumb);
                            attachmentThumb.setVisibility(View.VISIBLE);
                        } else {
                            attachmentThumb.setImageResource(R.drawable.ic_attach_file);
                            attachmentThumb.setVisibility(View.VISIBLE);
                        }
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
        return result != null ? result : "upload_" + System.currentTimeMillis() + ".png";
    }

    // ============================================================
    // REAL-TIME LIVE CHAT EXECUTION & INSTANT RESPONSE RENDERING
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
        attachedLocalBitmap = null;
        if (attachmentChip != null) attachmentChip.setVisibility(View.GONE);

        showEmptyMascotState(false);

        btnSend.setTag("busy");
        btnSend.setEnabled(false);
        promptInput.setEnabled(false);
        isLiveTaskRunning = true;

        String displayText = (file != null ? "[File: " + file + "]\n" : "") + text;
        addMessageCard("user", displayText, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        promptInput.setText("");

        // Scroll to bottom when user sends a prompt
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));

        startAutoRefresh();

        final String promptToSend = text;
        final String fileToSend = file;

        executor.execute(() -> {
            try {
                JSONObject req = new JSONObject();
                req.put("prompt", promptToSend);
                req.put("engine", currentEngine);
                req.put("resume", true);
                if (fileToSend != null) {
                    req.put("attachedFile", fileToSend);
                }
                if (activeConversationId != null && !activeConversationId.isEmpty()) {
                    req.put("conversationId", activeConversationId);
                }

                JSONObject res = executePost(endpoint, prefs.getString("token", ""), req);
                String activeId = res.optString("conversationId", activeConversationId);
                if (activeId != null && !activeId.isEmpty()) {
                    activeConversationId = activeId;
                }

                mainHandler.post(() -> {
                    isLiveTaskRunning = false;
                    btnSend.setTag(null);
                    btnSend.setEnabled(true);
                    promptInput.setEnabled(true);

                    // Render final output INSTANTLY from the completed response object
                    renderActiveSessionTurns(activeConversationId, res, false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    isLiveTaskRunning = false;
                    btnSend.setTag(null);
                    btnSend.setEnabled(true);
                    promptInput.setEnabled(true);
                    syncLiveExecution();
                });
            }
        });
    }

    private void syncLiveExecution() {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) return;

        executor.execute(() -> {
            try {
                String targetConvId = activeConversationId;
                String queryUrl;
                if (targetConvId != null && !targetConvId.isEmpty()) {
                    queryUrl = endpoint.replace("/api/chat", "/api/session/transcript?id=" + Uri.encode(targetConvId));
                } else {
                    queryUrl = endpoint.replace("/api/chat", "/api/session/live");
                }

                HttpURLConnection c = (HttpURLConnection) new URL(queryUrl).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(5000);
                c.setReadTimeout(5000);
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

                    mainHandler.post(() -> {
                        String newId = json.optString("conversationId", "");
                        if (activeConversationId == null && !newId.isEmpty()) {
                            activeConversationId = newId;
                        }
                        renderActiveSessionTurns(activeConversationId, json, false);
                    });
                }
            } catch (Exception ignored) {}
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
        syncLiveExecution();
        if (showFeedback) {
            Toast.makeText(this, "Syncing session...", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderActiveSessionTurns(String requestedConvId, JSONObject json, boolean showToast) {
        try {
            if (json == null) return;

            JSONObject session = json.optJSONObject("session");
            if (session != null) {
                activeSessionTitle = session.optString("title", activeSessionTitle);
                chatTopTitle.setText(activeSessionTitle);
            }

            JSONArray turns = json.optJSONArray("turns");
            if (turns == null) {
                turns = json.optJSONArray("messages");
            }

            if (turns != null) {
                int newTurnCount = turns.length();

                if (!isLiveTaskRunning && !lastRenderedWasRunning && requestedConvId != null && requestedConvId.equals(lastLoadedSessionId) && newTurnCount == lastLoadedTurnCount) {
                    return;
                }

                boolean isNearBottom = isScrollNearBottom();
                boolean isInitialSessionLoad = requestedConvId != null && !requestedConvId.equals(lastLoadedSessionId);

                lastLoadedSessionId = requestedConvId;
                lastLoadedTurnCount = newTurnCount;
                lastRenderedWasRunning = isLiveTaskRunning;

                chatMessagesList.removeAllViews();
                showEmptyMascotState(turns.length() == 0 && !isLiveTaskRunning);

                ArrayList<JSONObject> pendingTools = new ArrayList<>();
                ArrayList<JSONObject> allSessionTools = new ArrayList<>();

                for (int i = 0; i < turns.length(); i++) {
                    JSONObject turn = turns.getJSONObject(i);
                    String role = turn.optString("role", "info");
                    String content = turn.optString("content", "");
                    String time = turn.optString("time", "");

                    if ("tool".equalsIgnoreCase(role) || "thinking".equalsIgnoreCase(role)) {
                        pendingTools.add(turn);
                        allSessionTools.add(turn);
                    } else {
                        if (!pendingTools.isEmpty()) {
                            addCompactToolsGroupPill(new ArrayList<>(pendingTools), false);
                            pendingTools.clear();
                        }
                        addMessageCard(role, content, time);
                    }
                }

                if (!pendingTools.isEmpty()) {
                    addCompactToolsGroupPill(pendingTools, isLiveTaskRunning);
                } else if (isLiveTaskRunning) {
                    ArrayList<JSONObject> dummy = new ArrayList<>();
                    JSONObject o = new JSONObject();
                    o.put("role", "thinking");
                    o.put("title", "Processing prompt...");
                    o.put("content", "Starting CLI process and planning response...");
                    dummy.add(o);
                    addCompactToolsGroupPill(dummy, true);
                    allSessionTools.addAll(dummy);
                }

                if (activeBottomSheetDialog != null && activeBottomSheetDialog.isShowing()) {
                    ArrayList<JSONObject> toolsToUpdate = !pendingTools.isEmpty() ? pendingTools : allSessionTools;
                    updateExecutionBottomModalContent(toolsToUpdate, isLiveTaskRunning);
                }

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
        return distanceToBottom <= dp(120);
    }

    // ============================================================
    // COMPACT TEXT PILL & FULLY SWIPEABLE / EXPANDABLE FULLSCREEN BOTTOM SHEET
    // ============================================================
    private void addCompactToolsGroupPill(final ArrayList<JSONObject> toolTurns, final boolean isCurrentlyWorking) {
        final boolean isActuallyRunning = isCurrentlyWorking && isLiveTaskRunning;

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
            labelText = (isActuallyRunning ? "Working on " : "Worked on ") + (toolCount + thinkCount) + " steps (" + toolCount + " tools, " + thinkCount + " thinking)";
        } else if (toolCount > 0) {
            labelText = (isActuallyRunning ? "Executing " : "Executed ") + toolCount + " tool" + (toolCount > 1 ? "s" : "") + (latestToolName.isEmpty() ? "" : ": " + latestToolName);
        } else {
            labelText = (isActuallyRunning ? "Thinking..." : "Viewed thought process (" + thinkCount + " step" + (thinkCount > 1 ? "s" : "") + ")");
        }

        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(cBox(isActuallyRunning ? CLAUDE_AMBER_BG : CLAUDE_SURFACE_MUTED, isActuallyRunning ? CLAUDE_AMBER : CLAUDE_BORDER, 1, 14));
        pill.setPadding(dp(12), dp(9), dp(12), dp(9));

        ImageView actionIcon = cIcon(toolCount > 0 ? R.drawable.ic_build : R.drawable.ic_psychology, 18, isActuallyRunning ? CLAUDE_AMBER : CLAUDE_TERRACOTTA);
        pill.addView(actionIcon);

        TextView tv = cText("  " + labelText, 13f, CLAUDE_TEXT_MAIN, true, false);
        pill.addView(tv, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout stateBadge = new LinearLayout(this);
        stateBadge.setOrientation(LinearLayout.HORIZONTAL);
        stateBadge.setGravity(Gravity.CENTER_VERTICAL);

        if (isActuallyRunning) {
            stateBadge.setBackground(cBox(CLAUDE_AMBER_BG, CLAUDE_AMBER, 1, 6));
            stateBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
            ProgressBar pb = new ProgressBar(this);
            LinearLayout.LayoutParams lpPb = new LinearLayout.LayoutParams(dp(12), dp(12));
            stateBadge.addView(pb, lpPb);
            TextView runText = cText(" Running", 11f, CLAUDE_AMBER, true, false);
            stateBadge.addView(runText);
        } else {
            stateBadge.setBackground(cBox(CLAUDE_GREEN_BG, CLAUDE_GREEN, 1, 6));
            stateBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
            stateBadge.addView(cIcon(R.drawable.ic_check, 12, CLAUDE_GREEN));
            TextView doneText = cText(" Done", 11f, CLAUDE_GREEN, true, false);
            stateBadge.addView(doneText);
        }
        pill.addView(stateBadge);

        ImageView chevron = cIcon(R.drawable.ic_chevron_right, 20, CLAUDE_TEXT_MUTED);
        chevron.setPadding(dp(4), 0, 0, 0);
        pill.addView(chevron);

        pill.setOnClickListener(v -> openExecutionBottomModal(toolTurns, isActuallyRunning));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(8));
        chatMessagesList.addView(pill, lp);
    }

    private void openExecutionBottomModal(final ArrayList<JSONObject> items, final boolean isCurrentlyWorking) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int screenHeight = dm.heightPixels;
        final int peekHeight = (int) (screenHeight * 0.60f);
        final int fullHeight = (int) (screenHeight * 0.94f);

        final LinearLayout modalRoot = new LinearLayout(this);
        modalRoot.setOrientation(LinearLayout.VERTICAL);
        modalRoot.setBackground(cBox(CLAUDE_SURFACE, 0, 0, 24));
        modalRoot.setPadding(dp(20), dp(10), dp(20), dp(16));

        final LinearLayout dragArea = new LinearLayout(this);
        dragArea.setOrientation(LinearLayout.VERTICAL);
        dragArea.setGravity(Gravity.CENTER_HORIZONTAL);
        dragArea.setPadding(0, dp(6), 0, dp(10));

        View dragHandle = new View(this);
        dragHandle.setBackground(cBox(CLAUDE_BORDER_DARK, 0, 0, 3));
        LinearLayout.LayoutParams lpHandle = new LinearLayout.LayoutParams(dp(52), dp(6));
        dragArea.addView(dragHandle, lpHandle);
        modalRoot.addView(dragArea);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = cText("Execution & Thoughts", 18.5f, CLAUDE_TEXT_MAIN, true, true);
        headerRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        final ImageView fullscreenBtn = cIconButton(R.drawable.ic_fullscreen, 24, 40, CLAUDE_TEXT_MAIN);
        headerRow.addView(fullscreenBtn);

        ImageView closeBtn = cIconButton(R.drawable.ic_close, 22, 40, CLAUDE_TEXT_MAIN);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        headerRow.addView(closeBtn);
        modalRoot.addView(headerRow);

        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        final TextView sub = cText(items.size() + " actions • tap item to expand/collapse", 13f, CLAUDE_TEXT_MUTED, false, false);
        subRow.addView(sub, new LinearLayout.LayoutParams(0, -2, 1));
        modalRoot.addView(subRow);

        final ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);

        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, dp(12));

        scroll.addView(list);
        modalRoot.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        activeBottomSheetDialog = dialog;
        activeBottomSheetList = list;
        activeBottomSheetSubtitle = sub;

        updateExecutionBottomModalContent(items, isCurrentlyWorking);

        dialog.setContentView(modalRoot);
        dialog.setOnDismissListener(d -> {
            activeBottomSheetDialog = null;
            activeBottomSheetList = null;
            activeBottomSheetSubtitle = null;
        });

        final Window window = dialog.getWindow();
        final boolean[] isFullscreen = {false};

        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.gravity = Gravity.BOTTOM;
            wlp.width = WindowManager.LayoutParams.MATCH_PARENT;
            wlp.height = peekHeight;
            wlp.windowAnimations = android.R.style.Animation_InputMethod;
            window.setAttributes(wlp);
        }

        fullscreenBtn.setOnClickListener(v -> {
            isFullscreen[0] = !isFullscreen[0];
            if (window != null) {
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.height = isFullscreen[0] ? fullHeight : peekHeight;
                window.setAttributes(lp);
            }
            fullscreenBtn.setImageResource(isFullscreen[0] ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
        });

        View.OnTouchListener swipeDragListener = new View.OnTouchListener() {
            private float startY = 0;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        float deltaY = event.getRawY() - startY;
                        if (deltaY < -dp(50)) {
                            isFullscreen[0] = true;
                            if (window != null) {
                                WindowManager.LayoutParams lp = window.getAttributes();
                                lp.height = fullHeight;
                                window.setAttributes(lp);
                            }
                            fullscreenBtn.setImageResource(R.drawable.ic_fullscreen_exit);
                        } else if (deltaY > dp(70)) {
                            if (isFullscreen[0]) {
                                isFullscreen[0] = false;
                                if (window != null) {
                                    WindowManager.LayoutParams lp = window.getAttributes();
                                    lp.height = peekHeight;
                                    window.setAttributes(lp);
                                }
                                fullscreenBtn.setImageResource(R.drawable.ic_fullscreen);
                            } else {
                                dialog.dismiss();
                            }
                        }
                        return true;
                }
                return false;
            }
        };

        dragArea.setOnTouchListener(swipeDragListener);
        headerRow.setOnTouchListener(swipeDragListener);

        dialog.show();
    }

    private void updateExecutionBottomModalContent(final ArrayList<JSONObject> items, final boolean isCurrentlyWorking) {
        if (activeBottomSheetList == null) return;

        final boolean isActuallyRunning = isCurrentlyWorking && isLiveTaskRunning;

        if (activeBottomSheetSubtitle != null) {
            activeBottomSheetSubtitle.setText(items.size() + " actions • tap item to expand/collapse" + (isActuallyRunning ? " (Live)" : ""));
        }

        activeBottomSheetList.removeAllViews();

        for (int i = 0; i < items.size(); i++) {
            JSONObject it = items.get(i);
            String role = it.optString("role", "tool");
            String itTitle = it.optString("title", "tool".equalsIgnoreCase(role) ? "Executed Tool" : "Thinking Process");
            String content = it.optString("content", "");
            boolean isTool = "tool".equalsIgnoreCase(role);
            boolean isThisItemRunning = (isActuallyRunning && i == items.size() - 1);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(cBox(isThisItemRunning ? CLAUDE_AMBER_BG : CLAUDE_SURFACE_MUTED, isThisItemRunning ? CLAUDE_AMBER : CLAUDE_BORDER, 1, 12));
            card.setPadding(dp(12), dp(10), dp(12), dp(10));

            LinearLayout cHead = new LinearLayout(this);
            cHead.setOrientation(LinearLayout.HORIZONTAL);
            cHead.setGravity(Gravity.CENTER_VERTICAL);

            ImageView ic = cIcon(isTool ? R.drawable.ic_build : R.drawable.ic_psychology, 18, isThisItemRunning ? CLAUDE_AMBER : (isTool ? CLAUDE_TERRACOTTA : CLAUDE_TEXT_MUTED));
            cHead.addView(ic);

            TextView tView = cText("  " + itTitle, 13.5f, CLAUDE_TEXT_MAIN, true, false);
            cHead.addView(tView, new LinearLayout.LayoutParams(0, -2, 1));

            LinearLayout bBadge = new LinearLayout(this);
            bBadge.setOrientation(LinearLayout.HORIZONTAL);
            bBadge.setGravity(Gravity.CENTER_VERTICAL);

            if (isThisItemRunning) {
                bBadge.setBackground(cBox(CLAUDE_AMBER_BG, CLAUDE_AMBER, 1, 6));
                bBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
                ProgressBar pb = new ProgressBar(this);
                LinearLayout.LayoutParams lpPb = new LinearLayout.LayoutParams(dp(12), dp(12));
                bBadge.addView(pb, lpPb);
                TextView bText = cText(" Running", 11f, CLAUDE_AMBER, true, false);
                bBadge.addView(bText);
            } else {
                bBadge.setBackground(cBox(CLAUDE_GREEN_BG, CLAUDE_GREEN, 1, 6));
                bBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
                bBadge.addView(cIcon(R.drawable.ic_check, 12, CLAUDE_GREEN));
                TextView bText = cText(" Done", 11f, CLAUDE_GREEN, true, false);
                bBadge.addView(bText);
            }
            cHead.addView(bBadge);

            final ImageView expandChevron = cIcon(isThisItemRunning ? R.drawable.ic_expand_more : R.drawable.ic_chevron_right, 20, CLAUDE_TEXT_MUTED);
            expandChevron.setPadding(dp(4), 0, 0, 0);
            cHead.addView(expandChevron);

            card.addView(cHead);

            final TextView body = new TextView(this);
            body.setText(content);
            body.setTextSize(12.5f);
            body.setTextColor(CLAUDE_TEXT_MUTED);
            body.setTypeface(Typeface.MONOSPACE);
            body.setTextIsSelectable(true);
            body.setPadding(0, dp(8), 0, dp(4));
            body.setVisibility(isThisItemRunning ? View.VISIBLE : View.GONE);
            card.addView(body);

            cHead.setOnClickListener(v -> {
                if (body.getVisibility() == View.VISIBLE) {
                    body.setVisibility(View.GONE);
                    expandChevron.setImageResource(R.drawable.ic_chevron_right);
                } else {
                    body.setVisibility(View.VISIBLE);
                    expandChevron.setImageResource(R.drawable.ic_expand_more);
                }
            });

            LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(-1, -2);
            lpC.setMargins(0, 0, 0, dp(10));
            activeBottomSheetList.addView(card, lpC);
        }
    }

    // ============================================================
    // RICH MARKDOWN MESSAGE CARDS & DEDICATED COPY ACTIONS
    // ============================================================
    private void addMessageCard(String role, String content, String time) {
        boolean isUser = "user".equalsIgnoreCase(role);

        showEmptyMascotState(false);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        int bgColor = isUser ? CLAUDE_TERRACOTTA_LIGHT : CLAUDE_SURFACE;
        int borderColor = isUser ? CLAUDE_TERRACOTTA : CLAUDE_BORDER;
        card.setBackground(cBox(bgColor, borderColor, 1, 16));
        card.setPadding(dp(14), dp(10), dp(14), dp(12));

        // Header Row: Author + Timestamp + Quick Copy Button
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

        final String rawCleanContent = cleanMarkdownForCopy(content);
        ImageView copyBtn = cIconButton(R.drawable.ic_content_paste, 16, 28, CLAUDE_TEXT_MUTED);
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Chat message", rawCleanContent));
            Toast.makeText(MainActivity.this, "Pesan disalin ke clipboard", Toast.LENGTH_SHORT).show();
        });
        head.addView(copyBtn);
        card.addView(head);

        // Check if content contains image tags or file attachment
        renderMessageContentWithMedia(card, content, isUser);

        // Dedicated Bottom Action Bar for Assistant Messages (Salin / Copy Response Pill)
        if (!isUser && content != null && !content.trim().isEmpty()) {
            LinearLayout botActionRow = new LinearLayout(this);
            botActionRow.setOrientation(LinearLayout.HORIZONTAL);
            botActionRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            botActionRow.setPadding(0, dp(10), 0, dp(2));

            LinearLayout copyPill = new LinearLayout(this);
            copyPill.setOrientation(LinearLayout.HORIZONTAL);
            copyPill.setGravity(Gravity.CENTER_VERTICAL);
            copyPill.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 12));
            copyPill.setPadding(dp(10), dp(5), dp(12), dp(5));

            ImageView copyIc = cIcon(R.drawable.ic_content_paste, 14, CLAUDE_TEXT_MAIN);
            copyPill.addView(copyIc);

            TextView copyLbl = cText("  Salin Respon", 11.5f, CLAUDE_TEXT_MAIN, true, false);
            copyPill.addView(copyLbl);

            copyPill.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("AI Response", rawCleanContent));
                Toast.makeText(MainActivity.this, "Respon AI disalin ke clipboard", Toast.LENGTH_SHORT).show();
            });

            botActionRow.addView(copyPill);
            card.addView(botActionRow);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        chatMessagesList.addView(card, lp);
    }

    private String cleanMarkdownForCopy(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\[File: [^\\n\\]]+\\]\\n?", "").trim();
    }

    // Media & Image Preview Renderer in Chat Messages
    private void renderMessageContentWithMedia(LinearLayout container, String text, boolean isUser) {
        if (text == null || text.isEmpty()) return;

        Pattern imgFilePat = Pattern.compile("\\[File:\\s*([^\\]]+\\.(?:png|jpg|jpeg|webp|gif|svg))\\]", Pattern.CASE_INSENSITIVE);
        Matcher m = imgFilePat.matcher(text);

        String remainingText = text;

        if (m.find()) {
            String filePath = m.group(1).trim();
            remainingText = text.substring(0, m.start()) + text.substring(m.end());

            ImageView imgPreview = new ImageView(this);
            imgPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imgPreview.setAdjustViewBounds(true);
            imgPreview.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 12));
            imgPreview.setPadding(dp(2), dp(2), dp(2), dp(2));

            LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(-1, -2);
            lpImg.setMargins(0, dp(8), 0, dp(8));
            container.addView(imgPreview, lpImg);

            loadImageIntoView(filePath, imgPreview);
        }

        renderMarkdownIntoContainer(container, remainingText.trim(), isUser);
    }

    private void loadImageIntoView(String filePathOrUrl, ImageView imageView) {
        String key = filePathOrUrl;
        if (imageCache.containsKey(key)) {
            imageView.setImageBitmap(imageCache.get(key));
            return;
        }

        final String fileName = new File(filePathOrUrl).getName();
        if (imageCache.containsKey(fileName)) {
            imageView.setImageBitmap(imageCache.get(fileName));
            return;
        }

        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) return;

        executor.execute(() -> {
            try {
                String downloadUrl = endpoint.replace("/api/chat", "/api/uploads/" + Uri.encode(fileName));
                HttpURLConnection c = (HttpURLConnection) new URL(downloadUrl).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(8000);
                c.setReadTimeout(12000);
                String token = prefs.getString("token", "");
                if (!token.isEmpty()) {
                    c.setRequestProperty("Authorization", "Bearer " + token);
                }

                if (c.getResponseCode() == 200) {
                    InputStream is = c.getInputStream();
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    is.close();
                    if (bmp != null) {
                        imageCache.put(key, bmp);
                        imageCache.put(fileName, bmp);
                        mainHandler.post(() -> imageView.setImageBitmap(bmp));
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    // ============================================================
    // ADVANCED MARKDOWN FORMATTER (Tables, Headings, Quotes, Lists)
    // ============================================================
    private void renderMarkdownIntoContainer(LinearLayout container, String markdown, boolean isUser) {
        if (markdown == null || markdown.isEmpty()) return;

        String[] sections = markdown.split("```");
        for (int s = 0; s < sections.length; s++) {
            if (s % 2 == 1) {
                // Fenced Code Block
                String block = sections[s];
                String lang = "CODE";
                String codeContent = block;
                int firstLf = block.indexOf("\n");
                if (firstLf > 0 && firstLf < 25) {
                    lang = block.substring(0, firstLf).trim().toUpperCase(Locale.ROOT);
                    codeContent = block.substring(firstLf + 1);
                }

                LinearLayout codeBox = new LinearLayout(this);
                codeBox.setOrientation(LinearLayout.VERTICAL);
                codeBox.setBackground(cBox(CLAUDE_CODE_BG, 0, 0, 12));
                codeBox.setPadding(dp(12), dp(10), dp(12), dp(12));

                LinearLayout codeHeader = new LinearLayout(this);
                codeHeader.setOrientation(LinearLayout.HORIZONTAL);
                codeHeader.setGravity(Gravity.CENTER_VERTICAL);

                TextView langTag = cText(lang.isEmpty() ? "CODE" : lang, 11, CLAUDE_TERRACOTTA, true, false);
                codeHeader.addView(langTag, new LinearLayout.LayoutParams(0, -2, 1));

                LinearLayout copyCodeBtn = new LinearLayout(this);
                copyCodeBtn.setOrientation(LinearLayout.HORIZONTAL);
                copyCodeBtn.setGravity(Gravity.CENTER_VERTICAL);
                copyCodeBtn.setBackground(cBox(Color.rgb(45, 47, 52), 0, 0, 8));
                copyCodeBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
                copyCodeBtn.addView(cIcon(R.drawable.ic_content_paste, 12, Color.rgb(200, 200, 210)));
                TextView copyLbl = cText(" Copy", 11, Color.rgb(200, 200, 210), true, false);
                copyCodeBtn.addView(copyLbl);

                final String copyCode = codeContent.trim();
                copyCodeBtn.setOnClickListener(v -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("Code snippet", copyCode));
                    Toast.makeText(MainActivity.this, "Kode snippet disalin", Toast.LENGTH_SHORT).show();
                });
                codeHeader.addView(copyCodeBtn);
                codeBox.addView(codeHeader);

                TextView codeView = new TextView(this);
                codeView.setText(codeContent.trim());
                codeView.setTextSize(12.5f);
                codeView.setTextColor(Color.rgb(240, 240, 245));
                codeView.setTypeface(Typeface.MONOSPACE);
                codeView.setTextIsSelectable(true);
                codeView.setPadding(0, dp(8), 0, 0);
                codeBox.addView(codeView);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, dp(8), 0, dp(8));
                container.addView(codeBox, lp);
            } else {
                String text = sections[s];
                String[] lines = text.split("\n");
                ArrayList<String> tableBuffer = new ArrayList<>();
                ArrayList<String> quoteBuffer = new ArrayList<>();

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    String trimmed = line.trim();

                    if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                        flushQuoteBuffer(container, quoteBuffer);
                        tableBuffer.add(trimmed);
                    } else if (trimmed.startsWith(">")) {
                        flushTableBuffer(container, tableBuffer);
                        quoteBuffer.add(trimmed.substring(1).trim());
                    } else {
                        flushTableBuffer(container, tableBuffer);
                        flushQuoteBuffer(container, quoteBuffer);
                        renderMarkdownLine(container, line);
                    }
                }

                flushTableBuffer(container, tableBuffer);
                flushQuoteBuffer(container, quoteBuffer);
            }
        }
    }

    private void flushTableBuffer(LinearLayout container, ArrayList<String> tableBuffer) {
        if (!tableBuffer.isEmpty()) {
            renderMarkdownTable(container, new ArrayList<>(tableBuffer));
            tableBuffer.clear();
        }
    }

    private void flushQuoteBuffer(LinearLayout container, ArrayList<String> quoteBuffer) {
        if (!quoteBuffer.isEmpty()) {
            LinearLayout quoteBox = new LinearLayout(this);
            quoteBox.setOrientation(LinearLayout.VERTICAL);
            quoteBox.setBackground(cBox(CLAUDE_QUOTE_BG, CLAUDE_TERRACOTTA, 0, 8));
            quoteBox.setPadding(dp(12), dp(8), dp(10), dp(8));

            for (String q : quoteBuffer) {
                SpannableStringBuilder span = parseInlineMarkdown(q);
                TextView qv = new TextView(this);
                qv.setText(span);
                qv.setTextSize(13.5f);
                qv.setTextColor(CLAUDE_TEXT_MUTED);
                qv.setTypeface(Typeface.SERIF, Typeface.ITALIC);
                qv.setPadding(dp(4), dp(2), 0, dp(2));
                quoteBox.addView(qv);
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(6), 0, dp(6));
            container.addView(quoteBox, lp);
            quoteBuffer.clear();
        }
    }

    private void renderMarkdownLine(LinearLayout container, String line) {
        if (line.trim().isEmpty()) return;

        String trimmed = line.trim();
        if (trimmed.startsWith("#### ")) {
            TextView h4 = cText(trimmed.substring(5), 14, CLAUDE_TEXT_MAIN, true, true);
            h4.setPadding(0, dp(6), 0, dp(2));
            container.addView(h4);
        } else if (trimmed.startsWith("### ")) {
            TextView h3 = cText(trimmed.substring(4), 15.5f, CLAUDE_TEXT_MAIN, true, true);
            h3.setPadding(0, dp(8), 0, dp(2));
            container.addView(h3);
        } else if (trimmed.startsWith("## ")) {
            TextView h2 = cText(trimmed.substring(3), 17.5f, CLAUDE_TEXT_MAIN, true, true);
            h2.setPadding(0, dp(10), 0, dp(3));
            container.addView(h2);
        } else if (trimmed.startsWith("# ")) {
            TextView h1 = cText(trimmed.substring(2), 20f, CLAUDE_TEXT_MAIN, true, true);
            h1.setPadding(0, dp(12), 0, dp(4));
            container.addView(h1);
        } else if (trimmed.startsWith("---") || trimmed.startsWith("***") || trimmed.startsWith("___")) {
            View divider = new View(this);
            divider.setBackgroundColor(CLAUDE_BORDER);
            LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(-1, dp(1));
            lpDiv.setMargins(0, dp(10), 0, dp(10));
            container.addView(divider, lpDiv);
        } else {
            SpannableStringBuilder span = parseInlineMarkdown(line);
            TextView p = new TextView(this);
            p.setText(span);
            p.setTextSize(14.5f);
            p.setTextColor(CLAUDE_TEXT_MAIN);
            p.setLineSpacing(0, 1.28f);
            p.setTextIsSelectable(true);
            p.setPadding(0, dp(3), 0, dp(3));

            final String rawLine = line;
            p.setOnLongClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Chat text", rawLine));
                Toast.makeText(MainActivity.this, "Teks disalin ke clipboard", Toast.LENGTH_SHORT).show();
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
            cell.setPadding(dp(10), dp(4), dp(10), dp(4));
            cell.setMinWidth(dp(85));
            headerRow.addView(cell, new LinearLayout.LayoutParams(-2, -2));
        }
        tableLayout.addView(headerRow);

        // Data Rows
        for (int r = 1; r < tableLines.size(); r++) {
            String rowLine = tableLines.get(r);
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
                cell.setPadding(dp(10), dp(4), dp(10), dp(4));
                cell.setMinWidth(dp(85));
                dataRow.addView(cell, new LinearLayout.LayoutParams(-2, -2));
            }
            tableLayout.addView(dataRow);
        }

        hScroll.addView(tableLayout);
        LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams(-1, -2);
        lpH.setMargins(0, dp(8), 0, dp(10));
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
        if (line.trim().startsWith("* ") || line.trim().startsWith("- ") || line.trim().startsWith("+ ")) {
            line = "  •  " + line.trim().substring(2);
        } else if (line.trim().startsWith("- [x]") || line.trim().startsWith("* [x]")) {
            line = "  ☑  " + line.trim().substring(5);
        } else if (line.trim().startsWith("- [ ]") || line.trim().startsWith("* [ ]")) {
            line = "  ☐  " + line.trim().substring(5);
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder(line);

        // Bold (**bold** or __bold__)
        Pattern boldPat = Pattern.compile("(\\*\\*|__)(.+?)\\1");
        Matcher boldMat = boldPat.matcher(ssb.toString());
        while (boldMat.find()) {
            int start = boldMat.start();
            int end = boldMat.end();
            String inner = boldMat.group(2);
            ssb.replace(start, end, inner);
            ssb.setSpan(new StyleSpan(Typeface.BOLD), start, start + inner.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            boldMat = boldPat.matcher(ssb.toString());
        }

        // Italic (*italic* or _italic_)
        Pattern italicPat = Pattern.compile("(?<!\\*|_)((\\*|_))(?!\\*|_)(.+?)\\1");
        Matcher italicMat = italicPat.matcher(ssb.toString());
        while (italicMat.find()) {
            int start = italicMat.start();
            int end = italicMat.end();
            String inner = italicMat.group(3);
            ssb.replace(start, end, inner);
            ssb.setSpan(new StyleSpan(Typeface.ITALIC), start, start + inner.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            italicMat = italicPat.matcher(ssb.toString());
        }

        // Strikethrough (~~text~~)
        Pattern strikePat = Pattern.compile("~~(.+?)~~");
        Matcher strikeMat = strikePat.matcher(ssb.toString());
        while (strikeMat.find()) {
            int start = strikeMat.start();
            int end = strikeMat.end();
            String inner = strikeMat.group(1);
            ssb.replace(start, end, inner);
            ssb.setSpan(new StrikethroughSpan(), start, start + inner.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            strikeMat = strikePat.matcher(ssb.toString());
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
                    syncLiveExecution();
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
                        if (sidebarStatusDot != null) sidebarStatusDot.setBackground(cBox(CLAUDE_GREEN, 0, 0, 4));
                        if (sidebarStatusText != null) sidebarStatusText.setText("  Gateway Online");
                        Toast.makeText(this, "Gateway online & connected!", Toast.LENGTH_SHORT).show();
                    } else {
                        if (sidebarStatusDot != null) sidebarStatusDot.setBackground(cBox(CLAUDE_RED, 0, 0, 4));
                        if (sidebarStatusText != null) sidebarStatusText.setText("  HTTP " + code);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (sidebarStatusDot != null) sidebarStatusDot.setBackground(cBox(CLAUDE_RED, 0, 0, 4));
                    if (sidebarStatusText != null) sidebarStatusText.setText("  Gateway Offline");
                    Toast.makeText(this, "Gateway offline: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showConnectionDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(10), dp(22), dp(10));

        LinearLayout scanBtn = new LinearLayout(this);
        scanBtn.setOrientation(LinearLayout.HORIZONTAL);
        scanBtn.setGravity(Gravity.CENTER);
        scanBtn.setBackground(cBox(CLAUDE_TERRACOTTA, 0, 0, 12));
        scanBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
        scanBtn.addView(cIcon(R.drawable.ic_qr_code, 20, Color.WHITE));
        TextView scanLbl = cText("  Scan QR Code dari Terminal", 13.5f, Color.WHITE, true, false);
        scanBtn.addView(scanLbl);
        scanBtn.setOnClickListener(v -> startQrScanner());
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(-1, dp(44));
        lpBtn.setMargins(0, 0, 0, dp(8));
        form.addView(scanBtn, lpBtn);

        LinearLayout pasteBtn = new LinearLayout(this);
        pasteBtn.setOrientation(LinearLayout.HORIZONTAL);
        pasteBtn.setGravity(Gravity.CENTER);
        pasteBtn.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 12));
        pasteBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        pasteBtn.addView(cIcon(R.drawable.ic_content_paste, 18, CLAUDE_TEXT_MAIN));
        TextView pasteLbl = cText("  Tempel Link dari Clipboard", 13, CLAUDE_TEXT_MAIN, true, false);
        pasteBtn.addView(pasteLbl);
        pasteBtn.setOnClickListener(v -> pasteFromClipboard());
        LinearLayout.LayoutParams lpPBtn = new LinearLayout.LayoutParams(-1, dp(42));
        lpPBtn.setMargins(0, 0, 0, dp(14));
        form.addView(pasteBtn, lpPBtn);

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
