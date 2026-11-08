'use strict';
// Builds the browser bundle into ../src/main/resources/web (committed, so the Gradle build needs no Node).
const esbuild = require('esbuild');
const path = require('path');
const fs = require('fs');
const root = __dirname;
const out = path.join(root, '..', 'src', 'main', 'resources', 'web');
fs.mkdirSync(out, { recursive: true });
const shims = path.join(root, 'src', 'shared');
esbuild.build({
  bundle: true, minify: true, sourcemap: false, legalComments: 'none', logLevel: 'warning', absWorkingDir: root,
  platform: 'browser', target: 'chrome120', format: 'iife',
  inject: [path.join(shims, 'buffer-global.js')],
  alias: { fs: path.join(shims, 'fs-shim.js'), 'node:fs': path.join(shims, 'fs-shim.js') },
  entryPoints: ['src/hex/page.js'], outfile: path.join(out, 'hex.js'),
}).then(() => {
  fs.copyFileSync(path.join(root, 'src/hex/webview.css'), path.join(out, 'hex.css'));
  for (const f of fs.readdirSync(out)) console.log(f.padEnd(14), fs.statSync(path.join(out, f)).size);
}, e => { console.error(e && e.message); process.exit(1); });
