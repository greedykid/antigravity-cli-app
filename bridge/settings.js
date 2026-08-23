// Runtime settings the phone can change without touching the systemd unit.

const fs = require("fs");
const path = require("path");
const config = require("./config");

const SETTINGS_FILE = path.join(config.CONFIG_DIR, "settings.json");

// `codex exec` is non-interactive, so an approval prompt could never be
// answered from the phone. The honest control is the sandbox level.
const SANDBOX_MODES = {
  full: { codex: ["--dangerously-bypass-approvals-and-sandbox"], label: "Akses penuh" },
  workspace: { codex: ["--sandbox", "workspace-write"], label: "Tulis di workspace" },
  readonly: { codex: ["--sandbox", "read-only"], label: "Hanya baca" }
};

const DEFAULTS = {
  sandboxMode: "full",
  notifyOnFinish: true
};

function load() {
  try {
    if (fs.existsSync(SETTINGS_FILE)) {
      const stored = JSON.parse(fs.readFileSync(SETTINGS_FILE, "utf8"));
      return Object.assign({}, DEFAULTS, stored);
    }
  } catch (e) {}
  return Object.assign({}, DEFAULTS);
}

function save(patch) {
  const merged = Object.assign(load(), patch || {});
  if (!SANDBOX_MODES[merged.sandboxMode]) merged.sandboxMode = DEFAULTS.sandboxMode;
  merged.notifyOnFinish = Boolean(merged.notifyOnFinish);
  fs.mkdirSync(config.CONFIG_DIR, { recursive: true, mode: 0o700 });
  fs.writeFileSync(SETTINGS_FILE, JSON.stringify(merged, null, 2));
  return merged;
}

function codexSandboxArgs() {
  const mode = load().sandboxMode;
  return (SANDBOX_MODES[mode] || SANDBOX_MODES.full).codex.slice();
}

module.exports = { load, save, codexSandboxArgs, SANDBOX_MODES, SETTINGS_FILE };
