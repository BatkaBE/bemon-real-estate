import { test, expect, type Page, type BrowserContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';

const origin = process.env.APP_ORIGIN || 'http://localhost:3000';
const listing = `http://localhost:${process.env.BEMON_LISTING_PORT || 8080}`;
const runId = randomUUID();
const buyerEmail = `web-buyer-${runId}@example.test`;
const agentEmail = `web-agent-${runId}@example.test`;
const password = `Web#2026${randomUUID()}`;
let createdId = '';
test.describe.configure({ mode: 'serial' });

/** Follows the real redirect, Mongolian Identity form, PKCE callback, and encrypted session. */
async function login(page: Page, email: string, secret: string, next = '/dashboard') {
  await page.goto(`/login?next=${encodeURIComponent(next)}`);
  await page.getByRole('link', { name: 'Нэвтрэх хуудас руу' }).click();
  await expect(page.getByLabel('Имэйл')).toBeVisible();
  await page.getByLabel('Имэйл').fill(email);
  await page.getByLabel('Нууц үг').fill(secret);
  await page.getByRole('button', { name: 'Нэвтрэх' }).click();
  await expect(page).toHaveURL(`${origin}${next}`);
}

/** Ends both sessions, including Spring's protocol logout confirmation if presented. */
async function logout(page: Page, context: BrowserContext) {
  await page.getByRole('button', { name: 'Гарах', exact: true }).click();
  if (page.url().includes('/connect/logout')) {
    const confirmation = page.getByRole('button', { name: /Log Out|Logout|Гарах/i });
    if (await confirmation.count()) await confirmation.click();
  }
  await expect(page).toHaveURL(`${origin}/login`);
  expect((await context.cookies()).find(cookie => cookie.name === 'bemon_session')).toBeUndefined();
}

test('Mongolian MNT catalog, search, detail, and desktop layout use actual backend data', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('html')).toHaveAttribute('lang', 'mn');
  await expect(page).toHaveTitle('GerHub — Таны дараагийн орон зай');
  await expect(page.getByRole('link', { name: 'GerHub нүүр', exact: true })).toContainText('GerHub');
  await expect(page.getByRole('heading', { name: 'Танд тохирох орон зай.' })).toBeVisible();
  await expect(page.locator('.property-card').first()).toBeVisible();
  await expect(page.locator('.card-bottom').first()).toContainText('₮');
  await page.screenshot({ path: 'test-results/catalog-desktop.png', fullPage: true });
  await page.getByLabel('Байршил', { exact: true }).fill('Хан-Уул');
  await page.getByRole('button', { name: 'Хайх' }).click();
  await expect(page.locator('.card-address').first()).toContainText('Хан-Уул');
  await page.locator('.property-card h3 a').first().click();
  await expect(page.locator('.price-panel')).toContainText('₮');
  await expect(page.getByRole('link', { name: 'Газрын зураг дээр үзэх' })).toHaveAttribute('href', /openstreetmap/);
});

test('mobile catalog and registration remain within a 390px viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/?listingType=RENT');
  await expect(page.locator('.property-card').first()).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: 'test-results/catalog-mobile.png', fullPage: true });
  await page.goto('/register');
  await expect(page.getByLabel('Нууц үг')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('cookie adapters reject cross-origin writes, unauthenticated writes, tampered callbacks, and GET logout', async ({ request }) => {
  const crossOrigin = await request.post('/api/register', { headers: { Origin: 'https://foreign.test' },
    data: { email: 'never@example.test', password } });
  expect(crossOrigin.status()).toBe(403);
  expect((await request.post('/api/properties', { headers: { Origin: origin }, data: {} })).status()).toBe(401);
  expect((await request.get('/api/auth/logout')).status()).toBe(405);
  const callback = await request.get('/api/auth/callback/domain?state=é&code=invalid', { maxRedirects: 0 });
  expect(callback.status()).toBe(307);
  expect(callback.headers().location).toContain('/login?error=flow');
});

test('buyer signup and OAuth login protect credentials, role boundaries, and full logout', async ({ page, context }) => {
  await page.goto('/register');
  await page.getByLabel('Имэйл').fill(buyerEmail);
  await page.getByLabel('Нууц үг').fill(password);
  await page.getByRole('button', { name: 'Бүртгүүлэх', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Бүртгэл амжилттай');
  await login(page, buyerEmail, password);
  await expect(page.getByRole('heading', { name: 'Тавтай морил.' })).toBeVisible();
  const cookie = (await context.cookies()).find(item => item.name === 'bemon_session');
  expect(cookie?.httpOnly).toBe(true); expect(cookie?.sameSite).toBe('Lax');
  expect(cookie?.value.split('.')).toHaveLength(5);
  expect(await page.evaluate(() => Object.keys(localStorage))).toHaveLength(0);
  expect(await page.content()).not.toContain('accessToken');
  const result = await context.request.post('/api/properties', { headers: { Origin: origin }, data: {} });
  expect(result.status()).toBe(403);
  await page.goto('/dashboard/new'); await expect(page).toHaveURL(`${origin}/dashboard`);
  await logout(page, context);
  await page.getByRole('link', { name: 'Нэвтрэх хуудас руу' }).click();
  await expect(page.getByLabel('Имэйл')).toBeVisible();
});

test('administrator provisions an agent through the UI without assigning public roles', async ({ page, context }) => {
  await login(page, process.env.DEV_ADMIN_EMAIL!, process.env.DEV_ADMIN_PASSWORD!);
  await expect(page.getByRole('heading', { name: 'Агентын бүртгэл' })).toBeVisible();
  await page.getByLabel('Имэйл').fill(agentEmail);
  await page.getByLabel('Нууц үг').fill(password);
  await page.getByRole('button', { name: 'Агент үүсгэх' }).click();
  await expect(page.getByRole('status')).toContainText('Агент');
  await logout(page, context);
});

test('agent creates a private MNT draft, publishes, replaces it, and cannot overwrite a stale version', async ({ page, context, browser }) => {
  await login(page, agentEmail, password);
  await page.getByRole('link', { name: 'Шинэ зар нэмэх' }).click();
  await page.getByLabel('Зарын гарчиг').fill(`Веб шалгалт · ${runId}`);
  await page.getByLabel('Үнэ (₮)').fill('225000000');
  await page.getByLabel('Талбай').fill('65');
  await page.getByLabel('Унтлагын өрөө').fill('2');
  await page.getByLabel('Угаалгын өрөө').fill('1');
  await page.getByLabel('Зогсоол', { exact: true }).fill('1');
  await page.getByLabel('Дүүрэг / байршил').fill('Веб шалгалт');
  await page.getByLabel('Дэлгэрэнгүй хаяг').fill('Синтетик туршилтын хаяг');
  await page.getByLabel('Шуудангийн код').fill('17000');
  await page.getByLabel('Өргөрөг').fill('47.9188');
  await page.getByLabel('Уртраг').fill('106.9177');
  const creation = page.waitForResponse(response => response.url() === `${origin}/api/properties` && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Ноорог хадгалах' }).click();
  const created = await (await creation).json(); createdId = created.id;
  expect(created.currency).toBe('MNT');
  await expect(page).toHaveURL(new RegExp(`/dashboard/${createdId}\\?saved=1`));
  const anonymous = await browser.newContext();
  try {
    expect((await anonymous.request.get(`${listing}/v1/properties/${createdId}`)).status()).toBe(404);
    const preview = await anonymous.newPage();
    expect((await preview.goto(`${origin}/properties/${createdId}`))?.status()).toBe(404);
    await page.getByRole('button', { name: 'Төлөв өөрчлөх' }).click();
    await expect(page.locator('.status-panel .status')).toHaveText('Нийтэлсэн');
    await expect(page.getByLabel('Шинэ төлөв')).toHaveValue('UNDER_OFFER');
    await expect.poll(async () => { await preview.goto(`${origin}/?suburb=${encodeURIComponent('Веб шалгалт')}`); return preview.getByRole('heading', { name: `Веб шалгалт · ${runId}`, exact: true }).count(); }, { timeout: 30000 }).toBe(1);
    await page.getByLabel('Зарын гарчиг').fill(`Шинэчилсэн · ${runId}`);
    await page.getByRole('button', { name: 'Өөрчлөлт хадгалах' }).click();
    await expect(page.locator('.section-heading p')).toContainText(`Шинэчилсэн · ${runId}`);
    const stale = await context.request.put(`/api/properties/${createdId}`, { headers: { Origin: origin, 'If-Match': '"0"' }, data: created });
    expect(stale.status()).toBe(412);
    await page.screenshot({ path: 'test-results/agent-editor.png', fullPage: true });
  } finally { await anonymous.close(); }
});

test('another agent cannot read a private management page or replace an owned listing', async ({ page, context }) => {
  await login(page, process.env.DEV_AGENT_EMAIL!, process.env.DEV_AGENT_PASSWORD!);
  expect((await page.goto(`/dashboard/${createdId}`))?.status()).toBe(404);
  const property = await (await context.request.get(`${listing}/v1/properties/${createdId}`)).json();
  const response = await context.request.put(`/api/properties/${createdId}`, {
    headers: { Origin: origin, 'If-Match': `"${property.version}"` }, data: property });
  expect(response.status()).toBe(403);
  expect((await context.request.post('/api/admin/agents', { headers: { Origin: origin }, data: { email: 'blocked@example.test', password } })).status()).toBe(403);
});

test('encrypted session cookie rejects tampering without trusting the claimed role', async ({ page, context }) => {
  await login(page, process.env.DEV_AGENT_EMAIL!, process.env.DEV_AGENT_PASSWORD!);
  const cookie = (await context.cookies()).find(item => item.name === 'bemon_session')!;
  const parts = cookie.value.split('.'); parts[3] = (parts[3][0] === 'A' ? 'B' : 'A') + parts[3].slice(1);
  await context.addCookies([{ ...cookie, value: parts.join('.') }]);
  await page.goto('/dashboard'); await expect(page).toHaveURL(/\/login\?next=/);
  expect((await context.request.post('/api/properties', { headers: { Origin: origin }, data: {} })).status()).toBe(401);
});

test.afterAll(async ({ browser }) => {
  if (!createdId) return;
  const context = await browser.newContext({ baseURL: origin });
  try {
    const page = await context.newPage();
    await login(page, agentEmail, password);
    const property = await (await context.request.get(`${listing}/v1/properties/${createdId}`)).json();
    const response = await context.request.patch(`/api/properties/${createdId}/status`, {
      headers: { Origin: origin, 'If-Match': `"${property.version}"` }, data: { status: 'WITHDRAWN' } });
    expect(response.status()).toBe(200);
    await logout(page, context);
  } finally { await context.close(); }
});
