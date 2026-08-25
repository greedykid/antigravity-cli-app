const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const test = require("node:test");

test("audit entries older than retention are archived", () => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), "audit-retention-"));
  const originalHome = process.env.HOME;
  process.env.HOME = home;
  delete require.cache[require.resolve("../audit")];
  const audit = require("../audit");

  try {
    fs.mkdirSync(home, { recursive: true });
    fs.mkdirSync(path.join(home, ".codex-remote"), { recursive: true, mode: 0o700 });
    const oldAt = new Date(Date.now() - 91 * 24 * 60 * 60 * 1000).toISOString();
    fs.writeFileSync(audit.LOG_FILE, JSON.stringify({ at: oldAt, event: "old" }) + "\n", { mode: 0o600 });
    audit.log("new.event", {});

    const current = fs.readFileSync(audit.LOG_FILE, "utf8").trim().split("\n").filter(Boolean);
    assert.equal(current.length, 1);
    assert.equal(JSON.parse(current[0]).event, "new.event");

    const archived = fs.readFileSync(audit.ARCHIVE_FILE, "utf8").trim().split("\n").filter(Boolean);
    assert.equal(archived.length, 1);
    assert.equal(JSON.parse(archived[0]).event, "old");
  } finally {
    if (originalHome === undefined) delete process.env.HOME;
    else process.env.HOME = originalHome;
    delete require.cache[require.resolve("../audit")];
    fs.rmSync(home, { recursive: true, force: true });
  }
});
