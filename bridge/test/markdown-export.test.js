const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawn } = require("node:child_process");
const http = require("node:http");
const test = require("node:test");

// These tests cover the GFM→HTML converter that drives /api/session/html
// for assistant turns. They boot the bridge with TOKEN=test-token, seed a
// session, and call the export route.

function request(port, pathname, method, token, body) {
  return new Promise((resolve, reject) => {
    const req = http.request({
      host: "127.0.0.1", port, path: pathname, method,
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    }, res => {
      let raw = "";
      res.setEncoding("utf8");
      res.on("data", chunk => raw += chunk);
      res.on("end", () => {
        try { resolve({ status: res.statusCode, body: JSON.parse(raw) }); }
        catch { resolve({ status: res.statusCode, body: raw }); }
      });
    });
    req.on("error", reject);
    req.end(body ? JSON.stringify(body) : undefined);
  });
}

function startBridge(home, workdir, port) {
  return spawn(process.execPath, ["server.js"], {
    cwd: path.join(__dirname, ".."),
    env: { ...process.env, HOME: home, WORKDIR: workdir, PORT: String(port), TOKEN: "test-token" },
    stdio: ["ignore", "inherit", "inherit"]
  });
}

async function waitUntilReady(port) {
  for (let attempt = 0; attempt < 50; attempt++) {
    try {
      const r = await request(port, "/health", "GET", "");
      if (r.body && r.body.features) return;
    } catch { /* retry */ }
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  throw new Error("bridge did not start");
}

// The markdown converter is internal to server.js; the export route is the
// only public surface that exercises it, so we drive a fake assistant turn
// through /api/session/html and assert on the returned HTML.
test("assistant markdown table renders as <table> with aligned columns", async (t) => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), "codex-md-home-"));
  const workdir = fs.mkdtempSync(path.join(os.tmpdir(), "codex-md-work-"));
  const port = 18800;
  const child = startBridge(home, workdir, port);
  t.after(() => {
    child.kill("SIGTERM");
    fs.rmSync(home, { recursive: true, force: true });
    fs.rmSync(workdir, { recursive: true, force: true });
  });
  await waitUntilReady(port);

  // Seed a session by writing a transcript file the bridge can read. The
  // simplest portable transcript is the antigravity brain layout, which
  // getTranscript already parses; we use it directly here.
  const convId = "tableconv1234";
  const brainDir = path.join(home, ".gemini/antigravity-cli/brain", convId, ".system_generated/logs");
  fs.mkdirSync(brainDir, { recursive: true });
  const tableMd = [
    "Here is the summary:",
    "",
    "| Name  | Score |",
    "| :---- | ----: |",
    "| Alice |   100 |",
    "| Bob   |    42 |"
  ].join("\n");
  const transcriptLines = [
    JSON.stringify({ type: "USER_INPUT", content: "<USER_REQUEST>show table</USER_REQUEST>", step_index: "1" }),
    JSON.stringify({
      type: "PLANNER_RESPONSE",
      content: tableMd,
      thinking: "",
      tool_calls: [],
      step_index: "2"
    })
  ];
  fs.writeFileSync(path.join(brainDir, "transcript.jsonl"), transcriptLines.join("\n"));

  const html = await request(port, `/api/session/html?id=${encodeURIComponent(convId)}`, "GET", "test-token", null);
  assert.equal(html.status, 200);
  assert.ok(html.body && typeof html.body.html === "string", "response should be {ok, html}");
  const htmlStr = html.body.html;
  assert.match(htmlStr, /<table>/);
  assert.match(htmlStr, /<thead>/);
  assert.match(htmlStr, /<tbody>/);
  // Both columns get an explicit width so they line up.
  const colMatches = [...htmlStr.matchAll(/<col style="width:(\d+)px">/g)].map(m => Number(m[1]));
  assert.ok(colMatches.length >= 2, `expected at least two <col> widths, got ${colMatches.length}`);
  for (const w of colMatches) {
    assert.ok(w >= 60 && w <= 360, `column width ${w}px outside clamp`);
  }
  // Right-aligned column from the separator (---:) must carry text-align:right.
  assert.match(htmlStr, /text-align:right/);
  // Left-aligned column (`:---`) must carry text-align:left.
  assert.match(htmlStr, /text-align:left/);
  // The assistant bubble must not wrap the table in <pre><code>.
  assert.doesNotMatch(htmlStr, /<pre><code>[\s\S]*?<table>/);
});

test("plain prose falls back to the original <pre><code> wrapper", async (t) => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), "codex-md2-home-"));
  const workdir = fs.mkdtempSync(path.join(os.tmpdir(), "codex-md2-work-"));
  const port = 18801;
  const child = startBridge(home, workdir, port);
  t.after(() => {
    child.kill("SIGTERM");
    fs.rmSync(home, { recursive: true, force: true });
    fs.rmSync(workdir, { recursive: true, force: true });
  });
  await waitUntilReady(port);

  const convId = "plainconv1234";
  const brainDir = path.join(home, ".gemini/antigravity-cli/brain", convId, ".system_generated/logs");
  fs.mkdirSync(brainDir, { recursive: true });
  const transcriptLines = [
    JSON.stringify({ type: "USER_INPUT", content: "<USER_REQUEST>hi</USER_REQUEST>", step_index: "1" }),
    JSON.stringify({
      type: "PLANNER_RESPONSE",
      content: "Just a single line of plain prose, no markdown markers here.",
      step_index: "2"
    })
  ];
  fs.writeFileSync(path.join(brainDir, "transcript.jsonl"), transcriptLines.join("\n"));

  const html = await request(port, `/api/session/html?id=${encodeURIComponent(convId)}`, "GET", "test-token", null);
  assert.equal(html.status, 200);
  assert.ok(html.body && typeof html.body.html === "string");
  // The plain prose assistant turn keeps the legacy <pre><code> wrapper.
  assert.match(html.body.html, /<pre><code>Just a single line of plain prose/);
});

test("renderMarkdown handles headings, bold, code, links", async (t) => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), "codex-md3-home-"));
  const workdir = fs.mkdtempSync(path.join(os.tmpdir(), "codex-md3-work-"));
  const port = 18802;
  const child = startBridge(home, workdir, port);
  t.after(() => {
    child.kill("SIGTERM");
    fs.rmSync(home, { recursive: true, force: true });
    fs.rmSync(workdir, { recursive: true, force: true });
  });
  await waitUntilReady(port);

  const convId = "mdconv12345";
  const brainDir = path.join(home, ".gemini/antigravity-cli/brain", convId, ".system_generated/logs");
  fs.mkdirSync(brainDir, { recursive: true });
  const rich = "# Title\n\nSome **bold**, *italic* and `code`.\n\nSee [the docs](https://example.com).\n\n- item one\n- item two";
  const transcriptLines = [
    JSON.stringify({ type: "USER_INPUT", content: "<USER_REQUEST>rich</USER_REQUEST>", step_index: "1" }),
    JSON.stringify({ type: "PLANNER_RESPONSE", content: rich, step_index: "2" })
  ];
  fs.writeFileSync(path.join(brainDir, "transcript.jsonl"), transcriptLines.join("\n"));

  const html = await request(port, `/api/session/html?id=${encodeURIComponent(convId)}`, "GET", "test-token", null);
  assert.equal(html.status, 200);
  const htmlStr = html.body.html;
  assert.match(htmlStr, /<h1>Title<\/h1>/);
  assert.match(htmlStr, /<strong>bold<\/strong>/);
  assert.match(htmlStr, /<em>italic<\/em>/);
  assert.match(htmlStr, /<code>code<\/code>/);
  assert.match(htmlStr, /<a href="https:\/\/example\.com">/);
  assert.match(htmlStr, /<ul>/); // bullet list
  assert.match(htmlStr, /<li>item one<\/li>/);
});