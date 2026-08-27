// OpenCode Configuration & Session Manager for Bridge Server
// Handles ~/.opencode/config.json and persistent session history in ~/.opencode/sessions/

const fs = require("fs");
const path = require("path");
const os = require("os");

const OPENCODE_DIR = path.join(os.homedir(), ".opencode");
const CONFIG_FILE = path.join(OPENCODE_DIR, "config.json");
const SESSIONS_DIR = path.join(OPENCODE_DIR, "sessions");
const HISTORY_FILE = path.join(OPENCODE_DIR, "history.jsonl");

// Ensure directories exist
function ensureDirs() {
  try {
    if (!fs.existsSync(OPENCODE_DIR)) fs.mkdirSync(OPENCODE_DIR, { recursive: true });
    if (!fs.existsSync(SESSIONS_DIR)) fs.mkdirSync(SESSIONS_DIR, { recursive: true });
  } catch (e) {}
}
ensureDirs();

const DEFAULT_PROVIDERS = [
  { id: "deepseek", name: "DeepSeek", baseUrl: "https://api.deepseek.com/v1", token: "", defaultModel: "deepseek-coder" },
  { id: "anthropic", name: "Anthropic Claude", baseUrl: "https://api.anthropic.com/v1", token: "", defaultModel: "claude-3-5-sonnet-latest" },
  { id: "openai", name: "OpenAI", baseUrl: "https://api.openai.com/v1", token: "", defaultModel: "gpt-4o" },
  { id: "openrouter", name: "OpenRouter", baseUrl: "https://openrouter.ai/api/v1", token: "", defaultModel: "anthropic/claude-3.5-sonnet" },
  { id: "ollama", name: "Ollama (Lokal)", baseUrl: "http://127.0.0.1:11434", token: "", defaultModel: "qwen2.5-coder:latest" },
  { id: "groq", name: "Groq Cloud", baseUrl: "https://api.groq.com/openai/v1", token: "", defaultModel: "llama-3.3-70b-versatile" }
];

function readConfig() {
  ensureDirs();
  try {
    if (fs.existsSync(CONFIG_FILE)) {
      const data = JSON.parse(fs.readFileSync(CONFIG_FILE, "utf8"));
      return {
        activeProvider: data.activeProvider || "deepseek",
        activeModel: data.activeModel || "deepseek-coder",
        providers: data.providers || DEFAULT_PROVIDERS
      };
    }
  } catch (e) {}

  return {
    activeProvider: "deepseek",
    activeModel: "deepseek-coder",
    providers: DEFAULT_PROVIDERS
  };
}

function saveConfig(cfg) {
  ensureDirs();
  try {
    fs.writeFileSync(CONFIG_FILE, JSON.stringify(cfg, null, 2), "utf8");
    return true;
  } catch (e) {
    return false;
  }
}

function read() {
  const cfg = readConfig();
  return {
    activeProvider: cfg.activeProvider,
    activeModel: cfg.activeModel,
    providers: (cfg.providers || []).map(p => ({
      id: p.id,
      name: p.name || p.id,
      baseUrl: p.baseUrl || "",
      hasToken: Boolean(p.token && p.token.trim()),
      defaultModel: p.defaultModel || ""
    }))
  };
}

function providerSecret(id) {
  const cfg = readConfig();
  const found = (cfg.providers || []).find(p => p.id === id);
  if (!found) return null;
  return {
    id: found.id,
    name: found.name,
    baseUrl: found.baseUrl,
    token: found.token || ""
  };
}

function upsertProvider(payload) {
  if (!payload || !payload.id) return { ok: false, error: "ID provider wajib diisi" };
  const cfg = readConfig();
  const id = String(payload.id).trim().toLowerCase();
  let list = cfg.providers || [];
  const idx = list.findIndex(p => p.id === id);

  const item = {
    id,
    name: payload.name || id,
    baseUrl: (payload.baseUrl || "").replace(/\/+$/, ""),
    token: payload.token !== undefined ? payload.token : (idx >= 0 ? list[idx].token : ""),
    defaultModel: payload.defaultModel || ""
  };

  if (idx >= 0) {
    list[idx] = Object.assign({}, list[idx], item);
  } else {
    list.push(item);
  }

  cfg.providers = list;
  saveConfig(cfg);
  return { ok: true };
}

function removeProvider(id) {
  const cfg = readConfig();
  cfg.providers = (cfg.providers || []).filter(p => p.id !== id);
  if (cfg.activeProvider === id && cfg.providers.length) {
    cfg.activeProvider = cfg.providers[0].id;
  }
  saveConfig(cfg);
  return { ok: true };
}

function setActive(payload) {
  const cfg = readConfig();
  if (payload.provider) cfg.activeProvider = payload.provider;
  if (payload.model) cfg.activeModel = payload.model;
  saveConfig(cfg);
  return { ok: true };
}

// -------------------------------------------------------------
// SESSIONS & TRANSCRIPTS
// -------------------------------------------------------------

function getOpencodeSessions(workdir) {
  ensureDirs();
  const map = new Map();

  // 1. Scan history.jsonl
  if (fs.existsSync(HISTORY_FILE)) {
    try {
      const lines = fs.readFileSync(HISTORY_FILE, "utf8").trim().split("\n").filter(Boolean);
      for (let i = lines.length - 1; i >= 0; i--) {
        try {
          const item = JSON.parse(lines[i]);
          const sid = item.conversationId || item.id || item.sessionId;
          if (sid && !map.has(sid)) {
            map.set(sid, {
              conversationId: sid,
              title: item.title || ("OpenCode " + sid.slice(0, 8)),
              timestamp: item.timestamp || Date.now(),
              workspace: item.workspace || workdir || os.homedir(),
              engine: "opencode",
              hostname: os.hostname()
            });
          }
        } catch (e) {}
      }
    } catch (e) {}
  }

  // 2. Scan sessions directory
  if (fs.existsSync(SESSIONS_DIR)) {
    try {
      const files = fs.readdirSync(SESSIONS_DIR);
      for (const fname of files) {
        if (!fname.endsWith(".jsonl")) continue;
        const sid = fname.replace(/\.jsonl$/, "");
        if (map.has(sid)) continue;

        const fpath = path.join(SESSIONS_DIR, fname);
        let title = "OpenCode " + sid.slice(0, 8);
        let mtime = Date.now();
        try {
          const st = fs.statSync(fpath);
          mtime = st.mtimeMs;
          const firstLine = fs.readFileSync(fpath, "utf8").split("\n")[0];
          if (firstLine) {
            const first = JSON.parse(firstLine);
            if (first.content) title = first.content.slice(0, 60);
          }
        } catch (e) {}

        map.set(sid, {
          conversationId: sid,
          title,
          timestamp: mtime,
          workspace: workdir || os.homedir(),
          engine: "opencode",
          hostname: os.hostname()
        });
      }
    } catch (e) {}
  }

  return Array.from(map.values());
}

function getOpencodeTranscript(convId, limit = 1000) {
  ensureDirs();
  if (!convId) return [];
  const file = path.join(SESSIONS_DIR, `${convId}.jsonl`);
  if (!fs.existsSync(file)) return [];

  try {
    const lines = fs.readFileSync(file, "utf8").trim().split("\n").filter(Boolean);
    const turns = [];
    for (const l of lines) {
      try {
        const turn = JSON.parse(l);
        turns.push(turn);
      } catch (e) {}
    }
    return turns.slice(-limit);
  } catch (e) {
    return [];
  }
}

function recordTurn(convId, role, content, meta = {}) {
  ensureDirs();
  if (!convId) return;
  const file = path.join(SESSIONS_DIR, `${convId}.jsonl`);
  const turn = {
    role,
    content,
    time: meta.time || new Date().toISOString(),
    engine: "opencode",
    model: meta.model || undefined
  };

  try {
    fs.appendFileSync(file, JSON.stringify(turn) + "\n", "utf8");

    // Update history.jsonl
    let title = meta.title;
    if (!title && role === "user") {
      title = content.slice(0, 60);
    }
    if (title) {
      const histItem = {
        conversationId: convId,
        title,
        timestamp: Date.now(),
        engine: "opencode",
        workspace: meta.workspace || os.homedir()
      };
      fs.appendFileSync(HISTORY_FILE, JSON.stringify(histItem) + "\n", "utf8");
    }
  } catch (e) {}
}

module.exports = {
  read,
  readConfig,
  saveConfig,
  providerSecret,
  upsertProvider,
  removeProvider,
  setActive,
  getOpencodeSessions,
  getOpencodeTranscript,
  recordTurn
};
