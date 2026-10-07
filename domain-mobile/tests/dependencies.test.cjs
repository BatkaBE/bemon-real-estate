const { test } = require('node:test');
const assert = require('node:assert/strict');
const braces = require('braces');
const forge = require('node-forge');

test('brace nesting is rejected before recursive compilation can exhaust the stack', () => {
  assert.deepEqual(braces.expand('home/{android,ios}'), ['home/android', 'home/ios']);
  assert.throws(() => braces.compile('{'.repeat(1000) + 'x' + '}'.repeat(1000)), /nesting limit/);
  const root = { type: 'root', nodes: [] }; let parent = root;
  for (let i = 0; i < 1000; i++) { const child = { type: 'brace', nodes: [], open: true, close: true }; parent.nodes.push(child); parent = child; }
  assert.throws(() => braces.compile(root), /AST nesting limit/);
  assert.throws(() => braces.stringify(root), /AST nesting limit/);
});
test('RSA signature verification accepts a normal digest and rejects extra nested algorithm elements', () => {
  const pair = forge.pki.rsa.generateKeyPair({ bits: 1024, e: 65537 });
  const md = forge.md.sha256.create().update('owned synthetic signature test');
  const signature = pair.privateKey.sign(md); const digest = md.digest().getBytes();
  assert.equal(pair.publicKey.verify(digest, signature), true);
  const asn = forge.asn1; const algorithm = asn.create(asn.Class.UNIVERSAL, asn.Type.SEQUENCE, true, [
    asn.create(asn.Class.UNIVERSAL, asn.Type.OID, false, asn.oidToDer(forge.oids.sha256).getBytes()),
    asn.create(asn.Class.UNIVERSAL, asn.Type.NULL, false, ''),
    asn.create(asn.Class.UNIVERSAL, asn.Type.OCTETSTRING, false, 'unexpected garbage'),
  ]);
  const info = asn.create(asn.Class.UNIVERSAL, asn.Type.SEQUENCE, true, [algorithm, asn.create(asn.Class.UNIVERSAL, asn.Type.OCTETSTRING, false, digest)]);
  const malformed = pair.privateKey.sign(asn.toDer(info).getBytes(), 'NONE');
  assert.throws(() => pair.publicKey.verify(digest, malformed), /valid RSASSA/);
});
