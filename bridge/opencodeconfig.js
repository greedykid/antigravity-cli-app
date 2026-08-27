// OpenCode Comprehensive Configuration, Model & Session Manager
// Synchronizes with:
// 1. ~/.config/opencode/opencode.jsonc, ~/.config/opencode/opencode.json, ./opencode.json
// 2. ~/.local/state/opencode/model.json (Active & recent models)
// 3. ~/.cache/opencode/models.json (Full provider & model cache)
// 4. ~/.local/share/opencode/opencode.db (SQLite database of real OpenCode sessions & messages)
// 5. ~/.opencode/config.json & ~/.opencode/sessions/

const fs = require("fs");
const path = require("path");
const os = require("os");

const HOME = os.homedir();
const OPENCODE_DIR = path.join(HOME, ".opencode");
const SESSIONS_DIR = path.join(OPENCODE_DIR, "sessions");
const HISTORY_FILE = path.join(OPENCODE_DIR, "history.jsonl");

const CONFIG_PATHS = [
  path.join(HOME, ".config/opencode/opencode.jsonc"),
  path.join(HOME, ".config/opencode/opencode.json"),
  path.join(HOME, ".opencode.jsonc"),
  path.join(HOME, ".opencode.json"),
  path.join(OPENCODE_DIR, "config.json"),
  path.join(OPENCODE_DIR, "opencode.json")
];

const STATE_MODEL_FILE = path.join(HOME, ".local/state/opencode/model.json");
const CACHE_MODELS_FILE = path.join(HOME, ".cache/opencode/models.json");
const OPENCODE_DB_FILE = path.join(HOME, ".local/share/opencode/opencode.db");

function ensureDirs() {
  try {
    if (!fs.existsSync(OPENCODE_DIR)) fs.mkdirSync(OPENCODE_DIR, { recursive: true });
    if (!fs.existsSync(SESSIONS_DIR)) fs.mkdirSync(SESSIONS_DIR, { recursive: true });
  } catch (e) {}
}
ensureDirs();

function stripJsonComments(text) {
  if (!text) return "{}";
  return text
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/\/\/.*/g, "")
    .replace(/,\s*([\]}])/g, "$1")
    .trim();
}

/**
 * Reads all opencode json / jsonc config files and merges them.
 */
function readMergedJsonConfig() {
  let merged = {};
  for (const cp of CONFIG_PATHS) {
    if (fs.existsSync(cp)) {
      try {
        const raw = fs.readFileSync(cp, "utf8");
        const parsed = JSON.parse(stripJsonComments(raw));
        merged = Object.assign(merged, parsed);
      } catch (e) {}
    }
  }
  return merged;
}

/**
 * Returns active provider & active model from state/config files.
 */
function getActiveSelection() {
  let activeProvider = "opencode";
  let activeModel = "deepseek-v4-flash-free";

  // 1. Check ~/.local/state/opencode/model.json
  if (fs.existsSync(STATE_MODEL_FILE)) {
    try {
      const state = JSON.parse(fs.readFileSync(STATE_MODEL_FILE, "utf8"));
      if (state.recent && state.recent.length > 0 && state.recent[0]) {
        if (state.recent[0].providerID) activeProvider = state.recent[0].providerID;
        if (state.recent[0].modelID) activeModel = state.recent[0].modelID;
      }
    } catch (e) {}
  }

  // 2. Override from opencode config if specified
  const jsonCfg = readMergedJsonConfig();
  if (jsonCfg.model) {
    if (jsonCfg.model.includes("/")) {
      const parts = jsonCfg.model.split("/");
      activeProvider = parts[0];
      activeModel = parts.slice(1).join("/");
    } else {
      activeModel = jsonCfg.model;
    }
  }
  if (jsonCfg.provider) {
    activeProvider = jsonCfg.provider;
  }

  return { activeProvider, activeModel };
}

/**
 * Reads all configured providers from opencode.jsonc, models.json cache, and custom list.
 */
function read() {
  const { activeProvider, activeModel } = getActiveSelection();
  const jsonCfg = readMergedJsonConfig();
  const providersMap = new Map();

  // Standard popular providers
  const defaultPresets = [
    { id: "opencode", name: "OpenCode Zen / Free", baseUrl: "https://opencode.ai/api", defaultModel: "deepseek-v4-flash-free" },
    { id: "deepseek", name: "DeepSeek", baseUrl: "https://api.deepseek.com/v1", defaultModel: "deepseek-coder" },
    { id: "anthropic", name: "Anthropic Claude", baseUrl: "https://api.anthropic.com/v1", defaultModel: "claude-3-5-sonnet-latest" },
    { id: "openai", name: "OpenAI", baseUrl: "https://api.openai.com/v1", defaultModel: "gpt-4o" },
    { id: "openrouter", name: "OpenRouter", baseUrl: "https://openrouter.ai/api/v1", defaultModel: "anthropic/claude-3.5-sonnet" },
    { id: "ollama", name: "Ollama (Lokal)", baseUrl: "http://127.0.0.1:11434", defaultModel: "qwen2.5-coder:latest" },
    { id: "groq", name: "Groq Cloud", baseUrl: "https://api.groq.com/openai/v1", defaultModel: "llama-3.3-70b-versatile" }
  ];
  for (const p of defaultPresets) {
    providersMap.set(p.id, {
      id: p.id,
      name: p.name,
      baseUrl: p.baseUrl,
      hasToken: false,
      defaultModel: p.defaultModel
    });
  }

  // Read providers from cache ~/.cache/opencode/models.json
  if (fs.existsSync(CACHE_MODELS_FILE)) {
    try {
      const cacheObj = JSON.parse(fs.readFileSync(CACHE_MODELS_FILE, "utf8"));
      for (const [pid, pdata] of Object.entries(cacheObj)) {
        const existing = providersMap.get(pid);
        const pName = (pdata && pdata.name) || (existing ? existing.name : pid);
        const pApi = (pdata && pdata.api) || (existing ? existing.baseUrl : "");
        const hasEnv = Boolean(pdata && pdata.env && pdata.env.some(e => process.env[e]));
        providersMap.set(pid, {
          id: pid,
          name: pName,
          baseUrl: pApi,
          hasToken: hasEnv,
          defaultModel: existing ? existing.defaultModel : ""
        });
      }
    } catch (e) {}
  }

  // Read custom providers configured in opencode.jsonc / opencode.json
  if (jsonCfg.providers && typeof jsonCfg.providers === "object") {
    for (const [pid, pval] of Object.entries(jsonCfg.providers)) {
      const existing = providersMap.get(pid);
      const name = (pval && pval.name) || (existing ? existing.name : pid);
      const baseUrl = (pval && (pval.baseURL || pval.baseUrl || pval.api || pval.endpoint)) || (existing ? existing.baseUrl : "");
      const hasToken = Boolean(pval && (pval.apiKey || pval.token || pval.key)) || (existing ? existing.hasToken : false);
      const defaultModel = (pval && (pval.defaultModel || pval.model)) || (existing ? existing.defaultModel : "");
      providersMap.set(pid, {
        id: pid,
        name,
        baseUrl,
        hasToken,
        defaultModel
      });
    }
  }

  return {
    ok: true,
    activeProvider,
    activeModel,
    providers: Array.from(providersMap.values())
  };
}

function readConfig() {
  const res = read();
  return {
    activeProvider: res.activeProvider,
    activeModel: res.activeModel,
    providers: res.providers
  };
}

/**
 * Returns models for a provider, querying cache, config, or live endpoint.
 */
function getModelsForProvider(providerId) {
  const pid = String(providerId || "opencode").toLowerCase();
  const models = [];

  // 1. Check ~/.cache/opencode/models.json
  if (fs.existsSync(CACHE_MODELS_FILE)) {
    try {
      const cacheObj = JSON.parse(fs.readFileSync(CACHE_MODELS_FILE, "utf8"));
      if (cacheObj[pid] && cacheObj[pid].models) {
        models.push(...Object.keys(cacheObj[pid].models));
      }
    } catch (e) {}
  }

  // 2. Check opencode.jsonc configured models
  const jsonCfg = readMergedJsonConfig();
  if (jsonCfg.providers && jsonCfg.providers[pid]) {
    const pInfo = jsonCfg.providers[pid];
    if (pInfo.models && Array.isArray(pInfo.models)) {
      for (const m of pInfo.models) {
        const mid = typeof m === "string" ? m : (m && (m.id || m.name));
        if (mid && !models.includes(mid)) models.push(mid);
      }
    }
    if (pInfo.model && !models.includes(pInfo.model)) {
      models.unshift(pInfo.model);
    }
  }

  // 3. Defaults based on provider
  if (!models.length) {
    if (pid === "deepseek") models.push("deepseek-coder", "deepseek-chat", "deepseek-reasoner");
    else if (pid === "anthropic") models.push("claude-3-5-sonnet-latest", "claude-3-5-haiku-latest", "claude-3-opus-latest");
    else if (pid === "openai") models.push("gpt-4o", "gpt-4o-mini", "o1", "o3-mini");
    else if (pid === "ollama") models.push("qwen2.5-coder:latest", "llama3:latest", "deepseek-coder-v2:latest");
    else if (pid === "opencode") models.push("deepseek-v4-flash-free", "claude-sonnet-4-6", "gpt-5.1-codex-mini");
  }

  return Array.from(new Set(models));
}

function providerSecret(id) {
  const jsonCfg = readMergedJsonConfig();
  let foundUrl = "";
  let foundToken = "";

  if (jsonCfg.providers && jsonCfg.providers[id]) {
    const p = jsonCfg.providers[id];
    foundUrl = p.baseURL || p.baseUrl || p.api || p.endpoint || "";
    foundToken = p.apiKey || p.token || p.key || "";
  }

  if (!foundUrl) {
    const all = read().providers;
    const item = all.find(p => p.id === id);
    if (item) foundUrl = item.baseUrl;
  }

  return {
    id,
    baseUrl: foundUrl,
    token: foundToken
  };
}

function setActive(payload) {
  if (!payload) return { ok: false, error: "Payload kosong" };
  const provider = payload.provider;
  const model = payload.model;

  // 1. Update ~/.local/state/opencode/model.json
  try {
    const stateDir = path.dirname(STATE_MODEL_FILE);
    if (!fs.existsSync(stateDir)) fs.mkdirSync(stateDir, { recursive: true });

    let state = { recent: [], favorite: [], variant: {} };
    if (fs.existsSync(STATE_MODEL_FILE)) {
      try { state = JSON.parse(fs.readFileSync(STATE_MODEL_FILE, "utf8")); } catch (e) {}
    }

    if (model || provider) {
      const pid = provider || (state.recent && state.recent[0] && state.recent[0].providerID) || "opencode";
      const mid = model || (state.recent && state.recent[0] && state.recent[0].modelID) || "deepseek-v4-flash-free";
      state.recent = [{ providerID: pid, modelID: mid }];
    }
    fs.writeFileSync(STATE_MODEL_FILE, JSON.stringify(state, null, 2), "utf8");
  } catch (e) {}

  // 2. Also save to ~/.config/opencode/opencode.jsonc or ~/.opencode/config.json
  try {
    const targetFile = fs.existsSync(path.join(HOME, ".config/opencode/opencode.jsonc"))
      ? path.join(HOME, ".config/opencode/opencode.jsonc")
      : path.join(OPENCODE_DIR, "config.json");

    let cfg = {};
    if (fs.existsSync(targetFile)) {
      try { cfg = JSON.parse(stripJsonComments(fs.readFileSync(targetFile, "utf8"))); } catch (e) {}
    }
    if (provider) cfg.provider = provider;
    if (model) cfg.model = model;
    fs.writeFileSync(targetFile, JSON.stringify(cfg, null, 2), "utf8");
  } catch (e) {}

  return { ok: true, activeProvider: provider, activeModel: model };
}

function upsertProvider(payload) {
  if (!payload || !payload.id) return { ok: false, error: "ID provider wajib diisi" };
  const id = String(payload.id).trim().toLowerCase();

  try {
    const targetFile = fs.existsSync(path.join(HOME, ".config/opencode/opencode.jsonc"))
      ? path.join(HOME, ".config/opencode/opencode.jsonc")
      : path.join(OPENCODE_DIR, "config.json");

    let cfg = {};
    if (fs.existsSync(targetFile)) {
      try { cfg = JSON.parse(stripJsonComments(fs.readFileSync(targetFile, "utf8"))); } catch (e) {}
    }

    if (!cfg.providers) cfg.providers = {};
    cfg.providers[id] = Object.assign({}, cfg.providers[id] || {}, {
      name: payload.name || id,
      baseURL: (payload.baseUrl || "").replace(/\/+$/, ""),
      apiKey: payload.token !== undefined ? payload.token : (cfg.providers[id] && cfg.providers[id].apiKey) || "",
      defaultModel: payload.defaultModel || (cfg.providers[id] && cfg.providers[id].defaultModel) || ""
    });

    fs.writeFileSync(targetFile, JSON.stringify(cfg, null, 2), "utf8");
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e.message };
  }
}

function removeProvider(id) {
  try {
    const targetFile = fs.existsSync(path.join(HOME, ".config/opencode/opencode.jsonc"))
      ? path.join(HOME, ".config/opencode/opencode.jsonc")
      : path.join(OPENCODE_DIR, "config.json");

    if (fs.existsSync(targetFile)) {
      const cfg = JSON.parse(stripJsonComments(fs.readFileSync(targetFile, "utf8")));
      if (cfg.providers && cfg.providers[id]) {
        delete cfg.providers[id];
        fs.writeFileSync(targetFile, JSON.stringify(cfg, null, 2), "utf8");
      }
    }
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e.message };
  }
}

// -------------------------------------------------------------
// SESSIONS & TRANSCRIPTS (SQLite DB + JSONL)
// -------------------------------------------------------------

function getOpencodeSessions(workdir) {
  ensureDirs();
  const map = new Map();

  // 1. Scan real SQLite DB ~/.local/share/opencode/opencode.db
  if (fs.existsSync(OPENCODE_DB_FILE)) {
    try {
      const { DatabaseSync } = require("node:sqlite");
      const db = new DatabaseSync(OPENCODE_DB_FILE);
      const rows = db.prepare("SELECT id, title, time_updated, directory, model FROM session ORDER BY time_updated DESC").all();
      for (const r of rows) {
        if (r.id && !map.has(r.id)) {
          map.set(r.id, {
            conversationId: r.id,
            title: r.title || ("OpenCode " + r.id.slice(0, 8)),
            timestamp: r.time_updated || Date.now(),
            workspace: r.directory || workdir || HOME,
            engine: "opencode",
            hostname: os.hostname(),
            model: r.model
          });
        }
      }
    } catch (e) {}
  }

  // 2. Scan history.jsonl
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
              workspace: item.workspace || workdir || HOME,
              engine: "opencode",
              hostname: os.hostname()
            });
          }
        } catch (e) {}
      }
    } catch (e) {}
  }

  // 3. Scan sessions JSONL directory
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
          workspace: workdir || HOME,
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

  // 1. Try real SQLite DB
  if (fs.existsSync(OPENCODE_DB_FILE)) {
    try {
      const { DatabaseSync } = require("node:sqlite");
      const db = new DatabaseSync(OPENCODE_DB_FILE);
      const msgs = db.prepare("SELECT id, data, time_created FROM message WHERE session_id = ? ORDER BY time_created ASC").all(convId);
      const parts = db.prepare("SELECT message_id, data, time_created FROM part WHERE session_id = ? ORDER BY time_created ASC").all(convId);

      if (msgs && msgs.length > 0) {
        const partsByMsg = new Map();
        for (const p of parts) {
          if (!partsByMsg.has(p.message_id)) partsByMsg.set(p.message_id, []);
          try {
            partsByMsg.get(p.message_id).push(JSON.parse(p.data));
          } catch (e) {}
        }

        const turns = [];
        for (const m of msgs) {
          let mData = {};
          try { mData = JSON.parse(m.data); } catch (e) {}
          const role = mData.role || "user";
          const pList = partsByMsg.get(m.id) || [];

          for (const p of pList) {
            if (p.type === "text" && p.text) {
              turns.push({
                role: role,
                content: p.text,
                time: new Date(m.time_created).toISOString()
              });
            } else if (p.type === "reasoning" && p.text) {
              turns.push({
                role: "thinking",
                toolTitle: "Thinking",
                title: "Thinking Process",
                command: "Internal Reasoning",
                content: p.text,
                time: new Date(m.time_created).toISOString()
              });
            } else if (p.type === "tool") {
              const cmd = (p.state && p.state.input && (p.state.input.command || p.state.input.filePath)) || "";
              turns.push({
                role: "tool",
                toolName: p.tool || "tool",
                toolTitle: "Action: " + (p.tool || "tool"),
                title: (p.state && p.state.input && p.state.input.command) ? "$ " + p.state.input.command : ("Tool: " + (p.tool || "tool")),
                command: cmd,
                content: (p.state && p.state.output) ? p.state.output : JSON.stringify(p.state || {}),
                time: new Date(m.time_created).toISOString()
              });
            }
          }
        }

        if (turns.length > 0) return turns.slice(-limit);
      }
    } catch (e) {}
  }

  // 2. Try JSONL file
  const file = path.join(SESSIONS_DIR, `${convId}.jsonl`);
  if (fs.existsSync(file)) {
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
    } catch (e) {}
  }

  return [];
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
        workspace: meta.workspace || HOME
      };
      fs.appendFileSync(HISTORY_FILE, JSON.stringify(histItem) + "\n", "utf8");
    }
  } catch (e) {}
}

module.exports = {
  read,
  readConfig,
  getModelsForProvider,
  providerSecret,
  upsertProvider,
  removeProvider,
  setActive,
  getOpencodeSessions,
  getOpencodeTranscript,
  recordTurn
};
