'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
export interface Profile { id: string; email: string; displayName: string; phone: string; role: string; emailVerified: boolean }
/** Edits user-supplied contacts without accepting email or role changes. */
export function AccountForm({ profile }: { profile: Profile }) {
  const [message, setMessage] = useState(''); const [busy, setBusy] = useState(false); const router = useRouter();
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setMessage('');
    const values = new FormData(event.currentTarget);
    try {
      const response = await fetch('/api/account', { method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ displayName: values.get('displayName'), phone: values.get('phone') }) });
      const result = await response.json(); setMessage(response.ok ? 'Мэдээлэл хадгалагдлаа.' : result.message); if (response.ok) router.refresh();
    } catch { setMessage('Сүлжээний алдаа гарлаа.'); } finally { setBusy(false); }
  }
  async function verify() {
    setBusy(true); setMessage('');
    try { const response = await fetch('/api/account/verification', { method: 'POST' });
      const body = await response.json(); setMessage(response.ok ? 'Имэйлдээ ирсэн холбоосоор баталгаажуулна уу.' : body.message);
    } catch { setMessage('Сүлжээний алдаа гарлаа.'); } finally { setBusy(false); }
  }
  return <><form className="stack-form" onSubmit={submit}><label>Имэйл<input value={profile.email} readOnly /></label>
    <span className={`status ${profile.emailVerified ? 'active' : 'draft'}`}>{profile.emailVerified ? 'Имэйл баталгаажсан' : 'Имэйл баталгаажаагүй'}</span>
    <label>Нэр<input name="displayName" maxLength={120} defaultValue={profile.displayName} autoComplete="name" /></label>
    <label>Утас<input name="phone" type="tel" maxLength={30} defaultValue={profile.phone} autoComplete="tel" /></label>
    <p className="field-help">Агентын нэр, утас зарын дэлгэрэнгүйд харагдана. Хүсэлт илгээхэд таны бүртгэлийн холбоо барих мэдээлэл агент руу очно.</p>
    {message && <div className="notice" role="status">{message}</div>}<button className="button" disabled={busy}>Хадгалах</button>
    {!profile.emailVerified && <button className="button button-outline" type="button" disabled={busy} onClick={verify}>Имэйл баталгаажуулах</button>}
  </form></>;
}
