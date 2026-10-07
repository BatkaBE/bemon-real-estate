import Link from 'next/link';
import { getSession } from '@/lib/session';
import { SaveSearch } from '@/components/saved-search';
import { ApiError, backend, errorMessage } from '@/lib/api';
import type { PropertyPage } from '@/lib/types';
import { Architecture } from '@/components/architecture';
import { PropertyCard } from '@/components/property-card';
import { propertyTypes } from '@/lib/presentation';

type Search = Record<string, string | string[] | undefined>;
/** Fetches only MNT listings for the Mongolian public catalog. */
export default async function Home({ searchParams }: { searchParams: Promise<Search> }) {
  const params = await searchParams;
  const value = (key: string) => typeof params[key] === 'string' ? params[key] as string : '';
  const query = new URLSearchParams({ pageSize: '9', currency: 'MNT' });
  for (const key of ['suburb', 'listingType', 'propertyType', 'minPrice', 'maxPrice', 'minBedrooms', 'q', 'latitude', 'longitude', 'radiusKm', 'cursor']) if (value(key)) query.set(key, value(key));
  let page: PropertyPage & { facets?: Record<string, { key: string; doc_count: number }[]> } = { items: [], nextCursor: null, pageSize: 9 };
  let error = '';
  try { page = await backend<PropertyPage>('search', `/v1/search?${query}`); }
  catch (failure) { error = errorMessage(failure instanceof ApiError ? failure.status : 503); }
  const session = await getSession();
  const live = await Promise.all(page.items.map(async property => { try { return await backend<PropertyPage['items'][number]>('listing', '/v1/properties/' + property.id); } catch { return null; } }));
  page.items = live.filter((property): property is PropertyPage['items'][number] => property !== null && property.status === 'ACTIVE');
  const criteria: Record<string, unknown> = {}; for (const [key, entry] of query) if (!['pageSize', 'cursor'].includes(key)) criteria[key] = entry;
  const nextQuery = new URLSearchParams(query); nextQuery.delete('pageSize'); nextQuery.delete('currency');
  if (page.nextCursor) nextQuery.set('cursor', page.nextCursor);
  const featured = await backend<{ items: PropertyPage['items'] }>('listing', '/v1/properties/featured').catch(() => ({ items: [] }));
  const filtered = ['suburb', 'listingType', 'propertyType', 'minPrice', 'maxPrice', 'minBedrooms', 'q', 'radiusKm'].some(key => value(key));
  return <>
    <section className="hero"><div className="container hero-inner"><div className="hero-copy"><span className="eyebrow"><span className="tiny-dot" /> ҮЛ ХӨДЛӨХ ХӨРӨНГӨ</span>
      <h1>Танд тохирох<br /><em>орон зай.</em></h1><p>Шинэ амьдралын эхлэл, өөрийн гэсэн гэр. <br />Дараагийн алхмаа Bemon-той хамт хийгээрэй.</p>
      <div className="hero-actions"><a href="#listings" className="button">Заруудыг үзэх <span aria-hidden="true">↗</span></a><span className="hero-subnote">Худалдах · Түрээслэх</span></div>
    </div><div className="hero-art"><Architecture /><div className="hero-art-label"><span>ӨӨРИЙН ГЭСЭН ОРОН ЗАЙ</span><span>UB / MONGOLIA</span></div></div></div></section>
    <div className="container"><form action="/#listings" method="get" className="search-panel" aria-label="Зар шүүх">
      <label>Түлхүүр үг<input name="q" maxLength={120} placeholder="Жишээ нь: орон сууц" defaultValue={value('q')} /></label>
      <label>Байршил<input name="suburb" placeholder="Жишээ нь: Хан-Уул" defaultValue={value('suburb')} maxLength={100} /></label>
      <label>Зарын төрөл<select name="listingType" defaultValue={value('listingType')}><option value="">Худалдаа ба түрээс</option><option value="SALE">Худалдаа</option><option value="RENT">Түрээс</option></select></label>
      <label>Хөрөнгийн төрөл<select name="propertyType" defaultValue={value('propertyType')}><option value="">Бүх төрөл</option>{Object.entries(propertyTypes).map(([key, name]) => <option value={key} key={key}>{name}</option>)}</select></label>
      <button className="button" type="submit">Хайх <span aria-hidden="true">⌕</span></button>
      <details className="advanced-filters"><summary>Үнийн нэмэлт шүүлтүүр</summary><div className="advanced-grid">
        <label>Доод үнэ (₮)<input name="minPrice" type="number" min="0" step="0.01" defaultValue={value('minPrice')} /></label>
        <label>Дээд үнэ (₮)<input name="maxPrice" type="number" min="0" step="0.01" defaultValue={value('maxPrice')} /></label>
        <label>Унтлагын өрөө (доод)<input name="minBedrooms" type="number" min="0" max="50" defaultValue={value('minBedrooms')} /></label>
        <label>Өргөрөг<input name="latitude" type="number" step="any" min="-90" max="90" defaultValue={value('latitude')} /></label>
        <label>Уртраг<input name="longitude" type="number" step="any" min="-180" max="180" defaultValue={value('longitude')} /></label>
        <label>Радиус (км)<input name="radiusKm" type="number" step="any" min="0.1" max="200" defaultValue={value('radiusKm')} /></label>
      </div></details>
    </form></div>
    {!!featured.items.length && !filtered && <section className="container listings-section"><div className="section-heading"><h2>Онцлох зарууд</h2><span className="featured-tag">Төлбөртэй онцлох байрлал</span></div><div className="property-grid">{featured.items.map(property => <PropertyCard key={property.id} property={property} />)}</div></section>}
    <section className="container listings-section" id="listings"><div className="section-heading"><div><span className="eyebrow">ТАНЫ СОНГОЛТ</span><h2>{filtered ? 'Хайлтын үр дүн' : 'Шинээр нэмэгдсэн'}</h2></div>
      <div className="heading-meta">{page.items.length} зар харагдаж байна{(filtered || value('cursor')) && <Link href="/#listings">Шүүлтүүр арилгах ↗</Link>}</div></div>
      {!!page.facets?.suburb?.length && <div className="facet-row" aria-label="Байршлын үр дүн">{page.facets.suburb.map(facet => { const filteredQuery = new URLSearchParams(query); filteredQuery.set('suburb', facet.key); filteredQuery.delete('cursor'); return <Link key={facet.key} href={`/?${filteredQuery}#listings`}>{facet.key} ({facet.doc_count})</Link>; })}</div>}
      {session && <SaveSearch criteria={criteria} />}
      {error ? <div className="notice error" role="alert">{error}<Link href="/">Дахин оролдох ↗</Link></div>
        : page.items.length ? <div className="property-grid">{page.items.map(property => <PropertyCard key={property.id} property={property} />)}</div>
        : <div className="empty-state"><span className="empty-symbol">⌂</span><h3>Тохирох зар одоогоор алга</h3><p>Байршил, үнэ эсвэл төрлийн шүүлтүүрээ өөрчилж үзээрэй.</p><Link className="button button-outline" href="/">Бүх зар үзэх</Link></div>}
      {page.nextCursor && <div className="pagination"><Link href={`/?${nextQuery}#listings`} className="button button-outline">Дараагийн зарууд →</Link></div>}
    </section>
    <section className="container agent-cta"><div><span className="eyebrow">АГЕНТЫН ХЭСЭГ</span><h2>Дараагийн эзэнтэй нь<br />холбож өгье.</h2><p>Зараа үүсгэж, нийтлээд нэг газраас удирдаарай.</p></div><Link className="button" href="/dashboard">Зараа удирдах ↗</Link></section>
  </>;
}
