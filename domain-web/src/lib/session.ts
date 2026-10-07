import 'server-only';
import { cookies } from 'next/headers';
import { createRemoteJWKSet, EncryptJWT, jwtDecrypt, jwtVerify } from 'jose';
import { settings } from './config';
import type { Session } from './types';
import { randomUUID } from 'node:crypto';
export const SESSION_COOKIE = 'bemon_session';
export const FLOW_COOKIE = 'bemon_oauth_flow';
const COOKIE_LIMIT_BYTES = 3800;
export const SESSION_LIFETIME = 30 * 86400;
let jwks: ReturnType<typeof createRemoteJWKSet> | undefined;
/** Loads a mandatory 256-bit encryption key without a fallback secret. */
function encryptionKey(): Uint8Array {
  const value = settings().sessionSecret;
  if (!/^[a-f0-9]{64}$/i.test(value)) throw new Error('WEB_SESSION_SECRET must contain 32 random hex bytes');
  return Buffer.from(value, 'hex');
}
/** Encrypts a cookie with an enforced lifetime. */
export async function seal(value: Record<string, unknown>, ttlSeconds: number): Promise<string> {
  const token = await new EncryptJWT(value).setProtectedHeader({ alg: 'dir', enc: 'A256GCM' })
    .setIssuer('bemon-web').setAudience('bemon-web').setIssuedAt().setExpirationTime(`${ttlSeconds}s`)
    .encrypt(encryptionKey());
  if (Buffer.byteLength(token) > COOKIE_LIMIT_BYTES) throw new Error('Session exceeds the cookie size limit');
  return token;
}
/** Rejects expired or modified session and OAuth transaction cookies. */
export async function unseal(value: string): Promise<Record<string, unknown> | null> {
  try {
    const { payload } = await jwtDecrypt(value, encryptionKey(), { issuer: 'bemon-web', audience: 'bemon-web',
      keyManagementAlgorithms: ['dir'], contentEncryptionAlgorithms: ['A256GCM'] });
    return payload;
  } catch { return null; }
}
/** Protects cookies from JavaScript and enables Secure on HTTPS. */
export function cookieOptions(ttlSeconds: number) {
  return { httpOnly: true, secure: new URL(settings().appOrigin).protocol === 'https:',
    sameSite: 'lax' as const, path: '/', maxAge: ttlSeconds };
}
/** Talks to shared session persistence without exposing credentials to the browser. */
async function sessionStore(path: string, method = 'GET', body?: unknown) {
  const config = settings();
  const response = await fetch(`${config.identityInternal}/internal/web-sessions${path}`, { method,
    headers: { 'X-Internal-Key': config.internalKey, 'Content-Type': 'application/json' }, cache: 'no-store',
    signal: AbortSignal.timeout(5000), body: body === undefined ? undefined : JSON.stringify(body) });
  if (!response.ok) throw new Error('Session storage unavailable');
  return response.json();
}
/** Persists encrypted credentials and returns only an opaque encrypted handle for the cookie. */
export async function createSession(session: Session): Promise<string> {
  const id = randomUUID();
  await sessionStore('', 'POST', { id, userId: session.userId, payload: await seal({ ...session }, SESSION_LIFETIME) });
  return seal({ sessionId: id }, SESSION_LIFETIME);
}
/** Deletes the shared session in addition to removing the browser cookie. */
export async function destroySession(): Promise<void> {
  return destroySessionByHandle((await cookies()).get(SESSION_COOKIE)?.value);
}
/** Revokes a device or browser handle in the shared session store. */
export async function destroySessionByHandle(value: string | undefined): Promise<void> {
  const handle = value ? await unseal(value) : null;
  if (typeof handle?.sessionId === 'string') await sessionStore(`/${handle.sessionId}`, 'DELETE');
}
/** Resolves server-side credentials and rotates expiring tokens using a durable single-writer lease. */
export async function getSession(): Promise<Session | null> {
  return getSessionByHandle((await cookies()).get(SESSION_COOKIE)?.value);
}
/** Resolves the same durable refresh flow for a native SecureStore handle. */
export async function getSessionByHandle(value: string | undefined): Promise<Session | null> {
  const handle = value ? await unseal(value) : null;
  if (typeof handle?.sessionId !== 'string' || !/^[0-9a-f-]{36}$/.test(handle.sessionId)) return null;
  try {
    let session = await unseal((await sessionStore(`/${handle.sessionId}`)).payload);
    if (!validSession(session)) return null;
    if (session.expiresAt > Date.now() + 60000) return session;
    const leaseId = randomUUID();
    const lease = await sessionStore(`/${handle.sessionId}/lease`, 'POST', { leaseId });
    session = await unseal(lease.payload);
    if (!validSession(session)) return null;
    if (lease.acquired) {
      if (session.expiresAt <= Date.now() + 60000) {
        const config = settings();
        const clientId = session.clientId || config.clientId;
        if (![config.clientId, config.mobileClientId].includes(clientId)) return null;
        const clientSecret = clientId === config.mobileClientId ? config.mobileClientSecret : config.clientSecret;
        const response = await fetch(`${config.identityInternal}/oauth2/token`, { method: 'POST', cache: 'no-store',
          signal: AbortSignal.timeout(10000), headers: { Authorization: `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString('base64')}` },
          body: new URLSearchParams({ grant_type: 'refresh_token', refresh_token: session.refreshToken }) });
        if (!response.ok) { await sessionStore(`/${handle.sessionId}`, 'DELETE'); return null; }
        const tokens = await response.json();
        jwks ||= createRemoteJWKSet(new URL(config.jwksUrl), { timeoutDuration: 10000 });
        const access = await jwtVerify(tokens.access_token, jwks, { issuer: config.issuer, audience: clientId, algorithms: ['RS256'] });
        if (access.payload.sub !== session.userId || !access.payload.exp || typeof tokens.refresh_token !== 'string') return null;
        if (typeof tokens.id_token === 'string') {
          const id = await jwtVerify(tokens.id_token, jwks, { issuer: config.issuer, audience: clientId, algorithms: ['RS256'] });
          if (id.payload.sub !== session.userId) return null;
        }
        session = { ...session, accessToken: tokens.access_token, refreshToken: tokens.refresh_token,
          idToken: typeof tokens.id_token === 'string' ? tokens.id_token : session.idToken,
          expiresAt: access.payload.exp * 1000, roles: Array.isArray(access.payload.roles) ? access.payload.roles.filter((role): role is string => typeof role === 'string') : [] };
      }
      await sessionStore(`/${handle.sessionId}`, 'PUT', { leaseId, payload: await seal({ ...session }, SESSION_LIFETIME) });
      return validSession(session) ? session : null;
    }
    if (session.expiresAt > Date.now() + 5000) return session;
    const deadline = Date.now() + 30000;
    while (Date.now() < deadline) {
      await new Promise(resolve => setTimeout(resolve, 150));
      session = await unseal((await sessionStore(`/${handle.sessionId}`)).payload);
      if (validSession(session) && session.expiresAt > Date.now() + 5000) return session;
    }
    return null;
  } catch { return null; }
}
/** Rejects malformed stored credentials; cookie claims never supply roles or access tokens. */
function validSession(session: Record<string, unknown> | null): session is Record<string, unknown> & Session {
  if (!session || typeof session.userId !== 'string' || !Array.isArray(session.roles)
      || !session.roles.every(role => typeof role === 'string') || typeof session.accessToken !== 'string'
      || typeof session.refreshToken !== 'string' || typeof session.idToken !== 'string'
      || typeof session.expiresAt !== 'number') return false;
  return true;
}
/** Validates signatures and issuer/audience before trusting OAuth identities. */
export async function validateTokens(tokens: Record<string, unknown>, nonce: string, clientId = settings().clientId): Promise<Session> {
  if (typeof tokens.access_token !== 'string' || typeof tokens.id_token !== 'string'
      || typeof tokens.refresh_token !== 'string') throw new Error('OAuth token response is incomplete');
  const config = settings();
  jwks ||= createRemoteJWKSet(new URL(config.jwksUrl), { timeoutDuration: 10000 });
  const options = { issuer: config.issuer, audience: clientId, algorithms: ['RS256'] };
  const [id, access] = await Promise.all([
    jwtVerify(tokens.id_token, jwks, options), jwtVerify(tokens.access_token, jwks, options),
  ]);
  if (id.payload.nonce !== nonce || !id.payload.sub || access.payload.sub !== id.payload.sub
      || !access.payload.exp) throw new Error('OAuth identity validation failed');
  const roles = Array.isArray(access.payload.roles)
    ? access.payload.roles.filter((role): role is string => typeof role === 'string') : [];
  return { clientId, userId: id.payload.sub, roles, accessToken: tokens.access_token, refreshToken: tokens.refresh_token,
    idToken: tokens.id_token, expiresAt: access.payload.exp * 1000 };
}
