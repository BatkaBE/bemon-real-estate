# Хүрээ ба дууссан ажил — 2026-10-07

Хэрэглэгч: байршуулалт, бодит зураг/media-гаас бусад ажлыг хийх; Монгол/MNT интерфейс; төлбөрийн сонголтыг хэрэгжүүлэгч шийдэх; Android ба iOS хоёуланг тохируулах.

- [x] Account profile/contact, single-use email verification/password reset.
- [x] Shared encrypted sessions, refresh rotation/lease, web/native logout and credential revocation.
- [x] Favorites, participant-scoped inquiries and agent replies.
- [x] Durable outbox/inbox, full text/facets/geo/cursor search.
- [x] Personal saved searches, in-app/email alerts.
- [x] QPay adapter and explicit dev test provider, immutable/idempotent orders and receipt registry, balanced ledger.
- [x] Featured grant/revoke, subscription credits, checkout/admin reconciliation UI.
- [x] Expo Android/iOS code/configuration, secure PKCE BFF, both native bundles and web preview.
- [x] Body/rate/origin/security bounds, dependency mitigations/regression checks, restore proof, CI definitions and docs.

[Бэлэн байдлын тайлан](readiness.md) нь бодит тест/артефакт болон гадна орчноос хамаарах нотолгооны хязгаарыг тусад нь тайлбарлана. Credentials-гүй QPay/SMTP production integration болон signed native-device validation-ийг хийгдсэн гэж тооцоогүй.

Excluded: external deployment/hosting, real media. All existing data/accounts/passwords/volumes are retained; new credentials are appended once in ignored private settings.
