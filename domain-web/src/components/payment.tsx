'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { formatPrice } from '@/lib/presentation';
export type PaymentOrder = { id: string; offer: string; amount: number; currency: 'MNT'; provider: string; status: string; propertyId?: string; invoice?: { qr_image?: string; qr_text?: string; urls?: { name: string; link: string }[] } };
const states: Record<string, string> = { CREATING: 'Нэхэмжлэх үүсгэж байна', PENDING: 'Төлбөр хүлээж байна', CREATE_UNKNOWN: 'Нэхэмжлэхийн хариу тодорхойгүй — дэмжлэгтэй холбогдоно уу', PAID: 'Төлбөр батлагдсан', CANCELLED: 'Цуцалсан', CANCEL_UNKNOWN: 'Цуцлалтын хариу тодорхойгүй', REFUNDED: 'Буцаасан', REFUND_UNKNOWN: 'Буцаалтын хариу тодорхойгүй' };
/** Retains an idempotency key across retries of the same immutable product selection. */
export function Purchase({ propertyId, offers }: { propertyId?: string; offers: { id: string; amount: number }[] }) {
  const router = useRouter(); const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const [keys] = useState<Record<string, string>>({});
  async function submit(offer: string, credit = false) {
    setBusy(true); const key = `${offer}:${propertyId || ''}`; keys[key] ||= crypto.randomUUID();
    try { const response = await fetch(credit ? '/api/payments/credits' : '/api/payments', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': keys[key] }, body: JSON.stringify(credit ? { propertyId } : { offer, ...(propertyId ? { propertyId } : {}) }) });
      const result = await response.json(); if (!response.ok) setError(result.message || 'Алдаа гарлаа.'); else if (credit) { setError('Багцын эрхээр онцоллоо.'); router.refresh(); } else router.push(`/payments/${result.id}`);
    } catch { setError('Холбоос тасарлаа. Ижил хүсэлтийг дахин илгээж болно.'); } finally { setBusy(false); }
  }
  return <div className="panel"><h2>{propertyId ? 'Зараа онцлох' : 'Агентын багц'}</h2><p>{propertyId ? 'Нийтэлсэн MNT зарыг 7 хоног онцолно.' : '30 хоног хүчинтэй, 7 хоногийн онцлох зарын 5 эрхтэй.'}</p>
    {offers.filter(offer => offer.id === (propertyId ? 'FEATURED7' : 'AGENT30')).map(offer => <button key={offer.id} className="button" disabled={busy} onClick={() => submit(offer.id)}>{formatPrice(offer.amount, 'MNT')} — авах</button>)}
    {propertyId && <button className="button button-outline" disabled={busy} onClick={() => submit('CREDIT', true)}>Багцын эрх ашиглах</button>}{error && <p role="status">{error}</p>}
  </div>;
}
/** Displays only provider-produced QR/deeplinks and explicitly distinguishes local simulations. */
export function OrderView({ order, admin = false }: { order: PaymentOrder; admin?: boolean }) {
  const router = useRouter(); const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  async function action(name: string) {
    setBusy(true); try { const response = await fetch(`/api/payments/${order.id}/${name}`, { method: 'POST' }); if (!response.ok) setError((await response.json()).message || 'Төлбөр хараахан батлагдаагүй.'); else router.refresh(); } catch { setError('Холбоос тасарлаа.'); } finally { setBusy(false); }
  }
  return <div className="panel"><h2>{states[order.status] || 'Төлөв шалгаж байна'}</h2><p>{formatPrice(order.amount, 'MNT')} · {order.offer === 'AGENT30' ? 'Агентын багц' : 'Онцлох зар'}</p>
    {order.provider === 'LOCAL_TEST' && <p className="notice">Туршилтын төлбөр — бодит мөнгө шилжихгүй. Админ туршилтын захиалгыг баталгаажуулна.</p>}
    {order.status === 'PENDING' && order.provider === 'QPAY' && <>{order.invoice?.qr_image && /^[A-Za-z0-9+/=]{1,500000}$/.test(order.invoice.qr_image) && <img className="payment-qr" src={`data:image/png;base64,${order.invoice.qr_image}`} alt="QPay төлбөрийн QR" />}
      {order.invoice?.qr_text && <p className="qr-text">{order.invoice.qr_text}</p>}{order.invoice?.urls?.filter(url => url.link.startsWith('https://')).map(url => <a key={url.link} href={url.link} rel="noopener noreferrer">{url.name}</a>)}<button className="button" disabled={busy} onClick={() => action('check')}>Төлөв шалгах</button></>}
    {order.status === 'PENDING' && <button className="button button-outline" disabled={busy} onClick={() => action('cancel')}>Цуцлах</button>}
    {admin && order.provider === 'LOCAL_TEST' && order.status === 'PENDING' && <button className="button" disabled={busy} onClick={() => action('simulate')}>Туршилтын төлбөр батлах</button>}
    {admin && order.status === 'PAID' && <button className="button button-outline" disabled={busy} onClick={() => action('refund')}>Буцаах</button>}{error && <p role="alert">{error}</p>}
    {order.status === 'PAID' && <p>Эрхийг зарын хэсэгт автоматаар идэвхжүүлнэ.</p>}
  </div>;
}
