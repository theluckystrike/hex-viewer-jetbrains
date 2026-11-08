// Stand-in for node:fs in the browser bundle. The hex core's FileSource and save paths are never used in the
// IDE (the viewer is read-only and reads through HttpSource), so every call here fails loudly.
const no = name => () => { throw new Error(name + ' is not available in the viewer'); };
export const promises = { open: no('fs.promises.open'), stat: no('fs.promises.stat'), access: no('fs.promises.access'), rm: no('fs.promises.rm'), rename: no('fs.promises.rename') };
export const constants = { W_OK: 2 };
export default { promises, constants };
