import Link from 'next/link';
import { redirect } from 'next/navigation';
import { getSession } from '@/lib/session';
import { PropertyForm } from '@/components/property-form';
export const metadata = { title: 'Шинэ зар', robots: { index: false, follow: false } };
/** Requires an agent before exposing the draft editor. */
export default async function NewProperty() {
  const session = await getSession();
  if (!session) redirect('/login?next=/dashboard/new');
  if (!session.roles.includes('ROLE_AGENT')) redirect('/dashboard');
  return <section className="container editor-section"><Link className="back-link" href="/dashboard">← Миний зарууд</Link><span className="eyebrow">АГЕНТЫН ХЭСЭГ</span><h1>Шинэ зар нэмэх</h1><p className="page-intro">Мэдээллээ оруулаад ноорог хадгална уу. Шалгасны дараа нийтэлж болно.</p><PropertyForm /></section>;
}
