// Provider quota for the Antigravity CLI.
//
// `agy -p "/usage"` answers with the account's real remaining limits. It costs
// a CLI round trip (~6s here) and no model tokens, so the result is cached and
// a stale copy is preferred over failing.

const { spawnSync } = require("child_process");

const TTL_MS = 5 * 60 * 1000;
const TIMEOUT_MS = 45 * 1000;

let cache = null;
let cachedAt = 0;

/**
 * Parses the tab-separated report:
 *   Gemini Models\tWeekly Limit Remaining\t66%\t2026-08-28T22:56:41Z
 * Falls back to whitespace splitting, and ignores anything that does not carry
 * both a percentage and a group name.
 */
function parseUsage(raw) {
  const groups = [];
  const byName = new Map();

  for (const line of String(raw || "").split("\n")) {
    const trimmed = line.trim();
    if (!trimmed) continue;

    const percentMatch = trimmed.match(/(\d{1,3})\s*%/);
    if (!percentMatch) continue;

    let parts = trimmed.split("\t").map(p => p.trim()).filter(Boolean);
    if (parts.length < 3) {
      // No tabs: split on runs of two-or-more spaces so labels keep their words.
      parts = trimmed.split(/\s{2,}/).map(p => p.trim()).filter(Boolean);
    }
    if (parts.length < 3) continue;

    const groupName = parts[0];
    const label = parts[1];
    const percent = Math.max(0, Math.min(100, Number(percentMatch[1])));
    const resetAt = parts.find(p => /^\d{4}-\d{2}-\d{2}T/.test(p)) || null;

    if (!byName.has(groupName)) {
      const group = { group: groupName, limits: [] };
      byName.set(groupName, group);
      groups.push(group);
    }
    byName.get(groupName).limits.push({ label, percent, resetAt });
  }

  return groups;
}

function readQuota(agyBin) {
  const result = spawnSync(agyBin, ["-p", "/usage"], {
    encoding: "utf8",
    timeout: TIMEOUT_MS,
    maxBuffer: 1024 * 1024
  });
  if (result.error || result.status !== 0) return null;

  const groups = parseUsage(result.stdout);
  return groups.length ? groups : null;
}

function get(agyBin, force) {
  const now = Date.now();
  if (!force && cache && now - cachedAt < TTL_MS) {
    return { groups: cache, cachedAt, stale: false };
  }

  let groups = null;
  try {
    groups = readQuota(agyBin);
  } catch (e) {}

  if (groups) {
    cache = groups;
    cachedAt = now;
    return { groups, cachedAt, stale: false };
  }

  // The CLI can be busy or offline; last known numbers beat none at all, as
  // long as the caller is told they are stale.
  if (cache) return { groups: cache, cachedAt, stale: true };
  return { groups: null, cachedAt: 0, stale: false };
}

function reset() {
  cache = null;
  cachedAt = 0;
}

module.exports = { get, parseUsage, reset, TTL_MS };
