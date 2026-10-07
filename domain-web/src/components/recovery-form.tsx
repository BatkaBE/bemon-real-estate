'use client';
import { useState } from 'react';
import Link from 'next/link';
/** Processes explicit single-use links; rendering a verification URL never consumes its token. */
export function RecoveryForm({ mode, token = '' }: { mode: 'forgot-password' | 'reset-password' | 'verify-email'; token?: string }) {
  const [message, setMessage] = useState(''); const [success, setSuccess] = useState(false); const [busy, setBusy] = useState(false);
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setMessage('');
    const form = new FormData(event.currentTarget);
    try {
      const response = await fetch(`/api/account/${mode}`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token, email: form.get('email'), password: form.get('password') }) });
      const result = await response.json(); setSuccess(response.ok);
      setMessage(response.ok ? mode === 'forgot-password' ? 'Бүртгэлтэй имэйл бол сэргээх холбоос илгээгдэнэ.' : mode === 'verify-email' ? 'Имэйл баталгаажлаа.' : 'Нууц үг шинэчлэгдлээ. Дахин нэвтэрнэ үү.' : result.message);
    } catch { setMessage('Сүлжээний алдаа гарлаа.'); } finally { setBusy(false); }
  }
  return <form className="stack-form" onSubmit={submit}>
    {mode === 'forgot-password' && <label>Имэйл<input type="email" name="email" required maxLength={320} autoComplete="email" /></label>}
    {mode === 'reset-password' && <><label>Шинэ нууц үг<input type="password" name="password" required minLength={12} maxLength={72} autoComplete="new-password" /></label><p className="field-help">Том, жижиг үсэг, тоо, тусгай тэмдэгттэй; 12-оос дээш тэмдэгт, 72 UTF-8 байтаас хэтрэхгүй.</p></>}
    {message && <div className={`notice ${success ? 'success' : 'error'}`} role="status">{message}</div>}
    <button className="button" disabled={busy || (success && mode !== 'forgot-password')}>{busy ? 'Түр хүлээнэ үү…' : mode === 'forgot-password' ? 'Холбоос авах' : mode === 'verify-email' ? 'Баталгаажуулах' : 'Нууц үг шинэчлэх'}</button>
    <Link className="inline-link" href="/login">Нэвтрэх →</Link>
  </form>;
}
