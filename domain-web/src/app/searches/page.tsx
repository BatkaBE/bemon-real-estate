import Link from 'next/link';
import { redirect } from 'next/navigation';
import { getSession } from '@/lib/session';
import { backend } from '@/lib/api';
import { PersonalAction } from '@/components/saved-search';
export const metadata = { title: 'Хадгалсан хайлт', robots: { index: false } };
/** Shows personal filters without accepting an owner from the URL. */
export default async function Searches() {
  const session = await getSession(); if (!session) redirect('/login?next=/searches');
  const result = await backend<{ items: { id: string; name: string; criteria: Record<string, unknown>; emailEnabled: boolean }[] }>('search', '/v1/users/me/searches', { headers: { Authorization: `Bearer ${session.accessToken}` } });
  return <section className="container account-page"><h1>Хадгалсан хайлт</h1><p>Хайлтын шүүлтүүрээ хадгалбал шинэ тохирох зар нэмэгдэхэд мэдэгдэл ирнэ.</p>
    {result.items.map(item => { const params = new URLSearchParams(); for (const [key, value] of Object.entries(item.criteria)) if (value !== null && value !== '') params.set(key, String(value));
      return <article className="panel conversation" key={item.id}><h2><Link href={`/?${params}#listings`}>{item.name}</Link></h2><p>{item.emailEnabled ? 'Имэйл болон аппын мэдэгдэл' : 'Аппын мэдэгдэл'}</p><PersonalAction path={`/api/searches/${item.id}`} method="DELETE" label="Устгах" /></article>; })}
    {!result.items.length && <p>Хадгалсан хайлт алга. <Link href="/">Зар хайх →</Link></p>}
  </section>;
}
