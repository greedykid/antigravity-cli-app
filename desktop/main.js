const { app, BrowserWindow, ipcMain, Tray, Menu, shell, dialog } = require('electron');
const path = require('path');
const { spawn, execSync } = require('child_process');
const fs = require('fs');

let mainWindow = null;
let tray = null;
let bridgeProcess = null;

function startLocalBridge() {
  const bridgeScript = path.join(__dirname, '..', 'bridge', 'server.js');
  if (!fs.existsSync(bridgeScript)) return;

  try {
    bridgeProcess = spawn('node', [bridgeScript], {
      cwd: path.join(__dirname, '..', 'bridge'),
      env: Object.assign({}, process.env, { PORT: process.env.PORT || '18790' }),
      stdio: 'ignore'
    });
    bridgeProcess.unref();
  } catch (e) {
    console.error('Failed to spawn local bridge:', e);
  }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    backgroundColor: '#141416',
    title: 'Antigravity & Codex Remote Desktop',
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false,
      enableRemoteModule: true
    },
    autoHideMenuBar: true
  });

  mainWindow.loadFile(path.join(__dirname, 'index.html'));

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });
}

function createTray() {
  // Use a default icon or generate tray
  try {
    tray = new Tray(path.join(__dirname, 'icon.png'));
  } catch (e) {
    // If icon.png doesn't exist, skip tray
    return;
  }

  const contextMenu = Menu.buildFromTemplate([
    { label: 'Buka Antigravity Remote', click: () => { if (mainWindow) mainWindow.show(); } },
    { label: 'Koneksi Server: 127.0.0.1:18790', enabled: false },
    { type: 'separator' },
    { label: 'Keluar', click: () => { app.quit(); } }
  ]);

  tray.setToolTip('Antigravity & Codex Remote Desktop');
  tray.setContextMenu(contextMenu);
  tray.on('click', () => {
    if (mainWindow) {
      if (mainWindow.isVisible()) mainWindow.hide();
      else mainWindow.show();
    }
  });
}

app.whenReady().then(() => {
  startLocalBridge();
  createWindow();
  createTray();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('will-quit', () => {
  if (bridgeProcess) {
    try { bridgeProcess.kill(); } catch (e) {}
  }
});
