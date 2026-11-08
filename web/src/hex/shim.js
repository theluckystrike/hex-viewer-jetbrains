// Replaces the VS Code extension host for the Hex Viewer page in JetBrains IDEs. The unchanged webview
// (webview/main.js) talks to this file through acquireVsCodeApi(); the unchanged core (core/doc.js) reads the
// file by byte range from the plugin's private origin (GET /t/<token>/file?offset=&length=). Read-only:
// edit, delete, cut and paste get a notice and never reach the IDE.
require('../shared/set-immediate.js');
const { HexDocument } = require('./core/doc');
const { validateWebviewMessage } = require('./core/protocol');
const { fmtSize, fmtInt, parsePattern, formatCopy, detectType, COPY_FORMATS } = require('./core/bytes');
const { host, deliver, fileName, fileSize, token } = require('../shared/host.js');

const MAX_COPY = 16 << 20;
const READ_ONLY = 'This viewer is read-only. It never changes the file.';

class HttpSource {
  constructor(size) { this.size = size; }
  async stat() { return { size: this.size }; }
  async read(offset, length) {
    const n = Math.max(0, Math.min(length, this.size - offset));
    if (n <= 0 || offset < 0) return new Uint8Array(0);
    const r = await fetch('/t/' + encodeURIComponent(token) + '/file?offset=' + offset + '&length=' + n);
    if (r.status === 416) return new Uint8Array(0); // the file got shorter on disk
    if (!r.ok) throw new Error('Could not read bytes ' + offset + '-' + (offset + n) + ' (HTTP ' + r.status + ').');
    return new Uint8Array(await r.arrayBuffer());
  }
  async writable() { return false; }
  async reopen() {}
  async close() {}
}

const hex = new HexDocument(new HttpSource(fileSize), fileSize);
const stem = String(fileName || 'data').replace(/\.[^.]*$/, '');
let searchId = 0;

// Webview state: the cursor per tab (survives the reload on a theme change), view choices for all files.
const SKEY = 'zovo-hex-' + token, PKEY = 'zovo-hex-prefs';
const read = (store, k) => { try { return JSON.parse(store.getItem(k) || 'null'); } catch { return null; } };
const state = {
  get: () => { const p = read(localStorage, PKEY) || {}; const s = read(sessionStorage, SKEY) || {}; return { ...p, ...s }; },
  set: s => {
    try { sessionStorage.setItem(SKEY, JSON.stringify(s)); } catch { /* storage off */ }
    try { const { cursor, ...prefs } = s || {}; localStorage.setItem(PKEY, JSON.stringify(prefs)); } catch { /* storage off */ }
    return s;
  },
};

const SETTINGS = { bytesPerRow: 16, encoding: 'ascii', controlChars: 'dot', copyFormat: 'hexSpaced', fontSize: 0, littleEndian: true, readOnly: true, showInspector: true, uppercase: true };

async function post(path, body) {
  const r = await fetch('/t/' + encodeURIComponent(token) + '/' + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body || {}) });
  try { return await r.json(); } catch { return {}; }
}

async function handle(raw) {
  const v = validateWebviewMessage(raw);
  if (!v.ok) {
    if (raw && (raw.type === 'delete' || raw.type === 'cut' || raw.type === 'insertPrompt')) deliver({ type: 'notice', level: 'info', message: READ_ONLY });
    return;
  }
  const msg = v.msg;
  try {
    switch (msg.type) {
      case 'ready': {
        const head = await hex.read(0, 64);
        deliver({ type: 'init', name: fileName, size: hex.size, sizeText: fmtSize(hex.size), fileType: detectType(head), readOnly: true, readOnlyReason: READ_ONLY, settings: SETTINGS, edited: [], copyFormats: COPY_FORMATS });
        return;
      }
      case 'read': {
        const bytes = await hex.read(msg.offset, msg.length);
        deliver({ type: 'data', offset: msg.offset, bytes, edited: [], size: hex.size });
        return;
      }
      case 'rendered': {
        const r = await post('rendered');
        if (r && r.rate) { const a = document.getElementById('zovo-rate'); if (a) a.hidden = false; for (const s of document.querySelectorAll('.zovo-rate-sep')) s.hidden = false; }
        return;
      }
      case 'gotoPrompt': { const g = document.querySelector('input.goto'); if (g) { g.focus(); g.select(); } return; }
      case 'edit': deliver({ type: 'notice', level: 'info', message: READ_ONLY }); return;
      case 'copy': {
        const n = Math.min(msg.end, hex.size) - msg.start;
        if (n <= 0) return;
        if (n > MAX_COPY) { deliver({ type: 'notice', level: 'warn', message: `The selection is ${fmtSize(n)}. Copy works up to ${fmtSize(MAX_COPY)}.` }); return; }
        const bytes = await hex.read(msg.start, n);
        await host.copy(formatCopy(bytes, msg.format, stem));
        const label = (COPY_FORMATS.find(f => f[0] === msg.format) || [0, msg.format])[1];
        deliver({ type: 'copied', text: `Copied ${fmtInt(bytes.length)} byte${bytes.length === 1 ? '' : 's'} as ${label}.` });
        return;
      }
      case 'cancelSearch': searchId = -1; return;
      case 'search': {
        const p = parsePattern(msg.query, msg.mode, msg.ignoreCase);
        if (!p.ok) { deliver({ type: 'searchResult', id: msg.id, offset: -1, error: p.error }); return; }
        const id = searchId = msg.id;
        const t0 = Date.now();
        const r = await hex.search(p, msg.from, msg.backward, true, () => searchId !== id, (done, total) => deliver({ type: 'searchProgress', id, done, total }));
        deliver({ type: 'searchResult', id, offset: r.offset, length: p.bytes.length, wrapped: !!r.wrapped, cancelled: !!r.cancelled, ms: Date.now() - t0 });
        return;
      }
      case 'error': console.error('[hex-viewer page]', msg.message); return;
      default: return;
    }
  } catch (e) {
    deliver({ type: 'notice', level: 'error', message: (e && e.message) || String(e) });
  }
}

// Messages run one after another, except search, which runs on its own so Stop and paging stay live.
let queue = Promise.resolve();
globalThis.acquireVsCodeApi = () => ({
  postMessage: m => {
    if (m && (m.type === 'search' || m.type === 'cancelSearch')) { handle(m); return; }
    queue = queue.then(() => handle(m));
  },
  getState: () => state.get(),
  setState: s => state.set(s),
});
