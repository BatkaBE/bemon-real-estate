import Link from 'next/link';
import { notFound, redirect } from 'next/navigation';
import { backend, ApiError } from '@/lib/api';
import { getSession } from '@/lib/session';
import { isUuid } from '@/lib/security';
import { PropertyForm } from '@/components/property-form';
import { StatusForm } from '@/components/status-form';
import type { Property } from '@/lib/types';
export const metadata = { title: 'Зар засах', robots: { index: false, follow: false } };
/** Applies both server-side role checks and backend ownership before returning private form data. */
export default async function EditProperty({ params, searchParams }: { params: Promise<{ id: string }>; searchParams: Promise<{ saved?: string }> }) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const session = await getSession();
  if (!session) redirect(`/login?next=/dashboard/${id}`);
  if (!session.roles.includes('ROLE_AGENT')) redirect('/dashboard');
  let property: Property;
  try { property = await backend<Property>('listing', `/v1/properties/${id}`, { headers: { Authorization: `Bearer ${session.accessToken}` } }); }
  catch (error) { if (error instanceof ApiError && error.status === 404) notFound(); throw error; }
  if (property.agentId !== session.userId) notFound();
  return <section className="container editor-section"><Link className="back-link" href="/dashboard">← Миний зарууд</Link><div className="section-heading"><div><span className="eyebrow">ЗАР УДИРДАХ</span><h1>Зар засах</h1><p>{property.title}</p></div><Link className="button button-outline" href={`/payments?property=${id}`}>Зарыг онцлох</Link><Link className="button button-outline" href={`/properties/${id}`}>Зарыг харах ↗</Link></div>
    {(await searchParams).saved && <div className="notice success" role="status">Зар амжилттай хадгалагдлаа.</div>}
    <StatusForm property={property} /><PropertyForm property={property} />
  </section>;
}
