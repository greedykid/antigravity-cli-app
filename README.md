# Codex Remote

Native Android chat client for a user-hosted Codex CLI bridge.

## Build

Push to GitHub and open **Actions -> Build APK**. The workflow uploads `app-debug.apk` as an artifact.

## Run the bridge

On the machine where Codex CLI is installed:

```bash
cd bridge
REMOTE_TOKEN='replace-with-a-long-random-token' BRIDGE_HOST='100.64.0.10' CODEX_WORKDIR='/path/to/project' node server.js
```

The bridge listens on `127.0.0.1` by default. Set `BRIDGE_HOST` to the machine's private VPN address to reach it from the phone; do not bind it to a public interface. In the app, enter the bridge URL ending in `/api/chat` and the same token.

The GitHub token previously pasted into chat must be revoked and must never be committed to this repository.
