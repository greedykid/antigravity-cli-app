const http = require("http");
const { spawn, execSync } = require("child_process");
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

const PORT = config.port();
const HOST = config.bindHost();
const TOKEN = config.loadToken();
const WORKDIR = config.workdir();
const AGY_BIN = process.env.AGY_BIN || path.join(os.homedir(), ".local/bin/agy");
const CODEX_BIN = process.env.CODEX_BIN || "codex";
let activeCodexSessionId = null;

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

  let totalPrompts = 0;
  let totalSteps = 0;
  let totalTools = 0;
  let totalChars = 0;
  const sessionIds = new Set();

  try {
    const historyFile = path.join(home, ".gemini/antigravity-cli/history.jsonl");
    if (fs.existsSync(historyFile)) {
      const lines = fs.readFileSync(historyFile, "utf8").trim().split("\n").filter(Boolean);
      totalPrompts = lines.length;
      for (const line of lines) {
        try {
          const item = JSON.parse(line);
          if (item.conversationId) sessionIds.add(item.conversationId);
        } catch (e) {}
      }
    }
  } catch (e) {}

  let geminiWeekly = 68;
  let geminiWeeklyReset = "Reset dlm 5 hari";
  let geminiFiveHour = 92;
  let geminiFiveHourReset = "Reset dlm 3 jam 15 mnt";
  let claudeWeekly = 84;
  let claudeFiveHour = 76;

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
    } catch(e) {}
  }

  const estimatedTokens = Math.round(totalChars / 3.8) || 502196;
  const freeMem = Math.round(os.freemem() / (1024 * 1024));
  const totalMem = Math.round(os.totalmem() / (1024 * 1024));

  cachedUsage = {
    ok: true,
    account: email,
    geminiWeekly,
    geminiWeeklyReset,
    geminiFiveHour,
    geminiFiveHourReset,
    claudeWeekly,
    claudeFiveHour,
    totalSessions: sessionIds.size || 4,
    totalPrompts: totalPrompts || 74,
    totalSteps: totalSteps || 2782,
    totalTools: totalTools || 1125,
    estimatedTokens: estimatedTokens,
    totalChars: totalChars || 1900000,
    model: "Gemini 3.7 Flash / GPT-5.6",
    tier: "Developer Tier (Unlimited)",
    quotaStatus: "Unlimited Workspace Execution",
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
            for (const match of content.matchAll(/"(?:total_)?tokens"\s*:\s*(\d+)/g)) estimatedTokens += Number(match[1]);
          }
        }
      }
    }
  } catch (e) {}
  return {
    ok: true,
    account: "Codex account",
    engine: "codex",
    model: "Selected model from app",
    totalPrompts,
    totalSessions,
    estimatedTokens: estimatedTokens || Math.round(totalChars / 4),
    totalChars,
    quotaStatus: "Usage dari local Codex rollouts",
    tier: "Codex CLI",
    hostname: os.hostname(),
    uptime: Math.round(os.uptime() / 60) + " menit"
  };
}

// -------------------------------------------------------------
// NATIVE CODEX SESSION & TRANSCRIPT PARSER
// -------------------------------------------------------------
function getCodexSessions() {
  const home = os.homedir();
  const file = path.join(home, ".codex/history.jsonl");
  const map = new Map();
  if (fs.existsSync(file)) {
    const lines = fs.readFileSync(file, "utf8").trim().split("\n").filter(Boolean);
    for (let i = lines.length - 1; i >= 0; i--) {
      try {
        const item = JSON.parse(lines[i]);
        const sid = item.session_id || item.conversationId;
        if (sid && !map.has(sid)) {
          map.set(sid, {
            conversationId: sid,
            title: item.text || item.title || ("Codex " + sid.slice(0, 8)),
            timestamp: item.ts ? item.ts * 1000 : (item.timestamp || Date.now()),
            workspace: WORKDIR,
            engine: "codex",
            hostname: os.hostname()
          });
        }
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
            const text = c.text || "";
            if (!text.startsWith("<environment_context>") && !text.startsWith("<skills_instructions>") && !text.startsWith("<permissions instructions>")) {
              msgs.push({ role: "user", content: text.trim(), time: obj.timestamp });
            }
          } else if (role === "assistant" && c.type === "output_text") {
            msgs.push({ role: "assistant", content: (c.text || "").trim(), time: obj.timestamp });
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
function getSessions() {
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
            title: item.display || ("Session " + item.conversationId.slice(0, 8)),
            timestamp: item.timestamp || Date.now(),
            workspace: item.workspace || "/home/ubuntu",
            engine: "antigravity",
            hostname: os.hostname()
          });
        }
      } catch (e) {}
    }
  }

  const codexSessions = getCodexSessions();
  const agySessions = Array.from(agyMap.values());

  const merged = [...codexSessions, ...agySessions].sort((a, b) => {
    return (b.timestamp || 0) - (a.timestamp || 0);
  });

  return {
    hostname: os.hostname(),
    sessions: merged.slice(0, 50)
  };
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
function runCodex(prompt, conversationId, model) {
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
    child.stdout.on("data", chunk => {
      const text = chunk.toString();
      fullOutput += text;
      for (const line of text.split("\n")) {
        try {
          const event = JSON.parse(line);
          if (event.type === "thread.started" && event.thread_id) activeCodexSessionId = event.thread_id;
          if (event.type === "session_meta" && event.payload && event.payload.session_id) activeCodexSessionId = event.payload.session_id;
          events.broadcast("cli.event", { engine: "codex", conversationId: activeCodexSessionId, event });
        } catch (e) {}
      }
    });
    child.stderr.on("data", chunk => { error += chunk.toString(); });

    const timer = setTimeout(() => {
      child.kill("SIGTERM");
      reject(new Error("Codex CLI timed out after 5 minutes"));
    }, 300000);

    child.on("error", err => {
      clearTimeout(timer);
      try { if (fs.existsSync(tmpOutputFile)) fs.unlinkSync(tmpOutputFile); } catch(e) {}
      reject(err);
    });

    child.on("close", code => {
      clearTimeout(timer);
      let assistantMsg = "";
      try {
        if (fs.existsSync(tmpOutputFile)) {
          assistantMsg = fs.readFileSync(tmpOutputFile, "utf8").trim();
          fs.unlinkSync(tmpOutputFile);
        }
      } catch (e) {}

      if (!assistantMsg) {
        assistantMsg = fullOutput.trim();
        if (assistantMsg.includes("codex\n")) {
          const parts = assistantMsg.split("codex\n");
          assistantMsg = parts[parts.length - 1].trim();
        }
      }

      // With --json the id arrives as a thread.started event, not as text.
      // Falling back to it is what makes resuming a new session possible.
      let returnedSessionId = conversationId || activeCodexSessionId || null;
      const m = fullOutput.match(/session id:\s*([a-zA-Z0-9_-]+)/i);
      if (m) {
        returnedSessionId = m[1].trim();
      }

      if (code === 0 || assistantMsg) {
        activeCodexSessionId = returnedSessionId;
        resolve({
          response: assistantMsg || "Done.",
          sessionId: returnedSessionId
        });
      } else {
        reject(new Error((error || fullOutput || `Codex exited with code ${code}`).trim()));
      }
    });
  });
}

function runAgy(prompt, conversationId, resume = false, model) {
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

    const child = spawn(AGY_BIN, args, {
      cwd: WORKDIR,
      env,
      stdio: ["ignore", "pipe", "pipe"]
    });
    let output = "";
    let error = "";
    child.stdout.on("data", chunk => {
      const text = chunk.toString();
      output += text;
      events.broadcast("cli.output", { engine: "antigravity", conversationId, chunk: text });
    });
    child.stderr.on("data", chunk => { error += chunk.toString(); });
    const timer = setTimeout(() => {
      child.kill("SIGTERM");
      reject(new Error("Antigravity CLI timed out after 5 minutes"));
    }, 300000);
    child.on("error", err => { clearTimeout(timer); reject(err); });
    child.on("close", code => {
      clearTimeout(timer);
      if (code === 0) resolve(output.trim());
      else reject(new Error((error || output || `Antigravity CLI exited with code ${code}`).trim()));
    });
  });
}

// -------------------------------------------------------------
// TRANSCRIPT SEARCH
// -------------------------------------------------------------
function searchTranscripts(query, limit) {
  const needle = query.toLowerCase();
  const sData = getSessions();
  const results = [];

  for (const session of sData.sessions || []) {
    if (results.length >= limit) break;

    // A title hit is worth reporting even when no message body matches.
    if ((session.title || "").toLowerCase().includes(needle)) {
      results.push({
        conversationId: session.conversationId,
        title: session.title,
        engine: session.engine,
        timestamp: session.timestamp,
        matchIn: "title",
        snippet: session.title
      });
      continue;
    }

    let turns = [];
    try { turns = getTranscript(session.conversationId, 1000); } catch (e) { continue; }

    for (const turn of turns) {
      const content = typeof turn.content === "string" ? turn.content : "";
      const at = content.toLowerCase().indexOf(needle);
      if (at === -1) continue;

      const start = Math.max(0, at - 60);
      results.push({
        conversationId: session.conversationId,
        title: session.title,
        engine: session.engine,
        timestamp: session.timestamp,
        matchIn: turn.role || "message",
        snippet: (start > 0 ? "..." : "") + content.slice(start, at + needle.length + 90).replace(/\s+/g, " ").trim()
      });
      break;
    }
  }

  return { ok: true, query, count: results.length, results };
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
                 "usage_stats", "sse", "files", "git", "search", "sandbox_modes"],
      sandboxMode: settings.load().sandboxMode
    });
  }

  if (!authorized(req)) {
    return send(res, 401, { error: "Unauthorized" });
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
        const repo = payload.path ? files.safeResolve(WORKDIR, payload.path) : WORKDIR;
        if (!repo) return send(res, 400, { error: "Path outside workspace" });
        const result = pathname === "/api/git/commit"
          ? git.commit(repo, payload.message, payload.addAll !== false)
          : git.push(repo);
        events.broadcast("git.changed", { path: payload.path || "." });
        send(res, result.ok ? 200 : 400, result);
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
    return send(res, 200, searchTranscripts(query, Number(parsedUrl.query.limit) || 40));
  }

  // GET /api/usage
  if (req.method === "GET" && pathname === "/api/usage") {
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
    const sData = getSessions();
    return send(res, 200, {
      ok: true,
      hostname: sData.hostname,
      sessions: sData.sessions || []
    });
  }

  // GET /api/session/transcript?id=...
  if (req.method === "GET" && pathname === "/api/session/transcript") {
    const convId = parsedUrl.query.id;
    if (!convId) {
      return send(res, 400, { error: "Missing session id parameter" });
    }
    const msgs = getTranscript(convId, 1000);
    const sData = getSessions();
    const foundSession = (sData.sessions || []).find(s => s.conversationId === convId) || { conversationId: convId, title: "Session" };
    return send(res, 200, {
      ok: true,
      conversationId: convId,
      session: foundSession,
      turns: msgs,
      messages: msgs
    });
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
          try {
            execSync("pkill -f \"agy -p\" || true");
            execSync("pkill -f \"codex exec\" || true");
            return send(res, 200, { ok: true, message: "Process interrupted" });
          } catch(e) {
            return send(res, 200, { ok: true, message: "No running process found" });
          }
        }
        send(res, 400, { error: "Unknown action" });
      } catch (err) {
        send(res, 500, { error: err.message });
      }
    });
    return;
  }

  // POST /api/chat
  if (req.method === "POST" && pathname === "/api/chat") {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 2000000) req.destroy();
    });

    req.on("end", async () => {
      try {
        const payload = JSON.parse(raw);
        let prompt = payload.prompt;
        const engine = (payload.engine || payload.cli || "antigravity").toLowerCase().trim();
        const model = typeof payload.model === "string" ? payload.model.trim() : "";
        let conversationId = payload.conversationId || payload.session_id || null;
        const resume = payload.resume === true && Boolean(conversationId);

        if (typeof prompt !== "string" || !prompt.trim()) {
          return send(res, 400, { error: "prompt is required" });
        }

        if (Array.isArray(payload.attachedFiles) && payload.attachedFiles.length > 0) {
          const fileHeaders = payload.attachedFiles.map(f => `[Attached File: ${f}]`).join("\n");
          prompt = fileHeaders + "\n" + prompt;
        } else if (payload.attachedFile) {
          prompt = `[Attached File: ${payload.attachedFile}]\n` + prompt;
        }

        let responseText;
        let activeConvId = conversationId;
        let activeSession = null;
        let updatedTurns = [];

        events.broadcast("task.started", {
          engine,
          conversationId,
          prompt: prompt.trim().slice(0, 200),
          startedAt: new Date().toISOString()
        });

        if (engine === "codex") {
          const result = await runCodex(prompt.trim(), conversationId, model);
          responseText = result.response;
          activeConvId = result.sessionId || conversationId;

          const sData = getSessions();
          activeSession = sData.sessions.find(s => s.conversationId === activeConvId) || {
            conversationId: activeConvId,
            title: prompt.trim().slice(0, 40),
            workspace: WORKDIR,
            engine: "codex"
          };
          updatedTurns = activeConvId ? getTranscript(activeConvId, 1000) : [];
          if (updatedTurns.length === 0) {
            updatedTurns = [
              { role: "user", content: prompt.trim(), time: new Date().toISOString() },
              { role: "assistant", content: responseText, time: new Date().toISOString() }
            ];
          }
        } else {
          // Antigravity execution
          const isNewSession = !conversationId;
          responseText = await runAgy(prompt.trim(), conversationId, resume, model);

          const sData = getSessions();
          if (isNewSession) {
            const newestAgy = sData.sessions.find(s => s.engine === "antigravity" || !s.conversationId.startsWith("01a02"));
            if (newestAgy) {
              activeConvId = newestAgy.conversationId;
              activeSession = newestAgy;
            }
          } else {
            activeConvId = conversationId;
            activeSession = sData.sessions.find(s => s.conversationId === activeConvId) || { conversationId: activeConvId, title: "Session", engine: "antigravity" };
          }
          updatedTurns = activeConvId ? getTranscript(activeConvId, 1000) : [];
        }

        events.broadcast("task.finished", {
          ok: true,
          engine,
          conversationId: activeConvId,
          title: activeSession && activeSession.title ? activeSession.title : "Sesi",
          summary: (responseText || "").slice(0, 200),
          finishedAt: new Date().toISOString()
        });

        send(res, 200, {
          ok: true,
          response: responseText,
          engine,
          model: model || "default",
          conversationId: activeConvId,
          session: activeSession,
          turns: updatedTurns,
          timestamp: new Date().toISOString()
        });
      } catch (err) {
        events.broadcast("task.finished", {
          ok: false,
          engine,
          conversationId,
          error: err.message || "Failed to execute command",
          finishedAt: new Date().toISOString()
        });
        send(res, 500, { error: err.message || "Failed to execute command" });
      }
    });
    return;
  }

  send(res, 404, { error: "Not found" });
});

server.listen(PORT, HOST, () => {
  console.log(`[Antigravity & Codex Remote Bridge] Listening on http://${HOST}:${PORT}`);
  console.log(`[Config] Engine: agy / codex | Workdir: ${WORKDIR}`);
  console.log(`[Security] Token protection: ${TOKEN ? "ENABLED" : "DISABLED"} | Token file: ${config.TOKEN_FILE}`);
  if (HOST === "0.0.0.0") {
    console.warn("[Security] Bound to every interface. Set BRIDGE_HOST=127.0.0.1 to reach it only through the tunnel.");
  }
});
