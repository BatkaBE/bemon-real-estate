import { NextRequest, NextResponse } from 'next/server';
import { mutation } from '@/lib/mutation';
type Context = { params: Promise<{ segments?: string[] }> };
/** Selects only implemented self-service identity operations. */
async function route(request: NextRequest, context: Context) {
  const path = ((await context.params).segments || []).join('/');
  if (request.method === 'PUT' && path === '') return mutation(request, 'identity', '/v1/users/me',
    { body: value => ({ displayName: value.displayName, phone: value.phone }) });
  if (request.method === 'POST' && path === 'verification') return mutation(request, 'identity', '/v1/users/me/verification');
  if (request.method === 'POST' && ['forgot-password', 'reset-password', 'verify-email'].includes(path)) {
    return mutation(request, 'identity', `/v1/accounts/${path}`, { public: true });
  }
  return NextResponse.json({ message: 'Хүсэлт олдсонгүй.' }, { status: 404 });
}
export async function POST(request: NextRequest, context: Context) { return route(request, context); }
export async function PUT(request: NextRequest, context: Context) { return route(request, context); }
