# Bemon — локал платформын бэлэн байдлын тайлан

Шалгасан огноо: **2026-10-07, Asia/Ulaanbaatar**. Хэрэглэгчийн хүрээ: **байршуулалт болон бодит зураг/media-гаас бусад ажил**.

Монгол/MNT веб, Identity/Listing/Payment/Search үйлчилгээ, Expo Android/iOS код болон локал runtime холбогдсон. Доорх нь бодит локал шалгалтын үр дүн; production нэвтрүүлсэн гэсэн тайлан биш.

## Хэрэгжүүлсэн

- Зар хайх: full text, үнэ/төрөл/өрөө/байршлын шүүлтүүр, geo radius, facets, query-bound cursor; current publication state давхар шалгах; газрын зураг руу холбоос.
- Buyer бүртгэл, админы agent provisioning, агентын зар үүсгэх/засах/нийтлэх/татах dashboard; idempotency, private draft, optimistic ETag.
- Өөрийн профайл/утас, имэйл баталгаажуулах, нэг удаагийн нууц үг сэргээх; raw challenge API/логт буцахгүй; Mailpit локал SMTP delivery.
- OIDC Authorization Code+PKCE/state/nonce/signature/issuer/audience; API access-token scope шаардсанаар ID токеныг хүлээн авахгүй; encrypted server-side credentials, browser HttpOnly opaque handle, native SecureStore handle;30 хоногийн session, automatic refresh rotation, shared durable lease; password reset бүх session/OAuth grant болон JWT epoch-ийг цуцална.
- Хадгалсан зар, buyer хүсэлт, agent private inbox/хариу/төлөв, stale reply protection, durable idempotent notifications.
- Listing outbox → Search inbox/projection/job → OpenSearch monotonic version. Давталт/хуучин snapshot/private tombstone хамгаалалт; хадгалсан хайлт ба personal/email alerts.
- QPay adapter, dev-only тодорхой шошготой test provider;7 хоногийн онцлох20,000₮;30 хоногийн агентын багц99,000₮/5 эрх. Server price, invoice once-only writes/UNKNOWN states, provider evidence, unique receipt registry, PostgreSQL balanced ledger, subscription credit bounds, refund reversal, durable featured grant/revoke.
- Android/iOS React Native дэлгэцүүд, PKCE mobile BFF, web preview, profile/favorites/inquiries/searches/alerts. Нууц client secret mobile bundle-д орохгүй.
-64KiB body bounds, shared write rate limits, exact browser Origin, private internal key, persisted dev RSA key, shared Spring Session, local database restore script, CI job definitions and contracts/docs.

## Баталсан үр дүн

| Шалгалт | Үр дүн |
| --- | --- |
| Java unit + PostgreSQL/HTTP/OAuth integration |54 passed: Identity15, Listing27, Payment12 |
| Search PostgreSQL/OpenSearch integration (идэвхжүүлсэн Conda) |6 passed |
| Web unit |4 passed |
| Mobile API/dependency regression |7 passed |
| Chromium actual backend E2E |16 passed |
| **Нийт автомат тест** |**87 passed** |
| Local smoke |18 checks passed |
| Next.js production Docker build | Passed |
| Expo Android/iOS/web export | Passed, Hermes native bundles + web artifact |
| Mobile clean Docker npm ci/build | Passed, install-time mitigations applied |
| Local runtime |12 healthy Compose services |
| Backup + independent temporary restore |4 databases restored; current principal row counts matched |
| npm production audit | Web0; mobile15 high version-based findings remain locally mitigated; see below |

E2E нь бодит имэйлээр verification/reset, favorites/inquiries/reply, saved-search alert, agent purchase→admin explicit test settlement→public featured, concurrent web refresh, mobile refresh/client audience, Expo web preview OAuth→profile→logout, дөрвөн API дахь access/ID token зориулалтын ялгаа, revoked credentials/logout, authorization/CSRF/origin and desktop/390px layouts-ийг шалгасан. Search-ийн индекс eventual consistency-тэй тул publish-ийн шалгалт бодит propagation-ийг хүлээдэг.

Private restore proof: `/tmp/bemon-local-final-restore.log`; backup directory `/tmp/bemon-restore.W6wJL3` (mode700/600): Identity28 users, Listing19 properties, Payment3 orders, Search3 saved searches at that snapshot. User accounts/volumes/credentials хадгалагдсан; synthetic accounts болон withdrawn fixture зарууд цааш үлдэнэ. Энэ нь off-server/production backup proof биш.

Тестийн тайлан: `domain-*/target/surefire-reports`, `domain-*/target/failsafe-reports`, `domain-web/playwright-report`, `domain-web/test-results`; screenshot desktop/mobile/editor/native-web-preview. CI definitions нэмэгдсэн боловч remote CI ажиллуулаагүй. Тест тоо нь actual report-уудаас гарсан; app build-ийг төхөөрөмжийн ажиллуулалт гэж тооцоогүй.

## Гаднах орчноос хамаарах баталгаажуулалт

- Хэрэглэгчийн хүсэлтээр external deployment болон бодит зураг/media delivery хийхгүй.
- QPay merchant credentials, reachable HTTPS callback, шаардлагатай card/refund setup өгөгдөөгүй. Adapter-ийн fixture гэрээ/локал ledger урсгал батлагдсан; бодит purchase/refund хараахан шалгаагүй. UNKNOWN write-ийг дахин илгээх/гараар paid болгох хориотой; provider evidence-ээр support reconciliation шаардлагатай.
- Production SMTP credentials өгөгдөөгүй; одоогийн delivery localhost Mailpit. Email failure12 удаад operator review-д үлдэнэ.
- Ubuntu дээр Android SDK/Xcode байхгүй: signed APK/IPA, emulator/physical Android+iOS device validation болон store submission хийгдээгүй. Native bundle/export, mobile BFF болон ижил React Native web screens шалгагдсан.
- Upstream patch гараагүй braces/node-forge advisories-д version-checked local guard болон7 regression/API тест хэрэглэсэн. npm audit package version-оор15 high indirect findings хэвээр гаргана; clean upstream audit гэж тайлагнахгүй. [Дам хамаарлын нотолгоо](dependency-security.md).
- Spring Boot3.5.16/Java17 одоогийн validated baseline. Public launch-д supported Spring baseline эсвэл commercial security support шаардлагатай. Full Java CVE scanner, penetration/load test, Apple/Android release toolchain, real provider test, remote CI proof энэ тайланд байхгүй.

## Ажиллуулах

```bash
mvn -B verify -Pintegration
bash scripts/local.sh init
bash scripts/local.sh up
bash scripts/local.sh seed
bash scripts/local.sh smoke
npm ci --prefix domain-mobile
npm run typecheck --prefix domain-mobile
npm test --prefix domain-mobile
EXPO_OFFLINE=1 npm run export --prefix domain-mobile
node scripts/mobile-preview.mjs
```

Веб http://localhost:3000; mobile web preview http://localhost:8082; Identity9000; Listing8080; Payment8081; Search8001; Mailpit8025. Бүх host port loopback. `.local.env` нууц; passwords/API keys-ийг Git/chat/report-д бүү хуул. `stop` бүх data/index/signing-key volume-ийг хадгална. Search/Payment тохиргоо болон native commands тухайн module README-д бий.
