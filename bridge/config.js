// Shared configuration for the bridge server and the codex-remote CLI.
// Keeps the auth token in one place so the QR pairing and the server can
// never drift apart.

const fs = require("fs");
const path = require("path");
const os = require("os");
const crypto = require("crypto");

const CONFIG_DIR = path.join(os.homedir(), ".codex-remote");
const TOKEN_FILE = path.join(CONFIG_DIR, "token");

// The token that used to ship hardcoded in this repo. Anyone who read the
// source knew it, so it is treated as "no token at all".
const LEGACY_TOKEN = "codex-remote-token-2026";

function generateToken() {
  return crypto.randomBytes(24).toString("base64url");
}

function readTokenFile() {
  try {
    if (fs.existsSync(TOKEN_FILE)) {
      const value = fs.readFileSync(TOKEN_FILE, "utf8").trim();
      if (value) return value;
    }
  } catch (e) {}
  return null;
}

function writeTokenFile(token) {
  fs.mkdirSync(CONFIG_DIR, { recursive: true, mode: 0o700 });
  fs.writeFileSync(TOKEN_FILE, token + "\n", { mode: 0o600 });
  return token;
}

// Order: explicit env var -> stored file -> freshly generated secret.
// `create` is false for read-only callers (status output) so they do not
// silently mint a token the server does not know about.
function loadToken(create = true) {
  const fromEnv = process.env.TOKEN || process.env.REMOTE_TOKEN;
  // Old installs export the public default from shell wrappers and env files.
  // Honouring it would put a token the server rejects into the pairing QR.
  if (fromEnv && fromEnv.trim() && fromEnv.trim() !== LEGACY_TOKEN) {
    return fromEnv.trim();
  }

  const stored = readTokenFile();
  if (stored) return stored;

  return create ? writeTokenFile(generateToken()) : null;
}

function rotateToken() {
  return writeTokenFile(generateToken());
}

function isLegacyToken(token) {
  return token === LEGACY_TOKEN;
}

// Accept both the documented names and the ones the systemd unit used to set.
function workdir() {
  return process.env.WORKDIR || process.env.CODEX_WORKDIR || os.homedir();
}

// Default to loopback: the Cloudflare tunnel connects locally, so there is no
// reason to expose the CLI-executing API on every network interface.
function bindHost() {
  return process.env.BRIDGE_HOST || process.env.HOST || "127.0.0.1";
}

function port() {
  return Number(process.env.PORT || 8787);
}

module.exports = {
  CONFIG_DIR,
  TOKEN_FILE,
  LEGACY_TOKEN,
  generateToken,
  loadToken,
  rotateToken,
  isLegacyToken,
  workdir,
  bindHost,
  port
};
