import { NextRequest, NextResponse } from 'next/server';
import { isUuid } from '@/lib/security';
import { mutation, readJson } from '@/lib/mutation';
type Context = { params: Promise<{ segments?: string[] }> };
/** Creates requests with a stable idempotency key or replies with a strong ETag. */
async function route(request: NextRequest, context: Context) {
  const segments = (await context.params).segments || [];
  if (request.method === 'POST' && segments.length === 1 && isUuid(segments[0])) {
    const key = request.headers.get('idempotency-key');
    if (!key || !isUuid(key)) return NextResponse.json({ message: 'Хүсэлтийн дугаар буруу байна.' }, { status: 400 });
    return mutation(request, 'listing', `/v1/properties/${segments[0]}/inquiries`, {
      headers: { 'Idempotency-Key': key }, body: value => ({ message: value.message }) });
  }
  if (request.method === 'PATCH' && segments.length === 1 && isUuid(segments[0])) {
    const tag = request.headers.get('if-match');
    if (!tag || !/^"[0-9]{1,18}"$/.test(tag)) return NextResponse.json({ message: 'Хувилбар буруу байна.' }, { status: 400 });
    return mutation(request, 'listing', `/v1/inquiries/${segments[0]}`, { role: 'ROLE_AGENT', headers: { 'If-Match': tag } });
  }
  return NextResponse.json({ message: 'Хүсэлт олдсонгүй.' }, { status: 404 });
}
export async function POST(request: NextRequest, context: Context) { return route(request, context); }
export async function PATCH(request: NextRequest, context: Context) { return route(request, context); }
