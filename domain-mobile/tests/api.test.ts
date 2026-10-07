import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ApiClient, ApiFailure, searchParams } from '../src/api';
/** Supplies a test store with no persistence or device credentials. */
function store() { let value: string | null = 'opaque'; return { get: async () => value, set: async (next: string) => { value = next; }, remove: async () => { value = null; } }; }
test('native requests carry an opaque bearer, bounded URL, and a stable retry key', async () => {
  const storage = store(); let calls = 0;
  const client = new ApiClient('https://gerhub.test', storage, async (url, options) => { calls++; assert.equal(url, 'https://gerhub.test/api/mobile/inquiries/one'); assert.equal((options?.headers as Record<string, string>).Authorization, 'Bearer opaque'); assert.equal((options?.headers as Record<string, string>)['Idempotency-Key'], 'stable'); return new Response('{}'); });
  await client.request('/inquiries/one', { method: 'POST', key: 'stable', body: { message: 'Hello' } });
  await assert.rejects(client.request('//evil.test'), ApiFailure); assert.equal(calls, 1);
});
test('revoked sessions clear secure storage and preserve the HTTP error', async () => {
  const storage = store(); const client = new ApiClient('https://gerhub.test', storage, async () => new Response('{"message":"Нэвтрэх"}', { status: 401 }));
  await assert.rejects(client.request('/account'), (error: unknown) => error instanceof ApiFailure && error.status === 401); assert.equal(await storage.get(), null);
});
test('logout clears the device handle even when the network is unavailable', async () => {
  const storage = store(); const client = new ApiClient('https://gerhub.test', storage, async () => { throw new Error('offline'); });
  await assert.rejects(client.logout()); assert.equal(await storage.get(), null);
});
test('blank filters are omitted and pagination is explicit', () => {
  const value = searchParams({ q: ' орон ', suburb: '', listingType: 'SALE', minPrice: '', maxPrice: '', latitude: '', longitude: '', radiusKm: '' });
  assert.equal(value, 'currency=MNT&q=%D0%BE%D1%80%D0%BE%D0%BD&listingType=SALE');
});
test('the default browser fetch retains its global receiver', async () => {
  const previous = globalThis.fetch;
  globalThis.fetch = function (this: typeof globalThis, input, options) { assert.equal(this, globalThis); return Promise.resolve(new Response('{}')); };
  try { await new ApiClient('https://gerhub.test', store()).request('/search'); } finally { globalThis.fetch = previous; }
});
