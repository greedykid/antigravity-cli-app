#!/usr/bin/env bash
# ==============================================================================
# Antigravity & Codex Remote - 1-Click Server & Tunnel Auto Installer
# https://github.com/greedykid/codexcli-remote-app
# ==============================================================================

set -e

echo -e "\033[1;36m"
echo "  ========================================================"
echo "    🌌 Antigravity & Codex Remote Server Setup"
echo "    Real-time Mobile Companion for Antigravity & Codex CLI"
echo "  ========================================================"
echo -e "\033[0m"

# 1. Check & Install Node.js if missing
if ! command -v node >/dev/null 2>&1; then
    echo -e "\033[34m▶ Installing Node.js (LTS)...\033[0m"
    if command -v apt-get >/dev/null 2>&1; then
        curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
        sudo apt-get install -y nodejs
    else
        echo -e "\033[31m✘ Node.js is required. Please install Node.js 18+ first.\033[0m"
        exit 1
    fi
fi

# 2. Check & Install cloudflared if missing
if ! command -v cloudflared >/dev/null 2>&1; then
    echo -e "\033[34m▶ Installing Cloudflared tunnel...\033[0m"
    ARCH=$(uname -m)
    CLOUDFLARED_URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64"
    if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
        CLOUDFLARED_URL="https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64"
    fi
    
    mkdir -p "$HOME/.local/bin"
    curl -fsSL "$CLOUDFLARED_URL" -o "$HOME/.local/bin/cloudflared"
    chmod +x "$HOME/.local/bin/cloudflared"
    
    if sudo -n true 2>/dev/null; then
        sudo cp "$HOME/.local/bin/cloudflared" /usr/local/bin/cloudflared 2>/dev/null || true
    fi
fi

# 3. Setup App Repository Directory
INSTALL_DIR="$HOME/.codex-remote/app"
if [ -d "/home/ubuntu/codexcli-remote-app" ]; then
    INSTALL_DIR="/home/ubuntu/codexcli-remote-app"
fi

if [ ! -d "$INSTALL_DIR/bridge" ]; then
    echo -e "\033[34m▶ Downloading Antigravity Remote Bridge Server...\033[0m"
    mkdir -p "$HOME/.codex-remote"
    rm -rf "$INSTALL_DIR"
    git clone --depth=1 https://github.com/greedykid/codexcli-remote-app.git "$INSTALL_DIR"
fi

# 4. Install Bridge Dependencies
echo -e "\033[34m▶ Installing bridge dependencies...\033[0m"
cd "$INSTALL_DIR/bridge"
npm install --silent

# 5. Install Global CLI Command (codex-remote & agy-remote)
mkdir -p "$HOME/.local/bin"
cp "$INSTALL_DIR/bin/codex-remote" "$HOME/.local/bin/codex-remote"
chmod +x "$HOME/.local/bin/codex-remote"
cp "$INSTALL_DIR/bin/codex-remote" "$HOME/.local/bin/agy-remote"
chmod +x "$HOME/.local/bin/agy-remote"

if sudo -n true 2>/dev/null; then
    sudo cp "$INSTALL_DIR/bin/codex-remote" /usr/local/bin/codex-remote 2>/dev/null || true
    sudo cp "$INSTALL_DIR/bin/codex-remote" /usr/local/bin/agy-remote 2>/dev/null || true
fi

# 6. Ensure PATH is updated in shell profile
for profile in "$HOME/.bashrc" "$HOME/.zshrc" "$HOME/.profile"; do
    if [ -f "$profile" ]; then
        if ! grep -q ".local/bin" "$profile"; then
            echo "export PATH=\"\$HOME/.local/bin:\$PATH\"" >> "$profile"
        fi
    fi
done

echo ""
echo -e "\033[32m✔ Setup Completed Successfully!\033[0m"
echo -e "\033[32m✔ Global CLI commands installed: \033[1;37mcodex-remote\033[0;32m and \033[1;37magy-remote\033[0m"
echo ""

# 7. Start Bridge & Generate QR Code Pairing
echo -e "\033[34m▶ Starting Bridge Server and generating QR Pairing...\033[0m"
"$INSTALL_DIR/bin/codex-remote" pair
