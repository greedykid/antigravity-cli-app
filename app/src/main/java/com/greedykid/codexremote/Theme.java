package com.greedykid.codexremote;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The app's palette and small view builders.
 *
 * Each CLI gets its own visual identity, so these are live values rather than
 * constants: {@link #applyEngine} swaps the whole set and the UI is rebuilt
 * around it. Antigravity keeps the warm ivory-and-terracotta look; Codex gets a
 * cooler slate ground with a teal-green accent, so a glance at the screen is
 * enough to know which engine will run the next prompt.
 */
public final class Theme {

    public static final String ENGINE_ANTIGRAVITY = "antigravity";
    public static final String ENGINE_CODEX = "codex";
    public static final String ENGINE_OPENCODE = "opencode";

    private static String engine = ENGINE_ANTIGRAVITY;

    // ---- live palette ----
    public static int BG;
    public static int SURFACE;
    public static int SURFACE_MUTED;
    public static int BORDER;
    public static int BORDER_DARK;
    public static int CODE_BG;

    public static int TEXT_MAIN;
    public static int TEXT_MUTED;
    public static int TEXT_LIGHT;

    /** Primary brand colour of the active engine. */
    public static int ACCENT;
    /** Tinted background that pairs with {@link #ACCENT}. */
    public static int ACCENT_SOFT;
    /**
     * Label colour for text sitting on an {@link #ACCENT} fill.
     */
    public static int ON_ACCENT;

    public static int GREEN;
    public static int GREEN_BG;
    public static int AMBER;
    public static int AMBER_BG;
    public static int RED;
    public static int RED_BG;
    public static int BLUE;
    public static int BLUE_BG;

    static {
        applyEngine(ENGINE_ANTIGRAVITY);
    }

    private Theme() {}

    public static boolean isCodex() {
        return ENGINE_CODEX.equalsIgnoreCase(engine);
    }

    public static boolean isOpenCode() {
        return ENGINE_OPENCODE.equalsIgnoreCase(engine);
    }

    public static String engine() {
        return engine;
    }

    public static void applyEngine(String value) {
        if (ENGINE_CODEX.equalsIgnoreCase(value)) {
            engine = ENGINE_CODEX;
            applyCodexPalette();
        } else if (ENGINE_OPENCODE.equalsIgnoreCase(value)) {
            engine = ENGINE_OPENCODE;
            applyOpenCodePalette();
        } else {
            engine = ENGINE_ANTIGRAVITY;
            applyAntigravityPalette();
        }
    }

    /** Warm neutral ground, terracotta accent. */
    private static void applyAntigravityPalette() {
        BG = Color.rgb(24, 24, 23);               // #181817
        SURFACE = Color.rgb(33, 32, 30);          // #21201E
        SURFACE_MUTED = Color.rgb(42, 41, 38);    // #2A2926
        BORDER = Color.rgb(48, 46, 43);           // #302E2B
        BORDER_DARK = Color.rgb(62, 60, 56);      // #3E3C38
        CODE_BG = Color.rgb(18, 18, 18);          // #121212

        TEXT_MAIN = Color.rgb(237, 236, 232);     // #EDECE8
        TEXT_MUTED = Color.rgb(158, 157, 153);    // #9E9D99
        TEXT_LIGHT = Color.rgb(112, 111, 108);    // #706F6C

        ACCENT = Color.rgb(217, 107, 67);         // #D96B43
        ACCENT_SOFT = Color.rgb(56, 36, 29);      // #38241D
        ON_ACCENT = Color.WHITE;

        GREEN = Color.rgb(76, 175, 80);
        GREEN_BG = Color.rgb(27, 48, 30);
        AMBER = Color.rgb(245, 158, 11);
        AMBER_BG = Color.rgb(51, 38, 15);
        RED = Color.rgb(239, 68, 68);
        RED_BG = Color.rgb(54, 23, 24);
        BLUE = Color.rgb(59, 130, 246);
        BLUE_BG = Color.rgb(24, 40, 68);
    }

    /** Cool slate ground, teal-green accent. */
    private static void applyCodexPalette() {
        BG = Color.rgb(19, 20, 23);               // #131417
        SURFACE = Color.rgb(27, 29, 33);          // #1B1D21
        SURFACE_MUTED = Color.rgb(36, 39, 44);    // #24272C
        BORDER = Color.rgb(46, 50, 58);           // #2E323A
        BORDER_DARK = Color.rgb(59, 64, 73);      // #3B4049
        CODE_BG = Color.rgb(12, 13, 15);          // #0C0D0F

        TEXT_MAIN = Color.rgb(236, 238, 242);     // #ECEEF2
        TEXT_MUTED = Color.rgb(156, 163, 175);    // #9CA3AF
        TEXT_LIGHT = Color.rgb(107, 114, 128);    // #6B7280

        ACCENT = Color.rgb(16, 163, 127);         // #10A37F
        ACCENT_SOFT = Color.rgb(14, 42, 36);      // #0E2A24
        ON_ACCENT = Color.rgb(8, 12, 15);         // #08120F

        GREEN = Color.rgb(52, 199, 137);
        GREEN_BG = Color.rgb(13, 43, 33);
        AMBER = Color.rgb(233, 165, 61);
        AMBER_BG = Color.rgb(45, 36, 17);
        RED = Color.rgb(238, 92, 92);
        RED_BG = Color.rgb(54, 23, 24);
        BLUE = Color.rgb(90, 156, 248);
        BLUE_BG = Color.rgb(20, 36, 62);
    }

    /** Modern violet/indigo theme for OpenCode multi-provider. */
    private static void applyOpenCodePalette() {
        BG = Color.rgb(18, 18, 26);               // #12121A
        SURFACE = Color.rgb(26, 26, 38);          // #1A1A26
        SURFACE_MUTED = Color.rgb(36, 36, 52);    // #242434
        BORDER = Color.rgb(50, 50, 72);           // #323248
        BORDER_DARK = Color.rgb(65, 65, 95);      // #41415F
        CODE_BG = Color.rgb(12, 12, 18);          // #0C0C12

        TEXT_MAIN = Color.rgb(238, 238, 248);     // #EEEEF8
        TEXT_MUTED = Color.rgb(160, 160, 188);    // #A0A0BC
        TEXT_LIGHT = Color.rgb(115, 115, 145);    // #737391

        ACCENT = Color.rgb(139, 92, 246);         // #8B5CF6 (Purple/Violet)
        ACCENT_SOFT = Color.rgb(38, 28, 68);      // #261C44
        ON_ACCENT = Color.WHITE;

        GREEN = Color.rgb(52, 199, 137);
        GREEN_BG = Color.rgb(13, 43, 33);
        AMBER = Color.rgb(233, 165, 61);
        AMBER_BG = Color.rgb(45, 36, 17);
        RED = Color.rgb(238, 92, 92);
        RED_BG = Color.rgb(54, 23, 24);
        BLUE = Color.rgb(90, 156, 248);
        BLUE_BG = Color.rgb(20, 36, 62);
    }

    // ---- engine identity ----

    /** Short mark used where space is tight. */
    public static String wordmark() {
        if (isCodex()) return "Codex";
        if (isOpenCode()) return "OpenCode";
        return "Antigravity";
    }

    /** Full product name for the sidebar header. */
    public static String sidebarTitle() {
        return wordmark() + " CLI Remote";
    }

    /** Title shown on the empty-session screen. */
    public static String brandTitle() {
        if (isCodex()) return "Codex Remote";
        if (isOpenCode()) return "OpenCode Remote";
        return "Antigravity Code";
    }

    public static String engineLabel() {
        if (isCodex()) return "Codex CLI";
        if (isOpenCode()) return "OpenCode CLI";
        return "Antigravity CLI";
    }

    public static String engineShortLabel() {
        if (isCodex()) return "Codex";
        if (isOpenCode()) return "OpenCode";
        return "Agy";
    }

    public static String engineRepo() {
        if (isCodex()) return "openai/codex-cli";
        if (isOpenCode()) return "opencode-ai/cli";
        return "google/antigravity-cli";
    }

    public static String engineTagline() {
        if (isCodex()) return "Siap membantu. Ketik perintah untuk memulai sesi Codex.";
        if (isOpenCode()) return "Siap membantu. Ketik perintah untuk memulai sesi OpenCode.";
        return "Siap membantu. Ketik perintah untuk memulai sesi Antigravity.";
    }

    public static int mascotRes() {
        return isCodex() ? R.drawable.ic_mascot_codex : R.drawable.ic_mascot_character;
    }

    public static int engineIconRes() {
        return isCodex() ? R.drawable.ic_code : R.drawable.ic_spark;
    }

    /**
     * Antigravity leans on a serif voice; Codex reads as a developer tool, so
     * its headings stay sans-serif.
     */
    public static boolean headingsUseSerif() {
        return !isCodex();
    }

    public static Typeface headingTypeface() {
        return headingsUseSerif() ? Typeface.SERIF : Typeface.SANS_SERIF;
    }

    // ---- view builders ----

    public static int dp(Context ctx, float value) {
        return (int) (value * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static GradientDrawable box(Context ctx, int fillColor, int borderColor, int borderWidthDp, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        if (borderWidthDp > 0 && borderColor != 0) {
            d.setStroke(dp(ctx, borderWidthDp), borderColor);
        }
        d.setCornerRadius(dp(ctx, radiusDp));
        return d;
    }

    public static TextView text(Context ctx, String value, float sp, int color, boolean bold, boolean serif) {
        TextView v = new TextView(ctx);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        // "serif" at the call site means "this is a heading"; which face that
        // maps to is an engine decision.
        v.setTypeface(serif ? headingTypeface() : Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL);
        return v;
    }

    public static ImageView icon(Context ctx, int resId, int sizeDp, int tintColor) {
        ImageView iv = new ImageView(ctx);
        iv.setImageResource(resId);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (tintColor != 0) iv.setColorFilter(tintColor);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp)));
        return iv;
    }

    public static ImageView iconButton(Context ctx, int resId, int sizeDp, int touchSizeDp, int tintColor) {
        ImageView iv = new ImageView(ctx);
        iv.setImageResource(resId);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (tintColor != 0) iv.setColorFilter(tintColor);
        int pad = dp(ctx, Math.max(0, (touchSizeDp - sizeDp) / 2f));
        iv.setPadding(pad, pad, pad, pad);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(ctx, touchSizeDp), dp(ctx, touchSizeDp)));
        return iv;
    }
}
