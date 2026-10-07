/** Accepts internal paths only, preventing OAuth next parameters from becoming open redirects. */
export function safeReturnPath(value: string | null | undefined, fallback = '/dashboard'): string {
  if (!value || !value.startsWith('/') || value.startsWith('//') || /[\\\r\n]/.test(value)) return fallback;
  try {
    const parsed = new URL(value, 'https://bemon.invalid');
    return parsed.origin === 'https://bemon.invalid' ? parsed.pathname + parsed.search : fallback;
  } catch { return fallback; }
}
/** Cookie-authenticated mutations require an exact browser origin, including its port. */
export function isSameOrigin(origin: string | null, expectedOrigin: string): boolean {
  if (!origin) return false;
  try { return new URL(origin).origin === new URL(expectedOrigin).origin; } catch { return false; }
}
/** Limits identifiers before they are interpolated into backend URLs. */
export function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}
