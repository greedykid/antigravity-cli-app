// Archived sessions.
//
// Sessions belong to the CLIs, not to this bridge — their transcripts live in
// ~/.codex/sessions and ~/.gemini/antigravity-cli/brain. Archiving therefore
// never touches those files: it only records which ids to hide, so the action
// is reversible and no transcript is ever lost.

const fs = require("fs");
const path = require("path");
const config = require("./config");

const ARCHIVE_FILE = path.join(config.CONFIG_DIR, "archived.json");

function load() {
  try {
    if (fs.existsSync(ARCHIVE_FILE)) {
      const parsed = JSON.parse(fs.readFileSync(ARCHIVE_FILE, "utf8"));
      if (parsed && typeof parsed === "object" && parsed.entries
          && typeof parsed.entries === "object") {
        return parsed;
      }
    }
  } catch (e) {}
  return { version: 1, entries: {} };
}

function persist(state) {
  try {
    fs.mkdirSync(config.CONFIG_DIR, { recursive: true, mode: 0o700 });
    fs.writeFileSync(ARCHIVE_FILE, JSON.stringify(state, null, 2));
    return true;
  } catch (e) {
    return false;
  }
}

function ids() {
  return Object.keys(load().entries);
}

function isArchived(conversationId) {
  if (!conversationId) return false;
  return Boolean(load().entries[conversationId]);
}

function setArchived(conversationId, archived) {
  const id = String(conversationId || "").trim();
  if (!id) return { ok: false, error: "conversationId wajib diisi" };

  const state = load();
  if (archived) {
    state.entries[id] = { at: Date.now() };
  } else {
    delete state.entries[id];
  }

  if (!persist(state)) return { ok: false, error: "Gagal menyimpan daftar arsip" };
  return { ok: true, conversationId: id, archived: Boolean(archived) };
}

/** Tags a session list and, unless asked, drops the archived ones. */
function apply(sessions, includeArchived) {
  const entries = load().entries;
  const tagged = (sessions || []).map(session => Object.assign({}, session, {
    archived: Boolean(entries[session.conversationId])
  }));
  return includeArchived ? tagged : tagged.filter(s => !s.archived);
}

function count() {
  return ids().length;
}

function reset() {
  try { fs.unlinkSync(ARCHIVE_FILE); } catch (e) {}
}

module.exports = { ARCHIVE_FILE, ids, isArchived, setArchived, apply, count, reset, load };
