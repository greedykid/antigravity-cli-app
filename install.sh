#!/usr/bin/env bash
set -e

# ==============================================================================
#  🚀 ANTIGRAVITY & CODEX REMOTE - ONE-LINE AUTO INSTALLER
# ==============================================================================

BOLD="\033[1m"
GREEN="\033[0;32m"
CYAN="\033[0;36m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
RESET="\033[0m"

echo -e "${CYAN}"
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║        🤖 ANTIGRAVITY & CODEX CLI REMOTE - AUTO INSTALLER     ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo -e "${RESET}"

CURRENT_USER=$(whoami)
INSTALL_DIR="${HOME}/.codexcli-remote-app"
if [ "${CURRENT_USER}" = "root" ]; then
  INSTALL_DIR="/opt/codexcli-remote-app"
fi

# 1. Dependency Checks: curl, git, node, npm
echo -e "${YELLOW}[1/5] Memeriksa dependensi sistem...${RESET}"

if ! command -v curl &> /dev/null; then
  echo "Menginstall curl..."
  if command -v apt-get &> /dev/null; then
    sudo apt-get update -y && sudo apt-get install -y curl
  elif command -v yum &> /dev/null; then
    sudo yum install -y curl
  fi
fi

if ! command -v git &> /dev/null; then
  echo "Menginstall git..."
  if command -v apt-get &> /dev/null; then
    sudo apt-get update -y && sudo apt-get install -y git
  elif command -v yum &> /dev/null; then
    sudo yum install -y git
  fi
fi

if ! command -v node &> /dev/null; then
  echo -e "${YELLOW}Node.js belum terinstall. Menginstall Node.js 20 LTS...${RESET}"
  curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
  sudo apt-get install -y nodejs
fi

NODE_VER=$(node -v)
echo -e "${GREEN}✓ Node.js terdeteksi: ${NODE_VER}${RESET}"

# 2. Check and install cloudflared
if ! command -v cloudflared &> /dev/null; then
  echo -e "${YELLOW}Menginstall Cloudflared untuk tunneling publik aman...${RESET}"
  ARCH=$(uname -m)
  if [ "${ARCH}" = "x86_64" ]; then
    CF_ARCH="amd64"
  elif [ "${ARCH}" = "aarch64" ] || [ "${ARCH}" = "arm64" ]; then
    CF_ARCH="arm64"
  else
    CF_ARCH="386"
  fi
  sudo curl -fsSL "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-${CF_ARCH}" -o /usr/local/bin/cloudflared
  sudo chmod +x /usr/local/bin/cloudflared
fi
echo -e "${GREEN}✓ Cloudflared siap digunakan.${RESET}"

# 3. Clone / Update Repository
echo -e "${YELLOW}[2/5] Mengunduh komponen Codex Remote Bridge...${RESET}"
if [ -d "${INSTALL_DIR}/.git" ]; then
  echo "Memperbarui repositori di ${INSTALL_DIR}..."
  cd "${INSTALL_DIR}"
  git pull origin main
else
  mkdir -p "${INSTALL_DIR}"
  git clone https://github.com/greedykid/codexcli-remote-app.git "${INSTALL_DIR}"
  cd "${INSTALL_DIR}"
fi

# 4. Install NPM packages in bridge
echo -e "${YELLOW}[3/5] Menginstall modul server bridge...${RESET}"
cd "${INSTALL_DIR}/bridge"
npm install --silent

# 5. Setup Systemd Services
echo -e "${YELLOW}[4/5] Mengonfigurasi background services (systemd)...${RESET}"
NODE_BIN=$(which node)
CLOUDFLARED_BIN=$(which cloudflared || echo "/usr/local/bin/cloudflared")

# codex-bridge.service
sudo tee /etc/systemd/system/codex-bridge.service > /dev/null <<EOF
[Unit]
Description=Codex CLI Remote Bridge Server
After=network.target

[Service]
Type=simple
User=${CURRENT_USER}
WorkingDirectory=${HOME}
Environment=NODE_ENV=production
Environment=PORT=8787
Environment=BRIDGE_HOST=0.0.0.0
Environment=REMOTE_TOKEN=codex-remote-token-2026
Environment=CODEX_WORKDIR=${HOME}
Environment=PATH=${PATH}:/usr/local/bin:/usr/bin:/bin:${HOME}/.local/bin
ExecStart=${NODE_BIN} ${INSTALL_DIR}/bridge/server.js
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

# codex-tunnel.service
sudo tee /etc/systemd/system/codex-tunnel.service > /dev/null <<EOF
[Unit]
Description=Cloudflare Tunnel for Codex Bridge
After=network.target codex-bridge.service

[Service]
Type=simple
User=${CURRENT_USER}
ExecStart=${CLOUDFLARED_BIN} tunnel --url http://127.0.0.1:8787 --logfile ${INSTALL_DIR}/tunnel.log
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

# Setup global CLI command symlink
chmod +x "${INSTALL_DIR}/bin/codex-remote.js"
sudo ln -sf "${INSTALL_DIR}/bin/codex-remote.js" /usr/local/bin/codex-remote
sudo ln -sf "${INSTALL_DIR}/bin/codex-remote.js" /usr/local/bin/agy-remote

# Reload and start services
sudo systemctl daemon-reload
sudo systemctl enable codex-bridge codex-tunnel > /dev/null 2>&1
sudo systemctl restart codex-bridge codex-tunnel

echo -e "${GREEN}✓ Background service aktif dan berjalan!${RESET}"

# 6. Generate QR Code
echo -e "${YELLOW}[5/5] Membuka Cloudflare Tunnel & membuat QR Code...${RESET}"
sleep 4

node "${INSTALL_DIR}/bin/codex-remote.js" pair
