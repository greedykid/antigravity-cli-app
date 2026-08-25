package com.greedykid.codexremote;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class NotificationActionReceiver extends BroadcastReceiver {
    public static final String ACTION_COPY_TEXT = "com.greedykid.codexremote.ACTION_COPY_TEXT";
    public static final String EXTRA_TEXT_TO_COPY = "extra_text_to_copy";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_COPY_TEXT.equals(action)) {
            String text = intent.getStringExtra(EXTRA_TEXT_TO_COPY);
            if (text != null && !text.isEmpty()) {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("AI Result", text));
                    Toast.makeText(context, "Ringkasan tugas disalin ke clipboard ✓", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
