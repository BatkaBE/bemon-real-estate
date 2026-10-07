import { NextRequest, NextResponse } from 'next/server';
import { settings } from '@/lib/config';
import { getSession, destroySession, SESSION_COOKIE, FLOW_COOKIE } from '@/lib/session';
import { isSameOrigin } from '@/lib/security';

/** Deletes the local session, revokes refresh credentials, and ends the browser's Identity session. */
export async function POST(request: NextRequest) {
  const config = settings();
  if (!isSameOrigin(request.headers.get('origin'), config.appOrigin)) return NextResponse.json({ message: 'Хүсэлтийн эх үүсвэр буруу байна.' }, { status: 403 });
  const session = await getSession();
  try { await destroySession(); } catch { /* Browser logout continues during a storage outage. */ }
  let redirect = new URL('/login', config.appOrigin);
  if (session) {
    try {
      await fetch(`${config.identityInternal}/oauth2/revoke`, { method: 'POST', cache: 'no-store',
        signal: AbortSignal.timeout(10000), headers: { Authorization: `Basic ${Buffer.from(`${config.clientId}:${config.clientSecret}`).toString('base64')}` },
        body: new URLSearchParams({ token: session.refreshToken, token_type_hint: 'refresh_token' }) });
    } catch { /* Local logout must still clear the browser session if Identity is temporarily offline. */ }
    redirect = new URL('/connect/logout', config.issuer);
    redirect.search = new URLSearchParams({ id_token_hint: session.idToken,
      post_logout_redirect_uri: `${config.appOrigin}/login` }).toString();
  }
  const response = NextResponse.redirect(redirect, 303);
  response.cookies.delete(SESSION_COOKIE);
  response.cookies.delete(FLOW_COOKIE);
  return response;
}
