import { test, expect, type Page, type BrowserContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { jwtDecrypt, EncryptJWT } from 'jose';
// Actual PKCE helper; no access-token fixtures or API route mocks.
import { login as tokens, identity, listing, json } from '../../../scripts/lib/api.mjs';
const origin = process.env.APP_ORIGIN || 'http://localhost:3000';
const run = randomUUID();
const buyer = `extended-buyer-${run}@example.test`;
const agent = `extended-agent-${run}@example.test`;
const secret = `Tests#${randomUUID()}`;
const newSecret = `Changed#${randomUUID()}`;
let property: { id: string; version: number }; let orderId = '';
test.describe.configure({ mode: 'serial' });

/** Logs in through the actual Identity form and web PKCE callback. */
async function login(page: Page, email: string, password: string, next: string) {
  await page.goto(`/login?next=${encodeURIComponent(next)}`); await page.getByRole('link', { name: 'Нэвтрэх хуудас руу' }).click();
  await page.getByLabel('Имэйл').fill(email); await page.getByLabel('Нууц үг').fill(password); await page.getByRole('button', { name: /^Нэвтрэх/ }).click();
  await expect(page).toHaveURL(`${origin}${next}`);
}
/** Extracts a real local SMTP message without logging email body or recovery credentials. */
async function mailToken(email: string, path: string) {
  const base = `http://localhost:${process.env.BEMON_MAIL_PORT || 8025}`;
  for (let attempt = 0; attempt < 60; attempt++) {
    const inbox = await (await fetch(`${base}/api/v1/messages?limit=100`)).json();
    for (const message of inbox.messages || []) {
      if (!message.To?.some((to: { Address: string }) => to.Address === email)) continue;
      const full = await (await fetch(`${base}/api/v1/message/${message.ID}`)).json();
      const match = String(full.Text || '').match(new RegExp(`${path}\\?token=([A-Za-z0-9_-]{43})`));
      if (match) return match[1];
    }
    await new Promise(resolve => setTimeout(resolve, 300));
  }
  throw new Error('Expected local SMTP challenge was not delivered');
}
/** Sets up synthetic accounts/listing through actual authorization and publication APIs. */
test.beforeAll(async () => {
  await json(`${identity}/v1/users/register`, 201, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: buyer, password: secret }) });
  const admin = await tokens(process.env.DEV_ADMIN_EMAIL!, process.env.DEV_ADMIN_PASSWORD!);
  await json(`${identity}/v1/admin/users/agents`, 201, { method: 'POST', headers: { Authorization: `Bearer ${admin.access_token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ email: agent, password: secret }) });
  const owner = await tokens(agent, secret);
  const created = await json(`${listing}/v1/properties`, 201, { method: 'POST', headers: { Authorization: `Bearer ${owner.access_token}`, 'Idempotency-Key': randomUUID(), 'Content-Type': 'application/json' },
    body: JSON.stringify({ title: `Шинэ урсгал ${run}`, propertyType: 'APARTMENT', listingType: 'SALE', currency: 'MNT', price: 210000000, bedrooms: 2, bathrooms: 1, parkingSpaces: 0, landSizeSqm: 60,
      address: { addressLine: 'Синтетик тестийн хаяг', suburb: 'Тестийн дүүрэг', state: 'UB', postcode: '17000', latitude: 47.9188, longitude: 106.9177 } }) });
  property = (await json(`${listing}/v1/properties/${created.body.id}/status`, 200, { method: 'PATCH', headers: { Authorization: `Bearer ${owner.access_token}`, 'If-Match': '"0"', 'Content-Type': 'application/json' }, body: JSON.stringify({ status: 'ACTIVE' }) })).body;
});

test('profile, real verification mail, favorites and buyer inquiry reach the owning agent', async ({ page, context }) => {
  await login(page, buyer, secret, '/account'); await page.getByLabel('Нэр', { exact: true }).fill('Тест худалдан авагч'); await page.getByLabel('Утас', { exact: true }).fill('+976 99110022');
  await page.getByRole('button', { name: 'Хадгалах', exact: true }).click(); await expect(page.getByRole('status')).toContainText('Мэдээлэл хадгалагдлаа.');
  await page.getByRole('button', { name: 'Имэйл баталгаажуулах' }).click(); await expect(page.getByRole('status')).toContainText('Имэйлдээ');
  const token = await mailToken(buyer, 'verify-email'); await page.goto(`/verify-email?token=${token}`); await page.getByRole('button', { name: 'Баталгаажуулах', exact: true }).click(); await expect(page.getByRole('status')).toContainText('Имэйл баталгаажлаа.');
  const replay = await context.request.post('/api/account/verify-email', { headers: { Origin: origin }, data: { token } }); expect(replay.status()).toBe(400);
  await page.goto('/account'); await expect(page.getByText('Имэйл баталгаажсан', { exact: true })).toBeVisible();
  await page.goto(`/properties/${property.id}`); await page.getByRole('button', { name: '♡ Зар хадгалах' }).click(); await expect(page.getByRole('button', { name: '♥ Хадгалсан' })).toBeVisible();
  await page.getByLabel('Агент руу хүсэлт').fill(`Байр үзэх хүсэлт ${run}`); await page.getByRole('button', { name: 'Хүсэлт илгээх', exact: true }).click(); await expect(page.getByRole('status')).toContainText('Хүсэлт илгээгдлээ.');
  await page.goto('/favorites'); await expect(page.getByRole('heading', { name: `Шинэ урсгал ${run}`, exact: true })).toBeVisible();
});

test('owner inbox can reply and another participant sees only the reply to their own conversation', async ({ page, browser }) => {
  await login(page, agent, secret, '/inquiries'); await expect(page.getByText('Тест худалдан авагч', { exact: false })).toBeVisible();
  await page.getByLabel('Хариу', { exact: true }).fill('Маргааш 11 цагт үзэж болно.'); await page.getByRole('button', { name: 'Хариу хадгалах' }).click(); await expect(page.getByLabel('Хариу', { exact: true })).toHaveValue('Маргааш 11 цагт үзэж болно.');
  const context = await browser.newContext({ baseURL: origin }); try { const buyerPage = await context.newPage(); await login(buyerPage, buyer, secret, '/inquiries'); await expect(buyerPage.getByText('Маргааш 11 цагт үзэж болно.', { exact: true })).toBeVisible(); } finally { await context.close(); }
});

test('saved search generates a personal alert after a matching listing update', async ({ page }) => {
  await login(page, buyer, secret, '/'); await page.getByLabel('Түлхүүр үг', { exact: true }).fill(run); await page.getByRole('button', { name: 'Хайх', exact: true }).click();
  await expect(page.getByRole('heading', { name: `Шинэ урсгал ${run}`, exact: true })).toBeVisible();
  await page.getByLabel('Хайлтын нэр').fill(`Хайлт ${run}`); await page.getByRole('button', { name: 'Хайлт хадгалах' }).click(); await expect(page.getByRole('status')).toContainText('Хайлтыг хадгаллаа.');
  const owner = await tokens(agent, secret); const current = (await json(`${listing}/v1/properties/${property.id}`, 200)).body;
  await json(`${listing}/v1/properties/${property.id}`, 200, { method: 'PUT', headers: { Authorization: `Bearer ${owner.access_token}`, 'If-Match': `"${current.version}"`, 'Content-Type': 'application/json' }, body: JSON.stringify(current) });
  await expect.poll(async () => { await page.goto('/alerts'); return page.getByRole('link', { name: `Шинэ урсгал ${run}`, exact: true }).count(); }, { timeout: 30000 }).toBe(1);
  await page.getByRole('button', { name: 'Уншсан гэж тэмдэглэх' }).click(); await expect(page.getByText('Уншсан', { exact: true })).toBeVisible();
});

test('featured checkout has immutable pricing, admin test settlement and a public placement', async ({ page, browser }) => {
  await login(page, agent, secret, `/payments?property=${property.id}`); await page.getByRole('button', { name: /20,000.*авах/ }).click(); await expect(page).toHaveURL(/\/payments\/[0-9a-f-]+$/); orderId = page.url().split('/').pop()!;
  await expect(page.getByText(/Туршилтын төлбөр — бодит мөнгө шилжихгүй/)).toBeVisible();
  const admin = await browser.newContext({ baseURL: origin }); try { const adminPage = await admin.newPage(); await login(adminPage, process.env.DEV_ADMIN_EMAIL!, process.env.DEV_ADMIN_PASSWORD!, `/payments/${orderId}`); await adminPage.getByRole('button', { name: 'Туршилтын төлбөр батлах' }).click(); await expect(adminPage.getByRole('heading', { name: 'Төлбөр батлагдсан' })).toBeVisible(); }
  finally { await admin.close(); }
  await expect.poll(async () => (await (await fetch(`${listing}/v1/properties/${property.id}/featured`)).json()).featured, { timeout: 20000 }).toBe(true);
  await page.goto('/'); await expect(page.getByRole('heading', { name: 'Онцлох зарууд' })).toBeVisible();
});

test('refresh rotates one stored token set under concurrent requests and password reset revokes it', async ({ page, context }) => {
  await login(page, buyer, secret, '/account');
  const handle = (await context.cookies()).find(cookie => cookie.name === 'bemon_session')!.value;
  const key = Buffer.from(process.env.WEB_SESSION_SECRET!, 'hex'); const { payload: claims } = await jwtDecrypt(handle, key);
  const headers = { 'X-Internal-Key': process.env.INTERNAL_SERVICE_KEY!, 'Content-Type': 'application/json' };
  const store = `${identity}/internal/web-sessions/${claims.sessionId}`;
  const encrypted = (await (await fetch(store, { headers })).json()).payload;
  const { payload } = await jwtDecrypt(encrypted, key); const before = payload.refreshToken;
  // Trigger the real refresh threshold while retaining the actual signed access/refresh credentials.
  payload.expiresAt = Date.now() + 1000;
  const leaseId = randomUUID(); await fetch(`${store}/lease`, { method: 'POST', headers, body: JSON.stringify({ leaseId }) });
  const replacement = await new EncryptJWT(payload).setProtectedHeader({ alg: 'dir', enc: 'A256GCM' }).setIssuer('bemon-web').setAudience('bemon-web').setIssuedAt().setExpirationTime('30d').encrypt(key);
  expect((await fetch(store, { method: 'PUT', headers, body: JSON.stringify({ leaseId, payload: replacement }) })).ok).toBe(true);
  const requests = await Promise.all([context.request.get('/account'), context.request.get('/favorites')]); expect(requests.every(response => response.status() === 200)).toBe(true); expect(requests[0].url()).toBe(`${origin}/account`); expect(requests[1].url()).toBe(`${origin}/favorites`);
  const rotated = await jwtDecrypt((await (await fetch(store, { headers })).json()).payload, key); expect(rotated.payload.refreshToken !== before).toBe(true);
  const oldBearer = String(rotated.payload.accessToken);
  await page.goto('/forgot-password'); await page.getByLabel('Имэйл').fill(buyer); await page.getByRole('button', { name: 'Холбоос авах' }).click(); await expect(page.getByRole('status')).toContainText('Бүртгэлтэй имэйл');
  const token = await mailToken(buyer, 'reset-password'); await page.goto(`/reset-password?token=${token}`); await page.getByLabel('Шинэ нууц үг').fill(newSecret); await page.getByRole('button', { name: 'Нууц үг шинэчлэх', exact: true }).click(); await expect(page.getByRole('status')).toContainText('Нууц үг шинэчлэгдлээ.');
  await page.goto('/account'); await expect(page).toHaveURL(/\/login\?next=/);
  expect((await fetch(`${listing}/v1/users/me/favorites`, { headers: { Authorization: `Bearer ${oldBearer}` } })).status).toBe(401);
});

test('native BFF verifies mobile PKCE, stores only an opaque handle, rejects tampering and revokes logout', async ({ request }) => {
  const applicationTokens = await tokens(buyer, newSecret);
  for (const endpoint of [
    `${identity}/v1/users/me`, `${listing}/v1/users/me/favorites`,
    `http://localhost:${process.env.BEMON_PAYMENT_PORT || 8081}/v1/users/me/payments`,
    `http://localhost:${process.env.BEMON_SEARCH_PORT || 8000}/v1/users/me/searches`,
  ]) {
    expect((await fetch(endpoint, { headers: { Authorization: `Bearer ${applicationTokens.access_token}` } })).status).toBe(200);
    expect((await fetch(endpoint, { headers: { Authorization: `Bearer ${applicationTokens.id_token}` } })).status).toBe(401);
  }
  const nonce = randomUUID(); const authorization = await tokens(buyer, newSecret, { clientId: 'domain-mobile', redirectUri: 'gerhub://oauth', scope: 'openid profile', nonce, codeOnly: true });
  const response = await request.post('/api/mobile/exchange', { data: { code: authorization.code, verifier: authorization.verifier, nonce, redirectUri: 'gerhub://oauth' } }); expect(response.status()).toBe(200);
  const { handle } = await response.json(); expect(handle.split('.')).toHaveLength(5); const headers = { Authorization: `Bearer ${handle}` };
  const profile = await request.get('/api/mobile/account', { headers }); expect(profile.status()).toBe(200); expect((await profile.json()).email).toBe(buyer);
  const encryptionKey = Buffer.from(process.env.WEB_SESSION_SECRET!, 'hex');
  const { payload: mobileHandle } = await jwtDecrypt(handle, encryptionKey);
  const internalHeaders = { 'X-Internal-Key': process.env.INTERNAL_SERVICE_KEY!, 'Content-Type': 'application/json' };
  const store = identity + '/internal/web-sessions/' + mobileHandle.sessionId;
  const { payload: stored } = await jwtDecrypt((await (await fetch(store, { headers: internalHeaders })).json()).payload, encryptionKey);
  const originalRefresh = stored.refreshToken; stored.expiresAt = Date.now() + 1000;
  const leaseId = randomUUID(); await fetch(store + '/lease', { method: 'POST', headers: internalHeaders, body: JSON.stringify({ leaseId }) });
  const replacement = await new EncryptJWT(stored).setProtectedHeader({ alg: 'dir', enc: 'A256GCM' }).setIssuer('bemon-web').setAudience('bemon-web').setIssuedAt().setExpirationTime('30d').encrypt(encryptionKey);
  await fetch(store, { method: 'PUT', headers: internalHeaders, body: JSON.stringify({ leaseId, payload: replacement }) });
  expect((await request.get('/api/mobile/account', { headers })).status()).toBe(200);
  const renewed = await jwtDecrypt((await (await fetch(store, { headers: internalHeaders })).json()).payload, encryptionKey);
  expect(renewed.payload.refreshToken !== originalRefresh).toBe(true); expect(renewed.payload.clientId).toBe('domain-mobile');
  expect((await request.get('/api/mobile/account', { headers: { Authorization: 'Bearer tampered' } })).status()).toBe(401);
  expect((await request.post('/api/mobile/logout', { headers })).status()).toBe(200); expect((await request.get('/api/mobile/account', { headers })).status()).toBe(401);
});

test('mobile app web preview renders real MNT results without horizontal overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await page.goto('http://localhost:8082');
  await expect(page.getByText('Таны дараагийн орон зай', { exact: true })).toBeVisible(); await expect(page.getByText(/Жишээ/).first()).toBeVisible(); await expect(page.getByText(/₮/).last()).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await expect(page.getByRole('tab', { name: 'Хадгалсан', exact: true })).toBeInViewport();
  await page.getByText(/Жишээ/).first().scrollIntoViewIfNeeded();
  await page.screenshot({ path: 'test-results/native-web-preview.png', fullPage: true });
});

test.afterAll(async () => {
  if (!property) return; const owner = await tokens(agent, secret); const current = (await json(`${listing}/v1/properties/${property.id}`, 200)).body;
  await json(`${listing}/v1/properties/${property.id}/status`, 200, { method: 'PATCH', headers: { Authorization: `Bearer ${owner.access_token}`, 'If-Match': `"${current.version}"`, 'Content-Type': 'application/json' }, body: JSON.stringify({ status: 'WITHDRAWN' }) });
});
