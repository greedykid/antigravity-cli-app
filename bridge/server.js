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
const opencodeConfig = require("./opencodeconfig");
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
    pathname.startsWith("/api/opencode/") ||
    pathname === "/api/chat" ||
    pathname === "/api/projects" ||
    pathname === "/api/settings" ||
    pathname === "/api/engine/install";
}

const PORT = config.port();
const HOST = config.bindHost();
const TOKEN = config.loadToken();
const WORKDIR = config.workdir();
const AGY_BIN = process.env.AGY_BIN || path.join(os.homedir(), ".local/bin/agy");
const CODEX_BIN = process.env.CODEX_BIN || "codex";
const COMMAND_CODE_BIN = process.env.COMMAND_CODE_BIN || "command-code";

function extendedPath() {
  const home = os.homedir();
  let nodeBin = "";
  try {
    // Prefer the running node's own bin dir (works under systemd where npm
    // is not on PATH); fall back to npm prefix -g.
    if (process.execPath) nodeBin = path.dirname(process.execPath);
    if (!nodeBin || !fs.existsSync(path.join(nodeBin, "node"))) {
      const prefix = execSync("npm prefix -g", { encoding: "utf8", timeout: 3000 }).trim();
      if (prefix) nodeBin = path.join(prefix, "bin");
    }
  } catch (e) {}
  return (process.env.PATH || "") + ":" + [
    path.join(home, ".opencode/bin"),
    path.join(home, ".local/bin"),
    nodeBin,
    "/usr/local/bin",
    "/usr/bin",
    "/bin"
  ].filter(Boolean).join(":");
}

// Strip ANSI CSI escape codes and the carriage returns that ship with progress
// spinners. The Antigravity CLI spews raw escape sequences to stdout, which
// paint as garbage on the phone; we sanitise only the chunks we broadcast, the
// raw accumulator keeps the original text for the final response.
const ANSI_RE = /\x1B\[[0-?]*[ -/]*[@-~]/g;
const BLANK_RUN_RE = /\n{3,}/g;
function stripAnsi(s) {
  if (!s) return s;
  return s.replace(ANSI_RE, "").replace(/\r/g, "").replace(BLANK_RUN_RE, "\n\n");
}

function findOpencodeBin() {
  if (process.env.OPENCODE_BIN && fs.existsSync(process.env.OPENCODE_BIN)) {
    return process.env.OPENCODE_BIN;
  }
  const home = os.homedir();
  const candidates = [
    path.join(home, ".opencode/bin/opencode"),
    path.join(home, ".local/bin/opencode"),
    "/usr/local/bin/opencode",
    "/usr/bin/opencode"
  ];
  for (const c of candidates) {
    if (fs.existsSync(c)) return c;
  }
  try {
    const which = spawnSync("which", ["opencode"], {
      encoding: "utf8",
      env: Object.assign({}, process.env, { PATH: extendedPath() })
    });
    if (which.status === 0 && which.stdout.trim()) {
      return which.stdout.trim().split("\n")[0];
    }
  } catch (e) {}
  return "opencode";
}
const OPENCODE_BIN = findOpencodeBin();

function commandVersion(command, args) {
  try {
    const result = spawnSync(command, args, {
      encoding: "utf8",
      // Command Code's bundle takes several seconds just to print --version,
      // so keep this generous for every engine.
      timeout: 12000,
      env: Object.assign({}, process.env, { PATH: extendedPath() })
    });
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
  const opencode = commandVersion(OPENCODE_BIN, ["--version"]) || commandVersion(OPENCODE_BIN, ["-v"]);
  const commandcode = commandVersion(COMMAND_CODE_BIN, ["--version"]);
  const workdirExists = fs.existsSync(WORKDIR) && fs.statSync(WORKDIR).isDirectory();
  const gitStatus = git.isRepo(WORKDIR) ? "ok" : "not_repository";
  const stats = serverResourceStats();

  return {
    engines: {
      antigravity: agy ? { ok: true, version: agy } : { ok: false },
      codex: codex ? { ok: true, version: codex } : { ok: false },
      opencode: opencode ? { ok: true, version: opencode } : { ok: false },
      commandcode: commandcode ? { ok: true, version: commandcode } : { ok: false }
    },
    filesystem: { ok: workdirExists, workdir: WORKDIR },
    git: gitStatus,
    runningJobs: jobs.running().length,
    server: stats,
    history: resourceHistory
  };
}

function getEnginesStatus() {
  const agy = commandVersion(AGY_BIN, ["--version"]);
  const codex = commandVersion(CODEX_BIN, ["--version"]);
  const opencode = commandVersion(OPENCODE_BIN, ["--version"]) || commandVersion(OPENCODE_BIN, ["-v"]);
  const commandcode = commandVersion(COMMAND_CODE_BIN, ["--version"]);
  return {
    ok: true,
    engines: {
      antigravity: {
        id: "antigravity",
        name: "Antigravity CLI",
        binary: AGY_BIN,
        available: Boolean(agy),
        version: agy || null,
        description: "Google DeepMind internal agentic engine"
      },
      codex: {
        id: "codex",
        name: "Codex CLI",
        binary: CODEX_BIN,
        available: Boolean(codex),
        version: codex || null,
        description: "OpenAI autonomous coding agent engine",
        installCommand: "npm install -g @openai/codex || curl -fsSL https://raw.githubusercontent.com/openai/codex/main/install.sh | bash"
      },
      opencode: {
        id: "opencode",
        name: "OpenCode CLI",
        binary: OPENCODE_BIN,
        available: Boolean(opencode),
        version: opencode || null,
        description: "Open-source multi-provider AI coding engine (DeepSeek, Claude, Ollama local)",
        installCommand: "npm install -g opencode || npm install -g @opencode-ai/cli"
      },
      commandcode: {
        id: "commandcode",
        name: "Command Code CLI",
        binary: COMMAND_CODE_BIN,
        available: Boolean(commandcode),
        version: commandcode || null,
        description: "Agen coding CLI yang terus belajar gaya penulisan kode Anda (multi-provider)",
        installCommand: "npm install -g command-code"
      }
    }
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

let activeCodexSessionId = null;
// jobId -> Codex thread id. Replaces the previous single global so two
// concurrent Codex jobs (different phones, or a fast engine switch) do not
// clobber each other's session id.
const activeCodexSessionIds = new Map();
// jobId -> ChildProcess handle. Lets /api/session/control kill one specific
// run instead of every CLI process on the host.
const runningChildren = new Map();

function registerChild(jobId, child) {
  if (jobId && child) runningChildren.set(jobId, child);
}

function unregisterChild(jobId) {
  if (jobId) runningChildren.delete(jobId);
}

// Targeted process kill. The old /api/session/control action blanket-pkill'd
// every `agy -p` and `codex exec` on the host, which killed unrelated jobs
// from other devices or terminal-launched runs. Now we prefer the in-process
// child handle; if it's gone (server restart, another job grabbed the pids)
// we fall back to a tightly-anchored pkill, and only as a last resort do a
// global kill that requires the caller to opt in with confirm="all".
function killJobProcess(opts) {
  const jobId = opts && opts.jobId;
  const conversationId = opts && opts.conversationId;
  const engine = opts && opts.engine;
  const confirmAll = opts && opts.confirm === "all";

  const tried = { byHandle: false, scoped: false, blanket: false };

  // Preferred path: the child we spawned is still alive in this process.
  if (jobId) {
    const child = runningChildren.get(jobId);
    if (child && !child.killed) {
      tried.byHandle = true;
      try { child.kill("SIGTERM"); } catch (e) {}
      setTimeout(() => {
        try {
          if (child && !child.killed) child.kill("SIGKILL");
        } catch (e) {}
      }, 2000);
      unregisterChild(jobId);
      return { ok: true, method: "handle", tried };
    }
  }

  // Scoped fallback: only kill processes that look like the engine binary
  // AND carry either the conversation id or the jobId on the command line.
  // Anchored to "<bin> " so we don't catch arbitrary argv substrings.
  if ((jobId || conversationId) && engine) {
    let bin;
    if (engine === "codex") bin = CODEX_BIN;
    else if (engine === "opencode") bin = findOpencodeBin();
    else if (engine === "commandcode") bin = COMMAND_CODE_BIN;
    else if (engine === "antigravity") bin = AGY_BIN;
    if (bin) {
      const binBase = path.basename(bin);
      const tag = conversationId || jobId;
      // The command line always starts with the bin path then the args; we
      // anchor to the bin and require the tag anywhere afterwards.
      const pattern = `${binBase}.*${tag}`;
      try {
        execSync(`pkill -f -- ${JSON.stringify(pattern)} || true`, { stdio: "ignore" });
        tried.scoped = true;
        return { ok: true, method: "scoped", tried };
      } catch (e) {}
    }
  }

  // Last resort, requires explicit opt-in.
  if (confirmAll) {
    try { execSync("pkill -f \"agy -p\" || true", { stdio: "ignore" }); } catch (e) {}
    try { execSync("pkill -f \"codex exec\" || true", { stdio: "ignore" }); } catch (e) {}
    tried.blanket = true;
    return { ok: true, method: "blanket", tried };
  }

  return { ok: false, method: "noop", tried, message: "Nothing matched and confirm:\"all\" was not set." };
}

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
    "Access-Control-Allow-Headers": "Authorization, Content-Type, x-bridge-token, x-codex-token",
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
// COMMAND CODE CLI SESSIONS & TRANSCRIPTS
// -------------------------------------------------------------
// Command Code stores sessions as JSONL transcripts under
// ~/.commandcode/projects/<escaped-cwd>/<sessionId>.jsonl. The first record
// is a {type:"session"} header; the rest are {type:"message"} records whose
// "message" object carries {role, content:[{type:"text",text}, ...]}.
function commandCodeSessionsDir() {
  const home = os.homedir();
  return path.join(home, ".commandcode/projects");
}

function listCommandCodeSessionFiles(limit = 200) {
  const root = commandCodeSessionsDir();
  if (!fs.existsSync(root)) return [];
  const files = [];
  const stack = [root];
  try {
    while (stack.length && files.length < 4000) {
      const dir = stack.pop();
      let entries;
      try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { continue; }
      for (const ent of entries) {
        const full = path.join(dir, ent.name);
        if (ent.isDirectory()) stack.push(full);
        else if (ent.isFile() && ent.name.endsWith(".jsonl") && !ent.name.includes(".checkpoints")) {
          try {
            files.push({ full, name: ent.name, mtime: fs.statSync(full).mtimeMs });
          } catch (e) {}
        }
      }
    }
  } catch (e) {}
  files.sort((a, b) => b.mtime - a.mtime);
  return files.slice(0, limit);
}

function getCommandCodeSessions() {
  const map = new Map();
  for (const file of listCommandCodeSessionFiles()) {
    const id = file.name.replace(/\.jsonl$/, "");
    if (!id || map.has(id)) continue;
    let title = "Command Code " + id.slice(0, 8);
    let timestamp = file.mtime || Date.now();
    try {
      const lines = fs.readFileSync(file.full, "utf8").trim().split("\n").filter(Boolean);
      for (let i = lines.length - 1; i >= 0; i--) {
        try {
          const item = JSON.parse(lines[i]);
          if (item.type === "session") {
            if (item.timestamp) timestamp = new Date(item.timestamp).getTime() || timestamp;
            continue;
          }
          const msg = item.message || {};
          if (msg.role === "user" && Array.isArray(msg.content)) {
            const text = msg.content.map(c => (c && c.type === "text" ? c.text : "")).filter(Boolean).join(" ");
            const t = cleanTitle(text, "").slice(0, 80);
            if (t) { title = t; break; }
          }
        } catch (e) {}
      }
    } catch (e) {}
    map.set(id, {
      conversationId: id,
      title,
      timestamp,
      workspace: WORKDIR,
      engine: "commandcode",
      hostname: os.hostname()
    });
  }
  return Array.from(map.values());
}

function findCommandCodeSessionFile(convId) {
  if (!convId) return null;
  const root = commandCodeSessionsDir();
  if (!fs.existsSync(root)) return null;
  const direct = path.join(root, convId + ".jsonl");
  if (fs.existsSync(direct)) return direct;
  // The id sits under an escaped-cwd subdirectory; scan for a name match.
  const stack = [root];
  try {
    while (stack.length) {
      const dir = stack.pop();
      let entries;
      try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { continue; }
      for (const ent of entries) {
        const full = path.join(dir, ent.name);
        if (ent.isDirectory()) {
          stack.push(full);
        } else if (ent.isFile() && ent.name.endsWith(".jsonl") && !ent.name.includes(".checkpoints")
            && (ent.name.startsWith(convId) || ent.name.includes(convId))) {
          return full;
        }
      }
    }
  } catch (e) {}
  return null;
}

// Per-session transcript cache keyed by (file mtime) so polling every few
// seconds does not re-read and re-parse a multi-hundred-KB JSONL each time.
const commandCodeTranscriptCache = new Map(); // convId -> { mtimeMs, turns }

function getCommandCodeTranscript(convId, limit = 1000) {
  const file = findCommandCodeSessionFile(convId);
  if (!file || !fs.existsSync(file)) return [];

  let mtimeMs = 0;
  try { mtimeMs = fs.statSync(file).mtimeMs; } catch (e) {}
  const cached = commandCodeTranscriptCache.get(convId);
  if (cached && cached.mtimeMs === mtimeMs) {
    return limit >= cached.turns.length ? cached.turns : cached.turns.slice(-limit);
  }

  const lines = fs.readFileSync(file, "utf8").trim().split("\n").filter(Boolean);
  const msgs = [];
  for (const l of lines) {
    try {
      const obj = JSON.parse(l);
      if (obj.type !== "message") continue;
      const msg = obj.message || {};
      const role = msg.role || "";
      const content = msg.content;
      if (!Array.isArray(content)) continue;

      for (const c of content) {
        if (!c || typeof c !== "object") continue;
        const text = c.text || c.thinking || "";
        if (c.type === "thinking") {
          msgs.push({ role: "thinking", toolTitle: "Thinking", title: "Thinking Process", content: text.trim(), time: obj.timestamp });
        } else if (c.type === "text") {
          if (role === "user") {
            msgs.push({ role: "user", content: text.trim(), time: obj.timestamp });
          } else if (role === "assistant") {
            msgs.push({ role: "assistant", content: text.trim(), time: obj.timestamp });
          } else if (role === "tool") {
            msgs.push({ role: "tool", toolTitle: "Tool", title: "Tool", content: text.trim(), time: obj.timestamp });
          }
        } else if (c.type === "tool_use") {
          const name = c.name || "tool";
          let argsStr = "";
          try {
            argsStr = typeof c.input === "object" ? JSON.stringify(c.input, null, 2) : String(c.input || "");
          } catch (e) { argsStr = String(c.input || ""); }
          let friendlyTitle = "Tool: " + name;
          let commandText = "";
          const input = c.input || {};
          if (name === "run_command" || name === "bash" || name === "execute_command") {
            commandText = input.command || input.CommandLine || input.cmd || "";
            if (commandText) friendlyTitle = "$ " + commandText;
          } else if (name === "read_file" || name === "view_file") {
            const fp = input.file_path || input.path || input.AbsolutePath || "";
            friendlyTitle = "Baca " + (fp ? fp.split("/").pop() : "file");
          } else if (name === "write_to_file" || name === "edit_file" || name === "replace_file_content") {
            const fp = input.file_path || input.path || input.TargetFile || "";
            friendlyTitle = "Tulis " + (fp ? fp.split("/").pop() : "file");
          }
          msgs.push({
            role: "tool",
            toolName: name,
            toolTitle: name,
            title: friendlyTitle,
            command: commandText,
            callId: c.id || "",
            content: ("Command: " + name + "\n\nArguments:\n" + argsStr).trim(),
            time: obj.timestamp
          });
        } else if (c.type === "tool_result") {
          const raw = c.content;
          let outText = "";
          if (Array.isArray(raw)) {
            outText = raw.map(x => (x && typeof x.text === "string") ? x.text : "").filter(Boolean).join("\n");
          } else if (typeof raw === "string") {
            outText = raw;
          }
          if (outText.trim()) {
            msgs.push({
              role: "tool",
              toolTitle: "Tool result",
              title: "Output perintah",
              command: outText.trim(),
              content: outText.trim(),
              callId: c.tool_use_id || "",
              time: obj.timestamp
            });
          }
        } else if (typeof text === "string" && text.trim()) {
          if (role === "user") {
            msgs.push({ role: "user", content: text.trim(), time: obj.timestamp });
          } else if (role === "assistant") {
            msgs.push({ role: "assistant", content: text.trim(), time: obj.timestamp });
          } else if (role === "tool") {
            msgs.push({ role: "tool", toolTitle: "Tool", title: "Tool", content: text.trim(), time: obj.timestamp });
          }
        }
      }
    } catch (e) {}
  }
  const allTurns = msgs;
  // Keep only a bounded number of sessions in memory to avoid leaking.
  if (commandCodeTranscriptCache.size > 100) {
    const oldest = commandCodeTranscriptCache.keys().next().value;
    if (oldest) commandCodeTranscriptCache.delete(oldest);
  }
  commandCodeTranscriptCache.set(convId, { mtimeMs, turns: allTurns });
  return allTurns.slice(-limit);
}

// -------------------------------------------------------------
// SESSIONS & TRANSCRIPT RETRIEVAL
// -------------------------------------------------------------
function findLatestAgyConversationId(sinceMs = 0, jobId = null) {
  const home = os.homedir();
  const brainDir = path.join(home, ".gemini/antigravity-cli/brain");
  if (!fs.existsSync(brainDir)) return null;
  // When the bridge tagged the prompt with [BRIDGE_JOB=<jobId>], prefer the
  // brain dir whose transcript contains that marker; this is the only way to
  // disambiguate two concurrent Agy jobs that started within ~2 seconds of
  // each other (the old mtime-only check lost races).
  if (jobId) {
    const marker = `[BRIDGE_JOB=${jobId}]`;
    try {
      const entries = fs.readdirSync(brainDir, { withFileTypes: true });
      for (const ent of entries) {
        if (!ent.isDirectory() || ent.name.length < 8) continue;
        const transcript = path.join(brainDir, ent.name, ".system_generated/logs/transcript.jsonl");
        if (!fs.existsSync(transcript)) continue;
        try {
          const head = fs.readFileSync(transcript, "utf8").slice(0, 64 * 1024);
          if (head.includes(marker)) return ent.name;
        } catch (e) {}
      }
    } catch (e) {}
  }
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

let sessionsCache = null;
const SESSIONS_CACHE_TTL_MS = 3000;
// command-code --list-models is slow (~10s); cache the parsed list so the
// phone's model picker opens instantly on repeat visits.
let commandCodeModelsCache = null;

// When a job newly associates with a session id, the hub should re-render
// so the active session shows the running border, and any cached sessions
// snapshot is invalidated so the next /api/sessions reflects it.
function noteJobConversationId(convId) {
  if (!convId) return;
  sessionsCache = null;
  events.broadcast("sessions.changed", { reason: "job.association", conversationId: convId });
}

function getSessions(engineFilter) {
  // A filesystem-wide scan across every engine's session store is expensive;
  // cache it briefly so polling endpoints (transcript refresh) don't re-walk
  // the whole tree every few seconds. The filter is applied per call below.
  const now = Date.now();
  if (!sessionsCache || (now - sessionsCache.at >= SESSIONS_CACHE_TTL_MS)) {
    sessionsCache = { at: now, data: getSessionsUncached(null) };
  }
  const all = sessionsCache.data.sessions || [];
  if (!engineFilter) return sessionsCache.data;
  const wanted = normalizeEngine(engineFilter);
  return {
    hostname: sessionsCache.data.hostname,
    sessions: all.filter(s => normalizeEngine(s.engine) === wanted)
  };
}

function getSessionsUncached(engineFilter) {
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
  const opencodeSessions = opencodeConfig.getOpencodeSessions(WORKDIR);
  const commandcodeSessions = getCommandCodeSessions();

  let merged = [...codexSessions, ...agySessions, ...opencodeSessions, ...commandcodeSessions].sort((a, b) => {
    return (b.timestamp || 0) - (a.timestamp || 0);
  });

  const customTitles = getCustomSessionTitles();
  const activityMap = getSessionActivityMap();
  const runningJobsList = (jobs && typeof jobs.running === "function") ? jobs.running() : [];
  const runningConvIds = new Set(runningJobsList.map(j => j.conversationId).filter(Boolean));

  for (const s of merged) {
    if (customTitles[s.conversationId]) {
      s.title = customTitles[s.conversationId];
      s.customTitle = true;
    }
    if (activityMap[s.conversationId]) {
      s.timestamp = Math.max(s.timestamp || 0, activityMap[s.conversationId]);
    }
    if (runningConvIds.has(s.conversationId)) {
      s.running = true;
    }
    // Repair the engine tag from disk when it's missing or unknown: a pre-tag
    // Codex rollout should not silently appear in the Antigravity list.
    if (!s.engine || (s.engine !== "antigravity" && s.engine !== "codex"
        && s.engine !== "opencode" && s.engine !== "commandcode")) {
      const probed = probeSessionEngine(s.conversationId);
      if (probed) s.engine = probed;
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
    // When no filter is given keep a wider pool so per-engine filtering on
    // top of the cache does not lose sessions to the global cap.
    sessions: merged.slice(0, engineFilter ? 50 : 200)
  };
}

function normalizeEngine(value, probedEngine) {
  const v = String(value || "").toLowerCase();
  if (v === "codex") return "codex";
  if (v === "opencode") return "opencode";
  if (v === "commandcode" || v === "command-code" || v === "cmd") return "commandcode";
  // Pre-tag sessions without an engine field used to silently become
  // "antigravity"; the caller can now probe the filesystem and pass the
  // discovered engine so a Codex rollout with a missing tag ends up in the
  // Codex list instead of leaking into the Antigravity one.
  if (probedEngine) return probedEngine;
  return "antigravity";
}

// Cheap filesystem probe — returns the engine name whose transcript source
// actually exists on disk for this conversation id, or null when nothing
// matches (the caller should then fall back to its default).
function probeSessionEngine(convId) {
  if (!convId) return null;
  try {
    if (findCodexRolloutFile(convId)) return "codex";
  } catch (e) {}
  try {
    if (opencodeConfig.getOpencodeTranscript(convId, 1).length > 0) return "opencode";
  } catch (e) {}
  try {
    const commandcodeFile = findCommandCodeSessionFile(convId);
    if (commandcodeFile) return "commandcode";
  } catch (e) {}
  return null;
}

function getTranscript(convId, limit = 1000) {
  if (!convId) return [];

  // Check OpenCode transcript
  const opencodeTurns = opencodeConfig.getOpencodeTranscript(convId, limit);
  if (opencodeTurns && opencodeTurns.length > 0) {
    return opencodeTurns;
  }

  // Check Codex next
  const codexTurns = getCodexTranscript(convId, limit);
  if (codexTurns && codexTurns.length > 0) {
    return codexTurns;
  }

  // Check Command Code next
  const commandcodeTurns = getCommandCodeTranscript(convId, limit);
  if (commandcodeTurns && commandcodeTurns.length > 0) {
    return commandcodeTurns;
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

function getLatestAssistantTurn(convId, sinceMs = 0) {
  if (!convId) return null;
  try {
    const turns = getTranscript(convId, 10);
    for (let i = turns.length - 1; i >= 0; i--) {
      const t = turns[i];
      if (t.role === "assistant" && t.content && t.content.trim()) {
        const turnTime = t.time ? new Date(t.time).getTime() : 0;
        if (turnTime >= sinceMs - 15000) {
          return t.content.trim();
        }
      }
    }
  } catch (e) {}
  return null;
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
    registerChild(job && job.id, child);

    let fullOutput = "";
    let error = "";
    let lastCodexError = "";
    let agentMessage = "";
    // Codex ULIDs are monotonically increasing per turn. We only accept the
    // newest agent_message id so a transient mid-run fragment cannot become the
    // final reply if the process then crashes before flushing the next item.
    let lastAgentMessageItemId = "";
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
            if (job && job.id) activeCodexSessionIds.set(job.id, event.thread_id);
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
            const itemId = String(event.item.id || "");
            if (!itemId || itemId > lastAgentMessageItemId) {
              agentMessage = String(event.item.text);
              lastAgentMessageItemId = itemId;
            }
          }

          events.broadcast("cli.event", {
            jobId: job ? job.id : null,
            engine: "codex",
            conversationId: (job && job.id && activeCodexSessionIds.get(job.id)) || activeCodexSessionId,
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
      unregisterChild(job && job.id);
      try { if (fs.existsSync(tmpOutputFile)) fs.unlinkSync(tmpOutputFile); } catch(e) {}
      reject(err);
    });

    child.on("close", code => {
      clearInterval(timer);
      unregisterChild(job && job.id);
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
      const sessionForJob = (job && job.id && activeCodexSessionIds.get(job.id)) || null;
      let returnedSessionId = conversationId || sessionForJob || activeCodexSessionId || null;
      const m = fullOutput.match(/session id:\s*([a-zA-Z0-9_-]+)/i);
      if (m) {
        returnedSessionId = m[1].trim();
      }

      if (assistantMsg) {
        activeCodexSessionId = returnedSessionId;
        if (job && job.id) activeCodexSessionIds.set(job.id, returnedSessionId);
        resolve({ response: assistantMsg, sessionId: returnedSessionId });
      } else if (lastCodexError) {
        // Surface the provider's own words — "402 Payment Required", a missing
        // model, a bad key — instead of a silent empty turn.
        reject(new Error(lastCodexError));
      } else if (code === 0) {
        activeCodexSessionId = returnedSessionId;
        if (job && job.id) activeCodexSessionIds.set(job.id, returnedSessionId);
        resolve({ response: "Done.", sessionId: returnedSessionId });
      } else {
        reject(new Error((error || fullOutput || `Codex exited with code ${code}`).trim()));
      }
    });
  });
}

function runOpencode(prompt, conversationId, model, job) {
  return new Promise((resolve, reject) => {
    const bin = findOpencodeBin();
    const args = ["run"];
    if (conversationId) {
      args.push("-s", conversationId);
    }
    const current = opencodeConfig.readConfig();
    const activeProvider = current.activeProvider || "opencode";
    if (model && model !== "default" && model !== "auto") {
      let formattedModel = model;
      if (!model.includes("/") && activeProvider) {
        formattedModel = `${activeProvider}/${model}`;
      }
      args.push("-m", formattedModel);
    }
    args.push("--format", "json");
    args.push("--dir", WORKDIR);
    args.push(prompt);

    const child = spawn(bin, args, {
      cwd: WORKDIR,
      env: Object.assign({}, process.env, {
        PATH: extendedPath()
      }),
      stdio: ["ignore", "pipe", "pipe"]
    });
    registerChild(job && job.id, child);

    let fullOutput = "";
    let error = "";
    let discoveredSessionId = conversationId || null;

    child.stdout.on("data", chunk => {
      const text = chunk.toString();
      const lines = text.split("\n");
      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) continue;
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
          try {
            const ev = JSON.parse(trimmed);
            if (ev.sessionID && !discoveredSessionId) {
              discoveredSessionId = ev.sessionID;
              if (job && job.id) {
                jobs.update(job.id, { conversationId: discoveredSessionId });
                noteJobConversationId(discoveredSessionId);
              }
            }
            if (ev.type === "text" && ev.part && ev.part.text) {
              fullOutput += ev.part.text;
              events.broadcast("cli.output", { jobId: job.id, engine: "opencode", chunk: ev.part.text });
            } else if (ev.type === "reasoning" && ev.part && ev.part.text) {
              events.broadcast("cli.output", { jobId: job.id, engine: "opencode", chunk: "\n> " + ev.part.text + "\n" });
            } else if (ev.type === "error" && ev.error) {
              const errMsg = (ev.error.data && ev.error.data.message) ? ev.error.data.message : (ev.error.message || JSON.stringify(ev.error));
              fullOutput += `\n> ⚠️ **OpenCode Error**: ${errMsg}\n`;
              events.broadcast("cli.output", { jobId: job.id, engine: "opencode", chunk: `\n> ⚠️ ${errMsg}\n` });
            }
          } catch(e) {
            fullOutput += trimmed + "\n";
            events.broadcast("cli.output", { jobId: job.id, engine: "opencode", chunk: trimmed });
          }
        } else {
          fullOutput += line + "\n";
          events.broadcast("cli.output", { jobId: job.id, engine: "opencode", chunk: line });
        }
      }
    });

    child.stderr.on("data", chunk => {
      const text = chunk.toString();
      error += text;
      events.broadcast("cli.output", { jobId: job.id, engine: "opencode", chunk: text });
    });

    const timeout = setTimeout(() => {
      try { child.kill("SIGKILL"); } catch (e) {}
      reject(new Error("OpenCode execution timed out"));
    }, settings.taskTimeoutMs());

    child.on("close", code => {
      clearTimeout(timeout);
      unregisterChild(job && job.id);
      const resText = fullOutput.trim() || error.trim() || "Done.";
      const sid = discoveredSessionId || conversationId || `opencode_${Date.now()}`;

      // Persist turns into OpenCode session transcript
      try {
        opencodeConfig.recordTurn(sid, "user", prompt, { workspace: WORKDIR });
        opencodeConfig.recordTurn(sid, "assistant", resText, { workspace: WORKDIR, model });
      } catch (e) {}

      if (code === 0 || fullOutput.trim()) {
        resolve({
          response: resText,
          sessionId: sid
        });
      } else {
        reject(new Error(error.trim() || `OpenCode process exited with code ${code}`));
      }
    });

    child.on("error", err => {
      clearTimeout(timeout);
      unregisterChild(job && job.id);
      reject(err);
    });
  });
}

function runCommandCode(prompt, conversationId, model, job) {
  return new Promise((resolve, reject) => {
    const args = ["-p", prompt, "--output-format", "json"];
    // Resume the given session, or continue the last one when the caller
    // asked for it. Command Code uses -r <id> to resume by session id.
    if (conversationId) {
      args.push("-r", conversationId);
    }
    if (model && model !== "default" && model !== "auto") {
      args.push("-m", model);
    }
    // Auto-accept so remote prompts don't stall on permission prompts.
    args.push("--permission-mode", "auto-accept");
    args.push("--skip-onboarding");

    const child = spawn(COMMAND_CODE_BIN, args, {
      cwd: WORKDIR,
      env: Object.assign({}, process.env, {
        PATH: extendedPath()
      }),
      stdio: ["ignore", "pipe", "pipe"]
    });
    registerChild(job && job.id, child);

    let fullOutput = "";
    let error = "";
    let discoveredSessionId = conversationId || null;
    let finalText = "";
    let runError = null;
    let lastActivity = Date.now();

    child.stdout.on("data", chunk => {
      lastActivity = Date.now();
      const text = chunk.toString();
      fullOutput += text;
      for (const line of text.split("\n")) {
        const trimmed = line.trim();
        if (!trimmed) continue;
        try {
          const obj = JSON.parse(trimmed);
          if (obj.type === "event" && obj.event) {
            const ev = obj.event;
            if (ev.type === "run_start" && ev.sessionId && !discoveredSessionId) {
              discoveredSessionId = ev.sessionId;
              if (job && job.id) {
                jobs.update(job.id, { conversationId: discoveredSessionId });
                noteJobConversationId(discoveredSessionId);
              }
            }
            if (ev.type === "text_delta" && typeof ev.delta === "string") {
              finalText += ev.delta;
              events.broadcast("cli.output", { jobId: job.id, engine: "commandcode", chunk: ev.delta });
            } else if (ev.type === "thinking_delta" && typeof ev.delta === "string") {
              events.broadcast("cli.output", { jobId: job.id, engine: "commandcode", chunk: "\n> " + ev.delta });
            } else if (ev.type === "tool_queued" && ev.toolName) {
              const input = ev.input || {};
              let brief = "";
              try {
                if (typeof input === "object") {
                  if (input.command) brief = " " + String(input.command).slice(0, 120);
                  else if (input.file_path) brief = " " + path.basename(String(input.file_path));
                  else if (input.path) brief = " " + path.basename(String(input.path));
                }
              } catch (e) {}
              events.broadcast("cli.output", {
                jobId: job.id, engine: "commandcode",
                chunk: "\n\n> 🔧 **" + ev.toolName + "**" + brief + "\n"
              });
              // A turn about to be appended to the transcript: tell the client
              // to re-fetch so the new tool card appears in real time.
              events.broadcast("cli.event", {
                jobId: job.id, engine: "commandcode",
                conversationId: discoveredSessionId || conversationId || null,
                event: { type: "turn", role: "tool", toolName: ev.toolName, phase: "queued" }
              });
            } else if (ev.type === "tool_completed" && ev.toolName) {
              let resultBrief = "";
              if (Array.isArray(ev.result)) {
                resultBrief = ev.result.map(x => (x && typeof x.text === "string") ? x.text : "").filter(Boolean).join("\n");
              } else if (typeof ev.result === "string") {
                resultBrief = ev.result;
              }
              const clipped = resultBrief.trim().slice(0, 400);
              if (clipped) {
                events.broadcast("cli.output", {
                  jobId: job.id, engine: "commandcode",
                  chunk: "\n> ✅ **" + ev.toolName + "** selesai\n\n```\n" + clipped + "\n```\n"
                });
              } else {
                events.broadcast("cli.output", {
                  jobId: job.id, engine: "commandcode",
                  chunk: "\n> ✅ **" + ev.toolName + "** selesai\n"
                });
              }
              // The result turn is now in the file; let the client pull it
              // without waiting for the 5s safety-net poll.
              events.broadcast("cli.event", {
                jobId: job.id, engine: "commandcode",
                conversationId: discoveredSessionId || conversationId || null,
                event: { type: "turn", role: "tool", toolName: ev.toolName, phase: "completed" }
              });
            } else if (ev.type === "run_end" && ev.result) {
              const r = ev.result;
              if (r.sessionId && !discoveredSessionId) discoveredSessionId = r.sessionId;
              if (r.finalText && typeof r.finalText === "string") {
                finalText = r.finalText;
              }
              if (r.stopReason === "error" || r.stopReason === "abort" || r.stopReason === "max_turns") {
                runError = `Command Code berhenti: ${r.stopReason}`;
              }
            }
          } else if (obj.type === "result" && obj.subtype === "success") {
            if (obj.sessionId && !discoveredSessionId) discoveredSessionId = obj.sessionId;
            if (obj.finalText && typeof obj.finalText === "string") {
              finalText = obj.finalText;
            }
          } else if (obj.type === "result" && obj.subtype !== "success") {
            runError = obj.error || obj.stopReason || "Command Code run gagal";
          }
        } catch (e) {
          // Non-JSON progress line; ignore for the transcript but keep it raw.
          fullOutput += trimmed + "\n";
        }
      }
    });

    child.stderr.on("data", chunk => {
      lastActivity = Date.now();
      const text = chunk.toString();
      error += text;
      events.broadcast("cli.output", { jobId: job.id, engine: "commandcode", chunk: text });
    });

    const timeoutMs = settings.taskTimeoutMs();
    const timer = setInterval(() => {
      if (Date.now() - lastActivity > timeoutMs) {
        clearInterval(timer);
        child.kill("SIGTERM");
        reject(new Error(`Command Code CLI timed out after ${Math.round(timeoutMs / 60000)} minutes of inactivity`));
      }
    }, 10000);

    child.on("error", err => {
      clearInterval(timer);
      unregisterChild(job && job.id);
      reject(err);
    });

    child.on("close", code => {
      clearInterval(timer);
      unregisterChild(job && job.id);
      const sid = discoveredSessionId || conversationId || `commandcode_${Date.now()}`;

      if (runError) {
        reject(new Error(runError));
        return;
      }
      if (code === 0 || finalText.trim()) {
        resolve({
          response: finalText.trim() || "Done.",
          sessionId: sid
        });
        return;
      }
      reject(new Error((error || fullOutput || `Command Code exited with code ${code}`).trim()));
    });
  });
}

function runAgyOnce(prompt, conversationId, resume = false, model, job) {
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
    let lastScanTime = 0;

    const child = spawn(AGY_BIN, args, {
      cwd: WORKDIR,
      env,
      stdio: ["ignore", "pipe", "pipe"]
    });
    registerChild(job && job.id, child);
    let output = "";
    let error = "";
    let lastActivity = Date.now();
    child.stdout.on("data", chunk => {
      lastActivity = Date.now();
      const text = chunk.toString();
      output += text;

      const now = Date.now();
      if (!discoveredConvId && (now - lastScanTime > 2000)) {
        lastScanTime = now;
        discoveredConvId = findLatestAgyConversationId(startTime - 3000, job && job.id);
        if (discoveredConvId && job) {
          jobs.update(job.id, { conversationId: discoveredConvId });
          noteJobConversationId(discoveredConvId);
        }
      }

      events.broadcast("cli.output", {
        jobId: job ? job.id : null,
        engine: "antigravity",
        conversationId: discoveredConvId || conversationId,
        chunk: stripAnsi(text)
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
      unregisterChild(job && job.id);
      reject(err);
    });
    child.on("close", code => {
      clearInterval(timer);
      unregisterChild(job && job.id);
      if (!discoveredConvId) {
        discoveredConvId = findLatestAgyConversationId(startTime - 5000, job && job.id);
      }
      if (discoveredConvId && job) {
        jobs.update(job.id, { conversationId: discoveredConvId });
        noteJobConversationId(discoveredConvId);
      }
      if (code === 0 || output.trim().length > 0) {
        resolve({
          response: stripAnsi(output).trim() || "Done.",
          sessionId: discoveredConvId
        });
        return;
      }

      // If CLI exited with error/stall, check if the response was actually recorded in transcript
      const recoveredResponse = getLatestAssistantTurn(discoveredConvId || conversationId, startTime);
      if (recoveredResponse) {
        resolve({
          response: recoveredResponse,
          sessionId: discoveredConvId
        });
        return;
      }

      reject(new Error((error || output || `Antigravity CLI exited with code ${code}`).trim()));
    });
  });
}

async function runAgy(prompt, conversationId, resume = false, model, job) {
  const maxRetries = 2;
  let lastError = null;
  let currentConvId = conversationId;

  for (let attempt = 1; attempt <= maxRetries + 1; attempt++) {
    try {
      const result = await runAgyOnce(prompt, currentConvId, resume, model, job);
      return result;
    } catch (err) {
      lastError = err;
      const errMsg = (err && err.message) || "";
      const isTransient = /subscriber fell behind|interrupted before the response finished|transport is closing|stalled for/i.test(errMsg);

      if (isTransient && attempt <= maxRetries) {
        if (job) {
          events.broadcast("cli.output", {
            jobId: job.id,
            engine: "antigravity",
            conversationId: currentConvId,
            chunk: `\n[Sistem] Sambungan stream agen terhenti sesaat (${attempt}/${maxRetries}), mencoba menyambung ulang otomatis...\n`
          });
        }
        await new Promise(r => setTimeout(r, 1500 * attempt));
        continue;
      }
      throw err;
    }
  }
  throw lastError;
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
  const commandcodeFile = findCommandCodeSessionFile(convId);
  if (commandcodeFile) sources.push(commandcodeFile);
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

// -------------------------------------------------------------
// Tiny GFM→HTML converter for the standalone transcript export.
// -------------------------------------------------------------
// We avoid pulling marked/markdown-it just for the export: the bridge
// already has zero runtime deps and this is a well-defined subset. What
// the converter handles:
//   - fenced code blocks   (```lang ... ```)
//   - ATX headings         (# ... ######)
//   - GFM tables           (| col | col |\n| --- | --- |\n| ... |)
//   - blockquotes          (> ...)
//   - bullet / ordered lists
//   - horizontal rules     (---)
//   - paragraphs (anything else)
//   - inline: `code`, **bold**, *italic*, [link](url), bare URLs
// The output is HTML-safe (every text run is escaped first), and tables
// include per-column widths computed from the longest cell so the columns
// line up the way they do in the Android/iOS renderers.

function mdEscape(s) {
  return String(s == null ? "" : s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function mdInline(text) {
  // Run inline substitutions in an order that respects nesting. The
  // escape happens once on the input, then each rule only touches markup
  // we just emitted.
  let out = mdEscape(text);
  // Inline code first so its contents are not re-formatted.
  out = out.replace(/`([^`\n]+)`/g, (_, body) => `<code>${body}</code>`);
  // Links: [label](url). The URL is already escaped; only the label needs
  // its inner markdown (bold/italic) to keep working.
  out = out.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g,
    (_, label, url) => `<a href="${url}">${label}</a>`);
  // Bare URLs.
  out = out.replace(/(^|[^"'=])(https?:\/\/[^\s<]+)/g,
    (_, lead, url) => `${lead}<a href="${url}">${url}</a>`);
  // Bold (**...**) before italic so it doesn't swallow the asterisks.
  out = out.replace(/\*\*([^*\n]+)\*\*/g, "<strong>$1</strong>");
  out = out.replace(/__([^_\n]+)__/g, "<strong>$1</strong>");
  // Italic (*...* / _..._).
  out = out.replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, "$1<em>$2</em>");
  out = out.replace(/(^|[^_])_([^_\n]+)_(?!_)/g, "$1<em>$2</em>");
  return out;
}

function mdIsTableSeparator(line) {
  const stripped = line.replace(/[|:\-\s]/g, "");
  return !stripped.length && line.indexOf("-") >= 0;
}

function mdSplitRow(line) {
  let t = line.replace(/^\s*\|?/, "").replace(/\|\s*$/, "");
  return t.split("|").map(c => c.trim());
}

function mdAlignFor(spec) {
  const left = spec.startsWith(":");
  const right = spec.endsWith(":");
  if (left && right) return "center";
  if (right) return "right";
  return "left";
}

function mdRenderTable(lines) {
  const headers = mdSplitRow(lines[0]);
  const spec = mdSplitRow(lines[1]);
  const aligns = headers.map((_, i) => mdAlignFor(spec[i] || "---"));
  const rows = [];
  for (let i = 2; i < lines.length; i++) {
    if (mdIsTableSeparator(lines[i])) continue;
    const cells = mdSplitRow(lines[i]);
    while (cells.length < headers.length) cells.push("");
    rows.push(cells.slice(0, headers.length));
  }

  // Per-column width: average character length × 7.5 px (rough monospace
  // approximation that lines up well in the export's body font) with a
  // sensible floor/cap. The point is to keep columns visually aligned, not
  // to match the on-device renderer to the pixel.
  const charW = 7.5;
  const widths = headers.map((_, c) => {
    let max = headers[c].length;
    for (const r of rows) max = Math.max(max, (r[c] || "").length);
    return Math.max(60, Math.min(360, Math.round(max * charW)));
  });

  const cell = (content, align, width, isHeader) => {
    const tag = isHeader ? "th" : "td";
    const style = `style="text-align:${align};width:${width}px"`;
    return `<${tag} ${style}>${mdInline(content)}</${tag}>`;
  };

  const headerRow = `<tr>${headers.map((h, i) => cell(h, aligns[i], widths[i], true)).join("")}</tr>`;
  const bodyRows = rows.map(r =>
    `<tr>${r.map((c, i) => cell(c, aligns[i], widths[i], false)).join("")}</tr>`).join("");
  const colgroup = `<colgroup>${widths.map(w => `<col style="width:${w}px">`).join("")}</colgroup>`;
  return `<div class="table-wrap"><table>${colgroup}<thead>${headerRow}</thead><tbody>${bodyRows}</tbody></table></div>`;
}

function mdRenderList(lines, ordered) {
  const tag = ordered ? "ol" : "ul";
  const out = [`<${tag}>`];
  for (const line of lines) {
    const text = line.replace(/^\s*(?:\d+\.|[*+-])\s+/, "").trim();
    out.push(`<li>${mdInline(text || "")}</li>`);
  }
  out.push(`</${tag}>`);
  return out.join("\n");
}

function renderMarkdown(src) {
  // Split out fenced code first so its contents are not parsed as markdown.
  const codeBlocks = [];
  let working = src.replace(/```([a-zA-Z0-9_-]*)\n([\s\S]*?)```/g, (_, lang, body) => {
    const idx = codeBlocks.length;
    codeBlocks.push(`<pre><code class="lang-${mdEscape(lang)}">${mdEscape(body)}</code></pre>`);
    return `\u0000CODE${idx}\u0000`;
  });

  const lines = working.split(/\r?\n/);
  const out = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    const trimmed = line.trim();

    if (!trimmed) { i++; continue; }

    // Horizontal rule
    if (/^(-{3,}|\*{3,}|_{3,})\s*$/.test(trimmed)) {
      out.push("<hr>");
      i++; continue;
    }

    // Heading
    const headingMatch = /^(#{1,6})\s+(.*)$/.exec(trimmed);
    if (headingMatch) {
      const level = headingMatch[1].length;
      out.push(`<h${level}>${mdInline(headingMatch[2])}</h${level}>`);
      i++; continue;
    }

    // Fenced code placeholder
    const codePlaceholder = /^\u0000CODE(\d+)\u0000$/.exec(line);
    if (codePlaceholder) {
      out.push(codeBlocks[Number(codePlaceholder[1])]);
      i++; continue;
    }

    // Table: a pipe-delimited line followed by a separator row.
    if (trimmed.indexOf("|") >= 0 && i + 1 < lines.length && mdIsTableSeparator(lines[i + 1].trim())) {
      const tableLines = [trimmed, lines[i + 1].trim()];
      i += 2;
      while (i < lines.length && lines[i].trim().indexOf("|") >= 0) {
        tableLines.push(lines[i].trim());
        i++;
      }
      out.push(mdRenderTable(tableLines));
      continue;
    }

    // Blockquote (consecutive lines starting with ">")
    if (trimmed.startsWith(">")) {
      const quoteLines = [];
      while (i < lines.length && lines[i].trim().startsWith(">")) {
        quoteLines.push(lines[i].trim().replace(/^>\s?/, ""));
        i++;
      }
      out.push(`<blockquote>${quoteLines.map(l => mdInline(l)).join("<br>")}</blockquote>`);
      continue;
    }

    // Bullet / ordered list (consecutive matching lines).
    if (/^[*+-]\s+/.test(trimmed) || /^\d+\.\s+/.test(trimmed)) {
      const ordered = /^\d+\.\s+/.test(trimmed);
      const listLines = [];
      const re = ordered ? /^\s*\d+\.\s+/ : /^\s*[*+-]\s+/;
      while (i < lines.length && re.test(lines[i])) {
        listLines.push(lines[i]);
        i++;
      }
      out.push(mdRenderList(listLines, ordered));
      continue;
    }

    // Paragraph: collect consecutive non-empty lines that did not match
    // any of the block-level rules above.
    const paraLines = [line];
    i++;
    while (i < lines.length && lines[i].trim() && !/^(#{1,6}\s|>|\s*[*+-]\s|\s*\d+\.\s|-{3,}|\*{3,}|_{3,})/.test(lines[i].trim())
           && lines[i].trim().indexOf("|") < 0) {
      paraLines.push(lines[i]);
      i++;
    }
    out.push(`<p>${paraLines.map(mdInline).join("<br>")}</p>`);
  }

  return out.join("\n");
}

function generateStandaloneHtmlTranscript(session, turns) {
  const escapeHtml = str => String(str || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  const title = escapeHtml(session.title || "Sesi Antigravity");
  const engine = escapeHtml(session.engine || "antigravity");
  const dateStr = session.timestamp ? new Date(session.timestamp).toLocaleString("id-ID") : new Date().toLocaleString("id-ID");

  // Decide whether a turn's content is plain text or markdown. A turn is
  // treated as markdown if it contains any of the structural markers that
  // only markdown carries (fences, headings, list bullets, links, tables,
  // blockquote markers). Plain prose still renders correctly because
  // renderMarkdown falls back to <p>...</p> for paragraphs.
  const looksLikeMarkdown = s =>
    /(^|\n)(```|#{1,6}\s|>\s|\*\s|-\s|\d+\.\s|\[[^\]]+\]\([^)]+\)|\|[^\n]+\|)/m.test(s);

  let turnsHtml = "";
  for (const turn of turns || []) {
    const rawContent = typeof turn.content === "string" ? turn.content.trim() : "";
    if (!rawContent) continue;
    const role = turn.role || "assistant";
    const rendered = (role === "assistant" && looksLikeMarkdown(rawContent))
      ? renderMarkdown(rawContent)
      : `<pre><code>${escapeHtml(rawContent)}</code></pre>`;
    if (role === "user") {
      turnsHtml += `
      <div class="turn turn-user">
        <div class="turn-header"><span class="avatar">👤</span> <strong>User</strong></div>
        <div class="turn-body">${escapeHtml(rawContent).replace(/\n/g, "<br>")}</div>
      </div>`;
    } else if (role === "assistant") {
      turnsHtml += `
      <div class="turn turn-assistant">
        <div class="turn-header"><span class="avatar">🤖</span> <strong>Antigravity / Codex AI</strong></div>
        <div class="turn-body markdown">${rendered}</div>
      </div>`;
    } else if (role === "thinking") {
      turnsHtml += `
      <details class="turn turn-thinking">
        <summary>💭 <strong>Thinking Process</strong></summary>
        <div class="turn-body"><pre><code>${escapeHtml(rawContent)}</code></pre></div>
      </details>`;
    } else if (role === "tool") {
      const toolTitle = escapeHtml(turn.title || turn.toolTitle || "Tool Command");
      turnsHtml += `
      <details class="turn turn-tool">
        <summary>🔧 <strong>${toolTitle}</strong></summary>
        <div class="turn-body"><pre><code>${escapeHtml(rawContent)}</code></pre></div>
      </details>`;
    }
  }

  return `<!DOCTYPE html>
<html lang="id">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title} - Antigravity Remote</title>
  <style>
    :root {
      --bg: #141416;
      --surface: #1e1e22;
      --border: #2e2e36;
      --text: #f0f0f5;
      --text-muted: #9e9ea8;
      --accent: #d96b43;
      --user-bg: #26262e;
    }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      background: var(--bg);
      color: var(--text);
      margin: 0;
      padding: 24px 16px;
      line-height: 1.6;
    }
    .container {
      max-width: 840px;
      margin: 0 auto;
    }
    .header {
      border-bottom: 1px solid var(--border);
      padding-bottom: 16px;
      margin-bottom: 24px;
    }
    .header h1 {
      margin: 0 0 8px;
      font-size: 1.5rem;
      color: var(--text);
    }
    .meta {
      font-size: 0.85rem;
      color: var(--text-muted);
    }
    .turn {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 16px;
      margin-bottom: 16px;
    }
    .turn-user {
      background: var(--user-bg);
      border-color: #3e3e4a;
    }
    .turn-header {
      font-size: 0.9rem;
      color: var(--text-muted);
      margin-bottom: 8px;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    pre {
      background: #0e0e11;
      padding: 12px;
      border-radius: 8px;
      overflow-x: auto;
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      font-size: 0.85rem;
    }
    code {
      font-family: inherit;
    }
    .markdown > * { margin: 8px 0; }
    .markdown > *:first-child { margin-top: 0; }
    .markdown > *:last-child { margin-bottom: 0; }
    .markdown h1, .markdown h2, .markdown h3,
    .markdown h4, .markdown h5, .markdown h6 {
      margin: 14px 0 8px;
      line-height: 1.3;
    }
    .markdown h1 { font-size: 1.4rem; }
    .markdown h2 { font-size: 1.2rem; }
    .markdown h3 { font-size: 1.05rem; }
    .markdown p { margin: 8px 0; }
    .markdown blockquote {
      margin: 8px 0;
      padding: 6px 12px;
      border-left: 3px solid var(--accent);
      background: rgba(217, 107, 67, 0.08);
      color: var(--text-muted);
    }
    .markdown ul, .markdown ol { margin: 8px 0 8px 24px; padding: 0; }
    .markdown li { margin: 4px 0; }
    .markdown a { color: var(--accent); text-decoration: underline; }
    .markdown .table-wrap {
      margin: 10px 0 12px;
      overflow-x: auto;
      border: 1px solid var(--border);
      border-radius: 8px;
      background: var(--surface);
    }
    .markdown table {
      width: max-content;
      border-collapse: collapse;
      table-layout: fixed;
      font-size: 0.88rem;
    }
    .markdown thead th {
      background: rgba(255, 255, 255, 0.04);
      color: var(--text-muted);
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      font-size: 0.72rem;
      padding: 10px 14px;
      border-bottom: 1px solid var(--border-strong);
      vertical-align: middle;
      white-space: nowrap;
    }
    .markdown tbody td {
      padding: 10px 14px;
      border-bottom: 1px solid var(--border);
      color: var(--text);
      vertical-align: top;
      word-wrap: break-word;
      overflow-wrap: anywhere;
    }
    .markdown tbody tr:nth-child(even) td {
      background: rgba(255, 255, 255, 0.02);
    }
    .markdown tbody tr:last-child td { border-bottom: none; }
    .markdown code {
      background: rgba(255, 255, 255, 0.06);
      padding: 1px 5px;
      border-radius: 4px;
      font-size: 0.85em;
    }
    .markdown pre code { background: transparent; padding: 0; border-radius: 0; }
    .markdown hr {
      border: none;
      border-top: 1px solid var(--border);
      margin: 12px 0;
    }
    summary {
      cursor: pointer;
      color: var(--accent);
      padding: 4px 0;
      font-size: 0.9rem;
    }
    .footer {
      text-align: center;
      margin-top: 32px;
      font-size: 0.8rem;
      color: var(--text-muted);
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>${title}</h1>
      <div class="meta">Engine: <strong>${engine}</strong> • Waktu: ${dateStr} • Sesi: <code>${session.conversationId || ""}</code></div>
    </div>
    <div class="turns">
      ${turnsHtml}
    </div>
    <div class="footer">
      Generated by Antigravity & Codex Remote App
    </div>
  </div>
</body>
</html>`;
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

  // A new job is on the wire — invalidate the sessions cache so the next
  // /api/sessions call reflects the running flag, and notify any hub client
  // to re-render the session list (so the active session gets the live border).
  sessionsCache = null;
  events.broadcast("sessions.changed", { reason: "task.started", conversationId });

  try {
    let responseText;
    let activeConvId = conversationId;
    let activeSession = null;
    let updatedTurns = [];

    let effectiveEngine = engine;
    let effectiveModel = model;
    let fallbackNotice = "";

    if (effectiveEngine === "codex" && !commandVersion(CODEX_BIN, ["--version"])) {
      const agyOk = commandVersion(AGY_BIN, ["--version"]);
      if (agyOk) {
        effectiveEngine = "antigravity";
        effectiveModel = "auto";
        fallbackNotice = "> ⚠️ **Catatan Sistem**: Codex CLI belum terpasang di host server. Tugas dialihkan dan diselesaikan otomatis menggunakan Antigravity engine.\n\n";
        // Surface the swap on the phone so the user knows the engine changed
        // before the first streamed text arrives.
        try {
          events.broadcast("cli.output", {
            jobId: job && job.id,
            engine: "antigravity",
            conversationId: conversationId || null,
            chunk: fallbackNotice
          });
        } catch (e) {}
      }
    } else if (effectiveEngine === "opencode") {
      const opencodeBin = findOpencodeBin();
      const opencodeOk = commandVersion(opencodeBin, ["--version"]) || commandVersion(opencodeBin, ["-v"]);
      if (!opencodeOk) {
        throw new Error("OpenCode CLI Tidak Ditemukan di Host Server. Biner `opencode` belum terdeteksi. Silakan pastikan OpenCode terpasang (curl -fsSL https://opencode.ai/install.sh | bash atau npm i -g opencode) dan restart bridge server.");
      }
    } else if (effectiveEngine === "commandcode") {
      const commandcodeOk = commandVersion(COMMAND_CODE_BIN, ["--version"]);
      if (!commandcodeOk) {
        throw new Error("Command Code CLI Tidak Ditemukan di Host Server. Biner `command-code` belum terdeteksi. Silakan pastikan Command Code terpasang (npm install -g command-code) dan restart bridge server.");
      }
    }

    if (effectiveEngine === "codex") {
      const result = await runCodex(prompt, conversationId, effectiveModel, job);
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
    } else if (effectiveEngine === "opencode") {
      const result = await runOpencode(prompt, conversationId, effectiveModel, job);
      responseText = result.response;
      activeConvId = result.sessionId || conversationId;

      const sData = getSessions();
      activeSession = sData.sessions.find(s => s.conversationId === activeConvId) || {
        conversationId: activeConvId,
        title: prompt.slice(0, 40),
        workspace: WORKDIR,
        engine: "opencode"
      };
      updatedTurns = activeConvId ? getTranscript(activeConvId, 1000) : [];
      if (updatedTurns.length === 0) {
        updatedTurns = [
          { role: "user", content: prompt, time: new Date().toISOString() },
          { role: "assistant", content: responseText, time: new Date().toISOString() }
        ];
      }
    } else if (effectiveEngine === "commandcode") {
      const result = await runCommandCode(prompt, conversationId, effectiveModel, job);
      responseText = result.response;
      activeConvId = result.sessionId || conversationId;

      const sData = getSessions();
      activeSession = sData.sessions.find(s => s.conversationId === activeConvId) || {
        conversationId: activeConvId,
        title: prompt.slice(0, 40),
        workspace: WORKDIR,
        engine: "commandcode"
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
      // Prefix the prompt with a job tag so the on-disk transcript marks this
      // run unambiguously; findLatestAgyConversationId reads it back to scope
      // the brain dir to this jobId and dodge concurrent-run races.
      const agyPrompt = `[BRIDGE_JOB=${job.id}]\n` + prompt;
      const result = await runAgy(agyPrompt, conversationId, resume, effectiveModel, job);
      responseText = typeof result === "object" ? result.response : result;
      activeConvId = (typeof result === "object" && result.sessionId) ? result.sessionId : (isNewSession ? findLatestAgyConversationId(startTime - 10000, job.id) : conversationId);

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

    if (fallbackNotice && responseText) {
      responseText = fallbackNotice + responseText;
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

    // Give the CLI a beat to flush its transcript/rollout to disk before the
    // client polls /api/session/transcript on top of task.finished. Without
    // this, the first poll after task.finished returns an empty list and the
    // rendered chat bubbles disappear until the next throttled sync.
    await new Promise(resolve => setTimeout(resolve, 250));

    events.broadcast("task.finished", {
      ok: true,
      jobId: job.id,
      engine,
      conversationId: activeConvId,
      title: activeSession && activeSession.title ? activeSession.title : "Sesi",
      summary: (responseText || "").slice(0, 200),
      finishedAt: new Date().toISOString()
    });
    sessionsCache = null;
    events.broadcast("sessions.changed", { reason: "task.finished", conversationId: activeConvId });

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
    sessionsCache = null;
    events.broadcast("sessions.changed", { reason: "task.failed", conversationId: job.conversationId || conversationId });
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
      "Access-Control-Allow-Headers": "Authorization, Content-Type, x-bridge-token, x-codex-token",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS"
    });
    return res.end();
  }

  if (req.method === "GET" && (pathname === "/health" || pathname === "/api/health")) {
    return send(res, 200, {
      ok: true,
      hostname: os.hostname(),
      engines: ["antigravity", "codex", "opencode", "commandcode"],
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

  // GET /api/engines
  if (req.method === "GET" && pathname === "/api/engines") {
    return send(res, 200, getEnginesStatus());
  }

  // POST /api/engine/install
  if (req.method === "POST" && pathname === "/api/engine/install") {
    if (!authorized(req)) return send(res, 401, { error: "Unauthorized" });
    // Read-only mode must block installing new engines: it shells out to
    // `npm install -g` which mutates the host. The general isWriteBlocked
    // gate sits below this handler, so we duplicate the check here.
    if (settings.load().sandboxMode === "readonly") {
      auditLog.log("write.blocked", { path: pathname, sandboxMode: "readonly", device: deviceId(req) });
      return send(res, 403, { error: WRITE_BLOCKED_MESSAGE });
    }
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 64 * 1024) req.destroy();
    });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        const engine = payload.engine || "codex";
        let cmd = "";
        if (engine === "codex") {
          cmd = "npm install -g @openai/codex || curl -fsSL https://raw.githubusercontent.com/openai/codex/main/install.sh | bash 2>&1";
        } else if (engine === "opencode") {
          cmd = "npm install -g opencode || npm install -g @opencode-ai/cli 2>&1";
        } else if (engine === "antigravity") {
          cmd = "npm install -g @google/antigravity-cli || echo 'Antigravity CLI installed' 2>&1";
        } else if (engine === "commandcode") {
          cmd = "npm install -g command-code 2>&1";
        } else {
          return send(res, 400, { error: "Unknown engine: " + engine });
        }

        const installJobId = "install_" + Date.now();
        events.broadcast("engine.install_start", { jobId: installJobId, engine });

        const child = spawn("bash", ["-c", cmd], {
          cwd: WORKDIR,
          env: Object.assign({}, process.env, {
            PATH: (process.env.PATH || "") + ":/usr/bin:/usr/local/bin:/home/ubuntu/.local/bin:~/.nvm/versions/node/$(node -v 2>/dev/null)/bin"
          })
        });

        let output = "";
        child.stdout.on("data", d => {
          const chunk = d.toString();
          output += chunk;
          events.broadcast("engine.install_log", { jobId: installJobId, engine, log: chunk });
        });
        child.stderr.on("data", d => {
          const chunk = d.toString();
          output += chunk;
          events.broadcast("engine.install_log", { jobId: installJobId, engine, log: chunk });
        });

        child.on("close", (code) => {
          const updated = getEnginesStatus();
          const isInstalled = engine === "codex" ? updated.engines.codex.available
            : engine === "opencode" ? updated.engines.opencode.available
            : engine === "commandcode" ? updated.engines.commandcode.available
            : updated.engines.antigravity.available;
          events.broadcast("engine.installed", {
            jobId: installJobId,
            engine,
            ok: code === 0 || isInstalled,
            version: engine === "codex" ? updated.engines.codex.version
              : engine === "opencode" ? updated.engines.opencode.version
              : engine === "commandcode" ? updated.engines.commandcode.version
              : updated.engines.antigravity.version,
            output: output
          });
          audit("engine.install", { engine, ok: isInstalled, code });
        });

        return send(res, 200, { ok: true, jobId: installJobId, message: "Instalasi engine dimulai di server" });
      } catch (err) {
        send(res, 500, { error: err.message });
      }
    });
    return;
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

  // POST /api/files/patch
  if (req.method === "POST" && pathname === "/api/files/patch") {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 4 * 1024 * 1024) req.destroy();
    });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        const patchContent = (payload.patch || payload.diff || "").trim();
        if (!patchContent) return send(res, 400, { error: "Diff / patch content tidak boleh kosong" });

        const tmpFile = path.join(os.tmpdir(), "patch-" + Date.now() + ".diff");
        fs.writeFileSync(tmpFile, patchContent + "\n", "utf8");
        const { spawnSync } = require("child_process");
        let ok = false, msg = "";
        try {
          const resGit = spawnSync("git", ["apply", "--whitespace=nowarn", tmpFile], {
            cwd: WORKDIR,
            timeout: 10000,
            encoding: "utf8"
          });
          if (resGit.status === 0) {
            ok = true;
            msg = "Perubahan diff berhasil diterapkan ke workspace.";
          } else {
            const resPatch1 = spawnSync("patch", ["-p1", "-f", "-i", tmpFile], {
              cwd: WORKDIR,
              timeout: 10000,
              encoding: "utf8"
            });
            if (resPatch1.status === 0) {
              ok = true;
              msg = "Perubahan patch (p1) berhasil diterapkan.";
            } else {
              const resPatch0 = spawnSync("patch", ["-p0", "-f", "-i", tmpFile], {
                cwd: WORKDIR,
                timeout: 10000,
                encoding: "utf8"
              });
              if (resPatch0.status === 0) {
                ok = true;
                msg = "Perubahan patch (p0) berhasil diterapkan.";
              } else {
                ok = false;
                msg = (resGit.stderr || resPatch1.stderr || resPatch0.stderr || "Gagal menerapkan diff").toString().trim();
              }
            }
          }
          if (ok) {
            audit("file.patch", { ok: true });
            events.broadcast("git.changed", { path: WORKDIR });
          }
        } catch (e) {
          ok = false;
          msg = e.message || "Gagal menerapkan diff patch.";
        } finally {
          try { fs.unlinkSync(tmpFile); } catch (e) {}
        }
        return send(res, ok ? 200 : 400, { ok, message: msg });
      } catch (err) {
        send(res, 500, { error: err.message });
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

  // GET /api/session/html?id=... -> Standalone HTML webpage
  if (req.method === "GET" && pathname === "/api/session/html") {
    const convId = parsedUrl.query.id;
    if (!convId) return send(res, 400, { error: "Missing session id parameter" });
    const sData = getSessions();
    const session = (sData.sessions || []).find(x => x.conversationId === convId)
      || { conversationId: convId, title: "Sesi", engine: "unknown" };
    const turns = getTranscript(convId, 2000);
    const html = generateStandaloneHtmlTranscript(session, turns);
    return send(res, 200, {
      ok: true,
      conversationId: convId,
      title: session.title,
      filename: exportFilename(session).replace(/\.md$/, ".html"),
      html: html
    });
  }

  // POST /api/session/gist
  if (req.method === "POST" && pathname === "/api/session/gist") {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 4 * 1024 * 1024) req.destroy();
    });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        const convId = payload.id || payload.conversationId;
        if (!convId) return send(res, 400, { error: "Missing session id" });
        const sData = getSessions();
        const session = (sData.sessions || []).find(x => x.conversationId === convId)
          || { conversationId: convId, title: "Sesi Antigravity", engine: "antigravity" };
        const md = transcriptToMarkdown(session, getTranscript(convId, 2000));
        const token = payload.token || process.env.GITHUB_TOKEN || process.env.GH_TOKEN || "";
        const filename = (payload.filename || exportFilename(session)).replace(/[^a-zA-Z0-9._-]/g, "-");

        const body = JSON.stringify({
          description: "Antigravity & Codex Remote Transcript: " + (session.title || "Session"),
          public: Boolean(payload.isPublic),
          files: {
            [filename]: { content: md }
          }
        });

        const https = require("https");
        const headers = {
          "User-Agent": "Antigravity-Remote-App",
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(body)
        };
        if (token) headers["Authorization"] = `token ${token}`;

        const reqGist = https.request("https://api.github.com/gists", { method: "POST", headers }, (resGist) => {
          let gRaw = "";
          resGist.on("data", c => { gRaw += c; });
          resGist.on("end", () => {
            try {
              const gJson = JSON.parse(gRaw || "{}");
              if (resGist.statusCode >= 200 && resGist.statusCode < 300 && gJson.html_url) {
                audit("session.gist_exported", { conversationId: convId, url: gJson.html_url });
                return send(res, 200, { ok: true, url: gJson.html_url, id: gJson.id });
              }
              return send(res, 400, { error: gJson.message || ("GitHub API error: " + resGist.statusCode) });
            } catch (e) {
              return send(res, 500, { error: "Failed to parse GitHub response" });
            }
          });
        });
        reqGist.on("error", (e) => send(res, 500, { error: e.message }));
        reqGist.write(body);
        reqGist.end();
      } catch (err) {
        send(res, 500, { error: err.message });
      }
    });
    return;
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

  // GET /api/opencode/models — query active or requested OpenCode provider's catalogue
  if (req.method === "GET" && pathname === "/api/opencode/models") {
    const current = opencodeConfig.readConfig();
    const id = parsedUrl.query.provider || current.activeProvider;
    const fallbackModels = opencodeConfig.getModelsForProvider(id);
    const secret = opencodeConfig.providerSecret(id);

    if (!secret || !secret.baseUrl) {
      return send(res, 200, {
        ok: true,
        provider: id,
        activeModel: current.activeModel,
        models: fallbackModels
      });
    }

    providerModels.list(secret, parsedUrl.query.refresh === "1").then(result => {
      let list = (result && result.models && result.models.length) ? result.models : fallbackModels;
      send(res, 200, {
        ok: true,
        provider: id,
        activeModel: current.activeModel,
        models: list
      });
    }).catch(err => {
      send(res, 200, {
        ok: true,
        provider: id,
        activeModel: current.activeModel,
        models: fallbackModels,
        note: err.message
      });
    });
    return;
  }

  // GET /api/opencode/config
  if (req.method === "GET" && pathname === "/api/opencode/config") {
    return send(res, 200, opencodeConfig.read());
  }

  // GET /api/commandcode/models — models known to the Command Code CLI itself.
  // The CLI owns its provider catalogue, so ask it instead of hardcoding a list
  // that drifts out of date the moment a provider is added.
  //
  // `command-code --version` takes ~5s and `--list-models` ~10s, so the list is
  // cached (10 min TTL) and the version check is skipped in favour of a cheap
  // filesystem lookup — the phone should not wait 15s every time the picker
  // opens.
  if (req.method === "GET" && pathname === "/api/commandcode/models") {
    const now = Date.now();
    if (commandCodeModelsCache && (now - commandCodeModelsCache.at < 10 * 60 * 1000)) {
      return send(res, 200, commandCodeModelsCache.payload);
    }

    const binOk = fs.existsSync(COMMAND_CODE_BIN)
      || spawnSync("which", [COMMAND_CODE_BIN], { encoding: "utf8" }).status === 0;
    if (!binOk) {
      return send(res, 200, { ok: false, error: "Command Code CLI tidak terdeteksi", models: [] });
    }
    const child = spawn(COMMAND_CODE_BIN, ["--list-models"], {
      cwd: WORKDIR,
      env: Object.assign({}, process.env, { PATH: extendedPath() }),
      stdio: ["ignore", "pipe", "pipe"]
    });
    let out = "";
    let errOut = "";
    child.stdout.on("data", d => { out += d.toString(); });
    child.stderr.on("data", d => { errOut += d.toString(); });
    child.on("close", code => {
      // Lines look like: "deepseek/deepseek-v4-flash   fast hybrid-attention reasoning (default)"
      // and sections like "Open Source" / "Proprietary". Grab the model id from
      // each line that starts with a provider/model token.
      const models = [];
      for (const line of out.split("\n")) {
        const trimmed = line.trim();
        const m = trimmed.match(/^([a-zA-Z0-9_.\-\/]+)\s+(.+)$/);
        if (m && m[1].includes("/")) {
          models.push({ id: m[1], name: m[1], description: m[2].trim() });
        }
      }
      const payload = {
        ok: true,
        provider: "commandcode",
        activeModel: null,
        models,
        note: code === 0 ? null : (errOut.trim() || `command-code --list-models exited with code ${code}`)
      };
      commandCodeModelsCache = { at: Date.now(), payload };
      send(res, 200, payload);
    });
    child.on("error", err => {
      send(res, 200, { ok: false, error: err.message, models: [] });
    });
    return;
  }

  // POST /api/opencode/provider        upsert a model provider
  // POST /api/opencode/provider/delete remove one
  // POST /api/opencode/active          switch the active provider/model
  if (req.method === "POST" && pathname.startsWith("/api/opencode/")) {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 64 * 1024) req.destroy();
    });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        let result;
        if (pathname === "/api/opencode/provider") {
          result = opencodeConfig.upsertProvider(payload);
          audit("opencode.provider.saved", { id: payload.id, baseUrl: payload.baseUrl, ok: result.ok });
        } else if (pathname === "/api/opencode/provider/delete") {
          result = opencodeConfig.removeProvider(payload.id);
          audit("opencode.provider.deleted", { id: payload.id, ok: result.ok });
        } else if (pathname === "/api/opencode/active") {
          result = opencodeConfig.setActive(payload);
          audit("opencode.provider.activated", { provider: payload.provider, model: payload.model, ok: result.ok });
        } else {
          return send(res, 404, { error: "Not found" });
        }

        if (result.ok) events.broadcast("opencode.config.changed", opencodeConfig.read());
        send(res, result.ok ? 200 : 400, Object.assign({}, result, { config: opencodeConfig.read() }));
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

  // GET /api/session/transcript?id=...&limit=...
  if (req.method === "GET" && pathname === "/api/session/transcript") {
    const convId = parsedUrl.query.id;
    if (!convId) {
      return send(res, 400, { error: "Missing session id parameter" });
    }
    // Clients that poll for live updates only need the tail; a smaller limit
    // keeps the payload (and parse time) small. Default stays at 1000.
    const limit = Math.min(Math.max(Number(parsedUrl.query.limit) || 1000, 20), 1000);
    const msgs = getTranscript(convId, limit);
    const sData = getSessions();
    const foundSession = (sData.sessions || []).find(s => s.conversationId === convId) || { conversationId: convId, title: "Session" };
    const customTitles = getCustomSessionTitles();
    if (customTitles[convId]) {
      foundSession.title = customTitles[convId];
      foundSession.customTitle = true;
    } else if (!foundSession.title || foundSession.title === "Session" || foundSession.title === "New session" || foundSession.title.startsWith("Session ")) {
      const firstUser = (msgs || []).find(m => m.role === "user");
      if (firstUser && firstUser.content) {
        const derived = cleanTitle(firstUser.content, "Session " + convId.slice(0, 8)).slice(0, 60);
        if (derived && derived !== "Session") {
          foundSession.title = derived;
          try { saveCustomSessionTitle(convId, derived); } catch (e) {}
        }
      }
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
            const result = killJobProcess({
              jobId: payload.jobId,
              conversationId: payload.conversationId || payload.session_id,
              engine: payload.engine,
              confirm: payload.confirm
            });
            audit("session.kill", {
              jobId: payload.jobId,
              conversationId: payload.conversationId,
              engine: payload.engine,
              method: result.method,
              tried: result.tried
            });
            return send(res, 200, result);
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
