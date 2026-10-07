import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { ApiError, backend } from '@/lib/api';
import { getSession } from '@/lib/session';
import { isUuid } from '@/lib/security';
import { Architecture } from '@/components/architecture';
import { formatPrice, propertyTypes, statuses } from '@/lib/presentation';
import type { Property } from '@/lib/types';
import { FavoriteButton, InquiryForm } from '@/components/engagement';

/** Generates indexable public metadata without exposing draft titles to anonymous requests. */
export async function generateMetadata({ params }: { params: Promise<{ id: string }> }): Promise<Metadata> {
  const { id } = await params;
  if (!isUuid(id)) return { title: 'Зар олдсонгүй', robots: { index: false } };
  try {
    const property = await backend<Property>('listing', `/v1/properties/${id}`);
    return { title: property.title, description: `${property.address.suburb} · ${formatPrice(property.price, property.currency)}` };
  } catch { return { title: 'Зар', robots: { index: false } }; }
}
/** Renders the backend's visibility decision, including private owner previews. */
export default async function PropertyDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const session = await getSession();
  let property: Property;
  try { property = await backend<Property>('listing', `/v1/properties/${id}`, { headers: session ? { Authorization: `Bearer ${session.accessToken}` } : {} }); }
  catch (error) { if (error instanceof ApiError && error.status === 404) notFound(); throw error; }
  const contact = await backend<{ displayName?: string; phone?: string }>('identity', `/v1/agents/${property.agentId}/contact`).catch(() => ({} as { displayName?: string; phone?: string }));
  const saved = session ? await backend<{ saved: boolean }>('listing', `/v1/users/me/favorites/${id}`, { headers: { Authorization: `Bearer ${session.accessToken}` } }).catch(() => ({ saved: false })) : { saved: false };
  return <section className="container detail-section"><Link className="back-link" href="/#listings">← Зарууд руу буцах</Link>
    <div className="detail-heading"><div><span className="eyebrow">{propertyTypes[property.propertyType]} · {property.listingType === 'SALE' ? 'ХУДАЛДАА' : 'ТҮРЭЭС'}</span><h1>{property.title}</h1><p>{property.address.suburb} · {property.address.addressLine}</p></div><span className={`status ${property.status.toLowerCase()}`}>{statuses[property.status]}</span></div>
    <div className="detail-grid"><div><div className="detail-visual"><Architecture /><span className="photo-note">Зураг нэмээгүй</span></div>
      <div className="detail-facts"><div><span>Унтлагын өрөө</span><strong>{property.bedrooms ?? '—'}</strong></div><div><span>Угаалгын өрөө</span><strong>{property.bathrooms ?? '—'}</strong></div><div><span>Талбай</span><strong>{property.landSizeSqm === null ? '—' : `${property.landSizeSqm} м²`}</strong></div><div><span>Зогсоол</span><strong>{property.parkingSpaces ?? '—'}</strong></div></div>
      <div className="address-box"><h2>Байршил</h2><p>{property.address.addressLine}, {property.address.suburb}</p><p>Бүсийн код: {property.address.state} · Шуудангийн код: {property.address.postcode}</p><a className="inline-link" href={`https://www.openstreetmap.org/?mlat=${property.address.latitude}&mlon=${property.address.longitude}#map=16/${property.address.latitude}/${property.address.longitude}`} target="_blank" rel="noopener noreferrer">Газрын зураг дээр үзэх ↗</a></div>
    </div><aside className="price-panel"><span className="eyebrow">{property.listingType === 'SALE' ? 'ХУДАЛДАХ ҮНЭ' : property.currency === 'MNT' ? 'САРЫН ТҮРЭЭС' : 'ТҮРЭЭСИЙН ҮНЭ'}</span><h2>{formatPrice(property.price, property.currency)}</h2><p>Нийтэлсэн мэдээллийн үнэ, валютыг харуулж байна.</p><hr />
      <p>Шинэчлэгдсэн: {new Intl.DateTimeFormat('mn-MN', { dateStyle: 'medium', timeZone: 'Asia/Ulaanbaatar' }).format(new Date(property.updatedAt))}</p>
      {session?.userId === property.agentId && <Link className="button" href={`/dashboard/${property.id}`}>Зар засах ↗</Link>}
      <Link className="button button-outline" href="/#listings">Бусад зар үзэх</Link>
      {session ? <FavoriteButton id={id} saved={saved.saved} /> : <Link className="button button-outline" href={`/login?next=/properties/${id}`}>Нэвтэрч зар хадгалах</Link>}
      <hr /><h3>{contact.displayName || 'Зарын агент'}</h3>
      {contact.phone && <a className="inline-link" href={`tel:${contact.phone.replace(/[^+0-9]/g, '')}`}>{contact.phone}</a>}
      {property.status === 'ACTIVE' && session?.userId !== property.agentId && (session ? <InquiryForm id={id} /> : <Link className="button" href={`/login?next=/properties/${id}`}>Нэвтэрч хүсэлт илгээх</Link>)}
    </aside></div>
  </section>;
}
