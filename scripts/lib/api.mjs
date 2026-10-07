import assert from 'node:assert/strict';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
export const identity = `http://localhost:${process.env.BEMON_IDENTITY_PORT || 9000}`;
export const listing = `http://localhost:${process.env.BEMON_LISTING_PORT || 8080}`;
const redirectUri = process.env.OAUTH_WEB_REDIRECT_URI || 'http://localhost:3000/api/auth/callback/domain';
const clientId = process.env.OAUTH_WEB_CLIENT_ID || 'domain-web';
const clientSecret = process.env.OAUTH_WEB_CLIENT_SECRET;
const checkTimeoutMs = 15000;

/** Performs a bounded HTTP request and keeps redirects visible to the caller. */
export async function request(url, options = {}) {
  return fetch(url, { ...options, redirect: 'manual', signal: AbortSignal.timeout(checkTimeoutMs) });
}

/** Checks a JSON API response and returns its body together with headers. */
export async function json(url, expectedStatus, options = {}) {
  const response = await request(url, options);
  assert.equal(response.status, expectedStatus, `${new URL(url).pathname}: unexpected HTTP status`);
  return { body: await response.json(), headers: response.headers };
}

/** Uses interactive login with CSRF and an actual Authorization Code + PKCE exchange. */
export async function login(email, password, options = {}) {
  const chosenClient = options.clientId || clientId;
  const chosenRedirect = options.redirectUri || redirectUri;
  const chosenSecret = options.clientSecret || clientSecret;
  const cookies = new Map();
  async function sessionRequest(url, options = {}) {
    const headers = new Headers(options.headers);
    headers.set('Cookie', [...cookies].map(([key, value]) => `${key}=${value}`).join('; '));
    const response = await request(url, { ...options, headers });
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';', 1)[0];
      const separator = pair.indexOf('=');
      cookies.set(pair.slice(0, separator), pair.slice(separator + 1));
    }
    return response;
  }
  const page = await sessionRequest(`${identity}/login`);
  assert.equal(page.status, 200, 'Login page unavailable');
  const csrfToken = (await page.text()).match(/name="_csrf"[^>]*value="([^"]+)"/)?.[1];
  assert.ok(csrfToken, 'Login form must include a CSRF token');
  const loggedIn = await sessionRequest(`${identity}/login`, {
    method: 'POST', body: new URLSearchParams({ username: email, password, _csrf: csrfToken }),
  });
  assert.equal(loggedIn.status, 302, 'Interactive login failed');
  assert.ok(!loggedIn.headers.get('location')?.includes('error'), 'Interactive login rejected credentials');
  const verifier = randomBytes(48).toString('base64url');
  const state = randomUUID();
  const query = new URLSearchParams({
    client_id: chosenClient, response_type: 'code', redirect_uri: chosenRedirect,
    scope: options.scope || 'openid profile listings:write', state, ...(options.nonce ? { nonce: options.nonce } : {}),
    code_challenge: createHash('sha256').update(verifier).digest('base64url'), code_challenge_method: 'S256',
  });
  const authorized = await sessionRequest(`${identity}/oauth2/authorize?${query}`);
  assert.equal(authorized.status, 302, 'OAuth authorization failed');
  const callback = new URL(authorized.headers.get('location'));
  const configuredCallback = new URL(chosenRedirect);
  assert.equal(callback.origin + callback.pathname, configuredCallback.origin + configuredCallback.pathname);
  assert.equal(callback.searchParams.get('state'), state, 'OAuth state mismatch');
  assert.ok(callback.searchParams.get('code'), 'OAuth authorization code missing');
  if (options.codeOnly) return { code: callback.searchParams.get('code'), verifier, state };
  const { body } = await json(`${identity}/oauth2/token`, 200, {
    method: 'POST', headers: { Authorization: `Basic ${Buffer.from(`${chosenClient}:${chosenSecret}`).toString('base64')}` },
    body: new URLSearchParams({ grant_type: 'authorization_code', code: callback.searchParams.get('code'),
      redirect_uri: chosenRedirect, code_verifier: verifier }),
  });
  assert.ok(body.access_token && body.refresh_token, 'OAuth tokens missing');
  return body;
}
