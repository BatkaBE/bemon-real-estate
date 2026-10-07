export const identity: string;
export const listing: string;
export function request(url: string, options?: RequestInit): Promise<Response>;
export function json(url: string, expectedStatus: number, options?: RequestInit): Promise<{ body: any; headers: Headers }>;
export function login(email: string, password: string, options?: { clientId?: string; clientSecret?: string; redirectUri?: string; scope?: string; nonce?: string; codeOnly?: boolean }): Promise<{ access_token: string; refresh_token: string; id_token: string; code: string; verifier: string; state: string }>;
