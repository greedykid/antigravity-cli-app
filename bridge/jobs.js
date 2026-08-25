const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const config = require("./config");

const JOBS_FILE = path.join(config.CONFIG_DIR, "jobs.json");
const MAX_KEPT = 100;
const jobs = new Map();

function loadPersistedJobs() {
  try {
    if (fs.existsSync(JOBS_FILE)) {
      const data = JSON.parse(fs.readFileSync(JOBS_FILE, "utf8"));
      if (Array.isArray(data)) {
        for (const item of data) {
          if (item && item.id) {
            if (item.state === "running") {
              item.state = "failed";
              item.error = item.error || "Proses terhenti saat server dimuat ulang";
              item.finishedAt = item.finishedAt || Date.now();
            }
            jobs.set(item.id, item);
          }
        }
      }
    }
  } catch (e) {}
}

function persistJobs() {
  try {
    fs.mkdirSync(config.CONFIG_DIR, { recursive: true, mode: 0o700 });
    const arr = Array.from(jobs.values());
    fs.writeFileSync(JOBS_FILE, JSON.stringify(arr), "utf8");
  } catch (e) {}
}

loadPersistedJobs();

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
  persistJobs();
  return job;
}

function finish(id, patch) {
  const job = jobs.get(id);
  if (!job) return null;
  Object.assign(job, patch, { finishedAt: Date.now() });
  if (job.state === "running") job.state = patch.error ? "failed" : "done";
  persistJobs();
  return job;
}

function update(id, patch) {
  const job = jobs.get(id);
  if (!job) return null;
  Object.assign(job, patch);
  persistJobs();
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
  const cutoff = Date.now() - 60 * 60 * 1000;
  return Array.from(jobs.values())
    .filter(j => j.state === "running" && j.createdAt > cutoff)
    .map(summary);
}

// Keep the newest MAX_KEPT so a long-lived server does not grow unbounded.
function prune() {
  if (jobs.size <= MAX_KEPT) return;
  const ordered = Array.from(jobs.values()).sort((a, b) => a.createdAt - b.createdAt);
  for (const job of ordered) {
    if (jobs.size <= MAX_KEPT) break;
    if (job.state !== "running") jobs.delete(job.id);
  }
  persistJobs();
}

function reset() {
  jobs.clear();
  try {
    if (fs.existsSync(JOBS_FILE)) fs.unlinkSync(JOBS_FILE);
  } catch (e) {}
}

module.exports = { create, update, finish, get, list, running, summary, reset, MAX_KEPT };
