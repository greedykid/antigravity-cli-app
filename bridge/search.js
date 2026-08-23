// Transcript search index.
//
// The first implementation re-read and re-parsed every transcript on every
// query. That is fine for a handful of sessions and quadratic misery once the
// history grows. This keeps a cached, lowercased copy of each session's text
// and only rebuilds an entry when its source file has actually changed.

const fs = require("fs");
const path = require("path");
const config = require("./config");

const INDEX_FILE = path.join(config.CONFIG_DIR, "search-index.json");
const MAX_TEXT_PER_SESSION = 200 * 1024;

let index = null;

function load() {
  if (index) return index;
  try {
    if (fs.existsSync(INDEX_FILE)) {
      index = JSON.parse(fs.readFileSync(INDEX_FILE, "utf8"));
      if (index && typeof index === "object" && index.entries) return index;
    }
  } catch (e) {}
  index = { version: 1, entries: {} };
  return index;
}

function persist() {
  try {
    fs.mkdirSync(config.CONFIG_DIR, { recursive: true, mode: 0o700 });
    fs.writeFileSync(INDEX_FILE, JSON.stringify(index));
  } catch (e) {}
}

function sourceStamp(sourceFiles) {
  let stamp = 0;
  for (const file of sourceFiles || []) {
    try {
      if (fs.existsSync(file)) stamp = Math.max(stamp, fs.statSync(file).mtimeMs);
    } catch (e) {}
  }
  return stamp;
}

/**
 * Refreshes one session's entry when its transcript changed.
 * `loadTurns` is only called on a miss, which is the whole point.
 */
function sync(session, sourceFiles, loadTurns) {
  const data = load();
  const id = session.conversationId;
  if (!id) return null;

  const stamp = sourceStamp(sourceFiles);
  const existing = data.entries[id];
  if (existing && existing.stamp === stamp && existing.title === session.title) {
    return existing;
  }

  let text = "";
  try {
    for (const turn of loadTurns() || []) {
      if (typeof turn.content !== "string" || !turn.content) continue;
      text += turn.role + " " + turn.content + "\n";
      if (text.length > MAX_TEXT_PER_SESSION) break;
    }
  } catch (e) {}

  const entry = {
    conversationId: id,
    title: session.title || "Sesi",
    engine: session.engine || "antigravity",
    timestamp: session.timestamp || Date.now(),
    stamp,
    text: text.slice(0, MAX_TEXT_PER_SESSION).toLowerCase()
  };
  data.entries[id] = entry;
  return entry;
}

function dropMissing(validIds) {
  const data = load();
  const keep = new Set(validIds);
  for (const id of Object.keys(data.entries)) {
    if (!keep.has(id)) delete data.entries[id];
  }
}

function query(needleRaw, limit) {
  const data = load();
  const needle = (needleRaw || "").trim().toLowerCase();
  if (!needle) return [];

  const results = [];
  const entries = Object.values(data.entries)
    .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));

  for (const entry of entries) {
    if (results.length >= limit) break;

    const inTitle = (entry.title || "").toLowerCase().includes(needle);
    const at = inTitle ? -1 : entry.text.indexOf(needle);
    if (!inTitle && at === -1) continue;

    let snippet = entry.title;
    if (!inTitle) {
      const start = Math.max(0, at - 60);
      snippet = (start > 0 ? "..." : "")
        + entry.text.slice(start, at + needle.length + 90).replace(/\s+/g, " ").trim();
    }

    results.push({
      conversationId: entry.conversationId,
      title: entry.title,
      engine: entry.engine,
      timestamp: entry.timestamp,
      matchIn: inTitle ? "title" : "message",
      snippet
    });
  }
  return results;
}

function stats() {
  const data = load();
  return { sessions: Object.keys(data.entries).length, file: INDEX_FILE };
}

function reset() {
  index = { version: 1, entries: {} };
}

module.exports = { sync, dropMissing, query, persist, stats, reset, INDEX_FILE };
