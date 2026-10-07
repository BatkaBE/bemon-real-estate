import { readJson } from '@/lib/mutation';
import { NextRequest, NextResponse } from 'next/server';
import { backend, ApiError, errorMessage } from '@/lib/api';
import { getSession } from '@/lib/session';
import { settings } from '@/lib/config';
import { isSameOrigin } from '@/lib/security';

/** Protects agent provisioning with both cookie-origin checks and backend administrator authorization. */
export async function POST(request: NextRequest) {
  if (!isSameOrigin(request.headers.get('origin'), settings().appOrigin)) return NextResponse.json({ message: errorMessage(403) }, { status: 403 });
  const session = await getSession();
  if (!session) return NextResponse.json({ message: errorMessage(401) }, { status: 401 });
  if (!session.roles.includes('ROLE_AGENCY_ADMIN')) return NextResponse.json({ message: errorMessage(403) }, { status: 403 });
  try {
    const body = await readJson(request);
    await backend('identity', '/v1/admin/users/agents', { method: 'POST',
      headers: { Authorization: `Bearer ${session.accessToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: body.email, password: body.password }) });
    return NextResponse.json({ message: 'Агентын бүртгэл үүслээ. Имэйл, нууц үгийг тухайн агентдаа өгнө үү.' }, { status: 201 });
  } catch (error) {
    const status = error instanceof ApiError ? error.status : 400;
    return NextResponse.json({ message: status === 409 ? 'Энэ имэйл бүртгэлтэй байна.' : errorMessage(status) }, { status });
  }
}
