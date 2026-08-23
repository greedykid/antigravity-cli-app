const http = require("http");
const { spawn, execSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const os = require("os");
const url = require("url");

const PORT = process.env.PORT || 3456;
const TOKEN = process.env.TOKEN || "codex-remote-token-2026";
const WORKDIR = process.env.WORKDIR || "/home/ubuntu";
const AGY_BIN = process.env.AGY_BIN || "/home/ubuntu/.local/bin/agy";
const CODEX_BIN = process.env.CODEX_BIN || "codex";

const UPLOADS_DIR = path.join(WORKDIR, "uploads");
if (!fs.existsSync(UPLOADS_DIR)) {
  fs.mkdirSync(UPLOADS_DIR, { recursive: true });
}

function send(res, code, data) {
  res.writeHead(code, {
    "Content-Type": "application/json",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Authorization, Content-Type",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS"
  });
  res.end(JSON.stringify(data));
}

function authorized(req) {
  return !TOKEN || req.headers.authorization === `Bearer ${TOKEN}`;
}

function getAgyProcess() {
  try {
    const ps = execSync("ps -eo pid,pcpu,pmem,etime,args | grep -E \"[a]gy\" | head -n 1", { encoding: "utf8" }).trim();
    if (!ps) return { running: false };
    const parts = ps.split(/\s+/);
    return {
      running: true,
      pid: parts[0],
      cpu: parts[1] + "%",
      mem: parts[2] + "%",
      uptime: parts[3],
      cmd: parts.slice(4).join(" ")
    };
  } catch (e) {
    return { running: false };
  }
}

function getSessions() {
  const home = os.homedir();
  const file = path.join(home, ".gemini/antigravity-cli/history.jsonl");
  const map = new Map();
  if (fs.existsSync(file)) {
    const lines = fs.readFileSync(file, "utf8").trim().split("\n").filter(Boolean);
    for (let i = lines.length - 1; i >= 0; i--) {
      try {
        const item = JSON.parse(lines[i]);
        if (item.conversationId && !map.has(item.conversationId)) {
          map.set(item.conversationId, {
            conversationId: item.conversationId,
            title: item.display || ("Session " + item.conversationId.slice(0, 8)),
            timestamp: item.timestamp || Date.now(),
            workspace: item.workspace || "/home/ubuntu",
            hostname: os.hostname()
          });
        }
      } catch (e) {}
    }
  }
  return {
    hostname: os.hostname(),
    sessions: Array.from(map.values()).slice(0, 50)
  };
}

function getTranscript(convId, limit = 80) {
  if (!convId) return [];
  const home = os.homedir();
  const file = path.join(home, ".gemini/antigravity-cli/brain", convId, ".system_generated/logs/transcript.jsonl");
  if (!fs.existsSync(file)) return [];
  const lines = fs.readFileSync(file, "utf8").trim().split("\n").filter(Boolean);
  const msgs = [];
  for (const l of lines) {
    try {
      const s = JSON.parse(l);
      if (s.type === "USER_INPUT" && s.content) {
        let text = s.content;
        const m = text.match(/<USER_REQUEST>([\s\S]*?)<\/USER_REQUEST>/);
        if (m) text = m[1].trim();
        msgs.push({ role: "user", content: text, time: s.created_at, index: s.step_index });
      } else if (s.type === "PLANNER_RESPONSE") {
        if (s.thinking && s.thinking.trim()) {
          msgs.push({
            role: "thinking",
            toolTitle: "Thinking",
            title: "Thinking Process",
            command: "Internal Reasoning",
            content: s.thinking.trim(),
            time: s.created_at,
            index: s.step_index
          });
        }
        if (s.tool_calls && s.tool_calls.length) {
          for (const tc of s.tool_calls) {
            const toolName = tc.name || "tool";
            let argsObj = tc.args;
            if (typeof argsObj === "string") {
              try { argsObj = JSON.parse(argsObj); } catch(e) {}
            }
            let argsStr = typeof argsObj === "object" ? JSON.stringify(argsObj, null, 2) : String(argsObj || "");

            let toolTitle = "Action";
            let commandText = "";
            let friendlyTitle = "Aksi: " + toolName;

            if (toolName === "run_command") {
              toolTitle = "Bash";
              commandText = (argsObj && argsObj.CommandLine) || "";
              friendlyTitle = "Menjalankan: " + (commandText.length > 35 ? commandText.slice(0, 32) + "..." : commandText);
            } else if (toolName === "replace_file_content" || toolName === "write_to_file") {
              toolTitle = "Edit file";
              commandText = (argsObj && argsObj.TargetFile) || "";
              friendlyTitle = "Mengedit " + path.basename(commandText);
            } else if (toolName === "view_file") {
              toolTitle = "Read file";
              commandText = (argsObj && argsObj.AbsolutePath) || "";
              friendlyTitle = "Dibaca " + path.basename(commandText);
            } else if (toolName === "grep_search" || toolName === "find_by_name") {
              toolTitle = "Search files";
              commandText = (argsObj && (argsObj.Query || argsObj.Pattern)) || "";
              friendlyTitle = "Mencari file di project...";
            } else if (tc.toolSummary) {
              friendlyTitle = tc.toolSummary;
            }

            msgs.push({
              role: "tool",
              toolName: toolName,
              toolTitle: toolTitle,
              title: friendlyTitle,
              command: commandText || argsStr,
              content: argsStr || ("Action: " + toolName),
              time: s.created_at,
              index: s.step_index
            });
          }
        }
        if (s.content && s.content.trim()) {
          msgs.push({ role: "assistant", content: s.content.trim(), time: s.created_at, index: s.step_index });
        }
      }
    } catch (e) {}
  }
  return msgs.slice(-limit);
}

function runCodex(prompt) {
  return new Promise((resolve, reject) => {
    const child = spawn(CODEX_BIN, ["exec", prompt], {
      cwd: WORKDIR,
      env: process.env
    });
    let output = "";
    let error = "";
    child.stdout.on("data", chunk => { output += chunk.toString(); });
    child.stderr.on("data", chunk => { error += chunk.toString(); });
    const timer = setTimeout(() => {
      child.kill("SIGTERM");
      reject(new Error("Codex timed out after 5 minutes"));
    }, 300000);
    child.on("error", err => { clearTimeout(timer); reject(err); });
    child.on("close", code => {
      clearTimeout(timer);
      if (code === 0) resolve(output.trim());
      else reject(new Error((error || output || `Codex exited with code ${code}`).trim()));
    });
  });
}

function runAgy(prompt, conversationId, resume = true) {
  return new Promise((resolve, reject) => {
    const extraPath = ":/home/ubuntu/.local/bin:/usr/local/bin";
    const env = Object.assign({}, process.env, {
      PATH: (process.env.PATH || "") + extraPath
    });
    const args = [];
    if (conversationId) {
      args.push("--conversation", conversationId);
    } else if (resume) {
      args.push("-c");
    }
    args.push("-p", prompt, "--dangerously-skip-permissions");

    const child = spawn(AGY_BIN, args, {
      cwd: WORKDIR,
      env
    });
    let output = "";
    let error = "";
    child.stdout.on("data", chunk => { output += chunk.toString(); });
    child.stderr.on("data", chunk => { error += chunk.toString(); });
    const timer = setTimeout(() => {
      child.kill("SIGTERM");
      reject(new Error("Antigravity CLI timed out after 5 minutes"));
    }, 300000);
    child.on("error", err => { clearTimeout(timer); reject(err); });
    child.on("close", code => {
      clearTimeout(timer);
      if (code === 0) resolve(output.trim());
      else reject(new Error((error || output || `Antigravity CLI exited with code ${code}`).trim()));
    });
  });
}

const server = http.createServer((req, res) => {
  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;

  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "Authorization, Content-Type",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS"
    });
    return res.end();
  }

  if (req.method === "GET" && (pathname === "/health" || pathname === "/api/health")) {
    return send(res, 200, {
      ok: true,
      hostname: os.hostname(),
      engines: ["antigravity", "codex"],
      features: ["chat", "live_monitor", "session_history", "remote_control", "upload", "multi_upload"]
    });
  }

  if (!authorized(req)) {
    return send(res, 401, { error: "Unauthorized" });
  }

  // POST /api/upload (Upload files and images)
  if (req.method === "POST" && pathname === "/api/upload") {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 50 * 1024 * 1024) req.destroy(); // 50MB max
    });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw);
        const filename = payload.filename || `upload_${Date.now()}.png`;
        const base64Data = payload.data || payload.base64 || "";
        if (!base64Data) {
          return send(res, 400, { error: "data (base64) is required" });
        }
        const buffer = Buffer.from(base64Data, "base64");
        const safeName = `${Date.now()}_${path.basename(filename).replace(/[^a-zA-Z0-9._-]/g, "_")}`;
        const targetPath = path.join(UPLOADS_DIR, safeName);
        fs.writeFileSync(targetPath, buffer);

        send(res, 200, {
          ok: true,
          filename: safeName,
          originalName: filename,
          filePath: targetPath,
          url: "/api/uploads/" + safeName,
          size: buffer.length
        });
      } catch (err) {
        send(res, 500, { error: err.message || "Upload failed" });
      }
    });
    return;
  }

  // GET /api/uploads/:file (Serve uploaded images and files)
  if (req.method === "GET" && (pathname.startsWith("/api/uploads/") || pathname === "/api/upload")) {
    const filename = pathname.startsWith("/api/uploads/") ? path.basename(pathname.replace("/api/uploads/", "")) : parsedUrl.query.file;
    if (!filename) return send(res, 400, { error: "Missing filename" });
    const targetPath = path.join(UPLOADS_DIR, path.basename(filename));
    if (!fs.existsSync(targetPath)) return send(res, 404, { error: "File not found" });
    const ext = path.extname(targetPath).toLowerCase();
    const mimeTypes = {
      ".png": "image/png",
      ".jpg": "image/jpeg",
      ".jpeg": "image/jpeg",
      ".webp": "image/webp",
      ".gif": "image/gif",
      ".svg": "image/svg+xml"
    };
    const contentType = mimeTypes[ext] || "application/octet-stream";
    res.writeHead(200, {
      "Content-Type": contentType,
      "Cache-Control": "public, max-age=86400",
      "Access-Control-Allow-Origin": "*"
    });
    return fs.createReadStream(targetPath).pipe(res);
  }

  // GET /api/session/live
  if (req.method === "GET" && pathname === "/api/session/live") {
    const proc = getAgyProcess();
    const sData = getSessions();
    const latest = (sData.sessions && sData.sessions[0]) || null;
    const turns = latest ? getTranscript(latest.conversationId, 40) : [];
    return send(res, 200, {
      ok: true,
      hostname: sData.hostname,
      process: proc,
      session: latest,
      turns: turns
    });
  }

  // GET /api/sessions
  if (req.method === "GET" && pathname === "/api/sessions") {
    const sData = getSessions();
    return send(res, 200, {
      ok: true,
      hostname: sData.hostname,
      sessions: sData.sessions || []
    });
  }

  // GET /api/session/transcript?id=...
  if (req.method === "GET" && pathname === "/api/session/transcript") {
    const convId = parsedUrl.query.id;
    if (!convId) {
      return send(res, 400, { error: "Missing session id parameter" });
    }
    const msgs = getTranscript(convId, 100);
    const sData = getSessions();
    const foundSession = (sData.sessions || []).find(s => s.conversationId === convId) || { conversationId: convId, title: "Session" };
    return send(res, 200, {
      ok: true,
      conversationId: convId,
      session: foundSession,
      turns: msgs,
      messages: msgs
    });
  }

  // POST /api/session/control (Remote Control Stop / Interrupt)
  if (req.method === "POST" && pathname === "/api/session/control") {
    let raw = "";
    req.on("data", chunk => { raw += chunk; });
    req.on("end", () => {
      try {
        const payload = JSON.parse(raw || "{}");
        const action = payload.action;
        if (action === "stop" || action === "kill") {
          try {
            execSync("pkill -f \"agy -p\" || true");
            return send(res, 200, { ok: true, message: "Process interrupted" });
          } catch(e) {
            return send(res, 200, { ok: true, message: "No running process found" });
          }
        }
        send(res, 400, { error: "Unknown action" });
      } catch (err) {
        send(res, 500, { error: err.message });
      }
    });
    return;
  }

  // POST /api/chat
  if (req.method === "POST" && pathname === "/api/chat") {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 2000000) req.destroy();
    });

    req.on("end", async () => {
      try {
        const payload = JSON.parse(raw);
        let prompt = payload.prompt;
        const engine = (payload.engine || payload.cli || "antigravity").toLowerCase();
        const conversationId = payload.conversationId || payload.session_id || null;
        const resume = payload.resume !== false;

        if (typeof prompt !== "string" || !prompt.trim()) {
          return send(res, 400, { error: "prompt is required" });
        }

        // Support multiple attached files or single file
        if (Array.isArray(payload.attachedFiles) && payload.attachedFiles.length > 0) {
          const fileHeaders = payload.attachedFiles.map(f => `[Attached File: ${f}]`).join("\n");
          prompt = fileHeaders + "\n" + prompt;
        } else if (payload.attachedFile) {
          prompt = `[Attached File: ${payload.attachedFile}]\n` + prompt;
        }

        let response;
        if (engine === "codex") {
          response = await runCodex(prompt.trim());
        } else {
          response = await runAgy(prompt.trim(), conversationId, resume);
        }

        // Get latest transcript turns for 0ms instant UI rendering
        const sData = getSessions();
        const latestSession = (sData.sessions && sData.sessions[0]) || null;
        const activeConvId = conversationId || (latestSession ? latestSession.conversationId : null);
        const updatedTurns = activeConvId ? getTranscript(activeConvId, 40) : [];

        send(res, 200, {
          ok: true,
          response,
          engine,
          conversationId: activeConvId,
          session: latestSession,
          turns: updatedTurns,
          timestamp: new Date().toISOString()
        });
      } catch (err) {
        send(res, 500, { error: err.message || "Failed to execute command" });
      }
    });
    return;
  }

  send(res, 404, { error: "Not found" });
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`[Antigravity Remote Bridge] Listening on http://0.0.0.0:${PORT}`);
  console.log(`[Config] Engine: agy / codex | Workdir: ${WORKDIR}`);
  console.log(`[Security] Token protection: ${TOKEN ? "ENABLED" : "DISABLED"}`);
});
