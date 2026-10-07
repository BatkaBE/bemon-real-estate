import assert from 'node:assert/strict';
import { test } from 'node:test';
import { isSameOrigin, isUuid, safeReturnPath } from '../../src/lib/security';
import { formatPrice } from '../../src/lib/presentation';

test('OAuth return paths reject foreign origins and browser URL normalization tricks', () => {
  for (const value of ['https://evil.test', '//evil.test', '/\\evil.test', '/\n/evil.test', '/\r/evil.test', '', null]) {
    assert.equal(safeReturnPath(value), '/dashboard');
  }
  assert.equal(safeReturnPath('/dashboard/new?mode=edit#fragment'), '/dashboard/new?mode=edit');
  assert.equal(safeReturnPath('/properties/../dashboard'), '/dashboard');
});
test('cookie mutations require an exact scheme, host, and port', () => {
  const expected = 'http://localhost:3000';
  assert.equal(isSameOrigin(expected, expected), true);
  for (const value of [null, 'null', 'https://localhost:3000', 'http://localhost:3001', 'http://localhost.evil:3000']) {
    assert.equal(isSameOrigin(value, expected), false);
  }
});
test('path identifiers cannot add backend routes or query parameters', () => {
  assert.equal(isUuid('aa773196-603c-492b-b14c-819957a0fa43'), true);
  for (const value of ['../admin', 'aa773196-603c-492b-b14c-819957a0fa43?x=1', 'no-id']) assert.equal(isUuid(value), false);
});
test('prices retain their own currency and null prices are negotiable', () => {
  assert.match(formatPrice(320000000, 'MNT'), /₮/);
  assert.match(formatPrice(100, 'AUD'), /A\$|AUD/);
  assert.doesNotMatch(formatPrice(100, 'AUD'), /₮/);
  assert.equal(formatPrice(null, 'MNT'), 'Үнэ тохиролцоно');
});
