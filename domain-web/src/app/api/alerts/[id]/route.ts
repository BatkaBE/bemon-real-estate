import { NextRequest, NextResponse } from 'next/server';
import { mutation } from '@/lib/mutation';
import { isUuid } from '@/lib/security';
/** Marks only the authenticated user's personal alert. */
export async function POST(request: NextRequest, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  return isUuid(id) ? mutation(request, 'search', `/v1/users/me/alerts/${id}/read`) : NextResponse.json({}, { status: 400 });
}
