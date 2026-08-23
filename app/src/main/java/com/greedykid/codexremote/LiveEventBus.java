package com.greedykid.codexremote;

import org.json.JSONObject;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hands SSE events from the background service to whichever screen is open.
 * Listeners are called on the service thread; the UI hops to the main thread.
 */
public final class LiveEventBus {

    public interface Listener {
        void onEvent(String name, JSONObject data);
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private LiveEventBus() {}

    public static void register(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public static void unregister(Listener listener) {
        listeners.remove(listener);
    }

    public static void publish(String name, JSONObject data) {
        for (Listener l : listeners) {
            try {
                l.onEvent(name, data);
            } catch (Throwable ignored) {}
        }
    }
}
