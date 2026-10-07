import assert from 'node:assert/strict';
import { randomBytes, randomUUID } from 'node:crypto';
import { identity, listing, json, login } from './lib/api.mjs';

// This check creates labelled synthetic accounts and one listing in the local stack.
const clientSecret = process.env.OAUTH_WEB_CLIENT_SECRET;
const adminEmail = process.env.DEV_ADMIN_EMAIL;
const adminPassword = process.env.DEV_ADMIN_PASSWORD;
let checks = 0;

/** Reports completed checks without logging credentials, cookies, or tokens. */
function passed(name) {
  checks += 1;
  console.info(`PASS ${name}`);
}

/** Checks the local purchase-independent account-to-listing workflow using real signed tokens. */
async function main() {
  assert.ok(clientSecret && adminEmail && adminPassword, 'Run via bash scripts/local.sh smoke with .local.env');
  for (const [name, base] of [['identity', identity], ['listing', listing]]) {
    const { body } = await json(`${base}/actuator/health/readiness`, 200);
    assert.equal(body.status, 'UP');
    passed(`${name} readiness`);
  }
  const { body: discovery } = await json(`${identity}/.well-known/openid-configuration`, 200);
  assert.equal(discovery.issuer, identity);
  passed('OIDC discovery');
  const admin = await login(adminEmail, adminPassword);
  passed('administrator login + PKCE');
  const timestamp = randomUUID();
  const password = `Smoke#2026${randomBytes(12).toString('hex')}`;
  const agentEmail = `smoke-agent-${timestamp}@example.test`;
  const buyerEmail = `smoke-buyer-${timestamp}@example.test`;
  const { body: agent } = await json(`${identity}/v1/admin/users/agents`, 201, {
    method: 'POST', headers: { Authorization: `Bearer ${admin.access_token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: agentEmail, password }),
  });
  assert.equal(agent.role, 'ROLE_AGENT');
  passed('administrator provisions agent');
  const { body: buyer } = await json(`${identity}/v1/users/register`, 201, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: buyerEmail, password, role: 'ROLE_AGENCY_ADMIN' }),
  });
  assert.equal(buyer.role, 'ROLE_BUYER');
  passed('public registration cannot elevate role');
  const agentTokens = await login(agentEmail, password);
  const buyerTokens = await login(buyerEmail, password);
  passed('agent and buyer login + PKCE');
  const draft = {
    title: `Smoke test ${timestamp}`, propertyType: 'HOUSE', listingType: 'SALE', currency: 'MNT', price: 100000,
    bedrooms: 2, bathrooms: 1, parkingSpaces: 0,
    address: { addressLine: '1 Synthetic Street', suburb: 'Smoke Test', state: 'УБ', postcode: '17000',
      latitude: 47.9188, longitude: 106.9177 },
  };
  const key = randomUUID();
  const createOptions = { method: 'POST', headers: { Authorization: `Bearer ${agentTokens.access_token}`,
    'Content-Type': 'application/json', 'Idempotency-Key': key }, body: JSON.stringify(draft) };
  await json(`${listing}/v1/properties`, 401, { method: 'POST' });
  await json(`${listing}/v1/properties`, 403, { ...createOptions,
    headers: { ...createOptions.headers, Authorization: `Bearer ${buyerTokens.access_token}` } });
  passed('unauthenticated and buyer writes rejected');
  const { body: property, headers: createdHeaders } = await json(`${listing}/v1/properties`, 201, createOptions);
  assert.equal(property.agentId, agent.id);
  const tag = createdHeaders.get('etag');
  assert.ok(tag);
  passed('agent creates draft with ETag');
  await json(`${listing}/v1/properties/${property.id}`, 404);
  passed('private draft hidden from public');
  const { body: duplicate } = await json(`${listing}/v1/properties`, 201, createOptions);
  assert.deepEqual(duplicate, property);
  passed('idempotent retry returns original response');
  await json(`${listing}/v1/properties`, 409, { ...createOptions,
    body: JSON.stringify({ ...draft, title: 'Conflicting payload' }) });
  passed('changed idempotency payload rejected');
  const { headers: publishedHeaders } = await json(`${listing}/v1/properties/${property.id}/status`, 200, {
    method: 'PATCH', headers: { Authorization: `Bearer ${agentTokens.access_token}`,
      'Content-Type': 'application/json', 'If-Match': tag }, body: JSON.stringify({ status: 'ACTIVE' }),
  });
  assert.notEqual(publishedHeaders.get('etag'), tag);
  await json(`${listing}/v1/properties/${property.id}`, 200);
  passed('publish advances ETag and exposes public details');
  const { body: retained } = await json(`${listing}/v1/properties`, 201, createOptions);
  assert.deepEqual(retained, property);
  passed('create response retained after publishing');
  await json(`${listing}/v1/properties/${property.id}`, 412, { method: 'PUT',
    headers: { Authorization: `Bearer ${agentTokens.access_token}`, 'Content-Type': 'application/json',
      'If-Match': tag }, body: JSON.stringify(draft) });
  passed('stale ETag rejected');
  const { body: page } = await json(`${listing}/v1/properties?suburb=Smoke%20Test&pageSize=100`, 200);
  assert.ok(page.items.some((item) => item.id === property.id));
  passed('published listing browsable');
  await json(`${listing}/v1/properties/${property.id}/media/upload-url`, 503, {
    method: 'POST', headers: { Authorization: `Bearer ${agentTokens.access_token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ contentType: 'image/jpeg', contentLength: 1024, displayOrder: 0 }),
  });
  passed('unconfigured storage returns explicit 503');
  await json(`${listing}/v1/properties/${property.id}/status`, 200, { method: 'PATCH',
    headers: { ...createOptions.headers, 'If-Match': publishedHeaders.get('etag') },
    body: JSON.stringify({ status: 'WITHDRAWN' }) });
  await json(`${listing}/v1/properties/${property.id}`, 404);
  passed('synthetic test listing withdrawn and retained');
  console.info(`Completed ${checks} checks. Synthetic accounts and listing retained: ${property.id}`);
}

main().catch((error) => {
  console.error(`Smoke check failed: ${error.message}`);
  process.exitCode = 1;
});
