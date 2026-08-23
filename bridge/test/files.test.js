const { test } = require("node:test");
const assert = require("node:assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

const files = require("../files");

function makeRoot() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "files-test-"));
  fs.mkdirSync(path.join(root, "src"));
  fs.writeFileSync(path.join(root, "src", "main.js"), "console.log('hi')\n");
  fs.writeFileSync(path.join(root, "readme.md"), "# Title\n");
  fs.writeFileSync(path.join(root, ".hidden"), "secret\n");
  fs.mkdirSync(path.join(root, "node_modules"));
  fs.writeFileSync(path.join(root, "binary.bin"), Buffer.from([0x00, 0x01, 0x02, 0x00]));
  return root;
}

test("lists directories first, then files alphabetically", () => {
  const root = makeRoot();
  const result = files.list(root, ".");
  assert.equal(result.ok, true);
  const names = result.entries.map(e => e.name);
  assert.equal(names[0], "src", "directory should sort before files");
  assert.ok(names.includes("readme.md"));
});

test("hides dotfiles and heavy tool directories", () => {
  const root = makeRoot();
  const names = files.list(root, ".").entries.map(e => e.name);
  assert.ok(!names.includes(".hidden"));
  assert.ok(!names.includes("node_modules"));
});

test("refuses to escape the root with ..", () => {
  const root = makeRoot();
  for (const attempt of ["../..", "../../etc", "src/../../..", "/etc"]) {
    const result = files.list(root, attempt);
    assert.ok(result.error, `"${attempt}" must be rejected, got ${JSON.stringify(result)}`);
  }
});

test("safeResolve returns null outside the root and a path inside it", () => {
  const root = makeRoot();
  assert.equal(files.safeResolve(root, "../.."), null);
  assert.ok(files.safeResolve(root, "src").endsWith("src"));
});

test("a path whose name merely starts with the root is still rejected", () => {
  const root = makeRoot();
  assert.equal(files.safeResolve(root, "../" + path.basename(root) + "-evil"), null);
});

test("reads text files and reports the language", () => {
  const root = makeRoot();
  const result = files.read(root, "src/main.js");
  assert.equal(result.ok, true);
  assert.equal(result.binary, false);
  assert.equal(result.language, "js");
  assert.match(result.content, /console\.log/);
});

test("flags binary files instead of returning garbage", () => {
  const root = makeRoot();
  const result = files.read(root, "binary.bin");
  assert.equal(result.binary, true);
  assert.equal(result.content, "");
});

test("reading a missing file reports an error, not a crash", () => {
  const root = makeRoot();
  assert.ok(files.read(root, "nope.txt").error);
});
