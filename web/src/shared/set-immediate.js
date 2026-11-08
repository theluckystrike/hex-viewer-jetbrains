// The hex core yields with setImmediate during long searches. Browsers do not have it; a MessageChannel
// post runs the callback on the next task without the 4 ms clamp of nested setTimeout.
if (typeof globalThis.setImmediate !== 'function') {
  const ch = new MessageChannel();
  const queue = [];
  ch.port1.onmessage = () => { const f = queue.shift(); if (f) f(); };
  globalThis.setImmediate = f => { queue.push(f); ch.port2.postMessage(0); };
}
