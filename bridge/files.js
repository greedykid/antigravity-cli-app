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

module.exports = { list, read, safeResolve, MAX_READ_BYTES };
