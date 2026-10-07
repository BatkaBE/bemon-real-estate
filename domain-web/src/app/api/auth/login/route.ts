import { createHash, randomBytes } from 'node:crypto';
import { NextRequest, NextResponse } from 'next/server';
import { settings } from '@/lib/config';
import { FLOW_COOKIE, seal, cookieOptions } from '@/lib/session';
import { safeReturnPath } from '@/lib/security';

/** Starts a bound PKCE/state/nonce transaction before redirecting to Identity's browser login. */
export async function GET(request: NextRequest) {
  const config = settings();
  if (!config.clientSecret || !config.sessionSecret) return NextResponse.json({ message: 'Нэвтрэх тохиргоо бэлэн биш байна.' }, { status: 503 });
  const verifier = randomBytes(48).toString('base64url');
  const state = randomBytes(24).toString('base64url');
  const nonce = randomBytes(24).toString('base64url');
  const next = safeReturnPath(request.nextUrl.searchParams.get('next'));
  const url = new URL('/oauth2/authorize', config.issuer);
  url.search = new URLSearchParams({ client_id: config.clientId, response_type: 'code',
    redirect_uri: `${config.appOrigin}/api/auth/callback/domain`, scope: 'openid profile listings:write',
    code_challenge: createHash('sha256').update(verifier).digest('base64url'), code_challenge_method: 'S256', state, nonce }).toString();
  const response = NextResponse.redirect(url);
  response.headers.set('Cache-Control', 'no-store');
  response.cookies.set(FLOW_COOKIE, await seal({ verifier, state, nonce, next }, 600), cookieOptions(600));
  return response;
}
