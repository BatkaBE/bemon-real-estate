import { notFound, redirect } from 'next/navigation';
import { getSession } from '@/lib/session';
import { backend, ApiError } from '@/lib/api';
import { OrderView, type PaymentOrder } from '@/components/payment';
import { isUuid } from '@/lib/security';
export const metadata = { title: 'Төлбөрийн захиалга', robots: { index: false } };
/** Withholds another account's order even from a manually supplied URL. */
export default async function Order({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params; if (!isUuid(id)) notFound(); const session = await getSession(); if (!session) redirect(`/login?next=/payments/${id}`);
  let order: PaymentOrder;
  try { order = await backend<PaymentOrder>('payment', session.roles.includes('ROLE_AGENCY_ADMIN') ? `/v1/admin/payments/${id}` : `/v1/payments/orders/${id}`, { headers: { Authorization: `Bearer ${session.accessToken}` } }); }
  catch (failure) { if (failure instanceof ApiError && failure.status === 404) notFound(); throw failure; }
  return <section className="container account-page"><h1>Төлбөрийн захиалга</h1><OrderView order={order} admin={session.roles.includes('ROLE_AGENCY_ADMIN')} /></section>;
}
