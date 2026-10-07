import { NextResponse } from 'next/server';
/** Reports only process liveness without exposing configuration. */
export function GET() { return NextResponse.json({ status: 'UP' }, { headers: { 'Cache-Control': 'no-store' } }); }
