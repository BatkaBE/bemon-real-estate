import { NextRequest, NextResponse } from 'next/server';
import { mutation } from '@/lib/mutation';
import { backend, ApiError } from '@/lib/api';
import { getSession } from '@/lib/session';
import { isUuid } from '@/lib/security';
/** Selects only named payment operations; prices and user IDs are always supplied by the backend. */
async function route(request: NextRequest, context: { params: Promise<{ segments?: string[] }> }) {
  const segments = (await context.params).segments || []; const key = request.headers.get('idempotency-key') || '';
  if (request.method === 'GET' && segments.length === 1 && isUuid(segments[0])) {
    const session = await getSession(); if (!session) return NextResponse.json({}, { status: 401 });
    try { return NextResponse.json(await backend('payment', session.roles.includes('ROLE_AGENCY_ADMIN') ? `/v1/admin/payments/${segments[0]}` : `/v1/payments/orders/${segments[0]}`, { headers: { Authorization: `Bearer ${session.accessToken}` } }), { headers: { 'Cache-Control': 'no-store' } }); }
    catch (failure) { return NextResponse.json({}, { status: failure instanceof ApiError ? failure.status : 503 }); }
  }
  if (request.method !== 'POST') return NextResponse.json({}, { status: 405 });
  if (!segments.length || segments[0] === 'credits') {
    if (!isUuid(key)) return NextResponse.json({}, { status: 400 });
    return mutation(request, 'payment', segments.length ? '/v1/payments/credits' : '/v1/payments/orders', { role: 'ROLE_AGENT', headers: { 'Idempotency-Key': key },
      body: value => segments.length ? ({ propertyId: value.propertyId }) : ({ offer: value.offer, propertyId: value.propertyId }) });
  }
  if (segments.length === 2 && isUuid(segments[0]) && ['check', 'cancel', 'simulate', 'refund'].includes(segments[1])) {
    const admin = ['simulate', 'refund'].includes(segments[1]);
    return mutation(request, 'payment', admin ? `/v1/admin/payments/${segments[0]}/${segments[1]}` : `/v1/payments/orders/${segments[0]}/${segments[1]}`, { role: admin ? 'ROLE_AGENCY_ADMIN' : undefined });
  }
  return NextResponse.json({}, { status: 404 });
}
export const GET = route; export const POST = route;
