import { redirect } from 'next/navigation';
import { getSession } from '@/lib/session';
import { backend } from '@/lib/api';
import { AccountForm, type Profile } from '@/components/account-form';
export const metadata = { title: 'Миний бүртгэл', robots: { index: false, follow: false } };
/** Reads the account only through the verified server-side credential. */
export default async function Account() {
  const session = await getSession(); if (!session) redirect('/login?next=/account');
  const profile = await backend<Profile>('identity', '/v1/users/me', { headers: { Authorization: `Bearer ${session.accessToken}` } });
  return <section className="container dashboard-section"><div className="section-heading"><div><span className="eyebrow">МИНИЙ БҮРТГЭЛ</span><h1>Хувийн мэдээлэл</h1></div></div><div className="form-panel narrow-panel"><AccountForm profile={profile} /></div></section>;
}
