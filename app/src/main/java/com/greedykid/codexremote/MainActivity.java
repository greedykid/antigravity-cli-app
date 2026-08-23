package com.greedykid.codexremote;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
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
import android.text.method.LinkMovementMethod;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.ReplacementSpan;
import android.text.style.UnderlineSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

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
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;
import java.util.HashSet;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private boolean isAppInForeground = true;
    private String liveStreamingAssistantText = "";
    private String lastFailedPrompt = null;
    private long lastLiveSyncTimestamp = 0;
    private boolean isSyncScheduled = false;


    private static final String CHANNEL_TASK_NOTIFICATIONS = "channel_ai_task_alerts";
    private HorizontalScrollView quickActionScroll;
    private HorizontalScrollView slashSuggestionsScroll;
    private LinearLayout slashSuggestionsRow;
    private String hubSearchQuery = "";
    private JSONArray cachedHubSessionsRaw = null;

    private LinearLayout quickActionRow;
    private static final int REQ_CAMERA_CAPTURE = 1004;
    private Uri cameraCaptureUri;

    // Claude Dark Theme Palette (Exact Match to Official Claude App)

    private static final int REQ_PICK_FILES = 1001;
    private static final int REQ_VOICE_SPEECH = 1002;
    private static final int REQ_CAMERA_PERMISSION = 2001;
    private static final int REQ_NOTIFICATION_PERMISSION = 2004;
    private static final int REQ_PICK_QR_IMAGE = 1003;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    // Image Bitmap Memory Cache
    private final ConcurrentHashMap<String, Bitmap> imageCache = new ConcurrentHashMap<>();


    private View buildQuickActionToolbar() {
        quickActionScroll = new HorizontalScrollView(this);
        quickActionScroll.setHorizontalScrollBarEnabled(false);
        quickActionScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        quickActionScroll.setPadding(0, 0, 0, dp(6));

        quickActionRow = new LinearLayout(this);
        quickActionRow.setOrientation(LinearLayout.HORIZONTAL);
        quickActionRow.setGravity(Gravity.CENTER_VERTICAL);

        // Prompts & Tools
        addQuickChip(quickActionRow, "⚡ Perbaiki Error", "Tolong perbaiki error berikut: ");
        addQuickChip(quickActionRow, "🔍 Review Kode", "Tolong review dan periksa kode ini untuk potensi bug atau peningkatan: ");
        addQuickChip(quickActionRow, "🧪 Jalankan Test", "Jalankan test suite dan laporkan hasilnya.");
        addQuickChip(quickActionRow, "📖 Jelaskan Alur", "Jelaskan alur kerja kode ini secara ringkas.");
        addQuickChip(quickActionRow, "📦 Git Status", "Cek git status dan rangkum perubahan.");
        addQuickChip(quickActionRow, "🚀 Git Diff", "Tampilkan git diff dari perubahan terbaru.");
        addQuickChip(quickActionRow, "📝 Buat Commit", "Buat commit git dengan pesan yang jelas untuk perubahan saat ini.");
        
        // Code Symbols
        addSymbolChip(quickActionRow, "```", "```\n\n```", 4);
        addSymbolChip(quickActionRow, "{ }", "{  }", 2);
        addSymbolChip(quickActionRow, "[ ]", "[  ]", 2);
        addSymbolChip(quickActionRow, "/* */", "/*  */", 3);
        addSymbolChip(quickActionRow, "->", "-> ", 3);
        addSymbolChip(quickActionRow, "$", "$ ", 2);
        addSymbolChip(quickActionRow, "/", "/", 1);

        quickActionScroll.addView(quickActionRow, new ViewGroup.LayoutParams(-2, -2));
        return quickActionScroll;
    }

    private void addQuickChip(LinearLayout parent, String label, final String promptToInsert) {
        TextView chip = cText(label, 12f, Theme.TEXT_MAIN, true, false);
        chip.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 14));
        chip.setPadding(dp(11), dp(5), dp(11), dp(5));
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> {
            vibrateTick();
            String current = promptInput.getText().toString();
            if (current.isEmpty()) {
                promptInput.setText(promptToInsert);
            } else {
                promptInput.append("\n" + promptToInsert);
            }
            promptInput.requestFocus();
            promptInput.setSelection(promptInput.getText().length());
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(6), 0);
        parent.addView(chip, lp);
    }

    private void addSymbolChip(LinearLayout parent, String display, final String snippet, final int cursorOffset) {
        TextView chip = cText(display, 12f, Theme.ACCENT, true, false);
        chip.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        chip.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 14));
        chip.setPadding(dp(11), dp(5), dp(11), dp(5));
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> {
            vibrateTick();
            int start = Math.max(0, promptInput.getSelectionStart());
            int end = Math.max(0, promptInput.getSelectionEnd());
            promptInput.getText().replace(Math.min(start, end), Math.max(start, end), snippet, 0, snippet.length());
            promptInput.requestFocus();
            int newCursor = start + cursorOffset;
            if (newCursor <= promptInput.getText().length()) {
                promptInput.setSelection(newCursor);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(6), 0);
        parent.addView(chip, lp);
    }

    private void vibrateTick() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(18, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(18);
                }
            }
        } catch (Exception ignored) {}
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel(
                    CHANNEL_TASK_NOTIFICATIONS,
                    "Task Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                );
                ch.setDescription("Memberi tahu saat tugas AI selesai di background");
                ch.enableLights(true);
                ch.enableVibration(true);
                ch.setLightColor(Theme.ACCENT);
                nm.createNotificationChannel(ch);
            }
        }
    }

    private void showTaskCompletionNotification(String sessionTitle, boolean success, String details) {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (activeConversationId != null) {
                intent.putExtra("open_conversation_id", activeConversationId);
            }
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE :
                android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 100, intent, flags);

            String title = (success ? "✅ Tugas Selesai: " : "⚠️ Tugas Gagal: ") + (sessionTitle != null && !sessionTitle.isEmpty() ? sessionTitle : "Antigravity");
            String message = details != null && !details.isEmpty() ? details : (success ? "AI telah selesai menjalankan tugas coding Anda." : "Terjadi kendala saat menjalankan tugas.");

            androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, CHANNEL_TASK_NOTIFICATIONS)
                .setSmallIcon(R.drawable.ic_code)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pi);

            nm.notify(NOTIFY_RESULT_ID, builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static final int NOTIFY_RESULT_ID = 8821;


    private void renderRetryFailedMessageBlock(final String failedText, final String errorReason) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(cBox(Theme.SURFACE, Theme.RED, 1, 14));
        card.setPadding(dp(14), dp(10), dp(14), dp(10));

        ImageView icon = cIcon(R.drawable.ic_close, 18, Theme.RED);
        card.addView(icon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(10), 0, dp(8), 0);

        TextView errView = cText("Gagal terkirim: " + (failedText.length() > 30 ? failedText.substring(0, 27) + "..." : failedText), 13f, Theme.TEXT_MAIN, true, false);
        textCol.addView(errView);

        TextView subView = cText(errorReason != null ? errorReason : "Koneksi gateway terputus.", 11.5f, Theme.RED, false, false);
        textCol.addView(subView);

        card.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView btnRetry = cText("Kirim Ulang 🔄", 12.5f, Theme.ON_ACCENT, true, false);
        btnRetry.setBackground(cBox(Theme.ACCENT, 0, 0, 10));
        btnRetry.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnRetry.setClickable(true);
        btnRetry.setFocusable(true);
        btnRetry.setOnClickListener(v -> {
            chatMessagesList.removeView(card);
            promptInput.setText(failedText);
            sendClaudePrompt();
        });
        card.addView(btnRetry);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(16), dp(8), dp(16), dp(8));
        chatMessagesList.addView(card, lp);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }


    private void scheduleThrottledSync() {
        long now = System.currentTimeMillis();
        if (now - lastLiveSyncTimestamp > 600) {
            lastLiveSyncTimestamp = now;
            if (currentScreen == 1) syncLiveExecution();
        } else if (!isSyncScheduled) {
            isSyncScheduled = true;
            mainHandler.postDelayed(() -> {
                isSyncScheduled = false;
                lastLiveSyncTimestamp = System.currentTimeMillis();
                if (currentScreen == 1) syncLiveExecution();
            }, 600);
        }
    }


    // ============================================================
    // FEATURE A: SLASH COMMANDS AUTOCOMPLETE POPUP ( / )
    // ============================================================
    private static class SlashCmd {
        String cmd;
        String desc;
        String prompt;
        SlashCmd(String c, String d, String p) { this.cmd = c; this.desc = d; this.prompt = p; }
    }

    private static final SlashCmd[] SLASH_COMMANDS = new SlashCmd[]{
        new SlashCmd("/diff", "Tampilkan git diff perubahan terbaru", "Tampilkan git diff dari perubahan terbaru di repository ini."),
        new SlashCmd("/test", "Jalankan automated test suite", "Jalankan semua test suite di project dan laporkan hasilnya."),
        new SlashCmd("/commit", "Buat commit git otomatis", "Buat commit git dengan deskripsi ringkas dan rapi untuk perubahan saat ini."),
        new SlashCmd("/review", "Review kode & analisis kualitas", "Tolong review kode terbaru di workspace ini, periksa potensi bug, performa, dan keamanan."),
        new SlashCmd("/explain", "Jelaskan alur arsitektur kode", "Jelaskan arsitektur dan alur kerja utama dari codebase project ini secara ringkas."),
        new SlashCmd("/status", "Cek status repository git", "Periksa git status dan rangkum file apa saja yang diubah atau belum di-stage."),
        new SlashCmd("/fix", "Perbaiki bug atau error kode", "Tolong perbaiki bug atau error berikut pada project ini: ")
    };

    private View buildSlashCommandsPopup() {
        slashSuggestionsScroll = new HorizontalScrollView(this);
        slashSuggestionsScroll.setHorizontalScrollBarEnabled(false);
        slashSuggestionsScroll.setVisibility(View.GONE);
        slashSuggestionsScroll.setPadding(0, 0, 0, dp(6));

        slashSuggestionsRow = new LinearLayout(this);
        slashSuggestionsRow.setOrientation(LinearLayout.HORIZONTAL);
        slashSuggestionsRow.setGravity(Gravity.CENTER_VERTICAL);
        slashSuggestionsScroll.addView(slashSuggestionsRow, new ViewGroup.LayoutParams(-2, -2));
        return slashSuggestionsScroll;
    }

    private void updateSlashCommandsSuggestions(String text) {
        if (slashSuggestionsScroll == null || slashSuggestionsRow == null) return;
        if (!text.startsWith("/")) {
            slashSuggestionsScroll.setVisibility(View.GONE);
            return;
        }

        String filter = text.toLowerCase().trim();
        slashSuggestionsRow.removeAllViews();
        int matchCount = 0;

        for (final SlashCmd sc : SLASH_COMMANDS) {
            if (sc.cmd.toLowerCase().startsWith(filter) || filter.equals("/")) {
                matchCount++;
                LinearLayout chip = new LinearLayout(this);
                chip.setOrientation(LinearLayout.HORIZONTAL);
                chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setBackground(cBox(Theme.SURFACE, Theme.ACCENT, 1, 14));
                chip.setPadding(dp(12), dp(6), dp(12), dp(6));
                chip.setClickable(true);
                chip.setFocusable(true);

                TextView cmdTv = cText(sc.cmd, 12.5f, Theme.ACCENT, true, false);
                cmdTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                chip.addView(cmdTv);

                TextView descTv = cText(" • " + sc.desc, 11.5f, Theme.TEXT_MUTED, false, false);
                chip.addView(descTv);

                chip.setOnClickListener(v -> {
                    vibrateTick();
                    promptInput.setText(sc.prompt);
                    promptInput.requestFocus();
                    promptInput.setSelection(promptInput.getText().length());
                    slashSuggestionsScroll.setVisibility(View.GONE);
                });

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.setMargins(0, 0, dp(6), 0);
                slashSuggestionsRow.addView(chip, lp);
            }
        }

        slashSuggestionsScroll.setVisibility(matchCount > 0 ? View.VISIBLE : View.GONE);
    }

    // ============================================================
    // FEATURE B: PIN & UNPIN SESSION MANAGEMENT
    // ============================================================
    private Set<String> getPinnedSessionIds() {
        Set<String> set = prefs.getStringSet("pinned_sessions_ids", null);
        return set != null ? new HashSet<>(set) : new HashSet<String>();
    }

    private void togglePinSession(String convId) {
        if (convId == null || convId.isEmpty()) return;
        Set<String> pinned = getPinnedSessionIds();
        boolean isPinned = pinned.contains(convId);
        if (isPinned) {
            pinned.remove(convId);
            Toast.makeText(this, "Sesi batal disematkan", Toast.LENGTH_SHORT).show();
        } else {
            pinned.add(convId);
            Toast.makeText(this, "📌 Sesi disematkan ke paling atas", Toast.LENGTH_SHORT).show();
        }
        prefs.edit().putStringSet("pinned_sessions_ids", pinned).apply();
        fetchHubSessions();
    }

    // ============================================================
    // FEATURE C: WORKSPACE FILE EXPLORER & CODE VIEWER
    // ============================================================
    private void openFileExplorerModal(final String dirPath) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int fullHeight = (int) (dm.heightPixels * 0.88f);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(cBox(Theme.BG, Theme.BORDER, 1, 24));
        root.setPadding(dp(20), dp(14), dp(20), dp(16));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView folderIc = cIcon(R.drawable.ic_folder, 22, Theme.ACCENT);
        header.addView(folderIc);

        TextView title = cText("  File Explorer", 17f, Theme.TEXT_MAIN, true, false);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView closeBtn = cIconButton(R.drawable.ic_close, 20, 36, Theme.TEXT_MAIN);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        header.addView(closeBtn);
        root.addView(header);

        // Path bar
        final TextView pathView = cText("/" + (dirPath.equals(".") ? "" : dirPath), 12f, Theme.TEXT_MUTED, false, false);
        pathView.setTypeface(Typeface.MONOSPACE);
        pathView.setPadding(0, dp(4), 0, dp(10));
        root.addView(pathView);

        final ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        final ProgressBar pb = new ProgressBar(this);
        root.addView(pb, new LinearLayout.LayoutParams(-2, -2, Gravity.CENTER));

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, fullHeight);
            dialog.getWindow().setGravity(Gravity.BOTTOM);
        }
        dialog.show();

        executor.execute(() -> {
            try {
                JSONObject res = bridge.get("/api/files?path=" + BridgeClient.encode(dirPath), 10000);
                mainHandler.post(() -> {
                    pb.setVisibility(View.GONE);
                    if (!res.optBoolean("ok", true) && res.has("error")) {
                        list.addView(cText("Error: " + res.optString("error"), 13f, Theme.RED, false, false));
                        return;
                    }
                    final String parent = res.optString("parent", null);
                    if (parent != null && !parent.isEmpty()) {
                        LinearLayout upRow = createFileRow("📁 .. (Kembali ke folder atas)", true, 0L);
                        upRow.setOnClickListener(v -> {
                            dialog.dismiss();
                            openFileExplorerModal(parent);
                        });
                        list.addView(upRow);
                    }

                    JSONArray entries = res.optJSONArray("entries");
                    if (entries == null || entries.length() == 0) {
                        list.addView(cText("Folder kosong", 13f, Theme.TEXT_MUTED, false, false));
                        return;
                    }

                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject ent = entries.optJSONObject(i);
                        if (ent == null) continue;
                        final String name = ent.optString("name", "");
                        final String type = ent.optString("type", "file");
                        final long size = ent.optLong("size", 0);
                        final boolean isDir = "dir".equalsIgnoreCase(type);
                        final String relativeTarget = (dirPath.equals(".") ? "" : dirPath + "/") + name;

                        LinearLayout row = createFileRow((isDir ? "📁 " : "📄 ") + name, isDir, size);
                        row.setOnClickListener(v -> {
                            if (isDir) {
                                dialog.dismiss();
                                openFileExplorerModal(relativeTarget);
                            } else {
                                openCodeViewerModal(relativeTarget);
                            }
                        });
                        list.addView(row);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    pb.setVisibility(View.GONE);
                    list.addView(cText("Gagal membaca folder: " + e.getMessage(), 13f, Theme.RED, false, false));
                });
            }
        });
    }

    private LinearLayout createFileRow(String label, boolean isDir, long sizeBytes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 12));
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setClickable(true);
        row.setFocusable(true);

        TextView t = cText(label, 13.5f, isDir ? Theme.ACCENT : Theme.TEXT_MAIN, isDir, false);
        t.setTypeface(Typeface.MONOSPACE);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));

        if (!isDir && sizeBytes > 0) {
            String szStr = sizeBytes > 1024 ? (sizeBytes / 1024) + " KB" : sizeBytes + " B";
            TextView s = cText(szStr, 11.5f, Theme.TEXT_MUTED, false, false);
            row.addView(s);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(6));
        row.setLayoutParams(lp);
        return row;
    }

    private void openCodeViewerModal(final String filePath) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int fullHeight = (int) (dm.heightPixels * 0.90f);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(cBox(Theme.BG, Theme.BORDER, 1, 24));
        root.setPadding(dp(20), dp(14), dp(20), dp(16));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = cText(new File(filePath).getName(), 16f, Theme.TEXT_MAIN, true, false);
        title.setTypeface(Typeface.MONOSPACE);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView btnInsert = cText("Masukkan ke Chat 💬", 12f, Theme.ACCENT, true, false);
        btnInsert.setBackground(cBox(Theme.ACCENT_SOFT, 0, 0, 10));
        btnInsert.setPadding(dp(8), dp(4), dp(8), dp(4));
        btnInsert.setClickable(true);
        header.addView(btnInsert);

        ImageView closeBtn = cIconButton(R.drawable.ic_close, 20, 36, Theme.TEXT_MAIN);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        header.addView(closeBtn);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        HorizontalScrollView hScroll = new HorizontalScrollView(this);

        final TextView codeView = cText("Memuat file...", 12.5f, Theme.TEXT_MAIN, false, false);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setTextIsSelectable(true);
        codeView.setPadding(dp(12), dp(12), dp(12), dp(12));
        codeView.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 14));

        hScroll.addView(codeView);
        scroll.addView(hScroll);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, fullHeight);
            dialog.getWindow().setGravity(Gravity.BOTTOM);
        }
        dialog.show();

        executor.execute(() -> {
            try {
                JSONObject res = bridge.get("/api/files/read?path=" + BridgeClient.encode(filePath), 10000);
                final String fileContent = res.optString("content", "");
                mainHandler.post(() -> {
                    codeView.setText(fileContent.isEmpty() ? "(File kosong atau biner)" : fileContent);
                    btnInsert.setOnClickListener(v -> {
                        dialog.dismiss();
                        promptInput.append("\n[File: " + filePath + "]\n");
                        promptInput.requestFocus();
                        Toast.makeText(MainActivity.this, "File dimasukkan ke composer", Toast.LENGTH_SHORT).show();
                    });
                });
            } catch (Exception e) {
                mainHandler.post(() -> codeView.setText("Gagal membaca file: " + e.getMessage()));
            }
        });
    }

    // ============================================================
    // FEATURE D: INTERACTIVE QUICK TERMINAL MODAL
    // ============================================================
    private void openQuickTerminalModal() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int fullHeight = (int) (dm.heightPixels * 0.90f);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(cBox(Color.parseColor("#0d1117"), Theme.BORDER, 1, 24));
        root.setPadding(dp(18), dp(12), dp(18), dp(16));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView termIc = cIcon(R.drawable.ic_code, 22, Theme.GREEN);
        header.addView(termIc);

        TextView title = cText("  Quick Terminal (PTY)", 16f, Theme.TEXT_MAIN, true, false);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView clearBtn = cText("Clear", 12f, Theme.TEXT_MUTED, true, false);
        clearBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
        header.addView(clearBtn);

        ImageView closeBtn = cIconButton(R.drawable.ic_close, 20, 36, Theme.TEXT_MAIN);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        header.addView(closeBtn);
        root.addView(header);

        // Preset commands chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);

        final String[] PRESETS = new String[]{"git status", "git diff", "git log -n 5", "ls -la", "npm test", "docker ps", "free -m"};

        // Output Console
        final ScrollView outScroll = new ScrollView(this);
        outScroll.setFillViewport(true);
        final TextView outView = cText("$ Antigravity Terminal Ready.\nKetik perintah bash di bawah:\n", 12f, Color.parseColor("#c9d1d9"), false, false);
        outView.setTypeface(Typeface.MONOSPACE);
        outView.setTextIsSelectable(true);
        outView.setPadding(dp(10), dp(10), dp(10), dp(10));
        outScroll.addView(outView);
        root.addView(outScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        clearBtn.setOnClickListener(v -> outView.setText("$ Terminal cleared.\n"));

        // Input row
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, dp(8), 0, 0);

        final EditText cmdInput = new EditText(this);
        cmdInput.setHint("Ketik perintah bash (cth: git status)...");
        cmdInput.setHintTextColor(Theme.TEXT_LIGHT);
        cmdInput.setTextColor(Theme.TEXT_MAIN);
        cmdInput.setTextSize(13.5f);
        cmdInput.setTypeface(Typeface.MONOSPACE);
        cmdInput.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 14));
        cmdInput.setPadding(dp(12), dp(8), dp(12), dp(8));
        inputRow.addView(cmdInput, new LinearLayout.LayoutParams(0, dp(44), 1));

        final TextView btnExec = cText(" Run 🚀", 13f, Theme.ON_ACCENT, true, false);
        btnExec.setBackground(cBox(Theme.ACCENT, 0, 0, 14));
        btnExec.setPadding(dp(14), dp(10), dp(14), dp(10));
        btnExec.setClickable(true);

        final Runnable execAction = () -> {
            final String cmd = cmdInput.getText().toString().trim();
            if (cmd.isEmpty()) return;
            cmdInput.setText("");
            outView.append("\n$ " + cmd + "\n[Menjalankan...]\n");
            outScroll.post(() -> outScroll.fullScroll(View.FOCUS_DOWN));

            executor.execute(() -> {
                try {
                    JSONObject req = new JSONObject();
                    req.put("command", cmd);
                    JSONObject res = executePost(prefs.getString("url", "").replace("/api/chat", "/api/terminal/exec"),
                            prefs.getString("token", ""), req);
                    final String output = res.optString("output", "");
                    mainHandler.post(() -> {
                        outView.append(output.isEmpty() ? "(Selesai tanpa output)\n" : output + "\n");
                        outScroll.post(() -> outScroll.fullScroll(View.FOCUS_DOWN));
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        outView.append("Error: " + e.getMessage() + "\n");
                        outScroll.post(() -> outScroll.fullScroll(View.FOCUS_DOWN));
                    });
                }
            });
        };

        btnExec.setOnClickListener(v -> execAction.run());
        inputRow.addView(btnExec, new LinearLayout.LayoutParams(-2, dp(44)));
        root.addView(inputRow);

        for (final String pr : PRESETS) {
            TextView pChip = cText(pr, 11.5f, Theme.ACCENT, true, false);
            pChip.setTypeface(Typeface.MONOSPACE);
            pChip.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 10));
            pChip.setPadding(dp(8), dp(4), dp(8), dp(4));
            pChip.setClickable(true);
            pChip.setOnClickListener(v -> {
                cmdInput.setText(pr);
                execAction.run();
            });
            LinearLayout.LayoutParams lpPr = new LinearLayout.LayoutParams(-2, -2);
            lpPr.setMargins(0, dp(6), dp(6), dp(6));
            chipRow.addView(pChip, lpPr);
        }
        chipScroll.addView(chipRow);
        root.addView(chipScroll, 1);

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, fullHeight);
            dialog.getWindow().setGravity(Gravity.BOTTOM);
        }
        dialog.show();
    }


    private void showSessionOptionsDialog(final String convId, final String title) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 20));
        root.setPadding(dp(20), dp(16), dp(20), dp(16));

        TextView t = cText(title, 15f, Theme.TEXT_MAIN, true, false);
        root.addView(t);

        boolean isPinned = getPinnedSessionIds().contains(convId);
        LinearLayout optPin = createAttachmentOptionRow(isPinned ? "❌  Batal Sematkan Sesi" : "📌  Sematkan Sesi ke Paling Atas", "Tetap berada di bagian atas daftar");
        optPin.setOnClickListener(v -> {
            dialog.dismiss();
            togglePinSession(convId);
        });
        root.addView(optPin);

        LinearLayout optRename = createAttachmentOptionRow("✏️  Ubah Nama Sesi", "Ganti judul sesi ini");
        optRename.setOnClickListener(v -> {
            dialog.dismiss();
            showRenameSessionDialog(convId, title);
        });
        root.addView(optRename);

        dialog.setContentView(root);
        dialog.show();
    }

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
    private LinearLayout sidebarRecentContainer;
    private final Map<Integer, LinearLayout> sidebarNavRows = new LinkedHashMap<>();
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
    private ObjectAnimator mascotFloatAnimator;
    private MarkdownRenderer markdownRenderer;
    private BridgeClient bridge;
    private WorkspacePanels panels;
    private EditText promptInput;
    private FrameLayout btnSend;
    private ImageView btnAttach;
    private ImageView btnEnginePill;
    private ImageView btnVoice;
    private TextView repoTagLabel;
    private TextView modelTagLabel;
    private HorizontalScrollView attachmentScrollContainer;
    private LinearLayout attachmentChipsList;

    // Settings View Components
    private TextView settingsUserEmailText;
    private TextView settingsConnectorStatusText;
    private TextView settingsCapabilitiesSubtitle;
    private TextView settingsSandboxSubtitle;
    private TextView settingsGitPathSubtitle;

    // Active Session State
    private String activeConversationId = null;
    private String activeJobId = null;
    /**
     * Bumped whenever the open conversation changes. An in-flight request
     * carries the epoch it started under; if it comes back after the user moved
     * on, its data belongs to a session that is no longer on screen.
     */
    private int sessionEpoch = 0;
    private String activeSessionTitle = "New session";
    /** Engine the open session belongs to; sessions cannot cross engines. */
    private String activeSessionEngine = "";
    private String currentEngine = "antigravity";
    private String currentModel = "";
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
                // SSE drives the fast path; this is only a fallback for when
                // the stream is down, so it no longer polls every second.
                int delay = isLiveTaskRunning ? 5000 : 12000;
                mainHandler.postDelayed(this, delay);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // The window background comes from the XML theme, which is chosen before
        // the window is drawn — otherwise an engine switch flashes the previous
        // engine's ground colour during the rebuild.
        prefs = getSharedPreferences("connection", MODE_PRIVATE);
        setTheme("codex".equalsIgnoreCase(prefs.getString("engine", "antigravity"))
                ? R.style.AppTheme_Codex : R.style.AppTheme);

        super.onCreate(savedInstanceState);
        bridge = new BridgeClient(prefs);
        panels = new WorkspacePanels(this, workspaceHost, bridge, prefs, executor, mainHandler);
        currentEngine = prefs.getString("engine", "antigravity");

        // The whole UI is built in code, so the engine's palette has to be in
        // place before the first view exists.
        Theme.applyEngine(currentEngine);
        getWindow().setStatusBarColor(Theme.BG);
        getWindow().setNavigationBarColor(Theme.BG);
        currentModel = prefs.getString(modelPrefKey(currentEngine), defaultModelForEngine(currentEngine));
        currentServerHostname = prefs.getString("device_name", "Server Remote");
        buildClaudeUiWithSidebar();
        consumePendingEngineNotice();
        requestNotificationPermission();
        createNotificationChannel();
        LiveEventBus.register(liveEventListener);
        startLiveEvents();
    }

    @Override
    protected void onDestroy() {
        LiveEventBus.unregister(liveEventListener);
        super.onDestroy();
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

    private String modelPrefKey(String engine) {
        return "model_" + ("codex".equalsIgnoreCase(engine) ? "codex" : "antigravity");
    }

    private String defaultModelForEngine(String engine) {
        return "codex".equalsIgnoreCase(engine) ? "gpt-5.6-luna" : "auto";
    }

    private String displayModel(String model) {
        return model == null || model.trim().isEmpty() || "auto".equalsIgnoreCase(model) ? "Auto" : model;
    }

    private String formatCount(long value) {
        return String.format(Locale.getDefault(), "%,d", value);
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
        rootFrame.setBackgroundColor(Theme.BG);

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
        sidebarPanel.setBackgroundColor(Theme.SURFACE);
        sidebarPanel.setVisibility(View.GONE);
        buildSidebarContent(sidebarPanel);

        FrameLayout.LayoutParams lpSide = new FrameLayout.LayoutParams(dp(320), -1);
        lpSide.gravity = Gravity.START;
        rootFrame.addView(sidebarPanel, lpSide);

        setContentView(rootFrame);

        showScreen(0);
    }

    // ============================================================
    // SMOOTH ANIMATED SIDEBAR NAVIGATION (No Pro Badge)
    // ============================================================
    private void buildSidebarContent(LinearLayout sidebar) {
        sidebar.setPadding(0, dp(20), 0, dp(8));
        sidebarNavRows.clear();

        int sidePad = dp(18);

        // 1. Brand wordmark + quick settings
        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(sidePad + dp(6), dp(6), sidePad, dp(18));

        TextView wordmark = cText(Theme.wordmark(), 30, Theme.TEXT_MAIN, false, true);
        brandRow.addView(wordmark, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView brandGear = cIconButton(R.drawable.ic_settings, 20, 38, Theme.TEXT_MUTED);
        brandGear.setOnClickListener(v -> { closeSidebar(); showScreen(2); });
        brandRow.addView(brandGear);
        sidebar.addView(brandRow);

        // 2. Scrollable navigation body
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(10), 0, dp(10), dp(10));

        addSidebarMenuItem(body, R.drawable.ic_add, "Chat baru", null, -1, true, () -> {
            closeSidebar();
            startNewSession();
        });

        addSidebarMenuItem(body, R.drawable.ic_chat, "Obrolan", null, 1, false, () -> {
            closeSidebar();
            openLatestConversation();
        });

        addSidebarMenuItem(body, R.drawable.ic_code, "Kode", null, 0, false, () -> {
            closeSidebar();
            showScreen(0);
        });

        addSidebarMenuItem(body, R.drawable.ic_swap, "Engine",
                engineShortLabel(currentEngine), -1, false, () -> {
                    closeSidebar();
                    showEngineSwitcher();
                });

        addSidebarMenuItem(body, R.drawable.ic_qr_code, "Scan QR Pairing", null, -1, false, () -> {
            closeSidebar();
            startQrScanner();
        });

        addSidebarMenuItem(body, R.drawable.ic_content_paste, "Paste Pairing", null, -1, false, () -> {
            closeSidebar();
            pasteFromClipboard();
        });

        addSidebarMenuItem(body, R.drawable.ic_search, "Cari Sesi", null, -1, false, () -> {
            closeSidebar();
            showSearchPanel();
        });

        addSidebarMenuItem(body, R.drawable.ic_folder, "File Workspace", null, -1, false, () -> {
            closeSidebar();
            showFileBrowser(".");
        });

        addSidebarMenuItem(body, R.drawable.ic_source_branch, "Git", null, -1, false, () -> {
            closeSidebar();
            showGitPanel();
        });

        addSidebarMenuItem(body, R.drawable.ic_folder, "Proyek", null, -1, false, () -> {
            closeSidebar();
            showProjectPicker();
        });

        addSidebarMenuItem(body, R.drawable.ic_settings, "Pengaturan", null, 2, false, () -> {
            closeSidebar();
            showScreen(2);
        });

        // 3. Gateway section (replaces the reference's "starred" block)
        addSidebarDivider(body);
        addSidebarSectionHeader(body, "Gateway");

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(dp(14), dp(8), dp(12), dp(8));

        sidebarStatusDot = new View(this);
        sidebarStatusDot.setBackground(cBox(Theme.GREEN, 0, 0, 4));
        LinearLayout.LayoutParams lpDot = new LinearLayout.LayoutParams(dp(8), dp(8));
        lpDot.setMargins(dp(6), 0, dp(14), 0);
        statusRow.addView(sidebarStatusDot, lpDot);

        LinearLayout statusCol = new LinearLayout(this);
        statusCol.setOrientation(LinearLayout.VERTICAL);
        sidebarStatusText = cText("Gateway Online", 14, Theme.TEXT_MAIN, false, false);
        statusCol.addView(sidebarStatusText);
        sidebarDeviceHost = cText(currentServerHostname, 11.5f, Theme.TEXT_LIGHT, false, false);
        sidebarDeviceHost.setSingleLine(true);
        sidebarDeviceHost.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        statusCol.addView(sidebarDeviceHost);
        statusRow.addView(statusCol, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(statusRow, new LinearLayout.LayoutParams(-1, -2));

        addSidebarMenuItem(body, R.drawable.ic_laptop, "Ganti Server", null, -1, false, () -> {
            closeSidebar();
            showServerSwitcher();
        });

        addSidebarMenuItem(body, R.drawable.ic_security, "Mode Eksekusi", null, -1, false, () -> {
            closeSidebar();
            showSandboxPicker();
        });

        addSidebarMenuItem(body, R.drawable.ic_build, "Pemeliharaan", null, -1, false, () -> {
            closeSidebar();
            showMaintenanceSheet();
        });

        addSidebarMenuItem(body, R.drawable.ic_refresh, "Test Ping & Health", null, -1, false, this::checkHealth);
        addSidebarMenuItem(body, R.drawable.ic_stop, "Hentikan Proses CLI", null, -1, false, () -> {
            closeSidebar();
            stopRunningCliProcess();
        });

        // 4. Recent sessions, filled in by fetchHubSessions()
        addSidebarDivider(body);
        addSidebarSectionHeader(body, "Terbaru · " + engineShortLabel(currentEngine));
        sidebarRecentContainer = new LinearLayout(this);
        sidebarRecentContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(sidebarRecentContainer, new LinearLayout.LayoutParams(-1, -2));
        renderSidebarRecent(null);

        scroll.addView(body);
        sidebar.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // 5. Account footer
        View footRule = new View(this);
        footRule.setBackgroundColor(Theme.BORDER);
        LinearLayout.LayoutParams lpFr = new LinearLayout.LayoutParams(-1, dp(1));
        lpFr.setMargins(sidePad, 0, sidePad, dp(8));
        sidebar.addView(footRule, lpFr);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(sidePad, dp(8), sidePad - dp(6), dp(8));

        String userEmail = prefs.getString("user_email", "developer@antigravity.ai");
        footer.addView(buildAvatarBadge(userEmail), new LinearLayout.LayoutParams(dp(40), dp(40)));

        sidebarUserEmail = cText(shortUserName(userEmail), 15, Theme.TEXT_MAIN, false, false);
        sidebarUserEmail.setSingleLine(true);
        sidebarUserEmail.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lpEmail = new LinearLayout.LayoutParams(0, -2, 1);
        lpEmail.setMargins(dp(12), 0, dp(8), 0);
        footer.addView(sidebarUserEmail, lpEmail);

        ImageView footGear = cIconButton(R.drawable.ic_settings, 20, 40, Theme.TEXT_MUTED);
        footGear.setOnClickListener(v -> { closeSidebar(); showScreen(2); });
        footer.addView(footGear);

        footer.setOnClickListener(v -> { closeSidebar(); showScreen(2); });
        sidebar.addView(footer);

        updateSidebarActiveState();
    }

    // Circular initials badge, e.g. "equinox@..." -> "EQ".
    private FrameLayout buildAvatarBadge(String email) {
        FrameLayout frame = new FrameLayout(this);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Theme.ACCENT);
        frame.setBackground(circle);

        String name = shortUserName(email);
        String initials = name.length() >= 2 ? name.substring(0, 2) : (name.isEmpty() ? "?" : name);
        TextView tv = cText(initials.toUpperCase(Locale.ROOT), 13.5f, Theme.ON_ACCENT, true, false);
        tv.setGravity(Gravity.CENTER);
        frame.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        return frame;
    }

    private String shortUserName(String email) {
        if (email == null || email.trim().isEmpty()) return "user";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private void addSidebarDivider(LinearLayout container) {
        View v = new View(this);
        v.setBackgroundColor(Theme.BORDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(dp(8), dp(14), dp(8), dp(12));
        container.addView(v, lp);
    }

    private void addSidebarSectionHeader(LinearLayout container, String title) {
        TextView tv = cText(title, 13f, Theme.TEXT_LIGHT, false, false);
        tv.setPadding(dp(14), 0, 0, dp(6));
        container.addView(tv, new LinearLayout.LayoutParams(-1, -2));
    }

    // screenIndex >= 0 marks this row as the active tab for that screen; accent = terracotta styling.
    private void addSidebarMenuItem(LinearLayout container, int iconRes, String title, String badge,
                                    int screenIndex, boolean accent, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(cBox(Color.TRANSPARENT, 0, 0, 26));

        int tint = accent ? Theme.ACCENT : Theme.TEXT_MAIN;
        ImageView ic = cIcon(iconRes, 22, tint);
        row.addView(ic);

        TextView label = cText(title, 15.5f, tint, false, false);
        label.setPadding(dp(16), 0, 0, 0);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        if (badge != null && !badge.isEmpty()) {
            TextView chip = cText(badge, 11.5f, Theme.ACCENT, true, false);
            chip.setBackground(cBox(Theme.SURFACE_MUTED, 0, 0, 12));
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            row.addView(chip, new LinearLayout.LayoutParams(-2, -2));
        }

        row.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(2));
        container.addView(row, lp);

        if (screenIndex >= 0) sidebarNavRows.put(screenIndex, row);
    }

    private void updateSidebarActiveState() {
        for (Map.Entry<Integer, LinearLayout> e : sidebarNavRows.entrySet()) {
            boolean active = e.getKey() == currentScreen;
            e.getValue().setBackground(cBox(active ? Theme.BG : Color.TRANSPARENT, 0, 0, 26));
        }
    }

    private void renderSidebarRecent(JSONArray sessions) {
        if (sidebarRecentContainer == null) return;
        sidebarRecentContainer.removeAllViews();

        if (sessions == null || sessions.length() == 0) {
            TextView empty = cText("Belum ada sesi " + engineShortLabel(currentEngine), 14, Theme.TEXT_LIGHT, false, false);
            empty.setPadding(dp(14), dp(8), dp(14), dp(8));
            sidebarRecentContainer.addView(empty);
            return;
        }

        int max = Math.min(6, sessions.length());
        for (int i = 0; i < max; i++) {
            JSONObject s = sessions.optJSONObject(i);
            if (s == null) continue;
            final String convId = s.optString("conversationId", "");
            final String title = s.optString("title", "Sesi");

            TextView tv = cText(title, 15, Theme.TEXT_MAIN, false, false);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            tv.setPadding(dp(14), dp(11), dp(14), dp(11));
            tv.setBackground(cBox(Color.TRANSPARENT, 0, 0, 26));
            tv.setOnClickListener(v -> {
                closeSidebar();
                navigatedFromHub = true;
                openSpecificSession(convId, title);
            });
            tv.setOnLongClickListener(v -> {
                closeSidebar();
                showRenameSessionDialog(convId, title);
                return true;
            });
            sidebarRecentContainer.addView(tv, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void openSidebar() {
        if (isSidebarOpen) return;
        isSidebarOpen = true;

        final int panelWidth = sidebarPanel.getWidth() > 0 ? sidebarPanel.getWidth() : dp(320);

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

        final int panelWidth = sidebarPanel.getWidth() > 0 ? sidebarPanel.getWidth() : dp(320);

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
        updateSidebarActiveState();
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
                fetchActiveSessionTurns(false);
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
            chatNavIcon.setColorFilter(Theme.TEXT_MAIN);
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

        ImageView menuIcon = cIconButton(R.drawable.ic_menu, 24, 40, Theme.TEXT_MAIN);
        menuIcon.setOnClickListener(v -> openSidebar());
        topBar.addView(menuIcon);

        TextView headerTitle = cText("Kode", 22, Theme.TEXT_MAIN, true, false);
        headerTitle.setPadding(dp(12), 0, 0, 0);
        topBar.addView(headerTitle, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView tuneBtn = cIconButton(R.drawable.ic_tune, 22, 40, Theme.TEXT_MUTED);
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
        TextView devTitle = cText("Perangkat", 13.5f, Theme.TEXT_MUTED, false, false);
        LinearLayout.LayoutParams lpDevT = new LinearLayout.LayoutParams(-1, -2);
        lpDevT.setMargins(0, dp(6), 0, dp(10));
        scrollBody.addView(devTitle, lpDevT);

        LinearLayout deviceCard = new LinearLayout(this);
        deviceCard.setOrientation(LinearLayout.VERTICAL);
        deviceCard.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 16));
        deviceCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        ImageView laptopIcon = cIcon(R.drawable.ic_laptop, 22, Theme.TEXT_MAIN);
        deviceCard.addView(laptopIcon);

        hubDeviceHostText = cText(currentServerHostname, 15, Theme.TEXT_MAIN, true, false);
        hubDeviceHostText.setSingleLine(true);
        LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams(-2, -2);
        lpH.setMargins(0, dp(12), 0, dp(0));
        deviceCard.addView(hubDeviceHostText, lpH);

        hubDeviceStatusText = cText("Terhubung", 12.5f, Theme.GREEN, false, false);
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

        // Search Sessions Bar
        LinearLayout searchCard = new LinearLayout(this);
        searchCard.setOrientation(LinearLayout.HORIZONTAL);
        searchCard.setGravity(Gravity.CENTER_VERTICAL);
        searchCard.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 14));
        searchCard.setPadding(dp(12), dp(8), dp(12), dp(8));

        ImageView searchIc = cIcon(R.drawable.ic_search, 18, Theme.TEXT_MUTED);
        searchCard.addView(searchIc);

        EditText searchEt = new EditText(this);
        searchEt.setHint("Cari riwayat sesi...");
        searchEt.setHintTextColor(Theme.TEXT_LIGHT);
        searchEt.setTextColor(Theme.TEXT_MAIN);
        searchEt.setTextSize(13.5f);
        searchEt.setBackgroundColor(Color.TRANSPARENT);
        searchEt.setPadding(dp(8), 0, dp(8), 0);
        searchEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                hubSearchQuery = s.toString().trim().toLowerCase();
                if (cachedHubSessionsRaw != null) {
                    renderHubSessionGroups(cachedHubSessionsRaw);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchCard.addView(searchEt, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout.LayoutParams lpSearch = new LinearLayout.LayoutParams(-1, -2);
        lpSearch.setMargins(0, dp(6), 0, dp(12));
        scrollBody.addView(searchCard, lpSearch);

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
        fabNew.setBackground(cBox(Theme.ACCENT, 0, 0, 26));
        fabNew.setPadding(dp(18), dp(12), dp(20), dp(12));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            fabNew.setElevation(dp(8));
        }

        ImageView plusIc = cIcon(R.drawable.ic_add, 18, Theme.ON_ACCENT);
        fabNew.addView(plusIc);

        TextView fabLabel = cText(" Sesi baru", 14.5f, Theme.ON_ACCENT, true, false);
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
                // Sessions belong to one CLI or the other, so the list follows
                // the active engine instead of mixing both.
                String sessionsUrl = endpoint.replace("/api/chat", "/api/sessions")
                        + "?engine=" + BridgeClient.encode(currentEngine);
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
                        if (sidebarDeviceHost != null) sidebarDeviceHost.setText(currentServerHostname);
                        JSONArray mine = filterSessionsForEngine(sessions);
                        renderSidebarRecent(mine);
                        renderTimeGroupedSessions(mine);
                    });
                } else {
                    // Anything other than 200 used to fall through silently:
                    // the spinner never stopped and the list stayed empty.
                    mainHandler.post(() -> showHubLoadFailure(code));
                }
            } catch (Exception e) {
                final String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                mainHandler.post(() -> {
                    if (hubLoadingProgress != null) hubLoadingProgress.setVisibility(View.GONE);
                    if (hubDeviceStatusText != null) hubDeviceStatusText.setText("Terputus");
                    showHubMessage("Tidak bisa menghubungi server", reason, false);
                });
            }
        });
    }

    // A 401 means the phone still holds a token the server has replaced
    // (for example after `codex-remote rotate`). Say so instead of showing
    // an empty screen, and offer the one action that fixes it.
    private void showHubLoadFailure(int code) {
        if (hubLoadingProgress != null) hubLoadingProgress.setVisibility(View.GONE);
        if (code == 401 || code == 403) {
            if (hubDeviceStatusText != null) hubDeviceStatusText.setText("Token ditolak");
            showHubMessage("Pairing kedaluwarsa",
                    "Server menolak token ini (HTTP " + code + "). Scan ulang QR pairing dari terminal server.", true);
        } else {
            if (hubDeviceStatusText != null) hubDeviceStatusText.setText("HTTP " + code);
            showHubMessage("Gagal memuat sesi", "Server membalas HTTP " + code + ".", false);
        }
    }

    private void showHubMessage(String title, String detail, boolean offerPairing) {
        if (hubSessionGroupsContainer == null) return;
        hubSessionGroupsContainer.removeAllViews();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 18));
        card.setPadding(dp(18), dp(18), dp(18), dp(18));

        card.addView(cIcon(offerPairing ? R.drawable.ic_security : R.drawable.ic_link_off, 26,
                offerPairing ? Theme.AMBER : Theme.TEXT_MUTED));

        TextView t = cText(title, 15.5f, Theme.TEXT_MAIN, true, false);
        t.setPadding(0, dp(12), 0, dp(4));
        card.addView(t);
        card.addView(cText(detail, 13f, Theme.TEXT_MUTED, false, false));

        if (offerPairing) {
            TextView action = cText("Scan QR Pairing", 14f, Theme.ON_ACCENT, true, false);
            action.setGravity(Gravity.CENTER);
            action.setPadding(dp(16), dp(12), dp(16), dp(12));
            action.setBackground(cBox(Theme.ACCENT, 0, 0, 12));
            action.setOnClickListener(v -> startQrScanner());
            LinearLayout.LayoutParams lpA = new LinearLayout.LayoutParams(-1, -2);
            lpA.setMargins(0, dp(14), 0, 0);
            card.addView(action, lpA);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(16), 0, 0);
        hubSessionGroupsContainer.addView(card, lp);
    }

    /** Keeps only the active engine's sessions, whatever the server returned. */
    private JSONArray filterSessionsForEngine(JSONArray sessions) {
        if (sessions == null) return null;
        ArrayList<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < filteredList.size(); i++) {
            JSONObject s = filteredList.get(i);
            if (s == null) continue;
            String engine = s.optString("engine", "antigravity");
            boolean isCodex = "codex".equalsIgnoreCase(engine);
            if (isCodex == isCodexEngine()) {
                list.add(s);
            }
        }

        // Auto sort: Most recently opened / chatted sessions move to the very top
        Collections.sort(list, (a, b) -> {
            String idA = a.optString("conversationId", "");
            String idB = b.optString("conversationId", "");
            long tsA = Math.max(a.optLong("timestamp", 0), prefs.getLong("session_accessed_" + idA, 0));
            long tsB = Math.max(b.optLong("timestamp", 0), prefs.getLong("session_accessed_" + idB, 0));
            return Long.compare(tsB, tsA);
        });

        JSONArray filtered = new JSONArray();
        for (JSONObject s : list) {
            filtered.put(s);
        }
        return filtered;
    }

    private void renderTimeGroupedSessions(JSONArray sessions) {
        if (hubLoadingProgress != null) hubLoadingProgress.setVisibility(View.GONE);
        if (hubSessionGroupsContainer != null) hubSessionGroupsContainer.removeAllViews();
        cachedHubSessionsRaw = sessions;

        if (sessions == null || sessions.length() == 0) {
            addTimeSectionHeader("Hari ini");
            addSessionCard("Belum ada sesi " + engineLabel(currentEngine),
                    "Terhubung • " + currentServerHostname, "Baru", null, true);
            return;
        }

        Set<String> pinnedIds = getPinnedSessionIds();
        ArrayList<JSONObject> pinnedList = new ArrayList<>();
        ArrayList<JSONObject> filteredList = new ArrayList<>();

        for (int i = 0; i < sessions.length(); i++) {
            JSONObject s = sessions.optJSONObject(i);
            if (s == null) continue;
            String title = s.optString("title", "").toLowerCase();
            String convId = s.optString("conversationId", "").toLowerCase();
            if (!hubSearchQuery.isEmpty() && !title.contains(hubSearchQuery) && !convId.contains(hubSearchQuery)) {
                continue;
            }
            if (pinnedIds.contains(s.optString("conversationId", ""))) {
                pinnedList.add(s);
            } else {
                filteredList.add(s);
            }
        }

        if (!pinnedList.isEmpty()) {
            addTimeSectionHeader("📌 Disematkan");
            for (JSONObject s : pinnedList) {
                renderSingleSessionItem(s, false);
            }
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

        String engine = s.optString("engine", "antigravity");
        String subText = ("codex".equalsIgnoreCase(engine) ? "Codex CLI" : "Antigravity CLI") + " • " + currentServerHostname;
        addSessionCard(title, subText, dateStr, convId, true);
    }

    private void addTimeSectionHeader(String title) {
        TextView v = cText(title, 13.5f, Theme.TEXT_MUTED, false, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(18), 0, dp(10));
        hubSessionGroupsContainer.addView(v, lp);
    }

    private void addSessionCard(final String title, String subText, String dateStr, final String convId, boolean isConnected) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 18));
        card.setPadding(dp(14), dp(12), dp(16), dp(12));

        // Left Code Icon Badge with optional blue indicator dot
        FrameLayout badgeFrame = new FrameLayout(this);
        LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(dp(42), dp(42));
        badgeFrame.setLayoutParams(lpBadge);

        View badgeBg = new View(this);
        badgeBg.setBackground(cBox(Theme.SURFACE_MUTED, 0, 0, 12));
        badgeFrame.addView(badgeBg, new FrameLayout.LayoutParams(-1, -1));

        ImageView codeIcon = cIcon(R.drawable.ic_code, 20, Theme.TEXT_MUTED);
        FrameLayout.LayoutParams lpCode = new FrameLayout.LayoutParams(dp(20), dp(20));
        lpCode.gravity = Gravity.CENTER;
        badgeFrame.addView(codeIcon, lpCode);

        if (isConnected) {
            View blueDot = new View(this);
            blueDot.setBackground(cBox(Theme.BLUE, 0, 0, 4));
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

        TextView titleView = cText(title, 14.5f, Theme.TEXT_MAIN, true, false);
        titleView.setSingleLine(true);
        textCol.addView(titleView);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(3), 0, 0);

        ImageView statIcon = cIcon(isConnected ? R.drawable.ic_laptop : R.drawable.ic_link_off, 13, isConnected ? Theme.GREEN : Theme.TEXT_MUTED);
        statusRow.addView(statIcon);

        TextView subView = cText(" " + subText, 12.5f, isConnected ? Theme.GREEN : Theme.TEXT_MUTED, false, false);
        subView.setSingleLine(true);
        statusRow.addView(subView);

        textCol.addView(statusRow);
        card.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

        // Right date text
        TextView dateView = cText(dateStr, 12f, Theme.TEXT_MUTED, false, false);
        card.addView(dateView);

        ImageView optBtn = cIconButton(R.drawable.ic_more_vert, 18, 32, Theme.TEXT_MUTED);
        optBtn.setOnClickListener(v -> showSessionOptionsBottomSheet(convId, title));
        card.addView(optBtn);

        card.setOnClickListener(v -> {
            navigatedFromHub = true;
            openSpecificSession(convId, title);
        });
        card.setOnLongClickListener(v -> {
            showSessionOptionsBottomSheet(convId, title);
            return true;
        });

        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(-1, -2);
        lpCard.setMargins(0, 0, 0, dp(10));
        hubSessionGroupsContainer.addView(card, lpCard);
    }

    private void hideSessionLoading() {
        if (chatSessionLoadingView != null && chatSessionLoadingView.getVisibility() == View.VISIBLE) {
            chatSessionLoadingView.animate().alpha(0f).setDuration(160)
                    .withEndAction(() -> {
                        if (chatSessionLoadingView != null) {
                            chatSessionLoadingView.setVisibility(View.GONE);
                            chatSessionLoadingView.setAlpha(1f);
                        }
                    }).start();
        }
    }

    private void openSpecificSession(String convId, String title) {
        sessionEpoch++;
        activeJobId = null;
        activeConversationId = convId;
        if (convId != null && !convId.isEmpty()) {
            prefs.edit().putLong("session_accessed_" + convId, System.currentTimeMillis()).apply();
        }
        activeSessionEngine = currentEngine;
        activeSessionTitle = title != null && !title.isEmpty() ? title : "Session";

        // Remembered so "Obrolan" can resume across app restarts.
        prefs.edit()
                .putString("last_conversation_id", convId == null ? "" : convId)
                .putString("last_conversation_title", activeSessionTitle)
                .putString("last_conversation_engine", currentEngine)
                .apply();
        lastLoadedSessionId = null;
        lastLoadedTurnCount = -1;
        lastRenderedWasRunning = false;

        if (chatMessagesList != null) chatMessagesList.removeAllViews();
        showEmptyMascotState(false);
        if (chatSessionLoadingView != null) {
            chatSessionLoadingView.setVisibility(View.VISIBLE);
            chatSessionLoadingView.setAlpha(1f);
        }
        mainHandler.postDelayed(this::hideSessionLoading, 4000);
        showScreen(1);
    }

    /**
     * "Obrolan" continues where the user left off: the session already open, or
     * the most recent one on the server. It used to call showScreen(1) with no
     * active conversation, which lands on the same empty state as "Chat baru".
     */
    /**
     * "Obrolan" opens the newest chat of the engine that is currently active.
     * A session already on screen is kept — but only if it belongs to this
     * engine; the other CLI's sessions cannot be resumed from here.
     */
    private void openLatestConversation() {
        if (activeConversationId != null && !activeConversationId.isEmpty()
                && currentEngine.equalsIgnoreCase(activeSessionEngine)) {
            showScreen(1);
            return;
        }

        Toast.makeText(this, "Membuka sesi " + engineLabel(currentEngine) + " terbaru...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/sessions?engine=" + BridgeClient.encode(currentEngine));
                JSONArray sessions = filterSessionsForEngine(json.optJSONArray("sessions"));

                if (sessions == null || sessions.length() == 0) {
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this,
                                "Belum ada sesi " + engineLabel(currentEngine) + " — memulai yang baru",
                                Toast.LENGTH_SHORT).show();
                        startNewSession();
                    });
                    return;
                }

                // The server returns newest first, and the list is already
                // scoped to this engine.
                JSONObject newest = sessions.optJSONObject(0);
                final String convId = newest == null ? "" : newest.optString("conversationId", "");
                final String title = newest == null ? "Sesi" : newest.optString("title", "Sesi");
                mainHandler.post(() -> {
                    if (convId.isEmpty()) {
                        startNewSession();
                    } else {
                        navigatedFromHub = false;
                        openSpecificSession(convId, title);
                    }
                });
            } catch (Exception ex) {
                // Offline: the remembered session is the best we can do, and
                // only when we know it came from this engine.
                mainHandler.post(this::openRememberedConversationOrNew);
            }
        });
    }

    private void openRememberedConversationOrNew() {
        String cachedId = prefs.getString("last_conversation_id", "");
        String cachedTitle = prefs.getString("last_conversation_title", "Sesi");
        // No recorded engine means it predates the tag — treat it as unknown
        // rather than assuming it belongs to whichever engine is active now.
        String cachedEngine = prefs.getString("last_conversation_engine", "");

        if (!cachedId.isEmpty() && currentEngine.equalsIgnoreCase(cachedEngine)) {
            Toast.makeText(this, "Offline — membuka sesi tersimpan", Toast.LENGTH_SHORT).show();
            navigatedFromHub = false;
            openSpecificSession(cachedId, cachedTitle);
            return;
        }

        Toast.makeText(this, "Gagal memuat sesi terakhir", Toast.LENGTH_SHORT).show();
        startNewSession();
    }

    private void startNewSession() {
        sessionEpoch++;
        activeJobId = null;
        activeConversationId = null;
        activeSessionEngine = currentEngine;
        activeSessionTitle = "New session";
        lastLoadedSessionId = null;
        lastLoadedTurnCount = -1;
        lastRenderedWasRunning = false;
        isLiveTaskRunning = false;
        navigatedFromHub = false;

        hideSessionLoading();
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

        ImageView menuIcon = cIconButton(R.drawable.ic_menu, 24, 40, Theme.TEXT_MAIN);
        menuIcon.setOnClickListener(v -> openSidebar());
        topBar.addView(menuIcon);

        TextView headerTitle = cText("Pengaturan", 20, Theme.TEXT_MAIN, true, true);
        headerTitle.setGravity(Gravity.CENTER);
        topBar.addView(headerTitle, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView infoBtn = cIconButton(R.drawable.ic_info, 22, 40, Theme.TEXT_MUTED);
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
        topProfileCard.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 18));
        topProfileCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        String email = prefs.getString("user_email", "developer@antigravity.ai");
        settingsUserEmailText = cText(email, 14.5f, Theme.TEXT_MAIN, true, false);
        settingsUserEmailText.setSingleLine(true);
        topProfileCard.addView(settingsUserEmailText, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView editIcon = cIcon(R.drawable.ic_person, 18, Theme.TEXT_MUTED);
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

        // Group 3: Engine, Konektor, keamanan & proyek
        LinearLayout g3 = createSettingsGroupContainer();
        settingsCapabilitiesSubtitle = addSettingsRowItemWithSubtitle(g3, R.drawable.ic_swap, "Engine",
                engineLabel(currentEngine), () -> showEngineSwitcher(), true);
        settingsConnectorStatusText = addSettingsRowItemWithSubtitle(g3, R.drawable.ic_link, "Konektor", "1 terhubung", () -> showConnectionBottomSheet(), true);
        settingsSandboxSubtitle = addSettingsRowItemWithSubtitle(g3, R.drawable.ic_security, "Mode Eksekusi",
                sandboxLabel(prefs.getString("sandbox_mode", "full")), () -> showSandboxPicker(), true);
        settingsGitPathSubtitle = addSettingsRowItemWithSubtitle(g3, R.drawable.ic_source_branch, "Git repo path",
                gitPathLabel(), () -> showGitPathBottomSheet(), true);
        addSettingsRowItem(g3, R.drawable.ic_laptop, "Server Tersimpan", null, () -> showServerSwitcher(), true);
        addSettingsRowItem(g3, R.drawable.ic_folder, "Proyek", null, () -> showProjectPicker(), true);
        addSettingsRowItem(g3, R.drawable.ic_build, "Pemeliharaan", null, () -> showMaintenanceSheet(), true);
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

        ImageView vibIc = cIcon(R.drawable.ic_vibration, 22, Theme.TEXT_MAIN);
        hapticRow.addView(vibIc);

        TextView vibLabel = cText("  Umpan balik haptik", 14.5f, Theme.TEXT_MAIN, false, false);
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
        box.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 18));
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

        ImageView ic = cIcon(iconRes, 22, Theme.TEXT_MAIN);
        row.addView(ic);

        TextView label = cText("  " + title, 14.5f, Theme.TEXT_MAIN, false, false);
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

        ImageView ic = cIcon(iconRes, 22, Theme.TEXT_MAIN);
        row.addView(ic);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(14), 0, 0, 0);

        TextView label = cText(title, 14.5f, Theme.TEXT_MAIN, false, false);
        textCol.addView(label);

        TextView sub = cText(subtitle != null ? subtitle : "", 12.5f, Theme.TEXT_MUTED, false, false);
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
        div.setBackgroundColor(Theme.BORDER);
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
            settingsCapabilitiesSubtitle.setText(engineLabel(currentEngine) + " · " + displayModel(currentModel));
        }
        if (settingsSandboxSubtitle != null) {
            settingsSandboxSubtitle.setText(sandboxLabel(prefs.getString("sandbox_mode", "full")));
        }
        if (settingsGitPathSubtitle != null) {
            settingsGitPathSubtitle.setText(gitPathLabel());
        }
    }

    private String sandboxLabel(String mode) {
        if ("readonly".equals(mode)) return "Hanya baca";
        if ("workspace".equals(mode)) return "Tulis di workspace";
        return "Akses penuh";
    }

    private String gitPathLabel() {
        String path = prefs.getString("git_repo_path", "");
        return path.isEmpty() ? "Workdir server" : path;
    }

    private void showGitPathBottomSheet() {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Git repo path", true);
        root.addView(cText("Relatif terhadap workdir server. Kosongkan untuk memakai workdir itu sendiri.",
                12.5f, Theme.TEXT_MUTED, false, false));

        final EditText input = new EditText(this);
        input.setText(prefs.getString("git_repo_path", ""));
        input.setHint("mis. codexcli-remote-app");
        input.setTextSize(14.5f);
        input.setSingleLine(true);
        input.setTextColor(Theme.TEXT_MAIN);
        input.setHintTextColor(Theme.TEXT_LIGHT);
        input.setBackground(cBox(Theme.BG, Theme.BORDER, 1, 14));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lpIn = new LinearLayout.LayoutParams(-1, -2);
        lpIn.setMargins(0, dp(14), 0, dp(14));
        root.addView(input, lpIn);

        TextView save = cText("Simpan", 14.5f, Theme.ON_ACCENT, true, false);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(16), dp(14), dp(16), dp(14));
        save.setBackground(cBox(Theme.ACCENT, 0, 0, 14));
        save.setOnClickListener(v -> {
            prefs.edit().putString("git_repo_path", input.getText().toString().trim()).apply();
            refreshSettingsValues();
            dialog.dismiss();
        });
        root.addView(save, new LinearLayout.LayoutParams(-1, -2));

        dialog.setContentView(root);
        dialog.show();
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
        root.setBackground(cBox(Theme.SURFACE, 0, 0, 24));
        root.setPadding(dp(20), dp(10), dp(20), dp(20));

        // Drag Handle Pill
        LinearLayout dragArea = new LinearLayout(this);
        dragArea.setOrientation(LinearLayout.VERTICAL);
        dragArea.setGravity(Gravity.CENTER_HORIZONTAL);
        dragArea.setPadding(0, dp(4), 0, dp(12));

        View dragHandle = new View(this);
        dragHandle.setBackground(cBox(Theme.BORDER_DARK, 0, 0, 3));
        dragArea.addView(dragHandle, new LinearLayout.LayoutParams(dp(44), dp(5)));
        root.addView(dragArea);

        // Header Title Row
        if (title != null) {
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);

            TextView t = cText(title, 18f, Theme.TEXT_MAIN, true, true);
            head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));

            if (showClose) {
                ImageView close = cIconButton(R.drawable.ic_close, 20, 36, Theme.TEXT_MUTED);
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

        TextView sub = cText("Alamat email atau ID pengembang", 12.5f, Theme.TEXT_MUTED, false, false);
        root.addView(sub);

        final EditText input = new EditText(this);
        input.setText(prefs.getString("user_email", "developer@antigravity.ai"));
        input.setTextColor(Theme.TEXT_MAIN);
        input.setHintTextColor(Theme.TEXT_LIGHT);
        input.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lpIn = new LinearLayout.LayoutParams(-1, dp(48));
        lpIn.setMargins(0, dp(8), 0, dp(16));
        root.addView(input, lpIn);

        LinearLayout btnSave = new LinearLayout(this);
        btnSave.setOrientation(LinearLayout.HORIZONTAL);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setBackground(cBox(Theme.ACCENT, 0, 0, 14));
        btnSave.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnSave.addView(cText("Simpan Perubahan", 14f, Theme.ON_ACCENT, true, false));
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

        TextView sub = cText("Beri nama kustom untuk server remote yang terhubung", 12.5f, Theme.TEXT_MUTED, false, false);
        root.addView(sub);

        final EditText input = new EditText(this);
        input.setText(currentServerHostname);
        input.setTextColor(Theme.TEXT_MAIN);
        input.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lpIn = new LinearLayout.LayoutParams(-1, dp(48));
        lpIn.setMargins(0, dp(8), 0, dp(16));
        root.addView(input, lpIn);

        LinearLayout btnSave = new LinearLayout(this);
        btnSave.setOrientation(LinearLayout.HORIZONTAL);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setBackground(cBox(Theme.ACCENT, 0, 0, 14));
        btnSave.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnSave.addView(cText("Simpan Nama Perangkat", 14f, Theme.ON_ACCENT, true, false));
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
        root.setBackground(cBottomSheetBox(Theme.SURFACE));
        root.setPadding(dp(20), dp(10), dp(20), dp(24));
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Drag Handle Pill
        LinearLayout dragArea = new LinearLayout(this);
        dragArea.setOrientation(LinearLayout.VERTICAL);
        dragArea.setGravity(Gravity.CENTER_HORIZONTAL);
        dragArea.setPadding(0, dp(4), 0, dp(14));

        View dragHandle = new View(this);
        dragHandle.setBackground(cBox(Theme.BORDER_DARK, 0, 0, 3));
        dragArea.addView(dragHandle, new LinearLayout.LayoutParams(dp(44), dp(5)));
        root.addView(dragArea);

        // Header Row: Title + Refresh Button + Close Button
        LinearLayout headRow = new LinearLayout(this);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        headRow.setPadding(0, 0, 0, dp(10));

        TextView titleView = cText("Models & Quota", 18f, Theme.TEXT_MAIN, true, false);
        headRow.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));

        final ImageView btnRefresh = cIconButton(R.drawable.ic_refresh, 18, 36, Theme.TEXT_MUTED);
        headRow.addView(btnRefresh);

        ImageView closeBtn = cIconButton(R.drawable.ic_close, 18, 36, Theme.TEXT_MUTED);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        headRow.addView(closeBtn);
        root.addView(headRow);

        final String userEmail = "rizkiarbi65@gmail.com";
        final TextView sub = cText("Account: " + userEmail, 13.5f, Theme.TEXT_MAIN, true, false);
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(-1, -2);
        lpSub.setMargins(0, 0, 0, dp(14));
        root.addView(sub, lpSub);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        final boolean isCodex = "codex".equalsIgnoreCase(currentEngine);

        // 1. ACTIVITY SECTION (filled in by the fetch below)
        final LinearLayout geminiGroup = createSettingsGroupContainer();
        geminiGroup.setPadding(dp(16), dp(14), dp(16), dp(14));
        renderModelGroupHeader(geminiGroup, "AKTIVITAS", "Memuat...");
        list.addView(geminiGroup);

        // 2. QUOTA SECTION — deliberately empty until the server says what it knows
        final LinearLayout claudeGroup = createSettingsGroupContainer();
        claudeGroup.setPadding(dp(16), dp(14), dp(16), dp(14));
        renderModelGroupHeader(claudeGroup, "KUOTA PROVIDER", "Memuat...");
        list.addView(claudeGroup);

        // 3. ENGINE & SERVER DETAILS
        final LinearLayout detailCard = createSettingsGroupContainer();
        detailCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        addStatRow(detailCard, "Active Engine", isCodex ? "Codex CLI" : "Antigravity CLI (agy)");
        addStatRow(detailCard, "Active Model", displayModel(currentModel));
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
        btnClose.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 14));
        btnClose.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnClose.addView(cText("Tutup", 14f, Theme.TEXT_MAIN, true, false));
        btnClose.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(-1, dp(44));
        lpBtn.setMargins(0, dp(14), 0, 0);
        root.addView(btnClose, lpBtn);

        dialog.setContentView(root);
        dialog.show();

        // Refresh action runnable
        // Reading the quota runs the CLI, so opening the sheet uses the server's
        // cached answer and only the refresh button forces a fresh read.
        final boolean[] forceQuotaRefresh = { false };

        final Runnable fetchUsageRunnable = () -> {
            String endpoint = prefs.getString("url", "").trim();
            if (endpoint.isEmpty()) return;

            final boolean force = forceQuotaRefresh[0];
            forceQuotaRefresh[0] = false;

            btnRefresh.setColorFilter(Theme.ACCENT);
            executor.execute(() -> {
                try {
                    String usageUrl = endpoint.replace("/api/chat", "/api/usage")
                            + "?engine=" + currentEngine + (force ? "&refresh=1" : "");
                    HttpURLConnection c = (HttpURLConnection) new URL(usageUrl).openConnection();
                    c.setRequestMethod("GET");
                    c.setConnectTimeout(8000);
                    // A forced read waits on the CLI, which takes seconds.
                    c.setReadTimeout(force ? 60000 : 12000);
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
                            btnRefresh.setColorFilter(Theme.GREEN);
                            mainHandler.postDelayed(() -> btnRefresh.setColorFilter(Theme.TEXT_MUTED), 1500);

                            String acc = json.optString("account", userEmail);
                            sub.setText("Account: " + acc);

                            // Everything below is measured on the server. The old
                            // screen filled gaps with invented numbers (75%, 47%,
                            // 2782 steps); a real zero now shows as zero.
                            boolean quotaKnown = json.optBoolean("quotaKnown", false);

                            geminiGroup.removeAllViews();
                            renderModelGroupHeader(geminiGroup, "AKTIVITAS", "Dihitung dari riwayat lokal di server");
                            addStatRow(geminiGroup, "5 jam terakhir", json.optInt("promptsLast5h", 0) + " prompt");
                            addStatRow(geminiGroup, "24 jam terakhir", json.optInt("promptsLast24h", 0) + " prompt");
                            addStatRow(geminiGroup, "7 hari terakhir", json.optInt("promptsLast7d", 0) + " prompt");

                            claudeGroup.removeAllViews();
                            JSONArray quotaGroups = json.optJSONArray("quotaGroups");
                            if (quotaKnown && quotaGroups != null && quotaGroups.length() > 0) {
                                renderModelGroupHeader(claudeGroup, "KUOTA PROVIDER",
                                        json.optBoolean("quotaStale", false)
                                                ? "Angka terakhir yang berhasil dibaca"
                                                : "Sisa kuota akun Antigravity");
                                for (int g = 0; g < quotaGroups.length(); g++) {
                                    JSONObject group = quotaGroups.optJSONObject(g);
                                    if (group == null) continue;
                                    renderQuotaGroup(claudeGroup, group, g > 0);
                                }
                            } else {
                                renderModelGroupHeader(claudeGroup, "KUOTA PROVIDER", "Tidak bisa dibaca saat ini");
                                TextView note = cText(json.optString("quotaStatus",
                                                "CLI tidak mengembalikan sisa kuota."),
                                        12.5f, Theme.TEXT_MUTED, false, false);
                                note.setPadding(0, dp(6), 0, 0);
                                claudeGroup.addView(note);
                            }

                            detailCard.removeAllViews();
                            if (isCodex) {
                                addStatRow(detailCard, "Engine aktif", "Codex CLI");
                                addStatRow(detailCard, "Model aktif", displayModel(currentModel));
                                addStatRow(detailCard, "Total prompt", formatCount(json.optLong("totalPrompts", 0)));
                                addStatRow(detailCard, "Total sesi", formatCount(json.optLong("totalSessions", 0)));
                                addStatRow(detailCard, json.optBoolean("tokensMeasured", false)
                                                ? "Token terpakai" : "Perkiraan token",
                                        formatCount(json.optLong("estimatedTokens", 0)));
                            } else {
                                addStatRow(detailCard, "Engine aktif", "Antigravity CLI (agy)");
                                addStatRow(detailCard, "Model aktif", displayModel(currentModel));
                                addStatRow(detailCard, "Total prompt", formatCount(json.optLong("totalPrompts", 0)));
                                addStatRow(detailCard, "Total sesi", formatCount(json.optLong("totalSessions", 0)));
                                addStatRow(detailCard, "Langkah dieksekusi", formatCount(json.optLong("totalSteps", 0)));
                                addStatRow(detailCard, "Pemanggilan tool", formatCount(json.optLong("totalTools", 0)));
                                addStatRow(detailCard, "Perkiraan token", formatCount(json.optLong("estimatedTokens", 0)));
                                addStatRow(detailCard, "Memori server", json.optString("memoryUsage", "-"));
                            }
                            addStatRow(detailCard, "Host", json.optString("hostname", currentServerHostname));
                            addStatRow(detailCard, "Uptime", json.optString("uptime", "-"));
                        });
                    } else {
                        mainHandler.post(() -> btnRefresh.setColorFilter(Theme.TEXT_MUTED));
                    }
                } catch (Exception ignored) {
                    mainHandler.post(() -> btnRefresh.setColorFilter(Theme.TEXT_MUTED));
                }
            });
        };

        btnRefresh.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Membaca ulang kuota dari CLI...", Toast.LENGTH_SHORT).show();
            forceQuotaRefresh[0] = true;
            fetchUsageRunnable.run();
        });

        // Trigger initial fetch
        fetchUsageRunnable.run();
    }

    /** One quota group ("Gemini Models") with a bar per limit it reports. */
    private void renderQuotaGroup(LinearLayout container, JSONObject group, boolean withSpacer) {
        if (withSpacer) {
            View spacer = new View(this);
            container.addView(spacer, new LinearLayout.LayoutParams(-1, dp(16)));
        }

        TextView name = cText(group.optString("group", "Model"), 12.5f, Theme.TEXT_MAIN, true, false);
        name.setPadding(0, 0, 0, dp(8));
        container.addView(name);

        JSONArray limits = group.optJSONArray("limits");
        if (limits == null) return;

        for (int i = 0; i < limits.length(); i++) {
            JSONObject limit = limits.optJSONObject(i);
            if (limit == null) continue;
            renderQuotaBar(container,
                    limit.optString("label", "Limit"),
                    limit.optInt("percent", 0),
                    limit.isNull("resetAt") ? "" : limit.optString("resetAt", ""),
                    i > 0);
        }
    }

    private void renderQuotaBar(LinearLayout container, String label, int percent, String resetAt, boolean withSpacer) {
        if (withSpacer) {
            View spacer = new View(this);
            container.addView(spacer, new LinearLayout.LayoutParams(-1, dp(12)));
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(cText(label, 13f, Theme.TEXT_MUTED, false, false), new LinearLayout.LayoutParams(0, -2, 1));

        // Remaining quota: green when there is room, amber when it is getting
        // thin, red when nearly gone.
        final int fill = percent <= 10 ? Theme.RED : (percent <= 30 ? Theme.AMBER : Theme.GREEN);
        row.addView(cText(percent + "%", 13.5f, fill, true, false));
        container.addView(row, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout track = new FrameLayout(this);
        track.setBackground(cBox(Theme.SURFACE_MUTED, 0, 0, 4));
        LinearLayout.LayoutParams lpTrack = new LinearLayout.LayoutParams(-1, dp(7));
        lpTrack.setMargins(0, dp(7), 0, 0);
        container.addView(track, lpTrack);

        final View bar = new View(this);
        bar.setBackground(cBox(fill, 0, 0, 4));
        track.addView(bar, new FrameLayout.LayoutParams(0, -1));

        // The track has no width until it is laid out, so size the fill then.
        final int pct = Math.max(0, Math.min(100, percent));
        track.post(() -> {
            int width = track.getWidth();
            if (width <= 0) return;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bar.getLayoutParams();
            lp.width = Math.max(pct > 0 ? dp(4) : 0, Math.round(width * pct / 100f));
            bar.setLayoutParams(lp);
        });

        String reset = formatQuotaReset(resetAt);
        if (!reset.isEmpty()) {
            TextView resetView = cText(reset, 11.5f, Theme.TEXT_LIGHT, false, false);
            resetView.setPadding(0, dp(5), 0, 0);
            container.addView(resetView);
        }
    }

    /** "Reset dalam 3 jam 12 mnt" — more useful than a raw timestamp. */
    private String formatQuotaReset(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isEmpty()) return "";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            long resetAt = parser.parse(isoTimestamp).getTime();
            long remaining = resetAt - System.currentTimeMillis();
            if (remaining <= 0) return "Sudah direset";

            long minutes = remaining / 60000;
            long days = minutes / (60 * 24);
            long hours = (minutes % (60 * 24)) / 60;
            long mins = minutes % 60;

            if (days > 0) return "Reset dalam " + days + " hari " + hours + " jam";
            if (hours > 0) return "Reset dalam " + hours + " jam " + mins + " mnt";
            return "Reset dalam " + mins + " mnt";
        } catch (Throwable t) {
            return "";
        }
    }

    private void renderModelGroupHeader(LinearLayout container, String groupTitle, String modelsSub) {
        TextView gTitle = cText(groupTitle, 13f, Theme.TEXT_MAIN, true, false);
        container.addView(gTitle);

        TextView mSub = cText("Models within this group: " + modelsSub, 11.5f, Theme.TEXT_MUTED, false, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(2), 0, dp(12));
        container.addView(mSub, lp);
    }

    private void addStatRow(LinearLayout container, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView lbl = cText(label, 13f, Theme.TEXT_MUTED, false, false);
        row.addView(lbl, new LinearLayout.LayoutParams(0, -2, 1));

        TextView val = cText(value, 13f, Theme.TEXT_MAIN, true, false);
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
        card.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 14));
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

        TextView desc = cText("Token bearer digunakan untuk mengamankan komunikasi antara HP Android dan Bridge Server Anda.", 13f, Theme.TEXT_MUTED, false, false);
        LinearLayout.LayoutParams lpD = new LinearLayout.LayoutParams(-1, -2);
        lpD.setMargins(0, 0, 0, dp(14));
        root.addView(desc, lpD);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 14));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        addStatRow(card, "Status Bearer Token", token.isEmpty() ? "Tidak Ada (Publik)" : "•••••••••••• (Aman)");
        root.addView(card);

        LinearLayout btnEdit = new LinearLayout(this);
        btnEdit.setOrientation(LinearLayout.HORIZONTAL);
        btnEdit.setGravity(Gravity.CENTER);
        btnEdit.setBackground(cBox(Theme.ACCENT, 0, 0, 14));
        btnEdit.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnEdit.addView(cText("Ganti Token di Konektor", 14f, Theme.ON_ACCENT, true, false));
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

        ImageView sp = cIcon(R.drawable.ic_spark, 32, Theme.ACCENT);
        logoRow.addView(sp);

        LinearLayout lt = new LinearLayout(this);
        lt.setOrientation(LinearLayout.VERTICAL);
        lt.setPadding(dp(12), 0, 0, 0);
        lt.addView(cText("Antigravity Code Remote", 16f, Theme.TEXT_MAIN, true, true));
        lt.addView(cText("Versi 2.9.9 • Claude Dark Edition", 12.5f, Theme.TEXT_MUTED, false, false));
        logoRow.addView(lt);
        root.addView(logoRow);

        TextView info = cText("Klien remote cerdas untuk Antigravity CLI dan Codex CLI di Android dengan live synchronization dan format markdown interaktif.", 13.5f, Theme.TEXT_MUTED, false, false);
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
        scanBtn.setBackground(cBox(Theme.ACCENT, 0, 0, 14));
        scanBtn.setPadding(dp(12), dp(11), dp(12), dp(11));
        scanBtn.addView(cIcon(R.drawable.ic_qr_code, 20, Theme.ON_ACCENT));
        TextView scanLbl = cText("  Scan QR Code Pairing", 14f, Theme.ON_ACCENT, true, false);
        scanBtn.addView(scanLbl);
        scanBtn.setOnClickListener(v -> {
            dialog.dismiss();
            startQrScanner();
        });
        form.addView(scanBtn, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout pasteBtn = new LinearLayout(this);
        pasteBtn.setOrientation(LinearLayout.HORIZONTAL);
        pasteBtn.setGravity(Gravity.CENTER);
        pasteBtn.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 14));
        pasteBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
        pasteBtn.addView(cIcon(R.drawable.ic_content_paste, 18, Theme.TEXT_MAIN));
        TextView pasteLbl = cText("  Tempel dari Clipboard", 13.5f, Theme.TEXT_MAIN, true, false);
        pasteBtn.addView(pasteLbl);
        pasteBtn.setOnClickListener(v -> {
            dialog.dismiss();
            pasteFromClipboard();
        });
        LinearLayout.LayoutParams lpPBtn = new LinearLayout.LayoutParams(-1, dp(44));
        lpPBtn.setMargins(0, dp(8), 0, dp(14));
        form.addView(pasteBtn, lpPBtn);

        TextView orLbl = cText("— atau isi manual —", 12f, Theme.TEXT_LIGHT, false, false);
        orLbl.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpOr = new LinearLayout.LayoutParams(-1, -2);
        lpOr.setMargins(0, 0, 0, dp(12));
        form.addView(orLbl, lpOr);

        TextView urlLbl = cText("Bridge Endpoint URL:", 12.5f, Theme.TEXT_MUTED, true, false);
        form.addView(urlLbl);

        EditText urlInput = new EditText(this);
        urlInput.setHint("https://your-bridge.trycloudflare.com/api/chat");
        urlInput.setText(prefs.getString("url", ""));
        urlInput.setTextColor(Theme.TEXT_MAIN);
        urlInput.setHintTextColor(Theme.TEXT_LIGHT);
        urlInput.setTextSize(14);
        urlInput.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        urlInput.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams lpUrl = new LinearLayout.LayoutParams(-1, dp(48));
        lpUrl.setMargins(0, dp(4), 0, dp(14));
        form.addView(urlInput, lpUrl);

        TextView tokLbl = cText("Bearer Token (Secret):", 12.5f, Theme.TEXT_MUTED, true, false);
        form.addView(tokLbl);

        EditText tokenInput = new EditText(this);
        tokenInput.setHint("codex-remote-token-2026");
        tokenInput.setText(prefs.getString("token", ""));
        tokenInput.setTextColor(Theme.TEXT_MAIN);
        tokenInput.setHintTextColor(Theme.TEXT_LIGHT);
        tokenInput.setTextSize(14);
        tokenInput.setInputType(0x00000081);
        tokenInput.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        tokenInput.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams lpTok = new LinearLayout.LayoutParams(-1, dp(48));
        lpTok.setMargins(0, dp(4), 0, dp(16));
        form.addView(tokenInput, lpTok);

        LinearLayout btnSave = new LinearLayout(this);
        btnSave.setOrientation(LinearLayout.HORIZONTAL);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setBackground(cBox(Theme.ACCENT, 0, 0, 14));
        btnSave.setPadding(dp(16), dp(12), dp(16), dp(12));
        btnSave.addView(cText("Simpan & Hubungkan", 14f, Theme.ON_ACCENT, true, false));
        btnSave.setOnClickListener(v -> {
            String u = urlInput.getText().toString().trim();
            String t = tokenInput.getText().toString().trim();
            saveConnectionCredentials(u, t, currentEngine);
            dialog.dismiss();
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
        pill.setBackground(cBox(isActuallyRunning ? Theme.AMBER_BG : Theme.SURFACE_MUTED, isActuallyRunning ? Theme.AMBER : Theme.BORDER, 1, 14));
        pill.setPadding(dp(12), dp(9), dp(12), dp(9));

        ImageView actionIcon = cIcon(toolCount > 0 ? R.drawable.ic_build : R.drawable.ic_psychology, 18, isActuallyRunning ? Theme.AMBER : Theme.ACCENT);
        pill.addView(actionIcon);

        TextView tv = cText("  " + labelText, 13f, Theme.TEXT_MAIN, true, false);
        pill.addView(tv, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout stateBadge = new LinearLayout(this);
        stateBadge.setOrientation(LinearLayout.HORIZONTAL);
        stateBadge.setGravity(Gravity.CENTER_VERTICAL);

        if (isActuallyRunning) {
            stateBadge.setBackground(cBox(Theme.AMBER_BG, Theme.AMBER, 1, 6));
            stateBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
            ProgressBar pb = new ProgressBar(this);
            LinearLayout.LayoutParams lpPb = new LinearLayout.LayoutParams(dp(12), dp(12));
            stateBadge.addView(pb, lpPb);
            TextView runText = cText(" Running", 11f, Theme.AMBER, true, false);
            stateBadge.addView(runText);
        } else {
            stateBadge.setBackground(cBox(Theme.GREEN_BG, Theme.GREEN, 1, 6));
            stateBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
            stateBadge.addView(cIcon(R.drawable.ic_check, 12, Theme.GREEN));
            TextView doneText = cText(" Done", 11f, Theme.GREEN, true, false);
            stateBadge.addView(doneText);
        }
        pill.addView(stateBadge);

        ImageView chevron = cIcon(R.drawable.ic_chevron_right, 20, Theme.TEXT_MUTED);
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
        modalRoot.setBackground(cBox(Theme.BG, 0, 0, 24));
        modalRoot.setPadding(dp(20), dp(10), dp(20), dp(16));

        // Drag Area
        final LinearLayout dragArea = new LinearLayout(this);
        dragArea.setOrientation(LinearLayout.VERTICAL);
        dragArea.setGravity(Gravity.CENTER_HORIZONTAL);
        dragArea.setPadding(0, dp(4), 0, dp(10));

        View dragHandle = new View(this);
        dragHandle.setBackground(cBox(Theme.BORDER_DARK, 0, 0, 3));
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

        TextView title = cText("Execution & Thoughts", 18.5f, Theme.TEXT_MAIN, true, true);
        headerRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        final ImageView fullscreenBtn = cIconButton(R.drawable.ic_fullscreen, 24, 40, Theme.TEXT_MAIN);
        headerRow.addView(fullscreenBtn);

        ImageView closeBtn = cIconButton(R.drawable.ic_close, 22, 40, Theme.TEXT_MAIN);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        headerRow.addView(closeBtn);
        activeBottomSheetMasterView.addView(headerRow);

        activeBottomSheetSubtitle = cText(items.size() + " actions • ketuk item untuk melihat detail", 13f, Theme.TEXT_MUTED, false, false);
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
            window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
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

        int currentCount = activeBottomSheetMasterList.getChildCount();
        if (currentCount == items.size() && currentCount > 0) {
            // Incremental fast-path: Only update the last running badge without layout destruction!
            View lastCard = activeBottomSheetMasterList.getChildAt(currentCount - 1);
            if (lastCard instanceof LinearLayout) {
                final JSONObject lastItem = items.get(currentCount - 1);
                lastCard.setBackground(cBox(isActuallyRunning ? Theme.AMBER_BG : Theme.SURFACE, isActuallyRunning ? Theme.AMBER : Theme.BORDER, 1, 16));
                lastCard.setOnClickListener(v -> showStepDetailView(lastItem, isActuallyRunning));
            }
            return;
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
            card.setBackground(cBox(isThisItemRunning ? Theme.AMBER_BG : Theme.SURFACE, isThisItemRunning ? Theme.AMBER : Theme.BORDER, 1, 16));
            card.setPadding(dp(14), dp(12), dp(14), dp(12));

            ImageView ic = cIcon(isTool ? R.drawable.ic_build : R.drawable.ic_psychology, 20, isThisItemRunning ? Theme.AMBER : Theme.ACCENT);
            card.addView(ic);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setPadding(dp(12), 0, dp(8), 0);

            TextView tView = cText(toolTitle, 14.5f, Theme.TEXT_MAIN, true, false);
            textCol.addView(tView);

            TextView subV = cText(displayTitle, 12f, Theme.TEXT_MUTED, false, false);
            subV.setSingleLine(true);
            textCol.addView(subV);

            card.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

            LinearLayout bBadge = new LinearLayout(this);
            bBadge.setOrientation(LinearLayout.HORIZONTAL);
            bBadge.setGravity(Gravity.CENTER_VERTICAL);

            if (isThisItemRunning) {
                bBadge.setBackground(cBox(Theme.AMBER_BG, Theme.AMBER, 1, 6));
                bBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
                ProgressBar pb = new ProgressBar(this);
                LinearLayout.LayoutParams lpPb = new LinearLayout.LayoutParams(dp(12), dp(12));
                bBadge.addView(pb, lpPb);
                TextView bText = cText(" Running", 11f, Theme.AMBER, true, false);
                bBadge.addView(bText);
            } else {
                bBadge.setBackground(cBox(Theme.GREEN_BG, Theme.GREEN, 1, 6));
                bBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
                bBadge.addView(cIcon(R.drawable.ic_check, 12, Theme.GREEN));
                TextView bText = cText(" Selesai", 11f, Theme.GREEN, true, false);
                bBadge.addView(bText);
            }
            card.addView(bBadge);

            ImageView chevron = cIcon(R.drawable.ic_chevron_right, 20, Theme.TEXT_MUTED);
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

        ImageView backBtn = cIconButton(R.drawable.ic_arrow_back, 24, 40, Theme.TEXT_MAIN);
        backBtn.setOnClickListener(v -> {
            activeBottomSheetDetailView.setVisibility(View.GONE);
            activeBottomSheetMasterView.setVisibility(View.VISIBLE);
        });
        topBar.addView(backBtn);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView titleView = cText(isEditDiff ? "Edit" : toolTitle, 19f, Theme.TEXT_MAIN, true, false);
        titleView.setGravity(Gravity.CENTER);
        titleCol.addView(titleView);

        TextView statusView = cText(statusText, 13f, isRunning ? Theme.AMBER : Theme.TEXT_MUTED, false, false);
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

            TextView fnView = cText(fileName, 14.5f, Theme.TEXT_MAIN, true, false);
            fnView.setTypeface(Typeface.MONOSPACE);
            metaBar.addView(fnView);

            TextView pathView = cText("  " + dirPath, 13f, Theme.TEXT_MUTED, false, false);
            pathView.setSingleLine(true);
            pathView.setEllipsize(TextUtils.TruncateAt.END);
            metaBar.addView(pathView, new LinearLayout.LayoutParams(0, -2, 1));

            if (addedLines > 0 || deletedLines > 0) {
                LinearLayout diffBadge = new LinearLayout(this);
                diffBadge.setOrientation(LinearLayout.HORIZONTAL);
                diffBadge.setGravity(Gravity.CENTER_VERTICAL);

                if (addedLines > 0) {
                    diffBadge.addView(cText("+" + addedLines + " ", 13.5f, Theme.GREEN, true, false));
                }
                if (deletedLines > 0) {
                    diffBadge.addView(cText("-" + deletedLines, 13.5f, Theme.RED, true, false));
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
                TextView fallback = cText(outputText, 13f, Theme.TEXT_MAIN, false, false);
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
            TextView cmdLabel = cText("Perintah", 13.5f, Theme.TEXT_MUTED, false, false);
            LinearLayout.LayoutParams lpCmdL = new LinearLayout.LayoutParams(-1, -2);
            lpCmdL.setMargins(0, 0, 0, dp(8));
            body.addView(cmdLabel, lpCmdL);

            LinearLayout cmdBox = new LinearLayout(this);
            cmdBox.setOrientation(LinearLayout.VERTICAL);
            cmdBox.setBackground(cBox(Theme.CODE_BG, Theme.BORDER, 1, 12));
            cmdBox.setPadding(dp(14), dp(12), dp(14), dp(12));

            TextView cmdView = new TextView(this);
            cmdView.setText(commandText.isEmpty() ? toolTitle : commandText);
            cmdView.setTextSize(13.5f);
            cmdView.setTextColor(Color.rgb(255, 204, 128)); // Highlighted amber command syntax
            cmdView.setTypeface(Typeface.MONOSPACE);
            cmdView.setTextIsSelectable(true);
            cmdBox.addView(cmdView);

            body.addView(cmdBox);

            TextView outLabel = cText("Keluaran / Respons", 13.5f, Theme.TEXT_MUTED, false, false);
            LinearLayout.LayoutParams lpOutL = new LinearLayout.LayoutParams(-1, -2);
            lpOutL.setMargins(0, dp(16), 0, dp(8));
            body.addView(outLabel, lpOutL);

            LinearLayout outBox = new LinearLayout(this);
            outBox.setOrientation(LinearLayout.VERTICAL);
            outBox.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 12));
            outBox.setPadding(dp(14), dp(12), dp(14), dp(12));

            TextView outView = new TextView(this);
            outView.setText(outputText.isEmpty() ? "(Tidak ada output teks)" : outputText);
            outView.setTextSize(13.5f);
            outView.setTextColor(Theme.TEXT_MAIN);
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

        chatNavIcon = cIconButton(R.drawable.ic_arrow_back, 24, 40, Theme.TEXT_MAIN);
        chatNavIcon.setOnClickListener(v -> {
            if (navigatedFromHub || currentScreen == 1) {
                navigatedFromHub = false;
                showScreen(0);
            } else {
                openSidebar();
            }
        });
        topBar.addView(chatNavIcon);

        chatTopTitle = cText("New session", 16f, Theme.TEXT_MAIN, true, false);
        chatTopTitle.setGravity(Gravity.CENTER);
        chatTopTitle.setSingleLine(true);
        chatTopTitle.setOnClickListener(v -> showRenameSessionDialog(activeConversationId, activeSessionTitle));
        topBar.addView(chatTopTitle, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView qrTopBtn = cIconButton(R.drawable.ic_qr_code, 22, 40, Theme.TEXT_MAIN);
        qrTopBtn.setOnClickListener(v -> startQrScanner());
        topBar.addView(qrTopBtn);

        final ImageView moreBtn = cIconButton(R.drawable.ic_more_vert, 24, 40, Theme.TEXT_MAIN);
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
        floatingWrapper.addView(buildSlashCommandsPopup());
        floatingWrapper.addView(buildQuickActionToolbar());

        LinearLayout composerCard = new LinearLayout(this);
        composerCard.setOrientation(LinearLayout.VERTICAL);
        composerCard.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 24));
        composerCard.setPadding(dp(16), dp(12), dp(12), dp(10));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            composerCard.setElevation(dp(8));
        }

        promptInput = new EditText(this);
        promptInput.setHint("Code anything...");
        promptInput.setHintTextColor(Theme.TEXT_LIGHT);
        promptInput.setTextColor(Theme.TEXT_MAIN);
        promptInput.setTextSize(15.5f);
        promptInput.setTypeface(Typeface.SERIF);
        promptInput.setBackgroundColor(Color.TRANSPARENT);
        promptInput.setMaxLines(6);
        promptInput.setPadding(0, 0, 0, dp(8));
        promptInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSlashCommandsSuggestions(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        composerCard.addView(promptInput);

        // Action Toolbar
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);

        btnAttach = cIconButton(R.drawable.ic_add, 24, 38, Theme.TEXT_MUTED);
        btnAttach.setOnClickListener(v -> openMultiFilePicker());
        actionRow.addView(btnAttach);

        btnVoice = cIconButton(R.drawable.ic_mic, 22, 38, Theme.TEXT_MUTED);
        btnVoice.setOnClickListener(v -> startVoiceRecognition());
        actionRow.addView(btnVoice);

        repoTagLabel = cText(engineShortLabel(currentEngine), 12f, Theme.ACCENT, true, false);
        repoTagLabel.setBackground(cBox(Theme.ACCENT_SOFT, Theme.BORDER, 1, 12));
        repoTagLabel.setPadding(dp(10), dp(4), dp(10), dp(4));
        repoTagLabel.setOnClickListener(v -> showEngineSwitcher());
        LinearLayout.LayoutParams lpTag = new LinearLayout.LayoutParams(-2, -2);
        lpTag.setMargins(dp(6), 0, 0, 0);
        actionRow.addView(repoTagLabel, lpTag);

        modelTagLabel = cText(displayModel(currentModel), 11.5f, Theme.TEXT_MUTED, true, false);
        modelTagLabel.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        modelTagLabel.setPadding(dp(9), dp(4), dp(9), dp(4));
        modelTagLabel.setOnClickListener(v -> showModelPicker());
        LinearLayout.LayoutParams lpModelTag = new LinearLayout.LayoutParams(-2, -2);
        lpModelTag.setMargins(dp(6), 0, 0, 0);
        actionRow.addView(modelTagLabel, lpModelTag);

        View spring = new View(this);
        actionRow.addView(spring, new LinearLayout.LayoutParams(0, 1, 1));

        btnSend = new FrameLayout(this);
        btnSend.setBackground(cBox(Theme.ACCENT, 0, 0, 18));
        ImageView sendIcon = cIcon(R.drawable.ic_send, 18, Theme.ON_ACCENT);
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
        btnScrollToBottom.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 22));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnScrollToBottom.setElevation(dp(6));
        }
        ImageView downArrow = cIcon(R.drawable.ic_expand_more, 22, Theme.TEXT_MAIN);
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
        chatSessionLoadingView.setBackgroundColor(Theme.BG);
        chatSessionLoadingView.setVisibility(View.GONE);

        ProgressBar loadPb = new ProgressBar(this);
        LinearLayout.LayoutParams lpPb = new LinearLayout.LayoutParams(dp(36), dp(36));
        lpPb.gravity = Gravity.CENTER_HORIZONTAL;
        chatSessionLoadingView.addView(loadPb, lpPb);

        TextView loadText = cText("Memuat percakapan...", 13.5f, Theme.TEXT_MUTED, false, false);
        loadText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpText = new LinearLayout.LayoutParams(-2, -2);
        lpText.gravity = Gravity.CENTER_HORIZONTAL;
        lpText.setMargins(0, dp(12), 0, 0);
        chatSessionLoadingView.addView(loadText, lpText);

        FrameLayout.LayoutParams lpLoadingRoot = new FrameLayout.LayoutParams(-1, -1);
        lpLoadingRoot.gravity = Gravity.CENTER;
        root.addView(chatSessionLoadingView, lpLoadingRoot);
    }

    private void buildEmptyMascotState() {
        emptyMascotView = new LinearLayout(this);
        emptyMascotView.setOrientation(LinearLayout.VERTICAL);
        emptyMascotView.setGravity(Gravity.CENTER);
        emptyMascotView.setPadding(dp(24), dp(56), dp(24), dp(56));

        ImageView mascot = new ImageView(this);
        mascot.setImageResource(Theme.mascotRes());
        mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lpMascot = new LinearLayout.LayoutParams(dp(148), dp(148));
        emptyMascotView.addView(mascot, lpMascot);

        // Gentle idle float so the empty session does not feel static.
        mascotFloatAnimator = ObjectAnimator.ofFloat(mascot, "translationY", dp(6), dp(-6));
        mascotFloatAnimator.setDuration(1700);
        mascotFloatAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mascotFloatAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mascotFloatAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        TextView brandName = cText(Theme.brandTitle(), 19, Theme.TEXT_MAIN, true, true);
        brandName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpBn = new LinearLayout.LayoutParams(-1, -2);
        lpBn.setMargins(0, dp(18), 0, 0);
        emptyMascotView.addView(brandName, lpBn);

        TextView tagline = cText(Theme.engineTagline(), 13.5f, Theme.TEXT_MUTED, false, false);
        tagline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lpTag = new LinearLayout.LayoutParams(-1, -2);
        lpTag.setMargins(dp(20), dp(8), dp(20), 0);
        emptyMascotView.addView(tagline, lpTag);
    }

    private void showEmptyMascotState(boolean show) {
        if (emptyMascotView != null) {
            // chatMessagesList.removeAllViews() detaches the mascot, so flipping
            // visibility alone would never bring it back — re-attach it first.
            if (show && emptyMascotView.getParent() == null && chatMessagesList != null) {
                chatMessagesList.addView(emptyMascotView, new LinearLayout.LayoutParams(-1, -2));
            }
            emptyMascotView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        // Only run the float animation while the mascot is on screen.
        if (mascotFloatAnimator != null) {
            if (show && !mascotFloatAnimator.isStarted()) mascotFloatAnimator.start();
            else if (!show && mascotFloatAnimator.isStarted()) mascotFloatAnimator.cancel();
        }
    }

    private void updateRepoTag() {
        if (repoTagLabel != null) {
            repoTagLabel.setText(engineShortLabel(currentEngine));
        }
        if (modelTagLabel != null) modelTagLabel.setText(displayModel(currentModel));
    }

    private void showModelPicker() {
        final boolean isCodex = "codex".equalsIgnoreCase(currentEngine);
        final String[] models = isCodex
                ? new String[]{"gpt-5.6-luna", "gpt-5.6-sol", "default"}
                : new String[]{"auto", "gemini-3.7-flash-high", "gemini-3.7-flash-medium", "gemini-3.1-pro-high", "claude-sonnet-4-6", "gpt-oss-120b-medium"};
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Pilih Model", true);
        TextView subtitle = cText(isCodex ? "Model Codex yang dipakai untuk prompt baru" : "Model Antigravity yang dipakai untuk prompt baru", 12.5f, Theme.TEXT_MUTED, false, false);
        root.addView(subtitle);
        for (String model : models) {
            TextView option = cText((model.equalsIgnoreCase(currentModel) ? "✓  " : "    ") + displayModel(model), 14f,
                    model.equalsIgnoreCase(currentModel) ? Theme.ACCENT : Theme.TEXT_MAIN, true, false);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setPadding(dp(14), 0, dp(14), 0);
            option.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
            lp.setMargins(0, dp(8), 0, 0);
            root.addView(option, lp);
            option.setOnClickListener(v -> {
                currentModel = model;
                prefs.edit().putString(modelPrefKey(currentEngine), currentModel).apply();
                updateRepoTag();
                dialog.dismiss();
                startNewSession();
                Toast.makeText(this, "Model: " + displayModel(currentModel), Toast.LENGTH_SHORT).show();
            });
        }
        dialog.setContentView(root);
        dialog.show();
    }

    // ============================================================
    // ENGINE SWITCHING
    // ============================================================
    private boolean isCodexEngine() {
        return "codex".equalsIgnoreCase(currentEngine);
    }

    private String engineLabel(String engine) {
        return "codex".equalsIgnoreCase(engine) ? "Codex CLI" : "Antigravity CLI";
    }

    private String engineShortLabel(String engine) {
        return "codex".equalsIgnoreCase(engine) ? "Codex" : "Agy";
    }

    private String engineRepo(String engine) {
        return "codex".equalsIgnoreCase(engine) ? "openai/codex-cli" : "google/antigravity-cli";
    }

    /**
     * Engine picker with a real toggle. Switching starts a fresh session on the
     * other CLI, so an in-progress conversation is confirmed first instead of
     * being discarded the moment the row is tapped.
     */
    private void showEngineSwitcher() {
        final Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Engine", true);

        root.addView(cText("Sesi tidak dibagi antar engine. Berpindah akan memulai sesi baru.",
                12.5f, Theme.TEXT_MUTED, false, false));

        // --- toggle row ---
        LinearLayout toggleRow = new LinearLayout(this);
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        toggleRow.setGravity(Gravity.CENTER_VERTICAL);
        toggleRow.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 16));
        toggleRow.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lpToggle = new LinearLayout.LayoutParams(-1, -2);
        lpToggle.setMargins(0, dp(16), 0, dp(6));

        LinearLayout toggleCol = new LinearLayout(this);
        toggleCol.setOrientation(LinearLayout.VERTICAL);
        final TextView toggleTitle = cText(engineLabel(currentEngine), 15.5f, Theme.TEXT_MAIN, true, false);
        toggleCol.addView(toggleTitle);
        final TextView toggleSub = cText(engineRepo(currentEngine) + " · " + displayModel(currentModel),
                12f, Theme.TEXT_MUTED, false, false);
        toggleSub.setSingleLine(true);
        toggleSub.setEllipsize(TextUtils.TruncateAt.END);
        toggleCol.addView(toggleSub);
        toggleRow.addView(toggleCol, new LinearLayout.LayoutParams(0, -2, 1));

        final Switch engineSwitch = new Switch(this);
        engineSwitch.setChecked(isCodexEngine());
        engineSwitch.setThumbTintList(android.content.res.ColorStateList.valueOf(Theme.ACCENT));
        engineSwitch.setTrackTintList(android.content.res.ColorStateList.valueOf(Theme.BORDER_DARK));
        toggleRow.addView(engineSwitch);
        root.addView(toggleRow, lpToggle);

        TextView toggleHint = cText("Mati = Antigravity · Nyala = Codex", 11.5f, Theme.TEXT_LIGHT, false, false);
        toggleHint.setPadding(dp(4), 0, 0, dp(14));
        root.addView(toggleHint);

        engineSwitch.setOnClickListener(v -> {
            final String target = engineSwitch.isChecked() ? "codex" : "antigravity";
            if (target.equalsIgnoreCase(currentEngine)) return;

            // Put the switch back until the user actually confirms.
            engineSwitch.setChecked(isCodexEngine());
            dialog.dismiss();
            requestEngineSwitch(target);
        });

        // --- per-engine detail cards ---
        root.addView(buildEngineCard("antigravity", dialog));
        root.addView(buildEngineCard("codex", dialog));

        dialog.setContentView(root);
        dialog.show();
    }

    private LinearLayout buildEngineCard(final String engine, final Dialog dialog) {
        boolean selected = engine.equalsIgnoreCase(currentEngine);
        String model = prefs.getString(modelPrefKey(engine), defaultModelForEngine(engine));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(cBox(selected ? Theme.ACCENT_SOFT : Theme.SURFACE_MUTED,
                selected ? Theme.ACCENT : Theme.BORDER, 1, 14));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        card.addView(cIcon(R.drawable.ic_tune, 18, selected ? Theme.ACCENT : Theme.TEXT_MUTED));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(8), 0);
        col.addView(cText(engineLabel(engine), 14.5f,
                selected ? Theme.ACCENT : Theme.TEXT_MAIN, true, false));
        col.addView(cText(engineRepo(engine) + " · " + displayModel(model), 11.5f, Theme.TEXT_MUTED, false, false));
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        if (selected) card.addView(cIcon(R.drawable.ic_check, 18, Theme.ACCENT));

        card.setOnClickListener(v -> {
            if (selected) {
                dialog.dismiss();
                showModelPicker();
                return;
            }
            dialog.dismiss();
            requestEngineSwitch(engine);
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, 0);
        card.setLayoutParams(lp);
        return card;
    }

    /** Confirms first when there is a live task or an ongoing conversation. */
    private void requestEngineSwitch(final String target) {
        boolean hasWork = isLiveTaskRunning
                || (activeConversationId != null && !activeConversationId.isEmpty());

        if (!hasWork) {
            applyEngineSwitch(target);
            return;
        }

        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Ganti Engine?", true);

        root.addView(cText("Beralih ke " + engineLabel(target) + " akan menutup sesi "
                        + engineLabel(currentEngine) + " yang sedang terbuka.",
                13.5f, Theme.TEXT_MAIN, false, false));

        TextView note = cText(isLiveTaskRunning
                        ? "Ada task yang sedang berjalan. Task tetap jalan di server, tapi layar akan pindah ke sesi baru."
                        : "Sesi lama tetap tersimpan dan bisa dibuka lagi dari daftar Kode.",
                12.5f, isLiveTaskRunning ? Theme.AMBER : Theme.TEXT_MUTED, false, false);
        note.setPadding(0, dp(8), 0, dp(16));
        root.addView(note);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView cancel = cText("Batal", 14f, Theme.TEXT_MAIN, true, false);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(16), dp(13), dp(16), dp(13));
        cancel.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(0, -2, 1);
        lpC.setMargins(0, 0, dp(8), 0);
        actions.addView(cancel, lpC);

        TextView confirm = cText("Ganti", 14f, Theme.ON_ACCENT, true, false);
        confirm.setGravity(Gravity.CENTER);
        confirm.setPadding(dp(16), dp(13), dp(16), dp(13));
        confirm.setBackground(cBox(Theme.ACCENT, 0, 0, 12));
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            applyEngineSwitch(target);
        });
        actions.addView(confirm, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);

        dialog.setContentView(root);
        dialog.show();
    }

    private void applyEngineSwitch(String target) {
        final String previous = currentEngine;
        final String next = "codex".equalsIgnoreCase(target) ? "codex" : "antigravity";
        if (next.equals(previous)) return;

        prefs.edit()
                .putString("engine", next)
                // Survives the rebuild so the new screen can explain itself.
                .putString("pending_engine_notice_from", previous)
                .remove("last_conversation_id")
                .remove("last_conversation_title")
                .remove("last_conversation_engine")
                .apply();

        // Every colour, the wordmark, the mascot and the heading face change
        // with the engine, and this UI is built entirely in code — recreating
        // the activity is what actually repaints all of it.
        Theme.applyEngine(next);
        recreate();
    }

    /** Shown once after the rebuild that follows an engine switch. */
    private void consumePendingEngineNotice() {
        String from = prefs.getString("pending_engine_notice_from", "");
        if (from.isEmpty()) return;
        prefs.edit().remove("pending_engine_notice_from").apply();

        startNewSession();
        renderEngineSwitchNotice(from, currentEngine);
        Toast.makeText(this, "Engine: " + engineLabel(currentEngine)
                + " · " + displayModel(currentModel), Toast.LENGTH_SHORT).show();
    }

    /** A centered notice card in the transcript marking the engine change. */
    private void renderEngineSwitchNotice(String from, String to) {
        if (chatMessagesList == null) return;

        LinearLayout notice = new LinearLayout(this);
        notice.setOrientation(LinearLayout.HORIZONTAL);
        notice.setGravity(Gravity.CENTER_VERTICAL);
        notice.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 14));
        notice.setPadding(dp(14), dp(11), dp(14), dp(11));

        notice.addView(cIcon(R.drawable.ic_swap, 16, Theme.ACCENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), 0, 0, 0);
        col.addView(cText(engineLabel(from) + "  →  " + engineLabel(to), 13f, Theme.TEXT_MAIN, true, false));
        col.addView(cText("Sesi baru dimulai · model " + displayModel(currentModel),
                11.5f, Theme.TEXT_MUTED, false, false));
        notice.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(8), dp(10), dp(8), dp(6));
        chatMessagesList.addView(notice, lp);
    }

    private void showRenameSessionDialog(final String targetConvId, final String initialTitle) {
        final String convId = (targetConvId != null && !targetConvId.trim().isEmpty()) ? targetConvId.trim() : activeConversationId;
        if (convId == null || convId.trim().isEmpty()) {
            Toast.makeText(this, "Belum ada sesi aktif untuk diubah namanya", Toast.LENGTH_SHORT).show();
            return;
        }

        final Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Ubah Nama Sesi", true);

        TextView sub = cText("Beri nama baru untuk sesi percakapan ini agar mudah dicari", 12.5f, Theme.TEXT_MUTED, false, false);
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(-1, -2);
        lpSub.setMargins(0, 0, 0, dp(14));
        root.addView(sub, lpSub);

        final EditText titleInput = new EditText(this);
        String startText = initialTitle != null && !initialTitle.isEmpty() ? initialTitle : activeSessionTitle;
        titleInput.setText(startText);
        titleInput.setHint("Nama sesi...");
        titleInput.setHintTextColor(Theme.TEXT_LIGHT);
        titleInput.setTextColor(Theme.TEXT_MAIN);
        titleInput.setTextSize(15f);
        titleInput.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        titleInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        titleInput.setSingleLine(true);
        titleInput.selectAll();
        root.addView(titleInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(18), 0, 0);

        TextView cancelBtn = cText("Batal", 14f, Theme.TEXT_MUTED, true, false);
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
        cancelBtn.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(cancelBtn, new LinearLayout.LayoutParams(0, -2, 1));

        View spacer = new View(this);
        btnRow.addView(spacer, new LinearLayout.LayoutParams(dp(10), 1));

        TextView saveBtn = cText("Simpan", 14f, Theme.ON_ACCENT, true, false);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
        saveBtn.setBackground(cBox(Theme.ACCENT, 0, 0, 12));
        saveBtn.setOnClickListener(v -> {
            final String newTitle = titleInput.getText().toString().trim();
            if (newTitle.isEmpty()) {
                Toast.makeText(MainActivity.this, "Nama sesi tidak boleh kosong", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            renameSessionOnServer(convId, newTitle);
        });
        btnRow.addView(saveBtn, new LinearLayout.LayoutParams(0, -2, 1));

        root.addView(btnRow);
        dialog.setContentView(root);
        dialog.show();

        titleInput.requestFocus();
    }

    private void renameSessionOnServer(final String convId, final String newTitle) {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty()) {
            Toast.makeText(this, "Endpoint server belum disetel", Toast.LENGTH_SHORT).show();
            return;
        }

        // Optimistic UI update
        if (convId.equals(activeConversationId)) {
            activeSessionTitle = newTitle;
            if (chatTopTitle != null) chatTopTitle.setText(newTitle);
            prefs.edit().putString("last_conversation_title", newTitle).apply();
        }

        executor.execute(() -> {
            try {
                String renameUrl = endpoint.replace("/api/chat", "/api/session/rename");
                HttpURLConnection c = (HttpURLConnection) new URL(renameUrl).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                String token = prefs.getString("token", "");
                if (!token.isEmpty()) {
                    c.setRequestProperty("Authorization", "Bearer " + token);
                }

                JSONObject payload = new JSONObject();
                payload.put("id", convId);
                payload.put("title", newTitle);

                try (OutputStream os = c.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = c.getResponseCode();
                if (code == 200) {
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "Nama sesi berhasil diubah", Toast.LENGTH_SHORT).show();
                        fetchHubSessions();
                    });
                } else {
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "Gagal mengubah nama sesi (HTTP " + code + ")", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "Gagal sinkron nama sesi: " + err, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showSessionOptionsBottomSheet(final String convId, final String currentTitle) {
        final Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, currentTitle != null && !currentTitle.isEmpty() ? currentTitle : "Pilihan Sesi", true);

        // 0. Pin / Unpin
        final boolean isPinned = getPinnedSessionIds().contains(convId);
        LinearLayout pinRow = new LinearLayout(this);
        pinRow.setOrientation(LinearLayout.HORIZONTAL);
        pinRow.setGravity(Gravity.CENTER_VERTICAL);
        pinRow.setPadding(dp(14), dp(12), dp(14), dp(12));
        pinRow.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        pinRow.addView(cIcon(R.drawable.ic_push_pin, 20, Theme.ACCENT));
        pinRow.addView(cText(isPinned ? "   Batal Sematkan Sesi" : "   Sematkan Sesi ke Atas 📌", 14f, Theme.TEXT_MAIN, true, false));
        pinRow.setOnClickListener(v -> {
            dialog.dismiss();
            togglePinSession(convId);
        });
        LinearLayout.LayoutParams lpPin = new LinearLayout.LayoutParams(-1, -2);
        lpPin.setMargins(0, dp(4), 0, dp(10));
        root.addView(pinRow, lpPin);

        // 1. Buka Sesi
        LinearLayout openRow = new LinearLayout(this);
        openRow.setOrientation(LinearLayout.HORIZONTAL);
        openRow.setGravity(Gravity.CENTER_VERTICAL);
        openRow.setPadding(dp(14), dp(12), dp(14), dp(12));
        openRow.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        openRow.addView(cIcon(R.drawable.ic_chat, 20, Theme.ACCENT));
        openRow.addView(cText("   Buka Sesi Ini", 14f, Theme.TEXT_MAIN, true, false));
        openRow.setOnClickListener(v -> {
            dialog.dismiss();
            navigatedFromHub = true;
            openSpecificSession(convId, currentTitle);
        });
        LinearLayout.LayoutParams lpOpen = new LinearLayout.LayoutParams(-1, -2);
        lpOpen.setMargins(0, dp(4), 0, dp(10));
        root.addView(openRow, lpOpen);

        // 2. Ubah Nama Sesi
        LinearLayout renameRow = new LinearLayout(this);
        renameRow.setOrientation(LinearLayout.HORIZONTAL);
        renameRow.setGravity(Gravity.CENTER_VERTICAL);
        renameRow.setPadding(dp(14), dp(12), dp(14), dp(12));
        renameRow.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        renameRow.addView(cIcon(R.drawable.ic_edit, 20, Theme.TEXT_MAIN));
        renameRow.addView(cText("   Ubah Nama Sesi", 14f, Theme.TEXT_MAIN, true, false));
        renameRow.setOnClickListener(v -> {
            dialog.dismiss();
            showRenameSessionDialog(convId, currentTitle);
        });
        LinearLayout.LayoutParams lpRename = new LinearLayout.LayoutParams(-1, -2);
        lpRename.setMargins(0, 0, 0, dp(10));
        root.addView(renameRow, lpRename);

        // 3. Ekspor Transkrip
        LinearLayout exportRow = new LinearLayout(this);
        exportRow.setOrientation(LinearLayout.HORIZONTAL);
        exportRow.setGravity(Gravity.CENTER_VERTICAL);
        exportRow.setPadding(dp(14), dp(12), dp(14), dp(12));
        exportRow.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        exportRow.addView(cIcon(R.drawable.ic_content_copy, 20, Theme.TEXT_MAIN));
        exportRow.addView(cText("   Ekspor Transkrip", 14f, Theme.TEXT_MAIN, true, false));
        exportRow.setOnClickListener(v -> {
            dialog.dismiss();
            exportSessionById(convId, currentTitle);
        });
        root.addView(exportRow, new LinearLayout.LayoutParams(-1, -2));

        dialog.setContentView(root);
        dialog.show();
    }

    private void exportSessionById(String convId, String title) {
        String endpoint = prefs.getString("url", "").trim();
        if (endpoint.isEmpty() || convId == null || convId.isEmpty()) {
            Toast.makeText(this, "Tidak ada data sesi untuk diekspor", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Mengunduh transkrip...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                String transcriptUrl = endpoint.replace("/api/chat", "/api/session/transcript") + "?id=" + BridgeClient.encode(convId);
                HttpURLConnection c = (HttpURLConnection) new URL(transcriptUrl).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(8000);
                String token = prefs.getString("token", "");
                if (!token.isEmpty()) {
                    c.setRequestProperty("Authorization", "Bearer " + token);
                }

                if (c.getResponseCode() == 200) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder b = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) b.append(line);
                    JSONObject json = new JSONObject(b.toString());
                    JSONArray turns = json.optJSONArray("turns");
                    if (turns == null) turns = json.optJSONArray("messages");

                    StringBuilder out = new StringBuilder();
                    out.append("# ").append(title != null ? title : "Session").append("\n");
                    out.append("ID: ").append(convId).append("\n\n");
                    if (turns != null) {
                        for (int i = 0; i < turns.length(); i++) {
                            JSONObject t = turns.optJSONObject(i);
                            if (t == null) continue;
                            String role = t.optString("role", "unknown");
                            String content = t.optString("content", "");
                            out.append("### ").append(role.toUpperCase()).append(":\n").append(content).append("\n\n");
                        }
                    }

                    mainHandler.post(() -> {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("Codex Transcript", out.toString()));
                            Toast.makeText(MainActivity.this, "Transkrip disalin ke clipboard", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Gagal mengekspor: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showMoreDropdownMenu(View anchorView) {
        final PopupWindow popupWindow = new PopupWindow(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 20));
        root.setPadding(dp(6), dp(8), dp(6), dp(8));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setElevation(dp(18));
        }

        // 0. File Explorer & Quick Terminal
        addCustomPopupItem(root, "File Explorer", R.drawable.ic_folder, Theme.ACCENT, () -> {
            popupWindow.dismiss();
            openFileExplorerModal(".");
        });
        addCustomPopupItem(root, "Quick Terminal (PTY)", R.drawable.ic_code, Theme.GREEN, () -> {
            popupWindow.dismiss();
            openQuickTerminalModal();
        });

        // 1. Ubah Nama Sesi
        addCustomPopupItem(root, "Ubah Nama Sesi", R.drawable.ic_edit, Theme.TEXT_MAIN, () -> {
            popupWindow.dismiss();
            showRenameSessionDialog(activeConversationId, activeSessionTitle);
        });

        // 2. Ekspor Transkrip
        addCustomPopupItem(root, "Ekspor Transkrip", R.drawable.ic_content_copy, Theme.TEXT_MAIN, () -> {
            popupWindow.dismiss();
            exportActiveSession();
        });

        // 3. Refresh Transkrip
        addCustomPopupItem(root, "Refresh Transkrip", R.drawable.ic_refresh, Theme.TEXT_MAIN, () -> {
            popupWindow.dismiss();
            fetchActiveSessionTurns(true);
        });

        // 4. Bersihkan ke Sesi Baru
        addCustomPopupItem(root, "Bersihkan ke Sesi Baru", R.drawable.ic_add, Theme.ACCENT, () -> {
            popupWindow.dismiss();
            startNewSession();
        });

        // 5. Interrupt / Stop Task
        addCustomPopupItem(root, "Interrupt / Stop Task", R.drawable.ic_stop, Theme.RED, () -> {
            popupWindow.dismiss();
            stopRunningCliProcess();
        });

        // 6. Pengaturan
        addCustomPopupItem(root, "Pengaturan", R.drawable.ic_settings, Theme.TEXT_MUTED, () -> {
            popupWindow.dismiss();
            showScreen(2);
        });

        popupWindow.setContentView(root);
        popupWindow.setWidth(dp(220));
        popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        int xOffset = -dp(180);
        int yOffset = dp(6);
        popupWindow.showAsDropDown(anchorView, xOffset, yOffset);
    }

    private void addCustomPopupItem(LinearLayout container, String title, int iconRes, int tint, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(cBox(Color.TRANSPARENT, 0, 0, 12));

        ImageView icon = cIcon(iconRes, 18, tint);
        row.addView(icon);

        int textColor = tint == Theme.RED ? Theme.RED : (tint == Theme.ACCENT ? Theme.ACCENT : Theme.TEXT_MAIN);
        TextView text = cText("   " + title, 13.5f, textColor, false, false);
        text.setSingleLine(true);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));

        row.setOnClickListener(v -> onClick.run());
        container.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addDropdownItem(PopupMenu popup, int id, String title, int iconRes, int tint) {
        MenuItem item = popup.getMenu().add(0, id, id, title);
        Drawable icon = getResources().getDrawable(iconRes, getTheme());
        if (icon != null) {
            icon = icon.mutate();
            icon.setColorFilter(new PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN));
            item.setIcon(icon);
        }
    }

    // PopupMenu hides icons by default; setForceShowIcon only exists from API 29.
    private void forceShowPopupIcons(PopupMenu popup) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popup.setForceShowIcon(true);
            return;
        }
        try {
            Field field = PopupMenu.class.getDeclaredField("mPopup");
            field.setAccessible(true);
            Object helper = field.get(popup);
            helper.getClass()
                    .getDeclaredMethod("setForceShowIcon", boolean.class)
                    .invoke(helper, true);
        } catch (Throwable ignored) {}
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
                Toast.makeText(this, "Izin kamera ditolak. Pilih gambar QR dari galeri.", Toast.LENGTH_LONG).show();
                pickQrImageFromGallery();
            }
        }
    }

    private void showNativeQrScannerModal() {
        try {
            final Dialog dialog = createBaseBottomSheet(true);
            LinearLayout root = createBottomSheetRoot(dialog, "Scan QR Code Pairing", true);

            TextView sub = cText("Arahkan kamera ke QR Code di terminal (agy-pair)", 12.5f, Theme.TEXT_MUTED, false, false);
            LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(-1, -2);
            lpSub.setMargins(0, 0, 0, dp(14));
            root.addView(sub, lpSub);

            FrameLayout frame = new FrameLayout(this);
            frame.setBackground(cBox(Color.BLACK, Theme.ACCENT, 2, 16));
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

            LinearLayout galleryBtn = new LinearLayout(this);
            galleryBtn.setOrientation(LinearLayout.HORIZONTAL);
            galleryBtn.setGravity(Gravity.CENTER);
            galleryBtn.setBackground(cBox(Theme.ACCENT_SOFT, Theme.ACCENT, 1, 12));
            galleryBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
            galleryBtn.addView(cIcon(R.drawable.ic_image, 18, Theme.ACCENT));
            galleryBtn.addView(cText("  Ambil QR dari Galeri", 13, Theme.ACCENT, true, false));
            galleryBtn.setOnClickListener(v -> {
                // Release the camera before handing off to the picker.
                dialog.dismiss();
                pickQrImageFromGallery();
            });
            LinearLayout.LayoutParams lpGallery = new LinearLayout.LayoutParams(-1, dp(46));
            lpGallery.setMargins(0, 0, 0, dp(10));
            root.addView(galleryBtn, lpGallery);

            LinearLayout clipBtn = new LinearLayout(this);
            clipBtn.setOrientation(LinearLayout.HORIZONTAL);
            clipBtn.setGravity(Gravity.CENTER);
            clipBtn.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
            clipBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
            clipBtn.addView(cIcon(R.drawable.ic_content_paste, 18, Theme.TEXT_MAIN));
            TextView clipLbl = cText("  Tempel dari Clipboard", 13, Theme.TEXT_MAIN, true, false);
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
            Toast.makeText(this, "Kamera tidak tersedia. Pilih gambar QR dari galeri.", Toast.LENGTH_LONG).show();
            pickQrImageFromGallery();
        }
    }

    /** Opens the document picker; SAF grants read access without a permission. */
    private void pickQrImageFromGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/png", "image/jpeg", "image/webp", "image/*"});
            startActivityForResult(Intent.createChooser(intent, "Pilih gambar QR"), REQ_PICK_QR_IMAGE);
        } catch (Throwable t) {
            try {
                Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                fallback.setType("image/*");
                startActivityForResult(fallback, REQ_PICK_QR_IMAGE);
            } catch (Throwable inner) {
                Toast.makeText(this, "Tidak ada aplikasi galeri yang bisa dibuka", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void decodeQrFromImage(final Uri uri) {
        Toast.makeText(this, "Membaca QR dari gambar...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            final String text = QrImageDecoder.decode(getContentResolver(), uri);
            mainHandler.post(() -> {
                if (text == null || text.isEmpty()) {
                    showQrImageFailure();
                } else {
                    handleQrPayload(text);
                }
            });
        });
    }

    // A failed decode is usually a cropping or resolution problem, so say what
    // to try instead of just reporting failure.
    private void showQrImageFailure() {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "QR Tidak Terbaca", true);

        root.addView(cText("Tidak menemukan QR code pada gambar itu.", 14f, Theme.TEXT_MAIN, true, false));
        TextView tips = cText("Coba: potong gambar sampai QR memenuhi bingkai, pakai screenshot asli "
                        + "(bukan foto layar), atau perbesar terminal sebelum mengambil gambar.",
                12.5f, Theme.TEXT_MUTED, false, false);
        tips.setPadding(0, dp(8), 0, dp(16));
        root.addView(tips);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView retry = cText("Pilih lagi", 14f, Theme.TEXT_MAIN, true, false);
        retry.setGravity(Gravity.CENTER);
        retry.setPadding(dp(16), dp(13), dp(16), dp(13));
        retry.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        retry.setOnClickListener(v -> {
            dialog.dismiss();
            pickQrImageFromGallery();
        });
        LinearLayout.LayoutParams lpR = new LinearLayout.LayoutParams(0, -2, 1);
        lpR.setMargins(0, 0, dp(8), 0);
        actions.addView(retry, lpR);

        TextView paste = cText("Tempel token", 14f, Theme.ON_ACCENT, true, false);
        paste.setGravity(Gravity.CENTER);
        paste.setPadding(dp(16), dp(13), dp(16), dp(13));
        paste.setBackground(cBox(Theme.ACCENT, 0, 0, 12));
        paste.setOnClickListener(v -> {
            dialog.dismiss();
            pasteFromClipboard();
        });
        actions.addView(paste, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);

        dialog.setContentView(root);
        dialog.show();
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

    private String normalizeEndpointUrl(String rawUrl) {
        if (rawUrl == null) return "";
        String u = rawUrl.trim();
        if (u.isEmpty()) return "";

        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            if (u.contains("trycloudflare.com") || u.contains("cloudflare") || u.contains("ngrok") || u.contains(".app") || u.contains(".dev")) {
                u = "https://" + u;
            } else {
                u = "http://" + u;
            }
        }

        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }

        if (!u.endsWith("/api/chat")) {
            if (u.endsWith("/api")) {
                u = u + "/chat";
            } else {
                u = u + "/api/chat";
            }
        }

        return u;
    }

    private void saveConnectionCredentials(String url, String token, String engine) {
        String cleanUrl = normalizeEndpointUrl(url);
        prefs.edit()
                .putString("url", cleanUrl)
                .putString("token", token != null ? token.trim() : "")
                .putString("engine", engine != null ? engine.trim() : "antigravity")
                .apply();

        currentEngine = engine != null ? engine.trim() : "antigravity";
        currentModel = prefs.getString(modelPrefKey(currentEngine), defaultModelForEngine(currentEngine));
        saveServerProfile(currentServerHostname, cleanUrl, token != null ? token.trim() : "");
        updateRepoTag();
        refreshSettingsValues();
        restartLiveEvents();
        startNewSession();
        Toast.makeText(this, "Berhasil terhubung ke Server!", Toast.LENGTH_LONG).show();
        checkHealth();
        fetchHubSessions();
    }

    // ============================================================
    // MULTI-FILE & MULTI-IMAGE SELECTION & TRAY PREVIEW
    // ============================================================
    private void openMultiFilePicker() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 24));
        root.setPadding(dp(20), dp(18), dp(20), dp(20));

        View pill = new View(this);
        pill.setBackground(cBox(Theme.SURFACE_MUTED, 0, 0, 3));
        LinearLayout.LayoutParams lpPill = new LinearLayout.LayoutParams(dp(40), dp(5));
        lpPill.gravity = Gravity.CENTER_HORIZONTAL;
        lpPill.setMargins(0, 0, 0, dp(14));
        root.addView(pill, lpPill);

        TextView title = cText("Lampirkan File & Gambar", 16f, Theme.TEXT_MAIN, true, false);
        root.addView(title);

        TextView desc = cText("Kirim foto screenshot, error log, atau file kode ke AI", 12.5f, Theme.TEXT_MUTED, false, false);
        desc.setPadding(0, dp(4), 0, dp(14));
        root.addView(desc);

        // 1. Galeri Foto
        LinearLayout optGallery = createAttachmentOptionRow("🖼️  Pilih dari Galeri (Foto / Screenshot)", "Format PNG, JPG, WebP");
        optGallery.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(Intent.createChooser(intent, "Pilih Gambar"), REQ_PICK_FILES);
            } catch (Exception ex) {
                Toast.makeText(MainActivity.this, "Gagal membuka galeri", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(optGallery);

        // 2. Kamera
        LinearLayout optCamera = createAttachmentOptionRow("📷  Ambil Foto dengan Kamera", "Foto langsung dari layar / objek");
        optCamera.setOnClickListener(v -> {
            dialog.dismiss();
            try {
                Intent takePictureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                File photoFile = new File(getCacheDir(), "camera_" + System.currentTimeMillis() + ".jpg");
                cameraCaptureUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", photoFile);
                takePictureIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraCaptureUri);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(takePictureIntent, REQ_CAMERA_CAPTURE);
            } catch (Exception ex) {
                Toast.makeText(MainActivity.this, "Error kamera: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(optCamera);

        // 3. Semua Dokumen & File
        LinearLayout optFiles = createAttachmentOptionRow("📄  Pilih Dokumen / File Lainnya", "Format sembarang (txt, json, code, dll)");
        optFiles.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(Intent.createChooser(intent, "Pilih File"), REQ_PICK_FILES);
            } catch (Exception ex) {
                Toast.makeText(MainActivity.this, "Gagal membuka file picker", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(optFiles);

        dialog.setContentView(root);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.show();
    }

    private LinearLayout createAttachmentOptionRow(String titleStr, String subStr) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 14));
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setClickable(true);
        row.setFocusable(true);

        TextView t = cText(titleStr, 14f, Theme.TEXT_MAIN, true, false);
        row.addView(t);

        TextView s = cText(subStr, 11.5f, Theme.TEXT_MUTED, false, false);
        s.setPadding(0, dp(2), 0, 0);
        row.addView(s);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(lp);
        return row;
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
            } else if (requestCode == REQ_CAMERA_CAPTURE) {
                if (cameraCaptureUri != null) {
                    ArrayList<Uri> uris = new ArrayList<>();
                    uris.add(cameraCaptureUri);
                    uploadMultipleSelectedFiles(uris);
                }
            } else if (requestCode == REQ_PICK_QR_IMAGE) {
                Uri picked = data.getData();
                if (picked != null) {
                    decodeQrFromImage(picked);
                } else {
                    Toast.makeText(this, "Tidak ada gambar yang dipilih", Toast.LENGTH_SHORT).show();
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
            chip.setBackground(cBox(Theme.SURFACE, Theme.ACCENT, 1, 14));
            chip.setPadding(dp(6), dp(4), dp(8), dp(4));

            if (m.isImage && m.bitmap != null) {
                ImageView thumb = new ImageView(this);
                thumb.setImageBitmap(m.bitmap);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.setBackground(cBox(Theme.SURFACE_MUTED, 0, 0, 8));
                LinearLayout.LayoutParams lpTh = new LinearLayout.LayoutParams(dp(30), dp(30));
                thumb.setLayoutParams(lpTh);
                chip.addView(thumb);
            } else {
                ImageView docIcon = cIcon(R.drawable.ic_attach_file, 20, Theme.ACCENT);
                chip.addView(docIcon);
            }

            TextView nameView = cText(" " + (m.fileName.length() > 18 ? m.fileName.substring(0, 15) + "..." : m.fileName), 12f, Theme.ACCENT, true, false);
            nameView.setPadding(dp(4), 0, dp(4), 0);
            nameView.setSingleLine(true);
            chip.addView(nameView);

            ImageView closeBtn = cIconButton(R.drawable.ic_close, 14, 24, Theme.TEXT_MUTED);
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
        final int sendEpoch = sessionEpoch;

        executor.execute(() -> {
            try {
                boolean isNewSession = (activeConversationId == null || activeConversationId.isEmpty());
                JSONObject req = new JSONObject();
                req.put("prompt", promptToSend);
                req.put("engine", currentEngine);
                req.put("model", currentModel);
                req.put("resume", !isNewSession);

                if (!filePathsToSend.isEmpty()) {
                    JSONArray arr = new JSONArray();
                    for (String f : filePathsToSend) arr.put(f);
                    req.put("attachedFiles", arr);
                    req.put("attachedFile", filePathsToSend.get(0));
                }

                if (!isNewSession) {
                    req.put("conversationId", activeConversationId);
                }

                // Ask for a job so the run survives this request: a dropped
                // tunnel or a locked phone no longer loses the work.
                req.put("async", true);

                JSONObject accepted = executePost(endpoint, prefs.getString("token", ""), req);
                final String jobId = accepted.optString("jobId", "");
                // Tag the run as ours so live syncing can tell it apart from
                // anything else happening on the server.
                if (!jobId.isEmpty()) mainHandler.post(() -> activeJobId = jobId);

                JSONObject res = jobId.isEmpty()
                        ? accepted                       // server predates jobs: it already ran
                        : awaitJobResult(jobId);

                final String activeId = res.optString("conversationId", "");
                if (!activeId.isEmpty()) {
                    mainHandler.post(() -> {
                        if (sendEpoch == sessionEpoch) adoptConversationId(activeId);
                    });
                }

                final JSONObject finalRes = res;
                final int epochAtSend = sendEpoch;
                mainHandler.post(() -> {
                    activeJobId = null;
                    isLiveTaskRunning = false;
                    pendingOptimisticUserPrompt = null;
                    btnSend.setTag(null);
                    btnSend.setEnabled(true);
                    promptInput.setEnabled(true);

                    String error = finalRes.optString("error", "");
                    if (!error.isEmpty()) {
                        Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                    // The user may have opened another session while this ran;
                    // painting the result now would swap the transcript underneath them.
                    if (epochAtSend != sessionEpoch) return;
                    renderActiveSessionTurns(activeConversationId, finalRes, false);
                });
            } catch (Exception e) {
                final String err = e.getMessage() != null ? e.getMessage() : "Koneksi gateway terputus.";
                mainHandler.post(() -> {
                    activeJobId = null;
                    isLiveTaskRunning = false;
                    liveStreamingAssistantText = "";
                    lastFailedPrompt = promptToSend;
                    btnSend.setTag(null);
                    btnSend.setEnabled(true);
                    promptInput.setEnabled(true);
                    renderRetryFailedMessageBlock(promptToSend, err);
                });
            }
        });
    }

    /**
     * Polls a background job until it settles. SSE already streams progress, so
     * this only has to notice the end state; it backs off to stay cheap during
     * a long run, and gives up well after the server's own task timeout.
     */
    private JSONObject awaitJobResult(String jobId) {
        long deadline = System.currentTimeMillis() + 4L * 60 * 60 * 1000;
        int delayMs = 1500;

        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            delayMs = Math.min(delayMs + 500, 8000);

            try {
                JSONObject status = bridge.get("/api/jobs/" + BridgeClient.encode(jobId), 15000);
                JSONObject job = status.optJSONObject("job");
                if (job == null) continue;

                String state = job.optString("state", "running");
                if ("running".equals(state)) continue;

                JSONObject result = new JSONObject();
                result.put("ok", "done".equals(state));
                result.put("conversationId", job.optString("conversationId", ""));
                result.put("response", job.optString("response", ""));
                if (job.has("turns")) result.put("turns", job.opt("turns"));
                if (job.has("session")) result.put("session", job.opt("session"));
                if (!job.isNull("error")) result.put("error", job.optString("error", ""));
                return result;
            } catch (Exception ignored) {
                // Network blip: keep waiting, the job runs on the server.
            }
        }

        try {
            return new JSONObject().put("ok", false).put("error", "Task masih berjalan di server. Buka lagi nanti.");
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void syncLiveExecution() {
        if (!bridge.isPaired()) {
            mainHandler.post(this::hideSessionLoading);
            return;
        }

        final String targetConvId = activeConversationId;
        final String jobId = activeJobId;
        final int epoch = sessionEpoch;

        // With no conversation and no job of our own there is nothing to sync.
        // This used to fall back to /api/session/live, which reports whatever
        // ran most recently on the server — so a brand-new chat would adopt and
        // display a stranger's transcript for a moment.
        if ((targetConvId == null || targetConvId.isEmpty()) && (jobId == null || jobId.isEmpty())) {
            mainHandler.post(this::hideSessionLoading);
            return;
        }

        executor.execute(() -> {
            try {
                if (targetConvId != null && !targetConvId.isEmpty()) {
                    JSONObject json = bridge.get(
                            "/api/session/transcript?id=" + BridgeClient.encode(targetConvId), 8000);
                    mainHandler.post(() -> applySyncedTranscript(epoch, targetConvId, json));
                    return;
                }

                // A new session: only our own job can tell us which conversation
                // this turned into.
                JSONObject status = bridge.get("/api/jobs/" + BridgeClient.encode(jobId), 8000);
                JSONObject job = status.optJSONObject("job");
                final String discovered = job == null ? "" : job.optString("conversationId", "");
                if (discovered.isEmpty()) {
                    mainHandler.post(this::hideSessionLoading);
                    return;
                }

                JSONObject json = bridge.get(
                        "/api/session/transcript?id=" + BridgeClient.encode(discovered), 8000);
                mainHandler.post(() -> {
                    if (epoch != sessionEpoch) return;
                    adoptConversationId(discovered);
                    applySyncedTranscript(epoch, discovered, json);
                });
            } catch (Exception ignored) {
                mainHandler.post(this::hideSessionLoading);
            }
        });
    }

    /** Drops a response that arrived after the user moved to another session. */
    private void applySyncedTranscript(int epoch, String convId, JSONObject json) {
        hideSessionLoading();
        if (epoch != sessionEpoch) return;
        if (convId != null && activeConversationId != null && !convId.equals(activeConversationId)) return;
        renderActiveSessionTurns(convId, json, false);
    }

    private void adoptConversationId(String convId) {
        if (convId == null || convId.isEmpty()) return;
        if (activeConversationId != null && !activeConversationId.isEmpty()) return;
        activeConversationId = convId;
        activeSessionEngine = currentEngine;
        prefs.edit()
                .putString("last_conversation_id", convId)
                .putString("last_conversation_engine", currentEngine)
                .apply();
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

    /**
     * True when a transcript turn is the prompt we optimistically rendered.
     *
     * The old test allowed containment in both directions, so an empty turn —
     * which every string contains — counted as a match. The optimistic bubble
     * was then dropped and replaced by the blank one, and the user's text only
     * came back once the real turn reached the transcript.
     */
    private boolean matchesPendingPrompt(String content) {
        if (pendingOptimisticUserPrompt == null) return false;
        String pending = pendingOptimisticUserPrompt.trim();
        String candidate = content == null ? "" : content.trim();
        if (pending.isEmpty() || candidate.isEmpty()) return false;
        if (pending.equals(candidate)) return true;
        // Attachment headers are prepended to what we render, so the stored
        // turn can be the shorter of the two — but it must still be substantial.
        int shorter = Math.min(pending.length(), candidate.length());
        if (shorter < 8) return false;
        return pending.contains(candidate) || candidate.contains(pending);
    }

    private void renderActiveSessionTurns(String requestedConvId, JSONObject json, boolean showToast) {
        try {
            hideSessionLoading();
            if (json == null) return;

            JSONObject session = json.optJSONObject("session");
            if (session != null) {
                activeSessionTitle = session.optString("title", activeSessionTitle);
                if (requestedConvId != null && !requestedConvId.isEmpty()) {
                    prefs.edit().putLong("session_accessed_" + requestedConvId, System.currentTimeMillis()).apply();
                }
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

                // A running task must never be repainted from an empty transcript.
                // The Codex rollout is read while it is still being written, so a
                // poll can momentarily come back with nothing; rebuilding from
                // that wipes the message the user just sent, and it only returns
                // once the transcript is complete again.
                if (newTurnCount == 0 && isLiveTaskRunning && chatMessagesList.getChildCount() > 0) {
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
                        if (matchesPendingPrompt(content)) {
                            foundOptimisticInTranscript = true;
                        }
                        // A blank turn is never worth a bubble, and rendering one
                        // here is what made the typed message appear to vanish.
                        if (!content.trim().isEmpty()) {
                            renderUserMessageBlock(content, time);
                        }
                    } else {
                        if (!pendingTools.isEmpty()) {
                            renderInlineStepPill(new ArrayList<>(pendingTools), false);
                            pendingTools.clear();
                        }
                        if (!content.trim().isEmpty() || i == lastAssistantIdx) {
                            renderAssistantMessageBlock(content, time, (i == lastAssistantIdx));
                        }
                    }
                }

                // Deliberately not cleared here. It used to be dropped the first
                // time the transcript contained it, which left nothing to fall
                // back on if a later poll returned a partial transcript. It is
                // cleared when the task actually finishes.
                if (isLiveTaskRunning && pendingOptimisticUserPrompt != null && !foundOptimisticInTranscript) {
                    if (!pendingTools.isEmpty()) {
                        renderInlineStepPill(new ArrayList<>(pendingTools), false);
                        pendingTools.clear();
                    }
                    renderUserMessageBlock(pendingOptimisticUserPrompt, pendingOptimisticUserTime);
                }

                if (isLiveTaskRunning && liveStreamingAssistantText != null && !liveStreamingAssistantText.trim().isEmpty()) {
                    renderAssistantMessageBlock(liveStreamingAssistantText + " ▊", "Mengetik...", true);
                }
                if (!pendingTools.isEmpty()) {
                    renderInlineStepPill(pendingTools, isLiveTaskRunning);
                } else if (isLiveTaskRunning && (liveStreamingAssistantText == null || liveStreamingAssistantText.trim().isEmpty())) {
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
            iv.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 14));
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
            bubble.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 18));
            bubble.setPadding(dp(16), dp(12), dp(16), dp(12));

            TextView tv = new TextView(this);
            tv.setText(userPromptText);
            tv.setTextSize(15.5f);
            tv.setTextColor(Theme.TEXT_MAIN);
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
                label.setSpan(new ForegroundColorSpan(Theme.TEXT_MAIN), start, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (editCount == 1 && !singleFilename.isEmpty()) {
                label.append("Mengedit ");
                int start = label.length();
                label.append(singleFilename);
                label.setSpan(new ForegroundColorSpan(Theme.TEXT_MAIN), start, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
        pillText.setTextColor(Theme.TEXT_MUTED);
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
                TextView addView = cText("+" + totalAdded + " ", 13f, Theme.GREEN, true, false);
                diffBadge.addView(addView);
            }
            if (totalDeleted > 0) {
                TextView delView = cText("-" + totalDeleted + " ", 13f, Theme.RED, true, false);
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

        ImageView chevron = cIcon(R.drawable.ic_chevron_right, 14, Theme.TEXT_LIGHT);
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
            imgThumb.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 14));
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
            copyBtn.setBackground(cBox(Theme.SURFACE, Theme.BORDER, 1, 14));
            copyBtn.setPadding(dp(10), dp(5), dp(12), dp(5));

            ImageView copyIcon = cIcon(R.drawable.ic_content_copy, 14, Theme.TEXT_MUTED);
            copyBtn.addView(copyIcon);

            final TextView copyLabel = cText(" Salin", 12f, Theme.TEXT_MUTED, true, false);
            copyBtn.addView(copyLabel);

            final String fullTextToCopy = cleanMarkdownForCopy(content.trim());
            copyBtn.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Assistant Response", fullTextToCopy));
                Toast.makeText(MainActivity.this, "Jawaban disalin ke clipboard", Toast.LENGTH_SHORT).show();
                copyLabel.setText(" Tersalin ✓");
                copyLabel.setTextColor(Theme.GREEN);
                mainHandler.postDelayed(() -> {
                    copyLabel.setText(" Salin");
                    copyLabel.setTextColor(Theme.TEXT_MUTED);
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
                imgPreview.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
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

    // Markdown rendering lives in MarkdownRenderer.java.
    private void renderMarkdownIntoContainer(LinearLayout container, String markdown, boolean isUser) {
        if (markdownRenderer == null) markdownRenderer = new MarkdownRenderer(this);
        markdownRenderer.render(container, markdown, isUser);
    }

    // ============================================================
    // SERVER SWITCHER (multi VPS)
    // ============================================================
    private JSONArray loadSavedServers() {
        try {
            return new JSONArray(prefs.getString("servers", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveServerProfile(String name, String url, String token) {
        try {
            JSONArray list = loadSavedServers();
            for (int i = 0; i < list.length(); i++) {
                JSONObject s = list.optJSONObject(i);
                if (s != null && url.equals(s.optString("url"))) {
                    s.put("name", name);
                    s.put("token", token);
                    prefs.edit().putString("servers", list.toString()).apply();
                    return;
                }
            }
            list.put(new JSONObject().put("name", name).put("url", url).put("token", token));
            prefs.edit().putString("servers", list.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void showServerSwitcher() {
        final Dialog dialog = createBaseBottomSheet(true);
        final LinearLayout root = createBottomSheetRoot(dialog, "Server Tersimpan", true);

        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, new LinearLayout.LayoutParams(-1, -2));

        renderServerList(list, dialog);

        TextView hint = cText("Ketuk untuk beralih · ketuk ikon hapus untuk membuang server.",
                12f, Theme.TEXT_LIGHT, false, false);
        hint.setPadding(0, dp(14), 0, 0);
        root.addView(hint);

        dialog.setContentView(root);
        dialog.show();
    }

    private void renderServerList(final LinearLayout list, final Dialog dialog) {
        list.removeAllViews();

        JSONArray servers = loadSavedServers();
        final String activeUrl = prefs.getString("url", "");

        if (servers.length() == 0) {
            list.addView(cText("Belum ada server tersimpan. Pairing lewat QR akan menyimpannya otomatis.",
                    13.5f, Theme.TEXT_MUTED, false, false));
            return;
        }

        for (int i = 0; i < servers.length(); i++) {
            JSONObject s = servers.optJSONObject(i);
            if (s == null) continue;
            final String url = s.optString("url");
            final String token = s.optString("token");
            final String name = s.optString("name", "Server");
            final boolean isActive = url.equals(activeUrl);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(cBox(Theme.SURFACE_MUTED, isActive ? Theme.ACCENT : Theme.BORDER, 1, 14));
            card.setPadding(dp(14), dp(10), dp(8), dp(10));

            card.addView(cIcon(R.drawable.ic_laptop, 18, isActive ? Theme.ACCENT : Theme.TEXT_MUTED));

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(12), 0, dp(8), 0);

            LinearLayout titleRow = new LinearLayout(this);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.addView(cText(name, 14f, Theme.TEXT_MAIN, true, false));
            if (isActive) {
                TextView chip = cText("Aktif", 10.5f, Theme.ACCENT, true, false);
                chip.setBackground(cBox(Theme.ACCENT_SOFT, 0, 0, 8));
                chip.setPadding(dp(8), dp(2), dp(8), dp(2));
                LinearLayout.LayoutParams lpChip = new LinearLayout.LayoutParams(-2, -2);
                lpChip.setMargins(dp(8), 0, 0, 0);
                titleRow.addView(chip, lpChip);
            }
            col.addView(titleRow);

            TextView urlView = cText(url, 11.5f, Theme.TEXT_LIGHT, false, false);
            urlView.setSingleLine(true);
            urlView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            col.addView(urlView);
            card.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

            ImageView remove = cIconButton(R.drawable.ic_close, 16, 38, Theme.TEXT_LIGHT);
            remove.setOnClickListener(v -> confirmRemoveServer(name, url, isActive, list, dialog));
            card.addView(remove);

            card.setOnClickListener(v -> {
                if (isActive) {
                    Toast.makeText(this, name + " sudah aktif", Toast.LENGTH_SHORT).show();
                    return;
                }
                switchToServer(name, url, token);
                dialog.dismiss();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(8), 0, 0);
            list.addView(card, lp);
        }
    }

    private void switchToServer(String name, String url, String token) {
        prefs.edit().putString("url", url).putString("token", token).apply();
        currentServerHostname = name;
        Toast.makeText(this, "Beralih ke " + name, Toast.LENGTH_SHORT).show();

        restartLiveEvents();
        startNewSession();
        showScreen(0);
        checkHealth();
    }

    // Removing the active server would leave the app pointing at nothing, so
    // that case is called out rather than silently disconnecting.
    private void confirmRemoveServer(final String name, final String url, final boolean isActive,
                                     final LinearLayout list, final Dialog parent) {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Hapus Server", true);

        root.addView(cText("Hapus \"" + name + "\" dari daftar?", 14.5f, Theme.TEXT_MAIN, true, false));
        TextView detail = cText(isActive
                        ? "Ini server yang sedang aktif. Setelah dihapus aplikasi tidak terhubung ke mana pun sampai Anda pairing lagi."
                        : url,
                12.5f, isActive ? Theme.AMBER : Theme.TEXT_MUTED, false, false);
        detail.setPadding(0, dp(6), 0, dp(16));
        root.addView(detail);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView cancel = cText("Batal", 14f, Theme.TEXT_MAIN, true, false);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(16), dp(13), dp(16), dp(13));
        cancel.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(0, -2, 1);
        lpC.setMargins(0, 0, dp(8), 0);
        actions.addView(cancel, lpC);

        TextView confirm = cText("Hapus", 14f, Color.WHITE, true, false);
        confirm.setGravity(Gravity.CENTER);
        confirm.setPadding(dp(16), dp(13), dp(16), dp(13));
        confirm.setBackground(cBox(Theme.RED, 0, 0, 12));
        confirm.setOnClickListener(v -> {
            removeServerProfile(url);
            dialog.dismiss();
            if (isActive) {
                prefs.edit().remove("url").remove("token").apply();
                stopLiveEvents();
                if (parent != null) parent.dismiss();
                startNewSession();
                showScreen(0);
                Toast.makeText(this, "Server dihapus. Pairing lagi untuk terhubung.", Toast.LENGTH_LONG).show();
            } else {
                renderServerList(list, parent);
                Toast.makeText(this, "Server dihapus", Toast.LENGTH_SHORT).show();
            }
        });
        actions.addView(confirm, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);

        dialog.setContentView(root);
        dialog.show();
    }

    private void removeServerProfile(String url) {
        try {
            JSONArray current = loadSavedServers();
            JSONArray next = new JSONArray();
            for (int i = 0; i < current.length(); i++) {
                JSONObject s = current.optJSONObject(i);
                if (s == null || url.equals(s.optString("url"))) continue;
                next.put(s);
            }
            prefs.edit().putString("servers", next.toString()).apply();
        } catch (Exception ignored) {}
    }

    // ============================================================
    // SANDBOX MODE (server-side execution policy)
    // ============================================================
    private void showSandboxPicker() {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Mode Eksekusi", true);
        root.addView(cText("Menentukan seberapa bebas CLI boleh mengubah sistem di server.",
                12.5f, Theme.TEXT_MUTED, false, false));

        final String[] keys = {"full", "workspace", "readonly"};
        final String[] labels = {"Akses penuh", "Tulis di workspace", "Hanya baca"};
        final String[] notes = {
                "Tanpa sandbox. Paling cepat, paling berisiko.",
                "Boleh menulis di workdir saja.",
                "Tidak boleh mengubah file apa pun."
        };

        final String current = prefs.getString("sandbox_mode", "full");

        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            boolean selected = key.equals(current);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(cBox(Theme.SURFACE_MUTED, selected ? Theme.ACCENT : Theme.BORDER, 1, 14));
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.addView(cText((selected ? "✓  " : "") + labels[i], 14.5f,
                    selected ? Theme.ACCENT : Theme.TEXT_MAIN, true, false));
            card.addView(cText(notes[i], 12.5f, Theme.TEXT_MUTED, false, false));

            card.setOnClickListener(v -> {
                dialog.dismiss();
                applySandboxMode(key);
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(10), 0, 0);
            root.addView(card, lp);
        }

        dialog.setContentView(root);
        dialog.show();
    }

    private void applySandboxMode(final String mode) {
        executor.execute(() -> {
            try {
                JSONObject result = bridge.post("/api/settings", new JSONObject().put("sandboxMode", mode));
                mainHandler.post(() -> {
                    if (result.optBoolean("ok", false)) {
                        prefs.edit().putString("sandbox_mode", mode).apply();
                        refreshSettingsValues();
                        Toast.makeText(this, "Mode eksekusi: " + mode, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Server menolak perubahan", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception ex) {
                mainHandler.post(() -> Toast.makeText(this, "Gagal: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ============================================================
    // LIVE EVENTS (SSE) WIRING
    // ============================================================
    // The server owns the sandbox setting; mirror it so the UI cannot lie.
    private void syncServerSettings() {
        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/settings", 10000);
                JSONObject settings = json.optJSONObject("settings");
                if (settings == null) return;
                final String mode = settings.optString("sandboxMode", "full");
                mainHandler.post(() -> {
                    prefs.edit().putString("sandbox_mode", mode).apply();
                    refreshSettingsValues();
                });
            } catch (Exception ignored) {}
        });
    }

    // Reconnect in place. Stopping and immediately restarting a foreground
    // service races the system's startForeground deadline and force-closed the
    // app whenever the user switched servers.
    private void restartLiveEvents() {
        if (!bridge.isPaired()) {
            stopLiveEvents();
            return;
        }
        try {
            Intent intent = new Intent(this, LiveEventService.class);
            intent.setAction(LiveEventService.ACTION_RECONNECT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
            else startService(intent);
        } catch (Throwable ignored) {}
    }

    private void startLiveEvents() {
        if (!bridge.isPaired()) return;
        try {
            Intent intent = new Intent(this, LiveEventService.class);
            intent.setAction(LiveEventService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
            else startService(intent);
        } catch (Throwable ignored) {}
    }

    private void stopLiveEvents() {
        try {
            Intent intent = new Intent(this, LiveEventService.class);
            intent.setAction(LiveEventService.ACTION_STOP);
            startService(intent);
        } catch (Throwable ignored) {}
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION_PERMISSION);
            }
        }
    }

    // Events arrive on the SSE thread; everything below hops to the UI thread.
    private final LiveEventBus.Listener liveEventListener = (name, data) -> mainHandler.post(() -> {
        // Another device, or a task started from the terminal, can be running on
        // the same server. Only react to the job this screen started, otherwise
        // its transcript replaces the one the user is looking at.
        String eventJobId = data == null ? "" : data.optString("jobId", "");
        boolean isOurs = activeJobId != null && activeJobId.equals(eventJobId);

        if ("task.started".equals(name)) {
            if (isOurs) isLiveTaskRunning = true;
            return;
        }

        if ("task.finished".equals(name)) {
            if (!isOurs) return;
            isLiveTaskRunning = false;
            liveStreamingAssistantText = "";
            pendingOptimisticUserPrompt = null;
            if (currentScreen == 1) syncLiveExecution();
            if (!isAppInForeground) {
                boolean ok = data != null ? data.optBoolean("ok", true) : true;
                String err = data != null ? data.optString("error", "") : "";
                showTaskCompletionNotification(activeSessionTitle, ok, ok ? "AI telah selesai mengerjakan tugas." : err);
            }
            return;
        }

        if ("cli.event".equals(name) || "cli.output".equals(name)) {
            if (!isOurs) return;
            adoptConversationId(data != null ? data.optString("conversationId", "") : "");
            if ("cli.output".equals(name) && data != null) {
                String chunk = data.optString("chunk", "");
                if (!chunk.isEmpty()) {
                    liveStreamingAssistantText += chunk;
                }
            }
            scheduleThrottledSync();
        }
    });

    // ============================================================
    // SESSION EXPORT
    // ============================================================
    private void exportActiveSession() {
        final String convId = activeConversationId;
        if (convId == null || convId.isEmpty()) {
            Toast.makeText(this, "Belum ada sesi untuk diekspor", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Menyiapkan transkrip...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/session/export?id=" + BridgeClient.encode(convId), 60000);
                if (!json.optBoolean("ok", false)) {
                    mainHandler.post(() -> Toast.makeText(MainActivity.this,
                            describeApiError(json, "Gagal mengekspor"), Toast.LENGTH_LONG).show());
                    return;
                }

                final String markdown = json.optString("markdown", "");
                final String title = json.optString("title", "Sesi");
                final String filename = sanitizeExportName(json.optString("filename", "transkrip.md"));

                // Transcripts on this server reach two megabytes. Handing that to
                // Intent.putExtra blows past the Binder transaction limit and
                // takes the process down, so it goes out as a file instead.
                final File exported = writeExportFile(filename, markdown);
                if (exported == null) {
                    mainHandler.post(() -> copyTranscriptToClipboard(title, markdown));
                    return;
                }
                mainHandler.post(() -> shareExportedFile(exported, title));
            } catch (Exception ex) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this,
                        "Gagal: " + ex.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    /** Single path segment only — the provider serves that folder directly. */
    private String sanitizeExportName(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[^A-Za-z0-9._-]", "-");
        if (cleaned.isEmpty() || cleaned.startsWith(".")) cleaned = "transkrip.md";
        if (!cleaned.endsWith(".md")) cleaned = cleaned + ".md";
        return cleaned.length() > 80 ? cleaned.substring(cleaned.length() - 80) : cleaned;
    }

    private File writeExportFile(String filename, String markdown) {
        try {
            File dir = new File(getCacheDir(), "exports");
            if (!dir.exists() && !dir.mkdirs()) return null;

            // Earlier exports are throwaway; clear them so the cache does not
            // accumulate megabyte files.
            File[] previous = dir.listFiles();
            if (previous != null) {
                for (File old : previous) {
                    if (old.isFile()) old.delete();
                }
            }

            File target = new File(dir, filename);
            FileOutputStream out = new FileOutputStream(target);
            out.write(markdown.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
            return target;
        } catch (Throwable t) {
            return null;
        }
    }

    private void shareExportedFile(File file, String title) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);

            Intent share = new Intent(Intent.ACTION_SEND);
            // text/markdown hides most share targets; the file keeps its .md name.
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, title);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(share, "Bagikan transkrip");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
        } catch (Throwable t) {
            Toast.makeText(this, "Tidak bisa membagikan file: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Last resort when the cache is unwritable. The clipboard has limits of its
    // own, so a very long transcript is cut rather than dropped entirely.
    private void copyTranscriptToClipboard(String title, String markdown) {
        String payload = markdown.length() > 100000
                ? markdown.substring(0, 100000) + "\n\n… dipotong, transkrip terlalu panjang untuk clipboard"
                : markdown;
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(title, payload));
            Toast.makeText(this, "Transkrip disalin ke clipboard", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Gagal menyiapkan transkrip", Toast.LENGTH_LONG).show();
        }
    }
    // ============================================================
    // WORKSPACE PANELS (implemented in WorkspacePanels.java)
    // ============================================================
    private final WorkspacePanels.Host workspaceHost = new WorkspacePanels.Host() {
        @Override
        public Dialog createSheet() {
            return createBaseBottomSheet(true);
        }

        @Override
        public LinearLayout createSheetRoot(Dialog dialog, String title, boolean showClose) {
            return createBottomSheetRoot(dialog, title, showClose);
        }

        @Override
        public void renderMarkdown(LinearLayout container, String markdown) {
            renderMarkdownIntoContainer(container, markdown, false);
        }

        @Override
        public void openSession(String conversationId, String title) {
            navigatedFromHub = true;
            openSpecificSession(conversationId, title);
        }

        @Override
        public void onProjectChanged() {
            refreshSettingsValues();
        }
    };

    private void showFileBrowser(String startPath) {
        panels.showFileBrowser(startPath);
    }

    private void showGitPanel() {
        panels.showGitPanel();
    }

    private void showSearchPanel() {
        panels.showSearchPanel();
    }

    private void showProjectPicker() {
        panels.showProjectPicker();
    }

    private void showMaintenanceSheet() {
        panels.showMaintenanceSheet();
    }

    private String describeApiError(JSONObject json, String fallback) {
        return panels.describeApiError(json, fallback);
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
        final String normalizedUrl = normalizeEndpointUrl(endpoint);
        if (!normalizedUrl.equals(endpoint)) {
            prefs.edit().putString("url", normalizedUrl).apply();
        }
        executor.execute(() -> {
            try {
                String healthUrl = normalizedUrl.replace("/api/chat", "/health");
                HttpURLConnection c = (HttpURLConnection) new URL(healthUrl).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(6000);
                c.setReadTimeout(6000);
                String token = prefs.getString("token", "");
                if (!token.isEmpty()) {
                    c.setRequestProperty("Authorization", "Bearer " + token);
                }
                int code = c.getResponseCode();

                // /health needs no token, so a 200 there says nothing about auth.
                // Probe an authenticated route as well, otherwise a stale token
                // reads as "Gateway Online" while every screen stays empty.
                int authCode = 0;
                if (code == 200) {
                    try {
                        HttpURLConnection ac = bridge.open("/api/sessions", "GET", 8000);
                        authCode = ac.getResponseCode();
                        ac.disconnect();
                    } catch (Exception ignored) {}
                }
                final int authStatus = authCode;

                mainHandler.post(() -> {
                    if (code == 200 && (authStatus == 401 || authStatus == 403)) {
                        if (sidebarStatusDot != null) sidebarStatusDot.setBackground(cBox(Theme.AMBER, 0, 0, 4));
                        if (sidebarStatusText != null) sidebarStatusText.setText("Token ditolak");
                        Toast.makeText(this, "Server hidup, tapi token pairing ditolak. Scan ulang QR.", Toast.LENGTH_LONG).show();
                        showHubMessage("Pairing kedaluwarsa",
                                "Server menolak token ini (HTTP " + authStatus + "). Scan ulang QR pairing dari terminal server.", true);
                    } else if (code == 200) {
                        if (sidebarStatusDot != null) sidebarStatusDot.setBackground(cBox(Theme.GREEN, 0, 0, 4));
                        if (sidebarStatusText != null) sidebarStatusText.setText("Gateway Online");
                        Toast.makeText(this, "Gateway Online! Terhubung sukses.", Toast.LENGTH_SHORT).show();
                        syncServerSettings();
                        startLiveEvents();
                    } else {
                        if (sidebarStatusDot != null) sidebarStatusDot.setBackground(cBox(Theme.RED, 0, 0, 4));
                        if (sidebarStatusText != null) sidebarStatusText.setText("HTTP " + code);
                        Toast.makeText(this, "Gateway HTTP " + code, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (sidebarStatusDot != null) sidebarStatusDot.setBackground(cBox(Theme.RED, 0, 0, 4));
                    if (sidebarStatusText != null) sidebarStatusText.setText("Gateway Offline");
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    Toast.makeText(this, "Gagal koneksi: " + msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
