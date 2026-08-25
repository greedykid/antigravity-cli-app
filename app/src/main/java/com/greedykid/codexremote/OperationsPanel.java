package com.greedykid.codexremote;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;

/** Server operations: health stats, bridge log viewer and config backup. */
final class OperationsPanel {
    private final Activity act;
    private final BridgeClient bridge;
    private final ExecutorService executor;
    private final android.os.Handler mainHandler;

    OperationsPanel(Activity activity, BridgeClient client, ExecutorService executorService,
                    android.os.Handler handler) {
        act = activity;
        bridge = client;
        executor = executorService;
        mainHandler = handler;
    }

    void show() {
        Dialog dialog = createSheet("Server Operations");
        LinearLayout body = bodyFor(dialog);
        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/health/operations", 15000);
                mainHandler.post(() -> renderHealth(dialog, json.optJSONObject("health")));
            } catch (Exception ex) {
                postError(dialog, "Gagal memuat health: " + ex.getMessage());
            }
        });
        dialog.show();
    }

    private void renderHealth(Dialog dialog, JSONObject health) {
        LinearLayout body = bodyFor(dialog);
        body.removeAllViews();
        if (health == null) { error(body, "Data health kosong"); return; }
        JSONObject server = health.optJSONObject("server");

        body.addView(label("Running jobs", String.valueOf(health.optInt("runningJobs", 0))));
        body.addView(label("Memory used (MB)", server == null ? "-" : String.valueOf(server.opt("memoryMb"))));
        body.addView(label("Disk free (GB)", server == null ? "-" : String.valueOf(server.opt("diskFreeGb"))));
        body.addView(label("Uptime (s)", server == null ? "-" : String.valueOf(server.opt("uptimeSeconds"))));

        body.addView(actionButton("Lihat Logs", () -> showLogs()));
        body.addView(actionButton("Export Backup", () -> exportBackup()));
        body.addView(actionButton("Restore Settings dari Backup JSON", this::showRestoreInput));
    }

    private void showLogs() {
        Dialog dialog = createSheet("Bridge Logs");
        LinearLayout body = bodyFor(dialog);
        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/logs?lines=300", 20000);
                StringBuilder sb = new StringBuilder();
                org.json.JSONArray lines = json.optJSONArray("lines");
                for (int i = 0; lines != null && i < lines.length(); i++) sb.append(lines.optString(i)).append("\\n");
                mainHandler.post(() -> {
                    LinearLayout logBody = bodyFor(dialog);
                    logBody.removeAllViews();
                    TextView tv = Theme.text(act, sb.length() > 0 ? sb.toString() : "Log kosong.",
                            10.5f, Theme.TEXT_MUTED, false, false);
                    tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                    ScrollView scroll = new ScrollView(act);
                    scroll.addView(tv);
                    logBody.addView(scroll, new LinearLayout.LayoutParams(-1, dp(400)));
                });
            } catch (Exception ex) {
                postError(dialog, "Gagal memuat log: " + ex.getMessage());
            }
        });
        dialog.show();
    }

    private void exportBackup() {
        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/backup", 20000);
                JSONObject backup = json.optJSONObject("backup");
                if (backup == null) throw new Exception("Backup kosong");
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) act.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText(
                        "codex-remote-backup", backup.toString()));
                mainHandler.post(() -> Toast.makeText(act,
                        "Backup disalin ke clipboard", Toast.LENGTH_LONG).show());
            } catch (Exception ex) {
                mainHandler.post(() -> Toast.makeText(act, "Gagal: " + ex.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showRestoreInput() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(act);
        builder.setTitle("Paste backup JSON");
        final android.widget.EditText input = new android.widget.EditText(act);
        input.setMinLines(3);
        builder.setView(input);
        builder.setPositiveButton("Restore", (d, w) -> {
            try {
                JSONObject backup = new JSONObject(input.getText().toString().trim());
                executor.execute(() -> {
                    try {
                        JSONObject result = bridge.post("/api/backup/restore",
                                new org.json.JSONObject().put("backup", backup), 30000);
                        mainHandler.post(() -> Toast.makeText(act,
                                result.optBoolean("ok") ? "Settings dipulihkan" :
                                        result.optString("error", "Restore gagal"), Toast.LENGTH_LONG).show());
                    } catch (Exception ex) {
                        mainHandler.post(() -> Toast.makeText(act, "Gagal: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
            } catch (Exception ex) {
                Toast.makeText(act, "JSON tidak valid", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Batal", null).show();
    }

    private final java.util.Map<Dialog, LinearLayout> bodies = new java.util.HashMap<>();
    private Dialog createSheet(String titleText) {
        Dialog dialog = new Dialog(act);
        dialog.requestWindowFeature(Dialog.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Theme.box(act, Theme.SURFACE, 0, 0, 24));
        root.setPadding(dp(20), dp(18), dp(20), dp(16));
        TextView title = Theme.text(act, titleText, 17, Theme.TEXT_MAIN, true, false);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);
        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(act);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(430)));
        body.addView(Theme.text(act, "Memuat...", 13, Theme.TEXT_MUTED, false, false));
        dialog.setContentView(root);
        bodies.put(dialog, body);
        return dialog;
    }
    private LinearLayout bodyFor(Dialog d) { return bodies.computeIfAbsent(d, k -> new LinearLayout(act)); }
    private void postError(Dialog d, String message) { mainHandler.post(() -> error(bodyFor(d), message)); }
    private void error(LinearLayout body, String message) {
        body.removeAllViews();
        body.addView(Theme.text(act, message, 13, Theme.RED, false, false));
    }
    private TextView label(String key, String value) {
        TextView v = Theme.text(act, key + ":  " + value, 14.5f, Theme.TEXT_MAIN, false, false);
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }
    private TextView actionButton(String text, Runnable action) {
        TextView v = Theme.text(act, text, 14, Theme.ACCENT, true, false);
        v.setBackground(Theme.box(act, Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        v.setGravity(android.view.Gravity.CENTER);
        v.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        v.setLayoutParams(lp);
        v.setOnClickListener(v2 -> action.run());
        return v;
    }
    private int dp(float value) { return (int) (act.getResources().getDisplayMetrics().density * value + 0.5f); }
}
