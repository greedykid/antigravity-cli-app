package com.greedykid.codexremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Dedicated Full-Screen Server Operations Dashboard with Real-Time
 * Hardware & Engine Metric Charts (CPU, RAM, Disk, System Load, Live Logs).
 */
public final class OperationsPanel {
    private final Activity act;
    private final BridgeClient bridge;
    private final ExecutorService executor;
    private final android.os.Handler mainHandler;
    private final Runnable onBackOrMenu;

    private boolean isScreenVisible = false;
    private boolean isAutoPollEnabled = true;
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScreenVisible && isAutoPollEnabled) {
                fetchHealthData(false);
                mainHandler.postDelayed(this, 3000);
            }
        }
    };

    // UI Bindings
    private ImageView refreshIcon;
    private TextView liveBadge;
    private TextView hostSubtext;

    // RAM Card Views
    private TextView ramPercentText;
    private TextView ramDetailText;
    private ProgressBar ramProgressBar;
    private SparklineChartView ramChart;

    // Disk Card Views
    private TextView diskPercentText;
    private TextView diskDetailText;
    private ProgressBar diskProgressBar;

    // CPU Card Views
    private TextView cpuModelText;
    private TextView cpuLoadText;
    private TextView uptimeText;
    private SparklineChartView cpuLoadChart;

    // Engine & Runtime Views
    private TextView agyEngineText;
    private TextView cdxEngineText;
    private TextView bridgeRuntimeText;
    private TextView jobsRunningText;
    private TextView workdirText;

    public OperationsPanel(Activity activity, BridgeClient client, ExecutorService executorService,
                           android.os.Handler handler, Runnable backOrMenuAction) {
        act = activity;
        bridge = client;
        executor = executorService;
        mainHandler = handler;
        onBackOrMenu = backOrMenuAction;
    }

    public void show() {
        // Compatibility method: if caller triggers show(), handled via navigation
        if (onBackOrMenu != null) onBackOrMenu.run();
    }

    public void onScreenShown() {
        isScreenVisible = true;
        fetchHealthData(true);
        mainHandler.removeCallbacks(pollRunnable);
        mainHandler.postDelayed(pollRunnable, 3000);
    }

    public void onScreenHidden() {
        isScreenVisible = false;
        mainHandler.removeCallbacks(pollRunnable);
    }

    public void buildOperationsScreen(FrameLayout root) {
        LinearLayout content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(10), dp(16), dp(14));

        // 1. Top Bar Header
        LinearLayout topBar = new LinearLayout(act);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, dp(4), 0, dp(12));

        ImageView backBtn = Theme.iconButton(act, R.drawable.ic_arrow_back, 24, 40, Theme.TEXT_MAIN);
        backBtn.setOnClickListener(v -> {
            if (onBackOrMenu != null) onBackOrMenu.run();
        });
        topBar.addView(backBtn);

        LinearLayout titleCol = new LinearLayout(act);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(dp(10), 0, dp(8), 0);

        TextView headerTitle = Theme.text(act, "Server Operations", 19, Theme.TEXT_MAIN, true, true);
        titleCol.addView(headerTitle);

        hostSubtext = Theme.text(act, "Live Gateway Monitoring", 11.5f, Theme.TEXT_MUTED, false, false);
        titleCol.addView(hostSubtext);
        topBar.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        // Live status pill toggle
        liveBadge = Theme.text(act, "● LIVE", 11f, Theme.GREEN, true, false);
        liveBadge.setBackground(Theme.box(act, Theme.SURFACE_MUTED, Theme.BORDER, 1, 10));
        liveBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        liveBadge.setOnClickListener(v -> toggleAutoPoll());
        LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(-2, -2);
        lpBadge.rightMargin = dp(8);
        topBar.addView(liveBadge, lpBadge);

        refreshIcon = Theme.iconButton(act, R.drawable.ic_refresh, 22, 40, Theme.TEXT_MAIN);
        refreshIcon.setOnClickListener(v -> {
            rotateRefreshIcon();
            fetchHealthData(true);
        });
        topBar.addView(refreshIcon);
        content.addView(topBar);

        // 2. Scrollable Body
        ScrollView scroll = new ScrollView(act);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);

        // Card 1: RAM & Memori Dashboard
        list.addView(buildRamCard());

        // Card 2: Penyimpanan Disk Dashboard
        list.addView(buildDiskCard());

        // Card 3: CPU & Beban Sistem
        list.addView(buildCpuCard());

        // Card 4: Engine & Runtime Gateway
        list.addView(buildEngineRuntimeCard());

        // Card 5: Alat & Aksi Cepat
        list.addView(buildActionToolsCard());

        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
    }

    private View buildRamCard() {
        LinearLayout card = createDashboardCard();

        LinearLayout head = createCardHeader(R.drawable.ic_analytics, "Memori (RAM)");
        ramPercentText = Theme.text(act, "--%", 14f, Theme.ACCENT, true, false);
        head.addView(ramPercentText);
        card.addView(head);

        ramDetailText = Theme.text(act, "Memuat penggunaan RAM...", 12.5f, Theme.TEXT_LIGHT, false, false);
        ramDetailText.setPadding(0, dp(4), 0, dp(8));
        card.addView(ramDetailText);

        ramProgressBar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        ramProgressBar.setMax(100);
        ramProgressBar.setProgress(0);
        ramProgressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Theme.ACCENT));
        ramProgressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Theme.SURFACE_MUTED));
        LinearLayout.LayoutParams lpBar = new LinearLayout.LayoutParams(-1, dp(8));
        lpBar.bottomMargin = dp(12);
        card.addView(ramProgressBar, lpBar);

        // Live Sparkline Chart
        ramChart = new SparklineChartView(act);
        ramChart.setColors(Theme.ACCENT, 0x33D96B43);
        ramChart.setRange(0f, 100f, false);
        LinearLayout.LayoutParams lpChart = new LinearLayout.LayoutParams(-1, dp(80));
        card.addView(ramChart, lpChart);

        return card;
    }

    private View buildDiskCard() {
        LinearLayout card = createDashboardCard();

        LinearLayout head = createCardHeader(R.drawable.ic_folder, "Penyimpanan Disk");
        diskPercentText = Theme.text(act, "--%", 14f, Theme.ACCENT, true, false);
        head.addView(diskPercentText);
        card.addView(head);

        diskDetailText = Theme.text(act, "Memuat statistik ruang disk...", 12.5f, Theme.TEXT_LIGHT, false, false);
        diskDetailText.setPadding(0, dp(4), 0, dp(8));
        card.addView(diskDetailText);

        diskProgressBar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        diskProgressBar.setMax(100);
        diskProgressBar.setProgress(0);
        diskProgressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF38BDF8));
        diskProgressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Theme.SURFACE_MUTED));
        LinearLayout.LayoutParams lpBar = new LinearLayout.LayoutParams(-1, dp(8));
        card.addView(diskProgressBar, lpBar);

        return card;
    }

    private View buildCpuCard() {
        LinearLayout card = createDashboardCard();

        LinearLayout head = createCardHeader(R.drawable.ic_laptop, "Prosesor & Beban Sistem");
        card.addView(head);

        cpuModelText = Theme.text(act, "CPU: Memuat...", 13f, Theme.TEXT_MAIN, true, false);
        cpuModelText.setPadding(0, dp(4), 0, dp(2));
        card.addView(cpuModelText);

        cpuLoadText = Theme.text(act, "Load: 1m: -- | 5m: -- | 15m: --", 12f, Theme.TEXT_LIGHT, false, false);
        card.addView(cpuLoadText);

        uptimeText = Theme.text(act, "Uptime Sistem: --", 12f, Theme.TEXT_MUTED, false, false);
        uptimeText.setPadding(0, dp(2), 0, dp(10));
        card.addView(uptimeText);

        cpuLoadChart = new SparklineChartView(act);
        cpuLoadChart.setColors(0xFF22C55E, 0x3322C55E);
        cpuLoadChart.setRange(0f, 2f, true);
        LinearLayout.LayoutParams lpChart = new LinearLayout.LayoutParams(-1, dp(75));
        card.addView(cpuLoadChart, lpChart);

        return card;
    }

    private View buildEngineRuntimeCard() {
        LinearLayout card = createDashboardCard();

        LinearLayout head = createCardHeader(R.drawable.ic_swap, "Runtime & CLI Engine");
        card.addView(head);

        agyEngineText = Theme.text(act, "• Antigravity CLI: Memuat...", 13f, Theme.TEXT_MAIN, false, false);
        agyEngineText.setPadding(0, dp(3), 0, dp(3));
        card.addView(agyEngineText);

        cdxEngineText = Theme.text(act, "• Codex CLI: Memuat...", 13f, Theme.TEXT_MAIN, false, false);
        cdxEngineText.setPadding(0, dp(3), 0, dp(3));
        card.addView(cdxEngineText);

        bridgeRuntimeText = Theme.text(act, "• Bridge Gateway: Node.js ...", 13f, Theme.TEXT_MAIN, false, false);
        bridgeRuntimeText.setPadding(0, dp(3), 0, dp(3));
        card.addView(bridgeRuntimeText);

        jobsRunningText = Theme.text(act, "• Antrean Task Latar: 0 Berjalan", 12.5f, Theme.TEXT_LIGHT, false, false);
        jobsRunningText.setPadding(0, dp(3), 0, dp(3));
        card.addView(jobsRunningText);

        workdirText = Theme.text(act, "• Workdir: ...", 11.5f, Theme.TEXT_MUTED, false, false);
        workdirText.setSingleLine(true);
        workdirText.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        workdirText.setPadding(0, dp(3), 0, 0);
        card.addView(workdirText);

        return card;
    }

    private View buildActionToolsCard() {
        LinearLayout card = createDashboardCard();

        LinearLayout head = createCardHeader(R.drawable.ic_build, "Alat Operasional Server");
        card.addView(head);

        card.addView(createActionRow("📜 Lihat Live Bridge Logs", () -> showLogs()));
        card.addView(createActionRow("💾 Export Backup Konfigurasi JSON", () -> exportBackup()));
        card.addView(createActionRow("📥 Restore Settings dari Backup", this::showRestoreInput));
        card.addView(createActionRow("🧹 Bersihkan Cache & File Temp", this::cleanTempFiles));
        card.addView(createActionRow("🔄 Restart Server Bridge", this::confirmRestartBridge));

        return card;
    }

    private LinearLayout createDashboardCard() {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.box(act, Theme.SURFACE, Theme.BORDER, 1, 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    private LinearLayout createCardHeader(int iconRes, String title) {
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(6));

        ImageView icon = Theme.icon(act, iconRes, 18, Theme.ACCENT);
        head.addView(icon);

        TextView titleView = Theme.text(act, " " + title, 14.5f, Theme.TEXT_MAIN, true, false);
        head.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));
        return head;
    }

    private View createActionRow(String label, Runnable action) {
        TextView btn = Theme.text(act, label, 13.5f, Theme.TEXT_MAIN, true, false);
        btn.setBackground(Theme.box(act, Theme.SURFACE_MUTED, Theme.BORDER, 1, 10));
        btn.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> action.run());
        return btn;
    }

    private void toggleAutoPoll() {
        isAutoPollEnabled = !isAutoPollEnabled;
        if (isAutoPollEnabled) {
            liveBadge.setText("● LIVE");
            liveBadge.setTextColor(Theme.GREEN);
            mainHandler.post(pollRunnable);
            Toast.makeText(act, "Pemantauan live real-time aktif (3 detik)", Toast.LENGTH_SHORT).show();
        } else {
            liveBadge.setText("⏸ PAUSED");
            liveBadge.setTextColor(Theme.TEXT_MUTED);
            mainHandler.removeCallbacks(pollRunnable);
            Toast.makeText(act, "Pemantauan live dijeda", Toast.LENGTH_SHORT).show();
        }
    }

    private void rotateRefreshIcon() {
        if (refreshIcon == null) return;
        RotateAnimation rotate = new RotateAnimation(0, 360,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(500);
        refreshIcon.startAnimation(rotate);
    }

    private void fetchHealthData(boolean showSpinner) {
        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/health/operations", 15000);
                JSONObject health = json.optJSONObject("health");
                mainHandler.post(() -> renderDashboard(health));
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    if (hostSubtext != null) {
                        hostSubtext.setText("Koneksi gagal: " + ex.getMessage());
                    }
                });
            }
        });
    }

    private void renderDashboard(JSONObject health) {
        if (health == null) return;

        JSONObject server = health.optJSONObject("server");
        JSONObject engines = health.optJSONObject("engines");
        JSONObject filesystem = health.optJSONObject("filesystem");
        JSONArray history = health.optJSONArray("history");

        // 1. RAM Card
        if (server != null) {
            int memUsed = server.optInt("memoryMb", 0);
            int memTotal = server.optInt("memoryTotalMb", 0);
            int memFree = server.optInt("memoryFreeMb", 0);
            int memPercent = server.optInt("memoryPercent", memTotal > 0 ? Math.round((memUsed * 100f) / memTotal) : 0);

            if (ramPercentText != null) ramPercentText.setText(memPercent + "%");
            if (ramProgressBar != null) ramProgressBar.setProgress(memPercent);

            if (ramDetailText != null) {
                String usedStr = formatMb(memUsed);
                String totalStr = formatMb(memTotal);
                String freeStr = formatMb(memFree);
                ramDetailText.setText(usedStr + " digunakan dari " + totalStr + " (" + freeStr + " tersedia)");
            }

            // 2. Disk Card
            double diskTotal = server.optDouble("diskTotalGb", 0);
            double diskUsed = server.optDouble("diskUsedGb", 0);
            double diskFree = server.optDouble("diskFreeGb", 0);
            int diskPercent = server.optInt("diskPercent", diskTotal > 0 ? (int) Math.round((diskUsed * 100.0) / diskTotal) : 0);

            if (diskPercentText != null) diskPercentText.setText(diskPercent + "% Terpakai");
            if (diskProgressBar != null) diskProgressBar.setProgress(diskPercent);

            if (diskDetailText != null) {
                diskDetailText.setText(String.format("%.1f GB Digunakan · %.1f GB Sisa (Total %.1f GB)",
                        diskUsed, diskFree, diskTotal));
            }

            // 3. CPU & Load Card
            if (cpuModelText != null) {
                String model = server.optString("cpuModel", "Unknown CPU");
                int cores = server.optInt("cpuCores", 1);
                cpuModelText.setText(cores + " Cores · " + model);
            }

            if (cpuLoadText != null) {
                JSONArray load = server.optJSONArray("loadAvg");
                if (load != null && load.length() >= 3) {
                    cpuLoadText.setText(String.format("Load Avg:  1m: %.2f  ·  5m: %.2f  ·  15m: %.2f",
                            load.optDouble(0, 0), load.optDouble(1, 0), load.optDouble(2, 0)));
                }
            }

            if (uptimeText != null) {
                long uptimeSec = server.optLong("uptimeSeconds", 0);
                long procUptimeSec = server.optLong("processUptimeSeconds", 0);
                uptimeText.setText("Uptime: Sistem " + formatUptime(uptimeSec) + " · Bridge " + formatUptime(procUptimeSec));
            }

            if (hostSubtext != null) {
                String host = server.optString("hostname", "Host Gateway");
                String nodeVer = server.optString("nodeVersion", "");
                hostSubtext.setText(host + (nodeVer.isEmpty() ? "" : " · Node " + nodeVer) + " · Live");
            }
        }

        // 4. Update Charts from History
        if (history != null && history.length() > 0) {
            List<Float> ramPoints = new ArrayList<>();
            List<Float> cpuPoints = new ArrayList<>();
            for (int i = 0; i < history.length(); i++) {
                JSONObject pt = history.optJSONObject(i);
                if (pt != null) {
                    ramPoints.add((float) pt.optDouble("memPercent", 0));
                    cpuPoints.add((float) pt.optDouble("load1m", 0));
                }
            }
            if (ramChart != null) ramChart.setData(ramPoints);
            if (cpuLoadChart != null) cpuLoadChart.setData(cpuPoints);
        }

        // 5. Engine & Runtime Status
        if (engines != null) {
            JSONObject agy = engines.optJSONObject("antigravity");
            if (agyEngineText != null) {
                boolean ok = agy != null && agy.optBoolean("ok", false);
                String ver = agy != null ? agy.optString("version", "Aktif") : "-";
                agyEngineText.setText("• Antigravity Engine: " + (ok ? "🟢 " + ver : "🔴 Tidak Terdeteksi"));
            }

            JSONObject cdx = engines.optJSONObject("codex");
            if (cdxEngineText != null) {
                boolean ok = cdx != null && cdx.optBoolean("ok", false);
                String ver = cdx != null ? cdx.optString("version", "Aktif") : "-";
                cdxEngineText.setText("• Codex CLI Engine: " + (ok ? "🟢 " + ver : "🔴 Tidak Terdeteksi"));
            }
        }

        if (bridgeRuntimeText != null && server != null) {
            bridgeRuntimeText.setText("• Bridge: " + server.optString("platform", "Linux")
                    + " (Node " + server.optString("nodeVersion", "v20") + ")");
        }

        if (jobsRunningText != null) {
            int jobs = health.optInt("runningJobs", 0);
            jobsRunningText.setText("• Antrean Task Latar: " + (jobs == 0 ? "Tidak ada (Idle)" : jobs + " Task Berjalan"));
        }

        if (workdirText != null && filesystem != null) {
            String dir = filesystem.optString("workdir", "-");
            String gitStatus = health.optString("git", "ok");
            workdirText.setText("• Workdir: " + dir + " (" + ("ok".equals(gitStatus) ? "Git Aktif" : "Bukan Git") + ")");
        }
    }

    private String formatMb(int mb) {
        if (mb >= 1024) {
            return String.format("%.2f GB", mb / 1024f);
        }
        return mb + " MB";
    }

    private String formatUptime(long seconds) {
        if (seconds <= 0) return "0s";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long mins = (seconds % 3600) / 60;
        if (days > 0) return days + "h " + hours + "j " + mins + "m";
        if (hours > 0) return hours + "j " + mins + "m";
        return mins + "m " + (seconds % 60) + "d";
    }

    private void showLogs() {
        Dialog dialog = new Dialog(act);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
        }
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Theme.box(act, Theme.SURFACE, Theme.BORDER, 1, 20));
        root.setPadding(dp(18), dp(16), dp(18), dp(16));

        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Theme.text(act, "Live Bridge Logs", 17, Theme.TEXT_MAIN, true, false);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView closeBtn = Theme.iconButton(act, R.drawable.ic_close, 20, 36, Theme.TEXT_MUTED);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        head.addView(closeBtn);
        root.addView(head);

        LinearLayout logBody = new LinearLayout(act);
        logBody.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(act);
        scroll.addView(logBody);
        LinearLayout.LayoutParams lpScroll = new LinearLayout.LayoutParams(-1, dp(440));
        lpScroll.topMargin = dp(10);
        root.addView(scroll, lpScroll);

        logBody.addView(Theme.text(act, "Memuat logs...", 12.5f, Theme.TEXT_MUTED, false, false));

        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/logs?lines=300", 20000);
                StringBuilder sb = new StringBuilder();
                JSONArray lines = json.optJSONArray("lines");
                for (int i = 0; lines != null && i < lines.length(); i++) {
                    sb.append(lines.optString(i)).append("\n");
                }
                mainHandler.post(() -> {
                    logBody.removeAllViews();
                    TextView tv = Theme.text(act, sb.length() > 0 ? sb.toString() : "Log kosong.",
                            10.5f, Theme.TEXT_MUTED, false, false);
                    tv.setTypeface(Typeface.MONOSPACE);
                    tv.setTextIsSelectable(true);
                    logBody.addView(tv);
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    logBody.removeAllViews();
                    logBody.addView(Theme.text(act, "Gagal memuat log: " + ex.getMessage(), 13, Theme.RED, false, false));
                });
            }
        });

        dialog.setContentView(root);
        dialog.show();
    }

    private void exportBackup() {
        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/backup", 20000);
                JSONObject backup = json.optJSONObject("backup");
                if (backup == null) throw new Exception("Backup kosong");
                ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("codex-remote-backup", backup.toString()));
                }
                mainHandler.post(() -> Toast.makeText(act, "Konfigurasi backup disalin ke clipboard!", Toast.LENGTH_LONG).show());
            } catch (Exception ex) {
                mainHandler.post(() -> Toast.makeText(act, "Gagal export backup: " + ex.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showRestoreInput() {
        AlertDialog.Builder builder = new AlertDialog.Builder(act);
        builder.setTitle("Restore Settings dari JSON");
        final android.widget.EditText input = new android.widget.EditText(act);
        input.setHint("Paste isi JSON backup di sini...");
        input.setMinLines(4);
        builder.setView(input);
        builder.setPositiveButton("Restore", (d, w) -> {
            String raw = input.getText().toString().trim();
            if (raw.isEmpty()) return;
            try {
                JSONObject backup = new JSONObject(raw);
                executor.execute(() -> {
                    try {
                        JSONObject result = bridge.post("/api/backup/restore",
                                new JSONObject().put("backup", backup), 30000);
                        mainHandler.post(() -> Toast.makeText(act,
                                result.optBoolean("ok") ? "Settings berhasil dipulihkan!" :
                                        result.optString("error", "Restore gagal"), Toast.LENGTH_LONG).show());
                    } catch (Exception ex) {
                        mainHandler.post(() -> Toast.makeText(act, "Gagal: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
            } catch (Exception ex) {
                Toast.makeText(act, "Format JSON tidak valid!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Batal", null).show();
    }

    private void cleanTempFiles() {
        executor.execute(() -> {
            try {
                JSONObject res = bridge.post("/api/maintenance/prune", new JSONObject(), 15000);
                mainHandler.post(() -> {
                    Toast.makeText(act, res.optBoolean("ok") ? "Pembersihan cache selesai!" : "Gagal membersihkan cache", Toast.LENGTH_SHORT).show();
                    fetchHealthData(false);
                });
            } catch (Exception ex) {
                mainHandler.post(() -> Toast.makeText(act, "Gagal: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void confirmRestartBridge() {
        new AlertDialog.Builder(act)
                .setTitle("Restart Service Bridge")
                .setMessage("Apakah Anda yakin ingin me-restart service backend bridge di VPS?")
                .setPositiveButton("Ya, Restart", (d, w) -> {
                    executor.execute(() -> {
                        try {
                            bridge.post("/api/maintenance/restart", new JSONObject(), 5000);
                        } catch (Exception ignored) {}
                        mainHandler.post(() -> {
                            Toast.makeText(act, "Perintah restart dikirim. Menunggu koneksi kembali...", Toast.LENGTH_LONG).show();
                        });
                    });
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private int dp(float value) {
        return (int) (act.getResources().getDisplayMetrics().density * value + 0.5f);
    }
}
