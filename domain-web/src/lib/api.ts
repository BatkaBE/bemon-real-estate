import 'server-only';
import { settings } from './config';
import type { Problem } from './types';
/** Preserves backend status codes for page and mutation error handling. */
export class ApiError extends Error {
  constructor(public readonly status: number, public readonly problem: Problem = {}) {
    super(problem.detail || 'Үйлчилгээнд холбогдох үед алдаа гарлаа. Дахин оролдоно уу.');
  }
}
/** Fetches uncached backend data with bounded timeouts and server-only credentials. */
export async function backend<T>(service: 'identity' | 'listing' | 'search' | 'payment', path: string, init: RequestInit = {}): Promise<T> {
  const config = settings();
  const base = ({ identity: config.identityInternal, listing: config.listingInternal, search: config.searchInternal, payment: config.paymentInternal })[service];
  let response: Response;
  try { response = await fetch(`${base}${path}`, { ...init, cache: 'no-store', signal: AbortSignal.timeout(12000) }); }
  catch { throw new ApiError(503); }
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiError(response.status, body);
  return body as T;
}
/** Returns Mongolian messages without reflecting raw request bodies. */
export function errorMessage(status: number): string {
  return ({ 400: 'Оруулсан мэдээллээ шалгана уу.', 401: 'Нэвтрэх хугацаа дууссан. Дахин нэвтэрнэ үү.',
    403: 'Энэ үйлдлийг хийх эрхгүй байна.', 404: 'Зар олдсонгүй.',
    409: 'Хүсэлт одоогийн төлөвтэй зөрчилдөж байна. Мэдээллээ шинэчилнэ үү.',
    412: 'Зарыг өөр газраас шинэчилсэн байна. Хуудсыг шинэчилж байгаад дахин засна уу.',
    503: 'Үйлчилгээ түр боломжгүй байна. Түр хүлээгээд дахин оролдоно уу.' } as Record<number, string>)[status]
    || 'Алдаа гарлаа. Дахин оролдоно уу.';
}
