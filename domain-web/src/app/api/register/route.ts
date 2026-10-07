import { readJson } from '@/lib/mutation';
import { NextRequest, NextResponse } from 'next/server';
import { backend, ApiError, errorMessage } from '@/lib/api';
import { settings } from '@/lib/config';
import { isSameOrigin } from '@/lib/security';

/** Proxies registration without accepting a caller-selected account role. */
export async function POST(request: NextRequest) {
  if (!isSameOrigin(request.headers.get('origin'), settings().appOrigin)) return NextResponse.json({ message: 'Хүсэлтийн эх үүсвэр буруу байна.' }, { status: 403 });
  try {
    const body = await readJson(request);
    if (typeof body.email !== 'string' || typeof body.password !== 'string') return NextResponse.json({ message: errorMessage(400) }, { status: 400 });
    await backend('identity', '/v1/users/register', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: body.email, password: body.password }) });
    return NextResponse.json({ message: 'Бүртгэл амжилттай. Одоо нэвтэрнэ үү.' }, { status: 201 });
  } catch (error) {
    const status = error instanceof ApiError ? error.status : 400;
    return NextResponse.json({ message: status === 409 ? 'Энэ имэйл бүртгэлтэй байна.' : errorMessage(status) }, { status });
  }
}
