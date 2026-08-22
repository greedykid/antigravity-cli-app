# Antigravity & Codex Remote (Google Material Design 3)

Native Android mobile command center for user-hosted **Google Antigravity CLI (`agy`)** and **OpenAI Codex CLI** with real-time process monitoring and full conversation history.

## Features

- **Google Material Design 3 (Material You)**: Dark surface tokens, M3 pill containers, and high-contrast typography.
- **💬 Prompt Chat**: Interactive CLI execution with quick suggestion chips (*List files*, *Git status*, *Explain code*, *Run tests*).
- **⚡ Live Session Monitor**: Real-time telemetry (PID, CPU, Memory, Uptime), active turn inspection, and 3-second live auto-refresh.
- **📜 Session History**: Browse all past Antigravity CLI sessions and view full transcripts directly in the app.
- **Dual Engine Gateway**: Seamless 1-tap toggle between `⚡ Antigravity` and `🚀 OpenAI Codex`.

## Build APK

Push to GitHub and open **Actions -> Build APK**. The workflow compiles and uploads `codex-remote-debug-apk` containing `app-debug.apk` as an artifact.

## Run the Bridge

On the machine where Antigravity CLI and/or Codex CLI are installed:

```bash
cd bridge
REMOTE_TOKEN='your-secret-token' BRIDGE_HOST='0.0.0.0' PORT=8787 CODEX_WORKDIR='/home/ubuntu' node server.js
```

In the Android app:
1. Tap **⚙ Settings** and enter the bridge endpoint (e.g. `https://your-tunnel.trycloudflare.com/api/chat`) and the secret Bearer token.
2. Tap **Save & Connect**; the app automatically tests the connection.
3. Switch between **Chat**, **Live Monitor**, and **History** tabs.


