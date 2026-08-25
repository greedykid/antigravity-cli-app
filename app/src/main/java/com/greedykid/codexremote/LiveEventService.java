package com.greedykid.codexremote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds the Server-Sent Events connection while the app is backgrounded and
 * raises a notification when a task finishes, so a long prompt can be sent and
 * the phone locked without losing track of it.
 */
public class LiveEventService extends Service {

    public static final String ACTION_START = "com.greedykid.codexremote.START_EVENTS";
    public static final String ACTION_STOP = "com.greedykid.codexremote.STOP_EVENTS";
    /** Re-open the stream against a new server without tearing the service down. */
    public static final String ACTION_RECONNECT = "com.greedykid.codexremote.RECONNECT_EVENTS";

    private static final String CHANNEL_ONGOING = "codex_remote_ongoing";
    private static final String CHANNEL_ALERTS = "codex_remote_alerts";
    private static final int NOTIFY_ONGOING = 4101;
    private static final int NOTIFY_RESULT = 4102;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;
    private BridgeClient client;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = getSharedPreferences("connection", Context.MODE_PRIVATE);
        client = new BridgeClient(prefs);
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // startForegroundService() gives us five seconds to call startForeground()
        // or the system kills the process with ForegroundServiceDidNotStartInTime
        // — an exception thrown on the looper that no try/catch of ours can see.
        // Satisfying the contract first, before deciding what to do, makes that
        // impossible even when a stop and a start race each other.
        try {
            startForeground(NOTIFY_ONGOING, buildOngoingNotification("Memantau sesi CLI"));
        } catch (Throwable t) {
            // Android 12+ refuses a foreground start from the background. Stream
            // anyway while the app is alive rather than taking the process down.
        }

        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopStreaming();
            try {
                stopForeground(true);
            } catch (Throwable ignored) {}
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_RECONNECT.equals(action)) {
            // Point the existing stream at the newly selected server. Restarting
            // the whole service here is what used to crash on server switch.
            stopStreaming();
            client = new BridgeClient(getSharedPreferences("connection", Context.MODE_PRIVATE));
        }

        startStreaming();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopStreaming();
        super.onDestroy();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel ongoing = new NotificationChannel(
                CHANNEL_ONGOING, "Pemantauan sesi", NotificationManager.IMPORTANCE_MIN);
        ongoing.setDescription("Notifikasi tetap selama memantau CLI dari jauh");
        nm.createNotificationChannel(ongoing);

        NotificationChannel alerts = new NotificationChannel(
                CHANNEL_ALERTS, "Task selesai", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Memberi tahu saat task CLI selesai atau gagal");
        nm.createNotificationChannel(alerts);
    }

    private PendingIntent openAppIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 0, intent, flags);
    }

    private Notification buildOngoingNotification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ONGOING)
                : new Notification.Builder(this);
        return b.setContentTitle("Antigravity Remote")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_spark)
                .setOngoing(true)
                .setContentIntent(openAppIntent())
                .build();
    }

    private void notifyResult(boolean ok, String title, String body) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ALERTS)
                : new Notification.Builder(this);

        Notification n = b.setContentTitle(ok ? "Task selesai" : "Task gagal")
                .setContentText(title)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setSmallIcon(R.drawable.ic_spark)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .build();
        nm.notify(NOTIFY_RESULT, n);
    }

    private void startStreaming() {
        if (running.getAndSet(true)) return;
        worker = new Thread(this::streamLoop, "codex-sse");
        worker.setDaemon(true);
        worker.start();
    }

    private void stopStreaming() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    /** Reconnects with a backoff; the tunnel drops idle connections routinely. */
    private void streamLoop() {
        int backoffMs = 2000;
        while (running.get()) {
            HttpURLConnection connection = null;
            try {
                if (!client.isPaired()) {
                    Thread.sleep(5000);
                    continue;
                }
                connection = client.open("/api/events", "GET", 0);
                connection.setRequestProperty("Accept", "text/event-stream");
                connection.setUseCaches(false);

                if (connection.getResponseCode() != 200) throw new Exception("HTTP " + connection.getResponseCode());
                backoffMs = 2000;

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));

                String eventName = null;
                String line;
                while (running.get() && (line = reader.readLine()) != null) {
                    if (line.startsWith(":")) continue;                 // heartbeat
                    if (line.startsWith("event:")) {
                        eventName = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        handleEvent(eventName, line.substring(5).trim());
                    } else if (line.isEmpty()) {
                        eventName = null;
                    }
                }
                reader.close();
            } catch (Throwable ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }

            if (!running.get()) break;
            try {
                long jitter = (long) (Math.random() * Math.min(backoffMs * 0.3, 3000));
                Thread.sleep(backoffMs + jitter);
            } catch (InterruptedException e) {
                break;
            }
            backoffMs = Math.min(backoffMs * 2, 30000);
        }
    }

    private void handleEvent(String eventName, String data) {
        if (eventName == null) return;
        try {
            JSONObject json = new JSONObject(data);
            LiveEventBus.publish(eventName, json);

            if ("task.started".equals(eventName)) {
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.notify(NOTIFY_ONGOING, buildOngoingNotification("Menjalankan: " + json.optString("prompt", "task")));
            } else if ("task.finished".equals(eventName)) {
                boolean ok = json.optBoolean("ok", true);
                String title = json.optString("title", "Sesi");
                String body = ok ? json.optString("summary", "Selesai") : json.optString("error", "Gagal");
                notifyResult(ok, title, body);

                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.notify(NOTIFY_ONGOING, buildOngoingNotification("Memantau sesi CLI"));
            }
        } catch (Throwable ignored) {}
    }
}
