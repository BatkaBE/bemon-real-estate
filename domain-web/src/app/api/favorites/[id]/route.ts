import { NextRequest, NextResponse } from 'next/server';
import { isUuid } from '@/lib/security';
import { mutation } from '@/lib/mutation';
type Context = { params: Promise<{ id: string }> };
/** Restricts saved-listing mutations to the authenticated principal's relationship. */
async function route(request: NextRequest, context: Context) {
  const { id } = await context.params;
  if (!isUuid(id)) return NextResponse.json({ message: 'Зар олдсонгүй.' }, { status: 404 });
  return mutation(request, 'listing', `/v1/users/me/favorites/${id}`);
}
export async function PUT(request: NextRequest, context: Context) { return route(request, context); }
export async function DELETE(request: NextRequest, context: Context) { return route(request, context); }
