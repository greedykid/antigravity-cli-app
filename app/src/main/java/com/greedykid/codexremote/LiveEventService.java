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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
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
    /** Open the SSE stream and replay any cli.output/cli.event we missed
     *  while our POST /api/chat was still in flight. */
    public static final String ACTION_START_WITH_REPLAY = "com.greedykid.codexremote.START_EVENTS_REPLAY";
    public static final String EXTRA_REPLAY_JOB_ID = "replayJobId";

    private static final String CHANNEL_ONGOING = "codex_remote_ongoing";
    private static final String CHANNEL_ALERTS = "codex_remote_alerts";
    private static final int NOTIFY_ONGOING = 4101;
    private static final int NOTIFY_RESULT = 4102;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;
    private BridgeClient client;
    private volatile String pendingReplayJobId = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

        if (ACTION_START_WITH_REPLAY.equals(action) && intent != null) {
            String jobId = intent.getStringExtra(EXTRA_REPLAY_JOB_ID);
            if (jobId != null && !jobId.isEmpty()) pendingReplayJobId = jobId;
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

    /** Reconnects with a backoff; the tunnel drops idle connections routinely.
     *  When no data arrives for 45s we probe /api/health instead of tearing the
     *  stream down so a flapping tunnel does not drop in-flight cli.output. */
    private void streamLoop() {
        int backoffMs = 2000;
        while (running.get()) {
            HttpURLConnection connection = null;
            try {
                if (!client.isPaired()) {
                    Thread.sleep(5000);
                    continue;
                }
                String path = "/api/events";
                String replay = pendingReplayJobId;
                if (replay != null && !replay.isEmpty()) {
                    String encoded = URLEncoder.encode(replay, "UTF-8");
                    path = "/api/events?since=" + encoded;
                    pendingReplayJobId = null;
                }
                connection = client.open(path, "GET", 0);
                connection.setRequestProperty("Accept", "text/event-stream");
                connection.setUseCaches(false);

                if (connection.getResponseCode() != 200) throw new Exception("HTTP " + connection.getResponseCode());
                backoffMs = 2000;

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));

                String eventName = null;
                long lastEventAt = System.currentTimeMillis();
                int consecutiveIdleProbes = 0;
                String line;
                while (running.get() && (line = reader.readLine()) != null) {
                    if (line.startsWith(":")) {
                        // Heartbeat; treat as liveness signal.
                        lastEventAt = System.currentTimeMillis();
                        consecutiveIdleProbes = 0;
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        eventName = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        handleEvent(eventName, line.substring(5).trim());
                        lastEventAt = System.currentTimeMillis();
                        consecutiveIdleProbes = 0;
                    } else if (line.isEmpty()) {
                        eventName = null;
                    }

                    // If we've been idle past the threshold and the loop is
                    // still happily reading, the stream is alive but quiet
                    // (tunnel flap, long tool call, etc.). Probe health and
                    // reset the timer instead of tearing the socket down.
                    long now = System.currentTimeMillis();
                    if (now - lastEventAt > 45000) {
                        consecutiveIdleProbes++;
                        try {
                            HttpURLConnection probe = client.open("/health", "GET", 5000);
                            probe.setRequestProperty("Accept", "application/json");
                            probe.getResponseCode();
                            probe.disconnect();
                        } catch (Throwable ignored) {}
                        lastEventAt = System.currentTimeMillis();
                        if (consecutiveIdleProbes >= 3) {
                            throw new Exception("idle-stall");
                        }
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
