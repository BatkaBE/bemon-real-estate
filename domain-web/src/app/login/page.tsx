import Link from 'next/link';
import { getSession } from '@/lib/session';
import { safeReturnPath } from '@/lib/security';
import { redirect } from 'next/navigation';

/** Presents one clear entry into the platform's existing OAuth login. */
export default async function Login({ searchParams }: { searchParams: Promise<{ error?: string; next?: string }> }) {
  const params = await searchParams;
  const next = safeReturnPath(params.next);
  if (await getSession()) redirect(next);
  return <section className="auth-shell"><div className="auth-story"><span className="eyebrow">GERHUB-Д ТАВТАЙ МОРИЛ</span><h1>Дараагийн алхам<br /><em>эндээс эхэлнэ.</em></h1><p>Заруудаа нэг газраас удирдаж, өөрт тохирох орон зайгаа олоорой.</p><span className="auth-story-mark">g.</span></div>
    <div className="auth-panel"><span className="eyebrow">ТАНЫ БҮРТГЭЛ</span><h2>Нэвтрэх</h2><p>Имэйл, нууц үгээрээ GerHub бүртгэлд нэвтэрнэ үү.</p>
      {params.error && <div role="alert" className="notice error">Нэвтрэлт амжилтгүй боллоо. Дахин оролдоно уу.</div>}
      <a className="button" href={`/api/auth/login?next=${encodeURIComponent(next)}`}>Нэвтрэх хуудас руу ↗</a>
      <Link className="inline-link" href="/forgot-password">Нууц үг мартсан уу?</Link>
      <div className="auth-divider" /><p>Шинэ хэрэглэгч үү? <Link className="inline-link" href="/register">Бүртгүүлэх</Link></p><Link className="back-link" href="/">← Заруудыг үзэх</Link>
    </div></section>;
}
