'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import type { Property, PropertyStatus } from '@/lib/types';
import { statuses } from '@/lib/presentation';
const transitions: Record<PropertyStatus, PropertyStatus[]> = {
  DRAFT: ['ACTIVE', 'WITHDRAWN'], ACTIVE: ['UNDER_OFFER', 'SOLD', 'RENTED', 'WITHDRAWN'],
  UNDER_OFFER: ['ACTIVE', 'SOLD', 'WITHDRAWN'], SOLD: [], RENTED: [], WITHDRAWN: [],
};
/** Offers only allowed lifecycle transitions while the backend remains the final authority. */
export function StatusForm({ property }: { property: Property }) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const options = transitions[property.status];
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); setPending(true); setError('');
    const status = new FormData(event.currentTarget).get('status');
    try {
      const response = await fetch(`/api/properties/${property.id}/status`, { method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'If-Match': `"${property.version}"` }, body: JSON.stringify({ status }) });
      if (!response.ok) { const body = await response.json(); setError(body.message || 'Алдаа гарлаа.'); return; }
      router.refresh();
    } catch { setError('Сүлжээний алдаа гарлаа. Дахин оролдоно уу.'); }
    finally { setPending(false); }
  }
  return <div className="status-panel"><div><span className="eyebrow">ЗАРЫН ТӨЛӨВ</span><strong className={`status ${property.status.toLowerCase()}`}>{statuses[property.status]}</strong></div>
    {options.length ? <form onSubmit={submit}><label className="sr-only" htmlFor="status">Шинэ төлөв</label><select key={property.status} defaultValue={options[0]} name="status" id="status">{options.map(status => <option key={status} value={status}>{statuses[status]}</option>)}</select><button className="button button-small" type="submit" disabled={pending}>{pending ? 'Түр хүлээнэ үү…' : property.status === 'DRAFT' ? 'Төлөв өөрчлөх' : 'Шинэчлэх'}</button></form> : <p>Энэ төлөвөөс цааш шилжүүлэх боломжгүй.</p>}
    {error && <div className="notice error" role="alert">{error}</div>}
  </div>;
}
