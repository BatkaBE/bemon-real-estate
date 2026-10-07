'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
/** Saves the visible filter set under the caller's account. */
export function SaveSearch({ criteria }: { criteria: Record<string, unknown> }) {
  const [message, setMessage] = useState(''); const [busy, setBusy] = useState(false);
  return <form className="save-search" onSubmit={async event => { event.preventDefault(); setBusy(true); const form = new FormData(event.currentTarget);
    try { const response = await fetch('/api/searches', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: form.get('name'), criteria, emailEnabled: form.get('email') === 'on' }) });
      const result = await response.json(); setMessage(response.ok ? 'Хайлтыг хадгаллаа. Шинэ зарын мэдэгдэл авах боломжтой.' : result.message || 'Алдаа гарлаа.'); }
    catch { setMessage('Холбоос тасарлаа. Дахин оролдоно уу.'); } finally { setBusy(false); } }}>
    <label>Хайлтын нэр<input name="name" required maxLength={120} placeholder="Миний шинэ гэр" /></label>
    <label className="check-label"><input type="checkbox" name="email" /> Имэйлээр мэдэгдэх</label>
    <button className="button button-outline" disabled={busy}>Хайлт хадгалах</button>{message && <p role="status">{message}</p>}
  </form>;
}
/** Applies a caller-scoped alert or saved-search mutation and refreshes its server-rendered list. */
export function PersonalAction({ path, method, label }: { path: string; method: string; label: string }) {
  const router = useRouter(); const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  return <><button className="text-button" disabled={busy} onClick={async () => { setBusy(true); try { const response = await fetch(path, { method });
    if (!response.ok) setError((await response.json()).message || 'Алдаа гарлаа.'); else router.refresh(); } catch { setError('Холбоос тасарлаа.'); } finally { setBusy(false); } }}>{label}</button>{error && <p role="alert">{error}</p>}</>;
}
