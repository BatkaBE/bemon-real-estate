import type { Metadata } from 'next';
import Link from 'next/link';
import { getSession } from '@/lib/session';
import './globals.css';

export const metadata: Metadata = { title: { default: 'GerHub — Таны дараагийн орон зай', template: '%s | GerHub' },
  description: 'Монгол дахь үл хөдлөх хөрөнгийн зар. Орон сууц, хувийн сууц, газар худалдах, түрээслэх.' };

/** Supplies accessible navigation using server-derived session roles. */
export default async function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const session = await getSession();
  return <html lang="mn"><body>
    <a className="skip-link" href="#main">Үндсэн хэсэг рүү шилжих</a>
    <header className="site-header"><div className="container header-inner">
      <Link className="brand" href="/" aria-label="GerHub нүүр"><span className="brand-mark">g.</span><span>GerHub<span className="brand-dot">.</span></span></Link>
      <nav aria-label="Үндсэн цэс"><Link href="/?listingType=SALE#listings">Худалдаа</Link><Link href="/?listingType=RENT#listings">Түрээс</Link>
        {session ? <><Link href="/dashboard">Миний хэсэг</Link><form action="/api/auth/logout" method="post"><button className="text-button" type="submit">Гарах</button></form></>
          : <Link href="/login">Нэвтрэх</Link>}
        <Link className="button button-small" href="/dashboard/new">Зар нэмэх <span aria-hidden="true">↗</span></Link>
      </nav>
    </div></header>
    {session && <nav className="account-nav container" aria-label="Бүртгэлийн цэс"><Link href="/dashboard">Миний хэсэг</Link><Link href="/account">Миний бүртгэл</Link><Link href="/favorites">Хадгалсан зар</Link><Link href="/inquiries">Хүсэлтүүд</Link><Link href="/searches">Хадгалсан хайлт</Link><Link href="/alerts">Мэдэгдэл</Link>{session.roles.some(role => ['ROLE_AGENT', 'ROLE_AGENCY_ADMIN'].includes(role)) && <Link href="/payments">Төлбөр ба багц</Link>}</nav>}
    <main id="main">{children}</main>
    <footer className="site-footer"><div className="container footer-inner"><div><Link className="brand" href="/">GerHub</Link><p>Таны дараагийн орон зай.</p></div>
      <div className="footer-links"><Link href="/">Зар хайх</Link><Link href="/dashboard">Зар удирдах</Link><Link href="/register">Бүртгүүлэх</Link></div>
      <p className="footer-note">© {new Date().getFullYear()} GerHub · Монгол</p></div></footer>
  </body></html>;
}
