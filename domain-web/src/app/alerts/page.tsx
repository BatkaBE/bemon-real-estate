import Link from 'next/link';
import { redirect } from 'next/navigation';
import { getSession } from '@/lib/session';
import { backend } from '@/lib/api';
import { PersonalAction } from '@/components/saved-search';
export const metadata = { title: 'Мэдэгдэл', robots: { index: false } };
/** Lists the authenticated account's matching-property alerts. */
export default async function Alerts() {
  const session = await getSession(); if (!session) redirect('/login?next=/alerts');
  const result = await backend<{ items: { id: string; propertyId: string; title: string; readAt: string | null }[] }>('search', '/v1/users/me/alerts', { headers: { Authorization: `Bearer ${session.accessToken}` } });
  return <section className="container account-page"><h1>Мэдэгдэл</h1>{result.items.map(item => <article className="panel conversation" key={item.id}><h2><Link href={`/properties/${item.propertyId}`}>{item.title}</Link></h2><p>{item.readAt ? 'Уншсан' : 'Шинэ тохирох зар'}</p>{!item.readAt && <PersonalAction path={`/api/alerts/${item.id}`} method="POST" label="Уншсан гэж тэмдэглэх" />}</article>)}{!result.items.length && <p>Шинэ мэдэгдэл алга.</p>}</section>;
}
