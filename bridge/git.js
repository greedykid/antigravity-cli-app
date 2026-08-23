// Git panel backend. Commands are spawned with argument arrays and never a
// shell string, so branch names and commit messages cannot inject anything.

const { spawnSync } = require("child_process");
const fs = require("fs");
const path = require("path");

function run(cwd, args, timeout = 20000) {
  const result = spawnSync("git", args, {
    cwd,
    encoding: "utf8",
    timeout,
    maxBuffer: 8 * 1024 * 1024
  });
  return {
    code: result.status === null ? -1 : result.status,
    stdout: (result.stdout || "").trim(),
    // Porcelain status encodes state in the first two columns, so a leading
    // space is data — keep an untrimmed copy for parsers that need it.
    raw: result.stdout || "",
    stderr: (result.stderr || "").trim()
  };
}

function isRepo(cwd) {
  if (!fs.existsSync(path.join(cwd, ".git"))) {
    const probe = run(cwd, ["rev-parse", "--is-inside-work-tree"]);
    return probe.code === 0 && probe.stdout === "true";
  }
  return true;
}

function status(cwd) {
  if (!isRepo(cwd)) return { ok: false, isRepo: false, error: "Not a git repository" };

  const branch = run(cwd, ["rev-parse", "--abbrev-ref", "HEAD"]).stdout || "HEAD";
  const porcelain = run(cwd, ["status", "--porcelain=v1"]).raw;
  const ahead = run(cwd, ["rev-list", "--count", "@{u}..HEAD"]);
  const behind = run(cwd, ["rev-list", "--count", "HEAD..@{u}"]);

  const files = porcelain
    .split("\n")
    .filter(Boolean)
    .map(line => ({
      // Porcelain v1: two status chars, a space, then the path.
      index: line[0],
      worktree: line[1],
      path: line.slice(3).replace(/\s+$/, "")
    }));

  // Tab-separated so the fields survive commit subjects containing punctuation.
  const log = run(cwd, ["log", "-8", "--pretty=format:%h%x09%s%x09%an%x09%ar"]).stdout;
  const commits = log
    .split("\n")
    .filter(Boolean)
    .map(line => {
      const [hash, subject, author, when] = line.split("\t");
      return { hash, subject, author, when };
    });

  return {
    ok: true,
    isRepo: true,
    branch,
    ahead: ahead.code === 0 ? Number(ahead.stdout || 0) : 0,
    behind: behind.code === 0 ? Number(behind.stdout || 0) : 0,
    hasUpstream: ahead.code === 0,
    files,
    commits,
    clean: files.length === 0
  };
}

function diff(cwd, file) {
  if (!isRepo(cwd)) return { ok: false, error: "Not a git repository" };

  const args = ["diff", "--no-color"];
  if (file) args.push("--", file);
  const unstaged = run(cwd, args);

  const stagedArgs = ["diff", "--no-color", "--cached"];
  if (file) stagedArgs.push("--", file);
  const staged = run(cwd, stagedArgs);

  let untracked = "";
  if (file) {
    const tracked = run(cwd, ["ls-files", "--error-unmatch", "--", file]);
    if (tracked.code !== 0) {
      // Show a new file as an all-additions diff instead of nothing at all.
      const shown = run(cwd, ["diff", "--no-color", "--no-index", "/dev/null", file]);
      untracked = shown.stdout;
    }
  }

  return {
    ok: true,
    file: file || null,
    diff: [staged.stdout, unstaged.stdout, untracked].filter(Boolean).join("\n")
  };
}

function commit(cwd, message, addAll) {
  if (!isRepo(cwd)) return { ok: false, error: "Not a git repository" };
  if (!message || !message.trim()) return { ok: false, error: "Commit message is required" };

  if (addAll) {
    const added = run(cwd, ["add", "-A"]);
    if (added.code !== 0) return { ok: false, error: added.stderr || "git add failed" };
  }

  const result = run(cwd, ["commit", "-m", message.trim()]);
  if (result.code !== 0) {
    return { ok: false, error: result.stderr || result.stdout || "git commit failed" };
  }
  return { ok: true, output: result.stdout };
}

function push(cwd) {
  if (!isRepo(cwd)) return { ok: false, error: "Not a git repository" };
  const result = run(cwd, ["push"], 90000);
  if (result.code !== 0) {
    return { ok: false, error: result.stderr || result.stdout || "git push failed" };
  }
  return { ok: true, output: result.stdout || result.stderr };
}

module.exports = { status, diff, commit, push, isRepo };
