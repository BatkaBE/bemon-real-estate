# GerHub Payments

Spring Boot3.5.16 / Java17 matches the existing workspace; PostgreSQL owns immutable orders, double-entry ledger, subscriptions/credits and the entitlement outbox.

`bash scripts/local.sh up` starts the loopback API8081. Local Compose explicitly chooses `local-test` under the dev profile; UI labels it as simulation with no real money. The application's standalone default is **disabled**, and local simulation is unavailable outside dev. Only ROLE_AGENCY_ADMIN may simulate or refund.

Offers are configurable `FEATURED_PRICE_MNT=20000` (7days) and `SUBSCRIPTION_PRICE_MNT=99000` (30days,5 featured credits). Amount/currency always come from the server. Agents can buy only for their own active MNT properties; subscription credits expire with the plan. Every create/credit request requires UUID `Idempotency-Key`. Reusing a key with a changed user/product/target conflicts; identical retry returns the original order without another provider write.

Orders commit CREATING before making a provider call. Timeouts/missing invoice responses become CREATE_UNKNOWN; cancellation/refund uncertainty remains CANCEL_UNKNOWN/REFUND_UNKNOWN. Never retry uncertain writes blindly or mark them paid manually. Callback secrets authorize a hint; settlement requires provider-side invoice, MNT amount and paid receipt evidence. Repeated/concurrent confirmation creates one balanced ledger posting and one entitlement. PostgreSQL enforces zero posting balance at commit. Unused plans can be refunded; consumed credits require support review.

QPay configuration (server secrets in `.local.env`, never public/mobile env): PAYMENT_PROVIDER=qpay, QPAY_CLIENT_ID, QPAY_CLIENT_SECRET, QPAY_INVOICE_CODE, QPAY_API_URL, QPAY_CALLBACK_ORIGIN=https://... . Token responses are cached to expiry. Unique sender_invoice_no=orderUUID. Callback-based checking only, no scheduled payment polling. Refunds require provider-confirmed REFUNDED status; incomplete/unexpected provider evidence fails closed. QPay credentials/card-refund setup and real callback reachability remain external; the adapter has fixture contract tests and **has not completed a real-provider purchase/refund**.

API: GET `/v1/payments/offers`; agent POST `/v1/payments/orders` `{offer,propertyId?}`; owner GET `/v1/payments/orders/{id}`, POST `/{id}/check` (after callback only), POST `/{id}/cancel`; agent POST `/v1/payments/credits` `{propertyId}`; GET `/v1/users/me/payments`; admin GET `/v1/admin/payments` and `/{id}`, POST `/{id}/simulate` (dev only) or `/{id}/refund`. Callback GET/POST `/v1/payments/callback/{id}?token=...` never trusts body claims.

Paid grants/revocations retry through the durable outbox to Listing. Fixed expiry and stable grant ID prevent retry from extending service. Public featured placements require ACTIVE/current/unexpired status. Provider UNKNOWN states require operator reconciliation with provider evidence; there is no unsafe force-paid control.

```bash
mvn -B verify -Pintegration -pl domain-payment-service
```

Primary contract: [QPay MerchantV2](https://developer.qpay.mn/mn/docs/merchant?version=2.0.0), [official Postman export](https://developer.qpay.mn/api/docs/apis/merchant/export?locale=mn&version=2.0.0).
