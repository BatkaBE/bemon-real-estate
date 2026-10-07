/** Shares a bounded gateway client between native screens and deterministic boundary tests. */
export interface HandleStorage { get(): Promise<string | null>; set(value: string): Promise<void>; remove(): Promise<void> }
export class ApiFailure extends Error {
  constructor(public readonly status: number, message: string) { super(message); }
}
/** Never persists raw OAuth credentials or accepts a caller-selected absolute request URL. */
export class ApiClient {
  constructor(private readonly base: string, private readonly storage: HandleStorage, private readonly send: typeof fetch = (input, init) => globalThis.fetch(input, init)) {}
  async request<T>(path: string, options: { method?: string; body?: unknown; key?: string } = {}): Promise<T> {
    if (!path.startsWith('/') || path.startsWith('//') || path.includes('://')) throw new ApiFailure(400, 'Буруу хүсэлт.');
    const handle = await this.storage.get();
    const response = await this.send(`${this.base}/api/mobile${path}`, { method: options.method || 'GET',
      headers: { 'Content-Type': 'application/json', ...(handle ? { Authorization: `Bearer ${handle}` } : {}), ...(options.key ? { 'Idempotency-Key': options.key } : {}) },
      signal: AbortSignal.timeout(30000), ...(options.body === undefined ? {} : { body: JSON.stringify(options.body) }) });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) { if (response.status === 401) await this.storage.remove(); throw new ApiFailure(response.status, body.message || 'Хүсэлт амжилтгүй боллоо.'); }
    return body as T;
  }
  async exchange(code: string, verifier: string, nonce: string, redirectUri: string): Promise<void> {
    const result = await this.request<{ handle: string }>('/exchange', { method: 'POST', body: { code, verifier, nonce, redirectUri } });
    if (typeof result.handle !== 'string' || result.handle.split('.').length !== 5) throw new ApiFailure(401, 'Нэвтрэлт батлагдсангүй.');
    await this.storage.set(result.handle);
  }
  async logout(): Promise<void> {
    try { await this.request('/logout', { method: 'POST' }); } finally { await this.storage.remove(); }
  }
}
export interface Property { id: string; title: string; price: number | null; currency: string; listingType: string; bedrooms: number | null; landSizeSqm: number | null; address: { suburb: string; addressLine: string; latitude: number; longitude: number } }
export interface Criteria { q: string; suburb: string; listingType: string; minPrice: string; maxPrice: string; latitude: string; longitude: string; radiusKm: string }
/** Omits empty filters and resets pagination whenever a query changes. */
export function searchParams(criteria: Criteria, cursor?: string): string {
  const params = new URLSearchParams({ currency: 'MNT' });
  for (const [key, value] of Object.entries(criteria)) if (value.trim()) params.set(key, value.trim());
  if (cursor) params.set('cursor', cursor);
  return params.toString();
}
