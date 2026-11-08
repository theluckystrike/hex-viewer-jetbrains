'use strict';
// Runs the Hex Viewer core tests from the VS Code extension (test/hex, unchanged except the require paths)
// against the sources that go into the bundle, plus the shim and bundle tests of this plugin.
const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
const files = [
  ...fs.readdirSync(path.join(__dirname, 'hex')).filter(f => f.endsWith('.test.js')).map(f => path.join('test', 'hex', f)),
  path.join('test', 'shim.test.js'),
  path.join('test', 'bundle.test.js'),
];
const r = spawnSync(process.execPath, ['--test', '--test-concurrency=1', ...files], { cwd: root, stdio: 'inherit' });
process.exit(r.status);
