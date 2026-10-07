import { RecoveryForm } from '@/components/recovery-form';
export const metadata = { title: 'Нууц үг сэргээх', robots: { index: false, follow: false } };
/** Presents an explicit, single-use account challenge action. */
export default async function Page({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const { token } = await searchParams;
  return <section className="container dashboard-section"><div className="form-panel narrow-panel"><span className="eyebrow">ТАНЫ БҮРТГЭЛ</span><h1>Нууц үг сэргээх</h1><p>Бүртгэлтэй имэйлээ оруулна уу.</p><RecoveryForm mode="forgot-password" token={token} /></div></section>;
}
