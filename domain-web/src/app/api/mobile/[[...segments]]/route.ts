import { NextRequest, NextResponse } from 'next/server';
import { backend, ApiError, errorMessage } from '@/lib/api';
import { settings } from '@/lib/config';
import { readJson } from '@/lib/mutation';
import { createSession, destroySessionByHandle, getSessionByHandle, validateTokens } from '@/lib/session';
import { isUuid } from '@/lib/security';

/** Accepts native bearer handles and an explicit development-web origin without ambient cookie authentication. */
async function route(request: NextRequest, context: { params: Promise<{ segments?: string[] }> }) {
  const config = settings(); const origin = request.headers.get('origin');
  if (origin && origin !== config.mobileWebOrigin) return NextResponse.json({ message: errorMessage(403) }, { status: 403 });
  const cors: Record<string, string> = origin ? { 'Access-Control-Allow-Origin': origin, Vary: 'Origin', 'Access-Control-Allow-Headers': 'Authorization, Content-Type, Idempotency-Key', 'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS' } : {};
  if (request.method === 'OPTIONS') return new NextResponse(null, { status: 204, headers: cors });
  const segments = (await context.params).segments || []; const name = segments.join('/');
  try {
    if (name === 'exchange' && request.method === 'POST') {
      const body = await readJson(request);
      if (typeof body.code !== 'string' || body.code.length > 2048 || typeof body.verifier !== 'string' || !/^[A-Za-z0-9._~-]{43,128}$/.test(body.verifier)
          || typeof body.nonce !== 'string' || body.nonce.length > 128 || !['bemon://oauth', `${config.mobileWebOrigin}/oauth`].includes(String(body.redirectUri))) throw new ApiError(400);
      const exchange = await fetch(`${config.identityInternal}/oauth2/token`, { method: 'POST', cache: 'no-store', signal: AbortSignal.timeout(12000),
        headers: { Authorization: `Basic ${Buffer.from(`${config.mobileClientId}:${config.mobileClientSecret}`).toString('base64')}` },
        body: new URLSearchParams({ grant_type: 'authorization_code', code: body.code, code_verifier: body.verifier, redirect_uri: String(body.redirectUri) }) });
      if (!exchange.ok) throw new ApiError(401);
      const session = await validateTokens(await exchange.json(), body.nonce, config.mobileClientId).catch(() => { throw new ApiError(401); });
      return NextResponse.json({ handle: await createSession(session) }, { headers: { ...cors, 'Cache-Control': 'no-store' } });
    }
    const authorization = request.headers.get('authorization') || ''; const handle = authorization.startsWith('Bearer ') ? authorization.slice(7) : undefined;
    if (name === 'logout' && request.method === 'POST') { await destroySessionByHandle(handle); return NextResponse.json({ status: 'logged-out' }, { headers: cors }); }
    let service: 'listing' | 'identity' | 'search' = 'listing'; let path = ''; let anonymous = false;
    if (name === 'search' && request.method === 'GET') { service = 'search'; path = `/v1/search?${request.nextUrl.searchParams}`; anonymous = true; }
    else if (segments[0] === 'properties' && segments.length === 2 && isUuid(segments[1]) && request.method === 'GET') { path = `/v1/properties/${segments[1]}`; anonymous = true; }
    else if (name === 'account' && ['GET', 'PUT'].includes(request.method)) { service = 'identity'; path = '/v1/users/me'; }
    else if (name === 'favorites' && request.method === 'GET') path = `/v1/users/me/favorites?${request.nextUrl.searchParams}`;
    else if (segments[0] === 'favorites' && segments.length === 2 && isUuid(segments[1]) && ['PUT', 'DELETE'].includes(request.method)) path = `/v1/users/me/favorites/${segments[1]}`;
    else if (name === 'inquiries' && request.method === 'GET') path = `/v1/users/me/inquiries?${request.nextUrl.searchParams}`;
    else if (segments[0] === 'inquiries' && segments.length === 2 && isUuid(segments[1]) && request.method === 'POST') path = `/v1/properties/${segments[1]}/inquiries`;
    else if (name === 'searches' && ['GET', 'POST'].includes(request.method)) { service = 'search'; path = '/v1/users/me/searches'; }
    else if (segments[0] === 'searches' && segments.length === 2 && isUuid(segments[1]) && request.method === 'DELETE') { service = 'search'; path = `/v1/users/me/searches/${segments[1]}`; }
    else if (name === 'alerts' && request.method === 'GET') { service = 'search'; path = '/v1/users/me/alerts'; }
    else if (segments[0] === 'alerts' && segments.length === 2 && isUuid(segments[1]) && request.method === 'POST') { service = 'search'; path = `/v1/users/me/alerts/${segments[1]}/read`; }
    else throw new ApiError(404);
    const session = anonymous ? null : await getSessionByHandle(handle);
    if (!anonymous && !session) throw new ApiError(401);
    const key = request.headers.get('idempotency-key');
    if (request.method === 'POST' && segments[0] === 'inquiries' && (!key || !isUuid(key))) throw new ApiError(400);
    const body = request.method === 'GET' || request.method === 'DELETE' ? undefined : await readJson(request);
    const safeBody = name === 'account' && body ? { displayName: body.displayName, phone: body.phone } : body;
    const result = await backend<Record<string, unknown>>(service, path, { method: request.method, headers: { 'Content-Type': 'application/json',
      ...(session ? { Authorization: `Bearer ${session.accessToken}` } : {}), ...(key ? { 'Idempotency-Key': key } : {}) },
      ...(safeBody === undefined ? {} : { body: JSON.stringify(safeBody) }) });
    if (name === 'search' && Array.isArray(result.items)) {
      const current = await Promise.all(result.items.map(async item => { try { return await backend<{ id: string; status: string }>('listing', '/v1/properties/' + item.id); } catch { return null; } }));
      result.items = current.filter(item => item?.status === 'ACTIVE');
    }
    return NextResponse.json(result, { headers: { ...cors, 'Cache-Control': 'no-store' } });
  } catch (failure) {
    const status = failure instanceof ApiError ? failure.status : 503;
    return NextResponse.json({ message: errorMessage(status) }, { status, headers: cors });
  }
}
export const GET = route; export const POST = route; export const PUT = route; export const DELETE = route; export const OPTIONS = route;
