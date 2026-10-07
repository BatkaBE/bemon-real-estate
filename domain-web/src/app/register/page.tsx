import Link from 'next/link';
import { RegisterForm } from '@/components/register-form';
export const metadata = { title: 'Бүртгүүлэх' };

/** Creates a buyer account; administrator-controlled agent provisioning stays separate. */
export default function Register() {
  return <section className="auth-shell"><div className="auth-story"><span className="eyebrow">ШИНЭ БҮРТГЭЛ</span><h1>Өөрийн орон зайг<br /><em>олоорой.</em></h1><p>GerHub-д бүртгүүлээд худалдан авагч, түрээслэгчийн бүртгэлээ үүсгээрэй.</p><span className="auth-story-mark">g.</span></div>
    <div className="auth-panel"><span className="eyebrow">ЭХЛЭХЭД АМАРХАН</span><h2>Бүртгүүлэх</h2><RegisterForm /><div className="auth-divider" /><p>Бүртгэлтэй юу? <Link className="inline-link" href="/login">Нэвтрэх</Link></p><p className="field-help">Агентын эрхийг администратор үүсгэж өгнө.</p></div></section>;
}
