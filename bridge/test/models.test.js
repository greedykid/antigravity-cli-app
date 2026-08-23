const { test } = require("node:test");
const assert = require("node:assert");
const models = require("../models");

// Providers differ in how they shape /models; the picker must survive all of it.
test("reads the OpenAI shape", () => {
  const ids = models.extractIds({ data: [{ id: "b/model" }, { id: "a/model" }] });
  assert.deepEqual(ids, ["a/model", "b/model"], "sorted for a searchable list");
});

test("reads a bare array of objects or strings", () => {
  assert.deepEqual(models.extractIds([{ id: "x" }, { id: "y" }]), ["x", "y"]);
  assert.deepEqual(models.extractIds(["y", "x"]), ["x", "y"]);
});

test("falls back to a name field", () => {
  assert.deepEqual(models.extractIds({ data: [{ name: "only-a-name" }] }), ["only-a-name"]);
});

test("drops duplicates and unusable rows", () => {
  const ids = models.extractIds({ data: [{ id: "a" }, { id: "a" }, {}, null, { id: "" }] });
  assert.deepEqual(ids, ["a"]);
});

test("an unexpected payload yields no models rather than throwing", () => {
  assert.deepEqual(models.extractIds(null), []);
  assert.deepEqual(models.extractIds({ error: "nope" }), []);
  assert.deepEqual(models.extractIds("a string"), []);
});

test("a provider without a base_url is reported, not attempted", async () => {
  const r = await models.list({ baseUrl: "" });
  assert.equal(r.ok, false);
  assert.match(r.error, /base_url/);
});

test("an unreachable provider returns an error instead of hanging", async () => {
  models.reset();
  const r = await models.list({ baseUrl: "http://127.0.0.1:9/v1" });
  assert.equal(r.ok, false);
  assert.ok(r.error);
});
