// Server-Sent Events hub. Replaces the app's 1-second polling loop:
// the CLI child processes push here as they produce output, so the phone
// sees each step immediately and keeps its radio idle in between.

const clients = new Set();
let nextId = 1;

function addClient(req, res) {
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
  if (clients.size === 0) return;
  const payload = `event: ${event}\ndata: ${JSON.stringify(data || {})}\n\n`;
  for (const client of Array.from(clients)) {
    try {
      client.res.write(payload);
    } catch (e) {
      clients.delete(client);
    }
  }
}

function clientCount() {
  return clients.size;
}

module.exports = { addClient, broadcast, clientCount };
