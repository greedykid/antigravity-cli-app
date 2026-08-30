// Server-Sent Events hub. Replaces the app's 1-second polling loop:
// the CLI child processes push here as they produce output, so the phone
// sees each step immediately and keeps its radio idle in between.

const clients = new Set();
let nextId = 1;

// Per-jobId ring buffer of recent cli.event / cli.output payloads. Lets a
// freshly-connected client replay the first ~750ms of a job it missed while
// its POST /api/chat was in flight, closing the "events arrived before the
// client set activeJobId" race without making the client guess.
const jobEventBuffer = new Map(); // jobId -> Array<{event, data, at}>
const JOB_BUFFER_LIMIT = 50;
const JOB_BUFFER_TTL_MS = 750;

function bufferFor(jobId) {
  if (!jobId) return null;
  let list = jobEventBuffer.get(jobId);
  if (!list) { list = []; jobEventBuffer.set(jobId, list); }
  return list;
}

function bufferEvent(jobId, event, data) {
  if (!jobId) return;
  if (event !== "cli.event" && event !== "cli.output") return;
  const list = bufferFor(jobId);
  if (!list) return;
  list.push({ event, data: data || {}, at: Date.now() });
  if (list.length > JOB_BUFFER_LIMIT) list.shift();
}

function dropBuffer(jobId) {
  if (jobId) jobEventBuffer.delete(jobId);
}

function addClient(req, res) {
  const url = req.url || "/api/events";
  const queryIdx = url.indexOf("?");
  const query = new URLSearchParams(queryIdx >= 0 ? url.slice(queryIdx + 1) : "");
  const replayJobId = query.get("since");
  const replayFrom = Number(query.get("from")) || 0;

  res.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache, no-transform",
    "Connection": "keep-alive",
    "X-Accel-Buffering": "no",
    "Access-Control-Allow-Origin": "*"
  });

  const client = { id: nextId++, res };
  clients.add(client);

  res.write(`event: hello\ndata: ${JSON.stringify({ clientId: client.id })}\n\n`);

  // If the client asked for events for a specific jobId that we still have
  // buffered, replay them in order so they do not lose the first chunks.
  if (replayJobId) {
    const list = jobEventBuffer.get(replayJobId) || [];
    const cutoff = Math.max(replayFrom, Date.now() - JOB_BUFFER_TTL_MS);
    for (const entry of list) {
      if (entry.at < cutoff) continue;
      try {
        res.write(`event: ${entry.event}\ndata: ${JSON.stringify(entry.data)}\n\n`);
      } catch (e) {}
    }
  }

  // Proxies drop idle connections; a comment line every 15s keeps it warm.
  const heartbeat = setInterval(() => {
    try { res.write(": ping\n\n"); } catch (e) { cleanup(); }
  }, 15000);

  function cleanup() {
    clearInterval(heartbeat);
    clients.delete(client);
    try { res.end(); } catch (e) {}
  }

  req.on("close", cleanup);
  req.on("error", cleanup);
  return client;
}

function broadcast(event, data) {
  const payload = data || {};
  if (payload.jobId && (event === "cli.event" || event === "cli.output")) {
    bufferEvent(payload.jobId, event, payload);
    // Drop the buffer once the run finishes so a new client does not pick
    // up events from a previous job whose id was reused.
    if (event === "task.finished") {
      setTimeout(() => dropBuffer(payload.jobId), JOB_BUFFER_TTL_MS);
    }
  }
  if (clients.size === 0) return;
  const text = `event: ${event}\ndata: ${JSON.stringify(payload)}\n\n`;
  for (const client of Array.from(clients)) {
    try {
      client.res.write(text);
    } catch (e) {
      clients.delete(client);
    }
  }
}

function clientCount() {
  return clients.size;
}

module.exports = { addClient, broadcast, clientCount };
