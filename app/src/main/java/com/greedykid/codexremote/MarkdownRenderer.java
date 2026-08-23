package com.greedykid.codexremote;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Block-aware Markdown renderer for AI output: headings, lists, quotes,
 * tables, fenced code and inline spans, each built as a real view so spacing
 * and wrapping behave instead of being faked with text prefixes.
 */
public class MarkdownRenderer {

    private final Activity activity;

    public MarkdownRenderer(Activity activity) {
        this.activity = activity;
    }

    private int dp(float value) {
        return Theme.dp(activity, value);
    }

    private GradientDrawable box(int fill, int border, int borderWidthDp, float radiusDp) {
        return Theme.box(activity, fill, border, borderWidthDp, radiusDp);
    }

    private TextView text(String value, float sp, int color, boolean bold, boolean serif) {
        return Theme.text(activity, value, sp, color, bold, serif);
    }

    private ImageView icon(int resId, int sizeDp, int tint) {
        return Theme.icon(activity, resId, sizeDp, tint);
    }

    // ============================================================
    // MARKDOWN RENDERER (block-aware, Claude Code aesthetics)
    // ============================================================

    private static final Pattern MD_BULLET = Pattern.compile("^(\\s*)([-*+])\\s+(.*)$");
    private static final Pattern MD_ORDERED = Pattern.compile("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$");
    private static final Pattern MD_RULE = Pattern.compile("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$");
    private static final Pattern MD_INLINE_CODE = Pattern.compile("`([^`\n]+)`");
    private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]\n]*)\\]\\(([^)\\s]+)[^)]*\\)");
    private static final Pattern MD_AUTOLINK = Pattern.compile("(?<![\\w@/.\"'(])(https?://[^\\s<>\\)\\]]+)");
    private static final Pattern MD_BOLD_ITALIC = Pattern.compile("(\\*\\*\\*|___)(.+?)\\1");
    private static final Pattern MD_BOLD = Pattern.compile("(\\*\\*|__)(.+?)\\1");
    private static final Pattern MD_ITALIC_STAR = Pattern.compile("(?<!\\*)\\*([^\\*\n]+?)\\*(?!\\*)");
    private static final Pattern MD_ITALIC_US = Pattern.compile("(?<![a-zA-Z0-9_])_([^_\n]+?)_(?![a-zA-Z0-9_])");
    private static final Pattern MD_STRIKE = Pattern.compile("(~~)([^~\n]+?)\\1");

    // Rounded "pill" background for inline `code`, drawn instead of a flat highlight.
    private class CodePillSpan extends ReplacementSpan {
        private final int pillBg;
        private final int pillFg;

        CodePillSpan(int pillBg, int pillFg) {
            this.pillBg = pillBg;
            this.pillFg = pillFg;
        }

        private Paint textPaint(Paint base) {
            Paint p = new Paint(base);
            p.setTypeface(Typeface.MONOSPACE);
            p.setTextSize(base.getTextSize() * 0.92f);
            return p;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return Math.round(textPaint(paint).measureText(text, start, end)) + dp(11);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                         float x, int top, int y, int bottom, Paint paint) {
            Paint tp = textPaint(paint);
            float w = tp.measureText(text, start, end) + dp(11);
            Paint.FontMetrics fm = paint.getFontMetrics();
            RectF r = new RectF(x, y + fm.ascent - dp(1f), x + w, y + fm.descent + dp(1f));
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(pillBg);
            canvas.drawRoundRect(r, dp(5), dp(5), bg);
            tp.setColor(pillFg);
            canvas.drawText(text, start, end, x + dp(5.5f), y, tp);
        }
    }

    public void render(LinearLayout container, String markdown, boolean isUser) {
        if (markdown == null || markdown.isEmpty()) return;

        String[] sections = markdown.split("```");
        for (int s = 0; s < sections.length; s++) {
            if (s % 2 == 1) {
                renderFencedCodeBlock(container, sections[s]);
            } else {
                renderTextSection(container, sections[s]);
            }
        }
    }

    private void renderTextSection(LinearLayout container, String text) {
        String[] lines = text.split("\n", -1);

        ArrayList<String> tableBuffer = new ArrayList<>();
        ArrayList<String> quoteBuffer = new ArrayList<>();
        ArrayList<String[]> listBuffer = new ArrayList<>();   // {depth, marker, content}
        SpannableStringBuilder paragraph = new SpannableStringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();

            boolean isTable = trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length() > 1;
            boolean isQuote = trimmed.startsWith(">");
            Matcher bullet = MD_BULLET.matcher(line);
            Matcher ordered = MD_ORDERED.matcher(line);
            boolean isBullet = !isTable && bullet.matches();
            boolean isOrdered = !isTable && !isBullet && ordered.matches();
            boolean isRule = MD_RULE.matcher(line).matches();
            boolean isHeading = trimmed.startsWith("#") && trimmed.matches("^#{1,6}\\s+.*$");

            // Any non-matching line closes the open block of a different kind.
            if (!isTable && !tableBuffer.isEmpty()) {
                renderMarkdownTable(container, new ArrayList<>(tableBuffer));
                tableBuffer.clear();
            }
            if (!isQuote && !quoteBuffer.isEmpty()) {
                addQuoteBlock(container, quoteBuffer);
                quoteBuffer.clear();
            }
            if (!isBullet && !isOrdered && !listBuffer.isEmpty()) {
                addListBlock(container, listBuffer);
                listBuffer.clear();
            }

            if (isTable) {
                paragraph = flushParagraph(container, paragraph);
                tableBuffer.add(trimmed);
            } else if (isRule) {
                paragraph = flushParagraph(container, paragraph);
                addRuleBlock(container);
            } else if (isHeading) {
                paragraph = flushParagraph(container, paragraph);
                addHeadingBlock(container, trimmed);
            } else if (isQuote) {
                paragraph = flushParagraph(container, paragraph);
                quoteBuffer.add(trimmed.substring(1).trim());
            } else if (isBullet || isOrdered) {
                paragraph = flushParagraph(container, paragraph);
                String indent = isBullet ? bullet.group(1) : ordered.group(1);
                String marker = isBullet ? "\u2022" : (ordered.group(2) + ".");
                String content = isBullet ? bullet.group(3) : ordered.group(3);
                int depth = Math.min(3, indent.replace("\t", "  ").length() / 2);
                listBuffer.add(new String[]{ String.valueOf(depth), marker, content });
            } else if (trimmed.isEmpty()) {
                paragraph = flushParagraph(container, paragraph);
            } else {
                if (paragraph.length() > 0) paragraph.append("\n");
                paragraph.append(parseInlineMarkdownLine(line));
            }
        }

        if (!tableBuffer.isEmpty()) renderMarkdownTable(container, tableBuffer);
        if (!quoteBuffer.isEmpty()) addQuoteBlock(container, quoteBuffer);
        if (!listBuffer.isEmpty()) addListBlock(container, listBuffer);
        flushParagraph(container, paragraph);
    }

    // ---------- block builders ----------

    private void renderFencedCodeBlock(LinearLayout container, String block) {
        String lang = "";
        String codeContent = block;
        int firstLf = block.indexOf("\n");
        if (firstLf >= 0 && firstLf < 25 && !block.substring(0, firstLf).trim().contains(" ")) {
            lang = block.substring(0, firstLf).trim().toUpperCase(Locale.ROOT);
            codeContent = block.substring(firstLf + 1);
        }

        LinearLayout codeBox = new LinearLayout(activity);
        codeBox.setOrientation(LinearLayout.VERTICAL);
        codeBox.setBackground(box(Theme.CODE_BG, Theme.BORDER, 1, 12));
        codeBox.setClipToOutline(true);

        LinearLayout codeHeader = new LinearLayout(activity);
        codeHeader.setOrientation(LinearLayout.HORIZONTAL);
        codeHeader.setGravity(Gravity.CENTER_VERTICAL);
        codeHeader.setPadding(dp(12), dp(8), dp(8), dp(8));

        TextView langTag = text(lang.isEmpty() ? "CODE" : lang, 10.5f, Theme.ACCENT, true, false);
        langTag.setLetterSpacing(0.12f);
        langTag.setBackground(box(Theme.ACCENT_SOFT, 0, 0, 6));
        langTag.setPadding(dp(7), dp(3), dp(7), dp(3));
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(-2, -2);
        codeHeader.addView(langTag, tagLp);

        codeHeader.addView(new View(activity), new LinearLayout.LayoutParams(0, dp(1), 1));

        LinearLayout copyCodeBtn = new LinearLayout(activity);
        copyCodeBtn.setOrientation(LinearLayout.HORIZONTAL);
        copyCodeBtn.setGravity(Gravity.CENTER_VERTICAL);
        copyCodeBtn.setBackground(box(Theme.SURFACE_MUTED, Theme.BORDER_DARK, 1, 8));
        copyCodeBtn.setPadding(dp(9), dp(5), dp(9), dp(5));
        copyCodeBtn.addView(icon(R.drawable.ic_content_paste, 12, Theme.TEXT_MUTED));
        copyCodeBtn.addView(text("  Copy", 10.5f, Theme.TEXT_MUTED, true, false));
        codeHeader.addView(copyCodeBtn);
        codeBox.addView(codeHeader);

        View headerRule = new View(activity);
        headerRule.setBackgroundColor(Theme.BORDER);
        codeBox.addView(headerRule, new LinearLayout.LayoutParams(-1, dp(1)));

        // Long lines scroll sideways instead of wrapping into unreadable soup.
        final String code = codeContent.replaceAll("\\s+$", "");
        HorizontalScrollView codeScroll = new HorizontalScrollView(activity);
        codeScroll.setHorizontalScrollBarEnabled(false);
        codeScroll.setPadding(dp(12), dp(10), dp(12), dp(12));
        codeScroll.setClipToPadding(false);

        TextView codeView = new TextView(activity);
        codeView.setText(code);
        codeView.setTextSize(12.5f);
        codeView.setTextColor(Color.rgb(240, 240, 245));
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setTextIsSelectable(true);
        codeView.setLineSpacing(0, 1.2f);
        codeView.setHorizontallyScrolling(true);
        codeScroll.addView(codeView, new FrameLayout.LayoutParams(-2, -2));
        codeBox.addView(codeScroll, new LinearLayout.LayoutParams(-1, -2));

        final String copyCode = code.trim();
        copyCodeBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Code snippet", copyCode));
            Toast.makeText(activity, "Kode snippet disalin", Toast.LENGTH_SHORT).show();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, dp(10));
        container.addView(codeBox, lp);
    }

    private void addHeadingBlock(LinearLayout container, String trimmed) {
        int level = 0;
        while (level < trimmed.length() && trimmed.charAt(level) == '#') level++;
        String body = trimmed.substring(level).trim();
        if (body.isEmpty()) return;
        level = Math.min(6, Math.max(1, level));

        float size;
        int color = Theme.TEXT_MAIN;
        switch (level) {
            case 1: size = 20f; break;
            case 2: size = 17.5f; break;
            case 3: size = 15.5f; break;
            default:
                size = 14.5f;
                color = Theme.TEXT_MUTED;
                break;
        }

        TextView h = new TextView(activity);
        h.setText(parseInlineMarkdownLine(body));
        h.setTextSize(size);
        h.setTextColor(color);
        h.setTypeface(level <= 2 ? Typeface.SERIF : Typeface.SANS_SERIF, Typeface.BOLD);
        h.setLineSpacing(0, 1.15f);
        h.setTextIsSelectable(true);
        if (level >= 4) h.setLetterSpacing(0.04f);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(level <= 2 ? 16 : 12), 0, dp(level <= 2 ? 6 : 4));
        container.addView(h, lp);

        // H1/H2 get a hairline rule, which is what separates sections at a glance.
        if (level <= 2) {
            View rule = new View(activity);
            rule.setBackgroundColor(Theme.BORDER);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(1));
            rlp.setMargins(0, 0, 0, dp(8));
            container.addView(rule, rlp);
        }
    }

    private void addRuleBlock(LinearLayout container) {
        View divider = new View(activity);
        divider.setBackgroundColor(Theme.BORDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(dp(4), dp(14), dp(4), dp(14));
        container.addView(divider, lp);
    }

    private void addQuoteBlock(LinearLayout container, ArrayList<String> quoteLines) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        for (String q : quoteLines) {
            if (q.isEmpty()) continue;
            if (ssb.length() > 0) ssb.append("\n");
            ssb.append(parseInlineMarkdownLine(q));
        }
        if (ssb.length() == 0) return;

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(box(Theme.SURFACE, 0, 0, 10));

        View bar = new View(activity);
        bar.setBackgroundColor(Theme.ACCENT);
        row.addView(bar, new LinearLayout.LayoutParams(dp(3), -1));

        TextView body = new TextView(activity);
        body.setText(ssb);
        body.setTextSize(14f);
        body.setTextColor(Theme.TEXT_MUTED);
        body.setTypeface(Typeface.SERIF, Typeface.ITALIC);
        body.setLineSpacing(0, 1.3f);
        body.setPadding(dp(12), dp(10), dp(12), dp(10));
        applyTextInteractions(body, ssb);
        row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        container.addView(row, lp);
    }

    private void addListBlock(LinearLayout container, ArrayList<String[]> items) {
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);

        for (String[] item : items) {
            int depth = Integer.parseInt(item[0]);
            String marker = item[1];
            String content = item[2];

            boolean checked = false;
            boolean isTask = false;
            if (content.startsWith("[x] ") || content.startsWith("[X] ")) {
                isTask = true;
                checked = true;
                content = content.substring(4);
            } else if (content.startsWith("[ ] ")) {
                isTask = true;
                content = content.substring(4);
            }

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(2 + depth * 16), dp(3), 0, dp(3));

            // Hanging indent: marker in a fixed gutter so wrapped text lines up.
            TextView markerView = new TextView(activity);
            if (isTask) {
                markerView.setText(checked ? "\u2611" : "\u2610");
                markerView.setTextColor(checked ? Theme.GREEN : Theme.TEXT_LIGHT);
                markerView.setTextSize(14.5f);
            } else {
                markerView.setText(depth == 0 ? marker : (marker.equals("\u2022") ? "\u25E6" : marker));
                markerView.setTextColor(Theme.ACCENT);
                markerView.setTextSize(marker.equals("\u2022") ? 15f : 13.5f);
                markerView.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
            }
            markerView.setGravity(Gravity.START);
            markerView.setPadding(0, dp(1), dp(8), 0);
            markerView.setMinWidth(dp(marker.length() > 2 ? 26 : 18));
            row.addView(markerView, new LinearLayout.LayoutParams(-2, -2));

            SpannableStringBuilder span = parseInlineMarkdownLine(content);
            TextView body = new TextView(activity);
            body.setText(span);
            body.setTextSize(14.5f);
            body.setTextColor(checked ? Theme.TEXT_MUTED : Theme.TEXT_MAIN);
            body.setLineSpacing(0, 1.28f);
            applyTextInteractions(body, span);
            row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));

            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(6));
        container.addView(list, lp);
    }

    private SpannableStringBuilder flushParagraph(LinearLayout container, SpannableStringBuilder ssb) {
        flushTextBlockToContainer(container, ssb);
        return new SpannableStringBuilder();
    }

    private void flushTextBlockToContainer(LinearLayout container, SpannableStringBuilder ssb) {
        if (ssb == null || ssb.length() == 0) return;
        TextView p = new TextView(activity);
        p.setText(ssb);
        p.setTextSize(14.5f);
        p.setTextColor(Theme.TEXT_MAIN);
        p.setLineSpacing(0, 1.35f);
        applyTextInteractions(p, ssb);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(3), 0, dp(5));
        container.addView(p, lp);
    }

    // Links need LinkMovementMethod, which cannot coexist with text selection —
    // so link-bearing blocks fall back to long-press-to-copy.
    private void applyTextInteractions(TextView tv, SpannableStringBuilder ssb) {
        boolean hasLink = ssb.getSpans(0, ssb.length(), ClickableSpan.class).length > 0;
        if (hasLink) {
            tv.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            tv.setTextIsSelectable(true);
        }
        final String rawText = ssb.toString();
        tv.setOnLongClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Chat text", rawText));
            Toast.makeText(activity, "Teks disalin ke clipboard", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    // ---------- inline parsing ----------

    private boolean isInsideCodePill(SpannableStringBuilder ssb, int pos) {
        if (pos < 0 || pos >= ssb.length()) return false;
        CodePillSpan[] spans = ssb.getSpans(pos, pos + 1, CodePillSpan.class);
        return spans != null && spans.length > 0;
    }

    private SpannableStringBuilder parseInlineMarkdownLine(String rawLine) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(rawLine == null ? "" : rawLine);
        if (ssb.length() == 0) return ssb;

        // 1. Inline code first — its contents are literal and must survive later passes.
        try {
            Matcher m = MD_INLINE_CODE.matcher(ssb);
            int from = 0;
            while (from < ssb.length() && m.find(from)) {
                int start = m.start();
                String inner = m.group(1);
                ssb.replace(start, m.end(), inner);
                ssb.setSpan(new CodePillSpan(Theme.SURFACE_MUTED, Theme.ACCENT),
                        start, start + inner.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                from = start + inner.length();
                m = MD_INLINE_CODE.matcher(ssb);
            }
        } catch (Throwable ignored) {}

        // 2. Markdown links [label](url)
        try {
            Matcher m = MD_LINK.matcher(ssb);
            int from = 0;
            while (from < ssb.length() && m.find(from)) {
                int start = m.start();
                int end = m.end();
                if (isInsideCodePill(ssb, start) || isInsideCodePill(ssb, end - 1)) {
                    from = end;
                    m = MD_LINK.matcher(ssb);
                    continue;
                }
                String label = m.group(1);
                final String url = m.group(2);
                if (label == null || label.isEmpty()) label = url;
                ssb.replace(start, end, label);
                applyLinkSpan(ssb, start, start + label.length(), url);
                from = start + label.length();
                m = MD_LINK.matcher(ssb);
            }
        } catch (Throwable ignored) {}

        // 3. Bare URLs left over
        try {
            Matcher m = MD_AUTOLINK.matcher(ssb);
            int from = 0;
            while (from < ssb.length() && m.find(from)) {
                int start = m.start(1);
                int end = m.end(1);
                if (!isInsideCodePill(ssb, start) && !isInsideCodePill(ssb, end - 1)
                        && ssb.getSpans(start, end, ClickableSpan.class).length == 0) {
                    applyLinkSpan(ssb, start, end, m.group(1));
                }
                from = end;
            }
        } catch (Throwable ignored) {}

        applyDelimiterStyle(ssb, MD_BOLD_ITALIC, 3, new StyleSpan(Typeface.BOLD_ITALIC));
        applyDelimiterStyle(ssb, MD_BOLD, 2, new StyleSpan(Typeface.BOLD));
        applyDelimiterStyle(ssb, MD_ITALIC_STAR, 1, new StyleSpan(Typeface.ITALIC));
        applyDelimiterStyle(ssb, MD_ITALIC_US, 1, new StyleSpan(Typeface.ITALIC));
        applyDelimiterStyle(ssb, MD_STRIKE, 2, new StrikethroughSpan());

        return ssb;
    }

    private void applyLinkSpan(SpannableStringBuilder ssb, int start, int end, final String url) {
        ssb.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Throwable t) {
                    Toast.makeText(activity, "Tidak bisa membuka tautan", Toast.LENGTH_SHORT).show();
                }
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(Theme.ACCENT), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // Delimiters are stripped by deleting opening and closing markers cleanly,
    // preserving any child spans (like CodePillSpan, links, or nested styles) inside.
    private void applyDelimiterStyle(SpannableStringBuilder ssb, Pattern pattern, int markerLen, Object protoSpan) {
        try {
            Matcher m = pattern.matcher(ssb);
            int from = 0;
            while (from < ssb.length() && m.find(from)) {
                int start = m.start();
                int end = m.end();
                if (isInsideCodePill(ssb, start) || isInsideCodePill(ssb, end - 1)) {
                    from = end;
                    m = pattern.matcher(ssb);
                    continue;
                }

                int actualMarkerLen = markerLen;
                if (m.groupCount() >= 1 && m.group(1) != null) {
                    String g1 = m.group(1);
                    if (g1.equals("***") || g1.equals("___")) {
                        actualMarkerLen = 3;
                    } else if (g1.equals("**") || g1.equals("__") || g1.equals("~~")) {
                        actualMarkerLen = 2;
                    } else if (g1.equals("*") || g1.equals("_")) {
                        actualMarkerLen = 1;
                    }
                }

                // Delete closing delimiter first so earlier offsets don't shift
                ssb.delete(end - actualMarkerLen, end);
                // Delete opening delimiter
                ssb.delete(start, start + actualMarkerLen);

                int spanEnd = end - (actualMarkerLen * 2);
                if (spanEnd > start) {
                    ssb.setSpan(cloneSpan(protoSpan), start, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    from = spanEnd;
                } else {
                    from = start;
                }
                m = pattern.matcher(ssb);
            }
        } catch (Throwable ignored) {}
    }

    private Object cloneSpan(Object proto) {
        if (proto instanceof StyleSpan) return new StyleSpan(((StyleSpan) proto).getStyle());
        if (proto instanceof StrikethroughSpan) return new StrikethroughSpan();
        return proto;
    }

    // ---------- tables ----------

    private void renderMarkdownTable(LinearLayout container, ArrayList<String> tableLines) {
        if (tableLines.size() < 2) return;

        String[] headers = splitTableRow(tableLines.get(0));
        int colCount = headers.length;
        if (colCount == 0) return;

        // Column alignment comes from the separator row (:---, :---:, ---:).
        int[] align = new int[colCount];
        for (int i = 0; i < colCount; i++) align[i] = Gravity.START;
        int bodyStart = 1;
        if (tableLines.size() > 1 && isTableSeparator(tableLines.get(1))) {
            String[] spec = splitTableRow(tableLines.get(1));
            for (int c = 0; c < colCount && c < spec.length; c++) {
                String sp = spec[c];
                boolean left = sp.startsWith(":");
                boolean right = sp.endsWith(":");
                align[c] = (left && right) ? Gravity.CENTER_HORIZONTAL : (right ? Gravity.END : Gravity.START);
            }
            bodyStart = 2;
        }

        HorizontalScrollView hScroll = new HorizontalScrollView(activity);
        hScroll.setHorizontalScrollBarEnabled(false);

        LinearLayout tableLayout = new LinearLayout(activity);
        tableLayout.setOrientation(LinearLayout.VERTICAL);
        tableLayout.setBackground(box(Theme.SURFACE, Theme.BORDER, 1, 10));
        tableLayout.setClipToOutline(true);

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setBackgroundColor(Theme.SURFACE_MUTED);
        for (int c = 0; c < colCount; c++) {
            TextView cell = text(headers[c].toUpperCase(Locale.ROOT), 11f, Theme.TEXT_MUTED, true, false);
            cell.setLetterSpacing(0.07f);
            cell.setGravity(align[c]);
            cell.setPadding(dp(14), dp(11), dp(14), dp(11));
            cell.setMinWidth(dp(88));
            headerRow.addView(cell, new LinearLayout.LayoutParams(-2, -2));
        }
        tableLayout.addView(headerRow);

        View headRule = new View(activity);
        headRule.setBackgroundColor(Theme.BORDER_DARK);
        tableLayout.addView(headRule, new LinearLayout.LayoutParams(-1, dp(1)));

        int printed = 0;
        for (int r = bodyStart; r < tableLines.size(); r++) {
            String rowLine = tableLines.get(r);
            if (isTableSeparator(rowLine)) continue;

            String[] cells = splitTableRow(rowLine);
            if (printed > 0) {
                View div = new View(activity);
                div.setBackgroundColor(Theme.BORDER);
                tableLayout.addView(div, new LinearLayout.LayoutParams(-1, dp(1)));
            }

            LinearLayout dataRow = new LinearLayout(activity);
            dataRow.setOrientation(LinearLayout.HORIZONTAL);
            dataRow.setBackgroundColor(printed % 2 == 0 ? Theme.SURFACE : Theme.BG);

            for (int c = 0; c < colCount; c++) {
                String val = c < cells.length ? cells[c] : "";
                SpannableStringBuilder span = parseInlineMarkdownLine(val);
                TextView cell = new TextView(activity);
                cell.setText(span);
                cell.setTextSize(13f);
                cell.setTextColor(Theme.TEXT_MAIN);
                cell.setLineSpacing(0, 1.2f);
                cell.setGravity(align[c]);
                cell.setPadding(dp(14), dp(10), dp(14), dp(10));
                cell.setMinWidth(dp(88));
                dataRow.addView(cell, new LinearLayout.LayoutParams(-2, -2));
            }
            tableLayout.addView(dataRow);
            printed++;
        }

        hScroll.addView(tableLayout);
        LinearLayout.LayoutParams lpH = new LinearLayout.LayoutParams(-1, -2);
        lpH.setMargins(0, dp(10), 0, dp(12));
        container.addView(hScroll, lpH);
    }

    private boolean isTableSeparator(String row) {
        String stripped = row.replace("|", "").replace("-", "").replace(":", "").replace(" ", "");
        return stripped.isEmpty() && row.contains("-");
    }

    private String[] splitTableRow(String row) {
        String trimmed = row.trim();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        String[] parts = trimmed.split("\\|", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }
}
