const { test } = require("node:test");
const assert = require("node:assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

// config reads HOME at require time for CONFIG_DIR, so give it a scratch home.
const scratchHome = fs.mkdtempSync(path.join(os.tmpdir(), "config-home-"));
process.env.HOME = scratchHome;
delete process.env.TOKEN;
delete process.env.REMOTE_TOKEN;

const config = require("../config");

function clearEnv() {
  delete process.env.TOKEN;
  delete process.env.REMOTE_TOKEN;
}

function clearTokenFile() {
  try { fs.unlinkSync(config.TOKEN_FILE); } catch (e) {}
}

test("generates a token on first use and persists it", () => {
  clearEnv();
  clearTokenFile();
  const first = config.loadToken();
  assert.ok(first.length >= 24, "token should be long enough to resist guessing");
  assert.equal(config.loadToken(), first, "second call must reuse the stored token");
  assert.ok(fs.existsSync(config.TOKEN_FILE));
});

test("stores the token with owner-only permissions", () => {
  clearEnv();
  clearTokenFile();
  config.loadToken();
  const mode = fs.statSync(config.TOKEN_FILE).mode & 0o777;
  assert.equal(mode, 0o600, `expected 0600, got ${mode.toString(8)}`);
});

test("an explicit env token wins over the stored one", () => {
  clearEnv();
  clearTokenFile();
  config.loadToken();
  process.env.REMOTE_TOKEN = "a-deliberate-custom-secret";
  assert.equal(config.loadToken(), "a-deliberate-custom-secret");
  clearEnv();
});

// Regression: the shell wrapper exported the retired public default, which
// then went into the pairing QR while the server used the real secret.
test("the retired public default is ignored wherever it comes from", () => {
  clearEnv();
  clearTokenFile();
  const real = config.loadToken();

  process.env.REMOTE_TOKEN = config.LEGACY_TOKEN;
  assert.equal(config.loadToken(), real, "legacy token from REMOTE_TOKEN must not win");

  clearEnv();
  process.env.TOKEN = config.LEGACY_TOKEN;
  assert.equal(config.loadToken(), real, "legacy token from TOKEN must not win");
  clearEnv();
});

test("isLegacyToken recognises exactly the retired default", () => {
  assert.equal(config.isLegacyToken(config.LEGACY_TOKEN), true);
  assert.equal(config.isLegacyToken("something-else"), false);
});

test("rotate replaces the stored token", () => {
  clearEnv();
  clearTokenFile();
  const before = config.loadToken();
  const after = config.rotateToken();
  assert.notEqual(after, before);
  assert.equal(config.loadToken(), after);
});

test("loadToken(false) does not mint a token for read-only callers", () => {
  clearEnv();
  clearTokenFile();
  assert.equal(config.loadToken(false), null);
  assert.equal(fs.existsSync(config.TOKEN_FILE), false);
});

test("bind host defaults to loopback, not every interface", () => {
  delete process.env.BRIDGE_HOST;
  delete process.env.HOST;
  assert.equal(config.bindHost(), "127.0.0.1");
  process.env.BRIDGE_HOST = "0.0.0.0";
  assert.equal(config.bindHost(), "0.0.0.0");
  delete process.env.BRIDGE_HOST;
});

test("workdir accepts either spelling of the env var", () => {
  delete process.env.WORKDIR;
  delete process.env.CODEX_WORKDIR;
  assert.equal(config.workdir(), os.homedir());
  process.env.CODEX_WORKDIR = "/tmp/from-codex-workdir";
  assert.equal(config.workdir(), "/tmp/from-codex-workdir");
  process.env.WORKDIR = "/tmp/from-workdir";
  assert.equal(config.workdir(), "/tmp/from-workdir", "WORKDIR should take precedence");
  delete process.env.WORKDIR;
  delete process.env.CODEX_WORKDIR;
});
