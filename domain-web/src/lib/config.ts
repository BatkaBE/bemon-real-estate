import 'server-only';
/** Keeps public issuer, internal URLs, and browser origin explicit behind Compose. */
export function settings() {
  const issuer = process.env.IDENTITY_ISSUER_URI || `http://localhost:${process.env.BEMON_IDENTITY_PORT || '9000'}`;
  return {
    appOrigin: process.env.APP_ORIGIN || 'http://localhost:3000', issuer,
    identityInternal: process.env.IDENTITY_INTERNAL_URL || issuer,
    jwksUrl: process.env.IDENTITY_JWK_SET_URI || `${issuer}/oauth2/jwks`,
    listingInternal: process.env.LISTING_INTERNAL_URL || `http://localhost:${process.env.BEMON_LISTING_PORT || '8080'}`,
    clientId: process.env.OAUTH_WEB_CLIENT_ID || 'domain-web',
    clientSecret: process.env.OAUTH_WEB_CLIENT_SECRET || '', sessionSecret: process.env.WEB_SESSION_SECRET || '',
    searchInternal: process.env.SEARCH_INTERNAL_URL || 'http://localhost:8000',
    paymentInternal: process.env.PAYMENT_INTERNAL_URL || 'http://localhost:8081',
    mobileClientId: 'domain-mobile', mobileClientSecret: process.env.OAUTH_MOBILE_CLIENT_SECRET || '',
    mobileWebOrigin: process.env.MOBILE_WEB_ORIGIN || 'http://localhost:8082',
    internalKey: process.env.INTERNAL_SERVICE_KEY || '',
  };
}
