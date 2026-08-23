const { test, beforeEach } = require("node:test");
const assert = require("node:assert");
const jobs = require("../jobs");

beforeEach(() => jobs.reset());

test("a new job starts in the running state with an id", () => {
  const job = jobs.create({ engine: "codex", prompt: "hello" });
  assert.equal(job.state, "running");
  assert.ok(job.id.length > 8);
  assert.equal(jobs.get(job.id).prompt, "hello");
});

test("ids are unique across jobs", () => {
  const ids = new Set();
  for (let i = 0; i < 50; i++) ids.add(jobs.create({ engine: "codex", prompt: "p" }).id);
  assert.equal(ids.size, 50);
});

test("finishing stores the response and marks it done", () => {
  const job = jobs.create({ engine: "codex", prompt: "hello" });
  jobs.finish(job.id, { response: "world", conversationId: "abc" });
  const done = jobs.get(job.id);
  assert.equal(done.state, "done");
  assert.equal(done.response, "world");
  assert.equal(done.conversationId, "abc");
  assert.ok(done.finishedAt >= done.createdAt);
});

test("finishing with an error marks it failed", () => {
  const job = jobs.create({ engine: "codex", prompt: "hello" });
  jobs.finish(job.id, { error: "boom" });
  assert.equal(jobs.get(job.id).state, "failed");
});

test("finishing an unknown id is a no-op, not a crash", () => {
  assert.equal(jobs.finish("does-not-exist", { response: "x" }), null);
});

test("running() lists only unfinished jobs", () => {
  const a = jobs.create({ engine: "codex", prompt: "a" });
  jobs.create({ engine: "codex", prompt: "b" });
  jobs.finish(a.id, { response: "done" });
  const running = jobs.running();
  assert.equal(running.length, 1);
  assert.equal(running[0].prompt, "b");
});

test("summaries truncate the prompt instead of shipping the whole thing", () => {
  const job = jobs.create({ engine: "codex", prompt: "x".repeat(500) });
  assert.ok(jobs.summary(job).prompt.length <= 160);
});

test("pruning keeps the cap but never drops a running job", () => {
  const keepRunning = jobs.create({ engine: "codex", prompt: "long task" });
  for (let i = 0; i < jobs.MAX_KEPT + 30; i++) {
    const j = jobs.create({ engine: "codex", prompt: "p" + i });
    jobs.finish(j.id, { response: "ok" });
  }
  assert.ok(jobs.get(keepRunning.id), "a running job must survive pruning");
  assert.ok(jobs.list(1000).length <= jobs.MAX_KEPT + 1);
});

// Regression: a brand-new chat has no conversation id until the CLI creates
// one. The client needs it mid-run to tell its own transcript apart from
// whatever else last ran on the server, so the runner records it immediately
// rather than only at completion.
test("update records the conversation id while the job is still running", () => {
  const job = jobs.create({ engine: "codex", prompt: "start something" });
  assert.equal(job.conversationId, null);

  jobs.update(job.id, { conversationId: "conv-from-thread-started" });

  const live = jobs.get(job.id);
  assert.equal(live.state, "running", "recording the id must not finish the job");
  assert.equal(live.conversationId, "conv-from-thread-started");
});

test("update leaves other fields alone", () => {
  const job = jobs.create({ engine: "codex", prompt: "keep me" });
  jobs.update(job.id, { conversationId: "abc" });
  assert.equal(jobs.get(job.id).prompt, "keep me");
  assert.equal(jobs.get(job.id).engine, "codex");
});

test("update on an unknown id is a no-op", () => {
  assert.equal(jobs.update("nope", { conversationId: "x" }), null);
});

test("a resumed job keeps the conversation id it was created with", () => {
  const job = jobs.create({ engine: "codex", prompt: "p", conversationId: "existing" });
  assert.equal(job.conversationId, "existing");
});

test("the id survives into the finished job", () => {
  const job = jobs.create({ engine: "codex", prompt: "p" });
  jobs.update(job.id, { conversationId: "mid-run-id" });
  jobs.finish(job.id, { response: "done" });
  assert.equal(jobs.get(job.id).conversationId, "mid-run-id");
  assert.equal(jobs.get(job.id).state, "done");
});

test("summaries carry the conversation id so a client can match its own job", () => {
  const job = jobs.create({ engine: "codex", prompt: "p" });
  jobs.update(job.id, { conversationId: "match-me" });
  assert.equal(jobs.summary(jobs.get(job.id)).conversationId, "match-me");
  assert.equal(jobs.running()[0].conversationId, "match-me");
});
