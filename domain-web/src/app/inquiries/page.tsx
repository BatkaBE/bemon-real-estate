import Link from 'next/link';
import { redirect } from 'next/navigation';
import { getSession } from '@/lib/session';
import { backend } from '@/lib/api';
import { InquiryReply } from '@/components/engagement';
interface Inquiry { id: string; propertyId: string; propertyTitle: string; buyerName: string; buyerEmail: string; buyerPhone: string; message: string; status: string; reply: string; version: number }
export const metadata = { title: 'Миний хүсэлтүүд', robots: { index: false, follow: false } };
/** Lists only the current participant's conversations, keeping sender data out of public pages. */
export default async function Inquiries({ searchParams }: { searchParams: Promise<{ cursor?: string }> }) {
  const session = await getSession(); if (!session) redirect('/login?next=/inquiries');
  const { cursor } = await searchParams; const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : '';
  const page = await backend<{ items: Inquiry[]; nextCursor: string | null }>('listing', `/v1/users/me/inquiries${query}`, { headers: { Authorization: `Bearer ${session.accessToken}` } });
  const agent = session.roles.includes('ROLE_AGENT');
  return <section className="container dashboard-section"><div className="section-heading"><div><span className="eyebrow">ХОЛБОО БАРИХ</span><h1>{agent ? 'Ирсэн хүсэлтүүд' : 'Миний хүсэлтүүд'}</h1></div></div>
    {page.items.length ? <div className="conversation-list">{page.items.map(item => <article className="form-panel" key={item.id}><div className="section-heading"><h2><Link href={`/properties/${item.propertyId}`}>{item.propertyTitle}</Link></h2><span className="status">{{ OPEN: 'Шинэ', CONTACTED: 'Холбогдсон', CLOSED: 'Хаасан' }[item.status] || item.status}</span></div>
      {agent && <p>{item.buyerName || 'Хэрэглэгч'} · {item.buyerEmail}{item.buyerPhone && ` · ${item.buyerPhone}`}</p>}<p className="conversation-message">{item.message}</p>
      {agent ? <InquiryReply id={item.id} version={item.version} reply={item.reply} status={item.status} /> : item.reply ? <div className="reply-box"><strong>Агентын хариу</strong><p>{item.reply}</p></div> : <p className="field-help">Агентын хариуг хүлээж байна.</p>}
    </article>)}</div> : <div className="empty-state"><h2>Хүсэлт одоогоор алга</h2><p>Зарын дэлгэрэнгүйгээс агент руу асуултаа илгээнэ үү.</p></div>}
    {page.nextCursor && <div className="pagination"><Link className="button button-outline" href={`/inquiries?cursor=${encodeURIComponent(page.nextCursor)}`}>Дараагийн хүсэлтүүд →</Link></div>}</section>;
}
