import 'server-only';
import { NextRequest, NextResponse } from 'next/server';
import { backend, ApiError, errorMessage } from './api';
import { settings } from './config';
import { getSession } from './session';
import { isSameOrigin } from './security';
const MAX_JSON_BYTES = 65536;

/** Reads bounded JSON even when a client uses chunked transfer rather than Content-Length. */
export async function readJson(request: NextRequest): Promise<Record<string, unknown>> {
  if (!request.body) return {};
  const reader = request.body.getReader(); const chunks: Uint8Array[] = []; let size = 0;
  try {
    while (true) {
      const chunk = await reader.read(); if (chunk.done) break;
      size += chunk.value.byteLength;
      if (size > MAX_JSON_BYTES) { await reader.cancel(); throw new ApiError(413); }
      chunks.push(chunk.value);
    }
    const text = Buffer.concat(chunks).toString('utf8');
    const value = text ? JSON.parse(text) : {};
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new ApiError(400);
    return value;
  } catch (failure) { throw failure instanceof ApiError ? failure : new ApiError(400); }
}

/** Mutates only a route selected by server code after Origin/session/body validation. */
export async function mutation(request: NextRequest, service: 'identity' | 'listing' | 'search' | 'payment', path: string,
  options: { public?: boolean; role?: string; body?: (value: Record<string, unknown>) => unknown; headers?: Record<string, string> } = {}) {
  if (!isSameOrigin(request.headers.get('origin'), settings().appOrigin)) return NextResponse.json({ message: errorMessage(403) }, { status: 403 });
  const session = options.public ? null : await getSession();
  if (!options.public && !session) return NextResponse.json({ message: errorMessage(401) }, { status: 401 });
  if (options.role && !session?.roles.includes(options.role)) return NextResponse.json({ message: errorMessage(403) }, { status: 403 });
  try {
    const body = await readJson(request);
    const value = await backend(service, path, { method: request.method,
      headers: { 'Content-Type': 'application/json', ...(session ? { Authorization: `Bearer ${session.accessToken}` } : {}), ...options.headers },
      ...(request.method === 'DELETE' ? {} : { body: JSON.stringify(options.body ? options.body(body) : body) }) });
    return NextResponse.json(value, { headers: { 'Cache-Control': 'no-store' } });
  } catch (failure) {
    const status = failure instanceof ApiError ? failure.status : 400;
    return NextResponse.json({ message: errorMessage(status) }, { status });
  }
}
