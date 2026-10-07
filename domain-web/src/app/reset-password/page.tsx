import { RecoveryForm } from '@/components/recovery-form';
export const metadata = { title: 'Шинэ нууц үг', robots: { index: false, follow: false } };
/** Presents an explicit, single-use account challenge action. */
export default async function Page({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const { token } = await searchParams;
  return <section className="container dashboard-section"><div className="form-panel narrow-panel"><span className="eyebrow">ТАНЫ БҮРТГЭЛ</span><h1>Шинэ нууц үг</h1><p>Имэйлээр ирсэн холбоос 30 минутын хугацаатай.</p><RecoveryForm mode="reset-password" token={token} /></div></section>;
}
