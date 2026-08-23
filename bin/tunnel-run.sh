#!/usr/bin/env bash
# Chooses a stable named tunnel when one is configured, otherwise falls back to
# the free quick tunnel whose hostname changes on every restart.
set -e

CLOUDFLARED="${CLOUDFLARED:-$(command -v cloudflared || echo "$HOME/.local/bin/cloudflared")}"
TUNNEL_TOKEN_FILE="$HOME/.codex-remote/tunnel-token"
LOG="$HOME/tunnel.log"

TOKEN="${CF_TUNNEL_TOKEN:-}"
if [ -z "$TOKEN" ] && [ -f "$TUNNEL_TOKEN_FILE" ]; then
    TOKEN="$(cat "$TUNNEL_TOKEN_FILE")"
fi

if [ -n "$TOKEN" ]; then
    # Named tunnel: hostname is fixed, so the phone never needs re-pairing.
    exec "$CLOUDFLARED" tunnel --no-autoupdate --logfile "$LOG" run --token "$TOKEN"
fi

exec "$CLOUDFLARED" tunnel --no-autoupdate --url http://127.0.0.1:8787 --logfile "$LOG"
