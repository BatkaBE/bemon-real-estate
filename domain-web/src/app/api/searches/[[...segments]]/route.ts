import { NextRequest, NextResponse } from 'next/server';
import { mutation } from '@/lib/mutation';
import { isUuid } from '@/lib/security';
/** Permits only self-service saved-search writes. */
async function route(request: NextRequest, context: { params: Promise<{ segments?: string[] }> }) {
  const segments = (await context.params).segments || [];
  if (!segments.length && request.method === 'POST') return mutation(request, 'search', '/v1/users/me/searches', { body: value => ({ name: value.name, criteria: value.criteria, emailEnabled: value.emailEnabled }) });
  if (segments.length === 1 && isUuid(segments[0]) && request.method === 'DELETE') return mutation(request, 'search', `/v1/users/me/searches/${segments[0]}`);
  return NextResponse.json({}, { status: 404 });
}
export const POST = route; export const DELETE = route;
