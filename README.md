# QuickStack Labs - SaaS Factory Architecture

> A production-ready, multi-tenant SaaS architecture designed for rapid prototyping and enterprise scalability.

## 🎯 Project Vision

QuickStack Labs is not a single SaaS product—it's a **reusable architecture** for building multiple SaaS products efficiently. Think of it as a factory that produces custom SaaS solutions while maintaining consistency, security, and scalability.

### The Problem We Solve
Building SaaS products from scratch is time-consuming. Each new product requires:
- Authentication and authorization
- Multi-tenant architecture
- User management
- Subscription/contract handling
- Security best practices
- Deployment infrastructure

**QuickStack Labs solves this by providing a battle-tested core that handles all common SaaS concerns**, allowing teams to focus on product-specific features.

---

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend Layer                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Product A   │  │  Product B   │  │  Product C   │      │
│  │   (Vite)     │  │   (Vite)     │  │   (Vite)     │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
└─────────┼──────────────────┼──────────────────┼─────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────┐
│                      Backend Layer                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Product A   │  │  Product B   │  │  Product C   │      │
│  │  (FastAPI)   │  │  (FastAPI)   │  │  (FastAPI)   │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                  │                  │              │
│         └──────────────────┼──────────────────┘              │
│                            │                                 │
│                            ▼                                 │
│                   ┌─────────────────┐                        │
│                   │   Core Backend  │                        │
│                   │  (Spring Boot)  │                        │
│                   │                 │                        │
│                   │  • Auth (JWT)   │                        │
│                   │  • Multi-tenant │                        │
│                   │  • RBAC         │                        │
│                   │  • Users        │                        │
│                   │  • Contracts    │                        │
│                   └────────┬────────┘                        │
└────────────────────────────┼───────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                      Database Layer                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Product A   │  │  Product B   │  │  Core DB     │      │
│  │  PostgreSQL  │  │  PostgreSQL  │  │  PostgreSQL  │      │
│  │  (Neon)      │  │  (Neon)      │  │  (Neon)      │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### Key Architectural Principles

1. **Separation of Concerns**: Core handles identity, products handle business logic
2. **Database per Product**: Each product has its own database for isolation and scalability
3. **API-First**: All communication happens through well-defined REST APIs
4. **Security by Default**: No direct database access from frontend, JWT-based auth
5. **Multi-Tenant**: Logical data isolation using `tenant_id` in all product databases

---

## 🛠️ Technology Stack

### Backend
- **Core**: Java 17+ with Spring Boot 3.x
  - Spring Security for authentication
  - Flyway for database migrations
  - JWT for stateless auth

- **Products**: Python 3.10+ with FastAPI
  - SQLAlchemy for ORM
  - Alembic for database migrations
  - Pydantic for validation

### Frontend
- **React 18+** with TypeScript
- **Vite** for blazing-fast development
- **React Router** for navigation
- **Axios** for API communication

### Infrastructure
- **Databases**: Neon (managed PostgreSQL)
- **Backend Hosting**: Render (Docker containers)
- **Frontend Hosting**: Vercel
- **CI/CD**: GitHub Actions
- **Containerization**: Docker (backend only)

---

## 📦 Repository Structure

QuickStack Labs follows a **multi-repo strategy** where each component is an independent repository. This monorepo contains all components for documentation and reference:

```
QuickStack/                         # THIS REPOSITORY (Documentation & Reference)
│
├── README.md                       # 📚 Main architecture documentation
├── .gitignore                      # Git ignore patterns
│
├── quickstack-core/                # ⚙️ Core Backend (Spring Boot)
│   ├── src/main/java/              # Java source code
│   │   └── com/quickstack/core/
│   │       ├── auth/               # Authentication & JWT
│   │       ├── tenant/             # Multi-tenancy management
│   │       ├── security/           # Security configuration
│   │       ├── user/               # User management
│   │       └── contract/           # Subscription/billing
│   ├── pom.xml                     # Maven configuration
│   ├── Dockerfile                  # Docker container config
│   └── README.md                   # Core documentation
│
├── quickstack-python-sdk/          # 🐍 Python SDK
│   ├── quickstack_sdk/             # SDK source code
│   │   ├── auth.py                 # Auth client
│   │   ├── tenant.py               # Tenant client
│   │   └── permissions.py          # Permission helpers
│   ├── tests/                      # Test suite
│   ├── setup.py                    # Package configuration
│   └── README.md                   # SDK documentation
│
├── quickstack-react-ui/            # ⚛️ React Component Library
│   ├── src/
│   │   ├── components/             # Shared UI components
│   │   │   ├── Button/
│   │   │   ├── Form/
│   │   │   ├── Modal/
│   │   │   └── Layout/
│   │   ├── hooks/                  # React hooks (useAuth, useTenant)
│   │   └── theme/                  # Theme configuration
│   ├── package.json
│   └── README.md                   # Component library docs
│
├── quickstack-product-template/    # 🚀 Product Template
│   ├── backend/                    # FastAPI backend
│   │   ├── app/
│   │   │   ├── api/                # REST endpoints
│   │   │   ├── models/             # SQLAlchemy models
│   │   │   ├── schemas/            # Pydantic schemas
│   │   │   └── services/           # Business logic
│   │   ├── tests/                  # Backend tests
│   │   ├── Dockerfile
│   │   └── requirements.txt
│   ├── frontend/                   # React frontend
│   │   ├── src/
│   │   │   ├── components/
│   │   │   ├── pages/
│   │   │   └── services/           # API clients
│   │   ├── package.json
│   │   └── vite.config.ts
│   ├── docker-compose.yml          # Local development
│   ├── package.json                # Root scripts
│   └── README.md                   # Template usage guide
│
├── quickstack-product-crm/         # 📊 Example: CRM Product
│   └── README.md                   # CRM product documentation
│
└── quickstack-product-analytics/   # 📈 Example: Analytics Product
    └── README.md                   # Analytics product documentation
```

### Deployment Strategy

In production, each component would be in its own repository:

```
GitHub Organization: quickstack-labs/
│
├── quickstack-core                 → Deploy to Render
├── quickstack-python-sdk           → Publish to PyPI
├── quickstack-react-ui             → Publish to npm
├── quickstack-product-template     → Template repository
├── quickstack-product-crm          → Deploy backend (Render) + frontend (Vercel)
└── quickstack-product-analytics    → Deploy backend (Render) + frontend (Vercel)
```

### Why Multi-Repo?

- **Independent Deployment**: Each product can be deployed without affecting others
- **Team Autonomy**: Different teams can own different products
- **Clear Boundaries**: Reduces coupling between products
- **Simplified CI/CD**: Each repo has its own pipeline
- **Versioning**: Components can be versioned independently

### Why This Monorepo Format?

This repository serves as:
1. **Architecture Documentation** - See the complete system in one place
2. **Portfolio/Reference** - Demonstrate system design skills
3. **Quick Start** - Clone once, understand everything
4. **Proof of Concept** - Show how all pieces fit together

When deploying to production or working with a team, split into separate repositories.

---

## 🚀 Quick Start

### Exploring This Repository

This is a **documentation and reference repository**. To explore:

```bash
# Clone this repository
git clone https://github.com/YOUR_USERNAME/QuickStack
cd QuickStack

# Explore the structure
ls -la
```

Each directory contains:
- Complete documentation in README.md
- Production-ready code structure
- Configuration examples

### Using the Product Template

To create a new product from the template:

```bash
# Navigate to template
cd QuickStack/quickstack-product-template

# Install dependencies
npm run setup

# Start development environment
npm run dev
```

**That's it!**
- Backend API: `http://localhost:8000`
- API Docs: `http://localhost:8000/api/docs`
- Frontend: `http://localhost:5173`
- Database: `localhost:5432`

### Prerequisites
- Node.js 18+
- Python 3.10+
- Docker & Docker Compose
- Git
- Java 17+ (for Core Backend)
- Maven 3.8+ (for Core Backend)

---

## 🔐 Security & Multi-Tenancy

### Authentication Flow

1. User logs in via Core Backend → receives JWT
2. JWT contains: `user_id`, `tenant_id`, `roles`, `permissions`
3. Product backend validates JWT on every request
4. Product backend extracts `tenant_id` and enforces data isolation

### Multi-Tenant Data Isolation

Every table in product databases includes a `tenant_id` column:

```sql
CREATE TABLE contacts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,  -- Data isolation
    name VARCHAR(255),
    email VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

-- All queries automatically filter by tenant
SELECT * FROM contacts WHERE tenant_id = ?;
```

**No tenant can access another tenant's data—guaranteed by the database schema.**

---

## 📊 Database Migration Strategy

### Why We Use Migrations (Even with Managed Databases)

**Common Misconception**: "Neon manages my database, so I don't need migrations."

**Reality**: Neon manages **infrastructure** (backups, uptime, scaling), **NOT your schema**.

**Migrations give you**:
- ✅ Version control for database schema
- ✅ Reproducible environments (dev, staging, prod)
- ✅ Team collaboration without conflicts
- ✅ Rollback capability
- ✅ Automated CI/CD deployments

### Migration Tools

- **Core (Java)**: Flyway
  ```sql
  -- db/migration/V1__create_users.sql
  CREATE TABLE users (...)
  ```

- **Products (Python)**: Alembic
  ```bash
  alembic revision --autogenerate -m "Add contacts table"
  alembic upgrade head
  ```

---

## 🧩 Component Deep Dive

### 1. QuickStack Core (`quickstack-core/`)

**Purpose**: Central authentication and multi-tenant management service.

**What it provides:**
- User authentication (login, registration, password reset)
- JWT token generation and validation
- Tenant (organization) management
- Role-based access control (RBAC)
- Subscription/contract management
- Centralized user database

**Technology**: Java 17 + Spring Boot 3.x + PostgreSQL

**Key APIs**:
```
POST /api/auth/login          - Authenticate user
POST /api/auth/validate       - Validate JWT token
GET  /api/users               - List users in tenant
POST /api/tenants             - Create new organization
```

**When to use**: Every product authenticates through Core. Never build your own auth system.

**Documentation**: See `quickstack-core/README.md`

---

### 2. Python SDK (`quickstack-python-sdk/`)

**Purpose**: Official Python library for product backends to communicate with Core.

**What it provides:**
- Pre-built HTTP client for Core APIs
- Token validation helpers
- Type-safe data models (UserData, Tenant, etc.)
- Error handling and retries
- Permission checking utilities

**Installation:**
```bash
pip install quickstack-sdk
```

**Usage Example:**
```python
from quickstack_sdk import CoreClient

client = CoreClient(base_url="https://core.quickstack.io")
user_data = await client.auth.validate_token(jwt_token)
print(f"User: {user_data.email}, Tenant: {user_data.tenant_id}")
```

**When to use**: In every FastAPI product backend to validate requests and fetch user data.

**Documentation**: See `quickstack-python-sdk/README.md`

---

### 3. React UI Library (`quickstack-react-ui/`)

**Purpose**: Shared component library for consistent UI across all products.

**What it provides:**
- Pre-built React components (Button, Modal, Form, Table, etc.)
- React hooks (useAuth, useTenant, useApi, useForm)
- Theme system with dark/light mode
- TypeScript types for all components
- Accessibility (WCAG 2.1 AA compliant)

**Installation:**
```bash
npm install @quickstack/react-ui
```

**Usage Example:**
```tsx
import { Button, useAuth } from '@quickstack/react-ui';

function MyComponent() {
  const { user, logout } = useAuth();

  return (
    <div>
      <h1>Welcome {user.name}</h1>
      <Button onClick={logout}>Logout</Button>
    </div>
  );
}
```

**When to use**: In every product frontend to maintain consistent design and reduce development time.

**Documentation**: See `quickstack-react-ui/README.md`

---

### 4. Product Template (`quickstack-product-template/`)

**Purpose**: Starting point for all new QuickStack products.

**What it includes:**
- Complete FastAPI backend with:
  - JWT authentication integration
  - Multi-tenant models (BaseModel with tenant_id)
  - Example CRUD API (Items)
  - Database migrations (Alembic)
  - Test suite
  - Docker configuration

- Complete React frontend with:
  - TypeScript + Vite
  - React Router navigation
  - React Query for data fetching
  - Example pages (Dashboard, Items, Login)
  - API client with interceptors

- DevOps:
  - Docker Compose for local development
  - GitHub Actions CI/CD workflows
  - One-command setup (`npm run dev`)

**How to use:**
1. Copy template directory
2. Customize models and APIs
3. Build your UI
4. Deploy

**Documentation**: See `quickstack-product-template/TEMPLATE_README.md`

---

### 5. Example Products

#### CRM (`quickstack-product-crm/`)
Demonstrates a customer relationship management system with:
- Contact management
- Deal pipeline
- Activity tracking
- Sales reporting

#### Analytics (`quickstack-product-analytics/`)
Demonstrates a business intelligence platform with:
- Custom dashboards
- Data visualization (charts, graphs)
- SQL query builder
- Scheduled reports

**Note**: These are reference implementations showing how to build different types of products on QuickStack architecture.

---

## 🧩 Creating a New Product

### Step 1: Copy the Template

```bash
# Use this repository as template
git clone https://github.com/quickstack-labs/quickstack-product-template quickstack-product-myapp
cd quickstack-product-myapp
```

### Step 2: Configure Environment

```bash
# Backend
cp backend/.env.example backend/.env
# Edit backend/.env with your Neon DB URL, Core API URL, etc.

# Frontend
cp frontend/.env.example frontend/.env
# Edit frontend/.env with your backend API URL
```

### Step 3: Customize

1. Update `package.json` name
2. Modify `backend/app/api/` endpoints for your product logic
3. Modify `frontend/src/pages/` for your UI
4. Run migrations: `npm run db:migrate`

### Step 4: Deploy

```bash
# Push to GitHub
git remote add origin https://github.com/quickstack-labs/quickstack-product-myapp
git push -u origin main

# GitHub Actions will automatically:
# 1. Run tests
# 2. Deploy backend to Render
# 3. Deploy frontend to Vercel
```

---

## 🎨 Shared Components

### Python SDK (`quickstack-python-sdk`)

```python
from quickstack_sdk import CoreClient

# Authenticate with core
core = CoreClient(base_url="https://core.quickstack.io")
user = await core.auth.validate_token(jwt_token)

# Access tenant info
tenant = await core.tenant.get(user.tenant_id)
```

### React UI Library (`quickstack-react-ui`)

```tsx
import { Button, Modal, useAuth } from '@quickstack/react-ui';

function MyComponent() {
  const { user, logout } = useAuth();
  return <Button onClick={logout}>Sign Out</Button>;
}
```

---

## 🔄 CI/CD Pipeline

Each product repository includes GitHub Actions workflows:

### Backend CI/CD (`.github/workflows/backend-deploy.yml`)
- Triggers on: Push to `backend/` directory
- Steps:
  1. Run tests (`pytest`)
  2. Build Docker image
  3. Deploy to Render
  4. Run database migrations

### Frontend CI/CD (`.github/workflows/frontend-deploy.yml`)
- Triggers on: Push to `frontend/` directory
- Steps:
  1. Run tests (`npm test`)
  2. Build production bundle
  3. Deploy to Vercel

---

## 📈 Scalability Considerations

### Current Architecture (MVP)
- Suitable for: 100K+ users per product
- Single backend instance per product
- Database: Neon autoscaling

### Future Enhancements (When Needed)
- API Gateway (Kong/AWS API Gateway) for centralized routing
- Redis for caching and session management
- Message Queue (RabbitMQ/SQS) for async processing
- Horizontal scaling of backend services
- CDN for frontend assets (Cloudflare)

**Philosophy**: Start simple, scale when metrics demand it.

---

## 🤝 Contributing

This is a personal/portfolio project, but feedback is welcome!

### Development Workflow
1. Create feature branch: `git checkout -b feature/my-feature`
2. Make changes with clear commits
3. Ensure tests pass: `npm test` (frontend), `pytest` (backend)
4. Submit PR with description

---

## 📝 Documentation

- **Architecture Deep Dive**: `/docs/architecture.md` (TODO)
- **API Reference**: `/docs/api.md` (TODO)
- **Deployment Guide**: `/docs/deployment.md` (TODO)
- **Security Best Practices**: `/docs/security.md` (TODO)

---

## 🎓 Learning Outcomes

This project demonstrates expertise in:

- ✅ **System Design**: Multi-tenant SaaS architecture
- ✅ **Microservices**: Core + independent product backends
- ✅ **API Design**: RESTful APIs, JWT authentication
- ✅ **Database Design**: Multi-tenancy, migrations, normalization
- ✅ **DevOps**: Docker, CI/CD, infrastructure as code
- ✅ **Security**: RBAC, JWT, data isolation
- ✅ **Full-Stack**: Java, Python, React, TypeScript
- ✅ **Cloud Infrastructure**: Neon, Render, Vercel

---

## 📧 Contact

**Project by**: [Your Name]
**GitHub**: [@eartu](https://github.com/eartu)
**LinkedIn**: [Your LinkedIn]
**Portfolio**: [Your Website]

---

## 📄 License

MIT License - feel free to use this architecture for your own projects.

---

## 🗺️ Roadmap

- [x] Define architecture
- [x] Create product template
- [ ] Implement Core Backend (Spring Boot)
- [ ] Build Python SDK
- [ ] Create React UI library
- [ ] Build example CRM product
- [ ] Add API Gateway
- [ ] Implement caching layer
- [ ] Add monitoring/observability (Prometheus + Grafana)
- [ ] Write comprehensive documentation

---

**Built with ❤️ by a developer who understands that great products start with great architecture.**
