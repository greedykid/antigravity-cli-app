const { test } = require("node:test");
const assert = require("node:assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { execFileSync } = require("child_process");

const git = require("../git");

function makeRepo() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "git-test-"));
  const run = args => execFileSync("git", args, { cwd: root, encoding: "utf8" });
  run(["init", "-q", "-b", "main"]);
  run(["config", "user.email", "test@example.com"]);
  run(["config", "user.name", "Test"]);
  fs.writeFileSync(path.join(root, "tracked.txt"), "one\n");
  run(["add", "-A"]);
  run(["commit", "-q", "-m", "initial: add tracked file"]);
  return root;
}

test("reports a non-repository instead of throwing", () => {
  const empty = fs.mkdtempSync(path.join(os.tmpdir(), "not-git-"));
  const result = git.status(empty);
  assert.equal(result.ok, false);
  assert.equal(result.isRepo, false);
});

test("a fresh repo is clean and on its branch", () => {
  const root = makeRepo();
  const result = git.status(root);
  assert.equal(result.ok, true);
  assert.equal(result.branch, "main");
  assert.equal(result.clean, true);
  assert.equal(result.files.length, 0);
});

// Regression: porcelain output was trimmed as a whole, so the leading space
// of an unstaged modification was eaten and the path lost its first character.
test("keeps the full path of an unstaged modification", () => {
  const root = makeRepo();
  fs.writeFileSync(path.join(root, "tracked.txt"), "two\n");
  const result = git.status(root);
  assert.equal(result.clean, false);
  const entry = result.files.find(f => f.path === "tracked.txt");
  assert.ok(entry, `path was mangled: ${JSON.stringify(result.files)}`);
  assert.equal(entry.index, " ");
  assert.equal(entry.worktree, "M");
});

test("marks untracked files as ??", () => {
  const root = makeRepo();
  fs.writeFileSync(path.join(root, "brand-new.txt"), "hello\n");
  const entry = git.status(root).files.find(f => f.path === "brand-new.txt");
  assert.ok(entry);
  assert.equal(entry.index + entry.worktree, "??");
});

// Regression: commit subjects contain punctuation, so the log fields are
// tab-separated; splitting on anything inside the subject loses data.
test("parses commit subjects containing colons and dashes", () => {
  const root = makeRepo();
  const commits = git.status(root).commits;
  assert.equal(commits.length, 1);
  assert.equal(commits[0].subject, "initial: add tracked file");
  assert.equal(commits[0].author, "Test");
  assert.match(commits[0].hash, /^[0-9a-f]{7,}$/);
});

test("diff shows the change for one file", () => {
  const root = makeRepo();
  fs.writeFileSync(path.join(root, "tracked.txt"), "two\n");
  const result = git.diff(root, "tracked.txt");
  assert.equal(result.ok, true);
  assert.match(result.diff, /^\+two$/m);
  assert.match(result.diff, /^-one$/m);
});

test("commit refuses an empty message and accepts a real one", () => {
  const root = makeRepo();
  fs.writeFileSync(path.join(root, "tracked.txt"), "three\n");
  assert.equal(git.commit(root, "   ", true).ok, false);
  assert.equal(git.commit(root, "update tracked", true).ok, true);
  assert.equal(git.status(root).clean, true);
});

// A commit message is passed as an argv element, never through a shell.
test("a commit message with shell metacharacters is stored literally", () => {
  const root = makeRepo();
  fs.writeFileSync(path.join(root, "tracked.txt"), "four\n");
  const nasty = 'fix; touch /tmp/pwned_$(whoami) && echo "x"';
  assert.equal(git.commit(root, nasty, true).ok, true);
  assert.equal(git.status(root).commits[0].subject, nasty);
});
