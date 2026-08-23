// Job registry for CLI runs.
//
// /api/chat used to hold the HTTP response open for the entire run, so a task
// could not outlive the request: the tunnel or a flaky phone connection would
// drop it, and a hard 5-minute timeout killed whatever was still working.
// A job keeps running on the server; the client subscribes over SSE and reads
// the result whenever it comes back.

const crypto = require("crypto");

const MAX_KEPT = 100;
const jobs = new Map();

function newId() {
  return crypto.randomBytes(9).toString("base64url");
}

function create(input) {
  const job = {
    id: newId(),
    state: "running",
    engine: input.engine,
    model: input.model || "",
    prompt: input.prompt,
    conversationId: input.conversationId || null,
    createdAt: Date.now(),
    finishedAt: null,
    response: null,
    error: null,
    turns: [],
    session: null
  };
  jobs.set(job.id, job);
  prune();
  return job;
}

function finish(id, patch) {
  const job = jobs.get(id);
  if (!job) return null;
  Object.assign(job, patch, { finishedAt: Date.now() });
  if (job.state === "running") job.state = patch.error ? "failed" : "done";
  return job;
}

function get(id) {
  return jobs.get(id) || null;
}

function list(limit = 20) {
  return Array.from(jobs.values())
    .sort((a, b) => b.createdAt - a.createdAt)
    .slice(0, limit)
    .map(summary);
}

function summary(job) {
  return {
    id: job.id,
    state: job.state,
    engine: job.engine,
    conversationId: job.conversationId,
    prompt: (job.prompt || "").slice(0, 160),
    createdAt: job.createdAt,
    finishedAt: job.finishedAt,
    error: job.error
  };
}

function running() {
  return Array.from(jobs.values()).filter(j => j.state === "running").map(summary);
}

// Keep the newest MAX_KEPT so a long-lived server does not grow unbounded.
function prune() {
  if (jobs.size <= MAX_KEPT) return;
  const ordered = Array.from(jobs.values()).sort((a, b) => a.createdAt - b.createdAt);
  for (const job of ordered) {
    if (jobs.size <= MAX_KEPT) break;
    if (job.state !== "running") jobs.delete(job.id);
  }
}

function reset() {
  jobs.clear();
}

module.exports = { create, finish, get, list, running, summary, reset, MAX_KEPT };
