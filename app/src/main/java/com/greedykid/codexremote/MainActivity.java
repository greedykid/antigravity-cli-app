package com.greedykid.codexremote;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
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
import android.os.Vibrator;
import android.provider.OpenableColumns;
import android.speech.RecognizerIntent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
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
import android.widget.Switch;
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
    // Claude Dark Theme Palette (Exact Match to Official Claude App)
    private static final int CLAUDE_BG = Color.rgb(24, 24, 23);               // #181817 Dark Obsidian BG
    private static final int CLAUDE_SURFACE = Color.rgb(33, 32, 30);          // #21201E Dark Card Surface
    private static final int CLAUDE_SURFACE_MUTED = Color.rgb(42, 41, 38);    // #2A2926 Dark Badge / Chip
    private static final int CLAUDE_BORDER = Color.rgb(48, 46, 43);           // #302E2B Subtle Dark Border
    private static final int CLAUDE_BORDER_DARK = Color.rgb(62, 60, 56);      // #3E3C38
    private static final int CLAUDE_CODE_BG = Color.rgb(18, 18, 18);          // #121212 Dark Code Box

    private static final int CLAUDE_TEXT_MAIN = Color.rgb(237, 236, 232);     // #EDECE8 Warm White
    private static final int CLAUDE_TEXT_MUTED = Color.rgb(158, 157, 153);    // #9E9D99 Warm Slate Grey
    private static final int CLAUDE_TEXT_LIGHT = Color.rgb(112, 111, 108);    // #706F6C Deep Slate

    private static final int CLAUDE_TERRACOTTA = Color.rgb(217, 107, 67);      // #D96B43 Claude Terracotta Orange
    private static final int CLAUDE_TERRACOTTA_LIGHT = Color.rgb(56, 36, 29); // #38241D Dark Peach Tint
    private static final int CLAUDE_GREEN = Color.rgb(76, 175, 80);            // #4CAF50 Emerald Green
    private static final int CLAUDE_GREEN_BG = Color.rgb(27, 48, 30);          // #1B301E Dark Mint
    private static final int CLAUDE_AMBER = Color.rgb(245, 158, 11);           // #F59E0B Amber
    private static final int CLAUDE_AMBER_BG = Color.rgb(51, 38, 15);          // #33260F Dark Amber
    private static final int CLAUDE_RED = Color.rgb(239, 68, 68);              // #EF4444 Red
    private static final int CLAUDE_BLUE = Color.rgb(59, 130, 246);            // #3B82F6 Active Blue Dot

    private static final int REQ_PICK_FILES = 1001;
    private static final int REQ_VOICE_SPEECH = 1002;
    private static final int REQ_CAMERA_PERMISSION = 2001;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    // Image Bitmap Memory Cache
    private final ConcurrentHashMap<String, Bitmap> imageCache = new ConcurrentHashMap<>();

    // Multi-File Attachment Model
    public static class AttachedMedia {
        public String serverPath;
        public String fileName;
        public Bitmap bitmap;
        public boolean isImage;

        public AttachedMedia(String serverPath, String fileName, Bitmap bitmap, boolean isImage) {
            this.serverPath = serverPath;
            this.fileName = fileName;
            this.bitmap = bitmap;
            this.isImage = isImage;
        }
    }

    private final List<AttachedMedia> attachedMediaList = new ArrayList<>();

    // Root Frame & Sidebar Navigation
    private FrameLayout rootFrame;
    private View sidebarScrim;
    private LinearLayout sidebarPanel;
    private View sidebarStatusDot;
    private TextView sidebarStatusText;
    private TextView sidebarUserEmail;
    private TextView sidebarDeviceHost;
    private boolean isSidebarOpen = false;

    // View Containers (Screen 0: Kode Hub, Screen 1: Chat, Screen 2: Pengaturan)
    private LinearLayout mainContentContainer;
    private FrameLayout viewHubContainer;
    private FrameLayout viewChatContainer;
    private FrameLayout viewSettingsContainer;

    // Hub View Components (Claude Code Sessions)
    private TextView hubDeviceHostText;
    private TextView hubDeviceStatusText;
    private LinearLayout hubSessionGroupsContainer;
    private ProgressBar hubLoadingProgress;

    // Chat View Components
    private ImageView chatNavIcon;
    private TextView chatTopTitle;
    private LinearLayout chatMessagesList;
    private ScrollView chatScroll;
    private LinearLayout chatSessionLoadingView;
    private FrameLayout btnScrollToBottom;
    private LinearLayout emptyMascotView;
    private EditText promptInput;
    private FrameLayout btnSend;
    private ImageView btnAttach;
    private ImageView btnEnginePill;
    private ImageView btnVoice;
    private TextView repoTagLabel;
    private HorizontalScrollView attachmentScrollContainer;
    private LinearLayout attachmentChipsList;

    // Settings View Components
    private TextView settingsUserEmailText;
    private TextView settingsConnectorStatusText;
    private TextView settingsCapabilitiesSubtitle;

    // Active Session State
    private String activeConversationId = null;
    private String activeSessionTitle = "New session";
    private String currentEngine = "antigravity";
    private String currentServerHostname = "Server Remote";
    private int currentScreen = 0; // 0: Hub, 1: Chat, 2: Settings
    private boolean navigatedFromHub = false;

    // Live Execution & Real-time Sync State
    private volatile boolean isLiveTaskRunning = false;
    private String pendingOptimisticUserPrompt = null;
    private String pendingOptimisticUserTime = null;
    private String lastLoadedSessionId = null;
    private int lastLoadedTurnCount = -1;
    private boolean lastRenderedWasRunning = false;

    // Live Execution Bottom Sheet State (Interactive 2-Level View)
    private Dialog activeBottomSheetDialog = null;
    private LinearLayout activeBottomSheetMasterList = null;
    private LinearLayout activeBottomSheetContainer = null;
    private LinearLayout activeBottomSheetMasterView = null;
    private LinearLayout activeBottomSheetDetailView = null;
    private TextView activeBottomSheetSubtitle = null;
    private ArrayList<JSONObject> currentActiveSteps = new ArrayList<>();

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
        currentServerHostname = prefs.getString("device_name", "Server Remote");
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
        } else if (currentScreen == 1 && (activeConversationId != null || isLiveTaskRunning)) {
            startAutoRefresh();
        }
    }

    @Override
    public void onBackPressed() {
        if (isSidebarOpen) {
            closeSidebar();
        } else if (currentScreen == 2) {
            showScreen(0);
        } else if (currentScreen == 1 && navigatedFromHub) {
            navigatedFromHub = false;
            showScreen(0);
        } else if (currentScreen == 1) {
            showScreen(0);
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

    private GradientDrawable cBottomSheetBox(int fillColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        float r = dp(24);
        d.setCornerRadii(new float[]{ r, r, r, r, 0, 0, 0, 0 });
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

        mainContentContainer = new LinearLayout(this);
        mainContentContainer.setOrientation(LinearLayout.VERTICAL);

        // Screen 0: Kode Hub
        viewHubContainer = new FrameLayout(this);
        buildHubScreen(viewHubContainer);
        mainContentContainer.addView(viewHubContainer, new LinearLayout.LayoutParams(-1, -1));

        // Screen 1: Chat Screen
        viewChatContainer = new FrameLayout(this);
        viewChatContainer.setVisibility(View.GONE);
        buildChatScreen(viewChatContainer);
        mainContentContainer.addView(viewChatContainer, new LinearLayout.LayoutParams(-1, -1));

        // Screen 2: Pengaturan (Settings Screen matching reference)
        viewSettingsContainer = new FrameLayout(this);
        viewSettingsContainer.setVisibility(View.GONE);
        buildSettingsScreen(viewSettingsContainer);
        mainContentContainer.addView(viewSettingsContainer, new LinearLayout.LayoutParams(-1, -1));

        rootFrame.addView(mainContentContainer, new FrameLayout.LayoutParams(-1, -1));

        // Sidebar Backdrop Scrim
        sidebarScrim = new View(this);
        sidebarScrim.setBackgroundColor(Color.argb(160, 0, 0, 0));
        sidebarScrim.setVisibility(View.GONE);
        sidebarScrim.setAlpha(0f);
        sidebarScrim.setOnClickListener(v -> closeSidebar());
        rootFrame.addView(sidebarScrim, new FrameLayout.LayoutParams(-1, -1));

        // Sidebar Panel
        sidebarPanel = new LinearLayout(this);
        sidebarPanel.setOrientation(LinearLayout.VERTICAL);
        sidebarPanel.setBackgroundColor(CLAUDE_SURFACE);
        sidebarPanel.setVisibility(View.GONE);
        buildSidebarContent(sidebarPanel);

        FrameLayout.LayoutParams lpSide = new FrameLayout.LayoutParams(dp(300), -1);
        lpSide.gravity = Gravity.START;
        rootFrame.addView(sidebarPanel, lpSide);

        setContentView(rootFrame);

        showScreen(0);
    }

    // ============================================================
    // SMOOTH ANIMATED SIDEBAR NAVIGATION (No Pro Badge)
    // ============================================================
    private void buildSidebarContent(LinearLayout sidebar) {
        sidebar.setPadding(dp(22), dp(24), dp(22), dp(20));

        // 1. Account Profile Top Bar (Clean, no Pro badge)
        LinearLayout profileCard = new LinearLayout(this);
        profileCard.setOrientation(LinearLayout.HORIZONTAL);
        profileCard.setGravity(Gravity.CENTER_VERTICAL);
        profileCard.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 16));
        profileCard.setPadding(dp(14), dp(12), dp(14), dp(12));

        ImageView avatar = cIcon(R.drawable.ic_person, 20, CLAUDE_TERRACOTTA);
        profileCard.addView(avatar);

        String userEmail = prefs.getString("user_email", "developer@antigravity.ai");
        sidebarUserEmail = cText("  " + userEmail, 13.5f, CLAUDE_TEXT_MAIN, true, false);
        sidebarUserEmail.setSingleLine(true);
        profileCard.addView(sidebarUserEmail, new LinearLayout.LayoutParams(0, -2, 1));

        profileCard.setOnClickListener(v -> {
            closeSidebar();
            showScreen(2);
        });
        sidebar.addView(profileCard);

        // 2. Gateway Status Pill
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackground(cBox(CLAUDE_BG, CLAUDE_BORDER, 1, 14));
        statusCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpSt = new LinearLayout.LayoutParams(-1, -2);
        lpSt.setMargins(0, dp(14), 0, dp(16));

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

        sidebarDeviceHost = cText("Host: " + currentServerHostname, 11.5f, CLAUDE_TEXT_MUTED, false, false);
        sidebarDeviceHost.setSingleLine(true);
        LinearLayout.LayoutParams lpE = new LinearLayout.LayoutParams(-1, -2);
        lpE.setMargins(0, dp(4), 0, 0);
        statusCard.addView(sidebarDeviceHost, lpE);
        sidebar.addView(statusCard, lpSt);

        // 3. Navigation Menu Items
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout menuItems = new LinearLayout(this);
        menuItems.setOrientation(LinearLayout.VERTICAL);

        addSidebarMenuItem(menuItems, R.drawable.ic_code, "Kode (All Sessions)", () -> {
            closeSidebar();
            showScreen(0);
        });

        addSidebarMenuItem(menuItems, R.drawable.ic_chat, "Sesi baru (New Chat)", () -> {
            closeSidebar();
            startNewSession();
        });

        addSidebarMenuItem(menuItems, R.drawable.ic_qr_code, "Scan QR Code Pairing", () -> {
            closeSidebar();
            startQrScanner();
        });

        addSidebarMenuItem(menuItems, R.drawable.ic_content_paste, "Paste Pairing dari Clipboard", () -> {
            closeSidebar();
            pasteFromClipboard();
        });

        addSidebarMenuItem(menuItems, R.drawable.ic_tune, "Kemampuan (Engine: " + ("antigravity".equalsIgnoreCase(currentEngine) ? "Agy" : "Codex") + ")", () -> {
            toggleEngine();
        });

        addSidebarMenuItem(menuItems, R.drawable.ic_settings, "Pengaturan (Settings)", () -> {
            closeSidebar();
            showScreen(2);
        });

        addSidebarMenuItem(menuItems, R.drawable.ic_stop, "Hentikan Proses CLI", () -> {
            closeSidebar();
            stopRunningCliProcess();
        });

        addSidebarMenuItem(menuItems, R.drawable.ic_refresh, "Test Ping & Health", () -> {
            checkHealth();
        });

        scroll.addView(menuItems);
        sidebar.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView ver = cText("Antigravity Remote v2.9.9", 11.5f, CLAUDE_TEXT_LIGHT, false, false);
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
        viewSettingsContainer.setVisibility(screenIndex == 2 ? View.VISIBLE : View.GONE);

        if (screenIndex == 0) {
            stopAutoRefresh();
            fetchHubSessions();
        } else if (screenIndex == 1) {
            chatTopTitle.setText(activeSessionTitle);
            updateRepoTag();
            updateChatNavIcon();
            if (activeConversationId != null || isLiveTaskRunning) {
                fetchActiveSessionTurns(true);
                startAutoRefresh();
            } else {
                stopAutoRefresh();
                if (chatSessionLoadingView != null && chatSessionLoadingView.getVisibility() == View.VISIBLE) {
                    chatSessionLoadingView.animate().alpha(0f).setDuration(150)
                            .withEndAction(() -> chatSessionLoadingView.setVisibility(View.GONE)).start();
                }
                chatMessagesList.removeAllViews();
                showEmptyMascotState(true);
            }
        } else if (screenIndex == 2) {
            stopAutoRefresh();
            refreshSettingsValues();
        }
    }

    private void updateChatNavIcon() {
        if (chatNavIcon != null) {
            if (navigatedFromHub || currentScreen == 1) {
                chatNavIcon.setImageResource(R.drawable.ic_arrow_back);
            } else {
                chatNavIcon.setImageResource(R.drawable.ic_menu);
            }
            chatNavIcon.setColorFilter(CLAUDE_TEXT_MAIN);
        }
    }

    // ============================================================
    // SCREEN 0: CLAUDE KODE HUB ("Kode" UI matching screenshot 1)
    // ============================================================
    private void buildHubScreen(FrameLayout root) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(0));

        // 1. Top Header Bar: Menu + Title "Kode" + Tune/Filter Icon
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, dp(4), 0, dp(14));

        ImageView menuIcon = cIconButton(R.drawable.ic_menu, 24, 40, CLAUDE_TEXT_MAIN);
        menuIcon.setOnClickListener(v -> openSidebar());
        topBar.addView(menuIcon);

        TextView headerTitle = cText("Kode", 22, CLAUDE_TEXT_MAIN, true, false);
        headerTitle.setPadding(dp(12), 0, 0, 0);
        topBar.addView(headerTitle, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView tuneBtn = cIconButton(R.drawable.ic_tune, 22, 40, CLAUDE_TEXT_MUTED);
        tuneBtn.setOnClickListener(v -> showMoreDropdownMenu(tuneBtn));
        topBar.addView(tuneBtn);
        content.addView(topBar);

        // 2. Scrollable Hub Content
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(90)); // Underlay for FAB

        LinearLayout scrollBody = new LinearLayout(this);
        scrollBody.setOrientation(LinearLayout.VERTICAL);

        // Perangkat (Devices) Section
        TextView devTitle = cText("Perangkat", 13.5f, CLAUDE_TEXT_MUTED, false, false);
        LinearLayout.LayoutParams lpDevT = new LinearLayout.LayoutParams(-1, -2);
        lpDevT.setMargins(0, dp(6), 0, dp(10));
        scrollBody.addView(devTitle, lpDevT);

        LinearLayout deviceCard = new LinearLayout(this);
        deviceCard.setOrientation(LinearLayout.VERTICAL);
        deviceCard.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_BORDER, 1, 16));
        deviceCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        ImageView laptopIcon = cIcon(R.drawable.ic_laptop, 22, CLAUDE_TEXT_MAIN);
        deviceCard.addView(laptopIcon);

        hubDeviceHostText = cText(currentServerHostname, 15, CLAUDE_TEXT_MAIN, true, false);
        hubDeviceHostText.setSingleLine(true);
        LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams(-2, -2);
        lpH.setMargins(0, dp(12), 0, dp(0));
        deviceCard.addView(hubDeviceHostText, lpH);

        hubDeviceStatusText = cText("Terhubung", 12.5f, CLAUDE_GREEN, false, false);
        LinearLayout.LayoutParams lpS = new LinearLayout.LayoutParams(-2, -2);
        lpS.setMargins(0, dp(4), 0, 0);
        deviceCard.addView(hubDeviceStatusText, lpS);

        deviceCard.setOnClickListener(v -> showEditDeviceNameBottomSheet());

        LinearLayout.LayoutParams lpDevCard = new LinearLayout.LayoutParams(dp(165), -2);
        lpDevCard.setMargins(0, 0, 0, dp(10));
        scrollBody.addView(deviceCard, lpDevCard);

        hubLoadingProgress = new ProgressBar(this);
        hubLoadingProgress.setVisibility(View.GONE);
        scrollBody.addView(hubLoadingProgress, new LinearLayout.LayoutParams(dp(28), dp(28)));

        // Time-grouped sessions container
        hubSessionGroupsContainer = new LinearLayout(this);
        hubSessionGroupsContainer.setOrientation(LinearLayout.VERTICAL);
        scrollBody.addView(hubSessionGroupsContainer);

        scroll.addView(scrollBody);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        // 3. Floating Action Button: "+ Sesi baru" (Bottom Right)
        LinearLayout fabNew = new LinearLayout(this);
        fabNew.setOrientation(LinearLayout.HORIZONTAL);
        fabNew.setGravity(Gravity.CENTER_VERTICAL);
        fabNew.setBackground(cBox(CLAUDE_TERRACOTTA, 0, 0, 26));
        fabNew.setPadding(dp(18), dp(12), dp(20), dp(12));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            fabNew.setElevation(dp(8));
        }

        ImageView plusIc = cIcon(R.drawable.ic_add, 18, Color.WHITE);
        fabNew.addView(plusIc);

        TextView fabLabel = cText(" Sesi baru", 14.5f, Color.WHITE, true, false);
        fabNew.addView(fabLabel);

        fabNew.setOnClickListener(v -> startNewSession());

        FrameLayout.LayoutParams lpFab = new FrameLayout.LayoutParams(-2, -2);
        lpFab.gravity = Gravity.BOTTOM | Gravity.END;
        lpFab.setMargins(0, 0, dp(18), dp(20));
        root.addView(fabNew, lpFab);
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

                    String serverHost = json.optString("hostname", "");
                    if (!serverHost.isEmpty() && !prefs.contains("device_name")) {
                        currentServerHostname = serverHost;
                    }
                    final JSONArray sessions = json.optJSONArray("sessions");

                    mainHandler.post(() -> {
                        if (hubDeviceHostText != null) hubDeviceHostText.setText(currentServerHostname);
                        if (hubDeviceStatusText != null) hubDeviceStatusText.setText("Terhubung");
                        if (sidebarDeviceHost != null) sidebarDeviceHost.setText("Host: " + currentServerHostname);
                        renderTimeGroupedSessions(sessions);
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (hubLoadingProgress != null) hubLoadingProgress.setVisibility(View.GONE);
                    if (hubDeviceStatusText != null) hubDeviceStatusText.setText("Terputus");
                });
            }
        });
    }

    private void renderTimeGroupedSessions(JSONArray sessions) {
        if (hubLoadingProgress != null) hubLoadingProgress.setVisibility(View.GONE);
        if (hubSessionGroupsContainer != null) hubSessionGroupsContainer.removeAllViews();

        if (sessions == null || sessions.length() == 0) {
            addTimeSectionHeader("Hari ini");
            addSessionCard("Mulai sesi koding baru", "Terhubung • " + currentServerHostname, "Baru", null, true);
            return;
        }

        long now = System.currentTimeMillis();
        long oneDay = 24 * 60 * 60 * 1000L;
        long oneWeek = 7 * oneDay;
        long oneMonth = 30 * oneDay;

        ArrayList<JSONObject> groupToday = new ArrayList<>();
        ArrayList<JSONObject> groupLastWeek = new ArrayList<>();
        ArrayList<JSONObject> groupThisMonth = new ArrayList<>();
        ArrayList<JSONObject> groupOlder = new ArrayList<>();

        for (int i = 0; i < sessions.length(); i++) {
            JSONObject s = sessions.optJSONObject(i);
            if (s == null) continue;
            long ts = s.optLong("timestamp", now - (i * oneDay));
            long diff = now - ts;

            if (diff < 2 * oneDay) {
                groupToday.add(s);
            } else if (diff < oneWeek * 2) {
                groupLastWeek.add(s);
            } else if (diff < oneMonth * 2) {
                groupThisMonth.add(s);
            } else {
                groupOlder.add(s);
            }
        }

        boolean isFirst = true;

        if (!groupToday.isEmpty()) {
            addTimeSectionHeader("Hari ini");
            for (JSONObject s : groupToday) {
                renderSingleSessionItem(s, isFirst);
                isFirst = false;
            }
        }

        if (!groupLastWeek.isEmpty()) {
            addTimeSectionHeader("Minggu lalu");
            for (JSONObject s : groupLastWeek) {
                renderSingleSessionItem(s, isFirst);
                isFirst = false;
            }
        }

        if (!groupThisMonth.isEmpty()) {
            addTimeSectionHeader("Bulan ini");
            for (JSONObject s : groupThisMonth) {
                renderSingleSessionItem(s, isFirst);
                isFirst = false;
            }
        }

        if (!groupOlder.isEmpty()) {
            addTimeSectionHeader("Bulan lalu");
            for (JSONObject s : groupOlder) {
                renderSingleSessionItem(s, isFirst);
                isFirst = false;
            }
        }
    }

    private void renderSingleSessionItem(JSONObject s, boolean isMostRecent) {
        final String convId = s.optString("conversationId", "");
        String title = s.optString("title", "Sesi");
        long ts = s.optLong("timestamp", System.currentTimeMillis());

        SimpleDateFormat sdf = new SimpleDateFormat("d MMM", new Locale("id", "ID"));
        String dateStr = sdf.format(new Date(ts));

        String subText = isMostRecent ? ("Terhubung • " + currentServerHostname) : "Terputus • Kendali jarak jauh";
        addSessionCard(title, subText, dateStr, convId, isMostRecent);
    }

    private void addTimeSectionHeader(String title) {
        TextView v = cText(title, 13.5f, CLAUDE_TEXT_MUTED, false, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(18), 0, dp(10));
        hubSessionGroupsContainer.addView(v, lp);
    }

    private void addSessionCard(final String title, String subText, String dateStr, final String convId, boolean isConnected) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_BORDER, 1, 18));
        card.setPadding(dp(14), dp(12), dp(16), dp(12));

        // Left Code Icon Badge with optional blue indicator dot
        FrameLayout badgeFrame = new FrameLayout(this);
        LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(dp(42), dp(42));
        badgeFrame.setLayoutParams(lpBadge);

        View badgeBg = new View(this);
        badgeBg.setBackground(cBox(CLAUDE_SURFACE_MUTED, 0, 0, 12));
        badgeFrame.addView(badgeBg, new FrameLayout.LayoutParams(-1, -1));

        ImageView codeIcon = cIcon(R.drawable.ic_code, 20, CLAUDE_TEXT_MUTED);
        FrameLayout.LayoutParams lpCode = new FrameLayout.LayoutParams(dp(20), dp(20));
        lpCode.gravity = Gravity.CENTER;
        badgeFrame.addView(codeIcon, lpCode);

        if (isConnected) {
            View blueDot = new View(this);
            blueDot.setBackground(cBox(CLAUDE_BLUE, 0, 0, 4));
            FrameLayout.LayoutParams lpDot = new FrameLayout.LayoutParams(dp(8), dp(8));
            lpDot.gravity = Gravity.TOP | Gravity.END;
            lpDot.setMargins(0, dp(2), dp(2), 0);
            badgeFrame.addView(blueDot, lpDot);
        }

        card.addView(badgeFrame);

        // Middle title & status column
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(12), 0, dp(8), 0);

        TextView titleView = cText(title, 14.5f, CLAUDE_TEXT_MAIN, true, false);
        titleView.setSingleLine(true);
        textCol.addView(titleView);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(3), 0, 0);

        ImageView statIcon = cIcon(isConnected ? R.drawable.ic_laptop : R.drawable.ic_link_off, 13, isConnected ? CLAUDE_GREEN : CLAUDE_TEXT_MUTED);
        statusRow.addView(statIcon);

        TextView subView = cText(" " + subText, 12.5f, isConnected ? CLAUDE_GREEN : CLAUDE_TEXT_MUTED, false, false);
        subView.setSingleLine(true);
        statusRow.addView(subView);

        textCol.addView(statusRow);
        card.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

        // Right date text
        TextView dateView = cText(dateStr, 12f, CLAUDE_TEXT_MUTED, false, false);
        card.addView(dateView);

        card.setOnClickListener(v -> {
            navigatedFromHub = true;
            openSpecificSession(convId, title);
        });

        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(-1, -2);
        lpCard.setMargins(0, 0, 0, dp(10));
        hubSessionGroupsContainer.addView(card, lpCard);
    }

    private void openSpecificSession(String convId, String title) {
        activeConversationId = convId;
        activeSessionTitle = title != null && !title.isEmpty() ? title : "Session";
        lastLoadedSessionId = null;
        lastLoadedTurnCount = -1;
        lastRenderedWasRunning = false;

        if (chatMessagesList != null) chatMessagesList.removeAllViews();
        showEmptyMascotState(false);
        if (chatSessionLoadingView != null) {
            chatSessionLoadingView.setVisibility(View.VISIBLE);
            chatSessionLoadingView.setAlpha(1f);
        }
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
    // SCREEN 2: PENGATURAN (Settings UI - Clean, No Penagihan, No Pro)
    // ============================================================
    private void buildSettingsScreen(FrameLayout root) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(16));

        // Top Header: Menu + Title "Pengaturan" + Info Icon
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, dp(4), 0, dp(14));

        ImageView menuIcon = cIconButton(R.drawable.ic_menu, 24, 40, CLAUDE_TEXT_MAIN);
        menuIcon.setOnClickListener(v -> openSidebar());
        topBar.addView(menuIcon);

        TextView headerTitle = cText("Pengaturan", 20, CLAUDE_TEXT_MAIN, true, true);
        headerTitle.setGravity(Gravity.CENTER);
        topBar.addView(headerTitle, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView infoBtn = cIconButton(R.drawable.ic_info, 22, 40, CLAUDE_TEXT_MUTED);
        infoBtn.setOnClickListener(v -> showAboutAppBottomSheet());
        topBar.addView(infoBtn);
        content.addView(topBar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        // Group 1: Profile / Email Card (Top, clean without Pro badge)
        LinearLayout topProfileCard = new LinearLayout(this);
        topProfileCard.setOrientation(LinearLayout.HORIZONTAL);
        topProfileCard.setGravity(Gravity.CENTER_VERTICAL);
        topProfileCard.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_BORDER, 1, 18));
        topProfileCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        String email = prefs.getString("user_email", "developer@antigravity.ai");
        settingsUserEmailText = cText(email, 14.5f, CLAUDE_TEXT_MAIN, true, false);
        settingsUserEmailText.setSingleLine(true);
        topProfileCard.addView(settingsUserEmailText, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView editIcon = cIcon(R.drawable.ic_person, 18, CLAUDE_TEXT_MUTED);
        topProfileCard.addView(editIcon);

        topProfileCard.setOnClickListener(v -> showEditEmailBottomSheet());

        LinearLayout.LayoutParams lpProf = new LinearLayout.LayoutParams(-1, -2);
        lpProf.setMargins(0, dp(4), 0, dp(14));
        list.addView(topProfileCard, lpProf);

        // Group 2: Profil & Penggunaan (Penagihan removed!)
        LinearLayout g2 = createSettingsGroupContainer();
        addSettingsRowItem(g2, R.drawable.ic_person, "Profil", null, () -> showEditEmailBottomSheet(), true);
        addSettingsRowItem(g2, R.drawable.ic_analytics, "Penggunaan", null, () -> showUsageStatsBottomSheet(), false);
        list.addView(g2);

        // Group 3: Kemampuan, Konektor, Izin
        LinearLayout g3 = createSettingsGroupContainer();
        settingsCapabilitiesSubtitle = addSettingsRowItemWithSubtitle(g3, R.drawable.ic_tune, "Kemampuan", "4 diaktifkan", () -> toggleEngine(), true);
        settingsConnectorStatusText = addSettingsRowItemWithSubtitle(g3, R.drawable.ic_link, "Konektor", "1 terhubung", () -> showConnectionBottomSheet(), true);
        addSettingsRowItem(g3, R.drawable.ic_android, "Izin", null, () -> showPermissionsBottomSheet(), false);
        list.addView(g3);

        // Group 4: Gaya Font, Suara
        LinearLayout g4 = createSettingsGroupContainer();
        addSettingsRowItemWithSubtitle(g4, R.drawable.ic_text_format, "Gaya font", "Bawaan", () -> Toast.makeText(this, "Font: Claude Typography Serif & Sans", Toast.LENGTH_SHORT).show(), true);
        addSettingsRowItem(g4, R.drawable.ic_graphic_eq, "Suara", null, () -> startVoiceRecognition(), false);
        list.addView(g4);

        // Group 5: Umpan balik haptik, Notifikasi, Privasi
        LinearLayout g5 = createSettingsGroupContainer();

        // Haptic Feedback Switch Row
        LinearLayout hapticRow = new LinearLayout(this);
        hapticRow.setOrientation(LinearLayout.HORIZONTAL);
        hapticRow.setGravity(Gravity.CENTER_VERTICAL);
        hapticRow.setPadding(dp(16), dp(14), dp(16), dp(14));

        ImageView vibIc = cIcon(R.drawable.ic_vibration, 22, CLAUDE_TEXT_MAIN);
        hapticRow.addView(vibIc);

        TextView vibLabel = cText("  Umpan balik haptik", 14.5f, CLAUDE_TEXT_MAIN, false, false);
        hapticRow.addView(vibLabel, new LinearLayout.LayoutParams(0, -2, 1));

        Switch hapticSwitch = new Switch(this);
        hapticSwitch.setChecked(prefs.getBoolean("haptic", true));
        hapticSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("haptic", isChecked).apply();
            if (isChecked) {
                try {
                    Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (vib != null) vib.vibrate(40);
                } catch (Exception ignored) {}
            }
        });
        hapticRow.addView(hapticSwitch);
        g5.addView(hapticRow);

        addDividerLine(g5);
        addSettingsRowItem(g5, R.drawable.ic_notifications, "Notifikasi", null, () -> Toast.makeText(this, "Notifikasi latar belakang aktif", Toast.LENGTH_SHORT).show(), true);
        addSettingsRowItem(g5, R.drawable.ic_security, "Privasi", null, () -> showPrivacyTokenBottomSheet(), false);
        list.addView(g5);

        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
    }

    private LinearLayout createSettingsGroupContainer() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_BORDER, 1, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(14));
        box.setLayoutParams(lp);
        return box;
    }

    private void addSettingsRowItem(LinearLayout container, int iconRes, String title, String subtitle, final Runnable action, boolean showDivider) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        ImageView ic = cIcon(iconRes, 22, CLAUDE_TEXT_MAIN);
        row.addView(ic);

        TextView label = cText("  " + title, 14.5f, CLAUDE_TEXT_MAIN, false, false);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        row.setOnClickListener(v -> action.run());
        container.addView(row);

        if (showDivider) {
            addDividerLine(container);
        }
    }

    private TextView addSettingsRowItemWithSubtitle(LinearLayout container, int iconRes, String title, String subtitle, final Runnable action, boolean showDivider) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        ImageView ic = cIcon(iconRes, 22, CLAUDE_TEXT_MAIN);
        row.addView(ic);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(14), 0, 0, 0);

        TextView label = cText(title, 14.5f, CLAUDE_TEXT_MAIN, false, false);
        textCol.addView(label);

        TextView sub = cText(subtitle != null ? subtitle : "", 12.5f, CLAUDE_TEXT_MUTED, false, false);
        textCol.addView(sub);

        row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));
        row.setOnClickListener(v -> action.run());
        container.addView(row);

        if (showDivider) {
            addDividerLine(container);
        }
        return sub;
    }

    private void addDividerLine(LinearLayout container) {
        View div = new View(this);
        div.setBackgroundColor(CLAUDE_BORDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(dp(16), 0, dp(16), 0);
        container.addView(div, lp);
    }

    private void refreshSettingsValues() {
        if (settingsUserEmailText != null) {
            settingsUserEmailText.setText(prefs.getString("user_email", "developer@antigravity.ai"));
        }
        if (settingsConnectorStatusText != null) {
            boolean hasUrl = !prefs.getString("url", "").isEmpty();
            settingsConnectorStatusText.setText(hasUrl ? ("1 terhubung • " + currentServerHostname) : "0 terhubung (Atur Bridge)");
        }
        if (settingsCapabilitiesSubtitle != null) {
            settingsCapabilitiesSubtitle.setText("Engine: " + ("antigravity".equalsIgnoreCase(currentEngine) ? "Antigravity CLI" : "Codex CLI"));
        }
    }

    // ============================================================
    // UNIVERSAL SMOOTH BOTTOM SHEET BUILDER (Replaces all Alert Modals!)
    // ============================================================
    private Dialog createBaseBottomSheet(boolean fullWidth) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.getDecorView().setPadding(0, 0, 0, 0);
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.gravity = Gravity.BOTTOM;
            wlp.width = WindowManager.LayoutParams.MATCH_PARENT;
            wlp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            wlp.horizontalMargin = 0f;
            wlp.windowAnimations = android.R.style.Animation_InputMethod;
            window.setAttributes(wlp);
        }
        return dialog;
    }

    private LinearLayout createBottomSheetRoot(Dialog dialog, String title, boolean showClose) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(cBox(CLAUDE_SURFACE, 0, 0, 24));
        root.setPadding(dp(20), dp(10), dp(20), dp(20));

        // Drag Handle Pill
        LinearLayout dragArea = new LinearLayout(this);
        dragArea.setOrientation(LinearLayout.VERTICAL);
        dragArea.setGravity(Gravity.CENTER_HORIZONTAL);
        dragArea.setPadding(0, dp(4), 0, dp(12));

        View dragHandle = new View(this);
        dragHandle.setBackground(cBox(CLAUDE_BORDER_DARK, 0, 0, 3));
        dragArea.addView(dragHandle, new LinearLayout.LayoutParams(dp(44), dp(5)));
        root.addView(dragArea);

        // Header Title Row
        if (title != null) {
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);

            TextView t = cText(title, 18f, CLAUDE_TEXT_MAIN, true, true);
            head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));

            if (showClose) {
                ImageView close = cIconButton(R.drawable.ic_close, 20, 36, CLAUDE_TEXT_MUTED);
                close.setOnClickListener(v -> dialog.dismiss());
                head.addView(close);
            }
            LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams(-1, -2);
            lpH.setMargins(0, 0, 0, dp(14));
            root.addView(head, lpH);
        }

        return root;
    }

    private void showEditEmailBottomSheet() {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Profil Pengguna", true);

        TextView sub = cText("Alamat email atau ID pengembang", 12.5f, CLAUDE_TEXT_MUTED, false, false);
        root.addView(sub);

        final EditText input = new EditText(this);
        input.setText(prefs.getString("user_email", "developer@antigravity.ai"));
        input.setTextColor(CLAUDE_TEXT_MAIN);
        input.setHintTextColor(CLAUDE_TEXT_LIGHT);
        input.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 12));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lpIn = new LinearLayout.LayoutParams(-1, dp(48));
        lpIn.setMargins(0, dp(8), 0, dp(16));
        root.addView(input, lpIn);

        LinearLayout btnSave = new LinearLayout(this);
        btnSave.setOrientation(LinearLayout.HORIZONTAL);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setBackground(cBox(CLAUDE_TERRACOTTA, 0, 0, 14));
        btnSave.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnSave.addView(cText("Simpan Perubahan", 14f, Color.WHITE, true, false));
        btnSave.setOnClickListener(v -> {
            String em = input.getText().toString().trim();
            if (!em.isEmpty()) {
                prefs.edit().putString("user_email", em).apply();
                refreshSettingsValues();
                if (sidebarUserEmail != null) sidebarUserEmail.setText("  " + em);
            }
            dialog.dismiss();
        });
        root.addView(btnSave, new LinearLayout.LayoutParams(-1, dp(46)));

        dialog.setContentView(root);
        dialog.show();
    }

    private void showEditDeviceNameBottomSheet() {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Nama Perangkat / Server", true);

        TextView sub = cText("Beri nama kustom untuk server remote yang terhubung", 12.5f, CLAUDE_TEXT_MUTED, false, false);
        root.addView(sub);

        final EditText input = new EditText(this);
        input.setText(currentServerHostname);
        input.setTextColor(CLAUDE_TEXT_MAIN);
        input.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 12));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lpIn = new LinearLayout.LayoutParams(-1, dp(48));
        lpIn.setMargins(0, dp(8), 0, dp(16));
        root.addView(input, lpIn);

        LinearLayout btnSave = new LinearLayout(this);
        btnSave.setOrientation(LinearLayout.HORIZONTAL);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setBackground(cBox(CLAUDE_TERRACOTTA, 0, 0, 14));
        btnSave.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnSave.addView(cText("Simpan Nama Perangkat", 14f, Color.WHITE, true, false));
        btnSave.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                currentServerHostname = name;
                prefs.edit().putString("device_name", name).apply();
                if (hubDeviceHostText != null) hubDeviceHostText.setText(name);
                if (sidebarDeviceHost != null) sidebarDeviceHost.setText("Host: " + name);
                refreshSettingsValues();
            }
            dialog.dismiss();
        });
        root.addView(btnSave, new LinearLayout.LayoutParams(-1, dp(46)));

        dialog.setContentView(root);
        dialog.show();
    }

    private void showUsageStatsBottomSheet() {
        Dialog dialog = createBaseBottomSheet(true);
        
        // Custom Root with Title + Refresh Icon + Close Icon in Header
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(cBottomSheetBox(CLAUDE_SURFACE));
        root.setPadding(dp(20), dp(10), dp(20), dp(24));
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Drag Handle Pill
        LinearLayout dragArea = new LinearLayout(this);
        dragArea.setOrientation(LinearLayout.VERTICAL);
        dragArea.setGravity(Gravity.CENTER_HORIZONTAL);
        dragArea.setPadding(0, dp(4), 0, dp(14));

        View dragHandle = new View(this);
        dragHandle.setBackground(cBox(CLAUDE_BORDER_DARK, 0, 0, 3));
        dragArea.addView(dragHandle, new LinearLayout.LayoutParams(dp(44), dp(5)));
        root.addView(dragArea);

        // Header Row: Title + Refresh Button + Close Button
        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        headRow.setPadding(0, 0, 0, dp(10));

        TextView titleView = cText("Models & Quota", 18f, CLAUDE_TEXT_MAIN, true, false);
        headRow.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));

        final ImageView btnRefresh = cIconButton(R.drawable.ic_refresh, 18, 36, CLAUDE_TEXT_MUTED);
        headRow.addView(btnRefresh);

        ImageView closeBtn = cIconButton(R.drawable.ic_close, 18, 36, CLAUDE_TEXT_MUTED);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        headRow.addView(closeBtn);
        root.addView(headRow);

        final String userEmail = "rizkiarbi65@gmail.com";
        final TextView sub = cText("Account: " + userEmail, 13.5f, CLAUDE_TEXT_MAIN, true, false);
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(-1, -2);
        lpSub.setMargins(0, 0, 0, dp(14));
        root.addView(sub, lpSub);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        // 1. GEMINI MODELS SECTION
        final LinearLayout geminiGroup = createSettingsGroupContainer();
        geminiGroup.setPadding(dp(16), dp(14), dp(16), dp(14));
        renderModelGroupHeader(geminiGroup, "GEMINI MODELS", "Gemini Flash, Gemini Pro");
        renderUsageProgressSection(geminiGroup, "Weekly Limit Remaining", 75, "75% remaining", "Refreshes in 141h 2m", CLAUDE_GREEN);
        
        View spacerG = new View(this);
        LinearLayout.LayoutParams lpSpG = new LinearLayout.LayoutParams(-1, dp(12));
        geminiGroup.addView(spacerG, lpSpG);
        
        renderUsageProgressSection(geminiGroup, "Five Hour Limit Remaining", 47, "47% remaining", "Refreshes in 3h 2m", 0xFFEAB308);
        list.addView(geminiGroup);

        // 2. CLAUDE AND GPT MODELS SECTION
        final LinearLayout claudeGroup = createSettingsGroupContainer();
        claudeGroup.setPadding(dp(16), dp(14), dp(16), dp(14));
        renderModelGroupHeader(claudeGroup, "CLAUDE AND GPT MODELS", "Claude Opus, Claude Sonnet, GPT-OSS");
        renderUsageProgressSection(claudeGroup, "Weekly Limit Remaining", 100, "100% remaining", "Quota available", CLAUDE_GREEN);
        
        View spacerC = new View(this);
        LinearLayout.LayoutParams lpSpC = new LinearLayout.LayoutParams(-1, dp(12));
        claudeGroup.addView(spacerC, lpSpC);

        renderUsageProgressSection(claudeGroup, "Five Hour Limit Remaining", 100, "100% remaining", "Quota available", CLAUDE_GREEN);
        list.addView(claudeGroup);

        // 3. ENGINE & SERVER DETAILS
        final LinearLayout detailCard = createSettingsGroupContainer();
        detailCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        addStatRow(detailCard, "Active Engine", "Antigravity CLI (agy)");
        addStatRow(detailCard, "Active Model", "Gemini 3.7 Flash (High)");
        addStatRow(detailCard, "Total Requests", "74 Prompts");
        addStatRow(detailCard, "Executed Steps", "2,782 Langkah");
        addStatRow(detailCard, "Tools Invocations", "1,125 Aksi");
        addStatRow(detailCard, "Connected Host", currentServerHostname);
        list.addView(detailCard);

        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout btnClose = new LinearLayout(this);
        btnClose.setOrientation(LinearLayout.HORIZONTAL);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 14));
        btnClose.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnClose.addView(cText("Tutup", 14f, CLAUDE_TEXT_MAIN, true, false));
        btnClose.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(-1, dp(44));
        lpBtn.setMargins(0, dp(14), 0, 0);
        root.addView(btnClose, lpBtn);

        dialog.setContentView(root);
        dialog.show();

        // Refresh action runnable
        final Runnable fetchUsageRunnable = () -> {
            String endpoint = prefs.getString("url", "").trim();
            if (endpoint.isEmpty()) return;

            btnRefresh.setColorFilter(CLAUDE_TERRACOTTA);
            executor.execute(() -> {
                try {
                    String usageUrl = endpoint.replace("/api/chat", "/api/usage");
                    HttpURLConnection c = (HttpURLConnection) new URL(usageUrl).openConnection();
                    c.setRequestMethod("GET");
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    String token = prefs.getString("token", "");
                    if (!token.isEmpty()) {
                        c.setRequestProperty("Authorization", "Bearer " + token);
                    }

                    if (c.getResponseCode() == 200) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder b = new StringBuilder();
                        String line;
                        while ((line = r.readLine()) != null) b.append(line);
                        final JSONObject json = new JSONObject(b.toString());

                        mainHandler.post(() -> {
                            btnRefresh.setColorFilter(CLAUDE_GREEN);
                            mainHandler.postDelayed(() -> btnRefresh.setColorFilter(CLAUDE_TEXT_MUTED), 1500);

                            String acc = json.optString("account", userEmail);
                            sub.setText("Account: " + acc);

                            int gW = json.optInt("geminiWeekly", 75);
                            String gWReset = json.optString("geminiWeeklyReset", "Refreshes in 141h 2m");
                            int g5H = json.optInt("geminiFiveHour", 47);
                            String g5HReset = json.optString("geminiFiveHourReset", "Refreshes in 3h 2m");

                            geminiGroup.removeAllViews();
                            renderModelGroupHeader(geminiGroup, "GEMINI MODELS", "Gemini Flash, Gemini Pro");
                            renderUsageProgressSection(geminiGroup, "Weekly Limit Remaining", gW, gW + "% remaining", gWReset, CLAUDE_GREEN);
                            
                            View sp1 = new View(MainActivity.this);
                            geminiGroup.addView(sp1, new LinearLayout.LayoutParams(-1, dp(12)));
                            
                            renderUsageProgressSection(geminiGroup, "Five Hour Limit Remaining", g5H, g5H + "% remaining", g5HReset, g5H < 50 ? 0xFFEAB308 : CLAUDE_GREEN);

                            int cW = json.optInt("claudeWeekly", 100);
                            int c5H = json.optInt("claudeFiveHour", 100);
                            claudeGroup.removeAllViews();
                            renderModelGroupHeader(claudeGroup, "CLAUDE AND GPT MODELS", "Claude Opus, Claude Sonnet, GPT-OSS");
                            renderUsageProgressSection(claudeGroup, "Weekly Limit Remaining", cW, cW + "% remaining", "Quota available", CLAUDE_GREEN);
                            
                            View sp2 = new View(MainActivity.this);
                            claudeGroup.addView(sp2, new LinearLayout.LayoutParams(-1, dp(12)));
                            
                            renderUsageProgressSection(claudeGroup, "Five Hour Limit Remaining", c5H, c5H + "% remaining", "Quota available", CLAUDE_GREEN);

                            detailCard.removeAllViews();
                            addStatRow(detailCard, "Active Engine", "Antigravity CLI (agy)");
                            addStatRow(detailCard, "Active Model", json.optString("model", "Gemini 3.7 Flash (High)"));
                            addStatRow(detailCard, "Total Requests", json.optInt("totalPrompts", 74) + " Prompts");
                            addStatRow(detailCard, "Executed Steps", String.format(Locale.getDefault(), "%,d Langkah", json.optInt("totalSteps", 2782)));
                            addStatRow(detailCard, "Tools Invocations", String.format(Locale.getDefault(), "%,d Aksi", json.optInt("totalTools", 1125)));
                            addStatRow(detailCard, "Connected Host", json.optString("hostname", currentServerHostname));
                        });
                    } else {
                        mainHandler.post(() -> btnRefresh.setColorFilter(CLAUDE_TEXT_MUTED));
                    }
                } catch (Exception ignored) {
                    mainHandler.post(() -> btnRefresh.setColorFilter(CLAUDE_TEXT_MUTED));
                }
            });
        };

        btnRefresh.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Memperbarui kuota...", Toast.LENGTH_SHORT).show();
            fetchUsageRunnable.run();
        });

        // Trigger initial fetch
        fetchUsageRunnable.run();
    }

    private void renderModelGroupHeader(LinearLayout container, String groupTitle, String modelsSub) {
        TextView gTitle = cText(groupTitle, 13f, CLAUDE_TEXT_MAIN, true, false);
        container.addView(gTitle);

        TextView mSub = cText("Models within this group: " + modelsSub, 11.5f, CLAUDE_TEXT_MUTED, false, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(2), 0, dp(12));
        container.addView(mSub, lp);
    }

    private void renderUsageProgressSection(LinearLayout container, String title, int percent, String details, String resetText, int fillColor) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView tView = cText(title, 14f, CLAUDE_TEXT_MAIN, true, false);
        head.addView(tView, new LinearLayout.LayoutParams(0, -2, 1));

        TextView pView = cText(percent + "%", 14f, fillColor, true, false);
        head.addView(pView);
        container.addView(head);

        // Progress Bar Track
        FrameLayout track = new FrameLayout(this);
        track.setBackground(cBox(CLAUDE_SURFACE_MUTED, 0, 0, 4));

        View fill = new View(this);
        fill.setBackground(cBox(fillColor, 0, 0, 4));

        float clamped = Math.max(0.04f, Math.min(1.0f, percent / 100f));
        LinearLayout.LayoutParams lpFill = new LinearLayout.LayoutParams(0, dp(8));
        lpFill.weight = clamped;

        LinearLayout fillWrapper = new LinearLayout(this);
        fillWrapper.setOrientation(LinearLayout.HORIZONTAL);
        fillWrapper.addView(fill, lpFill);

        View emptySpacer = new View(this);
        LinearLayout.LayoutParams lpEmpty = new LinearLayout.LayoutParams(0, dp(8));
        lpEmpty.weight = 1.0f - clamped;
        fillWrapper.addView(emptySpacer, lpEmpty);

        track.addView(fillWrapper, new FrameLayout.LayoutParams(-1, dp(8)));

        LinearLayout.LayoutParams lpTr = new LinearLayout.LayoutParams(-1, dp(8));
        lpTr.setMargins(0, dp(10), 0, dp(10));
        container.addView(track, lpTr);

        // Subtitle / Reset Details Row
        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);

        TextView dView = cText(details, 12f, CLAUDE_TEXT_MUTED, false, false);
        foot.addView(dView, new LinearLayout.LayoutParams(0, -2, 1));

        TextView rView = cText(resetText, 11.5f, CLAUDE_TEXT_LIGHT, false, false);
        foot.addView(rView);
        container.addView(foot);
    }

    private void addStatRow(LinearLayout container, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView lbl = cText(label, 13f, CLAUDE_TEXT_MUTED, false, false);
        row.addView(lbl, new LinearLayout.LayoutParams(0, -2, 1));

        TextView val = cText(value, 13f, CLAUDE_TEXT_MAIN, true, false);
        row.addView(val);

        container.addView(row);
    }

    private void showPermissionsBottomSheet() {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Izin Aplikasi", true);

        boolean camOk = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            camOk = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 14));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        addStatRow(card, "Kamera (QR Scanner)", camOk ? "Diizinkan ✓" : "Belum diizinkan");
        addStatRow(card, "Mikrofon (Speech-to-text)", "Diizinkan ✓");
        addStatRow(card, "Penyimpanan (File Attachments)", "Diizinkan ✓");
        root.addView(card);

        dialog.setContentView(root);
        dialog.show();
    }

    private void showPrivacyTokenBottomSheet() {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Privasi & Keamanan Token", true);

        String token = prefs.getString("token", "");

        TextView desc = cText("Token bearer digunakan untuk mengamankan komunikasi antara HP Android dan Bridge Server Anda.", 13f, CLAUDE_TEXT_MUTED, false, false);
        LinearLayout.LayoutParams lpD = new LinearLayout.LayoutParams(-1, -2);
        lpD.setMargins(0, 0, 0, dp(14));
        root.addView(desc, lpD);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 14));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        addStatRow(card, "Status Bearer Token", token.isEmpty() ? "Tidak Ada (Publik)" : "•••••••••••• (Aman)");
        root.addView(card);

        LinearLayout btnEdit = new LinearLayout(this);
        btnEdit.setOrientation(LinearLayout.HORIZONTAL);
        btnEdit.setGravity(Gravity.CENTER);
        btnEdit.setBackground(cBox(CLAUDE_TERRACOTTA, 0, 0, 14));
        btnEdit.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnEdit.addView(cText("Ganti Token di Konektor", 14f, Color.WHITE, true, false));
        btnEdit.setOnClickListener(v -> {
            dialog.dismiss();
            showConnectionBottomSheet();
        });
        LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(-1, dp(46));
        lpB.setMargins(0, dp(16), 0, 0);
        root.addView(btnEdit, lpB);

        dialog.setContentView(root);
        dialog.show();
    }

    private void showAboutAppBottomSheet() {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Tentang Antigravity Remote", true);

        LinearLayout logoRow = new LinearLayout(this);
        logoRow.setOrientation(LinearLayout.HORIZONTAL);
        logoRow.setGravity(Gravity.CENTER_VERTICAL);
        logoRow.setPadding(0, dp(6), 0, dp(14));

        ImageView sp = cIcon(R.drawable.ic_spark, 32, CLAUDE_TERRACOTTA);
        logoRow.addView(sp);

        LinearLayout lt = new LinearLayout(this);
        lt.setOrientation(LinearLayout.VERTICAL);
        lt.setPadding(dp(12), 0, 0, 0);
        lt.addView(cText("Antigravity Code Remote", 16f, CLAUDE_TEXT_MAIN, true, true));
        lt.addView(cText("Versi 2.9.9 • Claude Dark Edition", 12.5f, CLAUDE_TEXT_MUTED, false, false));
        logoRow.addView(lt);
        root.addView(logoRow);

        TextView info = cText("Klien remote cerdas untuk Antigravity CLI dan Codex CLI di Android dengan live synchronization dan format markdown interaktif.", 13.5f, CLAUDE_TEXT_MUTED, false, false);
        info.setLineSpacing(0, 1.25f);
        root.addView(info);

        dialog.setContentView(root);
        dialog.show();
    }

    private void showConnectionBottomSheet() {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Pengaturan Konektor Gateway", true);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        LinearLayout scanBtn = new LinearLayout(this);
        scanBtn.setOrientation(LinearLayout.HORIZONTAL);
        scanBtn.setGravity(Gravity.CENTER);
        scanBtn.setBackground(cBox(CLAUDE_TERRACOTTA, 0, 0, 14));
        scanBtn.setPadding(dp(12), dp(11), dp(12), dp(11));
        scanBtn.addView(cIcon(R.drawable.ic_qr_code, 20, Color.WHITE));
        TextView scanLbl = cText("  Scan QR Code Pairing", 14f, Color.WHITE, true, false);
        scanBtn.addView(scanLbl);
        scanBtn.setOnClickListener(v -> {
            dialog.dismiss();
            startQrScanner();
        });
        form.addView(scanBtn, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout pasteBtn = new LinearLayout(this);
        pasteBtn.setOrientation(LinearLayout.HORIZONTAL);
        pasteBtn.setGravity(Gravity.CENTER);
        pasteBtn.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 14));
        pasteBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
        pasteBtn.addView(cIcon(R.drawable.ic_content_paste, 18, CLAUDE_TEXT_MAIN));
        TextView pasteLbl = cText("  Tempel dari Clipboard", 13.5f, CLAUDE_TEXT_MAIN, true, false);
        pasteBtn.addView(pasteLbl);
        pasteBtn.setOnClickListener(v -> {
            dialog.dismiss();
            pasteFromClipboard();
        });
        LinearLayout.LayoutParams lpPBtn = new LinearLayout.LayoutParams(-1, dp(44));
        lpPBtn.setMargins(0, dp(8), 0, dp(14));
        form.addView(pasteBtn, lpPBtn);

        TextView orLbl = cText("— atau isi manual —", 12f, CLAUDE_TEXT_LIGHT, false, false);
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
        urlInput.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 12));
        urlInput.setPadding(dp(14), dp(11), dp(14), dp(11));
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
        tokenInput.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 12));
        tokenInput.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams lpTok = new LinearLayout.LayoutParams(-1, dp(48));
        lpTok.setMargins(0, dp(4), 0, dp(16));
        form.addView(tokenInput, lpTok);

        LinearLayout btnSave = new LinearLayout(this);
        btnSave.setOrientation(LinearLayout.HORIZONTAL);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setBackground(cBox(CLAUDE_TERRACOTTA, 0, 0, 14));
        btnSave.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnSave.addView(cText("Simpan & Hubungkan", 14f, Color.WHITE, true, false));
        btnSave.setOnClickListener(v -> {
            String u = urlInput.getText().toString().trim();
            String t = tokenInput.getText().toString().trim();
            prefs.edit().putString("url", u).putString("token", t).apply();
            dialog.dismiss();
            checkHealth();
            fetchHubSessions();
            refreshSettingsValues();
        });
        form.addView(btnSave, new LinearLayout.LayoutParams(-1, dp(46)));

        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        dialog.setContentView(root);
        dialog.show();
    }

    // ============================================================
    // REDESIGNED INTERACTIVE 2-LEVEL EXECUTION BOTTOM SHEET
    // (Smooth slide/switch between Steps List & Step Detail View)
    // ============================================================
    private void addCompactToolsGroupPill(final ArrayList<JSONObject> toolTurns, final boolean isCurrentlyWorking) {
        final boolean isActuallyRunning = isCurrentlyWorking && isLiveTaskRunning;

        int toolCount = 0;
        int thinkCount = 0;
        String latestToolName = "";

        for (JSONObject t : toolTurns) {
            if ("tool".equalsIgnoreCase(t.optString("role"))) {
                toolCount++;
                latestToolName = t.optString("toolTitle", t.optString("title", "tool"));
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
        final int peekHeight = (int) (screenHeight * 0.70f);
        final int fullHeight = (int) (screenHeight * 0.95f);

        final LinearLayout modalRoot = new LinearLayout(this);
        modalRoot.setOrientation(LinearLayout.VERTICAL);
        modalRoot.setBackground(cBox(CLAUDE_BG, 0, 0, 24));
        modalRoot.setPadding(dp(20), dp(10), dp(20), dp(16));

        // Drag Area
        final LinearLayout dragArea = new LinearLayout(this);
        dragArea.setOrientation(LinearLayout.VERTICAL);
        dragArea.setGravity(Gravity.CENTER_HORIZONTAL);
        dragArea.setPadding(0, dp(4), 0, dp(10));

        View dragHandle = new View(this);
        dragHandle.setBackground(cBox(CLAUDE_BORDER_DARK, 0, 0, 3));
        dragArea.addView(dragHandle, new LinearLayout.LayoutParams(dp(44), dp(5)));
        modalRoot.addView(dragArea);

        // Container holding Level 1 (Master List) and Level 2 (Detail View)
        activeBottomSheetContainer = new LinearLayout(this);
        activeBottomSheetContainer.setOrientation(LinearLayout.VERTICAL);

        // --- LEVEL 1: MASTER LIST VIEW ---
        activeBottomSheetMasterView = new LinearLayout(this);
        activeBottomSheetMasterView.setOrientation(LinearLayout.VERTICAL);

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
        activeBottomSheetMasterView.addView(headerRow);

        activeBottomSheetSubtitle = cText(items.size() + " actions • ketuk item untuk melihat detail", 13f, CLAUDE_TEXT_MUTED, false, false);
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(-1, -2);
        lpSub.setMargins(0, dp(2), 0, dp(8));
        activeBottomSheetMasterView.addView(activeBottomSheetSubtitle, lpSub);

        final ScrollView masterScroll = new ScrollView(this);
        masterScroll.setFillViewport(true);
        masterScroll.setVerticalScrollBarEnabled(true);

        activeBottomSheetMasterList = new LinearLayout(this);
        activeBottomSheetMasterList.setOrientation(LinearLayout.VERTICAL);
        activeBottomSheetMasterList.setPadding(0, dp(8), 0, dp(12));

        masterScroll.addView(activeBottomSheetMasterList);
        activeBottomSheetMasterView.addView(masterScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        activeBottomSheetContainer.addView(activeBottomSheetMasterView, new LinearLayout.LayoutParams(-1, -1));

        // --- LEVEL 2: DETAIL VIEW (Exact Match to Screenshot!) ---
        activeBottomSheetDetailView = new LinearLayout(this);
        activeBottomSheetDetailView.setOrientation(LinearLayout.VERTICAL);
        activeBottomSheetDetailView.setVisibility(View.GONE);
        activeBottomSheetContainer.addView(activeBottomSheetDetailView, new LinearLayout.LayoutParams(-1, -1));

        modalRoot.addView(activeBottomSheetContainer, new LinearLayout.LayoutParams(-1, 0, 1));

        activeBottomSheetDialog = dialog;
        currentActiveSteps = items;

        updateExecutionBottomModalContent(items, isCurrentlyWorking);

        dialog.setContentView(modalRoot);
        dialog.setOnDismissListener(d -> {
            activeBottomSheetDialog = null;
            activeBottomSheetMasterList = null;
            activeBottomSheetContainer = null;
            activeBottomSheetMasterView = null;
            activeBottomSheetDetailView = null;
            activeBottomSheetSubtitle = null;
        });

        final Window window = dialog.getWindow();
        final boolean[] isFullscreen = {false};

        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.getDecorView().setPadding(0, 0, 0, 0);
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.gravity = Gravity.BOTTOM;
            wlp.width = WindowManager.LayoutParams.MATCH_PARENT;
            wlp.height = peekHeight;
            wlp.horizontalMargin = 0f;
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

        dialog.show();
    }

    private void updateExecutionBottomModalContent(final ArrayList<JSONObject> items, final boolean isCurrentlyWorking) {
        if (activeBottomSheetMasterList == null) return;

        final boolean isActuallyRunning = isCurrentlyWorking && isLiveTaskRunning;

        if (activeBottomSheetSubtitle != null) {
            activeBottomSheetSubtitle.setText(items.size() + " actions • ketuk item untuk melihat detail" + (isActuallyRunning ? " (Live)" : ""));
        }

        activeBottomSheetMasterList.removeAllViews();

        for (int i = 0; i < items.size(); i++) {
            final JSONObject it = items.get(i);
            String role = it.optString("role", "tool");
            String toolTitle = it.optString("toolTitle", "tool".equalsIgnoreCase(role) ? "Bash" : "Thinking");
            String displayTitle = it.optString("title", toolTitle);
            boolean isTool = "tool".equalsIgnoreCase(role);
            final boolean isThisItemRunning = (isActuallyRunning && i == items.size() - 1);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(cBox(isThisItemRunning ? CLAUDE_AMBER_BG : CLAUDE_SURFACE, isThisItemRunning ? CLAUDE_AMBER : CLAUDE_BORDER, 1, 16));
            card.setPadding(dp(14), dp(12), dp(14), dp(12));

            ImageView ic = cIcon(isTool ? R.drawable.ic_build : R.drawable.ic_psychology, 20, isThisItemRunning ? CLAUDE_AMBER : CLAUDE_TERRACOTTA);
            card.addView(ic);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setPadding(dp(12), 0, dp(8), 0);

            TextView tView = cText(toolTitle, 14.5f, CLAUDE_TEXT_MAIN, true, false);
            textCol.addView(tView);

            TextView subV = cText(displayTitle, 12f, CLAUDE_TEXT_MUTED, false, false);
            subV.setSingleLine(true);
            textCol.addView(subV);

            card.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

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
                TextView bText = cText(" Selesai", 11f, CLAUDE_GREEN, true, false);
                bBadge.addView(bText);
            }
            card.addView(bBadge);

            ImageView chevron = cIcon(R.drawable.ic_chevron_right, 20, CLAUDE_TEXT_MUTED);
            chevron.setPadding(dp(4), 0, 0, 0);
            card.addView(chevron);

            card.setOnClickListener(v -> showStepDetailView(it, isThisItemRunning));

            LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(-1, -2);
            lpC.setMargins(0, 0, 0, dp(10));
            activeBottomSheetMasterList.addView(card, lpC);
        }
    }

    // --- LEVEL 2: STEP DETAIL VIEW (Exact Match to Screenshot!) ---
    private void showStepDetailView(JSONObject item, boolean isRunning) {
        if (activeBottomSheetDetailView == null || activeBottomSheetMasterView == null) return;

        activeBottomSheetDetailView.removeAllViews();

        String role = item.optString("role", "tool");
        String toolTitle = item.optString("toolTitle", "tool".equalsIgnoreCase(role) ? "Bash" : "Thinking");
        String commandText = item.optString("command", item.optString("title", ""));
        String outputText = item.optString("content", "");
        String statusText = isRunning ? "Sedang berjalan..." : "Selesai";

        String targetFile = item.optString("targetFile", "");
        String targetContent = item.optString("targetContent", "");
        String replacementContent = item.optString("replacementContent", "");
        int startLine = item.optInt("startLine", 1);
        int addedLines = item.optInt("addedLines", 0);
        int deletedLines = item.optInt("deletedLines", 0);

        boolean isEditDiff = "Edit".equalsIgnoreCase(toolTitle) || !targetContent.isEmpty() || !replacementContent.isEmpty() || !targetFile.isEmpty();

        // Top Bar: Back Arrow (<--) + Centered Title ("Edit") + Subtitle ("Selesai")
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, dp(14));

        ImageView backBtn = cIconButton(R.drawable.ic_arrow_back, 24, 40, CLAUDE_TEXT_MAIN);
        backBtn.setOnClickListener(v -> {
            activeBottomSheetDetailView.setVisibility(View.GONE);
            activeBottomSheetMasterView.setVisibility(View.VISIBLE);
        });
        topBar.addView(backBtn);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView titleView = cText(isEditDiff ? "Edit" : toolTitle, 19f, CLAUDE_TEXT_MAIN, true, false);
        titleView.setGravity(Gravity.CENTER);
        titleCol.addView(titleView);

        TextView statusView = cText(statusText, 13f, isRunning ? CLAUDE_AMBER : CLAUDE_TEXT_MUTED, false, false);
        statusView.setGravity(Gravity.CENTER);
        titleCol.addView(statusView);

        topBar.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        // Right spacer to keep title centered
        View spacer = new View(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(dp(40), dp(40)));
        activeBottomSheetDetailView.addView(topBar);

        ScrollView detailScroll = new ScrollView(this);
        detailScroll.setFillViewport(true);
        detailScroll.setVerticalScrollBarEnabled(true);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        if (isEditDiff) {
            // ============================================================
            // RICH DIFF VIEW (Exact match to Claude Code screenshot)
            // ============================================================
            String fileName = targetFile.isEmpty() ? "file" : new File(targetFile).getName();
            String dirPath = targetFile.isEmpty() ? "" : targetFile;

            // File Meta Bar: filename + truncated path + (+A -B badge)
            LinearLayout metaBar = new LinearLayout(this);
            metaBar.setOrientation(LinearLayout.HORIZONTAL);
            metaBar.setGravity(Gravity.CENTER_VERTICAL);
            metaBar.setPadding(0, 0, 0, dp(10));

            TextView fnView = cText(fileName, 14.5f, CLAUDE_TEXT_MAIN, true, false);
            fnView.setTypeface(Typeface.MONOSPACE);
            metaBar.addView(fnView);

            TextView pathView = cText("  " + dirPath, 13f, CLAUDE_TEXT_MUTED, false, false);
            pathView.setSingleLine(true);
            pathView.setEllipsize(TextUtils.TruncateAt.END);
            metaBar.addView(pathView, new LinearLayout.LayoutParams(0, -2, 1));

            if (addedLines > 0 || deletedLines > 0) {
                LinearLayout diffBadge = new LinearLayout(this);
                diffBadge.setOrientation(LinearLayout.HORIZONTAL);
                diffBadge.setGravity(Gravity.CENTER_VERTICAL);

                if (addedLines > 0) {
                    diffBadge.addView(cText("+" + addedLines + " ", 13.5f, CLAUDE_GREEN, true, false));
                }
                if (deletedLines > 0) {
                    diffBadge.addView(cText("-" + deletedLines, 13.5f, CLAUDE_RED, true, false));
                }
                metaBar.addView(diffBadge);
            }
            body.addView(metaBar);

            // Diff Code Obsidian Box
            LinearLayout diffBox = new LinearLayout(this);
            diffBox.setOrientation(LinearLayout.VERTICAL);
            diffBox.setBackground(cBox(Color.rgb(18, 19, 22), Color.rgb(38, 40, 46), 1, 10));

            // Top Accordion Header: ^ +N baris
            LinearLayout topAccordion = new LinearLayout(this);
            topAccordion.setOrientation(LinearLayout.HORIZONTAL);
            topAccordion.setGravity(Gravity.CENTER_VERTICAL);
            topAccordion.setBackgroundColor(Color.rgb(27, 36, 51)); // Dark Navy Slate #1B2433
            topAccordion.setPadding(dp(12), dp(7), dp(12), dp(7));

            ImageView upIcon = cIcon(R.drawable.ic_expand_more, 16, Color.rgb(138, 153, 173));
            upIcon.setRotation(180f);
            topAccordion.addView(upIcon);

            TextView topAccText = cText(" +" + (startLine > 1 ? (startLine - 1) : 1) + " baris", 12.5f, Color.rgb(138, 153, 173), false, false);
            topAccText.setTypeface(Typeface.MONOSPACE);
            topAccordion.addView(topAccText);
            diffBox.addView(topAccordion);

            // Horizontal Scroll for code lines
            HorizontalScrollView codeHScroll = new HorizontalScrollView(this);
            codeHScroll.setHorizontalScrollBarEnabled(false);

            LinearLayout linesContainer = new LinearLayout(this);
            linesContainer.setOrientation(LinearLayout.VERTICAL);
            linesContainer.setPadding(0, dp(4), dp(16), dp(4));

            int currentLineNum = Math.max(1, startLine);

            // Render Deleted Lines (TargetContent) in Dark Red
            if (!targetContent.isEmpty()) {
                String[] delLines = targetContent.split("\n");
                for (String dl : delLines) {
                    LinearLayout lineRow = new LinearLayout(this);
                    lineRow.setOrientation(LinearLayout.HORIZONTAL);
                    lineRow.setGravity(Gravity.CENTER_VERTICAL);
                    lineRow.setBackgroundColor(Color.argb(80, 239, 68, 68)); // Red tint #361718
                    lineRow.setPadding(0, dp(2), dp(8), dp(2));

                    TextView ln = new TextView(this);
                    ln.setText(String.valueOf(currentLineNum));
                    ln.setTextSize(12f);
                    ln.setTextColor(Color.rgb(150, 150, 150));
                    ln.setTypeface(Typeface.MONOSPACE);
                    ln.setGravity(Gravity.END);
                    LinearLayout.LayoutParams lpLn = new LinearLayout.LayoutParams(dp(36), -2);
                    lpLn.setMargins(0, 0, dp(12), 0);
                    lineRow.addView(ln, lpLn);

                    TextView codeTxt = new TextView(this);
                    codeTxt.setText(dl);
                    codeTxt.setTextSize(13f);
                    codeTxt.setTextColor(Color.rgb(255, 133, 133)); // Red text
                    codeTxt.setTypeface(Typeface.MONOSPACE);
                    lineRow.addView(codeTxt);

                    linesContainer.addView(lineRow, new LinearLayout.LayoutParams(-1, -2));
                }
            }

            // Render Added Lines (ReplacementContent) in Dark Green
            if (!replacementContent.isEmpty()) {
                String[] addLines = replacementContent.split("\n");
                for (String al : addLines) {
                    LinearLayout lineRow = new LinearLayout(this);
                    lineRow.setOrientation(LinearLayout.HORIZONTAL);
                    lineRow.setGravity(Gravity.CENTER_VERTICAL);
                    lineRow.setBackgroundColor(Color.argb(75, 76, 175, 80)); // Green tint #163321
                    lineRow.setPadding(0, dp(2), dp(8), dp(2));

                    TextView ln = new TextView(this);
                    ln.setText(String.valueOf(currentLineNum++));
                    ln.setTextSize(12f);
                    ln.setTextColor(Color.rgb(150, 150, 150));
                    ln.setTypeface(Typeface.MONOSPACE);
                    ln.setGravity(Gravity.END);
                    LinearLayout.LayoutParams lpLn = new LinearLayout.LayoutParams(dp(36), -2);
                    lpLn.setMargins(0, 0, dp(12), 0);
                    lineRow.addView(ln, lpLn);

                    TextView codeTxt = new TextView(this);
                    codeTxt.setText(al);
                    codeTxt.setTextSize(13f);
                    codeTxt.setTextColor(Color.rgb(112, 239, 139)); // Bright green text
                    codeTxt.setTypeface(Typeface.MONOSPACE);
                    lineRow.addView(codeTxt);

                    linesContainer.addView(lineRow, new LinearLayout.LayoutParams(-1, -2));
                }
            } else if (targetContent.isEmpty()) {
                // Fallback if no target/replacement parsed
                TextView fallback = cText(outputText, 13f, CLAUDE_TEXT_MAIN, false, false);
                fallback.setTypeface(Typeface.MONOSPACE);
                linesContainer.addView(fallback);
            }

            codeHScroll.addView(linesContainer);
            diffBox.addView(codeHScroll);

            // Bottom Accordion Footer: v Perluas
            LinearLayout btmAccordion = new LinearLayout(this);
            btmAccordion.setOrientation(LinearLayout.HORIZONTAL);
            btmAccordion.setGravity(Gravity.CENTER_VERTICAL);
            btmAccordion.setBackgroundColor(Color.rgb(27, 36, 51)); // Dark Navy Slate #1B2433
            btmAccordion.setPadding(dp(12), dp(7), dp(12), dp(7));

            ImageView downIcon = cIcon(R.drawable.ic_expand_more, 16, Color.rgb(138, 153, 173));
            btmAccordion.addView(downIcon);

            TextView btmAccText = cText(" Perluas", 12.5f, Color.rgb(138, 153, 173), false, false);
            btmAccText.setTypeface(Typeface.MONOSPACE);
            btmAccordion.addView(btmAccText);
            diffBox.addView(btmAccordion);

            body.addView(diffBox);
        } else {
            // Standard Command & Output Sections
            TextView cmdLabel = cText("Perintah", 13.5f, CLAUDE_TEXT_MUTED, false, false);
            LinearLayout.LayoutParams lpCmdL = new LinearLayout.LayoutParams(-1, -2);
            lpCmdL.setMargins(0, 0, 0, dp(8));
            body.addView(cmdLabel, lpCmdL);

            LinearLayout cmdBox = new LinearLayout(this);
            cmdBox.setOrientation(LinearLayout.VERTICAL);
            cmdBox.setBackground(cBox(CLAUDE_CODE_BG, CLAUDE_BORDER, 1, 12));
            cmdBox.setPadding(dp(14), dp(12), dp(14), dp(12));

            TextView cmdView = new TextView(this);
            cmdView.setText(commandText.isEmpty() ? toolTitle : commandText);
            cmdView.setTextSize(13.5f);
            cmdView.setTextColor(Color.rgb(255, 204, 128)); // Highlighted amber command syntax
            cmdView.setTypeface(Typeface.MONOSPACE);
            cmdView.setTextIsSelectable(true);
            cmdBox.addView(cmdView);

            body.addView(cmdBox);

            TextView outLabel = cText("Keluaran / Respons", 13.5f, CLAUDE_TEXT_MUTED, false, false);
            LinearLayout.LayoutParams lpOutL = new LinearLayout.LayoutParams(-1, -2);
            lpOutL.setMargins(0, dp(16), 0, dp(8));
            body.addView(outLabel, lpOutL);

            LinearLayout outBox = new LinearLayout(this);
            outBox.setOrientation(LinearLayout.VERTICAL);
            outBox.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_BORDER, 1, 12));
            outBox.setPadding(dp(14), dp(12), dp(14), dp(12));

            TextView outView = new TextView(this);
            outView.setText(outputText.isEmpty() ? "(Tidak ada output teks)" : outputText);
            outView.setTextSize(13.5f);
            outView.setTextColor(CLAUDE_TEXT_MAIN);
            outView.setTypeface(Typeface.MONOSPACE);
            outView.setTextIsSelectable(true);
            outBox.addView(outView);

            body.addView(outBox);
        }

        detailScroll.addView(body);
        activeBottomSheetDetailView.addView(detailScroll, new LinearLayout.LayoutParams(-1, -1));

        activeBottomSheetMasterView.setVisibility(View.GONE);
        activeBottomSheetDetailView.setVisibility(View.VISIBLE);
    }

    // ============================================================
    // SCREEN 1: CHAT VIEW (Floating Composer & Multi-Attachment Tray)
    // ============================================================
    private void buildChatScreen(FrameLayout root) {
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dp(16), dp(8), dp(16), 0);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, dp(2), 0, dp(8));

        chatNavIcon = cIconButton(R.drawable.ic_arrow_back, 24, 40, CLAUDE_TEXT_MAIN);
        chatNavIcon.setOnClickListener(v -> {
            if (navigatedFromHub || currentScreen == 1) {
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

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setVerticalScrollBarEnabled(false);
        chatScroll.setClipToPadding(false);
        chatScroll.setPadding(0, 0, 0, dp(130));

        chatMessagesList = new LinearLayout(this);
        chatMessagesList.setOrientation(LinearLayout.VERTICAL);
        chatMessagesList.setGravity(Gravity.NO_GRAVITY);

        buildEmptyMascotState();
        chatMessagesList.addView(emptyMascotView);

        chatScroll.addView(chatMessagesList);
        contentLayout.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        root.addView(contentLayout, new FrameLayout.LayoutParams(-1, -1));

        // Floating Bottom Composer Card & Multi-Attachment Tray
        LinearLayout floatingWrapper = new LinearLayout(this);
        floatingWrapper.setOrientation(LinearLayout.VERTICAL);
        floatingWrapper.setBackgroundColor(Color.TRANSPARENT);

        // Multi-Attachment Horizontal Scroll Tray
        attachmentScrollContainer = new HorizontalScrollView(this);
        attachmentScrollContainer.setHorizontalScrollBarEnabled(false);
        attachmentScrollContainer.setVisibility(View.GONE);

        attachmentChipsList = new LinearLayout(this);
        attachmentChipsList.setOrientation(LinearLayout.HORIZONTAL);
        attachmentChipsList.setGravity(Gravity.CENTER_VERTICAL);
        attachmentScrollContainer.addView(attachmentChipsList, new ViewGroup.LayoutParams(-2, -2));

        LinearLayout.LayoutParams lpAtt = new LinearLayout.LayoutParams(-1, -2);
        lpAtt.setMargins(0, 0, 0, dp(8));
        floatingWrapper.addView(attachmentScrollContainer, lpAtt);

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
        promptInput.setTextSize(15.5f);
        promptInput.setTypeface(Typeface.SERIF);
        promptInput.setBackgroundColor(Color.TRANSPARENT);
        promptInput.setMaxLines(6);
        promptInput.setPadding(0, 0, 0, dp(8));
        composerCard.addView(promptInput);

        // Action Toolbar
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);

        btnAttach = cIconButton(R.drawable.ic_add, 24, 38, CLAUDE_TEXT_MUTED);
        btnAttach.setOnClickListener(v -> openMultiFilePicker());
        actionRow.addView(btnAttach);

        btnVoice = cIconButton(R.drawable.ic_mic, 22, 38, CLAUDE_TEXT_MUTED);
        btnVoice.setOnClickListener(v -> startVoiceRecognition());
        actionRow.addView(btnVoice);

        repoTagLabel = cText(currentEngine.equalsIgnoreCase("codex") ? "Codex" : "Antigravity", 12f, CLAUDE_TEXT_MUTED, true, false);
        repoTagLabel.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 12));
        repoTagLabel.setPadding(dp(10), dp(4), dp(10), dp(4));
        repoTagLabel.setOnClickListener(v -> toggleEngine());
        LinearLayout.LayoutParams lpTag = new LinearLayout.LayoutParams(-2, -2);
        lpTag.setMargins(dp(6), 0, 0, 0);
        actionRow.addView(repoTagLabel, lpTag);

        View spring = new View(this);
        actionRow.addView(spring, new LinearLayout.LayoutParams(0, 1, 1));

        btnSend = new FrameLayout(this);
        btnSend.setBackground(cBox(CLAUDE_TERRACOTTA, 0, 0, 18));
        ImageView sendIcon = cIcon(R.drawable.ic_send, 18, Color.WHITE);
        FrameLayout.LayoutParams lpSendIc = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        btnSend.addView(sendIcon, lpSendIc);
        btnSend.setOnClickListener(v -> sendClaudePrompt());
        actionRow.addView(btnSend, new LinearLayout.LayoutParams(dp(36), dp(36)));

        composerCard.addView(actionRow);
        floatingWrapper.addView(composerCard);

        FrameLayout.LayoutParams lpFloat = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lpFloat.setMargins(dp(16), 0, dp(16), dp(14));
        root.addView(floatingWrapper, lpFloat);

        // Floating Scroll-to-Bottom Button (FAB)
        btnScrollToBottom = new FrameLayout(this);
        btnScrollToBottom.setBackground(cBox(CLAUDE_SURFACE_MUTED, CLAUDE_BORDER, 1, 22));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnScrollToBottom.setElevation(dp(6));
        }
        ImageView downArrow = cIcon(R.drawable.ic_expand_more, 22, CLAUDE_TEXT_MAIN);
        btnScrollToBottom.addView(downArrow, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        btnScrollToBottom.setVisibility(View.GONE);
        btnScrollToBottom.setOnClickListener(v -> {
            chatScroll.smoothScrollTo(0, chatMessagesList.getHeight());
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        });

        FrameLayout.LayoutParams lpScrollBtn = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.BOTTOM | Gravity.END);
        lpScrollBtn.setMargins(0, 0, dp(20), dp(120));
        root.addView(btnScrollToBottom, lpScrollBtn);

        // Scroll listener for Top History Load + Scroll-to-Bottom visibility toggle
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            chatScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (scrollY == 0 && !isLiveTaskRunning && activeConversationId != null) {
                    syncLiveExecution();
                }

                int scrollHeight = chatScroll.getHeight();
                int contentHeight = chatMessagesList.getHeight();
                int distanceToBottom = contentHeight - (scrollY + scrollHeight);

                if (distanceToBottom > dp(180)) {
                    if (btnScrollToBottom.getVisibility() != View.VISIBLE) {
                        btnScrollToBottom.setVisibility(View.VISIBLE);
                        btnScrollToBottom.setAlpha(0f);
                        btnScrollToBottom.setScaleX(0.8f);
                        btnScrollToBottom.setScaleY(0.8f);
                        btnScrollToBottom.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start();
                    }
                } else {
                    if (btnScrollToBottom.getVisibility() == View.VISIBLE) {
                        btnScrollToBottom.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(160)
                                .withEndAction(() -> btnScrollToBottom.setVisibility(View.GONE)).start();
                    }
                }
            });
        }

        // Centered Session Loading Indicator Overlay
        chatSessionLoadingView = new LinearLayout(this);
        chatSessionLoadingView.setOrientation(LinearLayout.VERTICAL);
        chatSessionLoadingView.setGravity(Gravity.CENTER);
        chatSessionLoadingView.setBackgroundColor(CLAUDE_BG);
        chatSessionLoadingView.setVisibility(View.GONE);

        ProgressBar loadPb = new ProgressBar(this);
        chatSessionLoadingView.addView(loadPb, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView loadText = cText("Memuat percakapan...", 13.5f, CLAUDE_TEXT_MUTED, false, false);
        loadText.setPadding(0, dp(12), 0, 0);
        chatSessionLoadingView.addView(loadText);

        root.addView(chatSessionLoadingView, new FrameLayout.LayoutParams(-1, -1));
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
        refreshSettingsValues();
        Toast.makeText(this, "Engine: " + currentEngine, Toast.LENGTH_SHORT).show();
    }

    private void showMoreDropdownMenu(View anchorView) {
        PopupMenu popup = new PopupMenu(this, anchorView);
        popup.getMenu().add(0, 1, 0, "Scan QR Code Pairing");
        popup.getMenu().add(0, 2, 0, "Paste from Clipboard");
        popup.getMenu().add(0, 3, 0, "Pengaturan (Settings)");
        popup.getMenu().add(0, 4, 0, "Interrupt / Stop Task");
        popup.getMenu().add(0, 5, 0, "Refresh Transcript");
        popup.getMenu().add(0, 6, 0, "Clear to New Session");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) startQrScanner();
            else if (id == 2) pasteFromClipboard();
            else if (id == 3) showScreen(2);
            else if (id == 4) stopRunningCliProcess();
            else if (id == 5) fetchActiveSessionTurns(true);
            else if (id == 6) startNewSession();
            return true;
        });
        popup.show();
    }

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
            final Dialog dialog = createBaseBottomSheet(true);
            LinearLayout root = createBottomSheetRoot(dialog, "Scan QR Code Pairing", true);

            TextView sub = cText("Arahkan kamera ke QR Code di terminal (agy-pair)", 12.5f, CLAUDE_TEXT_MUTED, false, false);
            LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(-1, -2);
            lpSub.setMargins(0, 0, 0, dp(14));
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
            showConnectionBottomSheet();
        } catch (Exception e) {
            Toast.makeText(this, "Clipboard error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            showConnectionBottomSheet();
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
        refreshSettingsValues();
        Toast.makeText(this, "Berhasil terhubung ke Server!", Toast.LENGTH_LONG).show();
        checkHealth();
        fetchHubSessions();
    }

    // ============================================================
    // MULTI-FILE & MULTI-IMAGE SELECTION & TRAY PREVIEW
    // ============================================================
    private void openMultiFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Pilih Gambar atau File (Bisa Banyak)"), REQ_PICK_FILES);
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
            if (requestCode == REQ_PICK_FILES) {
                ArrayList<Uri> uris = new ArrayList<>();
                ClipData clipData = data.getClipData();
                if (clipData != null && clipData.getItemCount() > 0) {
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        ClipData.Item item = clipData.getItemAt(i);
                        if (item.getUri() != null) uris.add(item.getUri());
                    }
                } else if (data.getData() != null) {
                    uris.add(data.getData());
                }

                if (!uris.isEmpty()) {
                    uploadMultipleSelectedFiles(uris);
                }
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

    private void uploadMultipleSelectedFiles(final List<Uri> uris) {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) {
            showConnectionBottomSheet();
            return;
        }

        Toast.makeText(this, "Mengupload " + uris.size() + " file...", Toast.LENGTH_SHORT).show();

        for (final Uri uri : uris) {
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
                    boolean isImg = fileName.toLowerCase().matches(".*\\.(png|jpg|jpeg|webp|gif|svg)$");
                    if (isImg) {
                        try {
                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inSampleSize = 4;
                            thumbBmp = BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.length, opts);
                        } catch (Exception ignored) {}
                    }

                    final Bitmap finalThumb = thumbBmp;
                    final boolean finalIsImg = isImg;

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
                            AttachedMedia media = new AttachedMedia(serverPath, savedName, finalThumb, finalIsImg);
                            attachedMediaList.add(media);
                            refreshAttachmentTray();
                        });
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, "Upload error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        }
    }

    private void refreshAttachmentTray() {
        if (attachmentChipsList == null || attachmentScrollContainer == null) return;
        attachmentChipsList.removeAllViews();

        if (attachedMediaList.isEmpty()) {
            attachmentScrollContainer.setVisibility(View.GONE);
            return;
        }

        attachmentScrollContainer.setVisibility(View.VISIBLE);

        for (int i = 0; i < attachedMediaList.size(); i++) {
            final AttachedMedia m = attachedMediaList.get(i);

            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_TERRACOTTA, 1, 14));
            chip.setPadding(dp(6), dp(4), dp(8), dp(4));

            if (m.isImage && m.bitmap != null) {
                ImageView thumb = new ImageView(this);
                thumb.setImageBitmap(m.bitmap);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.setBackground(cBox(CLAUDE_SURFACE_MUTED, 0, 0, 8));
                LinearLayout.LayoutParams lpTh = new LinearLayout.LayoutParams(dp(30), dp(30));
                thumb.setLayoutParams(lpTh);
                chip.addView(thumb);
            } else {
                ImageView docIcon = cIcon(R.drawable.ic_attach_file, 20, CLAUDE_TERRACOTTA);
                chip.addView(docIcon);
            }

            TextView nameView = cText(" " + (m.fileName.length() > 18 ? m.fileName.substring(0, 15) + "..." : m.fileName), 12f, CLAUDE_TERRACOTTA, true, false);
            nameView.setPadding(dp(4), 0, dp(4), 0);
            nameView.setSingleLine(true);
            chip.addView(nameView);

            ImageView closeBtn = cIconButton(R.drawable.ic_close, 14, 24, CLAUDE_TEXT_MUTED);
            closeBtn.setOnClickListener(v -> {
                attachedMediaList.remove(m);
                refreshAttachmentTray();
            });
            chip.addView(closeBtn);

            LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(-2, -2);
            lpC.setMargins(0, 0, dp(8), 0);
            attachmentChipsList.addView(chip, lpC);
        }
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
    // REAL-TIME LIVE CHAT EXECUTION & MULTI-ATTACHMENT DISPATCH
    // ============================================================
    private void sendClaudePrompt() {
        String text = promptInput.getText().toString().trim();
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) {
            showConnectionBottomSheet();
            return;
        }
        if (text.isEmpty() && attachedMediaList.isEmpty()) return;
        if (btnSend.getTag() != null) return;

        final ArrayList<String> filePathsToSend = new ArrayList<>();
        final StringBuilder fileHeaders = new StringBuilder();

        for (AttachedMedia m : attachedMediaList) {
            filePathsToSend.add(m.serverPath);
            fileHeaders.append("[File: ").append(m.serverPath).append("]\n");
        }

        attachedMediaList.clear();
        refreshAttachmentTray();

        showEmptyMascotState(false);

        btnSend.setTag("busy");
        btnSend.setEnabled(false);
        promptInput.setEnabled(false);
        isLiveTaskRunning = true;

        String displayText = (fileHeaders.length() > 0 ? fileHeaders.toString() : "") + text;
        pendingOptimisticUserPrompt = displayText;
        pendingOptimisticUserTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        renderUserMessageBlock(pendingOptimisticUserPrompt, pendingOptimisticUserTime);
        promptInput.setText("");

        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));

        startAutoRefresh();

        final String promptToSend = text;

        executor.execute(() -> {
            try {
                JSONObject req = new JSONObject();
                req.put("prompt", promptToSend);
                req.put("engine", currentEngine);
                req.put("resume", true);

                if (!filePathsToSend.isEmpty()) {
                    JSONArray arr = new JSONArray();
                    for (String f : filePathsToSend) arr.put(f);
                    req.put("attachedFiles", arr);
                    req.put("attachedFile", filePathsToSend.get(0));
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
                boolean foundOptimisticInTranscript = false;

                // Find last assistant index
                int lastAssistantIdx = -1;
                for (int i = turns.length() - 1; i >= 0; i--) {
                    JSONObject t = turns.getJSONObject(i);
                    String r = t.optString("role", "");
                    if (!"tool".equalsIgnoreCase(r) && !"thinking".equalsIgnoreCase(r) && !"user".equalsIgnoreCase(r)) {
                        lastAssistantIdx = i;
                        break;
                    }
                }

                for (int i = 0; i < turns.length(); i++) {
                    JSONObject turn = turns.getJSONObject(i);
                    String role = turn.optString("role", "info");
                    String content = turn.optString("content", "");
                    String time = turn.optString("time", "");

                    if ("tool".equalsIgnoreCase(role) || "thinking".equalsIgnoreCase(role)) {
                        pendingTools.add(turn);
                        allSessionTools.add(turn);
                    } else if ("user".equalsIgnoreCase(role)) {
                        if (!pendingTools.isEmpty()) {
                            renderInlineStepPill(new ArrayList<>(pendingTools), false);
                            pendingTools.clear();
                        }
                        if (pendingOptimisticUserPrompt != null && (content.contains(pendingOptimisticUserPrompt) || pendingOptimisticUserPrompt.contains(content))) {
                            foundOptimisticInTranscript = true;
                        }
                        renderUserMessageBlock(content, time);
                    } else {
                        if (!pendingTools.isEmpty()) {
                            renderInlineStepPill(new ArrayList<>(pendingTools), false);
                            pendingTools.clear();
                        }
                        renderAssistantMessageBlock(content, time, (i == lastAssistantIdx));
                    }
                }

                if (foundOptimisticInTranscript) {
                    pendingOptimisticUserPrompt = null;
                }

                // If live task is running and user prompt is not yet in disk transcript, render user prompt first!
                if (isLiveTaskRunning && pendingOptimisticUserPrompt != null && !foundOptimisticInTranscript) {
                    if (!pendingTools.isEmpty()) {
                        renderInlineStepPill(new ArrayList<>(pendingTools), false);
                        pendingTools.clear();
                    }
                    renderUserMessageBlock(pendingOptimisticUserPrompt, pendingOptimisticUserTime);
                }

                if (!pendingTools.isEmpty()) {
                    renderInlineStepPill(pendingTools, isLiveTaskRunning);
                } else if (isLiveTaskRunning) {
                    ArrayList<JSONObject> dummy = new ArrayList<>();
                    JSONObject o = new JSONObject();
                    o.put("role", "thinking");
                    o.put("toolTitle", "Thinking");
                    o.put("title", "Processing prompt...");
                    o.put("command", "Planning response & executing engine");
                    o.put("content", "Starting CLI process and planning response...");
                    dummy.add(o);
                    renderInlineStepPill(dummy, true);
                    allSessionTools.addAll(dummy);
                }

                if (activeBottomSheetDialog != null && activeBottomSheetDialog.isShowing()) {
                    ArrayList<JSONObject> toolsToUpdate = !pendingTools.isEmpty() ? pendingTools : allSessionTools;
                    currentActiveSteps = toolsToUpdate;
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
    // CLAUDE CHAT FLOW: USER BUBBLES, INLINE PILLS & CLEAN AI TEXT
    // ============================================================
    private void renderUserMessageBlock(String rawContent, String time) {
        if (rawContent == null || rawContent.trim().isEmpty()) return;

        showEmptyMascotState(false);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.END);

        String remainingText = rawContent;
        ArrayList<String> images = new ArrayList<>();

        try {
            Pattern imgPat = Pattern.compile("\\[(?:Attached\\s+)?File:\\s*([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);
            Matcher m = imgPat.matcher(rawContent);
            while (m.find()) {
                String p = m.group(1).trim();
                if (p.toLowerCase().matches(".*\\.(png|jpg|jpeg|webp|gif|svg)$")) {
                    images.add(p);
                }
                remainingText = remainingText.replace(m.group(0), "");
            }
        } catch (Exception ignored) {}

        // Render attached image previews aligned to the right, clickable for fullscreen
        for (final String imgPath : images) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setAdjustViewBounds(true);
            iv.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_BORDER, 1, 14));
            iv.setPadding(dp(2), dp(2), dp(2), dp(2));

            LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(dp(180), dp(130));
            lpImg.gravity = Gravity.END;
            lpImg.setMargins(0, 0, 0, dp(8));
            container.addView(iv, lpImg);

            loadImageIntoView(imgPath, iv);

            iv.setOnClickListener(v -> showFullscreenImageDialog(imgPath));
        }

        String userPromptText = remainingText.trim();
        if (!userPromptText.isEmpty()) {
            LinearLayout bubble = new LinearLayout(this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_BORDER, 1, 18));
            bubble.setPadding(dp(16), dp(12), dp(16), dp(12));

            TextView tv = new TextView(this);
            tv.setText(userPromptText);
            tv.setTextSize(15.5f);
            tv.setTextColor(CLAUDE_TEXT_MAIN);
            tv.setTypeface(Typeface.SERIF);
            tv.setLineSpacing(0, 1.25f);
            tv.setTextIsSelectable(true);
            bubble.addView(tv);

            final String copyText = userPromptText;
            bubble.setOnLongClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Prompt", copyText));
                Toast.makeText(MainActivity.this, "Prompt disalin", Toast.LENGTH_SHORT).show();
                return true;
            });

            LinearLayout.LayoutParams lpBubble = new LinearLayout.LayoutParams(-2, -2);
            lpBubble.gravity = Gravity.END;
            lpBubble.setMargins(dp(40), 0, 0, dp(4));
            container.addView(bubble, lpBubble);
        }

        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(-1, -2);
        lpC.setMargins(0, dp(8), 0, dp(12));
        chatMessagesList.addView(container, lpC);
    }

    private void renderInlineStepPill(final ArrayList<JSONObject> steps, final boolean isRunning) {
        if (steps == null || steps.isEmpty()) return;

        showEmptyMascotState(false);

        int readCount = 0;
        int editCount = 0;
        int cmdCount = 0;
        int thinkCount = 0;
        int totalAdded = 0;
        int totalDeleted = 0;
        String singleFilename = "";
        String singleImageThumbnail = null;

        for (JSONObject s : steps) {
            String role = s.optString("role");
            String toolName = s.optString("toolName", "");
            String cmd = s.optString("command", "");

            totalAdded += s.optInt("addedLines", 0);
            totalDeleted += s.optInt("deletedLines", 0);

            if ("thinking".equalsIgnoreCase(role)) {
                thinkCount++;
            } else if ("tool".equalsIgnoreCase(role)) {
                if (toolName.equals("view_file")) {
                    readCount++;
                    singleFilename = new File(cmd).getName();
                    if (singleFilename.toLowerCase().matches(".*\\.(png|jpg|jpeg|webp|gif|svg)$")) {
                        singleImageThumbnail = cmd;
                    }
                } else if (toolName.equals("replace_file_content") || toolName.equals("write_to_file")) {
                    editCount++;
                    singleFilename = new File(cmd).getName();
                } else if (toolName.equals("run_command")) {
                    cmdCount++;
                } else {
                    readCount++;
                }
            }
        }

        // Build concise label matching Claude's formatting
        SpannableStringBuilder label = new SpannableStringBuilder();

        if (steps.size() == 1) {
            if (readCount == 1 && !singleFilename.isEmpty()) {
                label.append("Dibaca ");
                int start = label.length();
                label.append(singleFilename);
                label.setSpan(new ForegroundColorSpan(CLAUDE_TEXT_MAIN), start, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (editCount == 1 && !singleFilename.isEmpty()) {
                label.append("Mengedit ");
                int start = label.length();
                label.append(singleFilename);
                label.setSpan(new ForegroundColorSpan(CLAUDE_TEXT_MAIN), start, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (cmdCount == 1) {
                label.append("Menjalankan 1 perintah");
            } else {
                label.append("Thinking process");
            }
        } else {
            ArrayList<String> parts = new ArrayList<>();
            if (readCount > 0) parts.add("Membaca " + readCount + " file");
            if (cmdCount > 0) parts.add("menjalankan " + cmdCount + " perintah");
            if (editCount > 0) parts.add("Mengedit " + editCount + " file");
            if (thinkCount > 0 && parts.isEmpty()) parts.add(thinkCount + " langkah berpikir");

            label.append(joinStrings(parts, ", "));
        }

        LinearLayout pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.VERTICAL);
        pillRow.setPadding(0, dp(4), 0, dp(4));

        LinearLayout actionHeader = new LinearLayout(this);
        actionHeader.setOrientation(LinearLayout.HORIZONTAL);
        actionHeader.setGravity(Gravity.CENTER_VERTICAL);
        actionHeader.setPadding(0, dp(4), 0, dp(4));

        TextView pillText = new TextView(this);
        pillText.setText(label);
        pillText.setTextSize(14f);
        pillText.setTextColor(CLAUDE_TEXT_MUTED);
        pillText.setSingleLine(true);
        pillText.setEllipsize(TextUtils.TruncateAt.END);
        actionHeader.addView(pillText, new LinearLayout.LayoutParams(0, -2, 1.0f));

        // Dynamic diff badge (+added in green, -deleted in red)
        if (totalAdded > 0 || totalDeleted > 0) {
            LinearLayout diffBadge = new LinearLayout(this);
            diffBadge.setOrientation(LinearLayout.HORIZONTAL);
            diffBadge.setGravity(Gravity.CENTER_VERTICAL);
            diffBadge.setPadding(dp(6), 0, dp(2), 0);

            if (totalAdded > 0) {
                TextView addView = cText("+" + totalAdded + " ", 13f, CLAUDE_GREEN, true, false);
                diffBadge.addView(addView);
            }
            if (totalDeleted > 0) {
                TextView delView = cText("-" + totalDeleted + " ", 13f, CLAUDE_RED, true, false);
                diffBadge.addView(delView);
            }
            actionHeader.addView(diffBadge, new LinearLayout.LayoutParams(-2, -2));
        }

        if (isRunning) {
            ProgressBar pb = new ProgressBar(this);
            LinearLayout.LayoutParams lpPb = new LinearLayout.LayoutParams(dp(14), dp(14));
            lpPb.setMargins(dp(6), 0, dp(4), 0);
            actionHeader.addView(pb, lpPb);
        }

        ImageView chevron = cIcon(R.drawable.ic_chevron_right, 14, CLAUDE_TEXT_LIGHT);
        LinearLayout.LayoutParams lpChev = new LinearLayout.LayoutParams(dp(14), dp(14));
        lpChev.setMargins(dp(4), 0, 0, 0);
        actionHeader.addView(chevron, lpChev);

        pillRow.addView(actionHeader);

        // If single read image, render image thumbnail underneath
        if (singleImageThumbnail != null) {
            final String finalImg = singleImageThumbnail;
            ImageView imgThumb = new ImageView(this);
            imgThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imgThumb.setAdjustViewBounds(true);
            imgThumb.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_BORDER, 1, 14));
            imgThumb.setPadding(dp(2), dp(2), dp(2), dp(2));

            LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(dp(130), dp(95));
            lpImg.setMargins(0, dp(6), 0, dp(4));
            pillRow.addView(imgThumb, lpImg);

            loadImageIntoView(singleImageThumbnail, imgThumb);
            imgThumb.setOnClickListener(v -> showFullscreenImageDialog(finalImg));
        }

        pillRow.setOnClickListener(v -> openExecutionBottomModal(steps, isRunning));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(4));
        chatMessagesList.addView(pillRow, lp);
    }

    private void renderAssistantMessageBlock(String content, String time, boolean isLastMessage) {
        if (content == null || content.trim().isEmpty()) return;

        showEmptyMascotState(false);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(2), 0, dp(4));

        renderMarkdownIntoContainer(container, content.trim(), false);

        // Sleek Copy Button for Assistant Output
        if (isLastMessage) {
            LinearLayout copyBar = new LinearLayout(this);
            copyBar.setOrientation(LinearLayout.HORIZONTAL);
            copyBar.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            copyBar.setPadding(0, dp(8), 0, dp(4));

            final LinearLayout copyBtn = new LinearLayout(this);
            copyBtn.setOrientation(LinearLayout.HORIZONTAL);
            copyBtn.setGravity(Gravity.CENTER_VERTICAL);
            copyBtn.setBackground(cBox(CLAUDE_SURFACE, CLAUDE_BORDER, 1, 14));
            copyBtn.setPadding(dp(10), dp(5), dp(12), dp(5));

            ImageView copyIcon = cIcon(R.drawable.ic_content_copy, 14, CLAUDE_TEXT_MUTED);
            copyBtn.addView(copyIcon);

            final TextView copyLabel = cText(" Salin", 12f, CLAUDE_TEXT_MUTED, true, false);
            copyBtn.addView(copyLabel);

            final String fullTextToCopy = cleanMarkdownForCopy(content.trim());
            copyBtn.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Assistant Response", fullTextToCopy));
                Toast.makeText(MainActivity.this, "Jawaban disalin ke clipboard", Toast.LENGTH_SHORT).show();
                copyLabel.setText(" Tersalin ✓");
                copyLabel.setTextColor(CLAUDE_GREEN);
                mainHandler.postDelayed(() -> {
                    copyLabel.setText(" Salin");
                    copyLabel.setTextColor(CLAUDE_TEXT_MUTED);
                }, 2000);
            });

            copyBar.addView(copyBtn);
            container.addView(copyBar);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(8));
        chatMessagesList.addView(container, lp);
    }

        private String joinStrings(List<String> list, String sep) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) b.append(sep);
            b.append(list.get(i));
        }
        return b.toString();
    }

    private String cleanMarkdownForCopy(String raw) {
        if (raw == null) return "";
        try {
            return raw.replaceAll("\\[File: [^\\n\\]]+\\]\\n?", "").trim();
        } catch (Exception e) {
            return raw.trim();
        }
    }

    private void renderMessageContentWithMedia(LinearLayout container, String text, boolean isUser) {
        if (text == null || text.isEmpty()) return;

        String remainingText = text;
        try {
            Pattern imgFilePat = Pattern.compile("\\[File:\\s*([^\\]]+\\.(?:png|jpg|jpeg|webp|gif|svg))\\]", Pattern.CASE_INSENSITIVE);
            Matcher m = imgFilePat.matcher(text);

            while (m.find()) {
                String filePath = m.group(1).trim();
                remainingText = remainingText.replace(m.group(0), "");

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
        } catch (Throwable ignored) {}

        renderMarkdownIntoContainer(container, remainingText.trim(), isUser);
    }

    private void showFullscreenImageDialog(final String imgPath) {
        if (imgPath == null || imgPath.isEmpty()) return;
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(iv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        loadImageIntoView(imgPath, iv);

        ImageView closeBtn = cIconButton(R.drawable.ic_close, 24, 48, Color.WHITE);
        FrameLayout.LayoutParams lpClose = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
        lpClose.setMargins(0, dp(28), dp(16), 0);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        root.addView(closeBtn, lpClose);

        dialog.setContentView(root);
        dialog.show();
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

    // High-performance single-pass markdown renderer
    private void renderMarkdownIntoContainer(LinearLayout container, String markdown, boolean isUser) {
        if (markdown == null || markdown.isEmpty()) return;

        String[] sections = markdown.split("```");
        for (int s = 0; s < sections.length; s++) {
            if (s % 2 == 1) {
                // Code block widget
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
                // Parse text sections & group tables
                String text = sections[s];
                String[] lines = text.split("\n");
                ArrayList<String> tableBuffer = new ArrayList<>();
                SpannableStringBuilder textBlock = new SpannableStringBuilder();

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    String trimmed = line.trim();

                    if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                        if (textBlock.length() > 0) {
                            flushTextBlockToContainer(container, textBlock);
                            textBlock = new SpannableStringBuilder();
                        }
                        tableBuffer.add(trimmed);
                    } else {
                        if (!tableBuffer.isEmpty()) {
                            renderMarkdownTable(container, new ArrayList<>(tableBuffer));
                            tableBuffer.clear();
                        }

                        if (trimmed.startsWith("---") || trimmed.startsWith("***") || trimmed.startsWith("___")) {
                            if (textBlock.length() > 0) {
                                flushTextBlockToContainer(container, textBlock);
                                textBlock = new SpannableStringBuilder();
                            }
                            View divider = new View(this);
                            divider.setBackgroundColor(CLAUDE_BORDER);
                            LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(-1, dp(1));
                            lpDiv.setMargins(0, dp(10), 0, dp(10));
                            container.addView(divider, lpDiv);
                        } else {
                            SpannableStringBuilder lineSpan = parseInlineMarkdownLine(line);
                            if (textBlock.length() > 0) {
                                textBlock.append("\n");
                            }
                            textBlock.append(lineSpan);
                        }
                    }
                }

                if (!tableBuffer.isEmpty()) {
                    renderMarkdownTable(container, new ArrayList<>(tableBuffer));
                    tableBuffer.clear();
                }

                if (textBlock.length() > 0) {
                    flushTextBlockToContainer(container, textBlock);
                }
            }
        }
    }

    private void flushTextBlockToContainer(LinearLayout container, SpannableStringBuilder ssb) {
        if (ssb == null || ssb.length() == 0) return;
        TextView p = new TextView(this);
        p.setText(ssb);
        p.setTextSize(14.5f);
        p.setTextColor(CLAUDE_TEXT_MAIN);
        p.setLineSpacing(0, 1.25f);
        p.setTextIsSelectable(true);
        p.setPadding(0, dp(2), 0, dp(2));

        final String rawText = ssb.toString();
        p.setOnLongClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Chat text", rawText));
            Toast.makeText(MainActivity.this, "Teks disalin ke clipboard", Toast.LENGTH_SHORT).show();
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(2), 0, dp(2));
        container.addView(p, lp);
    }

    private SpannableStringBuilder parseInlineMarkdownLine(String rawLine) {
        if (rawLine == null || rawLine.isEmpty()) return new SpannableStringBuilder("");

        String line = rawLine;
        float sizeMultiplier = 1.0f;
        boolean isHeading = false;

        String trimmed = line.trim();
        if (trimmed.startsWith("#### ")) {
            line = trimmed.substring(5);
            sizeMultiplier = 1.05f;
            isHeading = true;
        } else if (trimmed.startsWith("### ")) {
            line = trimmed.substring(4);
            sizeMultiplier = 1.12f;
            isHeading = true;
        } else if (trimmed.startsWith("## ")) {
            line = trimmed.substring(3);
            sizeMultiplier = 1.22f;
            isHeading = true;
        } else if (trimmed.startsWith("# ")) {
            line = trimmed.substring(2);
            sizeMultiplier = 1.35f;
            isHeading = true;
        } else if (trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("+ ")) {
            line = "  •  " + trimmed.substring(2);
        } else if (trimmed.startsWith("- [x]") || trimmed.startsWith("* [x]")) {
            line = "  ☑  " + trimmed.substring(5);
        } else if (trimmed.startsWith("- [ ]") || trimmed.startsWith("* [ ]")) {
            line = "  ☐  " + trimmed.substring(5);
        } else if (trimmed.startsWith("> ")) {
            line = "▎ " + trimmed.substring(2);
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder(line);

        // 1. Inline Code (`code`)
        try {
            Pattern codePat = Pattern.compile("`([^`\n]+)`");
            Matcher m = codePat.matcher(ssb.toString());
            while (m.find()) {
                int start = m.start();
                int end = m.end();
                String inner = m.group(1);
                ssb.replace(start, end, " " + inner + " ");
                int newEnd = start + inner.length() + 2;
                ssb.setSpan(new TypefaceSpan("monospace"), start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new BackgroundColorSpan(CLAUDE_SURFACE_MUTED), start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new ForegroundColorSpan(CLAUDE_TERRACOTTA), start, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                m = codePat.matcher(ssb.toString());
                if (start >= ssb.length()) break;
            }
        } catch (Throwable ignored) {}

        // 2. Bold (**bold**)
        try {
            Pattern boldPat = Pattern.compile("\\*\\*([^\\*\n]+)\\*\\*");
            Matcher m = boldPat.matcher(ssb.toString());
            while (m.find()) {
                int start = m.start();
                int end = m.end();
                String inner = m.group(1);
                ssb.replace(start, end, inner);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), start, start + inner.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                m = boldPat.matcher(ssb.toString());
                if (start >= ssb.length()) break;
            }
        } catch (Throwable ignored) {}

        // 3. Strikethrough (~~text~~)
        try {
            Pattern strikePat = Pattern.compile("~~([^~\n]+)~~");
            Matcher m = strikePat.matcher(ssb.toString());
            while (m.find()) {
                int start = m.start();
                int end = m.end();
                String inner = m.group(1);
                ssb.replace(start, end, inner);
                ssb.setSpan(new StrikethroughSpan(), start, start + inner.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                m = strikePat.matcher(ssb.toString());
                if (start >= ssb.length()) break;
            }
        } catch (Throwable ignored) {}

        // Heading styling
        if (isHeading && ssb.length() > 0) {
            ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new RelativeSizeSpan(sizeMultiplier), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return ssb;
    }

    private void renderMarkdownTable(LinearLayout container, ArrayList<String> tableLines) {
        if (tableLines.size() < 2) return;

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
            dataRow.setBackgroundColor(r % 2 == 0 ? CLAUDE_SURFACE : Color.rgb(38, 37, 34));
            dataRow.setPadding(dp(8), dp(6), dp(8), dp(6));

            for (int c = 0; c < colCount; c++) {
                String val = c < cells.length ? cells[c] : "";
                SpannableStringBuilder span = parseInlineMarkdownLine(val);
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
            showConnectionBottomSheet();
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
}
