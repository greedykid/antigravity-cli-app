// Read and edit ~/.codex/config.toml.
//
// This file decides which endpoint Codex sends prompts and code to, so edits
// are surgical: the original text is preserved and only the targeted keys or
// provider block are rewritten. Anything this module does not understand — and
// there is plenty, like [projects."..."] trust levels — survives untouched.

const fs = require("fs");
const path = require("path");
const os = require("os");

const CONFIG_FILE = path.join(os.homedir(), ".codex", "config.toml");

const TOP_LEVEL_KEYS = ["model", "model_provider", "model_reasoning_effort"];
const WIRE_APIS = ["chat", "responses"];

function readRaw() {
  try {
    return fs.existsSync(CONFIG_FILE) ? fs.readFileSync(CONFIG_FILE, "utf8") : "";
  } catch (e) {
    return "";
  }
}

function parseScalar(raw) {
  const text = raw.trim();
  if (/^".*"$/.test(text) || /^'.*'$/.test(text)) return text.slice(1, -1);
  if (text === "true") return true;
  if (text === "false") return false;
  if (/^-?\d+$/.test(text)) return Number(text);
  return text;
}

/** Splits a table header into segments, honouring quoted ones. */
function splitHeader(header) {
  const parts = [];
  let current = "";
  let quoted = false;
  for (const ch of header) {
    if (ch === '"') { quoted = !quoted; continue; }
    if (ch === "." && !quoted) { parts.push(current); current = ""; continue; }
    current += ch;
  }
  parts.push(current);
  return parts.map(p => p.trim()).filter(Boolean);
}

function parse(text) {
  const top = {};
  const providers = {};
  let table = null;

  for (const line of String(text || "").split("\n")) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;

    const header = trimmed.match(/^\[([^\]]+)\]$/);
    if (header) {
      table = splitHeader(header[1]);
      continue;
    }

    const kv = trimmed.match(/^([A-Za-z0-9_.-]+)\s*=\s*(.+)$/);
    if (!kv) continue;
    const key = kv[1];
    const value = parseScalar(kv[2]);

    if (!table) {
      top[key] = value;
    } else if (table.length === 2 && table[0] === "model_providers") {
      const id = table[1];
      providers[id] = providers[id] || { id };
      providers[id][key] = value;
    }
  }
  return { top, providers };
}

function maskSecret(value) {
  const text = String(value || "");
  if (!text) return "";
  if (text.length <= 8) return "•".repeat(text.length);
  return text.slice(0, 4) + "•".repeat(Math.min(12, text.length - 8)) + text.slice(-4);
}

function read() {
  const raw = readRaw();
  const { top, providers } = parse(raw);

  const list = Object.values(providers).map(p => ({
    id: p.id,
    name: p.name || p.id,
    baseUrl: p.base_url || "",
    wireApi: p.wire_api || "chat",
    envKey: p.env_key || "",
    // Never hand the secret back out; the app only needs to know it is set.
    hasToken: Boolean(p.experimental_bearer_token || p.env_key),
    tokenPreview: p.experimental_bearer_token ? maskSecret(p.experimental_bearer_token) : ""
  }));

  return {
    ok: true,
    exists: Boolean(raw),
    file: CONFIG_FILE,
    activeProvider: top.model_provider || "",
    activeModel: top.model || "",
    reasoningEffort: top.model_reasoning_effort || "",
    providers: list,
    wireApis: WIRE_APIS
  };
}

function backup(raw) {
  try {
    fs.mkdirSync(path.dirname(CONFIG_FILE), { recursive: true });
    if (raw) fs.writeFileSync(CONFIG_FILE + ".bak", raw);
  } catch (e) {}
}

function write(text) {
  fs.mkdirSync(path.dirname(CONFIG_FILE), { recursive: true });
  fs.writeFileSync(CONFIG_FILE, text.endsWith("\n") ? text : text + "\n");
}

/** Replaces a top-level key, or inserts it above the first table header. */
function setTopLevelKey(text, key, value) {
  const lines = String(text).split("\n");
  const literal = `${key} = ${JSON.stringify(String(value))}`;

  let firstTable = lines.length;
  for (let i = 0; i < lines.length; i++) {
    if (/^\s*\[/.test(lines[i])) { firstTable = i; break; }
  }

  for (let i = 0; i < firstTable; i++) {
    if (new RegExp(`^\\s*${key}\\s*=`).test(lines[i])) {
      lines[i] = literal;
      return lines.join("\n");
    }
  }

  lines.splice(firstTable, 0, literal);
  return lines.join("\n");
}

/** Finds the [start, end) line range of a provider block. */
function providerRange(lines, id) {
  const header = new RegExp(`^\\s*\\[model_providers\\.(?:"${id}"|${id})\\]\\s*$`);
  let start = -1;
  for (let i = 0; i < lines.length; i++) {
    if (header.test(lines[i])) { start = i; break; }
  }
  if (start === -1) return null;

  let end = lines.length;
  for (let i = start + 1; i < lines.length; i++) {
    if (/^\s*\[/.test(lines[i])) { end = i; break; }
  }
  return { start, end };
}

function providerBlock(id, input) {
  const out = [`[model_providers.${id}]`];
  out.push(`name = ${JSON.stringify(input.name || id)}`);
  out.push(`base_url = ${JSON.stringify(input.baseUrl)}`);
  const wire = WIRE_APIS.includes(input.wireApi) ? input.wireApi : "chat";
  out.push(`wire_api = ${JSON.stringify(wire)}`);
  // A key can live inline or in an environment variable; never both.
  if (input.apiKey) {
    out.push(`experimental_bearer_token = ${JSON.stringify(input.apiKey)}`);
  } else if (input.envKey) {
    out.push(`env_key = ${JSON.stringify(input.envKey)}`);
  }
  return out.join("\n");
}

function validateId(id) {
  return /^[A-Za-z0-9_-]{1,40}$/.test(String(id || ""));
}

function validateUrl(url) {
  return /^https?:\/\/[^\s"']+$/.test(String(url || ""));
}

function upsertProvider(input) {
  const id = String(input.id || "").trim();
  if (!validateId(id)) return { ok: false, error: "ID provider hanya boleh huruf, angka, - dan _" };
  if (!validateUrl(input.baseUrl)) return { ok: false, error: "base_url harus diawali http:// atau https://" };

  const raw = readRaw();
  backup(raw);

  const lines = raw ? raw.split("\n") : [];
  const existing = providerRange(lines, id);

  // Keep the current secret when the app sends the masked placeholder back.
  const merged = Object.assign({}, input);
  if (!merged.apiKey && !merged.envKey && existing) {
    const current = parse(raw).providers[id] || {};
    if (current.experimental_bearer_token) merged.apiKey = current.experimental_bearer_token;
    else if (current.env_key) merged.envKey = current.env_key;
  }

  const block = providerBlock(id, merged).split("\n");
  if (existing) {
    lines.splice(existing.start, existing.end - existing.start, ...block, "");
  } else {
    if (lines.length && lines[lines.length - 1].trim() !== "") lines.push("");
    lines.push(...block, "");
  }

  write(lines.join("\n"));
  return { ok: true, id };
}

function removeProvider(id) {
  if (!validateId(id)) return { ok: false, error: "ID tidak valid" };
  const raw = readRaw();
  if (!raw) return { ok: false, error: "config.toml tidak ada" };

  const current = read();
  if (current.activeProvider === id) {
    return { ok: false, error: "Provider ini sedang aktif. Pindah dulu ke provider lain." };
  }

  backup(raw);
  const lines = raw.split("\n");
  const range = providerRange(lines, id);
  if (!range) return { ok: false, error: "Provider tidak ditemukan" };

  lines.splice(range.start, range.end - range.start);
  write(lines.join("\n"));
  return { ok: true, id };
}

function setActive(input) {
  const raw = readRaw();
  const parsed = parse(raw);

  if (input.provider) {
    if (!parsed.providers[input.provider]) {
      return { ok: false, error: "Provider belum terdaftar di config.toml" };
    }
  }

  backup(raw);
  let text = raw;
  if (input.provider) text = setTopLevelKey(text, "model_provider", input.provider);
  if (input.model) text = setTopLevelKey(text, "model", input.model);
  if (input.reasoningEffort) text = setTopLevelKey(text, "model_reasoning_effort", input.reasoningEffort);
  write(text);

  return { ok: true };
}

module.exports = {
  CONFIG_FILE, WIRE_APIS,
  read, parse, setActive, upsertProvider, removeProvider,
  setTopLevelKey, providerBlock, maskSecret, validateId, validateUrl
};
