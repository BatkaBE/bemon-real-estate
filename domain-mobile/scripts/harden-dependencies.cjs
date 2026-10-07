/** Applies version-checked local mitigations for upstream advisories without a patched release. */
const fs = require('node:fs');
const path = require('node:path');
// Preserve the marker so previously hardened installs remain idempotent after rebranding.
const marker = 'BEMON_SECURITY_MITIGATION';
const root = path.resolve(__dirname, '../node_modules');
/** Requires an exact upstream anchor and applies once; unexpected dependency updates fail visibly. */
function patch(file, anchor, replacement) {
  const source = fs.readFileSync(file, 'utf8');
  if (source.includes(marker)) return;
  if (source.split(anchor).length !== 2) throw new Error(`Security mitigation anchor changed: ${file}`);
  fs.writeFileSync(file, source.replace(anchor, replacement));
}
/** Patches each installed copy while leaving other package code unchanged. */
function packageFolder(folder) {
  const file = path.join(folder, 'package.json');
  if (!fs.existsSync(file)) return;
  const info = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (info.name === 'braces') {
    if (info.version !== '3.0.3') throw new Error('Review braces mitigation for new version');
    patch(path.join(folder, 'lib/parse.js'), '      depth++;', '      // BEMON_SECURITY_MITIGATION GHSA-vfj7-8cjw-p6xm\n      if (++depth > 64) throw new RangeError("Brace nesting limit exceeded");');
    patch(path.join(folder, 'lib/stringify.js'), 'const stringify = (node, parent = {}) => {', 'const stringify = (node, parent = {}, depth = 0) => {\n    // BEMON_SECURITY_MITIGATION GHSA-vfj7-8cjw-p6xm\n    if (depth > 64) throw new RangeError("Brace AST nesting limit exceeded");');
    const stringifyFile = path.join(folder, 'lib/stringify.js');
    fs.writeFileSync(stringifyFile, fs.readFileSync(stringifyFile, 'utf8').replace('stringify(child)', 'stringify(child, {}, depth + 1)'));
    for (const name of ['compile', 'expand']) {
      patch(path.join(folder, `lib/${name}.js`), 'const walk = (node, parent = {}) => {', 'const walk = (node, parent = {}, depth = 0) => {\n    // BEMON_SECURITY_MITIGATION GHSA-vfj7-8cjw-p6xm\n    if (depth > 64) throw new RangeError("Brace AST nesting limit exceeded");');
      const target = path.join(folder, `lib/${name}.js`);
      let source = fs.readFileSync(target, 'utf8');
      source = source.replace('walk(child, node)', 'walk(child, node, depth + 1)'); fs.writeFileSync(target, source);
    }
  }
  if (info.name === 'node-forge') {
    if (info.version !== '1.4.0') throw new Error('Review forge mitigation for new version');
    patch(path.join(folder, 'lib/rsa.js'), 'obj.value.length !== 2) {', `obj.value.length !== 2 ||
            // BEMON_SECURITY_MITIGATION GHSA-86w9-cpqp-85rv
            !Array.isArray(obj.value[0].value) ||
            (obj.value[0].value.length !== 1 && obj.value[0].value.length !== 2) ||
            (obj.value[0].value.length === 2 &&
              (obj.value[0].value[1].tagClass !== asn1.Class.UNIVERSAL ||
               obj.value[0].value[1].type !== asn1.Type.NULL ||
               obj.value[0].value[1].constructed || obj.value[0].value[1].value !== ''))) {`);
  }
  walk(path.join(folder, 'node_modules'));
}
/** Visits package roots, including nested/scoped copies, rather than traversing source assets. */
function walk(directory) {
  if (!fs.existsSync(directory)) return;
  for (const name of fs.readdirSync(directory)) {
    if (name.startsWith('.')) continue;
    const folder = path.join(directory, name);
    if (name.startsWith('@')) { for (const child of fs.readdirSync(folder)) packageFolder(path.join(folder, child)); }
    else if (fs.statSync(folder).isDirectory()) packageFolder(folder);
  }
}
walk(root);
console.info('Applied version-checked braces/forge security mitigations.');
