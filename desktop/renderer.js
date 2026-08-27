const SERVER_URL = localStorage.getItem('antigravity_server_url') || 'http://127.0.0.1:18790';
let activeConversationId = null;
let currentEngine = 'antigravity';
let currentModel = 'auto';
let isTaskRunning = false;
let sseSource = null;

// DOM Elements
const serverStatusBadge = document.getElementById('serverStatusBadge');
const sessionsList = document.getElementById('sessionsList');
const currentSessionTitle = document.getElementById('currentSessionTitle');
const currentSessionTag = document.getElementById('currentSessionTag');
const messagesList = document.getElementById('messagesList');
const emptyState = document.getElementById('emptyState');
const promptInput = document.getElementById('promptInput');
const btnSend = document.getElementById('btnSend');
const btnNewSession = document.getElementById('btnNewSession');
const slashPopup = document.getElementById('slashPopup');
const liveTaskBanner = document.getElementById('liveTaskBanner');
const btnToggleWorkspace = document.getElementById('btnToggleWorkspace');
const btnToggleTerminal = document.getElementById('btnToggleTerminal');
const rightDrawer = document.getElementById('rightDrawer');
const drawerTitle = document.getElementById('drawerTitle');
const drawerContent = document.getElementById('drawerContent');
const btnCloseDrawer = document.getElementById('btnCloseDrawer');
const engineSelectorBtn = document.getElementById('engineSelectorBtn');

// Initialize
async function init() {
  await checkServerHealth();
  await loadSessions();
  setupLiveEvents();
}

async function checkServerHealth() {
  try {
    const res = await fetch(`${SERVER_URL}/api/health`);
    const data = await res.json();
    if (data.ok) {
      serverStatusBadge.textContent = '● Terhubung ke Server';
      serverStatusBadge.style.color = '#2ea043';
    }
  } catch (e) {
    serverStatusBadge.textContent = '○ Offline / Menghubungkan...';
    serverStatusBadge.style.color = '#f85149';
  }
}

async function loadSessions() {
  try {
    const res = await fetch(`${SERVER_URL}/api/sessions`);
    const data = await res.json();
    sessionsList.innerHTML = '';

    (data.sessions || []).forEach(session => {
      const card = document.createElement('div');
      card.className = `session-card ${session.conversationId === activeConversationId ? 'active' : ''}`;
      card.innerHTML = `
        <div class="session-card-title">${escapeHtml(session.title || 'Sesi')}</div>
        <div class="session-card-meta">
          <span>${session.engine || 'agy'}</span>
          <span>${session.updatedAt ? new Date(session.updatedAt).toLocaleTimeString('id-ID', {hour: '2-digit', minute:'2-digit'}) : ''}</span>
        </div>
      `;
      card.onclick = () => openSession(session.conversationId, session.title, session.engine);
      sessionsList.appendChild(card);
    });
  } catch (e) {
    console.error('Failed to load sessions:', e);
  }
}

async function openSession(conversationId, title, engine) {
  activeConversationId = conversationId;
  currentEngine = engine || 'antigravity';
  currentSessionTitle.textContent = title || 'Sesi';
  currentSessionTag.textContent = `ID: ${conversationId.slice(0, 8)}...`;
  emptyState.classList.add('hidden');
  loadSessions(); // update active card

  try {
    const res = await fetch(`${SERVER_URL}/api/session/transcript?id=${encodeURIComponent(conversationId)}`);
    const data = await res.json();
    renderTranscript(data.turns || []);
  } catch (e) {
    console.error('Failed to load transcript:', e);
  }
}

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
        <span>${isUser ? '👤 Anda' : '🤖 ' + (currentEngine === 'codex' ? 'Codex' : 'Antigravity')}</span>
      </div>
      <div class="message-body">${contentHtml}</div>
    `;
    messagesList.appendChild(msg);
  });

  document.getElementById('chatContainer').scrollTop = document.getElementById('chatContainer').scrollHeight;
}

function formatMarkdown(text) {
  // Simple markdown and diff parser
  let escaped = escapeHtml(text);

  // Parse diff blocks
  escaped = escaped.replace(/```(?:diff|patch)\n([\s\S]*?)```/g, (match, diffContent) => {
    const lines = diffContent.split('\n').map(l => {
      if (l.startsWith('+')) return `<div class="diff-line add">${l}</div>`;
      if (l.startsWith('-')) return `<div class="diff-line del">${l}</div>`;
      return `<div>${l}</div>`;
    }).join('');

    return `
      <div class="diff-box">
        <div class="diff-header">
          <span>⚡ Diff Patch</span>
          <button class="btn-apply-diff" onclick="applyDiffPatch('${encodeURIComponent(diffContent)}')">⚡ Terapkan</button>
        </div>
        <pre>${lines}</pre>
      </div>
    `;
  });

  // Code blocks
  escaped = escaped.replace(/```([a-zA-Z0-9]*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>');
  // Bold
  escaped = escaped.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
  // Line breaks
  escaped = escaped.replace(/\n/g, '<br>');

  return escaped;
}

window.applyDiffPatch = async function(encodedDiff) {
  const patch = decodeURIComponent(encodedDiff);
  try {
    const res = await fetch(`${SERVER_URL}/api/files/patch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ patch })
    });
    const data = await res.json();
    alert(data.ok ? '✓ Perubahan diff berhasil diterapkan ke workspace!' : '✕ ' + data.message);
  } catch (e) {
    alert('Error: ' + e.message);
  }
};

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
    <div class="message-header"><span>👤 Anda</span></div>
    <div class="message-body">${escapeHtml(prompt).replace(/\n/g, '<br>')}</div>
  `;
  messagesList.appendChild(userMsg);
  document.getElementById('chatContainer').scrollTop = document.getElementById('chatContainer').scrollHeight;

  try {
    const res = await fetch(`${SERVER_URL}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
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
      await openSession(activeConversationId, data.session?.title || 'Sesi', currentEngine);
    }
  } catch (e) {
    console.error('Send error:', e);
  } finally {
    isTaskRunning = false;
    liveTaskBanner.classList.add('hidden');
  }
}

function setupLiveEvents() {
  if (sseSource) sseSource.close();
  sseSource = new EventSource(`${SERVER_URL}/api/events`);
  
  sseSource.addEventListener('task.finished', (e) => {
    isTaskRunning = false;
    liveTaskBanner.classList.add('hidden');
    if (activeConversationId) {
      openSession(activeConversationId, currentSessionTitle.textContent, currentEngine);
    }
  });
}

// Event Listeners
btnSend.onclick = sendPrompt;
promptInput.onkeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendPrompt();
  }
};

btnNewSession.onclick = () => {
  activeConversationId = null;
  currentSessionTitle.textContent = 'Sesi Baru';
  currentSessionTag.textContent = 'ID: Baru';
  messagesList.innerHTML = '';
  emptyState.classList.remove('hidden');
  loadSessions();
};

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

btnToggleWorkspace.onclick = async () => {
  rightDrawer.classList.toggle('hidden');
  if (!rightDrawer.classList.contains('hidden')) {
    drawerTitle.textContent = '📁 Workspace Files';
    drawerContent.innerHTML = 'Memuat file...';
    try {
      const res = await fetch(`${SERVER_URL}/api/files/list`);
      const data = await res.json();
      drawerContent.innerHTML = '';
      (data.entries || []).forEach(f => {
        const item = document.createElement('div');
        item.style.padding = '6px 0';
        item.style.fontSize = '12px';
        item.style.cursor = 'pointer';
        item.textContent = `${f.type === 'dir' ? '📁' : '📄'} ${f.name}`;
        drawerContent.appendChild(item);
      });
    } catch (e) {
      drawerContent.innerHTML = 'Gagal memuat file.';
    }
  }
};

btnCloseDrawer.onclick = () => {
  rightDrawer.classList.add('hidden');
};

function escapeHtml(str) {
  return String(str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

window.onload = init;
