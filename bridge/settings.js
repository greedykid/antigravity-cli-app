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
  notifyOnFinish: true,
  // Jobs survive the HTTP request now, so the old hard 5-minute cap can be
  // generous without risking a stuck connection.
  taskTimeoutMinutes: 30,
  uploadRetentionDays: 14,
  // Saved project folders, relative to the workdir. The first entry is the
  // default target for the git panel and the file browser.
  projects: []
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
  merged.taskTimeoutMinutes = clampNumber(merged.taskTimeoutMinutes, 1, 240, DEFAULTS.taskTimeoutMinutes);
  merged.uploadRetentionDays = clampNumber(merged.uploadRetentionDays, 0, 365, DEFAULTS.uploadRetentionDays);
  merged.projects = sanitizeProjects(merged.projects);
  fs.mkdirSync(config.CONFIG_DIR, { recursive: true, mode: 0o700 });
  fs.writeFileSync(SETTINGS_FILE, JSON.stringify(merged, null, 2));
  return merged;
}

// Projects are plain relative paths; anything trying to climb out is dropped
// here as well as at request time.
function sanitizeProjects(list) {
  if (!Array.isArray(list)) return [];
  const seen = new Set();
  const clean = [];
  for (const item of list.slice(0, 20)) {
    const raw = typeof item === "string" ? { path: item } : (item || {});
    const rel = String(raw.path || "").trim().replace(/^\/+/, "");
    if (!rel || rel === "." || rel.split("/").includes("..")) continue;
    if (seen.has(rel)) continue;
    seen.add(rel);
    clean.push({ name: String(raw.name || rel.split("/").pop() || rel).slice(0, 60), path: rel });
  }
  return clean;
}

function clampNumber(value, min, max, fallback) {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.min(max, Math.max(min, Math.round(n)));
}

function taskTimeoutMs() {
  return load().taskTimeoutMinutes * 60 * 1000;
}

function codexSandboxArgs() {
  const mode = load().sandboxMode;
  return (SANDBOX_MODES[mode] || SANDBOX_MODES.full).codex.slice();
}

module.exports = { load, save, codexSandboxArgs, taskTimeoutMs, clampNumber, sanitizeProjects, SANDBOX_MODES, SETTINGS_FILE, DEFAULTS };
