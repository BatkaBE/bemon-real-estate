import { readJson } from '@/lib/mutation';
import { NextRequest, NextResponse } from 'next/server';
import { ApiError, backend, errorMessage } from '@/lib/api';
import { settings } from '@/lib/config';
import { getSession } from '@/lib/session';
import { isSameOrigin, isUuid } from '@/lib/security';
import type { Property } from '@/lib/types';

/** Restricts the cookie-authenticated adapter to the three implemented listing mutations. */
async function mutate(request: NextRequest, segments: string[], method: 'POST' | 'PUT' | 'PATCH') {
  if (!isSameOrigin(request.headers.get('origin'), settings().appOrigin)) return NextResponse.json({ message: errorMessage(403) }, { status: 403 });
  const session = await getSession();
  if (!session) return NextResponse.json({ message: errorMessage(401) }, { status: 401 });
  if (!session.roles.includes('ROLE_AGENT')) return NextResponse.json({ message: errorMessage(403) }, { status: 403 });
  const validPath = (method === 'POST' && segments.length === 0)
    || (method === 'PUT' && segments.length === 1 && isUuid(segments[0]))
    || (method === 'PATCH' && segments.length === 2 && isUuid(segments[0]) && segments[1] === 'status');
  if (!validPath) return NextResponse.json({ message: errorMessage(404) }, { status: 404 });
  const headers: Record<string, string> = { Authorization: `Bearer ${session.accessToken}`, 'Content-Type': 'application/json' };
  if (method === 'POST') {
    const key = request.headers.get('idempotency-key');
    if (!key || !isUuid(key)) return NextResponse.json({ message: errorMessage(400) }, { status: 400 });
    headers['Idempotency-Key'] = key;
  } else {
    const tag = request.headers.get('if-match');
    if (!tag || !/^"[0-9]+"$/.test(tag)) return NextResponse.json({ message: errorMessage(400) }, { status: 400 });
    headers['If-Match'] = tag;
  }
  try {
    const body = await readJson(request);
    const property = await backend<Property>('listing', `/v1/properties${segments.length ? '/' + segments.join('/') : ''}`,
      { method, headers, body: JSON.stringify(body) });
    return NextResponse.json(property, { status: method === 'POST' ? 201 : 200,
      headers: { ETag: `"${property.version}"`, 'Cache-Control': 'no-store' } });
  } catch (error) {
    const status = error instanceof ApiError ? error.status : 400;
    return NextResponse.json({ message: errorMessage(status) }, { status });
  }
}
type Context = { params: Promise<{ segments?: string[] }> };
/** Creates a property through the verified agent session. */
export async function POST(request: NextRequest, context: Context) { return mutate(request, (await context.params).segments || [], 'POST'); }
/** Replaces a property while forwarding its strong ETag. */
export async function PUT(request: NextRequest, context: Context) { return mutate(request, (await context.params).segments || [], 'PUT'); }
/** Publishes or transitions a property using the same ETag boundary. */
export async function PATCH(request: NextRequest, context: Context) { return mutate(request, (await context.params).segments || [], 'PATCH'); }
