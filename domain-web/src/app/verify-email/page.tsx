import { RecoveryForm } from '@/components/recovery-form';
export const metadata = { title: 'Имэйл баталгаажуулах', robots: { index: false, follow: false } };
/** Presents an explicit, single-use account challenge action. */
export default async function Page({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const { token } = await searchParams;
  return <section className="container dashboard-section"><div className="form-panel narrow-panel"><span className="eyebrow">ТАНЫ БҮРТГЭЛ</span><h1>Имэйл баталгаажуулах</h1><p>Таны имэйл мөн болохыг баталгаажуулна уу.</p><RecoveryForm mode="verify-email" token={token} /></div></section>;
}
