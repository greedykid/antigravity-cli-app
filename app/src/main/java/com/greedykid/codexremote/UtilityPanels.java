package com.greedykid.codexremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/** Self-contained utility sheets: prompt library and device manager. */
final class UtilityPanels {
    private final Activity act;
    private final BridgeClient bridge;
    private final PromptLibrary promptLibrary;
    private final ExecutorService executor;
    private final android.os.Handler mainHandler;

    UtilityPanels(Activity activity, BridgeClient bridgeClient, PromptLibrary library,
                  ExecutorService executorService, android.os.Handler handler) {
        this.act = activity;
        this.bridge = bridgeClient;
        this.promptLibrary = library;
        this.executor = executorService;
        this.mainHandler = handler;
    }

    void showPromptLibrary(EditText promptInput) {
        LinearLayout container = new LinearLayout(act);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(22), dp(20), dp(22), dp(10));
        TextView title = text("Prompt Library", 20, Theme.TEXT_MAIN, true, false);
        container.addView(title);
        TextView subtitle = text("Simpan dan pakai ulang prompt penting.", 14, Theme.TEXT_LIGHT, false, false);
        subtitle.setPadding(0, dp(6), 0, dp(14));
        container.addView(subtitle);

        JSONArray prompts = promptLibrary.all();
        if (prompts.length() == 0) {
            container.addView(text("Belum ada prompt tersimpan.", 15, Theme.TEXT_MUTED, false, false));
        }

        AtomicReference<AlertDialog> dialogRef = new AtomicReference<>();
        for (int index = prompts.length() - 1; index >= 0; index--) {
            final JSONObject item;
            try { item = prompts.getJSONObject(index); }
            catch (Exception ignored) { continue; }

            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(box(Theme.SURFACE_MUTED, 0, 0, 16));
            row.setPadding(dp(14), dp(12), dp(8), dp(12));

            TextView label = text(item.optString("title", "Prompt"), 15.5f, Theme.TEXT_MAIN, true, false);
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

            ImageView use = icon(R.drawable.ic_send, 18, 40, Theme.ACCENT);
            use.setOnClickListener(v -> {
                dialogRef.get().dismiss();
                promptInput.setText(item.optString("prompt", ""));
                promptInput.requestFocus();
            });
            row.addView(use);

            ImageView remove = icon(R.drawable.ic_close, 18, 40, Theme.TEXT_MUTED);
            remove.setOnClickListener(v -> {
                promptLibrary.delete(item.optString("id", ""));
                dialogRef.get().dismiss();
                showPromptLibrary(promptInput);
            });
            row.addView(remove);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            container.addView(row, lp);
        }

        TextView save = text("+ Simpan teks input saat ini", 15, Theme.ACCENT, true, false);
        save.setPadding(dp(4), dp(16), dp(4), dp(8));
        save.setOnClickListener(v -> {
            String prompt = promptInput.getText().toString().trim();
            if (prompt.isEmpty()) return;
            String savedTitle = prompt.length() > 48 ? prompt.substring(0, 48) + "…" : prompt;
            promptLibrary.add(savedTitle, prompt);
            dialogRef.get().dismiss();
            Toast.makeText(act, "Prompt disimpan", Toast.LENGTH_SHORT).show();
            showPromptLibrary(promptInput);
        });
        container.addView(save);

        AlertDialog.Builder builder = new AlertDialog.Builder(act);
        builder.setView(container);
        builder.setNegativeButton("Tutup", null);
        AlertDialog dialog = builder.create();
        dialogRef.set(dialog);
        dialog.show();
    }

    void showDeviceManager() {
        Dialog dialog = createSheet("Perangkat");
        LinearLayout body = createBody(dialog);

        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/devices", 20000);
                JSONArray devices = json.optJSONArray("devices");
                mainHandler.post(() -> renderDevices(body, dialog, devices));
            } catch (Exception ex) {
                mainHandler.post(() -> showError(body, "Gagal memuat perangkat: " + ex.getMessage()));
            }
        });

        dialog.setContentView((View) body.getParent().getParent());
        dialog.show();
    }

    private void renderDevices(LinearLayout body, Dialog dialog, JSONArray devices) {
        body.removeAllViews();
        if (devices == null || devices.length() == 0) {
            body.addView(text("Belum ada perangkat terdaftar.", 14, Theme.TEXT_MUTED, false, false));
            return;
        }
        for (int index = 0; index < devices.length(); index++) {
            JSONObject device = devices.optJSONObject(index);
            if (device == null) continue;
            boolean revoked = device.optBoolean("revoked", false);
            String deviceId = device.optString("id", "");

            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(box(Theme.SURFACE_MUTED, 0, 0, 12));
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.addView(text(device.optString("name", "Device") + (revoked ? " · dicabut" : ""),
                    14.5f, revoked ? Theme.RED : Theme.TEXT_MAIN, true, false));

            TextView meta = text(deviceId + "\nTerakhir aktif: " +
                    new java.util.Date(device.optLong("lastSeenAt", 0)).toString(), 11.5f,
                    Theme.TEXT_LIGHT, false, false);
            meta.setPadding(0, dp(3), 0, 0);
            row.addView(meta);

            TextView action = text(revoked ? "Pulihkan" : "Cabut akses", 13.5f,
                    revoked ? Theme.GREEN : Theme.ACCENT, true, false);
            action.setPadding(0, dp(10), 0, 0);
            action.setOnClickListener(v -> executor.execute(() -> toggleDevice(body, dialog, deviceId, !revoked)));
            row.addView(action);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            body.addView(row, lp);
        }
    }

    private void toggleDevice(LinearLayout body, Dialog oldDialog, String deviceId, boolean revoked) {
        try {
            JSONObject payload = new JSONObject().put("id", deviceId).put("revoked", revoked);
            JSONObject result = bridge.post("/api/devices/revoke", payload, 20000);
            mainHandler.post(() -> {
                if (result.optBoolean("ok", false)) {
                    oldDialog.dismiss();
                    showDeviceManager();
                } else {
                    Toast.makeText(act, result.optString("error", "Gagal mengubah perangkat"), Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception ex) {
            mainHandler.post(() -> Toast.makeText(act, "Gagal: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private Dialog createSheet(String titleText) {
        Dialog dialog = new Dialog(act);
        dialog.requestWindowFeature(Dialog.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(bottomBox());
        root.setPadding(dp(20), dp(18), dp(20), dp(16));
        TextView title = text(titleText, 17, Theme.TEXT_MAIN, true, false);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);
        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setTag(root);
        ScrollView scroll = new ScrollView(act);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(430)));
        body.addView(text("Memuat...", 13, Theme.TEXT_MUTED, false, false));
        dialog.setContentView(root);
        return wrapDialogWithBody(dialog, root, body);
    }

    private final java.util.Map<Dialog, LinearLayout> bodies = new java.util.HashMap<>();
    private Dialog wrapDialogWithBody(Dialog d, LinearLayout root, LinearLayout body) { bodies.put(d, body); return d; }
    private LinearLayout createBody(Dialog d) { return bodies.computeIfAbsent(d, k -> new LinearLayout(act)); }

    private void showError(LinearLayout body, String message) {
        body.removeAllViews();
        body.addView(text(message, 13, Theme.RED, false, false));
    }

    private int dp(float value) { return (int) (act.getResources().getDisplayMetrics().density * value + 0.5f); }
    private TextView text(String s, float sp, int color, boolean bold, boolean serif) { return Theme.text(act, s, sp, color, bold, serif); }
    private android.graphics.drawable.GradientDrawable box(int fill, int border, int bw, float r) { return Theme.box(act, fill, border, bw, r); }
    private android.graphics.drawable.GradientDrawable bottomBox() { return Theme.box(act, Theme.SURFACE, 0, 0, 24); }
    private ImageView icon(int res, int sizeDp, int touchDp, int tint) { return Theme.icon(act, res, sizeDp, tint); }
}
