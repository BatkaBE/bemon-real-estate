import Link from 'next/link';
import type { Property } from '@/lib/types';
import { formatPrice, propertyTypes, statuses } from '@/lib/presentation';
import { Architecture } from './architecture';

/** Renders actual listing data while identifying the illustrated media placeholder. */
export function PropertyCard({ property, dashboard = false }: { property: Property; dashboard?: boolean }) {
  return <article className="property-card">
    <Link href={dashboard ? `/dashboard/${property.id}` : `/properties/${property.id}`} className="card-visual" aria-label={property.title}>
      <Architecture compact />
      <span className="card-category">{propertyTypes[property.propertyType]}</span>
      <span className="photo-note">Зураг нэмээгүй</span>
    </Link>
    <div className="card-body">
      <div className="card-topline"><span>{property.listingType === 'RENT' ? 'ТҮРЭЭС' : 'ХУДАЛДАА'}</span>
        {dashboard && <span className={`status ${property.status.toLowerCase()}`}>{statuses[property.status]}</span>}</div>
      <h3><Link href={dashboard ? `/dashboard/${property.id}` : `/properties/${property.id}`}>{property.title}</Link></h3>
      <p className="card-address">{property.address.suburb} · {property.address.addressLine}</p>
      <div className="card-features">
        {property.bedrooms !== null && <span>{property.bedrooms} унтлагын өрөө</span>}
        {property.landSizeSqm !== null && <span>{property.landSizeSqm} м²</span>}
      </div>
      <div className="card-bottom"><strong>{formatPrice(property.price, property.currency)}{property.listingType === 'RENT' && property.currency === 'MNT' && <small> / сар</small>}</strong>
        <Link href={dashboard ? `/dashboard/${property.id}` : `/properties/${property.id}`} aria-label={`${property.title} дэлгэрэнгүй`} className="arrow-link">↗</Link></div>
    </div>
  </article>;
}
