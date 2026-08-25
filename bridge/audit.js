// Append-only activity log plus a simple rate limiter.
//
// /api/chat executes arbitrary commands as the server user. Without a record
// there is no way to answer "what ran, and when" after the fact, and without a
// limit a leaked token can be used as fast as the CLI can spawn.

const fs = require("fs");
const path = require("path");
const config = require("./config");

const LOG_FILE = path.join(config.CONFIG_DIR, "audit.jsonl");
const ARCHIVE_FILE = path.join(config.CONFIG_DIR, "audit-archive.jsonl");
const MAX_BYTES = 5 * 1024 * 1024;
const RETENTION_DAYS = 90;

function pruneOldEntries() {
  try {
    if (!fs.existsSync(LOG_FILE)) return;
    const cutoff = Date.now() - RETENTION_DAYS * 24 * 60 * 60 * 1000;
    const lines = fs.readFileSync(LOG_FILE, "utf8").split("\n").filter(Boolean);
    const kept = [];
    let archived = false;
    for (const line of lines) {
      let entryAt = null;
      try { entryAt = new Date(JSON.parse(line).at).getTime(); } catch {}
      if (entryAt && entryAt < cutoff) {
        if (!archived) {
          fs.appendFileSync(ARCHIVE_FILE, "", { mode: 0o600 });
          archived = true;
        }
        fs.appendFileSync(ARCHIVE_FILE, line + "\n", { mode: 0o600 });
      } else {
        kept.push(line);
      }
    }
    if (archived) fs.writeFileSync(LOG_FILE, kept.join("\n") + (kept.length ? "\n" : ""), { mode: 0o600 });
  } catch {}
}

function log(event, details) {
  try {
    fs.mkdirSync(config.CONFIG_DIR, { recursive: true, mode: 0o700 });
    rotateIfLarge();
    pruneOldEntries();
    const line = JSON.stringify(Object.assign({
      at: new Date().toISOString(),
      event
    }, details || {}));
    fs.appendFileSync(LOG_FILE, line + "\n", { mode: 0o600 });
  } catch (e) {}
}

// One rotation is enough: keep the previous file, drop anything older.
function rotateIfLarge() {
  try {
    if (!fs.existsSync(LOG_FILE)) return;
    if (fs.statSync(LOG_FILE).size < MAX_BYTES) return;
    fs.renameSync(LOG_FILE, LOG_FILE + ".1");
  } catch (e) {}
}

function read(limit = 200) {
  try {
    if (!fs.existsSync(LOG_FILE)) return [];
    const lines = fs.readFileSync(LOG_FILE, "utf8").trim().split("\n").filter(Boolean);
    return lines.slice(-limit).reverse().map(line => {
      try { return JSON.parse(line); } catch (e) { return { raw: line }; }
    });
  } catch (e) {
    return [];
  }
}

// Fixed-window counter per bucket. Deliberately in-memory: a restart clearing
// it is fine, and it keeps the hot path free of disk writes.
const windows = new Map();

function rateLimit(bucket, limit, windowMs) {
  const now = Date.now();
  const entry = windows.get(bucket);

  if (!entry || now - entry.start >= windowMs) {
    windows.set(bucket, { start: now, count: 1 });
    return { allowed: true, remaining: limit - 1, retryAfterMs: 0 };
  }

  if (entry.count >= limit) {
    return { allowed: false, remaining: 0, retryAfterMs: entry.start + windowMs - now };
  }

  entry.count += 1;
  return { allowed: true, remaining: limit - entry.count, retryAfterMs: 0 };
}

function resetLimits() {
  windows.clear();
}

module.exports = { log, read, rateLimit, resetLimits, LOG_FILE, ARCHIVE_FILE, RETENTION_DAYS };
