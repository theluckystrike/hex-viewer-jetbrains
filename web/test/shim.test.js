'use strict';
// The JetBrains shim (src/hex/shim.js) with the plugin's routes faked: a 10 GB file served by byte range from
// a generator (no bytes on disk), so the test checks open, reads, search across a 64 MB range edge, copy and
// the read-only guard without an IDE.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('path');
const fs = require('fs');
const os = require('os');
const esbuild = require('esbuild');

const root = path.join(__dirname, '..');
const SIZE = 10 * 1024 ** 3;
const EDGE = 64 * 1024 * 1024;
const PATTERN = [0xde, 0xad, 0xbe, 0xef, 0x13, 0x37];
const AT = EDGE - 3; // the pattern spans the 64 MB edge
const byteAt = o => (o >= AT && o < AT + PATTERN.length ? PATTERN[o - AT] : o >= SIZE - 4 ? 0x45 : 0);

const delivered = [], posts = [], reads = [];
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'hexjb-shim-'));
test.after(() => fs.rmSync(tmp, { recursive: true, force: true }));

function setupGlobals() {
  const store = () => { const m = new Map(); return { getItem: k => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)) }; };
  globalThis.window = globalThis;
  globalThis.ZOVO = { token: 'tok', name: 'huge.bin', size: SIZE, kind: 'hex' };
  globalThis.localStorage = store();
  globalThis.sessionStorage = store();
  globalThis.document = { addEventListener() {}, querySelector: () => null, querySelectorAll: () => [], getElementById: () => null };
  globalThis.dispatchEvent = ev => { delivered.push(ev.data); return true; };
  globalThis.MessageEvent = class { constructor(type, init) { this.type = type; this.data = init.data; } };
  globalThis.fetch = async (url, init) => {
    const u = new URL(url, 'http://zovo-hex');
    if (init && init.method === 'POST') { posts.push([u.pathname, JSON.parse(init.body)]); const body = u.pathname.endsWith('/rendered') ? { rate: false } : { ok: true }; return { ok: true, status: 200, json: async () => body, text: async () => JSON.stringify(body) }; }
    const off = Number(u.searchParams.get('offset')), len = Number(u.searchParams.get('length'));
    assert.ok(len <= EDGE, 'one range read stays within 64 MB');
    if (off + len > SIZE) return { ok: false, status: 416 };
    reads.push([off, len]);
    const b = new Uint8Array(len);
    if (AT + PATTERN.length > off && AT < off + len) for (let i = 0; i < len; i++) b[i] = byteAt(off + i);
    if (off + len > SIZE - 4) for (let i = Math.max(0, SIZE - 4 - off); i < len; i++) b[i] = 0x45;
    return { ok: true, status: 200, arrayBuffer: async () => b.buffer };
  };
}

let api;
test.before(() => {
  setupGlobals();
  const out = path.join(tmp, 'shim.cjs');
  esbuild.buildSync({
    entryPoints: [path.join(root, 'src/hex/shim.js')], bundle: true, format: 'cjs', platform: 'browser', outfile: out, logLevel: 'error',
    inject: [path.join(root, 'src/shared/buffer-global.js')], alias: { fs: path.join(root, 'src/shared/fs-shim.js') },
  });
  require(out);
  api = globalThis.acquireVsCodeApi();
});

const until = async (pred, ms = 20000) => { const t0 = Date.now(); while (!pred()) { if (Date.now() - t0 > ms) throw new Error('timeout'); await new Promise(r => setTimeout(r, 5)); } };
const last = type => [...delivered].reverse().find(m => m.type === type);

test('init reports the size, read-only, and the copy formats without reading the file whole', async () => {
  api.postMessage({ type: 'ready' });
  await until(() => last('init'));
  const m = last('init');
  assert.equal(m.size, SIZE);
  assert.equal(m.readOnly, true);
  assert.match(m.sizeText, /10\.0 GB/);
  assert.ok(m.copyFormats.some(f => f[0] === 'c'));
  assert.ok(reads.every(([, l]) => l <= 64), 'only the 64-byte header was read');
});

test('reads the last row of a 10 GB file by range', async () => {
  api.postMessage({ type: 'read', offset: SIZE - 65536, length: 65536 });
  await until(() => last('data') && last('data').offset === SIZE - 65536);
  const d = last('data');
  assert.equal(d.bytes.length, 65536);
  assert.deepEqual([...d.bytes.subarray(-4)], [0x45, 0x45, 0x45, 0x45]);
});

test('finds hex across the 64 MB range edge, forward and backward', async () => {
  api.postMessage({ type: 'search', query: 'DE AD BE EF 13 37', mode: 'hex', ignoreCase: false, from: EDGE - 4 * 1024 * 1024, backward: false, id: 1 });
  await until(() => last('searchResult') && last('searchResult').id === 1);
  assert.equal(last('searchResult').offset, AT);
  api.postMessage({ type: 'search', query: 'de ?? be ef', mode: 'hex', ignoreCase: false, from: EDGE + 4 * 1024 * 1024, backward: true, id: 2 });
  await until(() => last('searchResult') && last('searchResult').id === 2);
  assert.equal(last('searchResult').offset, AT);
});

test('copy as C array goes to the IDE clipboard route', async () => {
  api.postMessage({ type: 'copy', start: AT, end: AT + 6, format: 'c' });
  await until(() => posts.some(([p]) => p.endsWith('/copy')));
  const [, body] = posts.find(([p]) => p.endsWith('/copy'));
  assert.equal(body.text, 'unsigned char huge[6] = {\n  0xDE, 0xAD, 0xBE, 0xEF, 0x13, 0x37\n};');
  await until(() => last('copied'));
  assert.match(last('copied').text, /Copied 6 bytes as C array/);
});

test('edits never reach the IDE: a notice instead', async () => {
  const before = posts.length;
  api.postMessage({ type: 'edit', offset: 0, values: Uint8Array.of(1), label: 'Edit byte' });
  await until(() => last('notice'));
  assert.match(last('notice').message, /read-only/);
  assert.equal(posts.length, before);
});

test('rendered asks the IDE once for the rating-link rule', async () => {
  api.postMessage({ type: 'rendered' });
  await until(() => posts.some(([p]) => p.endsWith('/rendered')));
});

test('state keeps the cursor per tab and view choices for all tabs', () => {
  api.setState({ bpr: 32, encoding: 'latin1', cursor: 1234 });
  assert.deepEqual(api.getState(), { bpr: 32, encoding: 'latin1', cursor: 1234 });
  assert.deepEqual(JSON.parse(localStorage.getItem('zovo-hex-prefs')), { bpr: 32, encoding: 'latin1' });
});
