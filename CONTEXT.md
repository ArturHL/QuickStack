# QuickStack Labs - Project Context

## 🎯 Project Goal

Build a **modular workflow automation platform** that enables automated business processes through interconnected modules and n8n orchestration.

## 📖 Background & Motivation

### Why This Project Exists

1. **Real Business Need** - Automate repetitive business tasks (expense tracking, order management, financial reporting)
2. **Learning Through Building** - Understanding modular architecture and enterprise patterns
3. **Portfolio Piece** - Demonstrating system design and full-stack development
4. **Scalable Foundation** - Build once, extend easily with new modules
5. **Solo Developer Friendly** - Architecture designed for maintainability by one person

### Philosophy

> "Start simple, scale when needed. Build modular, deploy monolithic."

We prioritize:
- **Simplicity**: One codebase, one database, one deployment
- **Modularity**: Features organized as independent modules
- **Automation**: Manual tasks replaced by bots and workflows
- **Understanding**: Every decision is intentional and documented

## 🏗️ Current State (January 2026)

### What We Have

**Phase 1: Foundation ✅ COMPLETE**
- ✅ Spring Boot 3.5 + Java 17 configured
- ✅ JWT Authentication (login, register)
- ✅ Multi-tenant User Management (CRUD)
- ✅ PostgreSQL + Flyway migrations
- ✅ 82 tests (100% critical path coverage)
- ✅ Deployed to Render (production ready)
- ✅ Rate Limiting (Bucket4j) - Prevents brute force
- ✅ CORS Configuration - Securely configured
- ✅ Security Headers - 6 HTTP headers implemented

**Phase 2: Business Modules 🔄 NEXT**
- ⏳ Gastos module (expense tracking)
- ⏳ Pedidos module (order management)
- ⏳ Finanzas module (financial statements)
- ⏳ Webhooks for n8n integration

**Phase 3: Automation & Frontend 🔴 PLANNED**
- ⏳ n8n workflows (Telegram bot, scheduled tasks)
- ⏳ React dashboard (central UI)
- ⏳ Reports and analytics

## 🛠️ Technology Stack Decisions

### Backend - Java + Spring Boot

**Decision**: Use Java 17 + Spring Boot 3.x for the entire backend

**Why Java:**
- ✅ Enterprise-grade stability and performance
- ✅ Mature authentication/security ecosystem
- ✅ Strong typing and compile-time safety
- ✅ Excellent for critical auth services
- ✅ Single language reduces context switching (solo dev advantage)
- ✅ Already have `quickstack-core` foundation built

**Why NOT Python (FastAPI):**
- ❌ Would require porting existing auth code
- ❌ Adds language context switching overhead
- ❌ No significant benefit for our use case
- ❌ Java integrates with n8n just as well (HTTP/JSON)

### Architecture - Modular Monolith

**Decision**: Build as a modular monolith, NOT microservices

**Why Modular Monolith:**
- ✅ **Single process** - One deployment, one container
- ✅ **Single database** - Easy SQL joins between modules
- ✅ **Simple debugging** - All logs in one place
- ✅ **Low operational complexity** - Perfect for solo developer
- ✅ **Fast development** - Direct function calls, no HTTP overhead
- ✅ **Cost-effective** - One backend instance (~$10-15/month)

**Why NOT Microservices:**
- ❌ Over-engineering for current scale
- ❌ High operational complexity (4+ services, 4+ databases)
- ❌ Network latency between services
- ❌ Harder to debug distributed systems
- ❌ Expensive hosting (~$40-80/month minimum)

**Module Organization:**
```
quickstack-core/
└── src/main/java/com/quickstack/core/
    ├── auth/       # Infrastructure module
    ├── user/       # Infrastructure module
    ├── tenant/     # Infrastructure module
    ├── gastos/     # Business module
    ├── pedidos/    # Business module
    ├── finanzas/   # Business module
    └── webhooks/   # Integration module
```

### Automation - n8n

**Decision**: Use n8n for workflow automation

**Why n8n:**
- ✅ Visual workflow builder (no code)
- ✅ 400+ pre-built integrations
- ✅ Self-hosted (full control)
- ✅ Perfect for bots (Telegram, WhatsApp, Slack)
- ✅ Scheduled tasks and webhooks
- ✅ Integrates with ANY backend via HTTP (Java is fine)

**n8n Use Cases:**
- Telegram bot: "Gasto: 150 comida" → POST /webhooks/n8n/gastos
- WhatsApp bot: Order notifications
- Scheduled: Daily financial report generation
- Integrations: Connect to external APIs (Stripe, email, etc.)

### Frontend - React + TypeScript + Vite

**Why:**
- ✅ Most popular framework (hiring advantage)
- ✅ Type safety with TypeScript
- ✅ Fast development with Vite
- ✅ Rich ecosystem of components

### Database - PostgreSQL (Neon)

**Why:**
- ✅ Best open-source relational database
- ✅ Excellent for multi-tenant (tenant_id filtering)
- ✅ JSON support for flexibility
- ✅ Neon provides managed hosting with auto-scaling

### Infrastructure - Render + Vercel

**Why:**
- ✅ Simple deployment (no DevOps overhead)
- ✅ Free tier for prototyping
- ✅ Auto-scaling built-in
- ✅ Easy to understand and debug

## 🎓 Key Architectural Decisions

### 1. Monolith vs Microservices

**Decision**: Modular Monolith

**Rationale**:
- Solo developer cannot maintain 4+ microservices efficiently
- Business modules are tightly coupled (finanzas needs gastos + pedidos data)
- No need for independent scaling (all modules scale together)
- Simpler deployment and debugging

**When to Reconsider**: If we reach 10,000+ concurrent users or need to scale modules independently

### 2. Single Database

**Decision**: All modules share one PostgreSQL database

**Rationale**:
- Easy SQL joins (e.g., finanzas can JOIN gastos and pedidos)
- Simpler migrations (one Flyway migration folder)
- Lower cost (one database vs 4+)
- Sufficient isolation via `tenant_id` column

**Trade-off**: Cannot scale modules' databases independently (not a problem at current scale)

### 3. Event-Driven Communication Between Modules

**Decision**: Use Spring's `ApplicationEventPublisher` for inter-module communication

**Rationale**:
- Decouples modules (gastos doesn't import finanzas)
- Easy to add new listeners without modifying existing code
- Synchronous by default (simple), can be async if needed

**Example**:
```java
// Gastos publishes event
eventPublisher.publishEvent(new GastoRegistradoEvent(gasto));

// Finanzas listens
@EventListener
public void onGastoRegistrado(GastoRegistradoEvent event) {
    actualizarBalance(event.getGasto());
}
```

### 4. Multi-Tenant at Logical Level

**Decision**: `tenant_id` column in all tables, not separate databases per tenant

**Rationale**:
- Cost-effective (one DB handles many tenants)
- Easier maintenance
- Sufficient for SMB/mid-market
- Can migrate to physical isolation if needed

**Security**: All queries include `WHERE tenant_id = ?` filter automatically

### 5. JWT Stateless Authentication

**Decision**: JWT tokens, no sessions

**Rationale**:
- Scalable (no session store needed)
- Works across services (if we add a frontend separately)
- Stateless = easier horizontal scaling
- Standard industry practice

### 6. n8n Integration via Webhooks

**Decision**: n8n calls backend via HTTP, NOT Python execution

**Rationale**:
- Language-agnostic (Java backend works perfectly)
- Clear separation of concerns (n8n = automation, backend = business logic)
- Easier to debug (HTTP requests visible in logs)
- More secure (no direct code execution)

## 🔒 Security Considerations

### Implemented Security Features ✅

1. **Rate Limiting** ✅ - Bucket4j Token Bucket algorithm
   - Login: 5 attempts per 15 minutes
   - Register: 3 attempts per hour
   - API: 100 requests per minute
   - Prevents brute force and DDoS attacks

2. **CORS Configuration** ✅ - Environment-based whitelist
   - Development: localhost:3000, localhost:5173
   - Production: Configurable via `CORS_ALLOWED_ORIGINS`
   - Credentials enabled for HTTP-only cookies

3. **Security Headers** ✅ - 6 HTTP headers configured
   - `X-Content-Type-Options: nosniff`
   - `X-Frame-Options: DENY`
   - `X-XSS-Protection: 1; mode=block`
   - `Strict-Transport-Security` (HSTS)
   - `Content-Security-Policy`
   - `Referrer-Policy: strict-origin-when-cross-origin`

4. **Input Validation** ✅ - Basic validation implemented
   - Bean Validation (`@Valid`, `@NotBlank`, `@Email`)
   - Password complexity requirements

### Security Roadmap (Future)

**Sprint 2: Critical Security**
- Audit Logging (track user actions)
- Account Lockout (after failed login attempts)
- Refresh Tokens (better UX)

**Sprint 3: Advanced Security**
- Two-Factor Authentication (2FA)
- HTTP-Only Cookies (replace localStorage)
- Row Level Security (PostgreSQL RLS)

## 📚 Documentation Structure

```
QuickStack/
├── README.md          # Architecture overview & system design
├── CONTEXT.md         # This file - project background & decisions (YOU ARE HERE)
└── quickstack-core/
    └── README.md      # Backend implementation guide
```

**Read in this order:**
1. CONTEXT.md (understand the "why")
2. README.md (understand the "what")
3. quickstack-core/README.md (understand the "how")

## 🎯 Implementation Roadmap

### Phase 1: Foundation ✅ COMPLETE
- [x] Spring Boot + PostgreSQL setup
- [x] Authentication & JWT
- [x] User & Tenant management
- [x] Security (Rate limiting, CORS, Headers)
- [x] Database migrations (Flyway)
- [x] 82 tests with 100% critical path coverage
- [x] Production deployment (Render)

### Phase 2: Business Modules 🔄 IN PROGRESS
**Focus**: Build core business features

**Gastos Module** (Expense Tracking)
- [ ] Entity: `Gasto` (id, tenant_id, monto, categoria, descripcion, fecha)
- [ ] Repository & Service
- [ ] REST Controller (`/api/gastos`)
- [ ] Flyway migration (V3__create_gastos.sql)
- [ ] Tests

**Pedidos Module** (Order Management)
- [ ] Entities: `Pedido`, `PedidoItem`
- [ ] Repository & Service
- [ ] REST Controller (`/api/pedidos`)
- [ ] Flyway migration (V4__create_pedidos.sql)
- [ ] Tests

**Finanzas Module** (Financial Statements)
- [ ] Service to consume gastos + pedidos data
- [ ] Generate financial statements (ingresos, egresos, balance)
- [ ] REST Controller (`/api/finanzas/estado`)
- [ ] Event listeners (listen to GastoRegistrado, PedidoCompletado)
- [ ] Tests

**Webhooks Module** (n8n Integration)
- [ ] GastosWebhookController (`/webhooks/n8n/gastos`)
- [ ] PedidosWebhookController (`/webhooks/n8n/pedidos`)
- [ ] Webhook secret validation
- [ ] Tests

**Estimated Time**: 2-3 weeks

### Phase 3: Automation 🔴 PLANNED
**Focus**: n8n workflows and bots

- [ ] Setup n8n (Docker or cloud)
- [ ] Telegram bot workflow for expenses
- [ ] WhatsApp bot workflow for orders
- [ ] Scheduled financial report generation
- [ ] Email notifications

**Estimated Time**: 1-2 weeks

### Phase 4: Dashboard 🔴 PLANNED
**Focus**: Central React UI

- [ ] React + TypeScript + Vite setup
- [ ] Login/Register pages
- [ ] Dashboard home
- [ ] Gastos management page (list, create, edit)
- [ ] Pedidos tracking page
- [ ] Finanzas dashboard (charts, balance)
- [ ] API client with Axios

**Estimated Time**: 2-3 weeks

## 🚫 What NOT to Do

### Don't:
- ❌ Add microservices complexity (not needed)
- ❌ Create separate databases per module (defeats the purpose)
- ❌ Use Python for backend (already have Java foundation)
- ❌ Over-engineer (YAGNI - You Ain't Gonna Need It)
- ❌ Skip tests ("I'll add them later" = never)
- ❌ Ignore linter warnings
- ❌ Store secrets in code

### Do:
- ✅ Keep modules independent (loose coupling)
- ✅ Write tests alongside features (TDD)
- ✅ Use event bus for inter-module communication
- ✅ Document complex logic
- ✅ Follow Spring Boot best practices
- ✅ Run linters before committing
- ✅ Update docs when code changes

## 💬 Common Questions

### Q: Why not use FastAPI for backend?
**A**: We already have `quickstack-core` built in Java with auth, security, and multi-tenancy. Porting to Python wastes 2-3 weeks. Java integrates with n8n perfectly via HTTP/JSON.

### Q: Doesn't n8n work better with Python?
**A**: No. n8n integrates via HTTP requests (language-agnostic). Java backend responds with JSON just like Python would. There's no difference for n8n.

### Q: Why not microservices?
**A**: Solo developer can't maintain 4+ services. Our modules are tightly coupled (finanzas needs data from gastos + pedidos). Monolith with modules is simpler and faster.

### Q: Can I scale this later?
**A**: Yes. If needed, we can extract modules into separate services. But start simple—premature optimization is the root of all evil.

### Q: Why one database?
**A**: Simplicity. We can do SQL joins (e.g., `SELECT SUM(gastos), SUM(ingresos) FROM ...`). Lower cost. Easier migrations. Sufficient isolation with `tenant_id`.

### Q: How do modules communicate?
**A**: Via Spring's event bus (`ApplicationEventPublisher`). Example: Gastos publishes `GastoRegistradoEvent`, Finanzas listens and updates balance.

## 🔄 How to Resume Development

If you're starting fresh (after `/init` or coming back later):

1. **Read this file** (CONTEXT.md) - Understand the "why"
2. **Read README.md** - Understand the architecture
3. **Check git log** - See what's been done
4. **Check roadmap** - Pick next task from Phase 2
5. **Start implementing** - One module at a time

## 📝 Notes for AI Assistant (Claude)

When I use `/init`, you will have access to:
- ✅ All files in this repository
- ✅ Git commit history
- ✅ This context document

But you will NOT remember:
- ❌ Previous conversations
- ❌ Decisions made verbally
- ❌ Why we made certain choices (unless documented here)

**That's why this file exists** - to preserve context across sessions.

## 🎓 Learning Goals

By the end of this project, we aim to understand:
- ✅ Modular monolith architecture patterns
- ✅ Multi-tenant architecture with logical isolation
- ✅ Event-driven communication between modules
- ✅ JWT authentication flow
- ✅ n8n workflow automation
- ✅ Database migration strategies (Flyway)
- ✅ Docker containerization
- ✅ CI/CD pipelines
- ✅ Security best practices
- ✅ System design for solo developers

## 💡 Ideas for Future Features

> Section for documenting ideas we won't implement now (YAGNI), but want to explore later.

### 1. Advanced RBAC (Role-Based Access Control)
**Date**: January 12, 2026
**Status**: 📝 Documented, not implemented yet

**Problem**: Currently only ADMIN/USER roles, no granular permissions

**Solution**: Add fine-grained permissions
- ADMIN: full access
- MANAGER: read/write users, no delete
- USER: read-only

**When**: After MVP is complete and we have real users

---

### 2. Two-Factor Authentication (2FA)
**Date**: January 12, 2026
**Status**: 📝 Documented, not implemented yet

**Problem**: Maximum security for sensitive accounts

**Solution**: TOTP with Google Authenticator

**When**: After we have audit logging and account lockout

---

### 3. API Gateway (Kong/AWS API Gateway)
**Date**: January 12, 2026
**Status**: 📝 Documented, not needed yet

**Problem**: Centralized routing, rate limiting, analytics

**Solution**: Add API Gateway in front of backend

**When**: If we reach 10,000+ concurrent users or need advanced routing

---

## 📅 Timeline

- **Started**: January 8, 2026
- **Phase 1 Complete**: January 10, 2026 (Foundation deployed to production)
- **Phase 2 Start**: January 12, 2026 (Business modules)
- **MVP Target**: February 2026 (Backend + n8n + Dashboard)

---

**Last Updated**: January 12, 2026

**Current Phase**: Phase 2 - Business Modules (Gastos, Pedidos, Finanzas)

**Next Tasks**:
1. Create Gastos module (expense tracking)
2. Create Pedidos module (order management)
3. Create Finanzas module (financial statements)
4. Add webhook endpoints for n8n integration

**Technical Stack**:
- Backend: Java 17 + Spring Boot 3.5
- Database: PostgreSQL (Neon)
- Frontend: React 18 + TypeScript + Vite (planned)
- Automation: n8n (planned)
- Deployment: Render (backend), Vercel (frontend planned)

---

**Remember**: This is a learning journey optimized for a solo developer. Simplicity and maintainability over premature optimization. 🚀

**Philosophy**: Build modular, deploy monolithic. Start simple, scale when proven necessary.
