# AI CLI Remote (Antigravity & Codex)

Native Android chat client for user-hosted Antigravity CLI (`agy`) & OpenAI Codex CLI bridge.

## Build APK

Push to GitHub and open **Actions -> Build APK**. The workflow compiles and uploads `codex-remote-debug-apk` containing `app-debug.apk` as an artifact.

## Run the Bridge

On the machine where Antigravity CLI and/or Codex CLI are installed:

```bash
cd bridge
REMOTE_TOKEN='your-secret-token' BRIDGE_HOST='0.0.0.0' PORT=8787 CODEX_WORKDIR='/path/to/project' node server.js
```

In the Android app:
1. Tap **Connection** and enter the bridge endpoint (e.g. `https://your-tunnel.trycloudflare.com/api/chat`) and the secret Bearer token.
2. Toggle between **⚡ Antigravity** and **🚀 Codex** directly on the top switcher.
3. Send prompts to execute remote CLI tasks seamlessly from your phone.

