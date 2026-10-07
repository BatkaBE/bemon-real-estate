import Link from 'next/link';
import { redirect } from 'next/navigation';
import { getSession } from '@/lib/session';
import { backend } from '@/lib/api';
import type { PropertyPage } from '@/lib/types';
import { PropertyCard } from '@/components/property-card';
import { FavoriteButton } from '@/components/engagement';
export const metadata = { title: 'Хадгалсан зар', robots: { index: false, follow: false } };
/** Shows the caller's available saved listings with keyset pagination. */
export default async function Favorites({ searchParams }: { searchParams: Promise<{ cursor?: string }> }) {
  const session = await getSession(); if (!session) redirect('/login?next=/favorites');
  const { cursor } = await searchParams; const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : '';
  const page = await backend<PropertyPage>('listing', `/v1/users/me/favorites${query}`, { headers: { Authorization: `Bearer ${session.accessToken}` } });
  return <section className="container dashboard-section"><div className="section-heading"><div><span className="eyebrow">ТАНЫ СОНГОЛТ</span><h1>Хадгалсан зарууд</h1></div></div>
    {page.items.length ? <div className="property-grid">{page.items.map(property => <div key={property.id}><PropertyCard property={property} /><div className="saved-action"><FavoriteButton id={property.id} saved /></div></div>)}</div> : <div className="empty-state"><h2>Хадгалсан зар алга</h2><p>Танд таалагдсан зарын дэлгэрэнгүйгээс хадгалаарай.</p><Link className="button" href="/">Зар үзэх</Link></div>}
    {page.nextCursor && <div className="pagination"><Link className="button button-outline" href={`/favorites?cursor=${encodeURIComponent(page.nextCursor)}`}>Дараагийн зарууд →</Link></div>}</section>;
}
