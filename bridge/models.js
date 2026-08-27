// Model list for whichever provider Codex is configured against.
//
// Every OpenAI-compatible endpoint exposes GET {base_url}/models, so the list
// follows the provider instead of being hardcoded per engine — which was wrong
// the moment the provider stopped being OpenAI.

const https = require("https");
const http = require("http");
const { URL } = require("url");

const TTL_MS = 5 * 60 * 1000;
const cache = new Map();

function fetchJson(target, headers, timeoutMs) {
  return new Promise(resolve => {
    let url;
    try { url = new URL(target); } catch (e) { return resolve({ error: "base_url tidak valid" }); }

    const client = url.protocol === "http:" ? http : https;
    const req = client.request(url, { method: "GET", headers, timeout: timeoutMs }, res => {
      let body = "";
      res.on("data", chunk => {
        body += chunk;
        if (body.length > 4 * 1024 * 1024) req.destroy();
      });
      res.on("end", () => {
        if (res.statusCode >= 400) {
          return resolve({ error: `HTTP ${res.statusCode} dari provider` });
        }
        try { resolve({ data: JSON.parse(body) }); }
        catch (e) { resolve({ error: "Balasan provider bukan JSON" }); }
      });
    });
    req.on("timeout", () => { req.destroy(); resolve({ error: "Provider tidak menjawab" }); });
    req.on("error", err => resolve({ error: err.message }));
    req.end();
  });
}

/** Accepts OpenAI shape ({data:[{id}]}), Ollama ({models:[{name}]}), and bare arrays. */
function extractIds(payload) {
  let rows = [];
  if (Array.isArray(payload)) {
    rows = payload;
  } else if (Array.isArray(payload && payload.data)) {
    rows = payload.data;
  } else if (Array.isArray(payload && payload.models)) {
    rows = payload.models;
  }
  const ids = [];
  for (const row of rows) {
    const id = typeof row === "string" ? row : (row && (row.id || row.name || row.model));
    if (id && !ids.includes(id)) ids.push(String(id));
  }
  return ids.sort((a, b) => a.localeCompare(b));
}

async function list(provider, force) {
  if (!provider || !provider.baseUrl) return { ok: false, error: "Provider belum punya base_url" };

  const key = provider.baseUrl;
  const hit = cache.get(key);
  if (!force && hit && Date.now() - hit.at < TTL_MS) {
    return { ok: true, models: hit.models, cached: true };
  }

  const headers = { "Accept": "application/json" };
  if (provider.token) headers["Authorization"] = "Bearer " + provider.token;

  const base = provider.baseUrl.replace(/\/+$/, "");
  
  // Try standard /models
  let result = await fetchJson(base + "/models", headers, 10000);
  
  // If failed and base does not end in /v1, try /v1/models
  if (result.error && !base.endsWith("/v1")) {
    const v1Result = await fetchJson(base + "/v1/models", headers, 10000);
    if (!v1Result.error) result = v1Result;
  }

  // If Ollama / custom tags endpoint
  if (result.error || (result.data && !result.data.data && !result.data.models && !Array.isArray(result.data))) {
    const tagResult = await fetchJson(base + "/api/tags", headers, 10000);
    if (!tagResult.error) result = tagResult;
  }

  if (result.error) return { ok: false, error: result.error };

  const models = extractIds(result.data);
  if (!models.length) return { ok: false, error: "Provider tidak mengembalikan daftar model" };

  cache.set(key, { at: Date.now(), models });
  return { ok: true, models };
}

function reset() {
  cache.clear();
}

module.exports = { list, extractIds, reset, TTL_MS };
