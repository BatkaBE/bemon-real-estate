# 🗺️ Ultimate Backend Mastery Curriculum (Junior → Senior Roadmap)

Энэхүү гарын авлага нь зөвхөн ярилцлагын 50 асуултаар хязгаарлагдахгүй, орчин үеийн **Enterprise & High-Load Backend Engineering**-д шаардагдах бүх фундаментал болон ахисан шатны сэдвүүдийг хамарсан мастер хөтөлбөр юм.

```
┌──────────────────────────────────────────────────────────────────────┐
│                    BACKEND MASTERY ROADMAP                          │
│                                                                    │
│  Модуль 1          Модуль 2          Модуль 3          Модуль 4    │
│  Network &  ──────> Security & ─────> Database & ─────> System     │
│  Web Proto.         Auth              SQL               Design     │
│                                                                    │
│  Модуль 5          Модуль 6          Модуль 7          Модуль 8    │
│  Software  ──────> DevOps &  ──────> Concurrency ────> Payment     │
│  Arch.              Cloud            & Perf.           Systems     │
│                                                                    │
│  ┌─────────┐  ┌─────────────┐  ┌──────────────┐                   │
│  │ JUNIOR  │→ │ MID-LEVEL   │→ │   SENIOR     │                   │
│  │ Mod 1-3 │  │ Mod 4-5     │  │ Mod 6-8      │                   │
│  └─────────┘  └─────────────┘  └──────────────┘                   │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 📍 Модуль 1: Сүлжээ & Вэб фундаментал (Network & Web Protocols)

> **Зорилго**: HTTP протоколын бүтэц, API дизайн, бодит цагийн протоколуудыг ойлгож, бодит жишээ дээр тайлбарлаж чаддаг болох.

| # | Сэдэв | Гол ойлголтууд |
|---|---|---|
| 1.1 | **HTTP Эволюци** | HTTP/1.1 vs HTTP/2 (Multiplexing, Header compression) vs HTTP/3 (QUIC/UDP) |
| 1.2 | **Сүлжээний суурь** | TCP 3-Way Handshake, TLS/SSL Handshake үйл явц, DNS Resolution |
| 1.3 | **RESTful API Зарчмууд** | Resource Naming, Idempotency, Statelessness, Richardson Maturity Model |
| 1.4 | **API Дизайн нэмэлт** | API Versioning (URI vs Header vs Media-Type), Pagination (Offset-based vs Cursor-based), HATEOAS |
| 1.5 | **Бодит цагийн протоколууд** | WebSockets, Server-Sent Events (SSE), Long Polling |
| 1.6 | **Microservice Харилцаа** | gRPC vs REST vs GraphQL, Protocol Buffers, gRPC streaming types: unary / server-streaming / client-streaming / bidi |
| 1.7 | **Вэб Аюулгүй байдал** | CORS, Content Negotiation, Security Headers (HSTS, CSP, X-Frame-Options) |

📄 **Дэлгэрэнгүй хичээл**: [module1_network_web_protocols.md — бэлэн]

---

## 📍 Модуль 2: Аюулгүй байдал & Аутентификаци (Security & Auth)

> **Зорилго**: Нэвтрэлт, эрхийн удирдлага, криптографи болон нийтлэг халдлагуудын зарчмыг ойлгож, хамгаалалтын кодыг бичиж чаддаг болох.

| # | Сэдэв | Гол ойлголтууд |
|---|---|---|
| 2.1 | **AuthN vs AuthZ** | RBAC (Role-Based), ABAC (Attribute-Based) |
| 2.2 | **Token ба Session** | Session Cookie vs JWT, Refresh Token Rotation, Stateless Authentication |
| 2.3 | **Олон улсын стандартууд** | OAuth 2.0 (Authorization Code Flow, PKCE), OpenID Connect (OIDC), SSO |
| 2.4 | **Криптографи** | Hashing (Argon2, bcrypt, PBKDF2), Symmetric (AES-256) vs Asymmetric (RSA/ECC) |
| 2.5 | **OWASP Top 10** | SQL Injection, XSS, CSRF, SSRF, Broken Access Control |
| 2.6 | **API Protection** | Rate Limiting (Token Bucket, Leaky Bucket, Sliding Window), IP Whitelisting |
| 2.7 | **Secrets Management** | Vault, KMS, Environment-based secret injection (12-Factor App) |

📄 **Дэлгэрэнгүй хичээл**: [module2_security_auth_deep_guide.md](file:///home/batka/.gemini/antigravity/brain/5dce79bd-f8ba-4f88-ba89-d3bee9ae8ff8/module2_security_auth_deep_guide.md)

---

## 📍 Модуль 3: Өгөгдлийн бааз ба Оновчлол (Databases & Storage)

> **Зорилго**: SQL/NoSQL баазын дотоод бүтэц, оновчлолын арга техник, тархмал баазын загваруудыг эзэмших.

| # | Сэдэв | Гол ойлголтууд |
|---|---|---|
| 3.1 | **Indexing** | B-Tree, Hash, GIN, GiST, BRIN, Clustered vs Non-Clustered, Covering Index |
| 3.2 | **ACID & Transactions** | Atomicity, Consistency, Isolation, Durability |
| 3.3 | **Isolation Levels** | Read Uncommitted → Read Committed → Repeatable Read → Serializable |
| 3.4 | **Concurrency anomalies** | Dirty Read, Non-repeatable Read, Phantom Read, Lost Update |
| 3.5 | **Normalization** | 1NF → 2NF → 3NF → BCNF, Denormalization trade-offs |
| 3.6 | **Connection & Deadlock** | Connection Pooling, Deadlock detection & prevention |
| 3.7 | **NoSQL Databases** | Document (MongoDB), Key-Value (Redis), Columnar (Cassandra), Graph (Neo4j) |
| 3.8 | **Баазын Ахисан шат** | Migration (Flyway), Replication (Primary-Replica, Sync/Async) |
| 3.9 | **Sharding & Partitioning** | Hash / Range / Directory-based sharding, Consistent Hashing |
| 3.10 | **OLTP vs OLAP** | Data Warehouse суурь, Query optimization (`EXPLAIN ANALYZE`) |

📄 **Дэлгэрэнгүй хичээл**: [module3_database_sql_deep_guide.md](file:///home/batka/.gemini/antigravity/brain/5dce79bd-f8ba-4f88-ba89-d3bee9ae8ff8/module3_database_sql_deep_guide.md)

---

## 📍 Модуль 4: Системийн архитектур & Тархмал систем (System Design & Scalability)

> **Зорилго**: Их ачааллын системийг зохиомжлох, Caching/Messaging/Distributed Systems-ийн загварчлалыг эзэмших.

| # | Сэдэв | Гол ойлголтууд |
|---|---|---|
| 4.1 | **Scalability & Availability** | Horizontal vs Vertical Scaling, Load Balancing (L4 vs L7), Health checks (liveness / readiness probes) |
| 4.2 | **Caching Стратеги** | Cache-Aside, Read-Through, Write-Through, Write-Around, Write-Back, Redis Eviction (LRU, LFU), Cache Stampede |
| 4.3 | **Message Queues & Events** | RabbitMQ vs Kafka, Pub-Sub, Event Sourcing, Consumer Groups, Dead Letter Queue, At-least-once vs Exactly-once delivery |
| 4.4 | **Тархмал систем** | CAP Theorem, PACELC Theorem, Eventual Consistency, Distributed Locking (Redlock), Idempotency Keys |
| 4.5 | **Advanced Patterns** | Outbox Pattern, Change Data Capture (CDC / Debezium), Consensus Algorithms (Raft, Paxos) — суурь |
| 4.6 | **Microservices Patterns** | API Gateway, Service Discovery, Circuit Breaker (Resilience4j), Bulkhead, Retry w/ Exponential Backoff, Saga Pattern, Strangler Fig Pattern |

📄 **Дэлгэрэнгүй хичээл**: *Дараагийн хичээлд бэлдэгдэнэ*

---

## 📍 Модуль 5: Кодын Архитектур ба Дизайн Паттернууд (Software Architecture & Best Practices)

> **Зорилго**: Цэвэр, тестлэгдсэн, өргөтгөх боломжтой код бичих зарчим, архитектурын загваруудыг эзэмших.

| # | Сэдэв | Гол ойлголтууд |
|---|---|---|
| 5.1 | **SOLID зарчмууд** | Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion |
| 5.2 | **Creational Patterns** | Singleton, Factory, Builder |
| 5.3 | **Structural Patterns** | Adapter, Decorator, Facade |
| 5.4 | **Behavioral Patterns** | Observer, Strategy, Chain of Responsibility |
| 5.5 | **Архитектурын загварууд** | Clean Architecture, Hexagonal (Ports & Adapters), Layered Architecture, DDD (Domain-Driven Design) |
| 5.6 | **Дата хандалтын паттернууд** | Repository Pattern, CQRS (Command Query Responsibility Segregation), Event-Driven Architecture |
| 5.7 | **Тестчлэл** | Unit / Integration / E2E Testing, TDD, Mocking |
| 5.8 | **Code Standards** | Middleware, Global Exception Handling, Structured Logging (JSON / Zap / Winston), Context Propagation |

📄 **Дэлгэрэнгүй хичээл**: [module5_software_architecture_deep_guide.md](file:///home/batka/.gemini/antigravity/brain/5dce79bd-f8ba-4f88-ba89-d3bee9ae8ff8/module5_software_architecture_deep_guide.md)

---

## 📍 Модуль 6: DevOps, Cloud & Observability (Инфраструктур & Хяналт)

> **Зорилго**: Аппыг containerize хийж, deploy хийж, хянаж сурах. CI/CD pipeline, Kubernetes, Observability stack-ийг ойлгох.

| # | Сэдэв | Гол ойлголтууд |
|---|---|---|
| 6.1 | **Containerization** | Dockerfile бичих, Docker Image optimization (multi-stage builds), Docker Compose |
| 6.2 | **Orchestration** | Kubernetes (Pods, Deployments, Services, Ingress, ConfigMaps, Secrets) |
| 6.3 | **Infrastructure as Code** | Terraform, Ansible — declarative infra provisioning |
| 6.4 | **Service Mesh** | Istio / Linkerd — traffic management, mTLS хоорондын service |
| 6.5 | **Deployment Strategies** | Blue-Green, Canary Release, Rolling Update, Feature Flags |
| 6.6 | **CI/CD Pipeline** | GitHub Actions / GitLab CI — automated test, build, deploy |
| 6.7 | **API Documentation** | OpenAPI / Swagger спецификаци |
| 6.8 | **Observability** | Metrics (Prometheus & Grafana), Logging (ELK / Loki), Distributed Tracing (OpenTelemetry, Jaeger, Correlation ID) |
| 6.9 | **12-Factor App** | Cloud-Native апп бичих 12 зарчим |

📄 **Дэлгэрэнгүй хичээл**: [module6_devops_cloud_deep_guide.md](file:///home/batka/.gemini/antigravity/brain/5dce79bd-f8ba-4f88-ba89-d3bee9ae8ff8/module6_devops_cloud_deep_guide.md)

---

## 📍 Модуль 7: Concurrency & Performance (JVM/Java Stack-д тусгайлан чухал)

> **Зорилго**: Олон урсгалт програмчлал, Reactive загвар, JVM-ийн дотоод ажиллагааг ойлгож, өндөр гүйцэтгэлтэй серверийн апп бичих.

| # | Сэдэв | Гол ойлголтууд |
|---|---|---|
| 7.1 | **Thread Model** | Thread Pool, ExecutorService, Future vs CompletableFuture |
| 7.2 | **Reactive Programming** | Project Reactor, Spring WebFlux, Backpressure зарчим |
| 7.3 | **Non-blocking I/O** | Netty суурь ойлголт, Event Loop |
| 7.4 | **JVM гүнзгийрүүлэлт** | Memory Model (Heap / Stack / Metaspace), GC алгоритмууд (G1, ZGC), JIT Compilation |
| 7.5 | **Performance Profiling** | Thread dump, Heap dump analysis, JFR (Java Flight Recorder) |

📄 **Дэлгэрэнгүй хичээл**: *Дараагийн хичээлд бэлдэгдэнэ*

---

## 📍 Модуль 8: Payment Systems Engineering (Сонголт — Payment чиглэлд)

> **Зорилго**: Төлбөрийн системийн суурь архитектурыг ойлгож, аюулгүй, найдвартай, тулгалттай (reconcilable) систем зохиомжлох.

| # | Сэдэв | Гол ойлголтууд |
|---|---|---|
| 8.1 | **Idempotency in Payments** | Idempotency Key, давхар transaction-оос сэргийлэх |
| 8.2 | **Double-Entry Ledger** | Debit/Credit бүртгэл, санхүүгийн зөрүүгүй байдал |
| 8.3 | **Distributed Transactions** | Saga Pattern — захиалга → төлбөр → inventory |
| 8.4 | **Webhook Reliability** | Retry logic, HMAC signature verification, at-least-once delivery |
| 8.5 | **Reconciliation Systems** | Дотоод бүртгэл vs банк/PSP-ийн өгөгдлийг тулгах |
| 8.6 | **Compliance** | PCI-DSS ойлголт, card data зохицуулалт |

📄 **Дэлгэрэнгүй хичээл**: *Дараагийн хичээлд бэлдэгдэнэ*

---

## 📊 Модуль бүрийн Явцын хянах хуудас (Progress Tracker)

| Модуль | Төлөв | Хичээлийн файл |
|---|---|---|
| **Модуль 1**: Network & Web | ✅ Бэлэн | Чат дотор дэлгэрэнгүй тайлбарлагдсан |
| **Модуль 2**: Security & Auth | ✅ Бэлэн | [module2_security_auth_deep_guide.md](file:///home/batka/.gemini/antigravity/brain/5dce79bd-f8ba-4f88-ba89-d3bee9ae8ff8/module2_security_auth_deep_guide.md) |
| **Модуль 3**: Database & SQL | ✅ Бэлэн | [module3_database_sql_deep_guide.md](file:///home/batka/.gemini/antigravity/brain/5dce79bd-f8ba-4f88-ba89-d3bee9ae8ff8/module3_database_sql_deep_guide.md) |
| **Модуль 4**: System Design | ✅ Бэлэн | [module4_system_design_deep_guide.md](file:///home/batka/.gemini/antigravity/brain/5dce79bd-f8ba-4f88-ba89-d3bee9ae8ff8/module4_system_design_deep_guide.md) |
| **Модуль 5**: Software Arch | ✅ Бэлэн | [module5_software_architecture_deep_guide.md](file:///home/batka/.gemini/antigravity/brain/5dce79bd-f8ba-4f88-ba89-d3bee9ae8ff8/module5_software_architecture_deep_guide.md) |
| **Модуль 6**: DevOps & Cloud | ✅ Бэлэн | [module6_devops_cloud_deep_guide.md](file:///home/batka/.gemini/antigravity/brain/5dce79bd-f8ba-4f88-ba89-d3bee9ae8ff8/module6_devops_cloud_deep_guide.md) |
| **Модуль 7**: Concurrency | ⏳ Дараагийн | — |
| **Модуль 8**: Payment Systems | ⏳ Хүлээгдэж буй | — |

---

## 🎓 Суралцах арга зүй

Бид модуль бүрийг дараах **4 давхаргын бүтцээр** хамтдаа эзэмшинэ:

```
┌─────────────────────────────────────────────────────────┐
│  1. 🏠 Бодит амьдралын аналоги                          │
│     → Ойлголтыг өдөр тутмын жишээгээр тайлбарлана       │
│                                                         │
│  2. 📊 Алхам алхмаар диаграмм                           │
│     → Техникийн үйл явцыг харааны зургаар харуулна       │
│                                                         │
│  3. 💻 Бодит кодын жишээ                                │
│     → Хэрхэн хэрэгжүүлдгийг кодоор харуулна             │
│                                                         │
│  4. 🧩 Шалгах асуулт                                    │
│     → Ойлголтоо батжуулах практик асуултууд              │
└─────────────────────────────────────────────────────────┘
```
