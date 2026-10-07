import Link from 'next/link';
import { redirect } from 'next/navigation';
import { getSession } from '@/lib/session';
import { backend } from '@/lib/api';
import { Purchase, type PaymentOrder } from '@/components/payment';
import { formatPrice } from '@/lib/presentation';
export const metadata = { title: 'Төлбөр ба багц', robots: { index: false } };
/** Shows personal purchase history, subscriptions and server-selected product prices. */
export default async function Payments({ searchParams }: { searchParams: Promise<{ property?: string }> }) {
  const session = await getSession(); if (!session) redirect('/login?next=/payments');
  const propertyId = (await searchParams).property;
  const result = await backend<{ items: PaymentOrder[]; subscriptions: { id: string; expiresAt: string; remaining: number }[] }>('payment', session.roles.includes('ROLE_AGENCY_ADMIN') ? '/v1/admin/payments' : '/v1/users/me/payments', { headers: { Authorization: `Bearer ${session.accessToken}` } });
  const offers = await backend<{ items: { id: string; amount: number }[] }>('payment', '/v1/payments/offers');
  return <section className="container account-page"><h1>Төлбөр ба багц</h1>{session.roles.includes('ROLE_AGENT') && <Purchase propertyId={propertyId} offers={offers.items} />}
    {result.subscriptions.map(plan => <p className="notice" key={plan.id}>Үлдсэн онцлох эрх: {plan.remaining} · {new Date(plan.expiresAt).toLocaleDateString('mn-MN')} хүртэл</p>)}
    {result.items.map(order => <article className="panel conversation" key={order.id}><Link href={`/payments/${order.id}`}>{order.offer === 'AGENT30' ? 'Агентын багц' : 'Онцлох зар'} · {formatPrice(order.amount, 'MNT')} · {order.status}</Link></article>)}{!result.items.length && <p>Төлбөрийн түүх алга.</p>}
  </section>;
}
