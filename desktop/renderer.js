let SERVER_URL = normalizeServerUrl(localStorage.getItem('antigravity_server_url') || 'http://127.0.0.1:18790');
let SERVER_TOKEN = localStorage.getItem('antigravity_server_token') || '';
let activeConversationId = null;
let activeSessionTitle = 'Sesi Baru';
let currentEngine = 'antigravity';
let currentModel = 'auto';
let isTaskRunning = false;
let sseSource = null;
let currentDrawerMode = 'files'; // 'files' or 'terminal'
let activeEditingPath = null;

// Smart URL Normalizer
function normalizeServerUrl(rawInput) {
  if (!rawInput) return 'http://127.0.0.1:18790';
  let s = rawInput.trim();

  // If user pasted a JSON pairing object
  if (s.startsWith('{') && s.endsWith('}')) {
    try {
      const obj = JSON.parse(s);
      if (obj.url) s = obj.url;
      if (obj.token) {
        SERVER_TOKEN = obj.token;
        localStorage.setItem('antigravity_server_token', SERVER_TOKEN);
        const tokenInput = document.getElementById('inputServerToken');
        if (tokenInput) tokenInput.value = SERVER_TOKEN;
      }
    } catch (e) {}
  }

  // If user pasted custom scheme e.g. codexremote://host:port?token=xyz or antigravity://...
  if (s.startsWith('codexremote://') || s.startsWith('antigravity://')) {
    s = s.replace(/^(?:codexremote|antigravity):\/\//i, 'http://');
  }

  // Ensure protocol
  if (!s.startsWith('http://') && !s.startsWith('https://')) {
    if (s.includes('trycloudflare.com') || s.includes('ngrok.io') || s.includes('loca.lt') || s.includes('.app') || s.includes('.dev')) {
      s = 'https://' + s;
    } else {
      s = 'http://' + s;
    }
  }

  try {
    const u = new URL(s);
    // If URL contains query params for token (e.g. ?token=...)
    const urlToken = u.searchParams.get('token') || u.searchParams.get('key');
    if (urlToken) {
      SERVER_TOKEN = urlToken;
      localStorage.setItem('antigravity_server_token', SERVER_TOKEN);
      const tokenInput = document.getElementById('inputServerToken');
      if (tokenInput) tokenInput.value = SERVER_TOKEN;
    }

    // Keep origin only (strip /api/chat, /api/health, /api/sessions, trailing slashes)
    return u.origin;
  } catch (e) {
    // Fallback regex cleanup
    return s.replace(/\/api(?:\/[a-zA-Z0-9_\-]+)*\/?$/i, '').replace(/\/+$/, '');
  }
}

// DOM Elements
const serverStatusBadge = document.getElementById('serverStatusBadge');
const sessionsList = document.getElementById('sessionsList');
const sessionSearchInput = document.getElementById('sessionSearchInput');
const currentSessionTitle = document.getElementById('currentSessionTitle');
const currentSessionTag = document.getElementById('currentSessionTag');
const messagesList = document.getElementById('messagesList');
const emptyState = document.getElementById('emptyState');
const promptInput = document.getElementById('promptInput');
const btnSend = document.getElementById('btnSend');
const btnNewSession = document.getElementById('btnNewSession');
const btnRenameSession = document.getElementById('btnRenameSession');
const btnServerConfig = document.getElementById('btnServerConfig');
const engineSelectorBtn = document.getElementById('engineSelectorBtn');
const currentEngineName = document.getElementById('currentEngineName');
const currentModelBadge = document.getElementById('currentModelBadge');
const slashPopup = document.getElementById('slashPopup');
const liveTaskBanner = document.getElementById('liveTaskBanner');
const btnCancelTask = document.getElementById('btnCancelTask');
const btnToggleWorkspace = document.getElementById('btnToggleWorkspace');
const btnToggleTerminal = document.getElementById('btnToggleTerminal');
const btnExportSession = document.getElementById('btnExportSession');
const rightDrawer = document.getElementById('rightDrawer');
const drawerTitleText = document.getElementById('drawerTitleText');
const drawerContent = document.getElementById('drawerContent');
const btnCloseDrawer = document.getElementById('btnCloseDrawer');
const btnRefreshDrawer = document.getElementById('btnRefreshDrawer');
const btnAttach = document.getElementById('btnAttach');
const filePickerHidden = document.getElementById('filePickerHidden');

// Initialize
async function init() {
  SERVER_URL = normalizeServerUrl(SERVER_URL);
  document.getElementById('inputServerUrl').value = SERVER_URL;
  document.getElementById('inputServerToken').value = SERVER_TOKEN;

  await checkServerHealth();
  await loadSessions();
  setupLiveEvents();
}

function getHeaders() {
  const h = { 'Content-Type': 'application/json' };
  if (SERVER_TOKEN) {
    h['Authorization'] = `Bearer ${SERVER_TOKEN}`;
    h['x-bridge-token'] = SERVER_TOKEN;
    h['x-codex-token'] = SERVER_TOKEN;
  }
  return h;
}

// Check Server Health
async function checkServerHealth() {
  try {
    const t0 = performance.now();
    const res = await fetch(`${SERVER_URL}/api/health`, { headers: getHeaders() });
    const data = await res.json();
    const latency = Math.round(performance.now() - t0);
    if (data.ok) {
      serverStatusBadge.textContent = `● Online (${latency}ms)`;
      serverStatusBadge.style.color = '#2ea043';
      return true;
    }
  } catch (e) {
    serverStatusBadge.textContent = '○ Offline / Menghubungkan...';
    serverStatusBadge.style.color = '#f85149';
  }
  return false;
}

// Load Sessions List
let allSessions = [];
async function loadSessions() {
  try {
    const res = await fetch(`${SERVER_URL}/api/sessions`, { headers: getHeaders() });
    const data = await res.json();
    allSessions = data.sessions || [];
    renderSessionsList(allSessions);
  } catch (e) {
    console.error('Failed to load sessions:', e);
  }
}

function renderSessionsList(sessions) {
  sessionsList.innerHTML = '';
  if (!sessions || sessions.length === 0) {
    sessionsList.innerHTML = '<div style="padding:14px; color:var(--text-muted); font-size:12px; text-align:center;">Belum ada riwayat sesi.</div>';
    return;
  }

  sessions.forEach(session => {
    const card = document.createElement('div');
    card.className = `session-card ${session.conversationId === activeConversationId ? 'active' : ''}`;
    card.innerHTML = `
      <div class="session-card-title">${escapeHtml(session.title || 'Sesi')}</div>
      <div class="session-card-meta">
        <span>${session.engine === 'codex' ? 'Codex' : 'Antigravity'}</span>
        <span>${session.updatedAt ? new Date(session.updatedAt).toLocaleTimeString('id-ID', {hour: '2-digit', minute:'2-digit'}) : ''}</span>
      </div>
    `;
    card.onclick = () => openSession(session.conversationId, session.title, session.engine);
    sessionsList.appendChild(card);
  });
}

// Filter Sessions
sessionSearchInput.oninput = () => {
  const query = sessionSearchInput.value.toLowerCase().trim();
  if (!query) {
    renderSessionsList(allSessions);
    return;
  }
  const filtered = allSessions.filter(s => 
    (s.title && s.title.toLowerCase().includes(query)) ||
    (s.conversationId && s.conversationId.toLowerCase().includes(query))
  );
  renderSessionsList(filtered);
};

// Open Session Transcript
async function openSession(conversationId, title, engine) {
  activeConversationId = conversationId;
  activeSessionTitle = title || 'Sesi';
  currentEngine = engine || 'antigravity';
  currentSessionTitle.textContent = activeSessionTitle;
  currentSessionTag.textContent = `ID: ${conversationId.slice(0, 8)}...`;
  currentEngineName.textContent = currentEngine === 'codex' ? 'Codex' : 'Antigravity';
  
  renderSessionsList(allSessions);

  try {
    const res = await fetch(`${SERVER_URL}/api/session/transcript?id=${encodeURIComponent(conversationId)}`, { headers: getHeaders() });
    const data = await res.json();
    renderTranscript(data.turns || []);
  } catch (e) {
    console.error('Failed to load transcript:', e);
  }
}

// Render Transcript Turns
function renderTranscript(turns) {
  messagesList.innerHTML = '';
  if (!turns || turns.length === 0) {
    emptyState.classList.remove('hidden');
    return;
  }
  emptyState.classList.add('hidden');

  turns.forEach(turn => {
    const isUser = turn.role === 'user';
    const msg = document.createElement('div');
    msg.className = `message-turn ${isUser ? 'user' : 'assistant'}`;
    
    let contentHtml = formatMarkdown(turn.content || '');
    msg.innerHTML = `
      <div class="message-header">
        <svg class="icon-xs"><use href="${isUser ? '#icon-user' : '#icon-bot'}"></use></svg>
        <span>${isUser ? 'Anda' : (currentEngine === 'codex' ? 'Codex' : 'Antigravity')}</span>
      </div>
      <div class="message-body">${contentHtml}</div>
    `;
    messagesList.appendChild(msg);
  });

  const chatContainer = document.getElementById('chatContainer');
  chatContainer.scrollTop = chatContainer.scrollHeight;
}

// Markdown & Diff Formatter
function formatMarkdown(text) {
  let escaped = escapeHtml(text);

  // Parse diff blocks with 1-tap apply button
  escaped = escaped.replace(/```(?:diff|patch)\n([\s\S]*?)```/g, (match, diffContent) => {
    const lines = diffContent.split('\n').map(l => {
      if (l.startsWith('+')) return `<div class="diff-line add">${l}</div>`;
      if (l.startsWith('-')) return `<div class="diff-line del">${l}</div>`;
      return `<div>${l}</div>`;
    }).join('');

    return `
      <div class="diff-box">
        <div class="diff-header">
          <span>Diff Patch</span>
          <button class="btn-apply-diff" onclick="applyDiffPatch('${encodeURIComponent(diffContent)}')">
            <svg class="icon-xs"><use href="#icon-zap"></use></svg>
            <span>Terapkan</span>
          </button>
        </div>
        <pre>${lines}</pre>
      </div>
    `;
  });

  // Regular Code blocks
  escaped = escaped.replace(/```([a-zA-Z0-9]*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>');
  // Inline code
  escaped = escaped.replace(/`([^`]+)`/g, '<code>$1</code>');
  // Bold
  escaped = escaped.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
  // Line breaks
  escaped = escaped.replace(/\n/g, '<br>');

  return escaped;
}

// 1-Tap Diff Patch Apply
window.applyDiffPatch = async function(encodedDiff) {
  const patch = decodeURIComponent(encodedDiff);
  try {
    const res = await fetch(`${SERVER_URL}/api/files/patch`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ patch })
    });
    const data = await res.json();
    alert(data.ok ? 'Perubahan diff berhasil diterapkan ke workspace!' : 'Gagal: ' + (data.message || data.error));
  } catch (e) {
    alert('Error: ' + e.message);
  }
};

// Send Prompt
async function sendPrompt() {
  const prompt = promptInput.value.trim();
  if (!prompt || isTaskRunning) return;

  promptInput.value = '';
  slashPopup.classList.add('hidden');
  isTaskRunning = true;
  liveTaskBanner.classList.remove('hidden');

  // Optimistic User Bubble
  emptyState.classList.add('hidden');
  const userMsg = document.createElement('div');
  userMsg.className = 'message-turn user';
  userMsg.innerHTML = `
    <div class="message-header">
      <svg class="icon-xs"><use href="#icon-user"></use></svg>
      <span>Anda</span>
    </div>
    <div class="message-body">${escapeHtml(prompt).replace(/\n/g, '<br>')}</div>
  `;
  messagesList.appendChild(userMsg);
  
  const chatContainer = document.getElementById('chatContainer');
  chatContainer.scrollTop = chatContainer.scrollHeight;

  try {
    const res = await fetch(`${SERVER_URL}/api/chat`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({
        prompt: prompt,
        conversationId: activeConversationId,
        engine: currentEngine,
        model: currentModel
      })
    });
    const data = await res.json();
    if (data.ok && data.conversationId) {
      activeConversationId = data.conversationId;
      activeSessionTitle = data.session?.title || prompt.slice(0, 30);
      await loadSessions();
      await openSession(activeConversationId, activeSessionTitle, currentEngine);
    }
  } catch (e) {
    console.error('Send error:', e);
  } finally {
    isTaskRunning = false;
    liveTaskBanner.classList.add('hidden');
  }
}

// SSE Live Events Listener
function setupLiveEvents() {
  if (sseSource) sseSource.close();
  try {
    sseSource = new EventSource(`${SERVER_URL}/api/events`);
    sseSource.addEventListener('task.finished', async (e) => {
      isTaskRunning = false;
      liveTaskBanner.classList.add('hidden');
      if (activeConversationId) {
        await openSession(activeConversationId, activeSessionTitle, currentEngine);
      }
    });
  } catch (e) {
    console.warn('SSE connection failed:', e);
  }
}

// New Session
btnNewSession.onclick = () => {
  activeConversationId = null;
  activeSessionTitle = 'Sesi Baru';
  currentSessionTitle.textContent = activeSessionTitle;
  currentSessionTag.textContent = 'ID: Baru';
  messagesList.innerHTML = '';
  emptyState.classList.remove('hidden');
  renderSessionsList(allSessions);
  promptInput.focus();
};

// Rename Session
btnRenameSession.onclick = async () => {
  if (!activeConversationId) {
    alert('Buka sesi percakapan terlebih dahulu.');
    return;
  }
  const newTitle = prompt('Masukkan judul baru untuk sesi ini:', activeSessionTitle);
  if (!newTitle || newTitle.trim() === activeSessionTitle) return;

  try {
    const res = await fetch(`${SERVER_URL}/api/session/rename`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({
        conversationId: activeConversationId,
        title: newTitle.trim()
      })
    });
    const data = await res.json();
    if (data.ok) {
      activeSessionTitle = newTitle.trim();
      currentSessionTitle.textContent = activeSessionTitle;
      loadSessions();
    }
  } catch (e) {
    alert('Gagal mengubah nama sesi: ' + e.message);
  }
};

// Slash Commands Popup
document.getElementById('btnSlash').onclick = () => {
  slashPopup.classList.toggle('hidden');
};

document.querySelectorAll('.slash-item').forEach(item => {
  item.onclick = () => {
    promptInput.value = item.getAttribute('data-cmd') + ' ';
    promptInput.focus();
    slashPopup.classList.add('hidden');
  };
});

document.getElementById('btnAt').onclick = () => {
  promptInput.value += '@';
  promptInput.focus();
};

// File Attachment Button
btnAttach.onclick = () => {
  filePickerHidden.click();
};

filePickerHidden.onchange = (e) => {
  const files = e.target.files;
  if (!files || files.length === 0) return;
  const names = Array.from(files).map(f => f.name).join(', ');
  promptInput.value += ` [Lampiran: ${names}] `;
  promptInput.focus();
};

// Send and Keydown Listeners
btnSend.onclick = sendPrompt;
promptInput.onkeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendPrompt();
  }
};

// Cancel Live Task
btnCancelTask.onclick = async () => {
  try {
    await fetch(`${SERVER_URL}/api/chat/cancel`, { method: 'POST', headers: getHeaders() });
    isTaskRunning = false;
    liveTaskBanner.classList.add('hidden');
  } catch (e) {
    console.error('Cancel task failed:', e);
  }
};

// Engine Switcher & 1-Tap Installer Modal
engineSelectorBtn.onclick = async () => {
  const modal = document.getElementById('modalEngine');
  const body = document.getElementById('engineModalBody');
  modal.classList.remove('hidden');
  body.innerHTML = '<div style="padding:14px; text-align:center; color:var(--text-muted);">Memeriksa status engine di server...</div>';

  try {
    const res = await fetch(`${SERVER_URL}/api/engines`, { headers: getHeaders() });
    const data = await res.json();
    const engines = data.engines || {};

    const agy = engines.antigravity || { available: true, version: '1.0.0' };
    const codex = engines.codex || { available: false, version: null };

    body.innerHTML = `
      <!-- Antigravity Engine Card -->
      <div class="engine-choice-card ${currentEngine === 'antigravity' ? 'active' : ''}" onclick="selectEngine('antigravity')">
        <div class="engine-choice-header">
          <strong>Antigravity CLI</strong>
          <span class="badge-green">Terinstall ● ${agy.version || 'v1.0'}</span>
        </div>
        <div style="font-size:12px; color:var(--text-muted);">Engine default Google Deepmind Antigravity dengan tool eksekusi coding lengkap.</div>
      </div>

      <!-- Codex Engine Card -->
      <div class="engine-choice-card ${currentEngine === 'codex' ? 'active' : ''}" onclick="${codex.available ? "selectEngine('codex')" : ''}">
        <div class="engine-choice-header">
          <strong>Codex CLI</strong>
          <span class="${codex.available ? 'badge-green' : 'badge-amber'}">${codex.available ? 'Terinstall ● ' + codex.version : 'Belum Terpasang'}</span>
        </div>
        <div style="font-size:12px; color:var(--text-muted);">OpenAI Codex CLI engine untuk otomatisasi agen terminal.</div>
        ${!codex.available ? `
          <button class="btn-install-engine" onclick="installEngine('codex')">
            <svg class="icon-xs"><use href="#icon-zap"></use></svg>
            <span>Pasang Codex di Server</span>
          </button>
        ` : ''}
      </div>

      <!-- Live Install Terminal Output Box -->
      <div id="installLogBox" class="hidden" style="margin-top:14px; background:#0d0d10; border:1px solid var(--border); border-radius:8px; padding:10px; font-family:Consolas,monospace; font-size:11px; max-height:160px; overflow-y:auto; white-space:pre-wrap;"></div>
    `;
  } catch (e) {
    body.innerHTML = '<div style="padding:14px; color:var(--red);">Gagal memuat status engine dari server.</div>';
  }
};

window.selectEngine = (engine) => {
  currentEngine = engine;
  currentEngineName.textContent = engine === 'codex' ? 'Codex' : 'Antigravity';
  closeModal('modalEngine');
};

window.installEngine = async (engine) => {
  const logBox = document.getElementById('installLogBox');
  logBox.classList.remove('hidden');
  logBox.textContent = `Memulai instalasi ${engine} di server...\n`;

  try {
    const res = await fetch(`${SERVER_URL}/api/engine/install`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ engine })
    });
    const data = await res.json();
    if (data.ok) {
      logBox.textContent += `✓ Berhasil terpasang: ${data.version || ''}\n`;
      setTimeout(() => {
        engineSelectorBtn.click(); // reload modal
      }, 1500);
    } else {
      logBox.textContent += `✕ Gagal: ${data.error || 'Terjadi kesalahan'}\n`;
    }
  } catch (e) {
    logBox.textContent += `✕ Error koneksi: ${e.message}\n`;
  }
};

// Server Configuration Modal
btnServerConfig.onclick = () => {
  document.getElementById('inputServerUrl').value = SERVER_URL;
  document.getElementById('inputServerToken').value = SERVER_TOKEN;
  document.getElementById('testResultBox').textContent = '';
  document.getElementById('modalServer').classList.remove('hidden');
};

document.getElementById('btnTestConnection').onclick = async () => {
  const rawUrl = document.getElementById('inputServerUrl').value.trim();
  const cleanUrl = normalizeServerUrl(rawUrl);
  document.getElementById('inputServerUrl').value = cleanUrl; // Auto-update input with clean base URL!
  const testToken = document.getElementById('inputServerToken').value.trim();
  const resBox = document.getElementById('testResultBox');
  resBox.textContent = 'Menghubungkan ke ' + cleanUrl + '...';
  resBox.style.color = 'var(--text-muted)';

  try {
    const t0 = performance.now();
    const h = { 'Content-Type': 'application/json' };
    if (testToken) {
      h['Authorization'] = `Bearer ${testToken}`;
      h['x-bridge-token'] = testToken;
      h['x-codex-token'] = testToken;
    }
    const res = await fetch(`${cleanUrl}/api/health`, { headers: h });
    const data = await res.json();
    const lat = Math.round(performance.now() - t0);
    if (data.ok) {
      resBox.textContent = `✓ Berhasil terhubung ke ${cleanUrl} (Latensi: ${lat}ms) - Host: ${data.hostname || 'Server OK'}`;
      resBox.style.color = 'var(--green)';
    } else {
      resBox.textContent = `✕ Server merespons (${res.status}): ${data.error || 'Autentikasi gagal / token tidak cocok'}`;
      resBox.style.color = 'var(--red)';
    }
  } catch (e) {
    resBox.textContent = `✕ Gagal terhubung ke ${cleanUrl}: ${e.message}`;
    resBox.style.color = 'var(--red)';
  }
};

document.getElementById('btnSaveServerConfig').onclick = () => {
  const rawUrl = document.getElementById('inputServerUrl').value.trim();
  SERVER_URL = normalizeServerUrl(rawUrl);
  SERVER_TOKEN = document.getElementById('inputServerToken').value.trim();
  localStorage.setItem('antigravity_server_url', SERVER_URL);
  localStorage.setItem('antigravity_server_token', SERVER_TOKEN);
  closeModal('modalServer');
  init();
};

// Export Session Modal
btnExportSession.onclick = () => {
  if (!activeConversationId) {
    alert('Buka sesi percakapan terlebih dahulu untuk diekspor.');
    return;
  }
  document.getElementById('modalExport').classList.remove('hidden');
};

document.getElementById('btnExportMarkdown').onclick = () => {
  window.open(`${SERVER_URL}/api/session/export?id=${encodeURIComponent(activeConversationId)}&format=md`, '_blank');
  closeModal('modalExport');
};

document.getElementById('btnExportHtml').onclick = () => {
  window.open(`${SERVER_URL}/api/session/html?id=${encodeURIComponent(activeConversationId)}`, '_blank');
  closeModal('modalExport');
};

document.getElementById('btnExportGist').onclick = async () => {
  closeModal('modalExport');
  try {
    const res = await fetch(`${SERVER_URL}/api/session/gist`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ conversationId: activeConversationId })
    });
    const data = await res.json();
    if (data.ok && data.url) {
      alert('Transkrip berhasil diunggah ke GitHub Gist:\n' + data.url);
      window.open(data.url, '_blank');
    } else {
      alert('Gagal unggah ke Gist: ' + (data.error || 'Pastikan GITHUB_TOKEN tersedia di server'));
    }
  } catch (e) {
    alert('Error: ' + e.message);
  }
};

document.getElementById('btnExportCopy').onclick = async () => {
  try {
    const res = await fetch(`${SERVER_URL}/api/session/transcript?id=${encodeURIComponent(activeConversationId)}`, { headers: getHeaders() });
    const data = await res.json();
    const text = (data.turns || []).map(t => `${t.role.toUpperCase()}:\n${t.content}\n`).join('\n---\n\n');
    navigator.clipboard.writeText(text);
    alert('Transkrip lengkap berhasil disalin ke clipboard!');
    closeModal('modalExport');
  } catch (e) {
    alert('Gagal menyalin: ' + e.message);
  }
};

// Workspace Drawer (Files & Terminal)
btnToggleWorkspace.onclick = () => {
  currentDrawerMode = 'files';
  drawerTitleText.textContent = 'Workspace Files';
  rightDrawer.classList.toggle('hidden');
  if (!rightDrawer.classList.contains('hidden')) {
    loadWorkspaceFiles();
  }
};

btnToggleTerminal.onclick = () => {
  currentDrawerMode = 'terminal';
  drawerTitleText.textContent = 'Mini Terminal';
  rightDrawer.classList.toggle('hidden');
  if (!rightDrawer.classList.contains('hidden')) {
    renderMiniTerminal();
  }
};

btnRefreshDrawer.onclick = () => {
  if (currentDrawerMode === 'files') loadWorkspaceFiles();
  else renderMiniTerminal();
};

btnCloseDrawer.onclick = () => {
  rightDrawer.classList.add('hidden');
};

async function loadWorkspaceFiles() {
  drawerContent.innerHTML = '<div style="color:var(--text-muted); font-size:12px;">Memuat daftar file...</div>';
  try {
    const res = await fetch(`${SERVER_URL}/api/files/list`, { headers: getHeaders() });
    const data = await res.json();
    drawerContent.innerHTML = '';
    const entries = data.entries || [];
    if (entries.length === 0) {
      drawerContent.innerHTML = '<div style="color:var(--text-muted); font-size:12px;">Workspace kosong.</div>';
      return;
    }
    entries.forEach(f => {
      const row = document.createElement('div');
      row.style.display = 'flex';
      row.style.alignItems = 'center';
      row.style.gap = '8px';
      row.style.padding = '6px 8px';
      row.style.borderRadius = '6px';
      row.style.cursor = 'pointer';
      row.style.fontSize = '12.5px';
      row.innerHTML = `
        <svg class="icon-xs" style="color: ${f.type === 'dir' ? 'var(--accent)' : 'var(--text-muted)'}">
          <use href="${f.type === 'dir' ? '#icon-folder' : '#icon-file'}"></use>
        </svg>
        <span>${escapeHtml(f.name)}</span>
      `;
      row.onmouseover = () => row.style.background = 'var(--bg-surface-elevated)';
      row.onmouseout = () => row.style.background = 'transparent';
      row.onclick = () => {
        if (f.type !== 'dir') openFileInEditor(f.path || f.name);
      };
      drawerContent.appendChild(row);
    });
  } catch (e) {
    drawerContent.innerHTML = '<div style="color:var(--red); font-size:12px;">Gagal memuat file workspace.</div>';
  }
}

async function openFileInEditor(filePath) {
  activeEditingPath = filePath;
  document.getElementById('editorFileName').textContent = filePath;
  document.getElementById('editorTextarea').value = 'Memuat konten file...';
  document.getElementById('modalEditor').classList.remove('hidden');

  try {
    const res = await fetch(`${SERVER_URL}/api/files/read?path=${encodeURIComponent(filePath)}`, { headers: getHeaders() });
    const data = await res.json();
    document.getElementById('editorTextarea').value = data.content || '';
  } catch (e) {
    document.getElementById('editorTextarea').value = 'Gagal membaca isi file: ' + e.message;
  }
}

document.getElementById('btnSaveFile').onclick = async () => {
  if (!activeEditingPath) return;
  const content = document.getElementById('editorTextarea').value;
  try {
    const res = await fetch(`${SERVER_URL}/api/files/write`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({
        path: activeEditingPath,
        content: content
      })
    });
    const data = await res.json();
    alert(data.ok ? 'File berhasil disimpan!' : 'Gagal menyimpan: ' + data.error);
  } catch (e) {
    alert('Error: ' + e.message);
  }
};

function renderMiniTerminal() {
  drawerContent.innerHTML = `
    <div class="terminal-view">
      <div class="terminal-logs" id="terminalLogs">$ Antigravity Terminal Ready.\n</div>
      <div class="terminal-input-row">
        <span style="color:var(--green); font-family:Consolas; font-size:12px; margin-right:6px;">$</span>
        <input type="text" class="terminal-input" id="terminalInput" placeholder="Ketik perintah bash...">
      </div>
    </div>
  `;

  const termInput = document.getElementById('terminalInput');
  const termLogs = document.getElementById('terminalLogs');
  termInput.onkeydown = async (e) => {
    if (e.key === 'Enter') {
      const cmd = termInput.value.trim();
      if (!cmd) return;
      termInput.value = '';
      termLogs.textContent += `$ ${cmd}\n`;
      try {
        const res = await fetch(`${SERVER_URL}/api/terminal/run`, {
          method: 'POST',
          headers: getHeaders(),
          body: JSON.stringify({ command: cmd })
        });
        const data = await res.json();
        termLogs.textContent += (data.output || data.result || 'Done') + '\n';
      } catch (err) {
        termLogs.textContent += `Error: ${err.message}\n`;
      }
      termLogs.scrollTop = termLogs.scrollHeight;
    }
  };
}

// Generic Modal Closer
window.closeModal = (id) => {
  document.getElementById(id).classList.add('hidden');
};

function escapeHtml(str) {
  return String(str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

window.onload = init;
