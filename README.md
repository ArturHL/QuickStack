# QuickStack Labs - Workflow Automation Platform

> A modular backend system for building automated business workflows with n8n integration.

## 🎯 Project Vision

**QuickStack Labs** is a software company building an ecosystem of interconnected business automation tools. Our platform enables automated workflows for financial management, order processing, expense tracking, and more—all orchestrated through a central dashboard and n8n automation.

### The Problem We Solve

Building business automation tools from scratch requires:
- Robust authentication and authorization
- Multi-tenant architecture for data isolation
- Secure API endpoints for integrations
- Workflow orchestration
- Manual data entry and reconciliation between systems

**QuickStack solves this by providing a modular backend architecture** where features can be easily added, connected, and automated—allowing us to focus on business logic instead of boilerplate infrastructure.

---

## 🏗️ Architecture Overview

### Modular Monolith Architecture

```
                         ┌──────────────────────┐
                         │   Dashboard Central  │
                         │   (React/TypeScript) │
                         └──────────┬───────────┘
                                    │
                                    ▼
                    ┌────────────────────────────────┐
                    │      n8n Orchestrator          │
                    │   (Bots, Webhooks, Triggers)   │
                    └─────────────┬──────────────────┘
                                  │
                                  ▼
                    ┌────────────────────────────────┐
                    │   QuickStack Backend           │
                    │   (Spring Boot Monolith)       │
                    │                                │
                    │   Modules:                     │
                    │   ├── auth/     (JWT, Login)   │
                    │   ├── user/     (User CRUD)    │
                    │   ├── tenant/   (Multi-tenant) │
                    │   ├── gastos/   (Expenses)     │
                    │   ├── pedidos/  (Orders)       │
                    │   └── finanzas/ (Financials)   │
                    └─────────────┬──────────────────┘
                                  │
                    ┌─────────────▼──────────────────┐
                    │      PostgreSQL Database       │
                    │                                │
                    │   Tables:                      │
                    │   ├── users, tenants           │
                    │   ├── gastos, categorias       │
                    │   ├── pedidos, order_items     │
                    │   └── estados_financieros      │
                    └────────────────────────────────┘
```

### Key Architectural Principles

1. **Modular Monolith**: Single codebase, organized by business modules
2. **Single Database**: All modules share one PostgreSQL database with logical separation
3. **Event-Driven Communication**: Modules communicate via internal event bus
4. **API-First**: RESTful APIs for all features
5. **Multi-Tenant**: Logical data isolation using `tenant_id` in all tables
6. **n8n Integration**: Workflow automation via webhooks and HTTP requests

---

## 🛠️ Technology Stack

### Backend
- **Java 17+** with **Spring Boot 3.x**
  - Spring Security for authentication
  - Spring Data JPA for database access
  - Flyway for database migrations
  - JWT for stateless authentication
  - Bucket4j for rate limiting

### Frontend
- **React 18+** with TypeScript
- **Vite** for fast development
- **React Router** for navigation
- **Axios** for API communication

### Automation
- **n8n** (self-hosted or cloud)
  - Bot workflows (Telegram, WhatsApp, etc.)
  - Scheduled tasks
  - External integrations
  - Webhook receivers

### Infrastructure
- **Database**: PostgreSQL (Neon managed)
- **Backend Hosting**: Render (Docker containers)
- **Frontend Hosting**: Vercel
- **CI/CD**: GitHub Actions
- **Containerization**: Docker

---

## 📦 Repository Structure

```
QuickStack/
│
├── README.md                       # 📚 Architecture documentation
├── CONTEXT.md                      # 📝 Project context and decisions
├── .gitignore                      # Git ignore patterns
│
├── quickstack-core/                # ⚙️ Backend Monolith (Spring Boot)
│   ├── src/main/java/com/quickstack/core/
│   │   ├── QuickStackCoreApplication.java
│   │   │
│   │   ├── auth/                   # Authentication module
│   │   │   ├── AuthController.java
│   │   │   ├── AuthService.java
│   │   │   └── dto/
│   │   │
│   │   ├── user/                   # User management module
│   │   │   ├── User.java
│   │   │   ├── UserRepository.java
│   │   │   ├── UserService.java
│   │   │   └── UserController.java
│   │   │
│   │   ├── tenant/                 # Multi-tenancy module
│   │   │   ├── Tenant.java
│   │   │   └── TenantRepository.java
│   │   │
│   │   ├── gastos/                 # 💰 Expense tracking module
│   │   │   ├── Gasto.java
│   │   │   ├── GastoRepository.java
│   │   │   ├── GastoService.java
│   │   │   └── GastoController.java
│   │   │
│   │   ├── pedidos/                # 📦 Order management module
│   │   │   ├── Pedido.java
│   │   │   ├── PedidoRepository.java
│   │   │   ├── PedidoService.java
│   │   │   └── PedidoController.java
│   │   │
│   │   ├── finanzas/               # 📊 Financial statements module
│   │   │   ├── EstadoFinanciero.java
│   │   │   ├── FinanzasService.java
│   │   │   └── FinanzasController.java
│   │   │
│   │   ├── webhooks/               # 🔗 n8n webhook endpoints
│   │   │   ├── GastosWebhookController.java
│   │   │   └── PedidosWebhookController.java
│   │   │
│   │   ├── security/               # Security configuration
│   │   │   ├── JwtService.java
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   ├── SecurityConfig.java
│   │   │   └── ratelimit/
│   │   │
│   │   ├── config/                 # Application configuration
│   │   │   ├── CorsConfig.java
│   │   │   └── WebConfig.java
│   │   │
│   │   └── common/                 # Shared utilities
│   │       └── GlobalExceptionHandler.java
│   │
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/           # Flyway migrations
│   │
│   ├── src/test/                   # Test suite
│   ├── build.gradle
│   ├── Dockerfile
│   └── README.md
│
├── dashboard/                      # ⚛️ Central Dashboard (React)
│   ├── src/
│   │   ├── pages/
│   │   │   ├── Dashboard.tsx
│   │   │   ├── GastosPage.tsx
│   │   │   ├── PedidosPage.tsx
│   │   │   └── FinanzasPage.tsx
│   │   ├── components/
│   │   ├── hooks/
│   │   │   └── useAuth.ts
│   │   └── services/
│   │       └── api.ts
│   ├── package.json
│   └── vite.config.ts
│
├── n8n/                            # 🤖 Automation Workflows
│   ├── workflows/
│   │   ├── bot-gastos.json         # Telegram bot for expenses
│   │   ├── bot-pedidos.json        # WhatsApp bot for orders
│   │   └── sync-financiero.json    # Scheduled financial sync
│   └── README.md
│
└── docker-compose.yml              # Local development stack
```

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Gradle 8+
- Node.js 18+
- Docker & Docker Compose
- PostgreSQL (or use Docker)
- n8n (optional for automation)

### Local Development

```bash
# Clone repository
git clone https://github.com/YOUR_USERNAME/QuickStack
cd QuickStack

# Start backend
cd quickstack-core
./gradlew bootRun

# In another terminal, start frontend
cd dashboard
npm install
npm run dev

# (Optional) Start n8n with Docker
docker run -it --rm \
  --name n8n \
  -p 5678:5678 \
  -v ~/.n8n:/home/node/.n8n \
  n8nio/n8n
```

**Access:**
- Backend API: `http://localhost:8080`
- API Docs: `http://localhost:8080/swagger-ui.html`
- Frontend: `http://localhost:5173`
- n8n: `http://localhost:5678`

---

## 🔐 Security & Multi-Tenancy

### Authentication Flow

1. User logs in via `/api/auth/login` → receives JWT
2. JWT contains: `user_id`, `tenant_id`, `roles`
3. Every API request includes JWT in Authorization header
4. Backend validates JWT and extracts `tenant_id`
5. All queries automatically filter by `tenant_id`

### Multi-Tenant Data Isolation

Every table includes a `tenant_id` column for logical data isolation:

```sql
CREATE TABLE gastos (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    monto DECIMAL(10, 2) NOT NULL,
    categoria VARCHAR(100),
    descripcion TEXT,
    fecha_registro TIMESTAMP DEFAULT NOW(),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

-- All queries automatically filter by tenant
SELECT * FROM gastos WHERE tenant_id = ?;
```

**No tenant can access another tenant's data—guaranteed by application logic.**

---

## 🧩 Module Deep Dive

### Core Modules (Infrastructure)

#### 1. Auth Module (`auth/`)
**Purpose**: User authentication and JWT management

**APIs**:
- `POST /api/auth/register` - Create new account
- `POST /api/auth/login` - Authenticate user
- `POST /api/auth/logout` - Invalidate token

#### 2. User Module (`user/`)
**Purpose**: User CRUD and management

**APIs**:
- `GET /api/users` - List users in tenant
- `POST /api/users` - Create user
- `PATCH /api/users/{id}` - Update user
- `DELETE /api/users/{id}` - Soft delete user

#### 3. Tenant Module (`tenant/`)
**Purpose**: Organization/tenant management

**Model**: Tenant (id, name, slug, created_at)

---

### Business Modules (Features)

#### 4. Gastos Module (`gastos/`)
**Purpose**: Expense tracking and categorization

**APIs**:
- `POST /api/gastos` - Register expense
- `GET /api/gastos` - List expenses (filtered by tenant)
- `GET /api/gastos/stats` - Expense statistics by category

**Models**:
- `Gasto` (id, tenant_id, monto, categoria, descripcion, fecha)
- `Categoria` (id, tenant_id, nombre)

**n8n Integration**: Webhook at `/webhooks/n8n/gastos` for bot registration

#### 5. Pedidos Module (`pedidos/`)
**Purpose**: Order management and tracking

**APIs**:
- `POST /api/pedidos` - Create order
- `GET /api/pedidos` - List orders
- `PATCH /api/pedidos/{id}/status` - Update order status

**Models**:
- `Pedido` (id, tenant_id, cliente, total, estado, fecha)
- `PedidoItem` (id, pedido_id, producto, cantidad, precio)

**n8n Integration**: Webhook at `/webhooks/n8n/pedidos`

#### 6. Finanzas Module (`finanzas/`)
**Purpose**: Financial statements and reporting

**APIs**:
- `GET /api/finanzas/estado` - Generate financial statement
- `GET /api/finanzas/balance` - Current balance

**Features**:
- Consumes data from `gastos` and `pedidos` modules
- Generates PDF/Excel reports
- Calculates: total_gastos, total_ingresos, balance

---

## 🤖 n8n Integration

### How n8n Connects

```
User via Telegram Bot              Dashboard User
        │                                 │
        ▼                                 ▼
┌───────────────┐              ┌──────────────┐
│  n8n Workflow │              │   Frontend   │
│  (Bot Gastos) │              │   Dashboard  │
└───────┬───────┘              └──────┬───────┘
        │                             │
        │ POST /webhooks/n8n/gastos   │ POST /api/gastos
        │                             │
        └──────────────┬──────────────┘
                       │
                       ▼
          ┌────────────────────────┐
          │  QuickStack Backend    │
          │  GastosController      │
          └────────────────────────┘
```

### Example: Telegram Expense Bot

**n8n Workflow**:
1. User sends: "Gasto: 150 comida"
2. n8n extracts: `{monto: 150, categoria: "comida"}`
3. n8n calls: `POST /webhooks/n8n/gastos`
4. Backend validates and saves expense
5. Backend responds: `{success: true, id: 123}`
6. n8n replies to user: "Gasto registrado: $150 en comida"

---

## 📊 Database Migration Strategy

### Flyway Migrations

Migrations are versioned SQL files in `src/main/resources/db/migration/`:

```
db/migration/
├── V1__create_tenants.sql
├── V2__create_users.sql
├── V3__create_gastos.sql
├── V4__create_pedidos.sql
└── V5__create_estados_financieros.sql
```

**Run migrations**:
```bash
./gradlew flywayMigrate
```

---

## 🔄 Inter-Module Communication

### Event Bus Pattern

Modules communicate via internal event bus:

```java
// Gastos module publishes event
@Service
public class GastoService {
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public Gasto registrar(GastoRequest request, Tenant tenant) {
        Gasto gasto = gastoRepository.save(...);

        // Publish event
        eventPublisher.publishEvent(
            new GastoRegistradoEvent(gasto)
        );

        return gasto;
    }
}

// Finanzas module listens
@Service
public class FinanzasService {
    @EventListener
    public void onGastoRegistrado(GastoRegistradoEvent event) {
        // Update financial balance automatically
        actualizarBalance(event.getGasto());
    }
}
```

**Benefits**:
- Modules are decoupled
- Easy to add new listeners
- No direct dependencies between modules

---

## 🧪 Testing Strategy

### Test Coverage
- Unit tests for services
- Integration tests for controllers
- Repository tests with H2 in-memory DB

**Run tests**:
```bash
./gradlew test
```

---

## 🎯 Roadmap

### Phase 1: Foundation ✅ COMPLETE
- [x] Authentication & JWT
- [x] User & Tenant management
- [x] Security (Rate limiting, CORS, Headers)
- [x] Database migrations

### Phase 2: Business Modules 🔄 IN PROGRESS
- [ ] Gastos module (expense tracking)
- [ ] Pedidos module (order management)
- [ ] Finanzas module (financial statements)

### Phase 3: Automation 🔴 PLANNED
- [ ] n8n webhook endpoints
- [ ] Telegram bot for expenses
- [ ] WhatsApp bot for orders
- [ ] Scheduled financial reports

### Phase 4: Dashboard 🔴 PLANNED
- [ ] React frontend
- [ ] Login/Register pages
- [ ] Gastos management UI
- [ ] Pedidos tracking UI
- [ ] Financial dashboard with charts

---

## 🤝 Contributing

This is a personal project by QuickStack Labs, but feedback is welcome!

---

## 📝 Documentation

- **README.md** - Architecture overview (you are here)
- **CONTEXT.md** - Project context and decisions
- **quickstack-core/README.md** - Backend implementation guide

---

## 📧 Contact

**Company**: QuickStack Labs
**Developer**: [@eartu](https://github.com/eartu)

---

## 📄 License

MIT License - feel free to use this architecture for your own projects.

---

**Built with a focus on modularity, automation, and clean architecture.**
