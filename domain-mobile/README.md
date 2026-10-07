# GerHub Android and iOS

Expo57 / React Native0.86.3 / React19.2.3, TypeScript. Mongolian/MNT buyer/renter app: full-text discovery, price/location/radius filters, detail/map, favorites, buyer inquiries/replies, saved searches/alerts, profile and recovery/signup links. No real images; placeholders are labeled.

```bash
npm ci --prefix domain-mobile
npm run typecheck --prefix domain-mobile
npm test --prefix domain-mobile
EXPO_OFFLINE=1 npm run export --prefix domain-mobile
node scripts/mobile-preview.mjs # localhost:8082, exported React Native web preview
```

Native setup: copy `.env.example` to ignored `.env`, then `npm run android` (installed Android SDK/JDK) or `npm run ios` (macOS/Xcode). App identifiers: `mn.gerhub.app`; scheme `gerhub://oauth`. AuthSession custom schemes require a native development build; Expo Go is not the target. Android emulator usually needs `EXPO_PUBLIC_API_URL=http://10.0.2.2:3000` and `EXPO_PUBLIC_IDENTITY_URL=http://10.0.2.2:9000`; iOS simulator uses localhost. Use reachable HTTPS URLs for physical/release devices. Native signed APK/IPA builds, physical-device testing and store submission are not claimed; this Ubuntu environment has no Android SDK/Xcode. EAS profiles are supplied but no cloud build or publication was run.

PKCE+state is handled by AuthSession. Server BFF exchanges the code using a confidential domain-mobile client, verifies signed access/ID tokens and nonce, stores encrypted credentials in Identity's30day session store, and returns an opaque encrypted handle. Android/iOS persist only that handle in SecureStore (Keystore/Keychain, device-only/unlocked). No client secret, access/refresh tokens or encryption keys are shipped. The optional web preview stores its handle only in memory. Server refresh uses the same durable lease as web sessions; logout deletes the session; password reset revokes web/native sessions and JWT epochs. Identity SSO may remain in the system browser until its own logout; removing the native handle still revokes access through this app.

Only named `/api/mobile/*` routes are proxied. Cookie credentials are ignored, wrong browser Origin is rejected, POST inquiry keys are stable per message. Account roles/email cannot be selected by profile forms. Private records remain participant-scoped on the backend.

Primary SDK docs: [AuthSession](https://docs.expo.dev/versions/latest/sdk/auth-session/), [SecureStore](https://docs.expo.dev/versions/latest/sdk/securestore/).


Security: `npm ci` runs version-checked local braces/node-forge mitigations and a uuid11.1.1 override; run the regression suite after dependency changes. The registry still flags15 high transitive findings because upstream patched releases are unavailable. See [dependency-security](../docs/dependency-security.md) for exact evidence and limitations.
