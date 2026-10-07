import Link from 'next/link';
export default function NotFound() { return <section className="container empty-state error-page"><span className="eyebrow">404</span><h1>Хуудас олдсонгүй.</h1><p>Зар устсан, нийтлэгдээгүй эсвэл энэ хаяг буруу байж болно.</p><Link className="button" href="/">Заруудыг үзэх ↗</Link></section>; }
