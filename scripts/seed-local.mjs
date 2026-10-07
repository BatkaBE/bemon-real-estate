import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { identity, listing, json, login } from './lib/api.mjs';

const samples = [
  ['Яармагт гэр бүлийн хувийн сууц', 'HOUSE', 'SALE', 680000000, 3, 2, 2, 180, 'Хан-Уул', 'Яармаг, 8-р хороо', 47.883, 106.822],
  ['Баянзүрхэд 700 м² газар', 'LAND', 'SALE', 95000000, null, null, 0, 700, 'Баянзүрх', 'Гачуурт, 20-р хороо', 47.922, 107.149],
  ['Төвд тохилог 2 өрөө байр', 'APARTMENT', 'RENT', 1800000, 1, 1, 0, 54, 'Сүхбаатар', '1-р хороо, Энхтайваны өргөн чөлөө', 47.918, 106.922],
  ['Зайсанд ногоон орчинтой таунхаус', 'TOWNHOUSE', 'SALE', 890000000, 4, 3, 2, 210, 'Хан-Уул', 'Зайсан, 11-р хороо', 47.881, 106.923],
  ['Богд Ард хотхонд нарлаг 3 өрөө', 'APARTMENT', 'SALE', 265000000, 2, 1, 1, 78, 'Баянгол', '2-р хороо, Богд Ард хотхон', 47.911, 106.876],
  ['Хан-Уулд шинэ 3 өрөө байр', 'APARTMENT', 'SALE', 320000000, 2, 2, 1, 86, 'Хан-Уул', '15-р хороо, шинэ хотхон', 47.896, 106.913],
];

/** Provisions labelled synthetic MNT listings without deleting or replacing existing records. */
async function main() {
  assert.ok(process.env.DEV_AGENT_EMAIL && process.env.DEV_AGENT_PASSWORD, 'Initialize .local.env first');
  const admin = await login(process.env.DEV_ADMIN_EMAIL, process.env.DEV_ADMIN_PASSWORD);
  const credentials = { email: process.env.DEV_AGENT_EMAIL, password: process.env.DEV_AGENT_PASSWORD };
  const provision = await fetch(`${identity}/v1/admin/users/agents`, { method: 'POST',
    signal: AbortSignal.timeout(15000), headers: { Authorization: `Bearer ${admin.access_token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(credentials) });
  assert.ok([201, 409].includes(provision.status), 'Local agent provisioning failed');
  const agent = await login(credentials.email, credentials.password);
  const headers = { Authorization: `Bearer ${agent.access_token}`, 'Content-Type': 'application/json' };
  const existing = new Map();
  let cursor;
  do {
    const query = new URLSearchParams({ pageSize: '100' }); if (cursor) query.set('cursor', cursor);
    const { body: page } = await json(`${listing}/v1/agents/me/properties?${query}`, 200, { headers });
    for (const property of page.items) existing.set(property.title, property);
    cursor = page.nextCursor;
  } while (cursor);
  let added = 0;
  for (const [title, propertyType, listingType, price, bedrooms, bathrooms, parkingSpaces, landSizeSqm, suburb, addressLine, latitude, longitude] of samples) {
    const labelledTitle = `Жишээ · ${title}`;
    if (existing.has(labelledTitle)) continue;
    const hash = createHash('sha256').update(`bemon-mn-seed-v1:${labelledTitle}`).digest('hex');
    const key = `${hash.slice(0,8)}-${hash.slice(8,12)}-${hash.slice(12,16)}-${hash.slice(16,20)}-${hash.slice(20,32)}`;
    const draft = { title: labelledTitle, propertyType, listingType, price, currency: 'MNT', bedrooms,
      bathrooms, parkingSpaces, landSizeSqm, address: { suburb, addressLine, state: 'УБ', postcode: '17000', latitude, longitude } };
    const { body: created } = await json(`${listing}/v1/properties`, 201, { method: 'POST',
      headers: { ...headers, 'Idempotency-Key': key }, body: JSON.stringify(draft) });
    const { body: current } = await json(`${listing}/v1/properties/${created.id}`, 200, { headers });
    if (current.status === 'DRAFT') await json(`${listing}/v1/properties/${created.id}/status`, 200, {
      method: 'PATCH', headers: { ...headers, 'If-Match': `"${current.version}"` }, body: JSON.stringify({ status: 'ACTIVE' }) });
    added += 1;
  }
  console.info(`Local Mongolian samples: ${added} added; ${samples.length - added} retained. Agent credentials remain in .local.env.`);
}

main().catch(error => { console.error(`Local sample setup failed: ${error.message}`); process.exitCode = 1; });
