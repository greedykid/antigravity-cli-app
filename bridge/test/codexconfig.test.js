const { test, beforeEach } = require("node:test");
const assert = require("node:assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

process.env.HOME = fs.mkdtempSync(path.join(os.tmpdir(), "codexcfg-"));
const cfg = require("../codexconfig");

// The real file on this machine, including parts the module must not touch.
const ORIGINAL = `model = "gpt-5.6-luna"
model_provider = "patungin"
model_reasoning_effort = "medium"
approvals_reviewer = "user"

[model_providers.patungin]
name = "Patungin"
base_url = "https://ai.patungin.id/v1"
experimental_bearer_token = "secret-token-value"
wire_api = "responses"

[projects."/home/ubuntu"]
trust_level = "trusted"

[tui.model_availability_nux]
"gpt-5.6-sol" = 4
`;

function seed(text = ORIGINAL) {
  fs.mkdirSync(path.dirname(cfg.CONFIG_FILE), { recursive: true });
  fs.writeFileSync(cfg.CONFIG_FILE, text);
}

beforeEach(() => seed());

test("reads the active provider and model", () => {
  const r = cfg.read();
  assert.equal(r.activeProvider, "patungin");
  assert.equal(r.activeModel, "gpt-5.6-luna");
  assert.equal(r.providers.length, 1);
  assert.equal(r.providers[0].baseUrl, "https://ai.patungin.id/v1");
  assert.equal(r.providers[0].wireApi, "responses");
});

test("never returns the raw API key", () => {
  const p = cfg.read().providers[0];
  assert.equal(p.hasToken, true);
  assert.ok(!p.tokenPreview.includes("token-value"), "secret leaked in preview");
  assert.match(p.tokenPreview, /•/);
  assert.equal(JSON.stringify(cfg.read()).includes("secret-token-value"), false);
});

test("adds a provider without disturbing the rest of the file", () => {
  const before = fs.readFileSync(cfg.CONFIG_FILE, "utf8");
  const res = cfg.upsertProvider({
    id: "openrouter", name: "OpenRouter",
    baseUrl: "https://openrouter.ai/api/v1", wireApi: "chat", apiKey: "sk-or-123"
  });
  assert.equal(res.ok, true);

  const after = fs.readFileSync(cfg.CONFIG_FILE, "utf8");
  // Unrelated tables must survive verbatim.
  assert.ok(after.includes('[projects."/home/ubuntu"]'));
  assert.ok(after.includes('trust_level = "trusted"'));
  assert.ok(after.includes('[tui.model_availability_nux]'));
  assert.ok(after.includes('approvals_reviewer = "user"'));
  assert.ok(before.includes("Patungin") && after.includes("Patungin"));

  const r = cfg.read();
  assert.equal(r.providers.length, 2);
  const or = r.providers.find(p => p.id === "openrouter");
  assert.equal(or.baseUrl, "https://openrouter.ai/api/v1");
  assert.equal(or.wireApi, "chat");
});

test("editing a provider keeps its key when none is supplied", () => {
  cfg.upsertProvider({ id: "patungin", name: "Patungin Baru",
    baseUrl: "https://ai.patungin.id/v2", wireApi: "responses" });
  const text = fs.readFileSync(cfg.CONFIG_FILE, "utf8");
  assert.ok(text.includes("secret-token-value"), "existing key was dropped");
  assert.ok(text.includes("https://ai.patungin.id/v2"));
  assert.equal(cfg.read().providers.length, 1, "editing must not duplicate the block");
});

test("an env key is written instead of an inline token", () => {
  cfg.upsertProvider({ id: "openrouter", baseUrl: "https://openrouter.ai/api/v1",
    wireApi: "chat", envKey: "OPENROUTER_API_KEY" });
  const text = fs.readFileSync(cfg.CONFIG_FILE, "utf8");
  assert.ok(text.includes('env_key = "OPENROUTER_API_KEY"'));
  assert.ok(!/\[model_providers\.openrouter\][\s\S]*?experimental_bearer_token/.test(text));
});

test("rejects a bad id or url instead of writing", () => {
  assert.equal(cfg.upsertProvider({ id: "bad id!", baseUrl: "https://x.dev/v1" }).ok, false);
  assert.equal(cfg.upsertProvider({ id: "ok", baseUrl: "ftp://x.dev" }).ok, false);
  assert.equal(cfg.upsertProvider({ id: "ok", baseUrl: "" }).ok, false);
  assert.equal(fs.readFileSync(cfg.CONFIG_FILE, "utf8"), ORIGINAL, "file changed on invalid input");
});

test("switching the active provider rewrites only that key", () => {
  cfg.upsertProvider({ id: "openrouter", baseUrl: "https://openrouter.ai/api/v1",
    wireApi: "chat", apiKey: "sk-or-123" });
  assert.equal(cfg.setActive({ provider: "openrouter", model: "anthropic/claude-sonnet-4.5" }).ok, true);

  const r = cfg.read();
  assert.equal(r.activeProvider, "openrouter");
  assert.equal(r.activeModel, "anthropic/claude-sonnet-4.5");
  assert.equal(r.reasoningEffort, "medium", "unrelated top-level key was lost");

  const text = fs.readFileSync(cfg.CONFIG_FILE, "utf8");
  assert.equal((text.match(/^model_provider\s*=/gm) || []).length, 1, "duplicate key written");
});

test("refuses to activate a provider that is not defined", () => {
  const res = cfg.setActive({ provider: "nope" });
  assert.equal(res.ok, false);
  assert.equal(cfg.read().activeProvider, "patungin");
});

test("refuses to delete the active provider", () => {
  const res = cfg.removeProvider("patungin");
  assert.equal(res.ok, false);
  assert.ok(fs.readFileSync(cfg.CONFIG_FILE, "utf8").includes("[model_providers.patungin]"));
});

test("deletes an inactive provider and leaves the rest intact", () => {
  cfg.upsertProvider({ id: "openrouter", baseUrl: "https://openrouter.ai/api/v1", wireApi: "chat" });
  assert.equal(cfg.removeProvider("openrouter").ok, true);

  const text = fs.readFileSync(cfg.CONFIG_FILE, "utf8");
  assert.ok(!text.includes("[model_providers.openrouter]"));
  assert.ok(text.includes("[model_providers.patungin]"));
  assert.ok(text.includes('[projects."/home/ubuntu"]'));
});

test("a backup of the previous file is kept before writing", () => {
  cfg.upsertProvider({ id: "openrouter", baseUrl: "https://openrouter.ai/api/v1", wireApi: "chat" });
  assert.equal(fs.readFileSync(cfg.CONFIG_FILE + ".bak", "utf8"), ORIGINAL);
});

test("an unknown wire_api falls back to chat rather than being written through", () => {
  cfg.upsertProvider({ id: "openrouter", baseUrl: "https://openrouter.ai/api/v1", wireApi: "telepathy" });
  assert.equal(cfg.read().providers.find(p => p.id === "openrouter").wireApi, "chat");
});

test("works from an empty config", () => {
  fs.writeFileSync(cfg.CONFIG_FILE, "");
  assert.equal(cfg.upsertProvider({ id: "openrouter", baseUrl: "https://openrouter.ai/api/v1",
    wireApi: "chat", apiKey: "sk" }).ok, true);
  assert.equal(cfg.setActive({ provider: "openrouter", model: "x/y" }).ok, true);
  const r = cfg.read();
  assert.equal(r.activeProvider, "openrouter");
  assert.equal(r.activeModel, "x/y");
});
