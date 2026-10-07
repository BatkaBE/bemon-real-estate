# GerHub веб MVP

Монгол интерфейс, MNT каталог, SSR зарын дэлгэрэнгүй болон агентын удирдлагын хэсэг. Next.js 16.3.8, React 19.3.0, TypeScript, jose ашиглана. Node.js 22+ шаардлагатай.

## Локал ажиллуулах

Repository root-оос:

```bash
mvn -B verify -Pintegration
bash scripts/local.sh init
bash scripts/local.sh up
bash scripts/local.sh seed
```

Веб: http://localhost:3000. `up` нь production Next.js image build хийнэ; host дээр npm install заавал хийхгүй. `seed` зургаан **Жишээ** гэсэн шошготой синтетик MNT зар болон локал агент нэмнэ. Дахин ажиллуулахад байгаа зар, account, password-ийг солихгүй. `stop` өгөгдлийн volume-уудыг хадгална.

Шинэ тохиргоонд админ: `admin@gerhub.local`; агент: `agent@gerhub.local`. Одоо байгаа installation-ийн бүртгэлүүд хадгалагдана; бодит имэйл, нууц үгс root-ийн Git-д орохгүй `.local.env` дахь `DEV_ADMIN_EMAIL`, `DEV_AGENT_EMAIL`, `DEV_ADMIN_PASSWORD`, `DEV_AGENT_PASSWORD`. Нууц утгуудыг тайлан, screenshot, Git-д хуулж болохгүй.

Public бүртгэл buyer үүсгэнэ. Админ `/dashboard`-оос агент үүсгэнэ. Агент өөрийн заруудаа жагсаах, MNT ноорог үүсгэх, засах, нийтлэх, төлөв шилжүүлэх боломжтой. MNT түрээсийн үнийг сараар оруулна. AUD зарын үнэ, хугацааг автоматаар хөрвүүлэхгүй.

## Frontend хөгжүүлэлт

Compose-ийн веб контейнер 3000 порт ашиглана. Backend-үүдийг үлдээн зөвхөн вебийг зогсоогоод host хөгжүүлэлтийн сервер ажиллуулж болно:

```bash
docker compose --project-name bemon-local --env-file .local.env \
  -f domain-platform-infra/compose.local.yaml stop web
cd domain-web
npm ci
node --env-file=../.local.env node_modules/next/dist/bin/next dev --hostname 127.0.0.1
```

Дараа нь браузерт **localhost** хаяг ашиглана; `127.0.0.1` нь өөр origin тул OAuth callback болон mutation origin шалгалттай зөрнө. `.env.example` сервер талын тохиргоонуудыг тайлбарлана. Compose ажиллуулбал internal Identity/Listing URL-ууд нь контейнерийн нэрээр, public issuer нь localhost-оор тохирно.

## Шалгалт

`typecheck` эхлээд Next route types үүсгэдэг тул цэвэр checkout-д ажиллана.

```bash
cd domain-web
npm ci
npm run typecheck
npm test
npm run build
PLAYWRIGHT_BROWSERS_PATH=0 npx playwright install chromium
PLAYWRIGHT_BROWSERS_PATH=0 npm run test:e2e
```

Browser тестэд root `.local.env`, ажиллаж буй Compose stack, `seed`-ийн агент/жишээ зар шаардлагатай. API mock ашиглахгүй. Buyer/admin/agent нэвтрэлт, PKCE callback, HttpOnly encrypted cookie, localStorage-д token байхгүй байдал, cross-origin/эрхийн хязгаар, private draft, publish/edit/stale ETag, logout, tampered cookie, desktop/mobile layout шалгана. Тест account-ууд хадгалагдана; амжилттай үүсгэсэн browser зар эцэстээ withdrawn болж хадгалагдана. Үүний өмнөх run эсвэл тасалдсан run-ийн синтетик зарууд үлдэж болно.

`playwright-report/index.html` болон `test-results/` screenshot-ууд Git-д орохгүй. OAuth trace password/token агуулж болзошгүй тул идэвхгүй. Chromium шалгалт нь Firefox/WebKit дээр шалгасан гэсэн үг биш.

## Нэвтрэлтийн хил

Веб Authorization Code + PKCE урсгалд state, nonce, issuer, audience, RS256 signature шалгана. Access/refresh/ID token нь Identity-ийн shared session store-д 32-byte random түлхүүрээр шифрлэгдэж хадгалагдана. HttpOnly cookie-д зөвхөн шифрлэсэн opaque session handle байна. JavaScript-д уншигдахгүй; HTTPS origin-д cookie Secure байна. Cookie хэмжээ 3800 байтаар хязгаарлагдана. Зар/admin/register/logout mutation нь exact Origin шалгана. Веб API нь дурын backend URL дамжуулах proxy биш.

Access token 15 минут; session absolute lifetime30 хоног. Сервер refresh token-ийг автоматаар сольж, PostgreSQL lease нь зэрэг refresh хийхээс хамгаална. Нууц үг сэргээхэд browser/native session, OAuth grants устаж, JWT auth epoch өөрчлөгдөнө. Гарахад веб cookie устаж, refresh token revoke хүсэлт илгээгдэж, Identity OIDC logout хийнэ. Identity түр offline үед локал cookie цэвэрлэгдэх боловч provider session/revocation баталгаажихгүй.

## Үлдсэн хүрээ

Зураг upload/confirmation/download, агенттай холбогдох lead/contact, favorite, төлбөр, password reset/email verification, мобайл апп, full-text/geo search хийгдээгүй. Зарын дүрслэл нь кодоор зурсан placeholder бөгөөд **Зураг нэмээгүй** гэж тэмдэглэгдэнэ. Production HTTPS, persistent signing keys, rate limiting, shared Identity browser sessions, backup/restore болон monitoring тусдаа шаардлагатай. [Бэлэн байдлын тайлан](../docs/readiness.md).

Хадгалсан зар `/favorites`, хүсэлт `/inquiries`, профайл `/account`, хайлт `/searches`, мэдэгдэл `/alerts`, төлбөр `/payments`. QPay credential-гүй локал төлбөр нь тодорхой шошготой тест; админ өөрийн төлбөрийн түүх хэсгээс тест захиалгыг батална. Native BFF `/api/mobile/*` зөвхөн named routes, cookie хэрэглэхгүй; browser Origin localhost8082-оор хязгаарлагдана.
