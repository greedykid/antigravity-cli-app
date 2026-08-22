package com.greedykid.codexremote;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    // Claude Official Android Theme Color Palette
    private static final int CLAUDE_BG = Color.rgb(20, 21, 25);                  // #141519 Deep Background
    private static final int CLAUDE_SURFACE = Color.rgb(27, 28, 34);             // #1B1C22
    private static final int CLAUDE_USER_BUBBLE = Color.rgb(37, 38, 45);         // #25262D User Bubble
    private static final int CLAUDE_COMPOSER_BG = Color.rgb(30, 32, 38);         // #1E2026 Bottom Composer
    private static final int CLAUDE_CODE_BG = Color.rgb(13, 14, 18);             // #0D0E12 Monospace Box

    private static final int CLAUDE_AMBER = Color.rgb(217, 119, 6);              // #D97706 Claude Terracotta/Amber
    private static final int CLAUDE_PRIMARY = Color.rgb(168, 199, 250);          // #A8C7FA Soft Blue
    private static final int CLAUDE_GREEN = Color.rgb(109, 213, 140);            // #6DD58C Diff Green
    private static final int CLAUDE_RED = Color.rgb(242, 184, 181);              // #F2B8B5 Diff Red

    private static final int CLAUDE_TEXT_PRIMARY = Color.rgb(238, 238, 242);     // #EEEEF2 Crisp White
    private static final int CLAUDE_TEXT_SECONDARY = Color.rgb(160, 163, 175);   // #A0A3AF Muted Subtitle
    private static final int CLAUDE_TEXT_MUTED = Color.rgb(115, 118, 130);       // #737682 Time / Tag
    private static final int CLAUDE_OUTLINE = Color.rgb(52, 55, 66);             // #343742 Border Outline

    private static final int REQ_PICK_ATTACHMENT = 2001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    // UI Root and Views
    private LinearLayout rootLayout;
    private TextView topTitleText;
    private TextView topSubtitleText;
    private TextView topStatusDot;

    private ScrollView chatScrollView;
    private LinearLayout chatFeedContainer;
    private LinearLayout liveStreamingIndicator;
    private TextView liveStreamingStatusText;

    // Composer Components
    private LinearLayout composerContainer;
    private LinearLayout attachmentChip;
    private TextView attachmentChipText;
    private EditText composerInput;
    private Button composerEngineBtn;
    private Button composerAttachBtn;
    private Button composerSendBtn;

    private String attachedServerPath = null;
    private String activeConversationId = null;
    private String currentEngine = "antigravity";
    private boolean isProcessing = false;
    private final Set<Integer> renderedTurnIndices = new HashSet<>();

    // Live Streaming Poller
    private final Runnable livePollerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isProcessing) {
                pollLiveTranscriptStream();
                mainHandler.postDelayed(this, 1500); // Fast live poll every 1.5s
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
        buildClaudeInterface();
        initLiveSessionSync();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(livePollerRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isProcessing) {
            mainHandler.post(livePollerRunnable);
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable roundedBox(int fillColor, int borderColor, int borderWidthDp, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        if (borderWidthDp > 0 && borderColor != 0) {
            d.setStroke(dp(borderWidthDp), borderColor);
        }
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private TextView text(String str, float sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(str);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private void applyTouchBounce(final View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).setInterpolator(new OvershootInterpolator()).start();
                    break;
            }
            return false;
        });
    }

    private void animateFadeSlide(View view) {
        view.setAlpha(0f);
        view.setTranslationY(dp(14));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ==========================================
    // CLAUDE ANDROID TOP APP BAR & LAYOUT
    // ==========================================
    private void buildClaudeInterface() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(CLAUDE_BG);

        // 1. Claude Top Bar (Back, Hostname/Title, Subtitle, 3-dots Menu)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(14), dp(8), dp(14), dp(8));
        topBar.setBackgroundColor(CLAUDE_BG);

        Button backBtn = new Button(this);
        backBtn.setText("←");
        backBtn.setTextSize(20);
        backBtn.setTextColor(CLAUDE_TEXT_PRIMARY);
        backBtn.setBackground(null);
        backBtn.setPadding(0, 0, dp(8), 0);
        backBtn.setOnClickListener(v -> showSessionHistoryDialog());
        applyTouchBounce(backBtn);
        topBar.addView(backBtn, new LinearLayout.LayoutParams(dp(36), dp(40)));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setGravity(Gravity.CENTER_HORIZONTAL);

        topTitleText = text("VPS TENCENT REMOTE", 15.5f, CLAUDE_TEXT_PRIMARY, true);
        topTitleText.setGravity(Gravity.CENTER_HORIZONTAL);
        titleCol.addView(topTitleText);

        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_HORIZONTAL);

        topStatusDot = text("●", 9, CLAUDE_GREEN, false);
        AlphaAnimation pulse = new AlphaAnimation(0.3f, 1.0f);
        pulse.setDuration(900);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        topStatusDot.startAnimation(pulse);
        subRow.addView(topStatusDot);

        topSubtitleText = text(" VM-0-4-ubuntu", 11.5f, CLAUDE_TEXT_SECONDARY, false);
        subRow.addView(topSubtitleText);
        titleCol.addView(subRow);

        topBar.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        Button menuBtn = new Button(this);
        menuBtn.setText("⋮");
        menuBtn.setTextSize(20);
        menuBtn.setTextColor(CLAUDE_TEXT_PRIMARY);
        menuBtn.setBackground(null);
        menuBtn.setPadding(0, 0, 0, 0);
        menuBtn.setOnClickListener(v -> showSettingsMenuDialog());
        applyTouchBounce(menuBtn);
        topBar.addView(menuBtn, new LinearLayout.LayoutParams(dp(36), dp(40)));

        rootLayout.addView(topBar);

        // Thin divider
        View topDivider = new View(this);
        topDivider.setBackgroundColor(CLAUDE_OUTLINE);
        rootLayout.addView(topDivider, new LinearLayout.LayoutParams(-1, dp(0.8f)));

        // 2. Chat Feed ScrollView (Unified Claude Chat & Live Tool Accordions)
        chatScrollView = new ScrollView(this);
        chatScrollView.setFillViewport(true);
        chatScrollView.setVerticalScrollBarEnabled(false);
        chatScrollView.setPadding(dp(16), dp(10), dp(16), dp(10));

        chatFeedContainer = new LinearLayout(this);
        chatFeedContainer.setOrientation(LinearLayout.VERTICAL);

        chatScrollView.addView(chatFeedContainer);
        rootLayout.addView(chatScrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        // 3. Live Streaming Status Indicator Bar (Pops up when executing)
        liveStreamingIndicator = new LinearLayout(this);
        liveStreamingIndicator.setOrientation(LinearLayout.HORIZONTAL);
        liveStreamingIndicator.setGravity(Gravity.CENTER_VERTICAL);
        liveStreamingIndicator.setBackground(roundedBox(CLAUDE_SURFACE, CLAUDE_OUTLINE, 1, 14));
        liveStreamingIndicator.setPadding(dp(12), dp(6), dp(12), dp(6));
        liveStreamingIndicator.setVisibility(View.GONE);

        ProgressBar liveSpin = new ProgressBar(this);
        liveStreamingIndicator.addView(liveSpin, new LinearLayout.LayoutParams(dp(16), dp(16)));

        liveStreamingStatusText = text(" Antigravity is thinking & executing tools...", 12, CLAUDE_PRIMARY, false);
        liveStreamingIndicator.addView(liveStreamingStatusText, new LinearLayout.LayoutParams(0, -2, 1));

        Button stopBtn = new Button(this);
        stopBtn.setText("🛑 Stop");
        stopBtn.setTextSize(11);
        stopBtn.setAllCaps(false);
        stopBtn.setTextColor(CLAUDE_RED);
        stopBtn.setBackground(roundedBox(CLAUDE_COMPOSER_BG, CLAUDE_RED, 1, 10));
        stopBtn.setPadding(dp(8), 0, dp(8), 0);
        stopBtn.setOnClickListener(v -> stopRunningTask());
        applyTouchBounce(stopBtn);
        liveStreamingIndicator.addView(stopBtn, new LinearLayout.LayoutParams(-2, dp(28)));

        LinearLayout.LayoutParams lpLive = new LinearLayout.LayoutParams(-1, -2);
        lpLive.setMargins(dp(16), 0, dp(16), dp(6));
        rootLayout.addView(liveStreamingIndicator, lpLive);

        // 4. Claude Bottom Floating Composer (Matches Screenshot)
        buildClaudeComposer();
        rootLayout.addView(composerContainer);

        setContentView(rootLayout);
    }

    private void buildClaudeComposer() {
        composerContainer = new LinearLayout(this);
        composerContainer.setOrientation(LinearLayout.VERTICAL);
        composerContainer.setBackground(roundedBox(CLAUDE_COMPOSER_BG, CLAUDE_OUTLINE, 1, 24));
        composerContainer.setPadding(dp(14), dp(8), dp(12), dp(10));
        LinearLayout.LayoutParams lpComp = new LinearLayout.LayoutParams(-1, -2);
        lpComp.setMargins(dp(14), 0, dp(14), dp(14));
        composerContainer.setLayoutParams(lpComp);

        // Attachment Preview Chip (if any)
        attachmentChip = new LinearLayout(this);
        attachmentChip.setOrientation(LinearLayout.HORIZONTAL);
        attachmentChip.setGravity(Gravity.CENTER_VERTICAL);
        attachmentChip.setBackground(roundedBox(CLAUDE_SURFACE, CLAUDE_AMBER, 1, 12));
        attachmentChip.setPadding(dp(10), dp(4), dp(8), dp(4));
        attachmentChip.setVisibility(View.GONE);

        attachmentChipText = text("📎 file.jpg (Attached)", 11.5f, CLAUDE_AMBER, true);
        attachmentChip.addView(attachmentChipText, new LinearLayout.LayoutParams(0, -2, 1));

        TextView closeChip = text("✕", 13, CLAUDE_RED, true);
        closeChip.setPadding(dp(6), 0, 0, 0);
        closeChip.setOnClickListener(v -> {
            attachmentChip.setVisibility(View.GONE);
            attachedServerPath = null;
        });
        attachmentChip.addView(closeChip);

        LinearLayout.LayoutParams lpChip = new LinearLayout.LayoutParams(-1, -2);
        lpChip.setMargins(0, 0, 0, dp(6));
        composerContainer.addView(attachmentChip, lpChip);

        // Multiline Input Text ("Tambahkan masukan...")
        composerInput = new EditText(this);
        composerInput.setHint("Tambahkan masukan...");
        composerInput.setHintTextColor(CLAUDE_TEXT_MUTED);
        composerInput.setTextColor(CLAUDE_TEXT_PRIMARY);
        composerInput.setTextSize(14.5f);
        composerInput.setBackground(null);
        composerInput.setMinLines(1);
        composerInput.setMaxLines(5);
        composerInput.setSingleLine(false);
        composerInput.setPadding(0, dp(2), 0, dp(6));
        composerContainer.addView(composerInput, new LinearLayout.LayoutParams(-1, -2));

        // Bottom Controls Row: [ ⚡ Otomatis / Antigravity ] on left, [ 📎 ] and [ ➔ ] on right
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);

        composerEngineBtn = new Button(this);
        composerEngineBtn.setText("⚡ Otomatis");
        composerEngineBtn.setTextSize(12);
        composerEngineBtn.setAllCaps(false);
        composerEngineBtn.setTextColor(CLAUDE_TEXT_PRIMARY);
        composerEngineBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        composerEngineBtn.setBackground(roundedBox(CLAUDE_SURFACE, CLAUDE_OUTLINE, 1, 16));
        composerEngineBtn.setPadding(dp(12), dp(4), dp(12), dp(4));
        composerEngineBtn.setOnClickListener(v -> toggleEngine());
        applyTouchBounce(composerEngineBtn);
        bottomRow.addView(composerEngineBtn, new LinearLayout.LayoutParams(-2, dp(34)));

        View spacer = new View(this);
        bottomRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        composerAttachBtn = new Button(this);
        composerAttachBtn.setText("📎");
        composerAttachBtn.setTextSize(18);
        composerAttachBtn.setTextColor(CLAUDE_TEXT_SECONDARY);
        composerAttachBtn.setBackground(null);
        composerAttachBtn.setPadding(0, 0, 0, 0);
        composerAttachBtn.setOnClickListener(v -> openAttachmentPicker());
        applyTouchBounce(composerAttachBtn);
        LinearLayout.LayoutParams lpAtt = new LinearLayout.LayoutParams(dp(36), dp(36));
        lpAtt.setMargins(0, 0, dp(4), 0);
        bottomRow.addView(composerAttachBtn, lpAtt);

        composerSendBtn = new Button(this);
        composerSendBtn.setText("➔");
        composerSendBtn.setTextSize(18);
        composerSendBtn.setTextColor(Color.WHITE);
        composerSendBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        composerSendBtn.setBackground(roundedBox(CLAUDE_AMBER, 0, 0, 18));
        composerSendBtn.setOnClickListener(v -> sendUserPrompt());
        applyTouchBounce(composerSendBtn);
        bottomRow.addView(composerSendBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        composerContainer.addView(bottomRow);
    }

    private void toggleEngine() {
        if ("antigravity".equalsIgnoreCase(currentEngine)) {
            currentEngine = "codex";
            composerEngineBtn.setText("🚀 Codex");
            composerEngineBtn.setTextColor(CLAUDE_PRIMARY);
        } else {
            currentEngine = "antigravity";
            composerEngineBtn.setText("⚡ Otomatis");
            composerEngineBtn.setTextColor(CLAUDE_TEXT_PRIMARY);
        }
        prefs.edit().putString("engine", currentEngine).apply();
    }

    // ==========================================
    // REAL-TIME CHAT SEND & LIVE STREAM UPDATER
    // ==========================================
    private void sendUserPrompt() {
        String prompt = composerInput.getText().toString().trim();
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) {
            showConnectionDialog();
            return;
        }
        if (prompt.isEmpty() && attachedServerPath == null) return;
        if (isProcessing) return;

        final String fileToAttach = attachedServerPath;
        attachedServerPath = null;
        if (attachmentChip != null) attachmentChip.setVisibility(View.GONE);

        isProcessing = true;
        composerInput.setText("");
        composerInput.setEnabled(false);
        composerSendBtn.setEnabled(false);

        // 1. Instantly render user message with attached file in Claude Style
        addUserMessageView(prompt, fileToAttach);

        // 2. Show Live Streaming Progress Indicator
        liveStreamingIndicator.setVisibility(View.VISIBLE);
        liveStreamingStatusText.setText(" Antigravity sedang memproses & menjalankan aksi...");
        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));

        // 3. Start Live Poller immediately to fetch and render turns as they happen!
        mainHandler.removeCallbacks(livePollerRunnable);
        mainHandler.postDelayed(livePollerRunnable, 1200);

        // 4. Send asynchronous request to Bridge
        final String engine = currentEngine;
        executor.execute(() -> {
            try {
                JSONObject req = new JSONObject();
                req.put("prompt", prompt);
                req.put("engine", engine);
                req.put("resume", true);
                if (fileToAttach != null) {
                    req.put("attachedFile", fileToAttach);
                }
                if (activeConversationId != null && !activeConversationId.isEmpty()) {
                    req.put("conversationId", activeConversationId);
                }

                JSONObject res = executePost(endpoint, prefs.getString("token", ""), req);
                String responseText = res.optString("response", "Selesai.");

                mainHandler.post(() -> {
                    isProcessing = false;
                    mainHandler.removeCallbacks(livePollerRunnable);
                    liveStreamingIndicator.setVisibility(View.GONE);
                    composerInput.setEnabled(true);
                    composerSendBtn.setEnabled(true);

                    // Final sync of turns and response
                    pollLiveTranscriptStream();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    isProcessing = false;
                    mainHandler.removeCallbacks(livePollerRunnable);
                    liveStreamingIndicator.setVisibility(View.GONE);
                    composerInput.setEnabled(true);
                    composerSendBtn.setEnabled(true);

                    // If HTTP 502 / network timeout occurred, keep polling live turns so user output is not lost!
                    pollLiveTranscriptStream();
                });
            }
        });
    }

    private void addUserMessageView(String text, String attachedFile) {
        LinearLayout userCol = new LinearLayout(this);
        userCol.setOrientation(LinearLayout.VERTICAL);
        userCol.setGravity(Gravity.END);

        if (attachedFile != null && !attachedFile.isEmpty()) {
            String fName = attachedFile.contains("/") ? attachedFile.substring(attachedFile.lastIndexOf("/") + 1) : attachedFile;
            LinearLayout fChip = new LinearLayout(this);
            fChip.setOrientation(LinearLayout.HORIZONTAL);
            fChip.setGravity(Gravity.CENTER_VERTICAL);
            fChip.setBackground(roundedBox(CLAUDE_SURFACE, CLAUDE_OUTLINE, 1, 10));
            fChip.setPadding(dp(10), dp(4), dp(10), dp(4));

            TextView fTv = text("Dibaca " + fName, 12, CLAUDE_TEXT_SECONDARY, false);
            fChip.addView(fTv);

            LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(-2, -2);
            lpF.setMargins(0, 0, 0, dp(4));
            userCol.addView(fChip, lpF);
        }

        if (!text.isEmpty()) {
            LinearLayout bubble = new LinearLayout(this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setBackground(roundedBox(CLAUDE_USER_BUBBLE, 0, 0, 18));
            bubble.setPadding(dp(14), dp(10), dp(14), dp(10));

            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextSize(15);
            tv.setTextColor(CLAUDE_TEXT_PRIMARY);
            tv.setLineSpacing(0, 1.2f);
            tv.setTextIsSelectable(true);
            bubble.addView(tv);

            LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(-2, -2);
            lpB.setMargins(dp(36), 0, 0, 0);
            userCol.addView(bubble, lpB);
        }

        LinearLayout.LayoutParams lpCol = new LinearLayout.LayoutParams(-1, -2);
        lpCol.setMargins(0, dp(10), 0, dp(10));
        chatFeedContainer.addView(userCol, lpCol);
        animateFadeSlide(userCol);
        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void pollLiveTranscriptStream() {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) return;

        executor.execute(() -> {
            try {
                String liveUrl = endpoint.replace("/api/chat", "/api/session/live");
                HttpURLConnection c = (HttpURLConnection) new URL(liveUrl).openConnection();
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

                    mainHandler.post(() -> renderLiveTurns(json));
                }
            } catch (Exception e) {}
        });
    }

    private void renderLiveTurns(JSONObject json) {
        try {
            JSONObject session = json.optJSONObject("session");
            if (session != null) {
                activeConversationId = session.optString("conversationId", "");
                String sTitle = session.optString("title", "Active Task");
                topTitleText.setText(sTitle);
                topSubtitleText.setText(" " + session.optString("workspace", "VM-0-4-ubuntu"));
            }

            JSONObject proc = json.optJSONObject("process");
            boolean running = proc != null && proc.optBoolean("running", false);
            topStatusDot.setTextColor(running ? CLAUDE_GREEN : CLAUDE_TEXT_MUTED);

            JSONArray turns = json.optJSONArray("turns");
            if (turns == null) return;

            for (int i = 0; i < turns.length(); i++) {
                JSONObject turn = turns.getJSONObject(i);
                int index = turn.optInt("index", i);
                if (renderedTurnIndices.contains(index)) continue;
                renderedTurnIndices.add(index);

                String role = turn.optString("role", "assistant");
                String content = turn.optString("content", "");
                String title = turn.optString("title", "");

                if ("user".equalsIgnoreCase(role)) {
                    // Check if already shown
                    continue;
                }

                if ("tool".equalsIgnoreCase(role) || "thinking".equalsIgnoreCase(role)) {
                    addClaudeToolRowView(title, content, "thinking".equalsIgnoreCase(role));
                } else {
                    addClaudeAssistantTextBlock(content);
                }
            }
            chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
        } catch (Exception e) {}
    }

    // Claude Style Collapsible Tool Row (e.g. "Dibaca 93d...jpg >", "Mengedit 2 file +7 -2 >")
    private void addClaudeToolRowView(String title, final String content, boolean isThinking) {
        final LinearLayout rowCard = new LinearLayout(this);
        rowCard.setOrientation(LinearLayout.VERTICAL);
        rowCard.setBackground(null);
        rowCard.setPadding(0, dp(4), 0, dp(4));

        LinearLayout rowHeader = new LinearLayout(this);
        rowHeader.setOrientation(LinearLayout.HORIZONTAL);
        rowHeader.setGravity(Gravity.CENTER_VERTICAL);

        String displayTitle = (title != null && !title.isEmpty()) ? title : (isThinking ? "Thinking Process" : "Menjalankan aksi");
        TextView titleTv = text(displayTitle + "  ❯", 13.5f, CLAUDE_TEXT_SECONDARY, true);
        rowHeader.addView(titleTv, new LinearLayout.LayoutParams(0, -2, 1));
        rowCard.addView(rowHeader);

        // Collapsible Detail Box
        final LinearLayout detailBox = new LinearLayout(this);
        detailBox.setOrientation(LinearLayout.VERTICAL);
        detailBox.setVisibility(View.GONE);
        detailBox.setBackground(roundedBox(CLAUDE_CODE_BG, CLAUDE_OUTLINE, 1, 10));
        detailBox.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lpDet = new LinearLayout.LayoutParams(-1, -2);
        lpDet.setMargins(0, dp(6), 0, 0);

        TextView bodyTv = new TextView(this);
        bodyTv.setText(content);
        bodyTv.setTextSize(12);
        bodyTv.setTextColor(CLAUDE_TEXT_SECONDARY);
        bodyTv.setTypeface(Typeface.MONOSPACE);
        bodyTv.setTextIsSelectable(true);
        detailBox.addView(bodyTv);

        rowCard.addView(detailBox, lpDet);

        rowHeader.setOnClickListener(v -> {
            TransitionManager.beginDelayedTransition(chatFeedContainer, new AutoTransition().setDuration(160));
            boolean isOpen = (detailBox.getVisibility() == View.VISIBLE);
            detailBox.setVisibility(isOpen ? View.GONE : View.VISIBLE);
            titleTv.setText(displayTitle + (isOpen ? "  ❯" : "  ▼"));
        });
        applyTouchBounce(rowHeader);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(2), 0, dp(4));
        chatFeedContainer.addView(rowCard, lp);
        animateFadeSlide(rowCard);
    }

    // Assistant Response Typography directly on Canvas
    private void addClaudeAssistantTextBlock(String rawMarkdown) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(4), 0, dp(8));

        renderMarkdownIntoClaudeContainer(block, rawMarkdown);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(2), 0, dp(6));
        chatFeedContainer.addView(block, lp);
        animateFadeSlide(block);
    }

    // ==========================================
    // MARKDOWN PARSING ENGINE FOR CLAUDE THEME
    // ==========================================
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*?)(?:\\s+#+)?$");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*([^\\*]+)\\*(?!\\*)");
    private static final Pattern CODE_PATTERN = Pattern.compile("`([^`]+)`");

    private void renderMarkdownIntoClaudeContainer(LinearLayout target, String markdown) {
        if (markdown == null || markdown.isEmpty()) return;

        String[] sections = markdown.split("```");
        for (int s = 0; s < sections.length; s++) {
            if (s % 2 == 1) {
                // Code block
                String block = sections[s];
                String lang = "";
                String codeContent = block;
                int lf = block.indexOf("\n");
                if (lf > 0 && lf < 20) {
                    lang = block.substring(0, lf).trim();
                    codeContent = block.substring(lf + 1);
                }

                LinearLayout codeBox = new LinearLayout(this);
                codeBox.setOrientation(LinearLayout.VERTICAL);
                codeBox.setBackground(roundedBox(CLAUDE_CODE_BG, CLAUDE_OUTLINE, 1, 10));
                codeBox.setPadding(dp(12), dp(8), dp(12), dp(8));

                if (!lang.isEmpty()) {
                    TextView lTag = text(lang.toUpperCase(Locale.ROOT), 10.5f, CLAUDE_AMBER, true);
                    lTag.setPadding(0, 0, 0, dp(4));
                    codeBox.addView(lTag);
                }

                TextView codeTv = new TextView(this);
                codeTv.setText(codeContent.trim());
                codeTv.setTextSize(12.5f);
                codeTv.setTextColor(CLAUDE_TEXT_PRIMARY);
                codeTv.setTypeface(Typeface.MONOSPACE);
                codeTv.setTextIsSelectable(true);
                codeBox.addView(codeTv);

                final String copyStr = codeContent.trim();
                codeBox.setOnLongClickListener(v -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("Code", copyStr));
                    Toast.makeText(MainActivity.this, "Code copied", Toast.LENGTH_SHORT).show();
                    return true;
                });

                LinearLayout.LayoutParams lpCode = new LinearLayout.LayoutParams(-1, -2);
                lpCode.setMargins(0, dp(6), 0, dp(6));
                target.addView(codeBox, lpCode);
            } else {
                String textContent = sections[s];
                String[] lines = textContent.split("\n");
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    String trimmed = line.trim();

                    Matcher hMatch = HEADING_PATTERN.matcher(trimmed);
                    if (hMatch.matches()) {
                        int level = hMatch.group(1).length();
                        String hText = hMatch.group(2).trim();

                        TextView hView = new TextView(this);
                        hView.setText(parseInlineSpans(hText));
                        hView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                        hView.setTextSize(level == 1 ? 18f : (level == 2 ? 16f : 14.5f));
                        hView.setTextColor(level <= 2 ? CLAUDE_PRIMARY : CLAUDE_TEXT_PRIMARY);
                        hView.setPadding(0, dp(8), 0, dp(3));
                        hView.setTextIsSelectable(true);
                        target.addView(hView);
                    } else if (trimmed.equals("---") || trimmed.equals("***")) {
                        View div = new View(this);
                        div.setBackgroundColor(CLAUDE_OUTLINE);
                        LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(-1, dp(1));
                        lpDiv.setMargins(0, dp(8), 0, dp(8));
                        target.addView(div, lpDiv);
                    } else {
                        TextView p = new TextView(this);
                        p.setText(parseInlineSpans(line));
                        p.setTextSize(14.5f);
                        p.setTextColor(CLAUDE_TEXT_PRIMARY);
                        p.setLineSpacing(0, 1.25f);
                        p.setPadding(0, dp(2), 0, dp(3));
                        p.setTextIsSelectable(true);
                        target.addView(p);
                    }
                }
            }
        }
    }

    private SpannableStringBuilder parseInlineSpans(String raw) {
        String line = raw;
        if (line.trim().startsWith("* ") || line.trim().startsWith("- ")) {
            line = "  •  " + line.trim().substring(2);
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder(line);

        // Bold
        Matcher bm = BOLD_PATTERN.matcher(ssb.toString());
        while (bm.find()) {
            int start = bm.start();
            int end = bm.end();
            String inner = bm.group(1);
            ssb.replace(start, end, inner);
            ssb.setSpan(new StyleSpan(Typeface.BOLD), start, start + inner.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            bm = BOLD_PATTERN.matcher(ssb.toString());
        }

        // Inline code
        Matcher cm = CODE_PATTERN.matcher(ssb.toString());
        while (cm.find()) {
            int start = cm.start();
            int end = cm.end();
            String inner = cm.group(1);
            ssb.replace(start, end, " " + inner + " ");
            int spanEnd = start + inner.length() + 2;
            ssb.setSpan(new TypefaceSpan("monospace"), start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new BackgroundColorSpan(CLAUDE_COMPOSER_BG), start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new ForegroundColorSpan(CLAUDE_PRIMARY), start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            cm = CODE_PATTERN.matcher(ssb.toString());
        }

        return ssb;
    }

    // ==========================================
    // INITIAL SYNC & ATTACHMENT HANDLING
    // ==========================================
    private void initLiveSessionSync() {
        pollLiveTranscriptStream();
    }

    private void openAttachmentPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Pilih Gambar atau Dokumen"), REQ_PICK_ATTACHMENT);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak ada file manager", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_ATTACHMENT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            uploadAttachmentFile(data.getData());
        }
    }

    private void uploadAttachmentFile(Uri uri) {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) {
            showConnectionDialog();
            return;
        }

        Toast.makeText(this, "Mengupload gambar/file ke server...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                String fileName = getFileNameFromUri(uri);
                InputStream is = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) bos.write(buf, 0, len);
                is.close();

                byte[] bytes = bos.toByteArray();
                String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

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
                req.put("data", base64);
                c.getOutputStream().write(req.toString().getBytes(StandardCharsets.UTF_8));

                int code = c.getResponseCode();
                if (code == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder out = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) out.append(line);
                    JSONObject res = new JSONObject(out.toString());
                    final String sPath = res.optString("filePath", "");
                    final String sName = res.optString("filename", fileName);

                    mainHandler.post(() -> {
                        attachedServerPath = sPath;
                        attachmentChipText.setText("📎 " + sName + " (Attached)");
                        attachmentChip.setVisibility(View.VISIBLE);
                        Toast.makeText(MainActivity.this, "File siap dikirim!", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, "Gagal upload: HTTP " + code, Toast.LENGTH_SHORT).show());
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
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception e) {}
        }
        if (result == null) result = uri.getLastPathSegment();
        return result != null ? result : "upload_" + System.currentTimeMillis() + ".jpg";
    }

    private void stopRunningTask() {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) return;

        executor.execute(() -> {
            try {
                String ctrlUrl = endpoint.replace("/api/chat", "/api/session/control");
                HttpURLConnection c = (HttpURLConnection) new URL(ctrlUrl).openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                String token = prefs.getString("token", "");
                if (!token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);

                JSONObject req = new JSONObject();
                req.put("action", "stop");
                c.getOutputStream().write(req.toString().getBytes(StandardCharsets.UTF_8));
                int code = c.getResponseCode();

                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "Tugas dihentikan", Toast.LENGTH_SHORT).show();
                    isProcessing = false;
                    liveStreamingIndicator.setVisibility(View.GONE);
                    composerInput.setEnabled(true);
                    composerSendBtn.setEnabled(true);
                    pollLiveTranscriptStream();
                });
            } catch (Exception e) {}
        });
    }

    // ==========================================
    // MENUS & DIALOGS
    // ==========================================
    private void showSettingsMenuDialog() {
        String[] options = {
                "⚙ Gateway & Connection Setup",
                "📜 Riwayat Semua Sesi (History)",
                "🔄 Refresh Status & Telemetri",
                "📥 Check Latest APK Update",
                "🛑 Stop Antigravity Process"
        };

        new AlertDialog.Builder(this)
                .setTitle("Remote Control Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showConnectionDialog();
                    else if (which == 1) showSessionHistoryDialog();
                    else if (which == 2) pollLiveTranscriptStream();
                    else if (which == 3) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/greedykid/codexcli-remote-app/actions")));
                    } else if (which == 4) stopRunningTask();
                })
                .show();
    }

    private void showSessionHistoryDialog() {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) {
            showConnectionDialog();
            return;
        }

        final AlertDialog ld = new AlertDialog.Builder(this)
                .setTitle("Memuat Riwayat Sesi...")
                .setMessage("Mengambil daftar percakapan dari server...")
                .create();
        ld.show();

        executor.execute(() -> {
            try {
                String sessionsUrl = endpoint.replace("/api/chat", "/api/sessions");
                HttpURLConnection c = (HttpURLConnection) new URL(sessionsUrl).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(8000);
                String token = prefs.getString("token", "");
                if (!token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);

                int code = c.getResponseCode();
                if (code == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder b = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) b.append(line);
                    JSONObject json = new JSONObject(b.toString());
                    JSONArray sessions = json.optJSONArray("sessions");

                    mainHandler.post(() -> {
                        ld.dismiss();
                        displayHistoryListModal(sessions);
                    });
                } else {
                    mainHandler.post(() -> {
                        ld.dismiss();
                        Toast.makeText(MainActivity.this, "Gagal mengambil sesi: HTTP " + code, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    ld.dismiss();
                    Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void displayHistoryListModal(JSONArray sessions) {
        if (sessions == null || sessions.length() == 0) {
            Toast.makeText(this, "Tidak ada riwayat sesi ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] titles = new String[sessions.length()];
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject s = sessions.optJSONObject(i);
            String title = s != null ? s.optString("title", "Sesi #" + (i + 1)) : ("Sesi #" + (i + 1));
            titles[i] = (i + 1) + ". " + title;
        }

        new AlertDialog.Builder(this)
                .setTitle("Pilih Sesi untuk Dilanjutkan")
                .setItems(titles, (dialog, which) -> {
                    JSONObject s = sessions.optJSONObject(which);
                    if (s != null) {
                        activeConversationId = s.optString("conversationId", "");
                        renderedTurnIndices.clear();
                        chatFeedContainer.removeAllViews();
                        pollLiveTranscriptStream();
                        Toast.makeText(MainActivity.this, "Beralih ke sesi: " + titles[which], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showConnectionDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(10), dp(22), dp(10));

        TextView urlLbl = text("Bridge Endpoint URL:", 12.5f, CLAUDE_TEXT_SECONDARY, true);
        form.addView(urlLbl);

        EditText urlInput = new EditText(this);
        urlInput.setHint("https://your-tunnel.trycloudflare.com/api/chat");
        urlInput.setText(prefs.getString("url", ""));
        urlInput.setTextColor(CLAUDE_TEXT_PRIMARY);
        urlInput.setHintTextColor(CLAUDE_TEXT_MUTED);
        urlInput.setTextSize(14);
        urlInput.setBackground(roundedBox(CLAUDE_SURFACE, CLAUDE_OUTLINE, 1, 10));
        urlInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpUrl = new LinearLayout.LayoutParams(-1, dp(48));
        lpUrl.setMargins(0, dp(4), 0, dp(14));
        form.addView(urlInput, lpUrl);

        TextView tokLbl = text("Bearer Token (Secret):", 12.5f, CLAUDE_TEXT_SECONDARY, true);
        form.addView(tokLbl);

        EditText tokenInput = new EditText(this);
        tokenInput.setHint("codex-remote-token-2026");
        tokenInput.setText(prefs.getString("token", ""));
        tokenInput.setTextColor(CLAUDE_TEXT_PRIMARY);
        tokenInput.setHintTextColor(CLAUDE_TEXT_MUTED);
        tokenInput.setTextSize(14);
        tokenInput.setInputType(0x00000081);
        tokenInput.setBackground(roundedBox(CLAUDE_SURFACE, CLAUDE_OUTLINE, 1, 10));
        tokenInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpTok = new LinearLayout.LayoutParams(-1, dp(48));
        lpTok.setMargins(0, dp(4), 0, dp(12));
        form.addView(tokenInput, lpTok);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Remote Gateway Setup")
                .setMessage("Masukkan HTTPS Cloudflare URL atau endpoint server bridge:")
                .setView(form)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan & Hubungkan", null)
                .create();

        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String u = urlInput.getText().toString().trim();
            String t = tokenInput.getText().toString().trim();
            prefs.edit().putString("url", u).putString("token", t).apply();
            dialog.dismiss();
            pollLiveTranscriptStream();
            Toast.makeText(MainActivity.this, "Konfigurasi tersimpan!", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private JSONObject executePost(String endpoint, String token, JSONObject req) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(15000);
        c.setReadTimeout(300000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        if (!token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);

        byte[] body = req.toString().getBytes(StandardCharsets.UTF_8);
        c.getOutputStream().write(body);

        int code = c.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 400 ? c.getErrorStream() : c.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line).append("\n");

        if (code >= 400) throw new Exception(out.toString().isEmpty() ? "HTTP " + code : out.toString().trim());
        return new JSONObject(out.toString());
    }
}
