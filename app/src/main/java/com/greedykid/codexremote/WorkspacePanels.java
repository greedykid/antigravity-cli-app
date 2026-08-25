package com.greedykid.codexremote;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Workspace tooling that hangs off the chat screen: the file browser and
 * editor, the git panel, transcript search, the project picker and the
 * maintenance sheet. Split out of MainActivity, which was carrying all of it
 * on top of the chat UI it actually owns.
 *
 * Everything it needs from the host arrives through {@link Host} rather than a
 * back-reference to MainActivity, so these panels stay testable in isolation.
 */
public class WorkspacePanels {

    /** The parts of the host screen these panels genuinely need. */
    public interface Host {
        Dialog createSheet();
        LinearLayout createSheetRoot(Dialog dialog, String title, boolean showClose);
        void renderMarkdown(LinearLayout container, String markdown);
        void openSession(String conversationId, String title);
        void onProjectChanged();
    }

    private final Activity act;
    private final Host host;
    private final BridgeClient bridge;
    private final SharedPreferences prefs;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public WorkspacePanels(Activity activity, Host host, BridgeClient bridge,
                           SharedPreferences prefs, ExecutorService executor, Handler mainHandler) {
        this.act = activity;
        this.host = host;
        this.bridge = bridge;
        this.prefs = prefs;
        this.executor = executor;
        this.mainHandler = mainHandler;
    }

    // ---- host shims so the moved code reads unchanged ----

    private int dp(float value) {
        return Theme.dp(act, value);
    }

    private GradientDrawable cBox(int fill, int border, int borderWidthDp, float radiusDp) {
        return Theme.box(act, fill, border, borderWidthDp, radiusDp);
    }

    private TextView cText(String value, float sp, int color, boolean bold, boolean serif) {
        return Theme.text(act, value, sp, color, bold, serif);
    }

    private ImageView cIcon(int resId, int sizeDp, int tint) {
        return Theme.icon(act, resId, sizeDp, tint);
    }

    private Dialog createBaseBottomSheet(boolean fullWidth) {
        return host.createSheet();
    }

    private LinearLayout createBottomSheetRoot(Dialog dialog, String title, boolean showClose) {
        return host.createSheetRoot(dialog, title, showClose);
    }

    private void renderMarkdownIntoContainer(LinearLayout container, String markdown, boolean isUser) {
        host.renderMarkdown(container, markdown);
    }

    private void refreshSettingsValues() {
        host.onProjectChanged();
    }

    private void openSpecificSession(String conversationId, String title) {
        host.openSession(conversationId, title);
    }


    // ============================================================
    // FILE BROWSER
    // ============================================================
    public void showFileBrowser(final String startPath) {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "File Workspace", true);

        final TextView pathLabel = cText(startPath == null ? "." : startPath, 12f, Theme.TEXT_MUTED, false, false);
        pathLabel.setSingleLine(true);
        pathLabel.setEllipsize(TextUtils.TruncateAt.START);
        root.addView(pathLabel);

        final ScrollView scroll = new ScrollView(act);
        final LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        LinearLayout.LayoutParams lpScroll = new LinearLayout.LayoutParams(-1, dp(420));
        lpScroll.setMargins(0, dp(10), 0, 0);
        root.addView(scroll, lpScroll);

        loadFileList(startPath == null ? "." : startPath, list, pathLabel, dialog);

        dialog.setContentView(root);
        dialog.show();
    }

    private void loadFileList(final String path, final LinearLayout list, final TextView pathLabel, final Dialog dialog) {
        list.removeAllViews();
        list.addView(cText("Memuat...", 13f, Theme.TEXT_MUTED, false, false));

        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/files?path=" + BridgeClient.encode(path));
                mainHandler.post(() -> {
                    list.removeAllViews();
                    if (!json.optBoolean("ok", false)) {
                        list.addView(cText(describeApiError(json, "Gagal memuat"), 13f, Theme.RED, false, false));
                        return;
                    }
                    pathLabel.setText("/" + json.optString("path", "."));

                    final String parent = json.isNull("parent") ? null : json.optString("parent", null);
                    if (parent != null) {
                        list.addView(buildFileRow("..", "dir", 0, () ->
                                loadFileList(parent, list, pathLabel, dialog)));
                    }

                    JSONArray entries = json.optJSONArray("entries");
                    if (entries == null || entries.length() == 0) {
                        list.addView(cText("Folder kosong", 13f, Theme.TEXT_LIGHT, false, false));
                        return;
                    }
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject e = entries.optJSONObject(i);
                        if (e == null) continue;
                        final String name = e.optString("name");
                        final String type = e.optString("type");
                        final String child = ".".equals(path) ? name : path + "/" + name;
                        list.addView(buildFileRow(name, type, e.optLong("size"), () -> {
                            if ("dir".equals(type)) {
                                loadFileList(child, list, pathLabel, dialog);
                            } else {
                                dialog.dismiss();
                                showFileViewer(child);
                            }
                        }));
                    }
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    list.removeAllViews();
                    list.addView(cText("Gagal: " + ex.getMessage(), 13f, Theme.RED, false, false));
                });
            }
        });
    }

    private LinearLayout buildFileRow(String name, String type, long size, final Runnable onClick) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(11), dp(10), dp(11));
        row.setBackground(cBox(Color.TRANSPARENT, 0, 0, 12));

        boolean isDir = "dir".equals(type);
        row.addView(cIcon(isDir ? R.drawable.ic_folder : R.drawable.ic_description, 20,
                isDir ? Theme.ACCENT : Theme.TEXT_MUTED));

        TextView label = cText(name, 14.5f, Theme.TEXT_MAIN, isDir, false);
        label.setPadding(dp(12), 0, dp(8), 0);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        if (!isDir && size > 0) {
            row.addView(cText(humanSize(size), 11.5f, Theme.TEXT_LIGHT, false, false));
        }

        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    public String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }

    public void showFileViewer(final String path) {
        final Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, path.substring(path.lastIndexOf('/') + 1), true);

        TextView pathLabel = cText("/" + path, 11.5f, Theme.TEXT_MUTED, false, false);
        pathLabel.setSingleLine(true);
        pathLabel.setEllipsize(TextUtils.TruncateAt.START);
        root.addView(pathLabel);

        final LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(act);
        scroll.addView(body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(430));
        lp.setMargins(0, dp(10), 0, 0);
        root.addView(scroll, lp);

        body.addView(cText("Memuat...", 13f, Theme.TEXT_MUTED, false, false));

        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/files/read?path=" + BridgeClient.encode(path));
                mainHandler.post(() -> {
                    body.removeAllViews();
                    if (!json.optBoolean("ok", false)) {
                        body.addView(cText(describeApiError(json, "Gagal membaca"), 13f, Theme.RED, false, false));
                        return;
                    }
                    if (json.optBoolean("binary", false)) {
                        body.addView(cText("File biner (" + humanSize(json.optLong("size")) + ") tidak ditampilkan.",
                                13f, Theme.TEXT_MUTED, false, false));
                        return;
                    }
                    String lang = json.optString("language", "");
                    final String content = json.optString("content", "");
                    final boolean truncated = json.optBoolean("truncated", false);

                    // Editing a truncated file would silently drop the tail.
                    if (!truncated) {
                        TextView edit = cText("Edit file", 13.5f, Theme.ACCENT, true, false);
                        edit.setGravity(Gravity.CENTER);
                        edit.setPadding(dp(14), dp(11), dp(14), dp(11));
                        edit.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
                        edit.setOnClickListener(v -> {
                            dialog.dismiss();
                            showFileEditor(path, content);
                        });
                        LinearLayout.LayoutParams lpEdit = new LinearLayout.LayoutParams(-1, -2);
                        lpEdit.setMargins(0, 0, 0, dp(10));
                        body.addView(edit, lpEdit);
                    }

                    // Reuse the markdown code-block styling by fencing the content.
                    renderMarkdownIntoContainer(body, "```" + lang + "\n" + content + "\n```", false);
                    if (truncated) {
                        body.addView(cText("… dipotong di 512 KB — edit dimatikan agar sisanya tidak hilang",
                                12f, Theme.AMBER, false, false));
                    }
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    body.removeAllViews();
                    body.addView(cText("Gagal: " + ex.getMessage(), 13f, Theme.RED, false, false));
                });
            }
        });

        dialog.setContentView(root);
        dialog.show();
    }

    // ============================================================
    // FILE EDITOR
    // ============================================================
    public void showFileEditor(final String path, final String initialContent) {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Edit " + path.substring(path.lastIndexOf('/') + 1), true);

        TextView pathLabel = cText("/" + path, 11.5f, Theme.TEXT_MUTED, false, false);
        pathLabel.setSingleLine(true);
        pathLabel.setEllipsize(TextUtils.TruncateAt.START);
        root.addView(pathLabel);

        final EditText editor = new EditText(act);
        editor.setText(initialContent);
        editor.setTextSize(12.5f);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextColor(Color.rgb(240, 240, 245));
        editor.setBackground(cBox(Theme.CODE_BG, Theme.BORDER, 1, 12));
        editor.setPadding(dp(12), dp(12), dp(12), dp(12));
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setHorizontallyScrolling(false);
        LinearLayout.LayoutParams lpEd = new LinearLayout.LayoutParams(-1, dp(360));
        lpEd.setMargins(0, dp(10), 0, dp(12));
        root.addView(editor, lpEd);

        final String originalContent = initialContent;
        TextView dirtyIndicator = cText("Belum ada perubahan", 12f, Theme.TEXT_MUTED, false, false);
        dirtyIndicator.setPadding(dp(4), 0, 0, dp(8));
        root.addView(dirtyIndicator);
        editor.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                boolean dirty = !s.toString().equals(originalContent);
                dirtyIndicator.setText(dirty ? "● Ada perubahan belum disimpan" : "Belum ada perubahan");
                dirtyIndicator.setTextColor(dirty ? Theme.ACCENT : Theme.TEXT_MUTED);
            }
        });

        boolean isHighlightable = path.endsWith(".js") || path.endsWith(".ts") ||
                path.endsWith(".py") || path.endsWith(".json") || path.endsWith(".sh");
        if (isHighlightable) {
            TextView hint = cText("Syntax: " + path.substring(path.lastIndexOf('.') + 1), 11f,
                    Theme.BLUE, false, false);
            hint.setPadding(dp(4), dp(4), 0, 0);
            root.addView(hint);
        }

        LinearLayout actions = new LinearLayout(act);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView cancel = cText("Batal", 14f, Theme.TEXT_MAIN, true, false);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(16), dp(13), dp(16), dp(13));
        cancel.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(0, -2, 1);
        lpC.setMargins(0, 0, dp(8), 0);
        actions.addView(cancel, lpC);

        TextView save = cText("Simpan", 14f, Theme.ON_ACCENT, true, false);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(16), dp(13), dp(16), dp(13));
        save.setBackground(cBox(Theme.ACCENT, 0, 0, 12));
        save.setOnClickListener(v -> {
            final String content = editor.getText().toString();
            save.setEnabled(false);
            executor.execute(() -> {
                try {
                    JSONObject payload = new JSONObject().put("path", path).put("content", content);
                    JSONObject result = bridge.post("/api/files/write", payload, 30000);
                    mainHandler.post(() -> {
                        save.setEnabled(true);
                        if (result.optBoolean("ok", false)) {
                            Toast.makeText(act, "Tersimpan", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } else {
                            Toast.makeText(act, describeApiError(result, "Gagal menyimpan"), Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception ex) {
                    mainHandler.post(() -> {
                        save.setEnabled(true);
                        Toast.makeText(act, "Gagal: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
        actions.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);

        dialog.setContentView(root);
        dialog.show();
    }

    // ============================================================
    // GIT PANEL
    // ============================================================
    public void showGitPanel() {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Git", true);

        final LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(act);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(440)));

        body.addView(cText("Memuat status...", 13f, Theme.TEXT_MUTED, false, false));
        loadGitStatus(body, dialog);

        dialog.setContentView(root);
        dialog.show();
    }

    private void loadGitStatus(final LinearLayout body, final Dialog dialog) {
        String repoPath = prefs.getString("git_repo_path", "");
        final String query = repoPath.isEmpty() ? "" : "?path=" + BridgeClient.encode(repoPath);

        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/git/status" + query);
                mainHandler.post(() -> renderGitStatus(body, dialog, json));
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    body.removeAllViews();
                    body.addView(cText("Gagal: " + ex.getMessage(), 13f, Theme.RED, false, false));
                });
            }
        });
    }

    private void renderGitStatus(final LinearLayout body, final Dialog dialog, JSONObject json) {
        body.removeAllViews();

        if (!json.optBoolean("ok", false)) {
            body.addView(cText(describeApiError(json, "Bukan repository git"), 13.5f, Theme.TEXT_MUTED, false, false));
            TextView hint = cText("Set folder repo di Pengaturan → Git repo path.", 12.5f, Theme.TEXT_LIGHT, false, false);
            hint.setPadding(0, dp(8), 0, 0);
            body.addView(hint);
            return;
        }

        // Branch summary
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        head.setPadding(dp(12), dp(10), dp(12), dp(10));
        head.addView(cIcon(R.drawable.ic_code, 16, Theme.ACCENT));
        head.addView(cText("  " + json.optString("branch", "?"), 14f, Theme.TEXT_MAIN, true, false),
                new LinearLayout.LayoutParams(0, -2, 1));

        int ahead = json.optInt("ahead", 0);
        int behind = json.optInt("behind", 0);
        head.addView(cText((ahead > 0 ? "↑" + ahead + "  " : "") + (behind > 0 ? "↓" + behind : ""),
                12.5f, Theme.AMBER, true, false));
        body.addView(head);

        // Changed files
        JSONArray files = json.optJSONArray("files");
        final boolean clean = json.optBoolean("clean", true);
        TextView sectionFiles = cText(clean ? "Tidak ada perubahan" : "Perubahan (" + (files == null ? 0 : files.length()) + ")",
                12.5f, Theme.TEXT_MUTED, false, false);
        sectionFiles.setPadding(0, dp(16), 0, dp(6));
        body.addView(sectionFiles);

        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.optJSONObject(i);
                if (f == null) continue;
                final String filePath = f.optString("path");
                String code = (f.optString("index", " ") + f.optString("worktree", " ")).trim();

                LinearLayout row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(4), dp(8), dp(4), dp(8));

                TextView badge = cText(code.isEmpty() ? "M" : code, 11f, gitStatusColor(code), true, false);
                badge.setMinWidth(dp(26));
                row.addView(badge);

                TextView name = cText(filePath, 13.5f, Theme.TEXT_MAIN, false, false);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                name.setPadding(dp(8), 0, 0, 0);
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

                row.setOnClickListener(v -> showGitDiff(filePath));
                body.addView(row);
            }
        }

        // Commit box
        if (!clean) {
            final EditText message = new EditText(act);
            message.setHint("Pesan commit");
            message.setTextSize(14f);
            message.setTextColor(Theme.TEXT_MAIN);
            message.setHintTextColor(Theme.TEXT_LIGHT);
            message.setBackground(cBox(Theme.BG, Theme.BORDER, 1, 12));
            message.setPadding(dp(12), dp(12), dp(12), dp(12));
            LinearLayout.LayoutParams lpMsg = new LinearLayout.LayoutParams(-1, -2);
            lpMsg.setMargins(0, dp(14), 0, dp(10));
            body.addView(message, lpMsg);

            LinearLayout actions = new LinearLayout(act);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(buildGitButton("Commit", Theme.ACCENT, () -> {
                String text = message.getText().toString().trim();
                if (text.isEmpty()) {
                    Toast.makeText(act, "Pesan commit kosong", Toast.LENGTH_SHORT).show();
                    return;
                }
                runGitAction("/api/git/commit", text, body, dialog);
            }), new LinearLayout.LayoutParams(0, -2, 1));
            body.addView(actions);
        }

        LinearLayout pushRow = new LinearLayout(act);
        pushRow.setOrientation(LinearLayout.HORIZONTAL);
        pushRow.setPadding(0, dp(10), 0, 0);
        pushRow.addView(buildGitButton("Push", Theme.SURFACE_MUTED, () ->
                runGitAction("/api/git/push", null, body, dialog)), new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(pushRow);

        // Recent commits
        JSONArray commits = json.optJSONArray("commits");
        if (commits != null && commits.length() > 0) {
            TextView sectionLog = cText("Commit terakhir", 12.5f, Theme.TEXT_MUTED, false, false);
            sectionLog.setPadding(0, dp(18), 0, dp(6));
            body.addView(sectionLog);

            for (int i = 0; i < commits.length(); i++) {
                JSONObject c = commits.optJSONObject(i);
                if (c == null) continue;
                LinearLayout row = new LinearLayout(act);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, dp(6), 0, dp(6));

                TextView subject = cText(c.optString("subject", ""), 13.5f, Theme.TEXT_MAIN, false, false);
                subject.setSingleLine(true);
                subject.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(subject);
                row.addView(cText(c.optString("hash", "") + " · " + c.optString("when", ""),
                        11.5f, Theme.TEXT_LIGHT, false, false));
                body.addView(row);
            }
        }
    }

    private int gitStatusColor(String code) {
        if (code.startsWith("?")) return Theme.BLUE;
        if (code.contains("D")) return Theme.RED;
        if (code.contains("A")) return Theme.GREEN;
        return Theme.AMBER;
    }

    private TextView buildGitButton(String label, int color, final Runnable action) {
        TextView btn = cText(label, 14f, color == Theme.ACCENT ? Theme.ON_ACCENT : Theme.TEXT_MAIN, true, false);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(16), dp(12), dp(16), dp(12));
        btn.setBackground(cBox(color, Theme.BORDER, color == Theme.ACCENT ? 0 : 1, 12));
        btn.setOnClickListener(v -> action.run());
        return btn;
    }

    private void runGitAction(final String apiPath, final String message, final LinearLayout body, final Dialog dialog) {
        executor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                String repoPath = prefs.getString("git_repo_path", "");
                if (!repoPath.isEmpty()) payload.put("path", repoPath);
                if (message != null) payload.put("message", message);

                JSONObject result = bridge.post(apiPath, payload, 90000);
                if (result.optString("code", "").equals("APPROVAL_REQUIRED")) {
                    payload.put("approvalToken", result.optString("approvalToken"));
                    mainHandler.post(() -> confirmDangerousAction("Konfirmasi aksi Git",
                            apiPath.equals("/api/git/push") ? "Push ke remote sekarang?" : "Commit sekarang?", () ->
                            executor.execute(() -> postGit(apiPath, payload, body, dialog))));
                    return;
                }
                mainHandler.post(() -> {
                    boolean ok = result.optBoolean("ok", false);
                    Toast.makeText(act,
                            ok ? "Berhasil" : result.optString("error", "Gagal"),
                            Toast.LENGTH_SHORT).show();
                    if (ok) loadGitStatus(body, dialog);
                });
            } catch (Exception ex) {
                mainHandler.post(() -> Toast.makeText(act, "Gagal: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void postGit(String apiPath, JSONObject payload, LinearLayout body, Dialog dialog) {
        executor.execute(() -> {
            try {
                JSONObject result = bridge.post(apiPath, payload, 90000);
                mainHandler.post(() -> {
                    boolean ok = result.optBoolean("ok", false);
                    Toast.makeText(act, ok ? "Berhasil" : result.optString("error", "Gagal"), Toast.LENGTH_SHORT).show();
                    if (ok) loadGitStatus(body, dialog);
                });
            } catch (Exception ex) {
                mainHandler.post(() -> Toast.makeText(act, "Gagal: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void confirmDangerousAction(String title, String message, Runnable action) {
        new android.app.AlertDialog.Builder(act)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Lanjutkan", (d, which) -> action.run())
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showGitDiff(final String filePath) {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Diff", true);
        root.addView(cText(filePath, 11.5f, Theme.TEXT_MUTED, false, false));

        final LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(act);
        scroll.addView(body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(430));
        lp.setMargins(0, dp(10), 0, 0);
        root.addView(scroll, lp);
        body.addView(cText("Memuat...", 13f, Theme.TEXT_MUTED, false, false));

        String repoPath = prefs.getString("git_repo_path", "");
        final String query = "?file=" + BridgeClient.encode(filePath)
                + (repoPath.isEmpty() ? "" : "&path=" + BridgeClient.encode(repoPath));

        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/git/diff" + query);
                final String diff = json.optString("diff", "");
                mainHandler.post(() -> {
                    body.removeAllViews();
                    if (diff.trim().isEmpty()) {
                        body.addView(cText("Tidak ada perubahan pada file ini.", 13f, Theme.TEXT_MUTED, false, false));
                    } else {
                        renderDiffLines(body, diff);
                    }
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    body.removeAllViews();
                    body.addView(cText("Gagal: " + ex.getMessage(), 13f, Theme.RED, false, false));
                });
            }
        });

        dialog.setContentView(root);
        dialog.show();
    }

    // Colour-coded diff: green additions, red removals, muted hunk headers.
    private void renderDiffLines(LinearLayout body, String diff) {
        final boolean[] viewed = {false};
        TextView viewedToggle = cText("Tandai sudah dibaca", 13f, Theme.ACCENT, true, false);
        viewedToggle.setPadding(dp(4), dp(4), dp(4), dp(10));
        viewedToggle.setOnClickListener(v -> {
            viewed[0] = !viewed[0];
            viewedToggle.setText(viewed[0] ? "✓ Sudah dibaca" : "Tandai sudah dibaca");
        });
        body.addView(viewedToggle);

        HorizontalScrollView hScroll = new HorizontalScrollView(act);
        hScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout column = new LinearLayout(act);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackground(cBox(Theme.CODE_BG, Theme.BORDER, 1, 12));
        column.setPadding(dp(12), dp(10), dp(12), dp(10));

        for (String line : diff.split("\n")) {
            int color = Theme.TEXT_MUTED;
            int background = Color.TRANSPARENT;
            if (line.startsWith("+++") || line.startsWith("---")) {
                color = Theme.TEXT_LIGHT;
            } else if (line.startsWith("@@")) {
                color = Theme.BLUE;
            } else if (line.startsWith("+")) {
                color = Theme.GREEN;
                background = Theme.GREEN_BG;
            } else if (line.startsWith("-")) {
                color = Theme.RED;
                background = Color.rgb(48, 24, 24);
            } else {
                color = Theme.TEXT_MUTED;
            }

            TextView tv = new TextView(act);
            tv.setText(line.isEmpty() ? " " : line);
            tv.setTextSize(11.5f);
            tv.setTextColor(color);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setSingleLine(true);
            if (background != Color.TRANSPARENT) tv.setBackgroundColor(background);
            tv.setPadding(dp(4), dp(1), dp(8), dp(1));
            column.addView(tv);
        }

        hScroll.addView(column);
        body.addView(hScroll, new LinearLayout.LayoutParams(-1, -2));

        int plus = 0, minus = 0;
        for (String line : diff.split("\n")) {
            if (line.startsWith("+") && !line.startsWith("+++")) plus++;
            if (line.startsWith("-") && !line.startsWith("---")) minus++;
        }
        TextView stats = cText("+" + plus + " / −" + minus + " baris", 12f, Theme.TEXT_MUTED, false, false);
        stats.setPadding(dp(4), dp(8), dp(4), 0);
        body.addView(stats);
    }

    // ============================================================
    // TRANSCRIPT SEARCH
    // ============================================================
    public void showSearchPanel() {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Cari Sesi", true);

        final EditText input = new EditText(act);
        input.setHint("Kata kunci di judul atau transkrip");
        input.setTextSize(14.5f);
        input.setTextColor(Theme.TEXT_MAIN);
        input.setHintTextColor(Theme.TEXT_LIGHT);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackground(cBox(Theme.BG, Theme.BORDER, 1, 14));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(input, new LinearLayout.LayoutParams(-1, -2));

        final LinearLayout results = new LinearLayout(act);
        results.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(act);
        scroll.addView(results);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(400));
        lp.setMargins(0, dp(12), 0, 0);
        root.addView(scroll, lp);

        input.setOnEditorActionListener((v, actionId, event) -> {
            runTranscriptSearch(input.getText().toString().trim(), results, dialog);
            return true;
        });

        dialog.setContentView(root);
        dialog.show();
    }

    private void runTranscriptSearch(final String query, final LinearLayout results, final Dialog dialog) {
        if (query.isEmpty()) return;
        results.removeAllViews();
        results.addView(cText("Mencari...", 13f, Theme.TEXT_MUTED, false, false));

        executor.execute(() -> {
            try {
                // Search stays inside the engine the user is working in.
                JSONObject json = bridge.get("/api/search?q=" + BridgeClient.encode(query)
                        + "&engine=" + BridgeClient.encode(prefs.getString("engine", "antigravity")), 30000);
                mainHandler.post(() -> {
                    results.removeAllViews();
                    JSONArray items = json.optJSONArray("results");
                    if (items == null || items.length() == 0) {
                        results.addView(cText("Tidak ada hasil untuk \"" + query + "\"", 13.5f, Theme.TEXT_MUTED, false, false));
                        return;
                    }
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject r = items.optJSONObject(i);
                        if (r == null) continue;
                        final String convId = r.optString("conversationId");
                        final String title = r.optString("title", "Sesi");

                        LinearLayout card = new LinearLayout(act);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 14));
                        card.setPadding(dp(14), dp(12), dp(14), dp(12));

                        TextView t = cText(title, 14f, Theme.TEXT_MAIN, true, false);
                        t.setSingleLine(true);
                        t.setEllipsize(TextUtils.TruncateAt.END);
                        card.addView(t);

                        TextView snippet = cText(r.optString("snippet", ""), 12.5f, Theme.TEXT_MUTED, false, false);
                        snippet.setMaxLines(2);
                        snippet.setEllipsize(TextUtils.TruncateAt.END);
                        snippet.setPadding(0, dp(4), 0, 0);
                        card.addView(snippet);

                        card.addView(cText(r.optString("engine", "") + " · " + r.optString("matchIn", ""),
                                11f, Theme.TEXT_LIGHT, false, false));

                        card.setOnClickListener(v -> {
                            dialog.dismiss();
                            // Host.openSession sets the back-navigation flag.
                            openSpecificSession(convId, title);
                        });

                        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(-1, -2);
                        lpCard.setMargins(0, 0, 0, dp(10));
                        results.addView(card, lpCard);
                    }
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    results.removeAllViews();
                    results.addView(cText("Gagal: " + ex.getMessage(), 13f, Theme.RED, false, false));
                });
            }
        });
    }

    // ============================================================
    // PROJECT PICKER (multi-workdir)
    // ============================================================
    private String activeProjectPath() {
        return prefs.getString("git_repo_path", "");
    }

    public void showProjectPicker() {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Proyek", true);
        root.addView(cText("Folder proyek di dalam workdir server. Dipakai oleh panel Git dan File.",
                12.5f, Theme.TEXT_MUTED, false, false));

        final LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpList = new LinearLayout.LayoutParams(-1, -2);
        lpList.setMargins(0, dp(12), 0, dp(8));
        root.addView(list, lpList);
        list.addView(cText("Memuat...", 13f, Theme.TEXT_MUTED, false, false));

        executor.execute(() -> {
            try {
                JSONObject json = bridge.get("/api/projects");
                mainHandler.post(() -> renderProjectList(list, dialog, json));
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    list.removeAllViews();
                    list.addView(cText("Gagal: " + ex.getMessage(), 13f, Theme.RED, false, false));
                });
            }
        });

        TextView add = cText("+  Tambah proyek", 14f, Theme.ACCENT, true, false);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(16), dp(13), dp(16), dp(13));
        add.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 12));
        add.setOnClickListener(v -> {
            dialog.dismiss();
            showAddProjectSheet();
        });
        root.addView(add, new LinearLayout.LayoutParams(-1, -2));

        dialog.setContentView(root);
        dialog.show();
    }

    private void renderProjectList(LinearLayout list, final Dialog dialog, JSONObject json) {
        list.removeAllViews();
        final String workdir = json.optString("workdir", "");
        JSONArray projects = json.optJSONArray("projects");

        // The workdir itself is always a valid target.
        list.addView(buildProjectRow("Workdir server", workdir, "", true, dialog, null));

        if (projects != null) {
            for (int i = 0; i < projects.length(); i++) {
                JSONObject p = projects.optJSONObject(i);
                if (p == null) continue;
                final String pPath = p.optString("path");
                String detail = (p.optBoolean("exists", false) ? "" : "tidak ditemukan · ")
                        + (p.optBoolean("isRepo", false) ? "git repo" : "bukan repo");
                list.addView(buildProjectRow(p.optString("name", pPath), detail, pPath,
                        p.optBoolean("exists", false), dialog, pPath));

                if (p.optBoolean("isRepo", false)) {
                    executor.execute(() -> {
                        try {
                            JSONObject status = bridge.get("/api/git/status?path=" + BridgeClient.encode(pPath), 15000);
                            String branch = status.optString("branch", "");
                            int dirty = status.optJSONArray("files") == null ? 0 : status.optJSONArray("files").length();
                            mainHandler.post(() -> {
                                TextView summary = cText(branch + " · " + dirty + " file berubah",
                                        11f, Theme.TEXT_MUTED, false, false);
                                summary.setPadding(dp(44), dp(2), dp(8), dp(8));
                                list.addView(summary);
                            });
                        } catch (Exception ignored) {}
                    });
                }
            }
        }
    }

    private LinearLayout buildProjectRow(String name, String detail, final String path,
                                         boolean enabled, final Dialog dialog, final String removable) {
        boolean isActive = path.equals(activeProjectPath());

        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(cBox(Theme.SURFACE_MUTED, isActive ? Theme.ACCENT : Theme.BORDER, 1, 14));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setAlpha(enabled ? 1f : 0.5f);

        card.addView(cIcon(R.drawable.ic_folder, 18, isActive ? Theme.ACCENT : Theme.TEXT_MUTED));

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(8), 0);
        col.addView(cText(name, 14f, Theme.TEXT_MAIN, true, false));
        TextView sub = cText(detail.isEmpty() ? "-" : detail, 11.5f, Theme.TEXT_LIGHT, false, false);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        col.addView(sub);
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        if (isActive) card.addView(cIcon(R.drawable.ic_check, 18, Theme.ACCENT));

        if (enabled) {
            card.setOnClickListener(v -> {
                prefs.edit().putString("git_repo_path", path).apply();
                refreshSettingsValues();
                dialog.dismiss();
                Toast.makeText(act, "Proyek: " + name, Toast.LENGTH_SHORT).show();
            });
        }

        if (removable != null && !removable.isEmpty()) {
            card.setOnLongClickListener(v -> {
                removeProject(removable);
                dialog.dismiss();
                return true;
            });
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, 0);
        card.setLayoutParams(lp);
        return card;
    }

    private void showAddProjectSheet() {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Tambah Proyek", true);
        root.addView(cText("Path relatif terhadap workdir server, mis. codexcli-remote-app",
                12.5f, Theme.TEXT_MUTED, false, false));

        final EditText nameInput = new EditText(act);
        nameInput.setHint("Nama tampilan (opsional)");
        nameInput.setTextSize(14.5f);
        nameInput.setSingleLine(true);
        nameInput.setTextColor(Theme.TEXT_MAIN);
        nameInput.setHintTextColor(Theme.TEXT_LIGHT);
        nameInput.setBackground(cBox(Theme.BG, Theme.BORDER, 1, 14));
        nameInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lpN = new LinearLayout.LayoutParams(-1, -2);
        lpN.setMargins(0, dp(14), 0, dp(10));
        root.addView(nameInput, lpN);

        final EditText pathInput = new EditText(act);
        pathInput.setHint("path/relatif");
        pathInput.setTextSize(14.5f);
        pathInput.setSingleLine(true);
        pathInput.setTextColor(Theme.TEXT_MAIN);
        pathInput.setHintTextColor(Theme.TEXT_LIGHT);
        pathInput.setBackground(cBox(Theme.BG, Theme.BORDER, 1, 14));
        pathInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lpP = new LinearLayout.LayoutParams(-1, -2);
        lpP.setMargins(0, 0, 0, dp(14));
        root.addView(pathInput, lpP);

        TextView save = cText("Simpan", 14.5f, Theme.ON_ACCENT, true, false);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(16), dp(14), dp(16), dp(14));
        save.setBackground(cBox(Theme.ACCENT, 0, 0, 14));
        save.setOnClickListener(v -> {
            String path = pathInput.getText().toString().trim();
            if (path.isEmpty()) {
                Toast.makeText(act, "Path tidak boleh kosong", Toast.LENGTH_SHORT).show();
                return;
            }
            addProject(nameInput.getText().toString().trim(), path);
            dialog.dismiss();
        });
        root.addView(save, new LinearLayout.LayoutParams(-1, -2));

        dialog.setContentView(root);
        dialog.show();
    }

    private void addProject(final String name, final String path) {
        executor.execute(() -> {
            try {
                JSONObject current = bridge.get("/api/projects");
                JSONArray list = current.optJSONArray("projects");
                if (list == null) list = new JSONArray();

                JSONArray next = new JSONArray();
                for (int i = 0; i < list.length(); i++) {
                    JSONObject p = list.optJSONObject(i);
                    if (p != null) next.put(new JSONObject().put("name", p.optString("name")).put("path", p.optString("path")));
                }
                next.put(new JSONObject().put("name", name.isEmpty() ? path : name).put("path", path));

                JSONObject result = bridge.post("/api/projects", new JSONObject().put("projects", next));
                mainHandler.post(() -> Toast.makeText(act,
                        result.optBoolean("ok", false) ? "Proyek ditambahkan" : describeApiError(result, "Gagal"),
                        Toast.LENGTH_SHORT).show());
            } catch (Exception ex) {
                mainHandler.post(() -> Toast.makeText(act, "Gagal: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void removeProject(final String path) {
        executor.execute(() -> {
            try {
                JSONObject current = bridge.get("/api/projects");
                JSONArray list = current.optJSONArray("projects");
                JSONArray next = new JSONArray();
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject p = list.optJSONObject(i);
                        if (p == null || path.equals(p.optString("path"))) continue;
                        next.put(new JSONObject().put("name", p.optString("name")).put("path", p.optString("path")));
                    }
                }
                bridge.post("/api/projects", new JSONObject().put("projects", next));
                if (path.equals(activeProjectPath())) {
                    mainHandler.post(() -> prefs.edit().putString("git_repo_path", "").apply());
                }
                mainHandler.post(() -> Toast.makeText(act, "Proyek dihapus", Toast.LENGTH_SHORT).show());
            } catch (Exception ex) {
                mainHandler.post(() -> Toast.makeText(act, "Gagal: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ============================================================
    // MAINTENANCE: UPLOADS & AUDIT LOG
    // ============================================================
    public void showMaintenanceSheet() {
        Dialog dialog = createBaseBottomSheet(true);
        LinearLayout root = createBottomSheetRoot(dialog, "Pemeliharaan", true);

        final LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(act);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, dp(420)));
        body.addView(cText("Memuat...", 13f, Theme.TEXT_MUTED, false, false));

        loadMaintenance(body);
        dialog.setContentView(root);
        dialog.show();
    }

    private void loadMaintenance(final LinearLayout body) {
        executor.execute(() -> {
            try {
                final JSONObject uploads = bridge.get("/api/uploads");
                final JSONObject audit = bridge.get("/api/audit?limit=30");
                mainHandler.post(() -> renderMaintenance(body, uploads, audit));
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    body.removeAllViews();
                    body.addView(cText("Gagal: " + ex.getMessage(), 13f, Theme.RED, false, false));
                });
            }
        });
    }

    private void renderMaintenance(final LinearLayout body, JSONObject uploads, JSONObject audit) {
        body.removeAllViews();

        TextView headUploads = cText("Uploads", 13f, Theme.TEXT_MUTED, false, false);
        headUploads.setPadding(0, 0, 0, dp(8));
        body.addView(headUploads);

        JSONArray entries = uploads.optJSONArray("entries");
        int count = entries == null ? 0 : entries.length();
        long totalBytes = uploads.optLong("totalBytes", 0);
        int retention = uploads.optInt("retentionDays", 0);

        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(cBox(Theme.SURFACE_MUTED, Theme.BORDER, 1, 14));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.addView(cText(count + " file · " + humanSize(totalBytes), 14.5f, Theme.TEXT_MAIN, true, false));
        card.addView(cText(retention > 0
                ? "Dihapus otomatis setelah " + retention + " hari"
                : "Penghapusan otomatis dimatikan", 12.5f, Theme.TEXT_MUTED, false, false));

        TextView clean = cText("Bersihkan sekarang", 13.5f, Theme.ACCENT, true, false);
        clean.setGravity(Gravity.CENTER);
        clean.setPadding(dp(14), dp(11), dp(14), dp(11));
        clean.setBackground(cBox(Theme.BG, Theme.BORDER, 1, 12));
        clean.setOnClickListener(v -> runUploadsCleanup(body));
        LinearLayout.LayoutParams lpClean = new LinearLayout.LayoutParams(-1, -2);
        lpClean.setMargins(0, dp(12), 0, 0);
        card.addView(clean, lpClean);
        body.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView headAudit = cText("Aktivitas terakhir", 13f, Theme.TEXT_MUTED, false, false);
        headAudit.setPadding(0, dp(20), 0, dp(8));
        body.addView(headAudit);

        JSONArray log = audit.optJSONArray("entries");
        if (log == null || log.length() == 0) {
            body.addView(cText("Belum ada catatan.", 13f, Theme.TEXT_LIGHT, false, false));
            return;
        }
        for (int i = 0; i < log.length(); i++) {
            JSONObject e = log.optJSONObject(i);
            if (e == null) continue;

            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(7), 0, dp(7));

            String event = e.optString("event", "?");
            row.addView(cText(event, 13.5f, auditColor(event), true, false));

            StringBuilder detail = new StringBuilder(e.optString("at", ""));
            if (e.has("promptPreview")) detail.append(" · ").append(e.optString("promptPreview"));
            else if (e.has("path")) detail.append(" · ").append(e.optString("path"));
            else if (e.has("error")) detail.append(" · ").append(e.optString("error"));

            TextView sub = cText(detail.toString(), 11.5f, Theme.TEXT_LIGHT, false, false);
            sub.setMaxLines(2);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(sub);
            body.addView(row);
        }
    }

    private int auditColor(String event) {
        if (event.contains("failed") || event.contains("rate_limited")) return Theme.RED;
        if (event.startsWith("git.") || event.startsWith("file.")) return Theme.AMBER;
        return Theme.TEXT_MAIN;
    }

    private void runUploadsCleanup(final LinearLayout body) {
        executor.execute(() -> {
            try {
                JSONObject result = bridge.post("/api/uploads/cleanup", new JSONObject(), 30000);
                final int removed = result.optJSONArray("removed") == null ? 0 : result.optJSONArray("removed").length();
                final long freed = result.optLong("freedBytes", 0);
                mainHandler.post(() -> {
                    Toast.makeText(act,
                            removed == 0 ? "Tidak ada file lama" : (removed + " file dihapus · " + humanSize(freed)),
                            Toast.LENGTH_SHORT).show();
                    loadMaintenance(body);
                });
            } catch (Exception ex) {
                mainHandler.post(() -> Toast.makeText(act, "Gagal: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    // Turns a bridge JSON reply into a message worth showing the user.
    public String describeApiError(JSONObject json, String fallback) {
        int status = json.optInt("status", 0);
        if (status == 401 || status == 403) {
            return "Unauthorized — token pairing sudah tidak cocok dengan server. Scan ulang QR pairing.";
        }
        String error = json.optString("error", "");
        if (!error.isEmpty()) return error + (status > 0 ? " (HTTP " + status + ")" : "");
        return fallback;
    }
}
