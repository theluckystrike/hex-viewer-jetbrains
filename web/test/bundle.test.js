'use strict';
// Checks the committed bundle in src/main/resources/web: built from the current sources, no VS Code host
// left, reads by range, CSP in the page template, no remote origin.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const web = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'web');

test('bundle and page template', () => {
  const js = fs.readFileSync(path.join(web, 'hex.js'), 'utf8');
  const html = fs.readFileSync(path.join(web, 'view.html'), 'utf8');
  assert.ok(js.length > 20000 && js.length < 400000, 'bundle size ' + js.length);
  assert.ok(js.includes('/file?offset='), 'reads by byte range');
  assert.ok(js.includes('acquireVsCodeApi'), 'the shim defines the host API');
  assert.ok(!/https?:\/\/(?!github\.com|plugins\.jetbrains|marketplace\.visualstudio|www\.w3\.org)[a-z0-9.-]+\.[a-z]{2,}/i.test(js.replace(/zovo-hex/g, '')), 'no remote origin in the bundle');
  assert.match(html, /default-src 'none'/);
  assert.match(html, /script-src 'self' 'nonce-\{\{NONCE\}\}'/);
  assert.ok(!html.includes('unsafe-eval'));
  assert.ok(fs.existsSync(path.join(web, 'hex.css')) && fs.existsSync(path.join(web, 'shell.css')));
  assert.deepEqual(fs.readFileSync(path.join(web, 'hex.css')), fs.readFileSync(path.join(__dirname, '..', 'src', 'hex', 'webview.css')));
});
