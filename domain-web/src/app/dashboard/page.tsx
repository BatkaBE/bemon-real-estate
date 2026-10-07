import Link from 'next/link';
import { redirect } from 'next/navigation';
import { backend } from '@/lib/api';
import { getSession } from '@/lib/session';
import { PropertyCard } from '@/components/property-card';
import { RegisterForm } from '@/components/register-form';
import type { PropertyPage } from '@/lib/types';
export const metadata = { title: 'Миний хэсэг', robots: { index: false, follow: false } };

/** Chooses buyer, agent, or administrator functionality from verified server-side roles. */
export default async function Dashboard({ searchParams }: { searchParams: Promise<{ cursor?: string }> }) {
  const session = await getSession();
  if (!session) redirect('/login?next=/dashboard');
  if (session.roles.includes('ROLE_AGENCY_ADMIN')) return <section className="container dashboard-section"><div className="section-heading"><div><span className="eyebrow">АДМИНИСТРАТОР</span><h1>Агентын бүртгэл</h1><p>Шинэ агент үүсгэж, зар удирдах эрх олгоно.</p></div></div><div className="form-panel narrow-panel"><RegisterForm agent /></div></section>;
  if (!session.roles.includes('ROLE_AGENT')) return <section className="container dashboard-section"><span className="eyebrow">МИНИЙ БҮРТГЭЛ</span><h1>Тавтай морил.</h1><div className="empty-state"><h2>Таны бүртгэл амжилттай нэвтэрсэн байна</h2><p>Та худалдах, түрээслэх заруудыг үзэх боломжтой. Зар үүсгэхэд агентын эрх шаардлагатай.</p><Link className="button" href="/">Заруудыг үзэх ↗</Link></div></section>;
  const { cursor } = await searchParams;
  const query = new URLSearchParams({ pageSize: '12' }); if (cursor) query.set('cursor', cursor);
  const page = await backend<PropertyPage>('listing', `/v1/agents/me/properties?${query}`, { headers: { Authorization: `Bearer ${session.accessToken}` } });
  return <section className="container dashboard-section"><div className="section-heading"><div><span className="eyebrow">АГЕНТЫН ХЭСЭГ</span><h1>Миний зарууд</h1><p>Ноорог болон нийтэлсэн заруудаа нэг дороос удирдаарай.</p></div><Link className="button" href="/dashboard/new">Шинэ зар нэмэх +</Link></div>
    <div className="dashboard-stats"><div><span>Энэ хуудсанд</span><strong>{page.items.length} зар</strong></div><div><span>Нийтэлсэн</span><strong>{page.items.filter(item => item.status === 'ACTIVE').length}</strong></div><div><span>Ноорог</span><strong>{page.items.filter(item => item.status === 'DRAFT').length}</strong></div></div>
    {page.items.length ? <div className="property-grid">{page.items.map(property => <PropertyCard key={property.id} property={property} dashboard />)}</div>
      : <div className="empty-state"><h2>Анхны зараа нэмээрэй</h2><p>Зар эхлээд ноорог болж хадгалагдана. Та шалгаж байгаад нийтэлж болно.</p><Link className="button" href="/dashboard/new">Зар үүсгэх ↗</Link></div>}
    {page.nextCursor && <div className="pagination"><Link className="button button-outline" href={`/dashboard?cursor=${encodeURIComponent(page.nextCursor)}`}>Дараагийн зарууд →</Link></div>}
  </section>;
}
