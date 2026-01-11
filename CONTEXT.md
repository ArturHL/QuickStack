# QuickStack Labs - Project Context

## 🎯 Project Goal

Build a **reusable multi-tenant SaaS architecture** that serves as a foundation for creating multiple SaaS products quickly and securely.

## 📖 Background & Motivation

### Why This Project Exists

1. **Learning Through Building** - Understanding SaaS architecture by designing a complete system
2. **Portfolio Piece** - Demonstrating system design and architectural thinking
3. **Reusable Foundation** - Create once, use for multiple products
4. **Professional Development** - Learning enterprise-grade patterns and practices

### Philosophy

> "Architecture first, code second. Understand before implementing."

We document and design the entire system before writing code to ensure:
- Every decision is intentional and justified
- The architecture is sound and scalable
- We can explain every component confidently
- No "mystery code" that we don't understand

## 🏗️ Current State (January 2026)

### What We Have

**Phase 1: Planning & Architecture ✅ COMPLETE**
- ✅ Complete architectural design (README.md)
- ✅ Detailed implementation blueprint (STRUCTURE.md)
- ✅ Development environment setup (DEV_SETUP.md)
- ✅ Project structure (6 empty component directories)
- ✅ Professional tooling (linters, formatters, VS Code config)

**Phase 2: Core Backend Implementation ✅ MVP DEPLOYED**
- ✅ Spring Boot 3.5 + Java 17 configured
- ✅ JWT Authentication (login, register)
- ✅ Multi-tenant User Management (CRUD)
- ✅ PostgreSQL + Flyway migrations
- ✅ 82 tests (100% critical path coverage)
- ✅ Deployed to Render (production ready)
- ✅ Rate Limiting (Bucket4j) - Prevents brute force
- ✅ CORS Configuration - Securely configured
- ✅ Security Headers - 6 HTTP headers implemented

**Phase 3: Security Enhancement 🎯 IN PROGRESS (Sprint 1)**
- ✅ Rate Limiting (Bucket4j Token Bucket)
- ✅ CORS Configuration (environment-based whitelist)
- ✅ Security Headers (X-Frame-Options, CSP, HSTS, etc.)
- ⏳ Input Validation Enhancement
- ⏳ Error Handling Standardization
- ⏳ Audit Logging
- ⏳ Monitoring & Metrics

**Phase 4: Product Template & SDK 🔴 NOT STARTED**
- ⏳ Python SDK - Empty directory ready
- ⏳ React UI Library - Empty directory ready
- ⏳ Product Template - Empty directory ready

### Why Are Directories Empty?

**Deliberate Decision**: We want to build incrementally, understanding every line of code.

> "Better to have 0 lines of code we fully understand, than 5000 lines we cannot explain."

This approach allows us to:
1. Learn each component thoroughly
2. Make conscious decisions about implementations
3. Avoid cargo-cult programming
4. Build confidence through understanding

## 🛠️ Technology Stack Decisions

### Core Backend - Java + Spring Boot
**Why:**
- ✅ Enterprise-grade stability
- ✅ Mature authentication/security ecosystem
- ✅ Strong typing and compile-time safety
- ✅ Excellent for critical auth services

### Product Backends - Python + FastAPI
**Why:**
- ✅ Rapid prototyping and development speed
- ✅ Excellent for AI integration (future)
- ✅ Type hints with Pydantic
- ✅ Modern async/await support
- ✅ Perfect for product-specific logic

### Frontend - React + TypeScript + Vite
**Why:**
- ✅ Most popular framework (hiring advantage)
- ✅ Type safety with TypeScript
- ✅ Fast development with Vite
- ✅ Rich ecosystem of components

### Database - PostgreSQL (Neon)
**Why:**
- ✅ Best open-source relational database
- ✅ Excellent for multi-tenant (Row Level Security)
- ✅ JSON support for flexibility
- ✅ Neon provides managed hosting

### Infrastructure - Render + Vercel
**Why:**
- ✅ Simple deployment (no DevOps overhead)
- ✅ Free tier for learning/prototyping
- ✅ Auto-scaling built-in
- ✅ Easy to understand and debug

### Security Libraries - Bucket4j
**Why:**
- ✅ Industry-standard Token Bucket algorithm
- ✅ Thread-safe in-memory rate limiting
- ✅ Flexible configuration per endpoint
- ✅ Can scale to Redis/Hazelcast for distributed systems
- ✅ Modern API (no deprecated methods)

## 🎓 Key Architectural Decisions

### 1. Multi-Repo Strategy (In Production)
**Decision**: Each component is independent repo in production
**Reason**:
- Independent deployment and versioning
- Clear ownership boundaries
- Simplified CI/CD per component

**Current Setup**: Monorepo for documentation (will split when implementing)

### 2. Shared Core Pattern
**Decision**: All products authenticate through a central Core Backend
**Reason**:
- Don't repeat auth logic in every product
- Centralized user/tenant management
- Consistent security model
- Single source of truth

### 3. Database Per Product
**Decision**: Each product has its own database
**Reason**:
- Data isolation (security)
- Independent scaling
- Schema flexibility per product
- Easier backup/restore

### 4. Multi-Tenant at Logical Level
**Decision**: `tenant_id` column in all tables, not separate databases per tenant
**Reason**:
- Cost-effective (one DB handles many tenants)
- Easier maintenance
- Sufficient for SMB/mid-market
- Can migrate to physical isolation if needed

### 5. JWT Stateless Authentication
**Decision**: JWT tokens, no sessions
**Reason**:
- Scalable (no session store needed)
- Works across services
- Stateless = easier horizontal scaling
- Standard industry practice

## 🔒 Security Considerations

### Implemented Security Features ✅

1. **Rate Limiting** ✅ - Bucket4j Token Bucket algorithm
   - Login: 5 attempts per 15 minutes
   - Register: 3 attempts per hour
   - API: 100 requests per minute
   - Prevents brute force and DDoS attacks
   - Implementation: `RateLimitService` + `RateLimitInterceptor`

2. **CORS Configuration** ✅ - Environment-based whitelist
   - Development: localhost:3000, localhost:5173
   - Production: Configurable via `CORS_ALLOWED_ORIGINS`
   - Credentials enabled for HTTP-only cookies
   - No wildcard (*) origins in production
   - Implementation: `CorsConfig` + Spring Security integration

3. **Security Headers** ✅ - 6 HTTP headers configured
   - `X-Content-Type-Options: nosniff` - Prevents MIME sniffing
   - `X-Frame-Options: DENY` - Prevents clickjacking
   - `X-XSS-Protection: 1; mode=block` - XSS protection (legacy)
   - `Strict-Transport-Security` - Forces HTTPS (HSTS)
   - `Content-Security-Policy` - Restricts resource loading
   - `Referrer-Policy: strict-origin-when-cross-origin` - Controls referer info
   - Implementation: Spring Security `.headers()` configuration

4. **Input Validation** ✅ - Basic validation implemented
   - Bean Validation (`@Valid`, `@NotBlank`, `@Email`)
   - Password complexity requirements
   - Email format validation
   - Implementation: `@Valid` annotations + custom validators

### Security Gaps (To Address in Future Sprints)

1. **Token Storage** - Plan to use HTTP-Only cookies (not localStorage)
2. **Audit Logs** - Essential for compliance and breach detection
3. **Row Level Security** - PostgreSQL RLS for defense-in-depth
4. **Enhanced Input Validation** - More comprehensive validation rules
5. **Secret Management** - Use proper secret managers (not .env in production)
6. **Dependency Scanning** - Automated vulnerability checks in CI/CD
7. **Account Lockout** - Temporary lockout after failed attempts
8. **Refresh Tokens** - Long-lived tokens for better UX

> Note: Security is addressed progressively. Core protections (Rate Limiting, CORS, Security Headers) are now in place.

## 📚 Documentation Structure

```
QuickStack/
├── README.md          # Architecture overview & system design
├── CONTEXT.md         # This file - project background & decisions (YOU ARE HERE)
├── STRUCTURE.md       # Complete implementation blueprint
├── PROJECT_GUIDE.md   # How to start implementing each component
├── DEV_SETUP.md       # Development environment setup guide
```

**Read in this order:**
1. CONTEXT.md (understand the "why")
2. README.md (understand the "what")
3. STRUCTURE.md (understand the "how")
4. PROJECT_GUIDE.md (start implementing)

## 🎯 Implementation Roadmap

### Phase 1: Core Foundation (Critical Path)
**Status**: 🔴 Not Started

1. **Core Backend** (Java + Spring Boot)
   - Authentication endpoints
   - JWT token generation/validation
   - User CRUD
   - Tenant management
   - Database migrations (Flyway)
   - **Estimated**: 2-3 weeks
   - **Start**: `cd quickstack-core/`

2. **Python SDK** (Python Package)
   - HTTP client for Core APIs
   - Token validation helpers
   - Data models (User, Tenant)
   - Error handling
   - **Estimated**: 3-5 days
   - **Start**: `cd quickstack-python-sdk/`

3. **React UI Library** (React + TypeScript)
   - Core components (Button, Modal, Form)
   - Hooks (useAuth, useTenant)
   - Theme system
   - **Estimated**: 1-2 weeks
   - **Start**: `cd quickstack-react-ui/`

### Phase 2: Product Template
**Status**: 🔴 Not Started

4. **Product Template** (FastAPI + React)
   - Backend with multi-tenant models
   - Frontend with authentication
   - Docker Compose setup
   - CI/CD pipelines
   - **Estimated**: 1-2 weeks
   - **Start**: `cd quickstack-product-template/`

### Phase 3: Example Products
**Status**: 🔴 Not Started

5. **CRM Example**
6. **Analytics Example**

## 🚫 What NOT to Do

### Don't:
- ❌ Copy-paste code you don't understand
- ❌ Skip documentation/comments
- ❌ Commit directly to main (use feature branches)
- ❌ Implement features not in the plan (avoid scope creep)
- ❌ Skip tests ("I'll add them later" = never)
- ❌ Over-engineer (YAGNI - You Ain't Gonna Need It)
- ❌ Ignore linter warnings
- ❌ Store secrets in code

### Do:
- ✅ Read existing code before modifying
- ✅ Write tests alongside features
- ✅ Commit small, focused changes
- ✅ Ask questions when unclear
- ✅ Document complex logic
- ✅ Follow the established patterns
- ✅ Run linters before committing
- ✅ Update docs when code changes

## 💬 Common Questions

### Q: Why not use a framework like Next.js for everything?
**A**: Separation of concerns. Backend handles business logic (FastAPI), frontend handles UI (React). Next.js blurs this line.

### Q: Why Java for Core? Python is easier.
**A**: Core handles critical auth/security. Java's strong typing and mature ecosystem provide safety. Python is fine for product logic.

### Q: Why not microservices?
**A**: We're building a small-medium system. Microservices add complexity. Our "Core + Products" pattern gives benefits without the overhead.

### Q: Why not serverless?
**A**: Learning traditional deployment first. Serverless adds abstraction that hides important concepts. We can migrate later.

### Q: Do we need Docker for frontend?
**A**: No. Frontend runs with `npm run dev` during development. Docker is for backend consistency only.

### Q: When do we deploy?
**A**: After Core Backend is working. No point deploying empty directories.

## 🔄 How to Resume Development

If you're starting fresh (after `/init` or coming back later):

1. **Read this file** (CONTEXT.md) - Understand the "why"
2. **Read README.md** - Understand the architecture
3. **Check git log** - See what's been done
4. **Run setup** - `./setup-dev-tools.sh`
5. **Pick next task** - Follow roadmap in this file
6. **Start implementing** - One component at a time

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
- ✅ Multi-tenant architecture patterns
- ✅ Microservices communication (HTTP APIs)
- ✅ JWT authentication flow
- ✅ Database migration strategies
- ✅ Docker containerization
- ✅ CI/CD pipelines
- ✅ Security best practices
- ✅ System design at scale

## 💡 Ideas de Features Futuras

> Sección para documentar ideas que NO implementaremos ahora (YAGNI), pero queremos explorar en el futuro.

### 1. Sistema de Entitlements (Feature Flags por Tenant)

**Fecha**: Enero 8, 2026
**Estado**: 📝 Documentado, no implementar aún

**Problema que resuelve**:
Habilitar/deshabilitar productos y features por tenant. Ej: Tenant A tiene CRM + Analytics, Tenant B solo CRM.

**Modelo de datos propuesto**:
```
Product (code, name, active)
Feature (code, name, product_id, active)
TenantProduct (tenant_id, product_id, enabled, config)
TenantFeature (tenant_id, feature_id, enabled, config)
```

**Fases de implementación**:
1. **Fase 1 (Manual)**: Admin habilita/deshabilita manualmente por tenant
2. **Fase 2 (Por Plan)**: Agregar tabla `Plan` y `PlanEntitlement` para automatizar según suscripción

**Consumo**: Exponer via endpoint `GET /api/tenants/{id}/entitlements` o incluir en JWT.

**Por qué NO ahora**: Aún no tenemos User/Tenant/Auth básico funcionando. Agregar complejidad prematura.

**Cuándo implementar**: Después de tener MVP funcionando con al menos 2 productos.

---

*Agregar nuevas ideas siguiendo este formato*

---

### 2. Features de Seguridad Pendientes (Post-MVP)

**Fecha**: Enero 10, 2026
**Estado**: 📝 Documentado para implementar incremental
**Contexto**: Core Backend desplegado en producción con autenticación JWT básica funcionando

#### Sprint 1: Seguridad Crítica ✅ COMPLETADO (70% - 3 de 4 features)

**1. Rate Limiting** ✅ IMPLEMENTADO
- **Fecha**: Enero 10, 2026
- **Estado**: Deployed to production
- **Solución**: Bucket4j 8.10.1 con Token Bucket algorithm
  - `/api/auth/login`: Max 5 intentos/15 min
  - `/api/auth/register`: Max 3 intentos/hora
  - `/api/**`: Max 100 requests/min
- **Implementación**:
  - `RateLimitService`: Gestión de buckets por IP
  - `RateLimitInterceptor`: Interceptor HTTP con validación
  - Soporte X-Forwarded-For para proxies
  - 20 tests (100% cobertura)
- **Tiempo real**: 3 horas (incluyendo tests y documentación)

**2. CORS Configurado Correctamente** ✅ IMPLEMENTADO
- **Fecha**: Enero 10, 2026
- **Estado**: Deployed to production
- **Solución**: Configuración environment-based
  - Desarrollo: `localhost:3000`, `localhost:5173`
  - Producción: Configurable vía `CORS_ALLOWED_ORIGINS`
  - Credentials habilitados para cookies HTTP-only
  - Métodos: GET, POST, PUT, DELETE, PATCH, OPTIONS
  - Max-Age: 3600 segundos (cacheo preflight)
- **Implementación**:
  - `CorsConfig`: Bean con configuración CORS
  - Integrado con Spring Security
  - 8 tests de CORS (100% cobertura)
- **Tiempo real**: 1.5 horas

**3. Security Headers** ✅ IMPLEMENTADO
- **Fecha**: Enero 10, 2026
- **Estado**: Deployed to production
- **Headers configurados**:
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `X-XSS-Protection: 1; mode=block`
  - `Strict-Transport-Security: max-age=31536000; includeSubDomains`
  - `Content-Security-Policy: default-src 'self'; script-src 'self'; ...`
  - `Referrer-Policy: strict-origin-when-cross-origin`
- **Implementación**:
  - Configuración directa en `SecurityConfig.headers()`
  - 9 tests de security headers (100% cobertura)
- **Tiempo real**: 1 hora

**4. Audit Logs Básicos** ⏳ PENDIENTE
- **Problema**: No hay visibilidad de quién hizo qué
- **Solución**: Tabla `audit_logs` con eventos críticos
- **Eventos**: Login exitoso/fallido, registro, cambios de contraseña
- **Complejidad**: Baja (1-2 horas)
- **Prioridad**: Siguiente en la lista

**5. Account Lockout** ⏳ PENDIENTE
- **Problema**: Cuentas comprometidas por intentos masivos
- **Solución**: Bloqueo temporal después de N intentos fallidos
  - 5 intentos → 15 min lockout
  - 10 intentos → 24 horas lockout
- **Modelo**: Columnas `failed_login_attempts`, `locked_until` en `users`
- **Complejidad**: Baja (1 hora)

#### Sprint 2: UX Esencial (1 semana)

**5. Refresh Tokens** ⭐
- **Problema**: Access tokens expiran en 1 hora, usuario debe re-autenticarse
- **Solución**: Access token corto (15 min) + Refresh token largo (7 días)
- **Endpoints**: `POST /api/auth/refresh`
- **Modelo**: Tabla `refresh_tokens`
- **Complejidad**: Media (2-3 horas)

**6. Password Reset** ⭐⭐⭐
- **Problema**: Usuarios olvidan contraseñas
- **Solución**: Flujo forgot password → email → reset con token
- **Endpoints**: `POST /api/auth/forgot-password`, `POST /api/auth/reset-password`
- **Modelo**: Tabla `password_reset_tokens` (expiran en 1 hora)
- **Complejidad**: Media (2-3 horas + integración email)

**7. Email Verification** ⭐⭐
- **Problema**: Validar emails reales, prevenir spam
- **Solución**: Email con link → click → cuenta activa
- **Modelo**: Columna `email_verified` en `users`
- **Endpoints**: `GET /api/auth/verify-email?token=...`
- **Complejidad**: Media (3-4 horas con SendGrid/Mailgun)

**8. User Management CRUD Completo**
- **Endpoints faltantes**:
  - `POST /api/users` - Admin crea usuarios USER
  - `PATCH /api/users/{id}` - Actualizar nombre, email, role
  - `DELETE /api/users/{id}` - Soft delete (active=false)
  - `POST /api/users/{id}/activate` - Reactivar
- **Complejidad**: Baja (2-3 horas con tests)

#### Sprint 3: Features Avanzadas (1-2 semanas)

**9. RBAC Mejorado (Role-Based Access Control)** ⭐⭐
- **Problema**: Solo ADMIN/USER, sin granularidad
- **Solución**: Más roles y permisos específicos
  - ADMIN: full access
  - MANAGER: read/write users, no delete
  - USER: solo lectura
- **Implementación**: `@PreAuthorize` annotations
- **Complejidad**: Media (3-4 horas)

**10. Two-Factor Authentication (2FA)** ⭐⭐⭐
- **Problema**: Máxima seguridad requerida
- **Solución**: TOTP con Google Authenticator
- **Flujo**: Login → QR code → código 6 dígitos → validación
- **Librería**: `java-totp`
- **Complejidad**: Alta (4-6 horas)

**11. HTTP-Only Cookies para JWT** ⭐⭐⭐
- **Problema**: localStorage vulnerable a XSS
- **Solución**: Cookies HTTP-only + Secure + SameSite
- **Impacto**: Requiere cambios en frontend
- **Complejidad**: Media (2-3 horas backend + frontend)

**12. Tenant Management**
- **Endpoints**:
  - `GET /api/tenants/me` - Info del tenant autenticado
  - `PATCH /api/tenants/{id}` - Actualizar nombre, settings
  - `GET /api/tenants/stats` - Estadísticas (# usuarios, fecha creación)
- **Complejidad**: Baja (1-2 horas)

#### Monitoring & Observability

**13. Metrics con Micrometer + Prometheus**
- **Métricas**: Requests/endpoint, latencia, errores 4xx/5xx, conexiones DB
- **Stack**: Micrometer → Prometheus → Grafana
- **Complejidad**: Media (2-3 horas setup)

**14. Structured Logging (JSON)**
- **Formato**: JSON logs con trace_id, user_id, tenant_id
- **Librería**: Logback JSON encoder
- **Complejidad**: Baja (1 hora)

**15. Health Checks Detallados**
- **Actual**: Solo UP/DOWN
- **Mejorado**: Checks por componente (DB, disk, latencia Neon)
- **Complejidad**: Baja (30 min)

#### Orden de Implementación Propuesto

**Prioridad 1 (Seguridad Crítica):**
1. Rate Limiting
2. Account Lockout
3. CORS
4. Audit Logs

**Prioridad 2 (UX):**
5. Refresh Tokens
6. Password Reset
7. Email Verification
8. User Management CRUD

**Prioridad 3 (Avanzado):**
9. RBAC Mejorado
10. 2FA
11. HTTP-Only Cookies
12. Metrics + Monitoring

**Por qué este orden**: Seguridad primero (prevenir ataques reales), luego UX (usuarios pueden usar el sistema), finalmente features avanzadas.

**Cuándo implementar**: Después de tener al menos 1 producto funcionando con el Core actual.

---

## 📅 Timeline

- **Started**: January 8, 2026
- **Phase 1 Complete**: January 8, 2026 (Architecture & Setup)
- **Phase 2 Start**: January 10, 2026 (Core Backend implementation)
- **Phase 2 Complete**: January 10, 2026 (Core Backend MVP deployed to production)
- **Sprint 1 Security**: January 10, 2026 (70% complete - Rate Limiting, CORS, Security Headers)
- **MVP Target**: ✅ ACHIEVED - Core Backend en producción con JWT auth + seguridad básica
- **Next**: Audit Logging, Account Lockout, luego Sprint 2 (UX features)

---

**Last Updated**: January 10, 2026 (22:45 CST)

**Current Phase**: Sprint 1 - Security Critical ✅ 70% COMPLETE (3/4 features)

**Production Status**:
- ✅ Core Backend deployed (Render)
- ✅ Rate Limiting active (Bucket4j)
- ✅ CORS configured (environment-based)
- ✅ Security Headers active (6 headers)
- ✅ 82 tests passing (100% critical path)

**Next Tasks**:
1. Audit Logging (Sprint 1 - remaining 30%)
2. Account Lockout (Sprint 1 - bonus)
3. Refresh Tokens (Sprint 2 - UX)
4. Password Reset (Sprint 2 - UX)

**Technical Achievements**:
- Test-Driven Development (TDD) workflow established
- Modern Bucket4j API (no deprecations)
- Comprehensive security headers (OWASP best practices)
- Environment-based CORS (production-ready)
- Thread-safe rate limiting (concurrent requests)

---

**Remember**: This is a learning journey. Take time to understand, not just implement. Quality over speed. 🚀

**Lessons Learned**:
1. TDD catches bugs early (write tests first, then implementation)
2. Spring Security headers require careful configuration (HSTS only on HTTPS)
3. Bucket4j Token Bucket is elegant and powerful
4. CORS preflight caching saves round trips (3600s max-age)
5. Security is incremental - focus on critical features first
