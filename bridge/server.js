const http = require("http");
const { spawn, spawnSync, execSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const os = require("os");
const url = require("url");
const crypto = require("crypto");
const config = require("./config");
const events = require("./events");
const files = require("./files");
const git = require("./git");
const settings = require("./settings");
const jobs = require("./jobs");
const auditLog = require("./audit");
const searchIndex = require("./search");
const quota = require("./quota");
const codexConfig = require("./codexconfig");
const providerModels = require("./models");
const archive = require("./archive");
const WRITE_BLOCKED_MESSAGE = "Endpoint ini dinonaktifkan oleh mode Hanya Baca";

function isWriteBlocked(pathname) {
  if (settings.load().sandboxMode !== "readonly") return false;
  return pathname === "/api/files/write" ||
    pathname === "/api/upload" ||
    pathname === "/api/uploads/cleanup" ||
    pathname.startsWith("/api/git/") ||
    pathname.startsWith("/api/codex/") ||
    pathname === "/api/chat" ||
    pathname === "/api/projects" ||
    pathname === "/api/settings";
}

function commandVersion(command, args) {
  try {
    const result = spawnSync(command, args, { encoding: "utf8", timeout: 2000 });
    return result.status === 0 ? (result.stdout || "").trim().split("\n")[0] : null;
  } catch {
    return null;
  }
}

const resourceHistory = [];

function recordResourceSample() {
  try {
    const stats = serverResourceStats();
    resourceHistory.push({
      time: Date.now(),
      memPercent: stats.memoryPercent,
      memUsedMb: stats.memoryMb,
      diskPercent: stats.diskPercent,
      load1m: Array.isArray(stats.loadAvg) ? stats.loadAvg[0] : 0
    });
    if (resourceHistory.length > 30) {
      resourceHistory.shift();
    }
  } catch {}
}

// Seed initial history points
for (let i = 0; i < 5; i++) recordResourceSample();
setInterval(recordResourceSample, 3000);

function operationalHealth() {
  const codex = commandVersion(CODEX_BIN, ["--version"]);
  const agy = commandVersion(AGY_BIN, ["--version"]);
  const workdirExists = fs.existsSync(WORKDIR) && fs.statSync(WORKDIR).isDirectory();
  const gitStatus = git.isRepo(WORKDIR) ? "ok" : "not_repository";
  const stats = serverResourceStats();

  return {
    engines: {
      antigravity: agy ? { ok: true, version: agy } : { ok: false },
      codex: codex ? { ok: true, version: codex } : { ok: false }
    },
    filesystem: { ok: workdirExists, workdir: WORKDIR },
    git: gitStatus,
    runningJobs: jobs.running().length,
    server: stats,
    history: resourceHistory
  };
}

function serverResourceStats() {
  const totalMem = os.totalmem();
  const freeMem = os.freemem();
  let memTotalMb = Math.round(totalMem / (1024 * 1024));
  let memFreeMb = Math.round(freeMem / (1024 * 1024));
  let memUsedMb = memTotalMb - memFreeMb;

  try {
    if (fs.existsSync("/proc/meminfo")) {
      const meminfo = fs.readFileSync("/proc/meminfo", "utf8");
      const total = Number((meminfo.match(/MemTotal:\s+(\d+) kB/) || [])[1]);
      const available = Number((meminfo.match(/MemAvailable:\s+(\d+) kB/) || [])[1]);
      if (total && Number.isFinite(available)) {
        memTotalMb = Math.round(total / 1024);
        memFreeMb = Math.round(available / 1024);
        memUsedMb = memTotalMb - memFreeMb;
      }
    }
  } catch {}

  const memPercent = memTotalMb > 0 ? Math.round((memUsedMb / memTotalMb) * 100) : 0;

  let diskTotalGb = null;
  let diskUsedGb = null;
  let diskFreeGb = null;
  let diskPercent = 0;

  try {
    const result = spawnSync("df", ["-m", WORKDIR || "/home/ubuntu"], { encoding: "utf8", timeout: 2000 });
    const lines = (result.stdout || "").trim().split("\n");
    if (lines.length >= 2) {
      const parts = lines[lines.length - 1].split(/\s+/);
      if (parts.length >= 5) {
        const totalMb = parseInt(parts[1], 10) || 0;
        const usedMb = parseInt(parts[2], 10) || 0;
        const availMb = parseInt(parts[3], 10) || 0;
        diskTotalGb = parseFloat((totalMb / 1024).toFixed(1));
        diskUsedGb = parseFloat((usedMb / 1024).toFixed(1));
        diskFreeGb = parseFloat((availMb / 1024).toFixed(1));
        diskPercent = totalMb > 0 ? Math.round((usedMb / totalMb) * 100) : 0;
      }
    }
  } catch {}

  const cpus = os.cpus() || [];
  const load = os.loadavg() || [0, 0, 0];

  return {
    memoryMb: memUsedMb,
    memoryTotalMb: memTotalMb,
    memoryFreeMb: memFreeMb,
    memoryPercent: memPercent,
    diskTotalGb: diskTotalGb,
    diskUsedGb: diskUsedGb,
    diskFreeGb: diskFreeGb,
    diskPercent: diskPercent,
    cpuCores: cpus.length,
    cpuModel: cpus.length > 0 ? cpus[0].model.trim() : "Unknown CPU",
    loadAvg: [parseFloat(load[0].toFixed(2)), parseFloat(load[1].toFixed(2)), parseFloat(load[2].toFixed(2))],
    uptimeSeconds: Math.round(os.uptime()),
    processUptimeSeconds: Math.round(process.uptime()),
    platform: `${os.type()} ${os.arch()}`,
    hostname: os.hostname(),
    nodeVersion: process.version
  };
}

function readBridgeLogs(lines = 200) {
  const candidates = [
    path.join(config.CONFIG_DIR, "bridge.log"),
    path.join(config.CONFIG_DIR, "server.log")
  ];
  for (const file of candidates) {
    try {
      if (!fs.existsSync(file)) continue;
      const content = fs.readFileSync(file, "utf8").trim().split("\\n");
      return { source: file, lines: content.slice(-lines).reverse() };
    } catch {}
  }
  try {
    const result = spawnSync("journalctl", ["-u", "codex-bridge", "-n", String(lines), "--no-pager"], {
      encoding: "utf8", timeout: 5000
    });
    if (result.status === 0 && result.stdout.trim()) {
      return { source: "journalctl:codex-bridge", lines: result.stdout.trim().split("\\n").reverse() };
    }
  } catch {}
  return { source: null, lines: [] };
}

function backupPayload() {
  return {
    exportedAt: new Date().toISOString(),
    settings: settings.load(),
    promptLibrary: safeReadJson(path.join(config.CONFIG_DIR, "prompt-library.json")),
    devices: Object.values(loadDeviceRegistry()).map(d => ({ id: d.id, name: d.name, revoked: d.revoked }))
  };
}

function safeReadJson(file) {
  try { return JSON.parse(fs.readFileSync(file, "utf8")); } catch { return null; }
}

function deviceId(req) {
  const raw = String(req.headers["x-codex-device-id"] || "").trim();
  if (raw && /^[A-Za-z0-9_-]{8,64}$/.test(raw)) return raw;
  return crypto.createHash("sha256").update(String(req.headers["user-agent"] || "unknown")).digest("hex").slice(0, 16);
}

function deviceRegistryFile() {
  return path.join(config.CONFIG_DIR, "devices.json");
}

function loadDeviceRegistry() {
  try {
    if (!fs.existsSync(deviceRegistryFile())) return {};
    return JSON.parse(fs.readFileSync(deviceRegistryFile(), "utf8"));
  } catch {
    return {};
  }
}

function saveDeviceRegistry(registry) {
  fs.mkdirSync(config.CONFIG_DIR, { recursive: true, mode: 0o700 });
  fs.writeFileSync(deviceRegistryFile(), JSON.stringify(registry, null, 2), { mode: 0o600 });
}

function registerDevice(req) {
  const id = deviceId(req);
  const registry = loadDeviceRegistry();
  const now = Date.now();
  const existing = registry[id];
  registry[id] = {
    id,
    name: String(req.headers["x-codex-device-name"] || (existing && existing.name) || id.slice(0, 8)).slice(0, 60),
    userAgent: String(req.headers["user-agent"] || "").slice(0, 180),
    createdAt: existing && existing.createdAt || now,
    lastSeenAt: now,
    revoked: existing ? Boolean(existing.revoked) : false
  };
  saveDeviceRegistry(registry);
  return registry[id];
}

function isDeviceRevoked(req) {
  const device = loadDeviceRegistry()[deviceId(req)];
  return Boolean(device && device.revoked);
}

function issueApproval(action) {
  const token = crypto.randomBytes(18).toString("base64url");
  pendingApprovals.set(token, { action, expiresAt: Date.now() + 120000 });
  return token;
}

function consumeApproval(token, action) {
  const key = String(token || "");
  const approval = pendingApprovals.get(key);
  if (!approval || approval.action !== action || approval.expiresAt < Date.now()) return false;
  pendingApprovals.delete(key);
  return true;
}

function requireApproval(req, res, pathname, payload, next) {
  if (consumeApproval(payload.approvalToken, pathname)) return next();
  auditLog.log("approval.requested", { path: pathname, device: deviceId(req) });
  send(res, 403, {
    error: "Konfirmasi diperlukan",
    code: "APPROVAL_REQUIRED",
    approvalToken: issueApproval(pathname)
  });
}

const PORT = config.port();
const HOST = config.bindHost();
const TOKEN = config.loadToken();
const WORKDIR = config.workdir();
const AGY_BIN = process.env.AGY_BIN || path.join(os.homedir(), ".local/bin/agy");
const CODEX_BIN = process.env.CODEX_BIN || "codex";
let activeCodexSessionId = null;
const SESSION_ACTIVITY_FILE = path.join(os.homedir(), ".gemini/antigravity-cli/session_activity.json");
const pendingApprovals = new Map();

function getSessionActivityMap() {
  try {
    if (fs.existsSync(SESSION_ACTIVITY_FILE)) {
      return JSON.parse(fs.readFileSync(SESSION_ACTIVITY_FILE, "utf8"));
    }
  } catch (e) {}
  return {};
}

function touchSessionActivity(convId) {
  if (!convId) return;
  try {
    const map = getSessionActivityMap();
    map[convId] = Date.now();
    const dir = path.dirname(SESSION_ACTIVITY_FILE);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(SESSION_ACTIVITY_FILE, JSON.stringify(map, null, 2), "utf8");
  } catch (e) {}
}

const SESSION_TITLES_FILE = path.join(os.homedir(), ".gemini/antigravity-cli/session_titles.json");

function getCustomSessionTitles() {
  try {
    if (fs.existsSync(SESSION_TITLES_FILE)) {
      return JSON.parse(fs.readFileSync(SESSION_TITLES_FILE, "utf8"));
    }
  } catch (e) {}
  return {};
}

function saveCustomSessionTitle(convId, title) {
  try {
    const titles = getCustomSessionTitles();
    if (title && title.trim()) {
      titles[convId] = title.trim();
    } else {
      delete titles[convId];
    }
    const dir = path.dirname(SESSION_TITLES_FILE);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(SESSION_TITLES_FILE, JSON.stringify(titles, null, 2), "utf8");
    return true;
  } catch (e) {
    console.error("[SessionTitles] Failed to save:", e);
    return false;
  }
}

if (config.isLegacyToken(TOKEN)) {
  console.error("[Security] Refusing to start: the token is the public default from this repository.");
  console.error("[Security] Unset TOKEN/REMOTE_TOKEN and restart to generate a private one,");
  console.error(`[Security] or write your own secret to ${config.TOKEN_FILE}.`);
  process.exit(1);
}

const UPLOADS_DIR = path.join(WORKDIR, "uploads");
if (!fs.existsSync(UPLOADS_DIR)) {
  fs.mkdirSync(UPLOADS_DIR, { recursive: true });
}

function send(res, code, data) {
  res.writeHead(code, {
    "Content-Type": "application/json",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Authorization, Content-Type",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS"
  });
  res.end(JSON.stringify(data));
}

// Constant-time compare so the token cannot be recovered by timing the 401s.
function authorized(req) {
  if (!TOKEN) return true;
  const header = req.headers.authorization || "";
  const expected = `Bearer ${TOKEN}`;
  const a = Buffer.from(header);
  const b = Buffer.from(expected);
  if (a.length !== b.length) return false;
  try {
    return crypto.timingSafeEqual(a, b);
  } catch (e) {
    return false;
  }
}

function getAgyProcess() {
  try {
    const ps = execSync("ps -eo pid,pcpu,pmem,etime,args | grep -E \"[a]gy\" | head -n 1", { encoding: "utf8" }).trim();
    if (!ps) return { running: false };
    const parts = ps.split(/\s+/);
    return {
      running: true,
      pid: parts[0],
      cpu: parts[1] + "%",
      mem: parts[2] + "%",
      uptime: parts[3],
      cmd: parts.slice(4).join(" ")
    };
  } catch (e) {
    return { running: false };
  }
}

function getCodexProcess() {
  try {
    const ps = execSync("ps -eo pid,pcpu,pmem,etime,args | grep -E '[c]odex exec' | head -n 1", { encoding: "utf8" }).trim();
    if (!ps) return { running: false };
    const parts = ps.split(/\s+/);
    return { running: true, pid: parts[0], cpu: parts[1] + "%", mem: parts[2] + "%", uptime: parts[3], cmd: parts.slice(4).join(" ") };
  } catch (e) {
    return { running: false };
  }
}

// -------------------------------------------------------------
// USAGE & TOKEN METRICS
// -------------------------------------------------------------
let cachedUsage = null;
let lastUsageFetch = 0;

// Counts prompts inside a trailing time window from a history.jsonl file.
// This is real local activity — unlike a provider quota, which the CLI does
// not expose, so we must not pretend to know it.
function countRecentPrompts(entries, windowMs, now) {
  return entries.filter(ts => ts > 0 && now - ts <= windowMs).length;
}

function readAgyHistory(home) {
  const file = path.join(home, ".gemini/antigravity-cli/history.jsonl");
  const prompts = [];
  const sessionIds = new Set();
  try {
    if (fs.existsSync(file)) {
      for (const line of fs.readFileSync(file, "utf8").trim().split("\n").filter(Boolean)) {
        try {
          const item = JSON.parse(line);
          if (item.conversationId) sessionIds.add(item.conversationId);
          prompts.push(Number(item.timestamp) || 0);
        } catch (e) {}
      }
    }
  } catch (e) {}
  return { prompts, sessionIds };
}

function getUsageStats() {
  const now = Date.now();
  if (cachedUsage && (now - lastUsageFetch < 15000)) {
    return cachedUsage;
  }

  const home = os.homedir();
  let email = "ubuntu@remote-server";
  try {
    const authFile = path.join(home, ".gemini/antigravity-cli/auth.json");
    if (fs.existsSync(authFile)) {
      const auth = JSON.parse(fs.readFileSync(authFile, "utf8"));
      if (auth.email) email = auth.email;
      else if (auth.account) email = auth.account;
    }
  } catch (e) {}

  const { prompts, sessionIds } = readAgyHistory(home);

  let totalSteps = 0;
  let totalTools = 0;
  let totalChars = 0;

  for (const convId of sessionIds) {
    try {
      const logFile = path.join(home, ".gemini/antigravity-cli/brain", convId, ".system_generated/logs/transcript.jsonl");
      if (fs.existsSync(logFile)) {
        const lines = fs.readFileSync(logFile, "utf8").trim().split("\n").filter(Boolean);
        totalSteps += lines.length;
        for (const line of lines) {
          totalChars += line.length;
          if (line.includes('"tool_calls"')) totalTools++;
        }
      }
    } catch (e) {}
  }

  const quotaResult = quota.get(AGY_BIN);
  const quotaGroups = quotaResult.groups || [];

  const freeMem = Math.round(os.freemem() / (1024 * 1024));
  const totalMem = Math.round(os.totalmem() / (1024 * 1024));

  cachedUsage = {
    ok: true,
    account: email,
    engine: "antigravity",

    // Measured locally. Zero means zero — no invented fallbacks.
    totalSessions: sessionIds.size,
    totalPrompts: prompts.length,
    totalSteps,
    totalTools,
    totalChars,
    estimatedTokens: Math.round(totalChars / 3.8),

    // Activity windows, derived from real history timestamps.
    promptsLast5h: countRecentPrompts(prompts, 5 * 60 * 60 * 1000, now),
    promptsLast24h: countRecentPrompts(prompts, 24 * 60 * 60 * 1000, now),
    promptsLast7d: countRecentPrompts(prompts, 7 * 24 * 60 * 60 * 1000, now),

    // Real remaining limits, straight from `agy -p "/usage"`.
    quotaKnown: quotaGroups.length > 0,
    quotaGroups,
    quotaStale: quotaResult.stale,
    quotaCheckedAt: quotaResult.cachedAt || null,
    quotaStatus: quotaGroups.length
        ? "Sisa kuota dari akun Antigravity"
        : "Kuota provider tidak bisa dibaca dari CLI saat ini",

    memoryUsage: `${totalMem - freeMem} MB / ${totalMem} MB`,
    hostname: os.hostname(),
    uptime: Math.round(os.uptime() / 60) + " menit"
  };
  lastUsageFetch = now;
  return cachedUsage;
}

function getCodexUsageStats() {
  const home = os.homedir();
  const historyFile = path.join(home, ".codex/history.jsonl");
  let totalPrompts = 0;
  let totalSessions = 0;
  let estimatedTokens = 0;
  let totalChars = 0;
  try {
    if (fs.existsSync(historyFile)) {
      const lines = fs.readFileSync(historyFile, "utf8").split("\n").filter(Boolean);
      totalPrompts = lines.length;
      totalSessions = new Set(lines.map(line => {
        try { return JSON.parse(line).session_id; } catch (e) { return null; }
      }).filter(Boolean)).size;
    }
    const baseDir = path.join(home, ".codex/sessions");
    if (fs.existsSync(baseDir)) {
      const stack = [baseDir];
      let visited = 0;
      while (stack.length && visited < 400) {
        const dir = stack.pop();
        for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
          const full = path.join(dir, entry.name);
          if (entry.isDirectory()) stack.push(full);
          else if (entry.isFile() && entry.name.endsWith(".jsonl")) {
            visited++;
            const content = fs.readFileSync(full, "utf8");
            totalChars += content.length;
            // total_tokens is cumulative per turn, so summing every match
            // counted the same tokens once per turn. Take the file's peak.
            let fileMax = 0;
            for (const match of content.matchAll(/"total_tokens"\s*:\s*(\d+)/g)) {
              fileMax = Math.max(fileMax, Number(match[1]));
            }
            estimatedTokens += fileMax;
          }
        }
      }
    }
  } catch (e) {}
  const now = Date.now();
  let promptTimes = [];
  try {
    if (fs.existsSync(historyFile)) {
      promptTimes = fs.readFileSync(historyFile, "utf8").split("\n").filter(Boolean).map(line => {
        try {
          const item = JSON.parse(line);
          // Codex history stores unix seconds in `ts`.
          return item.ts ? Number(item.ts) * 1000 : Number(item.timestamp) || 0;
        } catch (e) {
          return 0;
        }
      });
    }
  } catch (e) {}

  return {
    ok: true,
    account: "Codex account",
    engine: "codex",
    totalPrompts,
    totalSessions,
    // Prefer token counts parsed from the rollouts; fall back to a character
    // estimate only when none were found, and label it as such.
    estimatedTokens: estimatedTokens || Math.round(totalChars / 4),
    tokensMeasured: estimatedTokens > 0,
    totalChars,
    promptsLast5h: countRecentPrompts(promptTimes, 5 * 60 * 60 * 1000, now),
    promptsLast24h: countRecentPrompts(promptTimes, 24 * 60 * 60 * 1000, now),
    promptsLast7d: countRecentPrompts(promptTimes, 7 * 24 * 60 * 60 * 1000, now),
    quotaKnown: false,
    quotaStatus: "Kuota provider tidak tersedia dari CLI lokal",
    hostname: os.hostname(),
    uptime: Math.round(os.uptime() / 60) + " menit"
  };
}

// -------------------------------------------------------------
// NATIVE CODEX SESSION & TRANSCRIPT PARSER
// -------------------------------------------------------------
// Codex sessions come from two places that do not overlap.
//
// history.jsonl only records interactive runs — `codex exec`, which is what the
// bridge uses, never appends to it. Every run does leave a rollout file, so the
// rollouts are the authoritative list; history is merged in for sessions
// started from a terminal.
function readRolloutHead(file) {
  const meta = { id: null, timestamp: 0, title: "" };
  try {
    const fd = fs.openSync(file, "r");
    const buf = Buffer.alloc(64 * 1024);
    const read = fs.readSync(fd, buf, 0, buf.length, 0);
    fs.closeSync(fd);

    for (const line of buf.toString("utf8", 0, read).split("\n")) {
      if (!line.trim()) continue;
      let obj;
      try { obj = JSON.parse(line); } catch (e) { continue; }   // last line may be partial

      if (obj.type === "session_meta" && obj.payload) {
        meta.id = meta.id || obj.payload.id || obj.payload.session_id || null;
        const ts = Date.parse(obj.payload.timestamp || "");
        if (!Number.isNaN(ts)) meta.timestamp = ts;
      }

      if (!meta.title && obj.type === "response_item" && obj.payload
          && obj.payload.role === "user") {
        for (const part of obj.payload.content || []) {
          const text = (part.text || "").trim();
          // Skip the harness preamble Codex injects before the real prompt.
          if (text && !text.startsWith("<")) { meta.title = text; break; }
        }
      }
      if (meta.id && meta.title) break;
    }
  } catch (e) {}
  return meta;
}

function listCodexRollouts(limit = 200) {
  const baseDir = path.join(os.homedir(), ".codex/sessions");
  const files = [];
  if (!fs.existsSync(baseDir)) return files;

  const stack = [baseDir];
  while (stack.length && files.length < 4000) {
    const dir = stack.pop();
    let entries;
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { continue; }
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) stack.push(full);
      else if (entry.isFile() && entry.name.startsWith("rollout-") && entry.name.endsWith(".jsonl")) {
        let mtime = 0;
        try { mtime = fs.statSync(full).mtimeMs; } catch (e) {}
        files.push({ full, name: entry.name, mtime });
      }
    }
  }

  // Only the newest are parsed; the rest cannot reach the 50-session cap anyway.
  files.sort((a, b) => b.mtime - a.mtime);
  return files.slice(0, limit);
}

function getCodexSessions() {
  const home = os.homedir();
  const map = new Map();

  for (const file of listCodexRollouts()) {
    const meta = readRolloutHead(file.full);
    // The filename carries the id too, which covers a truncated head.
    const fromName = file.name.match(/rollout-[\dT:-]*-([0-9a-f-]{36})\.jsonl$/i);
    const id = meta.id || (fromName ? fromName[1] : null);
    if (!id || map.has(id)) continue;

    map.set(id, {
      conversationId: id,
      title: cleanTitle(meta.title, "Codex " + id.slice(0, 8)).slice(0, 80),
      timestamp: meta.timestamp || file.mtime || Date.now(),
      workspace: WORKDIR,
      engine: "codex",
      hostname: os.hostname()
    });
  }

  const historyFile = path.join(home, ".codex/history.jsonl");
  if (fs.existsSync(historyFile)) {
    const lines = fs.readFileSync(historyFile, "utf8").trim().split("\n").filter(Boolean);
    for (let i = lines.length - 1; i >= 0; i--) {
      try {
        const item = JSON.parse(lines[i]);
        const sid = item.session_id || item.conversationId;
        if (!sid || map.has(sid)) continue;
        map.set(sid, {
          conversationId: sid,
          title: cleanTitle(item.text || item.title, "Codex " + sid.slice(0, 8)),
          timestamp: item.ts ? item.ts * 1000 : (item.timestamp || Date.now()),
          workspace: WORKDIR,
          engine: "codex",
          hostname: os.hostname()
        });
      } catch (e) {}
    }
  }

  return Array.from(map.values());
}

function findCodexRolloutFile(sessionId) {
  if (!sessionId) return null;
  const home = os.homedir();
  const baseDir = path.join(home, ".codex/sessions");
  if (!fs.existsSync(baseDir)) return null;

  function search(dir) {
    try {
      const entries = fs.readdirSync(dir, { withFileTypes: true });
      for (const ent of entries) {
        const full = path.join(dir, ent.name);
        if (ent.isDirectory()) {
          const f = search(full);
          if (f) return f;
        } else if (ent.isFile() && ent.name.includes(sessionId) && ent.name.endsWith(".jsonl")) {
          return full;
        }
      }
    } catch (e) {}
    return null;
  }
  return search(baseDir);
}

function getCodexTranscript(sessionId, limit = 1000) {
  const file = findCodexRolloutFile(sessionId);
  if (!file || !fs.existsSync(file)) return [];

  const lines = fs.readFileSync(file, "utf8").trim().split("\n").filter(Boolean);
  const msgs = [];
  for (const l of lines) {
    try {
      const obj = JSON.parse(l);
      if (obj.type === "response_item" && obj.payload) {
        const payload = obj.payload;
        const role = obj.payload.role;
        const contents = obj.payload.content || [];
        if (payload.type === "reasoning") {
          const summary = Array.isArray(payload.summary) ? payload.summary.map(s => s.text || "").filter(Boolean).join("\n") : (payload.content || "");
          if (summary.trim()) msgs.push({ role: "thinking", toolTitle: "Thinking", title: "Thinking Process", content: summary.trim(), time: obj.timestamp });
        } else if (["custom_tool_call", "function_call"].includes(payload.type)) {
          const name = payload.name || "exec";
          let command = payload.arguments || payload.input || payload.command || "";
          if (typeof command !== "string") command = JSON.stringify(command);
          msgs.push({ role: "tool", toolTitle: name === "exec" ? "Exec" : name, title: name, command, content: command, time: obj.timestamp, callId: payload.call_id || "" });
        } else if (["custom_tool_call_output", "function_call_output"].includes(payload.type)) {
          let output = payload.output || payload.content || "";
          if (Array.isArray(output)) output = output.map(x => x.text || "").join("\n");
          if (typeof output !== "string") output = JSON.stringify(output);
          if (output.trim()) msgs.push({ role: "tool", toolTitle: "Exec result", title: "Command output", content: output.trim(), command: output.trim(), time: obj.timestamp, callId: payload.call_id || "" });
        }
        for (const c of contents) {
          if (role === "user" && c.type === "input_text") {
            const text = (c.text || "").trim();
            // Codex rollouts contain empty input_text parts. Emitting them as
            // turns produced blank bubbles in the app and, worse, matched the
            // optimistic prompt (every string contains ""), so the message the
            // user just typed was replaced by an empty one mid-run.
            if (text
                && !text.startsWith("<environment_context>")
                && !text.startsWith("<skills_instructions>")
                && !text.startsWith("<permissions instructions>")) {
              msgs.push({ role: "user", content: text, time: obj.timestamp });
            }
          } else if (role === "assistant" && c.type === "output_text") {
            const text = (c.text || "").trim();
            if (text) msgs.push({ role: "assistant", content: text, time: obj.timestamp });
          }
        }
      }
    } catch (e) {}
  }
  return msgs.slice(-limit);
}

// -------------------------------------------------------------
// SESSIONS & TRANSCRIPT RETRIEVAL
// -------------------------------------------------------------
function findLatestAgyConversationId(sinceMs = 0) {
  const home = os.homedir();
  const brainDir = path.join(home, ".gemini/antigravity-cli/brain");
  if (!fs.existsSync(brainDir)) return null;
  let newestId = null;
  let newestTime = sinceMs;
  try {
    const entries = fs.readdirSync(brainDir, { withFileTypes: true });
    for (const ent of entries) {
      if (ent.isDirectory() && ent.name.length >= 8) {
        const full = path.join(brainDir, ent.name);
        const stat = fs.statSync(full);
        if (stat.mtimeMs > newestTime) {
          newestTime = stat.mtimeMs;
          newestId = ent.name;
        }
      }
    }
  } catch (e) {}
  return newestId;
}

// Session titles come from several places: the CLI history files and, for
// Antigravity, the <USER_REQUEST> block scraped out of a transcript log. That
// log stores the request JSON-encoded, so its newlines arrive as the literal
// two characters \n and ended up in session names. Decode the common escapes
// and flatten to a single line.
function cleanTitle(raw, fallback) {
  let text = typeof raw === "string" ? raw : "";
  text = text
    .replace(/\\r\\n|\\n|\\r/g, " ")
    .replace(/\\t/g, " ")
    .replace(/\\"/g, '"')
    .replace(/\\\\/g, "\\")
    .replace(/\s+/g, " ")
    .trim();
  return text || fallback;
}

function getSessions(engineFilter) {
  const home = os.homedir();
  const file = path.join(home, ".gemini/antigravity-cli/history.jsonl");
  const agyMap = new Map();
  if (fs.existsSync(file)) {
    const lines = fs.readFileSync(file, "utf8").trim().split("\n").filter(Boolean);
    for (let i = lines.length - 1; i >= 0; i--) {
      try {
        const item = JSON.parse(lines[i]);
        if (item.conversationId && !agyMap.has(item.conversationId)) {
          agyMap.set(item.conversationId, {
            conversationId: item.conversationId,
            title: cleanTitle(item.display, "Session " + item.conversationId.slice(0, 8)),
            timestamp: item.timestamp || Date.now(),
            workspace: item.workspace || "/home/ubuntu",
            engine: "antigravity",
            hostname: os.hostname()
          });
        }
      } catch (e) {}
    }
  }

  // Also scan all sessions in ~/.gemini/antigravity-cli/brain/
  const brainDir = path.join(home, ".gemini/antigravity-cli/brain");
  if (fs.existsSync(brainDir)) {
    try {
      const entries = fs.readdirSync(brainDir, { withFileTypes: true });
      for (const ent of entries) {
        if (ent.isDirectory() && ent.name.length >= 8) {
          const convId = ent.name;
          const transcriptFile = path.join(brainDir, convId, ".system_generated/logs/transcript.jsonl");
          let mtime = 0;
          let title = "Session " + convId.slice(0, 8);
          try {
            if (fs.existsSync(transcriptFile)) {
              const stat = fs.statSync(transcriptFile);
              mtime = stat.mtimeMs;
              const fd = fs.openSync(transcriptFile, "r");
              const buf = Buffer.alloc(4096);
              const bytesRead = fs.readSync(fd, buf, 0, 4096, 0);
              fs.closeSync(fd);
              const headText = buf.toString("utf8", 0, bytesRead);
              const m = headText.match(/<USER_REQUEST>([\s\S]*?)<\/USER_REQUEST>/);
              if (m && m[1].trim()) {
                title = cleanTitle(m[1], title).slice(0, 60);
              }
            } else {
              const stat = fs.statSync(path.join(brainDir, convId));
              mtime = stat.mtimeMs;
            }
          } catch (e) {}

          if (!agyMap.has(convId)) {
            agyMap.set(convId, {
              conversationId: convId,
              title: title,
              timestamp: mtime || Date.now(),
              workspace: WORKDIR || "/home/ubuntu",
              engine: "antigravity",
              hostname: os.hostname()
            });
          } else {
            const existing = agyMap.get(convId);
            if (mtime && mtime > (existing.timestamp || 0)) {
              existing.timestamp = mtime;
            }
            if (title && !title.startsWith("Session ") && existing.title.startsWith("Session ")) {
              existing.title = title;
            }
          }
        }
      }
    } catch (e) {}
  }

  const codexSessions = getCodexSessions();
  const agySessions = Array.from(agyMap.values());

  let merged = [...codexSessions, ...agySessions].sort((a, b) => {
    return (b.timestamp || 0) - (a.timestamp || 0);
  });

  const customTitles = getCustomSessionTitles();
  const activityMap = getSessionActivityMap();
  for (const s of merged) {
    if (customTitles[s.conversationId]) {
      s.title = customTitles[s.conversationId];
      s.customTitle = true;
    }
    if (activityMap[s.conversationId]) {
      s.timestamp = Math.max(s.timestamp || 0, activityMap[s.conversationId]);
    }
  }

  merged.sort((a, b) => {
    return (b.timestamp || 0) - (a.timestamp || 0);
  });

  // Filter before the cap. Trimming to 50 first and filtering afterwards would
  // hide an engine's sessions whenever the other engine dominates the top 50.
  if (engineFilter) {
    const wanted = normalizeEngine(engineFilter);
    merged = merged.filter(s => normalizeEngine(s.engine) === wanted);
  }

  return {
    hostname: os.hostname(),
    sessions: merged.slice(0, 50)
  };
}

function normalizeEngine(value) {
  return String(value || "").toLowerCase() === "codex" ? "codex" : "antigravity";
}

function getTranscript(convId, limit = 1000) {
  if (!convId) return [];

  // Check Codex first
  const codexTurns = getCodexTranscript(convId, limit);
  if (codexTurns && codexTurns.length > 0) {
    return codexTurns;
  }

  // Antigravity session transcript
  const home = os.homedir();
  const file = path.join(home, ".gemini/antigravity-cli/brain", convId, ".system_generated/logs/transcript.jsonl");
  if (!fs.existsSync(file)) return [];
  const lines = fs.readFileSync(file, "utf8").trim().split("\n").filter(Boolean);
  const msgs = [];
  for (const l of lines) {
    try {
      const s = JSON.parse(l);
      if (s.type === "USER_INPUT" && s.content) {
        let text = s.content;
        const m = text.match(/<USER_REQUEST>([\s\S]*?)<\/USER_REQUEST>/);
        if (m) text = m[1].trim();
        msgs.push({ role: "user", content: text, time: s.created_at, index: s.step_index });
      } else if (s.type === "PLANNER_RESPONSE") {
        if (s.thinking && s.thinking.trim()) {
          msgs.push({
            role: "thinking",
            toolTitle: "Thinking",
            title: "Thinking Process",
            command: "Internal Reasoning",
            content: s.thinking.trim(),
            time: s.created_at,
            index: s.step_index
          });
        }
        if (s.tool_calls && s.tool_calls.length) {
          for (const tc of s.tool_calls) {
            const toolName = tc.name || "tool";
            let argsObj = tc.args;
            if (typeof argsObj === "string") {
              try { argsObj = JSON.parse(argsObj); } catch(e) {}
            }
            let argsStr = typeof argsObj === "object" ? JSON.stringify(argsObj, null, 2) : String(argsObj || "");

            let toolTitle = "Action";
            let commandText = "";
            let friendlyTitle = "Aksi: " + toolName;
            if (toolName === "run_command") {
              toolTitle = "Run Command";
              commandText = (argsObj && argsObj.CommandLine) || "";
              friendlyTitle = commandText ? "$ " + commandText : "Run Command";
            } else if (toolName === "view_file") {
              toolTitle = "Read File";
              commandText = (argsObj && argsObj.AbsolutePath) || "";
              friendlyTitle = "View " + (commandText ? path.basename(commandText) : "file");
            } else if (toolName === "write_to_file" || toolName === "replace_file_content") {
              toolTitle = "Edit File";
              commandText = (argsObj && (argsObj.TargetFile || argsObj.AbsolutePath)) || "";
              friendlyTitle = "Write " + (commandText ? path.basename(commandText) : "file");
            }

            let desc = (argsObj && (argsObj.Description || argsObj.toolAction || argsObj.toolSummary)) || "";
            let bodyText = "";
            if (desc) bodyText += desc + "\n\n";
            if (commandText) bodyText += "Command: " + commandText + "\n\n";
            bodyText += "Arguments:\n" + argsStr;

            msgs.push({
              role: "tool",
              toolName: toolName,
              toolTitle: toolTitle,
              title: friendlyTitle,
              command: commandText,
              content: bodyText.trim(),
              time: s.created_at,
              index: s.step_index
            });
          }
        }
        if (s.content && s.content.trim()) {
          msgs.push({ role: "assistant", content: s.content.trim(), time: s.created_at, index: s.step_index });
        }
      }
    } catch (e) {}
  }
  return msgs.slice(-limit);
}

// -------------------------------------------------------------
// CLI PROCESS EXECUTORS
// -------------------------------------------------------------
function runCodex(prompt, conversationId, model, job) {
  return new Promise((resolve, reject) => {
    const tmpOutputFile = path.join(os.tmpdir(), `codex_${Date.now()}_${Math.random().toString(36).slice(2, 6)}.txt`);
    const args = ["exec", "--json"];
    if (model && model !== "default" && model !== "auto") args.push("--model", model);
    if (conversationId) {
      args.push("resume", conversationId);
    }
    args.push("--output-last-message", tmpOutputFile);
    // Sandbox level comes from settings so it can be tightened from the phone.
    for (const flag of settings.codexSandboxArgs()) args.push(flag);
    args.push("--skip-git-repo-check", prompt);

    const child = spawn(CODEX_BIN, args, {
      cwd: WORKDIR,
      env: Object.assign({}, process.env, {
        PATH: (process.env.PATH || "") + ":/usr/bin:/usr/local/bin:/home/ubuntu/.local/bin"
      }),
      stdio: ["ignore", "pipe", "pipe"]
    });

    let fullOutput = "";
    let error = "";
    let lastCodexError = "";
    let agentMessage = "";
    let lastActivity = Date.now();
    child.stdout.on("data", chunk => {
      lastActivity = Date.now();
      const text = chunk.toString();
      fullOutput += text;
      for (const line of text.split("\n")) {
        try {
          const event = JSON.parse(line);
          if (event.type === "thread.started" && event.thread_id) {
            activeCodexSessionId = event.thread_id;
          }
          // The run can fail while the process still exits 0, so failures are
          // read from the event stream rather than the exit code.
          if (event.type === "error" && event.message) {
            lastCodexError = String(event.message);
          }
          if (event.type === "turn.failed" && event.error && event.error.message) {
            lastCodexError = String(event.error.message);
          }
          if (event.type === "item.completed" && event.item
              && event.item.type === "agent_message" && event.item.text) {
            agentMessage = String(event.item.text);
          }

          events.broadcast("cli.event", {
            jobId: job ? job.id : null,
            engine: "codex",
            conversationId: activeCodexSessionId,
            event
          });
        } catch (e) {}
      }
    });
    child.stderr.on("data", chunk => {
      lastActivity = Date.now();
      error += chunk.toString();
    });

    const timeoutMs = settings.taskTimeoutMs();
    const timer = setInterval(() => {
      if (Date.now() - lastActivity > timeoutMs) {
        clearInterval(timer);
        child.kill("SIGTERM");
        reject(new Error(`Codex CLI timed out after ${Math.round(timeoutMs / 60000)} minutes of inactivity`));
      }
    }, 10000);

    child.on("error", err => {
      clearInterval(timer);
      try { if (fs.existsSync(tmpOutputFile)) fs.unlinkSync(tmpOutputFile); } catch(e) {}
      reject(err);
    });

    child.on("close", code => {
      clearInterval(timer);
      let assistantMsg = "";
      try {
        if (fs.existsSync(tmpOutputFile)) {
          assistantMsg = fs.readFileSync(tmpOutputFile, "utf8").trim();
          fs.unlinkSync(tmpOutputFile);
        }
      } catch (e) {}

      // The agent message parsed from the stream, never the raw stream itself:
      // dumping the JSONL turned a failed run into a wall of events presented
      // as the assistant's reply.
      if (!assistantMsg && agentMessage.trim()) {
        assistantMsg = agentMessage.trim();
      }

      // With --json the id arrives as a thread.started event, not as text.
      // Falling back to it is what makes resuming a new session possible.
      let returnedSessionId = conversationId || activeCodexSessionId || null;
      const m = fullOutput.match(/session id:\s*([a-zA-Z0-9_-]+)/i);
      if (m) {
        returnedSessionId = m[1].trim();
      }

      if (assistantMsg) {
        activeCodexSessionId = returnedSessionId;
        resolve({ response: assistantMsg, sessionId: returnedSessionId });
      } else if (lastCodexError) {
        // Surface the provider's own words — "402 Payment Required", a missing
        // model, a bad key — instead of a silent empty turn.
        reject(new Error(lastCodexError));
      } else if (code === 0) {
        activeCodexSessionId = returnedSessionId;
        resolve({ response: "Done.", sessionId: returnedSessionId });
      } else {
        reject(new Error((error || fullOutput || `Codex exited with code ${code}`).trim()));
      }
    });
  });
}

function runAgy(prompt, conversationId, resume = false, model, job) {
  return new Promise((resolve, reject) => {
    const extraPath = ":/home/ubuntu/.local/bin:/usr/local/bin";
    const env = Object.assign({}, process.env, {
      PATH: (process.env.PATH || "") + extraPath
    });
    const args = [];
    if (conversationId) {
      args.push("--conversation", conversationId);
    } else if (resume) {
      args.push("-c");
    }
    args.push("-p", prompt, "--dangerously-skip-permissions");
    if (model && model !== "auto" && model !== "default") args.push("--model", model);

    const startTime = Date.now();
    let discoveredConvId = conversationId || null;

    const child = spawn(AGY_BIN, args, {
      cwd: WORKDIR,
      env,
      stdio: ["ignore", "pipe", "pipe"]
    });
    let output = "";
    let error = "";
    let lastActivity = Date.now();
    child.stdout.on("data", chunk => {
      lastActivity = Date.now();
      const text = chunk.toString();
      output += text;

      if (!discoveredConvId) {
        discoveredConvId = findLatestAgyConversationId(startTime - 3000);
        if (discoveredConvId && job) {
          jobs.update(job.id, { conversationId: discoveredConvId });
        }
      }

      events.broadcast("cli.output", {
        jobId: job ? job.id : null,
        engine: "antigravity",
        conversationId: discoveredConvId || conversationId,
        chunk: text
      });
    });
    child.stderr.on("data", chunk => {
      lastActivity = Date.now();
      error += chunk.toString();
    });

    const timeoutMs = settings.taskTimeoutMs();
    const timer = setInterval(() => {
      if (Date.now() - lastActivity > timeoutMs) {
        clearInterval(timer);
        child.kill("SIGTERM");
        reject(new Error(`Antigravity CLI timed out after ${Math.round(timeoutMs / 60000)} minutes of inactivity`));
      }
    }, 10000);

    child.on("error", err => {
      clearInterval(timer);
      reject(err);
    });
    child.on("close", code => {
      clearInterval(timer);
      if (!discoveredConvId) {
        discoveredConvId = findLatestAgyConversationId(startTime - 5000);
      }
      if (discoveredConvId && job) {
        jobs.update(job.id, { conversationId: discoveredConvId });
      }
      if (code === 0 || output.trim().length > 0) {
        resolve({
          response: output.trim() || "Done.",
          sessionId: discoveredConvId
        });
      } else {
        reject(new Error((error || output || `Antigravity CLI exited with code ${code}`).trim()));
      }
    });
  });
}

// -------------------------------------------------------------
// TRANSCRIPT SEARCH
// -------------------------------------------------------------
// Files whose mtime tells us a session's transcript changed.
function transcriptSources(convId) {
  const home = os.homedir();
  const sources = [
    path.join(home, ".gemini/antigravity-cli/brain", convId, ".system_generated/logs/transcript.jsonl")
  ];
  const rollout = findCodexRolloutFile(convId);
  if (rollout) sources.push(rollout);
  return sources;
}

function searchTranscripts(query, limit, engineFilter) {
  // Index every session, then narrow the answer: switching engines must not
  // force a re-index of the sessions it just hid.
  const sData = getSessions();
  const sessions = sData.sessions || [];

  // Only sessions whose transcript actually changed get re-read.
  for (const session of sessions) {
    if (!session.conversationId) continue;
    searchIndex.sync(
      session,
      transcriptSources(session.conversationId),
      () => getTranscript(session.conversationId, 1000)
    );
  }
  searchIndex.dropMissing(sessions.map(s => s.conversationId).filter(Boolean));
  searchIndex.persist();

  const results = searchIndex.query(query, limit, engineFilter ? normalizeEngine(engineFilter) : null);
  return {
    ok: true,
    query,
    engine: engineFilter || null,
    count: results.length,
    results,
    indexed: searchIndex.stats().sessions
  };
}

function exportFilename(session) {
  const safeTitle = (session.title || "sesi")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 50) || "sesi";
  return `${safeTitle}-${(session.conversationId || "").slice(0, 8)}.md`;
}

function transcriptToMarkdown(session, turns) {
  const lines = [];
  lines.push(`# ${session.title || "Sesi"}`);
  lines.push("");
  lines.push(`- Engine: ${session.engine || "unknown"}`);
  lines.push(`- Session: \`${session.conversationId}\``);
  if (session.timestamp) lines.push(`- Terakhir: ${new Date(session.timestamp).toISOString()}`);
  lines.push("");
  lines.push("---");
  lines.push("");

  for (const turn of turns || []) {
    const content = typeof turn.content === "string" ? turn.content.trim() : "";
    if (!content) continue;

    if (turn.role === "user") {
      lines.push("## 👤 User", "", content, "");
    } else if (turn.role === "assistant") {
      lines.push("## 🤖 Assistant", "", content, "");
    } else if (turn.role === "thinking") {
      lines.push("<details><summary>💭 Thinking</summary>", "", content, "", "</details>", "");
    } else if (turn.role === "tool") {
      const title = turn.title || turn.toolTitle || "Tool";
      lines.push(`<details><summary>🔧 ${title}</summary>`, "", "```", content, "```", "", "</details>", "");
    }
  }
  return lines.join("\n");
}

function audit(event, details) {
  auditLog.log(event, details);
}

// Runs one chat job to completion, broadcasting lifecycle events and storing
// the outcome so a client that disconnected can still collect it.
async function runChatJob(job, payload) {
  let prompt = job.prompt;
  const engine = job.engine;
  const model = job.model;
  const conversationId = job.conversationId;
  const resume = payload.resume === true && Boolean(conversationId);

  if (Array.isArray(payload.attachedFiles) && payload.attachedFiles.length > 0) {
    prompt = payload.attachedFiles.map(f => `[Attached File: ${f}]`).join("\n") + "\n" + prompt;
  } else if (payload.attachedFile) {
    prompt = `[Attached File: ${payload.attachedFile}]\n` + prompt;
  }

  events.broadcast("task.started", {
    jobId: job.id,
    engine,
    conversationId,
    prompt: prompt.slice(0, 200),
    startedAt: new Date(job.createdAt).toISOString()
  });

  try {
    let responseText;
    let activeConvId = conversationId;
    let activeSession = null;
    let updatedTurns = [];

    if (engine === "codex") {
      const result = await runCodex(prompt, conversationId, model, job);
      responseText = result.response;
      activeConvId = result.sessionId || conversationId;

      const sData = getSessions();
      activeSession = sData.sessions.find(s => s.conversationId === activeConvId) || {
        conversationId: activeConvId,
        title: prompt.slice(0, 40),
        workspace: WORKDIR,
        engine: "codex"
      };
      updatedTurns = activeConvId ? getTranscript(activeConvId, 1000) : [];
      if (updatedTurns.length === 0) {
        updatedTurns = [
          { role: "user", content: prompt, time: new Date().toISOString() },
          { role: "assistant", content: responseText, time: new Date().toISOString() }
        ];
      }
    } else {
      const isNewSession = !conversationId;
      const startTime = Date.now();
      const result = await runAgy(prompt, conversationId, resume, model, job);
      responseText = typeof result === "object" ? result.response : result;
      activeConvId = (typeof result === "object" && result.sessionId) ? result.sessionId : (isNewSession ? findLatestAgyConversationId(startTime - 10000) : conversationId);

      const sData = getSessions();
      if (!activeConvId && isNewSession) {
        const newestAgy = sData.sessions.find(s => s.engine === "antigravity");
        if (newestAgy) activeConvId = newestAgy.conversationId;
      }

      activeSession = (activeConvId && sData.sessions.find(s => s.conversationId === activeConvId))
        || { conversationId: activeConvId || "new", title: prompt.slice(0, 40), workspace: WORKDIR, engine: "antigravity" };

      updatedTurns = activeConvId ? getTranscript(activeConvId, 1000) : [];
      if (updatedTurns.length === 0) {
        updatedTurns = [
          { role: "user", content: prompt, time: new Date(startTime).toISOString() },
          { role: "assistant", content: responseText, time: new Date().toISOString() }
        ];
      }
    }

    const result = {
      response: responseText,
      engine,
      model: model || "default",
      conversationId: activeConvId,
      session: activeSession,
      turns: updatedTurns,
      timestamp: new Date().toISOString()
    };

    jobs.finish(job.id, {
      state: "done",
      response: responseText,
      conversationId: activeConvId,
      session: activeSession,
      turns: updatedTurns
    });

    audit("chat.finished", { jobId: job.id, engine, conversationId: activeConvId });

    events.broadcast("task.finished", {
      ok: true,
      jobId: job.id,
      engine,
      conversationId: activeConvId,
      title: activeSession && activeSession.title ? activeSession.title : "Sesi",
      summary: (responseText || "").slice(0, 200),
      finishedAt: new Date().toISOString()
    });

    return result;
  } catch (err) {
    const message = err.message || "Failed to execute command";
    const failedTurns = [
      { role: "user", content: prompt, time: new Date().toISOString() },
      { role: "assistant", content: `**Gagal menjalankan perintah**\n\n\`\`\`\n${message}\n\`\`\``, time: new Date().toISOString() }
    ];
    jobs.finish(job.id, {
      state: "failed",
      error: message,
      turns: failedTurns,
      conversationId: job.conversationId || conversationId
    });
    audit("chat.failed", { jobId: job.id, engine, error: message });

    events.broadcast("task.finished", {
      ok: false,
      jobId: job.id,
      engine,
      conversationId: job.conversationId || conversationId,
      error: message,
      finishedAt: new Date().toISOString()
    });
    throw err;
  }
}

// -------------------------------------------------------------
// HTTP SERVER & ROUTING
// -------------------------------------------------------------
const server = http.createServer((req, res) => {
  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;

  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "Authorization, Content-Type",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS"
    });
    return res.end();
  }

  if (req.method === "GET" && (pathname === "/health" || pathname === "/api/health")) {
    return send(res, 200, {
      ok: true,
      hostname: os.hostname(),
      engines: ["antigravity", "codex"],
      features: ["chat", "live_monitor", "session_history", "remote_control", "upload", "multi_upload",
                 "usage_stats", "sse", "files", "git", "search", "sandbox_modes",
                 "jobs", "file_write", "projects", "audit", "session_export", "uploads_cleanup",
                 "codex_config", "session_archive", "operational_health", "device_management"],
      sandboxMode: settings.load().sandboxMode
    });
  }

  if (req.method === "GET" && pathname === "/api/health/operations") {
    if (!authorized(req)) return send(res, 401, { error: "Unauthorized" });
    return send(res, 200, { ok: true, health: operationalHealth() });
  }

  if (req.method === "GET" && pathname === "/api/logs") {
    const limit = Math.min(Math.max(Number(parsedUrl.query.lines) || 200, 10), 1000);
    return send(res, 200, { ok: true, ...readBridgeLogs(limit) });
  }

  if (req.method === "GET" && pathname === "/api/backup") {
    return send(res, 200, { ok: true, backup: backupPayload() });
  }

  if (req.method === "POST" && pathname === "/api/backup/restore") {
    let raw = "";
    req.on("data", chunk => raw += chunk);
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        const backup = payload.backup || payload;
        if (!backup || typeof backup !== "object") throw new Error("Invalid backup payload");
        if (backup.settings) settings.save(backup.settings);
        send(res, 200, { ok: true, restored: { settings: Boolean(backup.settings), promptLibrary: false, devices: false } });
      } catch (err) {
        send(res, 400, { error: err.message });
      }
    });
    return;
  }

  if (req.method === "POST" && pathname === "/api/maintenance/restart") {
    if (!authorized(req)) return send(res, 401, { error: "Unauthorized" });
    send(res, 200, { ok: true, message: "Graceful restart initiated in background" });
    setTimeout(() => {
      try {
        const { spawn } = require("child_process");
        const child = spawn("bash", ["-c", "sleep 0.5 && codex-remote restart --detach >/dev/null 2>&1"], {
          detached: true,
          stdio: "ignore"
        });
        child.unref();
      } catch (e) {}
    }, 300);
    return;
  }

  if (req.method === "POST" && pathname === "/api/maintenance/prune") {
    if (!authorized(req)) return send(res, 401, { error: "Unauthorized" });
    try {
      const uploadDir = path.join(config.CONFIG_DIR, "uploads");
      if (fs.existsSync(uploadDir)) {
        const now = Date.now();
        const files = fs.readdirSync(uploadDir);
        for (const file of files) {
          const filePath = path.join(uploadDir, file);
          try {
            const stat = fs.statSync(filePath);
            if (now - stat.mtimeMs > 24 * 60 * 60 * 1000) {
              fs.unlinkSync(filePath);
            }
          } catch (e) {}
        }
      }
      return send(res, 200, { ok: true, message: "Pruned temporary cache" });
    } catch (err) {
      return send(res, 500, { error: err.message });
    }
  }

  if ((req.method === "GET" && pathname === "/api/devices") ||
      (req.method === "POST" && pathname === "/api/devices/revoke")) {
    if (!authorized(req)) return send(res, 401, { error: "Unauthorized" });
    if (isDeviceRevoked(req)) {
      auditLog.log("device.revoked_access", { path: pathname, device: deviceId(req) });
      return send(res, 403, { error: "Perangkat telah dicabut", code: "DEVICE_REVOKED" });
    }
    try { registerDevice(req); } catch {}

    if (req.method === "GET") {
      const entries = Object.values(loadDeviceRegistry()).sort((a, b) => b.lastSeenAt - a.lastSeenAt);
      return send(res, 200, { ok: true, devices: entries });
    }

    let raw = "";
    req.on("data", chunk => raw += chunk);
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        const id = String(payload.id || "");
        const registry = loadDeviceRegistry();
        if (!registry[id]) return send(res, 404, { error: "Device not found" });
        registry[id].revoked = payload.revoked !== false;
        saveDeviceRegistry(registry);
        auditLog.log(registry[id].revoked ? "device.revoked" : "device.restored", { device: id });
        return send(res, 200, { ok: true, device: registry[id] });
      } catch (err) {
        send(res, 400, { error: err.message });
      }
    });
    return;
  }

  if (!authorized(req)) {
    return send(res, 401, { error: "Unauthorized" });
  }

  if (isDeviceRevoked(req)) {
    auditLog.log("device.revoked_access", { path: pathname, device: deviceId(req) });
    return send(res, 403, { error: "Perangkat telah dicabut", code: "DEVICE_REVOKED" });
  }

  try { registerDevice(req); } catch {}

  if (req.method === "POST" && isWriteBlocked(pathname)) {
    auditLog.log("write.blocked", { path: pathname, sandboxMode: "readonly", device: deviceId(req) });
    return send(res, 403, { error: WRITE_BLOCKED_MESSAGE });
  }

  // GET /api/events  (Server-Sent Events live stream)
  if (req.method === "GET" && pathname === "/api/events") {
    events.addClient(req, res);
    return;
  }

  // GET /api/settings  |  POST /api/settings
  if (pathname === "/api/settings") {
    if (req.method === "GET") {
      return send(res, 200, {
        ok: true,
        settings: settings.load(),
        sandboxModes: Object.keys(settings.SANDBOX_MODES).map(key => ({
          key,
          label: settings.SANDBOX_MODES[key].label
        }))
      });
    }
    if (req.method === "POST") {
      let raw = "";
      req.on("data", chunk => { raw += chunk; });
      req.on("end", () => {
        try {
          const saved = settings.save(JSON.parse(raw || "{}"));
          events.broadcast("settings.changed", saved);
          send(res, 200, { ok: true, settings: saved });
        } catch (err) {
          send(res, 400, { error: err.message });
        }
      });
      return;
    }
  }

  // GET /api/files?path=...
  if (req.method === "GET" && pathname === "/api/files") {
    const result = files.list(WORKDIR, parsedUrl.query.path || ".");
    return send(res, result.error ? 400 : 200, result);
  }

  // GET /api/files/read?path=...
  if (req.method === "GET" && pathname === "/api/files/read") {
    if (!parsedUrl.query.path) return send(res, 400, { error: "Missing path" });
    const result = files.read(WORKDIR, parsedUrl.query.path);
    return send(res, result.error ? 400 : 200, result);
  }

  // POST /api/files/write
  if (req.method === "POST" && pathname === "/api/files/write") {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 4 * 1024 * 1024) req.destroy();
    });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        const result = files.write(WORKDIR, payload.path, payload.content);
        audit("file.write", { path: payload.path, ok: Boolean(result.ok), error: result.error });
        if (result.ok) events.broadcast("file.changed", { path: payload.path });
        send(res, result.ok ? 200 : 400, result);
      } catch (err) {
        send(res, 400, { error: err.message });
      }
    });
    return;
  }

  // POST /api/terminal/exec
  if (req.method === "POST" && pathname === "/api/terminal/exec") {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 1024 * 1024) req.destroy();
    });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        const rawCmd = typeof payload.command === "string" ? payload.command.trim() : "";
        if (!rawCmd) return send(res, 400, { error: "command is required" });
        
        if (!global.__terminalCwd) {
          global.__terminalCwd = fs.existsSync(path.join(WORKDIR, "codexcli-remote-app")) 
            ? path.join(WORKDIR, "codexcli-remote-app") 
            : WORKDIR;
        }

        // Handle cd command
        if (rawCmd.startsWith("cd ") || rawCmd === "cd") {
          const target = rawCmd === "cd" ? (WORKDIR || os.homedir()) : rawCmd.slice(3).trim();
          const newDir = path.isAbsolute(target) ? target : path.resolve(global.__terminalCwd, target);
          if (fs.existsSync(newDir) && fs.statSync(newDir).isDirectory()) {
            global.__terminalCwd = newDir;
            return send(res, 200, {
              ok: true,
              command: rawCmd,
              cwd: global.__terminalCwd,
              output: `Directory changed to: ${global.__terminalCwd}\n`,
              exitCode: 0
            });
          } else {
            return send(res, 200, {
              ok: false,
              command: rawCmd,
              cwd: global.__terminalCwd,
              output: `cd: no such file or directory: ${target}\n`,
              exitCode: 1,
              error: "Directory not found"
            });
          }
        }

        audit("terminal.exec", { command: rawCmd.slice(0, 100), cwd: global.__terminalCwd });
        
        const child_process = require("child_process");
        child_process.exec(rawCmd, {
          cwd: global.__terminalCwd,
          shell: "/bin/bash",
          timeout: 45000,
          maxBuffer: 4 * 1024 * 1024,
          env: Object.assign({}, process.env, {
            PATH: (process.env.PATH || "") + ":/home/ubuntu/.local/bin:/usr/local/bin:/usr/bin:/bin"
          })
        }, (error, stdout, stderr) => {
          const out = (stdout || "") + (stderr || "");
          send(res, 200, {
            ok: !error,
            command: rawCmd,
            cwd: global.__terminalCwd,
            output: out.length > 0 ? out : (error ? error.message : "(Perintah selesai tanpa output)\n"),
            exitCode: error ? (error.code || 1) : 0,
            error: error ? error.message : null
          });
        });
      } catch (err) {
        send(res, 400, { error: err.message });
      }
    });
    return;
  }

  // GET /api/session/export?id=...  -> Markdown transcript
  if (req.method === "GET" && pathname === "/api/session/export") {
    const convId = parsedUrl.query.id;
    if (!convId) return send(res, 400, { error: "Missing session id parameter" });
    const sData = getSessions();
    const session = (sData.sessions || []).find(x => x.conversationId === convId)
      || { conversationId: convId, title: "Sesi", engine: "unknown" };
    return send(res, 200, {
      ok: true,
      conversationId: convId,
      title: session.title,
      filename: exportFilename(session),
      markdown: transcriptToMarkdown(session, getTranscript(convId, 2000))
    });
  }

  // GET /api/codex/models — the active provider's own catalogue
  if (req.method === "GET" && pathname === "/api/codex/models") {
    const current = codexConfig.read();
    const id = parsedUrl.query.provider || current.activeProvider;
    const secret = codexConfig.providerSecret(id);
    if (!secret) {
      return send(res, 200, { ok: false, error: "Provider tidak ditemukan di config.toml", models: [] });
    }
    providerModels.list(secret, parsedUrl.query.refresh === "1").then(result => {
      send(res, 200, Object.assign({ provider: id, activeModel: current.activeModel }, result));
    }).catch(err => send(res, 200, { ok: false, error: err.message, models: [] }));
    return;
  }

  // GET /api/codex/config
  if (req.method === "GET" && pathname === "/api/codex/config") {
    return send(res, 200, codexConfig.read());
  }

  // POST /api/codex/provider        upsert a model provider
  // POST /api/codex/provider/delete remove one
  // POST /api/codex/active          switch the active provider/model
  if (req.method === "POST" && pathname.startsWith("/api/codex/")) {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 64 * 1024) req.destroy();
    });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        let result;
        if (pathname === "/api/codex/provider") {
          result = codexConfig.upsertProvider(payload);
          // This decides where prompts and code are sent, so it is recorded.
          audit("codex.provider.saved", { id: payload.id, baseUrl: payload.baseUrl, ok: result.ok });
        } else if (pathname === "/api/codex/provider/delete") {
          result = codexConfig.removeProvider(payload.id);
          audit("codex.provider.deleted", { id: payload.id, ok: result.ok });
        } else if (pathname === "/api/codex/active") {
          result = codexConfig.setActive(payload);
          audit("codex.provider.activated", { provider: payload.provider, model: payload.model, ok: result.ok });
        } else {
          return send(res, 404, { error: "Not found" });
        }

        if (result.ok) events.broadcast("codex.config.changed", codexConfig.read());
        send(res, result.ok ? 200 : 400, Object.assign({}, result, { config: codexConfig.read() }));
      } catch (err) {
        send(res, 400, { error: err.message });
      }
    });
    return;
  }

  // GET /api/projects  |  POST /api/projects
  if (pathname === "/api/projects") {
    if (req.method === "GET") {
      const saved = settings.load().projects || [];
      return send(res, 200, {
        ok: true,
        workdir: WORKDIR,
        projects: saved.map(p => Object.assign({}, p, {
          exists: Boolean(files.safeResolve(WORKDIR, p.path)) && fs.existsSync(files.safeResolve(WORKDIR, p.path)),
          isRepo: Boolean(files.safeResolve(WORKDIR, p.path)) && git.isRepo(files.safeResolve(WORKDIR, p.path))
        }))
      });
    }
    if (req.method === "POST") {
      let raw = "";
      req.on("data", chunk => { raw += chunk; });
      req.on("end", () => {
        try {
          const payload = JSON.parse(raw || "{}");
          const saved = settings.save({ projects: payload.projects });
          audit("projects.updated", { count: saved.projects.length });
          send(res, 200, { ok: true, projects: saved.projects });
        } catch (err) {
          send(res, 400, { error: err.message });
        }
      });
      return;
    }
  }

  // POST /api/uploads/cleanup
  if (req.method === "POST" && pathname === "/api/uploads/cleanup") {
    let raw = "";
    req.on("data", chunk => { raw += chunk; });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        const days = payload.olderThanDays !== undefined
          ? settings.clampNumber(payload.olderThanDays, 0, 365, settings.load().uploadRetentionDays)
          : settings.load().uploadRetentionDays;
        const result = files.pruneOlderThan(UPLOADS_DIR, days);
        audit("uploads.cleanup", { days, removed: result.removed.length, freedBytes: result.freedBytes });
        send(res, 200, Object.assign({ olderThanDays: days }, result));
      } catch (err) {
        send(res, 400, { error: err.message });
      }
    });
    return;
  }

  // GET /api/uploads
  if (req.method === "GET" && pathname === "/api/uploads") {
    const listing = files.list(WORKDIR, path.relative(WORKDIR, UPLOADS_DIR) || "uploads");
    const total = (listing.entries || []).reduce((sum, e) => sum + (e.size || 0), 0);
    return send(res, 200, Object.assign({ totalBytes: total, retentionDays: settings.load().uploadRetentionDays }, listing));
  }

  // GET /api/git/status  |  /api/git/diff
  if (req.method === "GET" && pathname === "/api/git/status") {
    const repo = parsedUrl.query.path ? files.safeResolve(WORKDIR, parsedUrl.query.path) : WORKDIR;
    if (!repo) return send(res, 400, { error: "Path outside workspace" });
    return send(res, 200, git.status(repo));
  }

  if (req.method === "GET" && pathname === "/api/git/diff") {
    const repo = parsedUrl.query.path ? files.safeResolve(WORKDIR, parsedUrl.query.path) : WORKDIR;
    if (!repo) return send(res, 400, { error: "Path outside workspace" });
    return send(res, 200, git.diff(repo, parsedUrl.query.file || null));
  }

  // POST /api/git/commit  |  /api/git/push
  if (req.method === "POST" && (pathname === "/api/git/commit" || pathname === "/api/git/push")) {
    let raw = "";
    req.on("data", chunk => { raw += chunk; });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        requireApproval(req, res, pathname, payload, () => {
          const repo = payload.path ? files.safeResolve(WORKDIR, payload.path) : WORKDIR;
          if (!repo) return send(res, 400, { error: "Path outside workspace" });
          const result = pathname === "/api/git/commit"
            ? git.commit(repo, payload.message, payload.addAll !== false)
            : git.push(repo);
          audit(pathname === "/api/git/commit" ? "git.commit" : "git.push", {
            path: payload.path || ".",
            ok: result.ok,
            message: payload.message ? String(payload.message).slice(0, 120) : undefined,
            device: deviceId(req)
          });
          events.broadcast("git.changed", { path: payload.path || "." });
          send(res, result.ok ? 200 : 400, result);
        });
      } catch (err) {
        send(res, 500, { error: err.message });
      }
    });
    return;
  }

  // GET /api/search?q=...
  if (req.method === "GET" && pathname === "/api/search") {
    const query = (parsedUrl.query.q || "").trim();
    if (!query) return send(res, 400, { error: "Missing q parameter" });
    return send(res, 200, searchTranscripts(query, Number(parsedUrl.query.limit) || 40, parsedUrl.query.engine));
  }

  // GET /api/usage
  if (req.method === "GET" && pathname === "/api/usage") {
    if (parsedUrl.query.refresh === "1") {
      cachedUsage = null;
      quota.reset();
    }
    const stats = parsedUrl.query.engine === "codex" ? getCodexUsageStats() : getUsageStats();
    return send(res, 200, stats);
  }

  // POST /api/upload
  if (req.method === "POST" && pathname === "/api/upload") {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 50 * 1024 * 1024) req.destroy();
    });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw);
        const filename = payload.filename || `upload_${Date.now()}.png`;
        const base64Data = payload.data || payload.base64 || "";
        if (!base64Data) {
          return send(res, 400, { error: "data (base64) is required" });
        }
        const buffer = Buffer.from(base64Data, "base64");
        const safeName = `${Date.now()}_${path.basename(filename).replace(/[^a-zA-Z0-9._-]/g, "_")}`;
        const targetPath = path.join(UPLOADS_DIR, safeName);
        fs.writeFileSync(targetPath, buffer);

        send(res, 200, {
          ok: true,
          filename: safeName,
          filePath: targetPath,
          url: `/api/uploads/${safeName}`,
          sizeBytes: buffer.length
        });
      } catch (err) {
        send(res, 500, { error: err.message });
      }
    });
    return;
  }

  // GET /api/uploads/:file
  if (req.method === "GET" && pathname.startsWith("/api/uploads/")) {
    const fileName = path.basename(pathname.replace("/api/uploads/", ""));
    const filePath = path.join(UPLOADS_DIR, fileName);
    if (fs.existsSync(filePath)) {
      const ext = path.extname(fileName).toLowerCase();
      let contentType = "application/octet-stream";
      if (ext === ".png") contentType = "image/png";
      else if (ext === ".jpg" || ext === ".jpeg") contentType = "image/jpeg";
      else if (ext === ".webp") contentType = "image/webp";
      else if (ext === ".gif") contentType = "image/gif";
      else if (ext === ".svg") contentType = "image/svg+xml";

      res.writeHead(200, { "Content-Type": contentType, "Access-Control-Allow-Origin": "*" });
      return fs.createReadStream(filePath).pipe(res);
    } else {
      return send(res, 404, { error: "File not found" });
    }
  }

  // GET /api/session/live
  if (req.method === "GET" && pathname === "/api/session/live") {
    const agyProc = getAgyProcess();
    const codexProc = getCodexProcess();
    const proc = codexProc.running ? codexProc : agyProc;
    const sData = getSessions();
    const latestCodex = (sData.sessions || []).find(s => s.engine === "codex");
    const latest = activeCodexSessionId ? (sData.sessions || []).find(s => s.conversationId === activeCodexSessionId) || latestCodex : latestCodex || (sData.sessions && sData.sessions[0]);
    const convId = activeCodexSessionId || (latest ? latest.conversationId : null);
    const msgs = convId ? getTranscript(convId, 1000) : [];
    return send(res, 200, {
      ok: true,
      process: proc,
      conversationId: convId,
      session: latest || null,
      turns: msgs,
      messages: msgs
    });
  }

  // GET /api/sessions
  if (req.method === "GET" && pathname === "/api/sessions") {
    const sData = getSessions(parsedUrl.query.engine);
    const includeArchived = parsedUrl.query.includeArchived === "1";
    return send(res, 200, {
      ok: true,
      hostname: sData.hostname,
      engine: parsedUrl.query.engine || null,
      archivedCount: archive.count(),
      includeArchived,
      sessions: archive.apply(sData.sessions || [], includeArchived)
    });
  }

  // POST /api/sessions/archive — hides a session from the list. The transcript
  // is never touched, so this is always reversible.
  if (req.method === "POST" && pathname === "/api/sessions/archive") {
    let raw = "";
    req.on("data", chunk => { raw += chunk; });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        const wanted = payload.archived !== false;
        const result = archive.setArchived(payload.conversationId, wanted);
        audit(wanted ? "session.archived" : "session.unarchived", {
          conversationId: payload.conversationId, ok: result.ok
        });
        if (result.ok) events.broadcast("sessions.changed", { archivedCount: archive.count() });
        send(res, result.ok ? 200 : 400, Object.assign({}, result, { archivedCount: archive.count() }));
      } catch (err) {
        send(res, 400, { error: err.message });
      }
    });
    return;
  }

  // GET /api/session/transcript?id=...
  if (req.method === "GET" && pathname === "/api/session/transcript") {
    const convId = parsedUrl.query.id;
    if (!convId) {
      return send(res, 400, { error: "Missing session id parameter" });
    }
    touchSessionActivity(convId);
    const msgs = getTranscript(convId, 1000);
    const sData = getSessions();
    const foundSession = (sData.sessions || []).find(s => s.conversationId === convId) || { conversationId: convId, title: "Session" };
    const customTitles = getCustomSessionTitles();
    if (customTitles[convId]) {
      foundSession.title = customTitles[convId];
      foundSession.customTitle = true;
    }
    return send(res, 200, {
      ok: true,
      conversationId: convId,
      session: foundSession,
      turns: msgs,
      messages: msgs
    });
  }

  // POST /api/session/rename
  if (req.method === "POST" && pathname === "/api/session/rename") {
    let raw = "";
    req.on("data", chunk => { raw += chunk; });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        const convId = payload.id || payload.conversationId;
        const newTitle = (payload.title || "").trim();
        if (!convId) {
          return send(res, 400, { error: "Missing session id (id or conversationId)" });
        }
        if (!newTitle) {
          return send(res, 400, { error: "Title cannot be empty" });
        }
        saveCustomSessionTitle(convId, newTitle);
        touchSessionActivity(convId);
        audit("session.rename", { conversationId: convId, title: newTitle });
        return send(res, 200, {
          ok: true,
          conversationId: convId,
          title: newTitle
        });
      } catch (err) {
        send(res, 500, { error: err.message });
      }
    });
    return;
  }

  // POST /api/session/control
  if (req.method === "POST" && pathname === "/api/session/control") {
    let raw = "";
    req.on("data", chunk => { raw += chunk; });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        const action = payload.action;
        if (action === "stop" || action === "kill") {
          requireApproval(req, res, pathname, payload, () => {
            try {
              execSync("pkill -f \"agy -p\" || true");
              execSync("pkill -f \"codex exec\" || true");
              return send(res, 200, { ok: true, message: "Process interrupted" });
            } catch(e) {
              return send(res, 200, { ok: true, message: "No running process found" });
            }
          });
          return;
        }
        send(res, 400, { error: "Unknown action" });
      } catch (err) {
        send(res, 500, { error: err.message });
      }
    });
    return;
  }

  // POST /api/chat
  // Pass async:true to get a jobId immediately; otherwise the request waits
  // for the run to finish, which is what older app builds expect.
  if (req.method === "POST" && pathname === "/api/chat") {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 2000000) req.destroy();
    });

    req.on("end", async () => {
      let payload;
      try {
        payload = JSON.parse(raw);
      } catch (err) {
        return send(res, 400, { error: "Invalid JSON body" });
      }

      if (typeof payload.prompt !== "string" || !payload.prompt.trim()) {
        return send(res, 400, { error: "prompt is required" });
      }

      // A leaked token should not be able to spawn CLI runs in a loop.
      const limit = auditLog.rateLimit(`chat:${deviceId(req)}`, 20, 60 * 1000);
      if (!limit.allowed) {
      audit("chat.rate_limited", { retryAfterMs: limit.retryAfterMs });
      auditLog.log("chat.rate_limited", { retryAfterMs: limit.retryAfterMs, device: deviceId(req) });
        return send(res, 429, {
          error: "Terlalu banyak permintaan. Coba lagi sebentar.",
          retryAfterMs: limit.retryAfterMs
        });
      }

      const wantsAsync = payload.async === true;
      const job = jobs.create({
        engine: (payload.engine || payload.cli || "antigravity").toLowerCase().trim(),
        model: typeof payload.model === "string" ? payload.model.trim() : "",
        prompt: payload.prompt.trim(),
        conversationId: payload.conversationId || payload.session_id || null
      });

      audit("chat.started", {
        jobId: job.id,
        engine: job.engine,
        model: job.model,
        conversationId: job.conversationId,
        promptPreview: job.prompt.slice(0, 120)
      });

      if (wantsAsync) {
        send(res, 202, { ok: true, accepted: true, jobId: job.id, state: job.state });
      }

      try {
        const result = await runChatJob(job, payload);
        if (!wantsAsync) {
          send(res, 200, Object.assign({ ok: true, jobId: job.id }, result));
        }
      } catch (err) {
        if (!wantsAsync) {
          send(res, 500, { error: err.message || "Failed to execute command", jobId: job.id });
        }
      }
    });
    return;
  }

  // GET /api/audit
  if (req.method === "GET" && pathname === "/api/audit") {
    return send(res, 200, { ok: true, entries: auditLog.read(Number(parsedUrl.query.limit) || 200) });
  }

  // GET /api/jobs  |  GET /api/jobs/:id
  if (req.method === "GET" && pathname === "/api/jobs") {
    return send(res, 200, { ok: true, running: jobs.running(), recent: jobs.list(20) });
  }

  if (req.method === "GET" && pathname.startsWith("/api/jobs/")) {
    const job = jobs.get(pathname.slice("/api/jobs/".length));
    if (!job) return send(res, 404, { error: "Job not found" });
    return send(res, 200, { ok: true, job });
  }

  send(res, 404, { error: "Not found" });
});

// Uploads used to accumulate forever; sweep once at boot.
try {
  const swept = files.pruneOlderThan(UPLOADS_DIR, settings.load().uploadRetentionDays);
  if (swept.removed && swept.removed.length) {
    console.log(`[Uploads] Removed ${swept.removed.length} file(s), freed ${Math.round(swept.freedBytes / 1024)} KB`);
  }
} catch (e) {}

server.listen(PORT, HOST, () => {
  console.log(`[Antigravity & Codex Remote Bridge] Listening on http://${HOST}:${PORT}`);
  console.log(`[Config] Engine: agy / codex | Workdir: ${WORKDIR}`);
  console.log(`[Security] Token protection: ${TOKEN ? "ENABLED" : "DISABLED"} | Token file: ${config.TOKEN_FILE}`);
  if (HOST === "0.0.0.0") {
    console.warn("[Security] Bound to every interface. Set BRIDGE_HOST=127.0.0.1 to reach it only through the tunnel.");
  }
});
