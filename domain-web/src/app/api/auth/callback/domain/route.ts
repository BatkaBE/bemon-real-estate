import { timingSafeEqual } from 'node:crypto';
import { NextRequest, NextResponse } from 'next/server';
import { settings } from '@/lib/config';
import { FLOW_COOKIE, SESSION_COOKIE, cookieOptions, createSession, unseal, validateTokens, SESSION_LIFETIME } from '@/lib/session';
import { safeReturnPath } from '@/lib/security';

/** Consumes the OAuth transaction, verifies ID/access tokens, and stores credentials in an encrypted cookie. */
export async function GET(request: NextRequest) {
  const config = settings();
  const flowCookie = request.cookies.get(FLOW_COOKIE)?.value;
  const flow = flowCookie ? await unseal(flowCookie) : null;
  const state = request.nextUrl.searchParams.get('state') || '';
  const code = request.nextUrl.searchParams.get('code');
  const validState = typeof flow?.state === 'string' && /^[A-Za-z0-9_-]{32}$/.test(state)
    && flow.state.length === state.length
    && timingSafeEqual(Buffer.from(flow.state), Buffer.from(state));
  if (!flow || !validState || !code || typeof flow.verifier !== 'string' || typeof flow.nonce !== 'string') {
    const response = NextResponse.redirect(new URL('/login?error=flow', config.appOrigin));
    response.cookies.delete(FLOW_COOKIE);
    return response;
  }
  try {
    const exchange = await fetch(`${config.identityInternal}/oauth2/token`, { method: 'POST', cache: 'no-store',
      signal: AbortSignal.timeout(12000), headers: { Authorization: `Basic ${Buffer.from(`${config.clientId}:${config.clientSecret}`).toString('base64')}` },
      body: new URLSearchParams({ grant_type: 'authorization_code', code, code_verifier: flow.verifier,
        redirect_uri: `${config.appOrigin}/api/auth/callback/domain` }) });
    if (!exchange.ok) throw new Error('OAuth exchange rejected');
    const session = await validateTokens(await exchange.json(), flow.nonce);
    const response = NextResponse.redirect(new URL(safeReturnPath(typeof flow.next === 'string' ? flow.next : null), config.appOrigin));
    response.headers.set('Cache-Control', 'no-store');
    response.cookies.delete(FLOW_COOKIE);
    response.cookies.set(SESSION_COOKIE, await createSession(session), cookieOptions(SESSION_LIFETIME));
    return response;
  } catch {
    const response = NextResponse.redirect(new URL('/login?error=auth', config.appOrigin));
    response.cookies.delete(FLOW_COOKIE);
    return response;
  }
}
