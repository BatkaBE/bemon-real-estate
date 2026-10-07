'use client';
import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
/** Adds/removes only a saved relationship and leaves the listing untouched. */
export function FavoriteButton({ id, saved = false }: { id: string; saved?: boolean }) {
  const [value, setValue] = useState(saved); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const router = useRouter();
  async function toggle() {
    setBusy(true); setError('');
    try { const response = await fetch(`/api/favorites/${id}`, { method: value ? 'DELETE' : 'PUT' }); const body = await response.json();
      if (response.ok) { setValue(body.saved); router.refresh(); } else setError(body.message);
    } catch { setError('Сүлжээний алдаа гарлаа.'); } finally { setBusy(false); }
  }
  return <><button className="button button-outline" onClick={toggle} disabled={busy} aria-pressed={value}>{value ? '♥ Хадгалсан' : '♡ Зар хадгалах'}</button>{error && <p role="alert">{error}</p>}</>;
}
/** Reuses one request ID after uncertain failures and never lets the browser choose a sender identity. */
export function InquiryForm({ id }: { id: string }) {
  const [message, setMessage] = useState(''); const [busy, setBusy] = useState(false); const [success, setSuccess] = useState(false);
  const replay = useRef<{ text: string; key: string } | null>(null);
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setMessage(''); const text = String(new FormData(event.currentTarget).get('message') || '').trim();
    if (!replay.current || replay.current.text !== text) replay.current = { text, key: crypto.randomUUID() };
    try { const response = await fetch(`/api/inquiries/${id}`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': replay.current.key }, body: JSON.stringify({ message: text }) });
      const result = await response.json(); setSuccess(response.ok); setMessage(response.ok ? 'Хүсэлт илгээгдлээ. Хариуг Миний хүсэлтүүдээс үзнэ үү.' : result.message);
    } catch { setMessage('Сүлжээний алдаа гарлаа. Дахин илгээж болно.'); } finally { setBusy(false); }
  }
  return <form onSubmit={submit} className="stack-form"><label>Агент руу хүсэлт<textarea name="message" required maxLength={2000} rows={4} placeholder="Байрыг үзэх цаг, асуух зүйлээ бичнэ үү." /></label>
    <p className="field-help">Таны нэр, имэйл, бүртгэлд оруулсан утас агент руу очно.</p>{message && <div className={`notice ${success ? 'success' : 'error'}`} role="status">{message}</div>}<button className="button" disabled={busy || success}>Хүсэлт илгээх</button></form>;
}
/** Replies conditionally so stale inbox tabs cannot overwrite each other. */
export function InquiryReply({ id, version, reply = '', status }: { id: string; version: number; reply?: string; status: string }) {
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const router = useRouter();
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(''); const form = new FormData(event.currentTarget);
    try { const response = await fetch(`/api/inquiries/${id}`, { method: 'PATCH', headers: { 'Content-Type': 'application/json', 'If-Match': `"${version}"` }, body: JSON.stringify({ reply: form.get('reply'), status: form.get('status') }) });
      if (response.ok) router.refresh(); else setError((await response.json()).message);
    } catch { setError('Сүлжээний алдаа гарлаа.'); } finally { setBusy(false); }
  }
  return <form className="stack-form" onSubmit={submit}><label>Хариу<textarea name="reply" maxLength={2000} rows={3} defaultValue={reply} /></label><label>Хүсэлтийн төлөв<select name="status" defaultValue={status}><option value="OPEN">Шинэ</option><option value="CONTACTED">Холбогдсон</option><option value="CLOSED">Хаасан</option></select></label>{error && <div className="notice error" role="alert">{error}</div>}<button className="button" disabled={busy}>Хариу хадгалах</button></form>;
}
