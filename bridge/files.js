// Read-only file browser rooted at the workdir. Every path is resolved and
// re-checked against the root so "../.." cannot escape it.

const fs = require("fs");
const path = require("path");

const MAX_READ_BYTES = 512 * 1024;
const SKIP_DIRS = new Set([".git", "node_modules", ".cache", "__pycache__", ".gradle"]);

function safeResolve(root, relative) {
  const rootReal = path.resolve(root);
  const target = path.resolve(rootReal, relative || ".");
  if (target !== rootReal && !target.startsWith(rootReal + path.sep)) {
    return null;
  }
  return target;
}

function list(root, relative) {
  const dir = safeResolve(root, relative);
  if (!dir) return { error: "Path outside workspace" };
  if (!fs.existsSync(dir)) return { error: "Not found" };

  const stat = fs.statSync(dir);
  if (!stat.isDirectory()) return { error: "Not a directory" };

  const entries = [];
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    if (ent.name.startsWith(".") && ent.name !== ".env.example") continue;
    if (ent.isDirectory() && SKIP_DIRS.has(ent.name)) continue;
    let size = 0;
    let mtime = 0;
    try {
      const st = fs.statSync(path.join(dir, ent.name));
      size = st.size;
      mtime = st.mtimeMs;
    } catch (e) {}
    entries.push({
      name: ent.name,
      type: ent.isDirectory() ? "dir" : "file",
      size,
      mtime
    });
  }

  // Directories first, then alphabetical — the order people expect.
  entries.sort((a, b) => {
    if (a.type !== b.type) return a.type === "dir" ? -1 : 1;
    return a.name.localeCompare(b.name);
  });

  const rootReal = path.resolve(root);
  return {
    ok: true,
    path: path.relative(rootReal, dir) || ".",
    parent: dir === rootReal ? null : path.relative(rootReal, path.dirname(dir)) || ".",
    entries
  };
}

function looksBinary(buffer) {
  const sample = buffer.subarray(0, Math.min(buffer.length, 4096));
  for (const byte of sample) {
    if (byte === 0) return true;
  }
  return false;
}

function read(root, relative) {
  const file = safeResolve(root, relative);
  if (!file) return { error: "Path outside workspace" };
  if (!fs.existsSync(file)) return { error: "Not found" };

  const stat = fs.statSync(file);
  if (stat.isDirectory()) return { error: "Is a directory" };

  const fd = fs.openSync(file, "r");
  const buffer = Buffer.alloc(Math.min(stat.size, MAX_READ_BYTES));
  fs.readSync(fd, buffer, 0, buffer.length, 0);
  fs.closeSync(fd);

  if (looksBinary(buffer)) {
    return { ok: true, binary: true, size: stat.size, path: relative, content: "" };
  }

  return {
    ok: true,
    binary: false,
    size: stat.size,
    truncated: stat.size > MAX_READ_BYTES,
    path: relative,
    language: path.extname(file).replace(".", "").toLowerCase(),
    content: buffer.toString("utf8")
  };
}

const MAX_WRITE_BYTES = 2 * 1024 * 1024;

function write(root, relative, content) {
  if (typeof content !== "string") return { error: "content must be a string" };
  if (Buffer.byteLength(content, "utf8") > MAX_WRITE_BYTES) {
    return { error: "File terlalu besar (maks 2 MB)" };
  }

  const file = safeResolve(root, relative);
  if (!file) return { error: "Path outside workspace" };

  if (fs.existsSync(file) && fs.statSync(file).isDirectory()) {
    return { error: "Is a directory" };
  }

  try {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    // Write beside the target then rename, so a failure cannot leave a
    // half-written file where working code used to be.
    const tmp = file + ".codexremote.tmp";
    fs.writeFileSync(tmp, content, "utf8");
    fs.renameSync(tmp, file);
  } catch (e) {
    return { error: e.message };
  }

  return { ok: true, path: relative, size: Buffer.byteLength(content, "utf8") };
}

// Deletes uploads older than the retention window. Returns what it removed so
// the caller can report it rather than silently reclaiming space.
function pruneOlderThan(dir, maxAgeDays) {
  const result = { ok: true, removed: [], freedBytes: 0, kept: 0 };
  if (!maxAgeDays || maxAgeDays <= 0) return Object.assign(result, { skipped: true });
  if (!fs.existsSync(dir)) return result;

  const cutoff = Date.now() - maxAgeDays * 24 * 60 * 60 * 1000;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (!entry.isFile()) continue;
    const full = path.join(dir, entry.name);
    try {
      const st = fs.statSync(full);
      if (st.mtimeMs >= cutoff) {
        result.kept++;
        continue;
      }
      fs.unlinkSync(full);
      result.removed.push(entry.name);
      result.freedBytes += st.size;
    } catch (e) {}
  }
  return result;
}

module.exports = { list, read, write, pruneOlderThan, safeResolve, MAX_READ_BYTES, MAX_WRITE_BYTES };
