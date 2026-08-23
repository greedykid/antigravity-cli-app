package com.greedykid.codexremote;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Single source of truth for the Claude Code palette and the small view
 * builders every screen uses. MainActivity aliases these constants so the
 * palette can be retuned in one place.
 */
public final class Theme {

    private Theme() {}

    public static final int BG = Color.rgb(24, 24, 23);               // #181817
    public static final int SURFACE = Color.rgb(33, 32, 30);          // #21201E
    public static final int SURFACE_MUTED = Color.rgb(42, 41, 38);    // #2A2926
    public static final int BORDER = Color.rgb(48, 46, 43);           // #302E2B
    public static final int BORDER_DARK = Color.rgb(62, 60, 56);      // #3E3C38
    public static final int CODE_BG = Color.rgb(18, 18, 18);          // #121212

    public static final int TEXT_MAIN = Color.rgb(237, 236, 232);     // #EDECE8
    public static final int TEXT_MUTED = Color.rgb(158, 157, 153);    // #9E9D99
    public static final int TEXT_LIGHT = Color.rgb(112, 111, 108);    // #706F6C

    public static final int TERRACOTTA = Color.rgb(217, 107, 67);     // #D96B43
    public static final int TERRACOTTA_LIGHT = Color.rgb(56, 36, 29); // #38241D
    public static final int GREEN = Color.rgb(76, 175, 80);
    public static final int GREEN_BG = Color.rgb(27, 48, 30);
    public static final int AMBER = Color.rgb(245, 158, 11);
    public static final int AMBER_BG = Color.rgb(51, 38, 15);
    public static final int RED = Color.rgb(239, 68, 68);
    public static final int BLUE = Color.rgb(59, 130, 246);

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
        v.setTypeface(serif ? Typeface.SERIF : Typeface.SANS_SERIF, bold ? Typeface.BOLD : Typeface.NORMAL);
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
