'use client';
import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import type { Property, PropertyInput } from '@/lib/types';
import { propertyTypes } from '@/lib/presentation';

/** Creates replay-safe drafts and preserves an existing listing's currency and ETag during replacement. */
export function PropertyForm({ property }: { property?: Property }) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [stale, setStale] = useState(false);
  const replay = useRef<{ payload: string; key: string } | null>(null);
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setPending(true); setError(''); setStale(false);
    const data = new FormData(event.currentTarget);
    const text = (key: string) => String(data.get(key) || '').trim();
    const number = (key: string) => text(key) ? Number(text(key)) : null;
    const body: PropertyInput = {
      title: text('title'), propertyType: text('propertyType') as PropertyInput['propertyType'],
      listingType: text('listingType') as PropertyInput['listingType'], currency: property?.currency || 'MNT',
      price: number('price'), bedrooms: number('bedrooms'), bathrooms: number('bathrooms'),
      parkingSpaces: number('parkingSpaces'), landSizeSqm: number('landSizeSqm'),
      address: { addressLine: text('addressLine'), suburb: text('suburb'), state: text('state'),
        postcode: text('postcode'), latitude: Number(text('latitude')), longitude: Number(text('longitude')) },
    };
    const payload = JSON.stringify(body);
    if (!replay.current || replay.current.payload !== payload) replay.current = { payload, key: crypto.randomUUID() };
    try {
      const response = await fetch(property ? `/api/properties/${property.id}` : '/api/properties', {
        method: property ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json',
          ...(property ? { 'If-Match': `"${property.version}"` } : { 'Idempotency-Key': replay.current.key }) }, body: payload });
      const result = await response.json();
      if (!response.ok) { setError(result.message || 'Алдаа гарлаа.'); setStale(response.status === 412); return; }
      router.push(`/dashboard/${result.id}?saved=1`); router.refresh();
    } catch { setError('Сүлжээний алдаа гарлаа. Ижил мэдээллээр дахин илгээж болно.'); }
    finally { setPending(false); }
  }
  return <form onSubmit={submit} className="property-form">
    <div className="form-panel"><div className="form-section-title"><span>01</span><div><h2>Үндсэн мэдээлэл</h2><p>Зарынхаа гол мэдээллийг оруулна уу. MNT түрээсийн үнийг сараар оруулна.</p></div></div>
      <label className="full-field">Зарын гарчиг<input name="title" required maxLength={255} defaultValue={property?.title} placeholder="Жишээ нь: Хан-Уулд нарлаг 3 өрөө байр" /></label>
      <div className="form-grid"><label>Хөрөнгийн төрөл<select name="propertyType" defaultValue={property?.propertyType || 'APARTMENT'}>{Object.entries(propertyTypes).map(([key, value]) => <option value={key} key={key}>{value}</option>)}</select></label>
        <label>Зарын төрөл<select name="listingType" defaultValue={property?.listingType || 'SALE'}><option value="SALE">Худалдаа</option><option value="RENT">Түрээс</option></select></label>
        <label>Үнэ ({property?.currency === 'AUD' ? 'AUD' : '₮'})<input name="price" type="number" min="0" max="9999999999.99" step="0.01" defaultValue={property?.price ?? ''} placeholder="Тохиролцох бол хоосон үлдээнэ" /></label>
        <label>Талбай (м²)<input name="landSizeSqm" type="number" min="0" max="99999999.99" step="0.01" defaultValue={property?.landSizeSqm ?? ''} /></label>
        <label>Унтлагын өрөө<input name="bedrooms" type="number" min="0" max="50" defaultValue={property?.bedrooms ?? ''} /></label>
        <label>Угаалгын өрөө<input name="bathrooms" type="number" min="0" max="50" defaultValue={property?.bathrooms ?? ''} /></label>
        <label>Зогсоол<input name="parkingSpaces" type="number" min="0" max="50" defaultValue={property?.parkingSpaces ?? ''} /></label>
      </div></div>
    <div className="form-panel"><div className="form-section-title"><span>02</span><div><h2>Байршил</h2><p>Хаяг болон газрын зурагт ашиглах координат.</p></div></div>
      <div className="form-grid"><label>Дүүрэг / байршил<input name="suburb" required maxLength={100} defaultValue={property?.address.suburb} placeholder="Хан-Уул" /></label>
        <label>Хот / аймгийн товчлол<input name="state" required minLength={2} maxLength={10} defaultValue={property?.address.state || 'УБ'} /></label>
        <label className="full-field">Дэлгэрэнгүй хаяг<input name="addressLine" required maxLength={255} defaultValue={property?.address.addressLine} placeholder="Хороо, хотхон, байрны дугаар" /></label>
        <label>Шуудангийн код<input name="postcode" required minLength={property?.currency === 'AUD' ? 4 : 5} maxLength={10} defaultValue={property?.address.postcode} placeholder="17000" /></label>
        <label>Өргөрөг<input name="latitude" type="number" required min="-90" max="90" step="any" defaultValue={property?.address.latitude} placeholder="47.9188" /></label>
        <label>Уртраг<input name="longitude" type="number" required min="-180" max="180" step="any" defaultValue={property?.address.longitude} placeholder="106.9177" /></label>
      </div></div>
    <div className="form-footer"><p>Зар {property ? 'шинэчлэгдэж' : 'ноорог болж'} хадгалагдана. Зураг нэмэх үйлчилгээ хараахан бэлэн болоогүй.</p>
      {error && <div className="notice error" role="alert">{error}{stale && <button type="button" className="text-button" onClick={() => window.location.reload()}>Шинэ төлөвийг ачаалах</button>}</div>}
      <button className="button" type="submit" disabled={pending}>{pending ? 'Хадгалж байна…' : property ? 'Өөрчлөлт хадгалах' : 'Ноорог хадгалах'} ↗</button>
    </div>
  </form>;
}
