package com.greedykid.codexremote;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Centralises task notification channel and smart alert formatting. */
final class TaskNotificationManager {
    static final String CHANNEL_TASK_ALERTS = "channel_ai_task_alerts";
    private static final int NOTIFY_RESULT_ID = 8821;

    private final Context context;

    TaskNotificationManager(Context context) {
        this.context = context;
    }

    void createTaskChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_TASK_ALERTS, "Task Alerts", android.app.NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Memberi tahu saat tugas AI selesai di background");
        ch.enableLights(true);
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
    }

    void showTaskCompletion(String sessionTitle, boolean success, String details,
                            String conversationId) {
        try {
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (conversationId != null && !conversationId.isEmpty()) {
                intent.putExtra("open_conversation_id", conversationId);
            }
            PendingIntent pi = PendingIntent.getActivity(context, 100, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            boolean authError = details != null &&
                    (details.contains("Unauthorized") || details.contains("401"));
            String emoji = success ? "✅" : (authError ? "🔒" : "⚠️");
            String label = success ? "Tugas Selesai" : (authError ? "Autentikasi Gagal" : "Tugas Gagal");
            String title = emoji + " " + label + ": " +
                    (sessionTitle != null && !sessionTitle.isEmpty() ? sessionTitle : "Antigravity");
            String message = details != null && !details.isEmpty() ? details :
                    success ? "AI telah selesai menjalankan tugas coding Anda."
                            : "Terjadi kendala saat menjalankan tugas.";

            androidx.core.app.NotificationCompat.Builder builder =
                    new androidx.core.app.NotificationCompat.Builder(context, CHANNEL_TASK_ALERTS)
                            .setSmallIcon(authError ? R.drawable.ic_security : R.drawable.ic_code)
                            .setContentTitle(title)
                            .setContentText(message)
                            .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                            .setAutoCancel(true)
                            .setContentIntent(pi);
            nm.notify(NOTIFY_RESULT_ID, builder.build());
        } catch (Exception ignored) {}
    }
}
