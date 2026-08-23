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
     * Label colour for text sitting on an {@link #ACCENT} fill. White reads
     * fine on terracotta but only reaches 3.2:1 on the Codex green, so that
     * engine uses a near-black label instead (5.95:1).
     */
    public static int ON_ACCENT;

    public static int GREEN;
    public static int GREEN_BG;
    public static int AMBER;
    public static int AMBER_BG;
    public static int RED;
    public static int RED_BG;
    public static int BLUE;

    static {
        applyEngine(ENGINE_ANTIGRAVITY);
    }

    private Theme() {}

    public static boolean isCodex() {
        return ENGINE_CODEX.equalsIgnoreCase(engine);
    }

    public static String engine() {
        return engine;
    }

    public static void applyEngine(String value) {
        engine = ENGINE_CODEX.equalsIgnoreCase(value) ? ENGINE_CODEX : ENGINE_ANTIGRAVITY;
        if (isCodex()) {
            applyCodexPalette();
        } else {
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
    }

    /** Cool slate ground, teal-green accent. */
    private static void applyCodexPalette() {
        BG = Color.rgb(19, 20, 23);               // #131417
        SURFACE = Color.rgb(27, 29, 33);          // #1B1D21
        SURFACE_MUTED = Color.rgb(36, 39, 44);    // #24272C
        BORDER = Color.rgb(46, 50, 58);           // #2E323A
        BORDER_DARK = Color.rgb(59, 64, 73);      // #3B4049
        CODE_BG = Color.rgb(14, 16, 19);          // #0E1013

        TEXT_MAIN = Color.rgb(231, 234, 238);     // #E7EAEE
        TEXT_MUTED = Color.rgb(152, 160, 172);    // #98A0AC
        TEXT_LIGHT = Color.rgb(107, 114, 128);    // #6B7280

        ACCENT = Color.rgb(16, 163, 127);         // #10A37F
        ACCENT_SOFT = Color.rgb(14, 42, 36);      // #0E2A24
        ON_ACCENT = Color.rgb(8, 18, 15);         // #08120F

        GREEN = Color.rgb(52, 199, 137);
        GREEN_BG = Color.rgb(13, 43, 33);
        AMBER = Color.rgb(233, 165, 61);
        AMBER_BG = Color.rgb(45, 36, 17);
        RED = Color.rgb(238, 92, 92);
        RED_BG = Color.rgb(54, 23, 24);
        BLUE = Color.rgb(90, 156, 248);
    }

    // ---- engine identity ----

    public static String wordmark() {
        return isCodex() ? "Codex" : "Antigravity";
    }

    /** Title shown on the empty-session screen. */
    public static String brandTitle() {
        return isCodex() ? "Codex Remote" : "Antigravity Code";
    }

    public static String engineLabel() {
        return isCodex() ? "Codex CLI" : "Antigravity CLI";
    }

    public static String engineShortLabel() {
        return isCodex() ? "Codex" : "Agy";
    }

    public static String engineRepo() {
        return isCodex() ? "openai/codex-cli" : "google/antigravity-cli";
    }

    public static String engineTagline() {
        return isCodex()
                ? "Siap membantu. Ketik perintah untuk memulai sesi Codex."
                : "Siap membantu. Ketik perintah untuk memulai sesi Antigravity.";
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
}
