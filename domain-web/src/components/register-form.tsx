'use client';
import { useState } from 'react';
import Link from 'next/link';

/** Submits registration without storing or logging the password. */
export function RegisterForm({ agent = false }: { agent?: boolean }) {
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState('');
  const [success, setSuccess] = useState(false);
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setPending(true); setMessage(''); setSuccess(false);
    const form = event.currentTarget;
    const values = new FormData(form);
    try {
      const response = await fetch(agent ? '/api/admin/agents' : '/api/register', { method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: values.get('email'), password: values.get('password') }) });
      const result = await response.json();
      setMessage(result.message || 'Алдаа гарлаа.'); setSuccess(response.ok);
      if (response.ok) form.reset();
    } catch { setMessage('Сүлжээний алдаа гарлаа. Дахин оролдоно уу.'); }
    finally { setPending(false); }
  }
  return <form className="stack-form" onSubmit={submit}>
    <label>Имэйл<input type="email" name="email" required maxLength={320} autoComplete="email" placeholder="name@example.mn" /></label>
    <label>Нууц үг<input type="password" name="password" required minLength={12} maxLength={72} autoComplete="new-password" /></label>
    <p className="field-help">12-оос дээш тэмдэгт, 72 UTF-8 байтаас хэтрэхгүй. Том, жижиг үсэг, тоо, тусгай тэмдэгт оруулна уу.</p>
    {message && <div className={`notice ${success ? 'success' : 'error'}`} role="status">{message}</div>}
    <button type="submit" className="button" disabled={pending}>{pending ? 'Илгээж байна…' : agent ? 'Агент үүсгэх' : 'Бүртгүүлэх'} <span aria-hidden="true">↗</span></button>
    {success && !agent && <Link className="inline-link" href="/login">Одоо нэвтрэх →</Link>}
  </form>;
}
