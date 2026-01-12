# QuickStack Core - Security Sprint Roadmap

## Executive Summary

**Objective**: Implement 12 critical security features for QuickStack Core multi-tenant SaaS backend
**Timeline**: 6 working days (Sprint 1-6)
**Pace**: 2 critical security features per day
**Teams Involved**: Backend, QA, DevOps

### Current Security Baseline
- BCrypt password hashing (implemented)
- JWT with HMAC-SHA256 signing (implemented)
- Security headers: CSP, HSTS, X-Frame-Options (implemented)
- Rate limiting via Bucket4j (implemented)
- CORS configuration (implemented)
- Multi-tenancy with tenant_id isolation (implemented)

### Critical Vulnerabilities to Address
- JWT secret hardcoded in application.properties
- No refresh token mechanism (tokens cannot be revoked)
- No audit logging for security events
- No account lockout after failed attempts
- Sensitive data stored in plaintext
- No automatic tenant isolation verification
- No dependency vulnerability scanning

---

## Sprint Breakdown

### Sprint 1: Foundation & Critical Vulnerabilities
**Dates**: Day 1-2
**Sprint Goal**: Establish audit logging foundation and fix critical JWT secret vulnerability

---

#### Day 1: Audit Logging & Security Events + Secrets Management

##### Feature 1: Audit Logging & Security Events
**Priority**: P0 (Critical - Foundation for all security monitoring)
**Estimated Effort**: 6 hours

**Backend Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Create `V003__create_audit_logs.sql` migration | P0 | 30 min | None |
| Create `AuditLog` entity with fields: id, event_type, user_id, tenant_id, ip_address, user_agent, details (JSONB), created_at | P0 | 45 min | Migration |
| Create `AuditLogRepository` with query methods for filtering by tenant, user, event type, date range | P0 | 30 min | Entity |
| Create `AuditService` with async event logging capability | P0 | 1 hour | Repository |
| Create `SecurityEventType` enum: LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT, PASSWORD_CHANGE, TOKEN_REFRESH, ACCOUNT_LOCKED, ACCOUNT_UNLOCKED, USER_CREATED, USER_UPDATED, USER_DELETED, PERMISSION_DENIED, SUSPICIOUS_ACTIVITY | P0 | 15 min | None |
| Integrate audit logging into `AuthService.login()` for success/failure | P0 | 45 min | AuditService |
| Integrate audit logging into `AuthService.register()` | P1 | 30 min | AuditService |
| Add `@Async` configuration for non-blocking audit writes | P1 | 30 min | AuditService |
| Create `AuditLogController` with admin-only endpoints: GET /api/admin/audit-logs | P2 | 45 min | Service |

**Database Migration** (`V003__create_audit_logs.sql`):
```sql
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL,
    user_id UUID REFERENCES users(id),
    tenant_id UUID REFERENCES tenants(id),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_tenant_id ON audit_logs(tenant_id);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_event_type ON audit_logs(event_type);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_tenant_created ON audit_logs(tenant_id, created_at DESC);
```

**QA Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Write unit tests for `AuditService` (min 10 tests) | P0 | 1.5 hours | AuditService |
| Write integration tests for audit log persistence | P0 | 1 hour | Repository |
| Verify audit logs are created for login success/failure | P0 | 30 min | Integration |
| Test async logging does not block auth flow | P1 | 30 min | Async config |
| Test JSONB details field serialization | P1 | 30 min | Entity |

**Configuration Changes**:
- Add async thread pool configuration in `application.properties`
- Configure log retention policy (optional: 90 days default)

**Documentation**:
- Document audit event types and their meanings
- Add API documentation for audit log endpoints

---

##### Feature 2: Secrets Management & JWT Secret Rotation
**Priority**: P0 (Critical - Hardcoded secret is a severe vulnerability)
**Estimated Effort**: 5 hours

**Backend Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Create `SecretsService` interface for abstracting secret retrieval | P0 | 30 min | None |
| Implement `EnvironmentSecretsService` that reads from env vars | P0 | 30 min | Interface |
| Create `JwtKeyProvider` that supports multiple active keys for rotation | P0 | 1 hour | SecretsService |
| Modify `JwtService` to use `JwtKeyProvider` instead of hardcoded secret | P0 | 45 min | JwtKeyProvider |
| Implement key ID (kid) header in JWT for key identification | P0 | 30 min | JwtService |
| Create `V004__create_jwt_keys.sql` for storing key metadata (not the actual secrets) | P1 | 30 min | None |
| Add key rotation endpoint: POST /api/admin/security/rotate-jwt-key (admin only) | P1 | 45 min | JwtKeyProvider |
| Implement grace period logic: old keys valid for 24h after rotation | P1 | 45 min | JwtKeyProvider |

**Configuration Changes** (`application.properties` / environment):
```properties
# Remove hardcoded secret, use environment variable
jwt.secret=${JWT_SECRET}
jwt.expiration-ms=3600000
jwt.key-id=${JWT_KEY_ID:default-key}
jwt.rotation.grace-period-hours=24
```

**DevOps Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Add JWT_SECRET to Render environment variables | P0 | 15 min | None |
| Generate cryptographically secure 256-bit secret | P0 | 10 min | None |
| Document secret rotation procedure | P0 | 30 min | Backend impl |
| Add secret to local development .env.example | P1 | 10 min | None |
| Configure secrets in CI/CD pipeline | P1 | 30 min | None |

**QA Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Test JWT generation with new key provider | P0 | 45 min | JwtKeyProvider |
| Test token validation with rotated keys | P0 | 1 hour | Rotation impl |
| Test grace period: old tokens valid for 24h | P0 | 45 min | Rotation impl |
| Verify hardcoded secret is removed | P0 | 15 min | Config changes |
| Test key rotation endpoint security (admin only) | P1 | 30 min | Endpoint |

**Security Tests**:
- Verify JWT secret is not in source code
- Test that tokens signed with old key fail after grace period
- Test that tokens signed with unknown key fail immediately

---

#### Day 2: Refresh Tokens + Account Lockout

##### Feature 3: Refresh Tokens
**Priority**: P0 (Critical - Enables secure token lifecycle management)
**Estimated Effort**: 6 hours
**Dependencies**: Audit Logging (Day 1)

**Backend Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Create `V005__create_refresh_tokens.sql` migration | P0 | 30 min | None |
| Create `RefreshToken` entity: id, token_hash, user_id, tenant_id, expires_at, revoked, device_info, created_at | P0 | 45 min | Migration |
| Create `RefreshTokenRepository` with queries: findByTokenHash, findActiveByUserId, revokeAllByUserId | P0 | 30 min | Entity |
| Create `RefreshTokenService` with generate, validate, refresh, revoke methods | P0 | 1.5 hours | Repository |
| Modify `AuthResponse` to include refresh_token and expires_in | P0 | 15 min | None |
| Modify `AuthService.login()` to generate and return refresh token | P0 | 30 min | RefreshTokenService |
| Create `POST /api/auth/refresh` endpoint | P0 | 45 min | RefreshTokenService |
| Create `POST /api/auth/logout` endpoint to revoke refresh token | P0 | 30 min | RefreshTokenService |
| Create `POST /api/auth/logout-all` endpoint to revoke all user sessions | P1 | 30 min | RefreshTokenService |
| Add audit logging for token refresh and revocation events | P1 | 30 min | AuditService |

**Database Migration** (`V005__create_refresh_tokens.sql`):
```sql
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    revoked_at TIMESTAMP,
    device_info VARCHAR(500),
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_active ON refresh_tokens(user_id, revoked, expires_at);
```

**Configuration Changes**:
```properties
jwt.access-token.expiration-ms=900000       # 15 minutes
jwt.refresh-token.expiration-days=7         # 7 days
jwt.refresh-token.max-per-user=5            # Max active sessions per user
```

**QA Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Test refresh token generation on login | P0 | 30 min | Backend impl |
| Test refresh token rotation (new token on refresh) | P0 | 45 min | Backend impl |
| Test access token refresh flow | P0 | 45 min | Backend impl |
| Test refresh token revocation (logout) | P0 | 30 min | Backend impl |
| Test logout-all revokes all sessions | P0 | 30 min | Backend impl |
| Test expired refresh token rejection | P0 | 30 min | Backend impl |
| Test refresh token reuse detection (security) | P1 | 45 min | Backend impl |
| E2E test: full auth flow with token refresh | P1 | 1 hour | All endpoints |

---

##### Feature 4: Account Lockout & Brute Force Protection
**Priority**: P0 (Critical - Prevents credential stuffing attacks)
**Estimated Effort**: 4 hours
**Dependencies**: Audit Logging (Day 1)

**Backend Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Create `V006__add_lockout_columns.sql` migration | P0 | 20 min | None |
| Add columns to User entity: failed_login_attempts, locked_until, last_failed_login | P0 | 30 min | Migration |
| Create `AccountLockoutService` with lockout logic | P0 | 1 hour | User entity |
| Implement progressive lockout: 5 attempts = 15 min, 10 attempts = 1 hour, 15 attempts = 24 hours | P0 | 45 min | LockoutService |
| Integrate lockout check in `AuthService.login()` | P0 | 30 min | LockoutService |
| Update failed login counter on auth failure | P0 | 20 min | LockoutService |
| Reset counter on successful login | P0 | 15 min | LockoutService |
| Add audit events for ACCOUNT_LOCKED, ACCOUNT_UNLOCKED | P0 | 20 min | AuditService |
| Create admin endpoint: POST /api/admin/users/{id}/unlock | P1 | 30 min | LockoutService |

**Database Migration** (`V006__add_lockout_columns.sql`):
```sql
ALTER TABLE users ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP;
ALTER TABLE users ADD COLUMN last_failed_login TIMESTAMP;
```

**Configuration Changes**:
```properties
security.lockout.max-attempts=5
security.lockout.duration-minutes=15
security.lockout.progressive-multiplier=4    # Each threshold multiplies duration
security.lockout.reset-after-minutes=30      # Reset counter after successful login
```

**QA Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Test account locks after 5 failed attempts | P0 | 30 min | Backend impl |
| Test locked account cannot login | P0 | 20 min | Backend impl |
| Test progressive lockout durations | P0 | 45 min | Backend impl |
| Test automatic unlock after timeout | P0 | 30 min | Backend impl |
| Test admin unlock endpoint | P1 | 20 min | Backend impl |
| Test counter reset on successful login | P0 | 20 min | Backend impl |
| Test audit events are logged for lockout | P1 | 20 min | AuditService |

---

### Sprint 2: Input Security & Data Protection
**Dates**: Day 3-4
**Sprint Goal**: Harden input handling and protect sensitive data at rest

---

#### Day 3: Input Validation & Sanitization + Encryption at Rest

##### Feature 5: Input Validation & Sanitization
**Priority**: P0 (Critical - Prevents injection attacks)
**Estimated Effort**: 5 hours

**Backend Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Create `InputSanitizer` utility class for XSS prevention | P0 | 45 min | None |
| Create `@Sanitized` annotation for automatic field sanitization | P0 | 45 min | InputSanitizer |
| Create `SanitizationAspect` for AOP-based input cleaning | P0 | 1 hour | Annotation |
| Add OWASP Java HTML Sanitizer dependency to build.gradle | P0 | 10 min | None |
| Create custom validators: @NoHtml, @SafeString, @ValidTenantSlug | P0 | 1 hour | None |
| Apply validators to all DTOs: LoginRequest, RegisterRequest, UserUpdateRequest | P0 | 45 min | Validators |
| Create `GlobalInputFilter` servlet filter for request body size limits | P1 | 30 min | None |
| Configure max request body size (10MB default) | P1 | 15 min | Filter |
| Add SQL injection prevention patterns to validation | P1 | 30 min | Validators |

**Dependency Addition** (build.gradle):
```groovy
implementation 'org.owasp:java-html-sanitizer:20240325.1'
implementation 'commons-validator:commons-validator:1.8.0'
```

**DTO Validation Examples**:
```java
public class RegisterRequest {
    @NotBlank @Email @Size(max = 255)
    private String email;

    @NotBlank @Size(min = 8, max = 100) @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$")
    private String password;

    @NotBlank @Size(min = 2, max = 100) @SafeString
    private String userName;

    @NotBlank @Size(min = 2, max = 100) @ValidTenantSlug
    private String tenantSlug;
}
```

**QA Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Test XSS payload rejection in all string inputs | P0 | 45 min | Sanitizer |
| Test SQL injection patterns are blocked | P0 | 45 min | Validators |
| Test HTML entity encoding in outputs | P0 | 30 min | Sanitizer |
| Test request body size limits | P1 | 20 min | Filter |
| Test all DTOs have proper validation annotations | P0 | 30 min | DTO updates |
| Test error messages do not leak sensitive info | P1 | 30 min | Exception handler |
| Create injection test suite (SQLi, XSS, XXE) | P0 | 1.5 hours | All features |

---

##### Feature 6: Encryption at Rest for Sensitive Data
**Priority**: P0 (Critical - Protects PII and sensitive business data)
**Estimated Effort**: 6 hours
**Dependencies**: Secrets Management (Day 1)

**Backend Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Create `EncryptionService` with AES-256-GCM implementation | P0 | 1.5 hours | None |
| Create `@Encrypted` annotation for entity field encryption | P0 | 30 min | EncryptionService |
| Create `EncryptedStringConverter` JPA AttributeConverter | P0 | 1 hour | EncryptionService |
| Add encryption key management to `SecretsService` | P0 | 45 min | SecretsService |
| Create `V007__add_encryption_metadata.sql` for key version tracking | P0 | 30 min | None |
| Identify and mark sensitive fields for encryption (phone, address, etc.) | P1 | 30 min | Annotation |
| Implement key rotation for encrypted data | P1 | 1 hour | EncryptionService |
| Create data re-encryption job for key rotation | P2 | 1 hour | Key rotation |

**Database Migration** (`V007__add_encryption_metadata.sql`):
```sql
CREATE TABLE encryption_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    key_version INTEGER NOT NULL DEFAULT 1,
    encrypted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(entity_type, field_name)
);

-- Add key_version column to any table with encrypted fields
-- (Will be used when we add encrypted fields to user profile, etc.)
```

**Configuration Changes**:
```properties
encryption.algorithm=AES/GCM/NoPadding
encryption.key-length=256
encryption.master-key=${ENCRYPTION_MASTER_KEY}
encryption.key-version=1
```

**DevOps Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Generate encryption master key (32 bytes) | P0 | 10 min | None |
| Add ENCRYPTION_MASTER_KEY to environment | P0 | 15 min | Key generation |
| Document key backup and recovery procedure | P0 | 30 min | Key management |

**QA Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Test encryption/decryption round-trip | P0 | 30 min | EncryptionService |
| Verify encrypted data is not readable in DB | P0 | 30 min | Converter |
| Test encryption with different data sizes | P1 | 30 min | EncryptionService |
| Test key rotation does not break existing data | P0 | 45 min | Key rotation |
| Test performance impact of encryption | P2 | 30 min | All features |

---

#### Day 4: RBAC Granular & Permissions + Secure Session Management

##### Feature 7: RBAC Granular & Permissions
**Priority**: P1 (High - Enables fine-grained access control)
**Estimated Effort**: 6 hours

**Backend Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Create `V008__create_permissions.sql` migration | P0 | 30 min | None |
| Create `Permission` entity with hierarchical structure | P0 | 45 min | Migration |
| Create `RolePermission` join entity | P0 | 30 min | Permission |
| Create `PermissionRepository` and `RolePermissionRepository` | P0 | 30 min | Entities |
| Create `PermissionService` for permission checking | P0 | 1 hour | Repositories |
| Define core permissions: users:read, users:write, users:delete, tenant:manage, audit:read, etc. | P0 | 30 min | Service |
| Create `@RequirePermission` annotation for method-level security | P0 | 45 min | PermissionService |
| Create `PermissionCheckAspect` for AOP-based authorization | P0 | 1 hour | Annotation |
| Modify `JwtService` to include permissions in token claims | P1 | 30 min | Permission setup |
| Create admin endpoints for role-permission management | P2 | 1 hour | Service |

**Database Migration** (`V008__create_permissions.sql`):
```sql
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    resource VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE role_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role VARCHAR(50) NOT NULL,
    permission_id UUID NOT NULL REFERENCES permissions(id),
    tenant_id UUID REFERENCES tenants(id),  -- NULL = global, set = tenant-specific
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(role, permission_id, tenant_id)
);

-- Seed default permissions
INSERT INTO permissions (id, code, name, description, resource, action) VALUES
    (gen_random_uuid(), 'users:read', 'View Users', 'View user list and details', 'users', 'read'),
    (gen_random_uuid(), 'users:write', 'Manage Users', 'Create and update users', 'users', 'write'),
    (gen_random_uuid(), 'users:delete', 'Delete Users', 'Delete users', 'users', 'delete'),
    (gen_random_uuid(), 'tenant:read', 'View Tenant', 'View tenant info', 'tenant', 'read'),
    (gen_random_uuid(), 'tenant:manage', 'Manage Tenant', 'Update tenant settings', 'tenant', 'manage'),
    (gen_random_uuid(), 'audit:read', 'View Audit Logs', 'Access audit log history', 'audit', 'read'),
    (gen_random_uuid(), 'security:manage', 'Manage Security', 'Configure security settings', 'security', 'manage');

CREATE INDEX idx_role_permissions_role ON role_permissions(role);
CREATE INDEX idx_permissions_code ON permissions(code);
```

**QA Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Test permission check for each protected endpoint | P0 | 1 hour | Backend impl |
| Test ADMIN role has all permissions | P0 | 30 min | Backend impl |
| Test USER role has limited permissions | P0 | 30 min | Backend impl |
| Test permission denied returns 403 | P0 | 20 min | Backend impl |
| Test tenant-specific permission scoping | P1 | 45 min | Backend impl |
| Test permission inheritance (if implemented) | P2 | 30 min | Backend impl |

---

##### Feature 8: Secure Session Management
**Priority**: P0 (Critical - Enables token revocation and session control)
**Estimated Effort**: 5 hours
**Dependencies**: Refresh Tokens (Day 2)

**Backend Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Create `V009__create_active_sessions.sql` migration | P0 | 30 min | None |
| Create `ActiveSession` entity tracking all user sessions | P0 | 45 min | Migration |
| Create `SessionService` for session lifecycle management | P0 | 1 hour | Entity |
| Implement session creation on login | P0 | 30 min | SessionService |
| Implement session termination on logout | P0 | 30 min | SessionService |
| Create token blacklist mechanism using Redis/in-memory cache | P0 | 1 hour | None |
| Add session validation in `JwtAuthenticationFilter` | P0 | 45 min | SessionService |
| Create endpoint: GET /api/users/me/sessions (list active sessions) | P1 | 30 min | SessionService |
| Create endpoint: DELETE /api/users/me/sessions/{id} (terminate session) | P1 | 30 min | SessionService |

**Database Migration** (`V009__create_active_sessions.sql`):
```sql
CREATE TABLE active_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    refresh_token_id UUID REFERENCES refresh_tokens(id),
    device_info VARCHAR(500),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    last_activity TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    terminated_at TIMESTAMP
);

CREATE INDEX idx_active_sessions_user_id ON active_sessions(user_id);
CREATE INDEX idx_active_sessions_active ON active_sessions(user_id, terminated_at);

-- Token blacklist for immediate revocation
CREATE TABLE token_blacklist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_jti VARCHAR(255) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMP NOT NULL,
    reason VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_token_blacklist_jti ON token_blacklist(token_jti);
CREATE INDEX idx_token_blacklist_expires ON token_blacklist(expires_at);
```

**Configuration Changes**:
```properties
session.max-concurrent=5
session.idle-timeout-minutes=30
session.absolute-timeout-hours=24
token.blacklist.cleanup-cron=0 */15 * * * *   # Every 15 minutes
```

**QA Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Test session created on login | P0 | 20 min | Backend impl |
| Test session terminated on logout | P0 | 20 min | Backend impl |
| Test blacklisted token is rejected | P0 | 30 min | Backend impl |
| Test session list endpoint | P0 | 20 min | Backend impl |
| Test remote session termination | P0 | 30 min | Backend impl |
| Test concurrent session limits | P1 | 30 min | Backend impl |
| Test session cleanup job | P2 | 20 min | Backend impl |

---

### Sprint 3: Logging & Database Security
**Dates**: Day 5
**Sprint Goal**: Implement secure logging and harden database access

---

#### Day 5: Request/Response Logging Seguro + Database Query Security

##### Feature 9: Request/Response Logging Seguro
**Priority**: P1 (High - Essential for debugging and security investigation)
**Estimated Effort**: 4 hours

**Backend Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Create `SecureLoggingFilter` servlet filter | P0 | 1 hour | None |
| Implement request ID generation and propagation (X-Request-ID) | P0 | 30 min | Filter |
| Create `SensitiveDataMasker` utility for PII redaction | P0 | 45 min | None |
| Configure patterns for sensitive fields: password, token, creditCard, ssn | P0 | 30 min | Masker |
| Add structured JSON logging format with Logback | P0 | 30 min | None |
| Include tenant_id, user_id, request_id in MDC context | P0 | 30 min | Filter |
| Create log rotation and retention configuration | P1 | 30 min | None |
| Add request/response body logging with size limits | P2 | 45 min | Filter |

**Configuration Changes** (logback-spring.xml):
```xml
<encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <includeMdcKeyName>requestId</includeMdcKeyName>
    <includeMdcKeyName>tenantId</includeMdcKeyName>
    <includeMdcKeyName>userId</includeMdcKeyName>
</encoder>
```

**Dependency Addition** (build.gradle):
```groovy
implementation 'net.logstash.logback:logstash-logback-encoder:7.4'
```

**QA Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Verify passwords are never logged | P0 | 30 min | Masker |
| Verify tokens are masked in logs | P0 | 30 min | Masker |
| Test request ID propagation | P0 | 20 min | Filter |
| Test tenant context in logs | P0 | 20 min | MDC |
| Test log format is valid JSON | P1 | 20 min | Config |

---

##### Feature 10: Database Query Security
**Priority**: P0 (Critical - Prevents SQL injection and data leakage)
**Estimated Effort**: 5 hours

**Backend Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Audit all existing queries for SQL injection vulnerabilities | P0 | 1 hour | None |
| Replace any raw SQL with parameterized queries | P0 | 1 hour | Audit |
| Create `TenantAwareRepository` base class with automatic tenant filtering | P0 | 1 hour | None |
| Implement `@TenantScoped` annotation for repository methods | P0 | 45 min | Base class |
| Create `TenantInterceptor` for JPA to inject tenant_id in queries | P0 | 1 hour | None |
| Add query timeout configuration to prevent slow query attacks | P1 | 20 min | None |
| Enable query logging in development for security review | P1 | 15 min | None |
| Create database user with minimal required privileges | P2 | 30 min | DevOps assist |

**Configuration Changes**:
```properties
spring.jpa.properties.hibernate.default_batch_fetch_size=25
spring.jpa.properties.jakarta.persistence.query.timeout=5000
spring.datasource.hikari.connection-timeout=10000
spring.datasource.hikari.validation-timeout=5000
```

**DevOps Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Create read-only database user for reporting queries | P2 | 20 min | None |
| Configure connection pooling limits | P1 | 20 min | None |
| Enable PostgreSQL query logging (development only) | P2 | 15 min | None |

**QA Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Test parameterized query prevents injection | P0 | 45 min | Backend impl |
| Test tenant isolation - user cannot access other tenant data | P0 | 1 hour | TenantAware |
| Test query timeout behavior | P1 | 30 min | Config |
| Penetration test: attempt SQL injection on all endpoints | P0 | 1 hour | All features |

---

### Sprint 4: Multi-Tenant Security & Supply Chain
**Dates**: Day 6
**Sprint Goal**: Ensure tenant isolation and establish supply chain security

---

#### Day 6: Tenant Isolation Verification + Dependency Scanning & SBOM

##### Feature 11: Tenant Isolation Verification
**Priority**: P0 (Critical - Prevents cross-tenant data access)
**Estimated Effort**: 5 hours

**Backend Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Create `TenantContext` thread-local holder for current tenant | P0 | 30 min | None |
| Create `TenantContextFilter` to extract tenant from JWT | P0 | 45 min | TenantContext |
| Create `TenantIsolationAspect` for automatic tenant checking | P0 | 1 hour | TenantContext |
| Add `@TenantIsolated` annotation for entity-level protection | P0 | 30 min | Aspect |
| Implement tenant validation in all service layer methods | P0 | 1 hour | TenantContext |
| Create `TenantMismatchException` for isolation violations | P0 | 15 min | None |
| Add tenant isolation check in `JwtAuthenticationFilter` | P0 | 30 min | Filter |
| Create tenant isolation test suite | P0 | 1 hour | All features |

**Implementation Pattern**:
```java
@Aspect
@Component
public class TenantIsolationAspect {
    @Before("@annotation(TenantIsolated)")
    public void verifyTenantIsolation(JoinPoint joinPoint) {
        UUID currentTenant = TenantContext.getCurrentTenant();
        // Extract tenant from method arguments and verify match
        // Throw TenantMismatchException if mismatch detected
    }
}
```

**QA Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Test user cannot read other tenant's users | P0 | 30 min | Backend impl |
| Test user cannot update other tenant's data | P0 | 30 min | Backend impl |
| Test user cannot delete other tenant's resources | P0 | 30 min | Backend impl |
| Test audit logs are tenant-scoped | P0 | 20 min | Backend impl |
| Test all API endpoints for tenant isolation | P0 | 1.5 hours | Backend impl |
| Create automated tenant isolation test suite | P0 | 1 hour | All tests |

---

##### Feature 12: Dependency Scanning & SBOM
**Priority**: P1 (High - Supply chain security)
**Estimated Effort**: 4 hours

**DevOps Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Add OWASP Dependency-Check plugin to build.gradle | P0 | 30 min | None |
| Configure vulnerability threshold (fail on HIGH/CRITICAL) | P0 | 15 min | Plugin |
| Add CycloneDX plugin for SBOM generation | P0 | 30 min | None |
| Configure automated SBOM generation on each build | P0 | 15 min | Plugin |
| Set up GitHub Dependabot for automated PR updates | P0 | 30 min | None |
| Create .dependabot.yml configuration | P0 | 20 min | None |
| Add dependency scan to CI/CD pipeline | P0 | 30 min | Plugin |
| Configure weekly vulnerability scan schedule | P1 | 15 min | CI/CD |
| Create process for vulnerability remediation | P1 | 30 min | Documentation |

**Build Configuration** (build.gradle additions):
```groovy
plugins {
    id 'org.owasp.dependencycheck' version '9.0.9'
    id 'org.cyclonedx.bom' version '1.8.2'
}

dependencyCheck {
    failBuildOnCVSS = 7    // Fail on HIGH (7+) vulnerabilities
    suppressionFile = 'dependency-check-suppressions.xml'
    analyzers {
        assemblyEnabled = false
        nodeEnabled = false
    }
}

cyclonedxBom {
    includeConfigs = ["runtimeClasspath"]
    outputFormat = "json"
    destination = file("build/reports")
}
```

**GitHub Configuration** (.github/dependabot.yml):
```yaml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/quickstack-core"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 10
    reviewers:
      - "security-team"
    labels:
      - "dependencies"
      - "security"
```

**QA Team Tasks**:

| Task | Priority | Estimate | Dependencies |
|------|----------|----------|--------------|
| Verify dependency scan runs on PR | P0 | 20 min | CI/CD |
| Verify build fails on CRITICAL vulnerability | P0 | 20 min | Config |
| Verify SBOM is generated correctly | P0 | 15 min | Plugin |
| Review current dependencies for known CVEs | P0 | 1 hour | Scan output |
| Document vulnerability triage process | P1 | 30 min | Process def |

---

## Backlog: Nice-to-Have Features

The following features are tracked as backlog items for future sprints. They are valuable but not critical for initial security hardening.

---

### BACKLOG-001: Multi-Factor Authentication (MFA/2FA)
**Priority**: P2 (Future Sprint)
**Estimated Effort**: 8 hours

**Description**: Implement TOTP-based two-factor authentication using authenticator apps (Google Authenticator, Authy). Includes QR code generation for setup, backup codes, and 2FA enforcement policies.

**Acceptance Criteria**:
- User can enable 2FA from account settings
- QR code displayed for authenticator app setup
- 10 backup codes generated on 2FA setup
- Login flow prompts for 2FA code when enabled
- Admin can enforce 2FA for all tenant users
- 2FA can be reset by admin if user loses device

**Technical Notes**:
- Use java-totp library for TOTP implementation
- Store encrypted TOTP secret in users table
- Consider adding SMS fallback (requires Twilio integration)

---

### BACKLOG-002: OAuth2/SSO Integration
**Priority**: P2 (Future Sprint)
**Estimated Effort**: 12 hours

**Description**: Enable Single Sign-On via OAuth2 providers (Google, Microsoft, GitHub). Support both social login for individual users and enterprise SSO with SAML/OIDC.

**Acceptance Criteria**:
- Support Google, Microsoft, GitHub OAuth2 providers
- Tenant can configure allowed OAuth providers
- Auto-create user on first OAuth login (with tenant association)
- Link existing email accounts to OAuth identity
- Support OIDC for enterprise customers

**Technical Notes**:
- Use Spring Security OAuth2 Client
- Store OAuth tokens securely (encrypted)
- Consider tenant-specific OAuth app credentials

---

### BACKLOG-003: API Key Management
**Priority**: P2 (Future Sprint)
**Estimated Effort**: 6 hours

**Description**: Allow users/tenants to create API keys for programmatic access. Support key rotation, scoped permissions, and usage tracking.

**Acceptance Criteria**:
- Create API keys with custom names/descriptions
- Set expiration date for keys
- Assign specific permissions to keys
- View key usage statistics
- Revoke keys immediately
- Rate limit per API key

**Technical Notes**:
- Store key hash only (never store plaintext)
- Use separate table: api_keys
- Include key ID prefix for identification

---

### BACKLOG-004: IP Whitelisting/Blacklisting
**Priority**: P3 (Future Sprint)
**Estimated Effort**: 4 hours

**Description**: Allow tenants to restrict access based on IP addresses or CIDR ranges. Support both allow-lists and block-lists.

**Acceptance Criteria**:
- Tenant admin can configure allowed IP ranges
- Support CIDR notation (e.g., 192.168.1.0/24)
- Block known malicious IPs globally
- Temporary IP blocks after suspicious activity
- Whitelist bypass for admin accounts (emergency access)

---

### BACKLOG-005: Advanced Rate Limiting
**Priority**: P3 (Future Sprint)
**Estimated Effort**: 6 hours

**Description**: Extend current rate limiting with per-user limits, dynamic throttling, and distributed rate limiting using Redis.

**Acceptance Criteria**:
- Rate limits per user (not just IP)
- Rate limits per API key
- Dynamic throttling based on server load
- Distributed rate limiting with Redis
- Custom rate limits per tenant/plan
- Bypass rate limits for internal services

**Technical Notes**:
- Upgrade to Bucket4j Redis integration
- Consider implementing leaky bucket for smoother throttling

---

### BACKLOG-006: Security Monitoring & Alerting
**Priority**: P2 (Future Sprint)
**Estimated Effort**: 8 hours

**Description**: Real-time security monitoring with alerting for suspicious activities. Integration with PagerDuty/Slack for notifications.

**Acceptance Criteria**:
- Alert on multiple failed login attempts
- Alert on login from new device/location
- Alert on privilege escalation attempts
- Alert on unusual API activity patterns
- Dashboard for security metrics
- Integration with PagerDuty/Slack

**Technical Notes**:
- Use Spring Boot Actuator metrics
- Consider Prometheus + Grafana stack
- Implement anomaly detection for unusual patterns

---

### BACKLOG-007: Automated Vulnerability Scanning
**Priority**: P2 (Future Sprint)
**Estimated Effort**: 6 hours

**Description**: Automated security scanning of application code and infrastructure. Integration with Snyk or SonarQube for continuous security analysis.

**Acceptance Criteria**:
- SAST scanning on every PR
- Container image scanning (when Docker is used)
- Infrastructure as Code scanning
- Weekly comprehensive scan reports
- Automatic issue creation for findings

**Technical Notes**:
- Integrate Snyk or SonarQube
- Consider GitHub Advanced Security if using GitHub Enterprise

---

### BACKLOG-008: WAF Integration
**Priority**: P3 (Future Sprint)
**Estimated Effort**: 4 hours

**Description**: Integration with Web Application Firewall for additional protection against common attacks.

**Acceptance Criteria**:
- Configure WAF rules for OWASP Top 10
- Custom rules for application-specific threats
- Geo-blocking capability
- DDoS protection at edge
- Attack logging and analysis

**Technical Notes**:
- Consider Cloudflare or AWS WAF
- May require infrastructure changes at Render

---

### BACKLOG-009: Advanced Threat Detection
**Priority**: P3 (Future Sprint)
**Estimated Effort**: 10 hours

**Description**: Machine learning-based threat detection for identifying anomalous behavior and potential security breaches.

**Acceptance Criteria**:
- Baseline normal user behavior
- Detect unusual access patterns
- Identify potential account takeover
- Flag suspicious data exports
- Automated response actions (block, alert)

**Technical Notes**:
- Start with rule-based detection
- Consider AWS GuardDuty or similar service
- Build custom ML model if data volume justifies

---

### BACKLOG-010: Compliance Automation
**Priority**: P2 (Future Sprint)
**Estimated Effort**: 12 hours

**Description**: Automated compliance checks and reporting for SOC 2, GDPR, HIPAA requirements.

**Acceptance Criteria**:
- Automated compliance evidence collection
- Policy enforcement verification
- Data retention compliance checks
- Access review automation
- Compliance dashboard and reports
- Export compliance artifacts

**Technical Notes**:
- Map controls to code/config checks
- Consider Vanta or Drata integration
- Build audit trail for all compliance-relevant actions

---

### BACKLOG-011: Backup & Disaster Recovery
**Priority**: P2 (Future Sprint)
**Estimated Effort**: 8 hours

**Description**: Comprehensive backup strategy and disaster recovery procedures for data protection.

**Acceptance Criteria**:
- Automated daily database backups
- Point-in-time recovery capability
- Cross-region backup replication
- Tested recovery procedures (RTO < 4 hours)
- Backup encryption at rest
- Backup integrity verification

**Technical Notes**:
- Neon provides automatic backups (verify settings)
- Document recovery procedures
- Schedule quarterly DR tests

---

### BACKLOG-012: Security Headers Enhancement
**Priority**: P3 (Future Sprint)
**Estimated Effort**: 3 hours

**Description**: Add additional security headers and refine existing CSP policy for maximum protection.

**Acceptance Criteria**:
- Add Permissions-Policy header
- Refine CSP based on actual resource usage
- Add Report-URI for CSP violations
- Enable HSTS preload
- Add Cross-Origin headers (COOP, COEP, CORP)

**Technical Notes**:
- Test CSP changes thoroughly (can break functionality)
- Consider CSP report-only mode for testing
- Submit to HSTS preload list after stability

---

## Sprint Summary

| Sprint | Day | Features | Total Hours |
|--------|-----|----------|-------------|
| Sprint 1 | Day 1 | Audit Logging + Secrets Management | 11 hours |
| Sprint 1 | Day 2 | Refresh Tokens + Account Lockout | 10 hours |
| Sprint 2 | Day 3 | Input Validation + Encryption at Rest | 11 hours |
| Sprint 2 | Day 4 | RBAC Granular + Secure Session Mgmt | 11 hours |
| Sprint 3 | Day 5 | Secure Logging + Database Security | 9 hours |
| Sprint 4 | Day 6 | Tenant Isolation + Dependency Scanning | 9 hours |

**Total Estimated Effort**: 61 hours across 6 days

---

## Dependencies Graph

```
Day 1: Audit Logging (foundation)
   |
   +---> Secrets Management (parallel)
   |
   v
Day 2: Refresh Tokens -----> Account Lockout
   |                              |
   +---------+--------------------+
             |
             v
Day 3: Input Validation (parallel) <---> Encryption at Rest
   |                                           |
   v                                           v
Day 4: RBAC Granular <---------> Secure Session Management
   |                                           |
   v                                           v
Day 5: Secure Logging <---------> Database Query Security
   |                                           |
   v                                           v
Day 6: Tenant Isolation Verification -----> Dependency Scanning
```

**Critical Path**: Audit Logging -> Refresh Tokens -> Secure Session Management -> Tenant Isolation

---

## Success Metrics

1. **Security Posture**
   - [ ] Zero hardcoded secrets in codebase
   - [ ] All endpoints protected with appropriate permissions
   - [ ] 100% tenant isolation coverage
   - [ ] No HIGH/CRITICAL vulnerabilities in dependencies

2. **Test Coverage**
   - [ ] Minimum 80% code coverage for security modules
   - [ ] All critical paths have integration tests
   - [ ] Penetration test passing for OWASP Top 10

3. **Operational**
   - [ ] Audit logs capturing all security events
   - [ ] Token revocation working < 1 second
   - [ ] Account lockout triggered after 5 attempts

4. **Compliance Readiness**
   - [ ] Encryption at rest for sensitive data
   - [ ] Complete audit trail available
   - [ ] Session management with revocation capability

---

## Risk Register

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| JWT secret rotation causes token invalidation | High | Medium | Implement 24h grace period for old keys |
| Encryption key loss causes data loss | Critical | Low | Document backup procedure, test recovery |
| Performance degradation from encryption | Medium | Medium | Benchmark before/after, optimize if needed |
| Tenant isolation bug exposes data | Critical | Low | Comprehensive test suite, code review |
| Dependency vulnerability discovered mid-sprint | Medium | Medium | Daily dependency scans, have remediation process |

---

## Open Questions / Decisions Needed

1. **Redis for Session Management**: Should we add Redis now for token blacklist, or use in-memory for MVP?
   - Recommendation: Start with in-memory (ConcurrentHashMap), plan Redis migration for production scaling

2. **Encryption Key Storage**: Where should encryption master key be stored long-term?
   - Options: Environment variable (current), AWS Secrets Manager, HashiCorp Vault
   - Recommendation: Environment variable for MVP, evaluate Vault for enterprise

3. **Audit Log Retention**: How long should audit logs be retained?
   - Recommendation: 90 days in primary table, archive to cold storage after

4. **SBOM Distribution**: Should SBOM be included in releases or stored separately?
   - Recommendation: Generate on each release, store in artifact repository

---

## Appendix: File Structure Changes

```
quickstack-core/src/main/java/com/quickstack/core/
├── audit/
│   ├── AuditLog.java
│   ├── AuditLogRepository.java
│   ├── AuditService.java
│   ├── AuditLogController.java
│   └── SecurityEventType.java
├── security/
│   ├── JwtService.java (modified)
│   ├── JwtKeyProvider.java (new)
│   ├── SecretsService.java (new)
│   ├── EnvironmentSecretsService.java (new)
│   ├── encryption/
│   │   ├── EncryptionService.java
│   │   ├── EncryptedStringConverter.java
│   │   └── Encrypted.java
│   ├── session/
│   │   ├── ActiveSession.java
│   │   ├── SessionService.java
│   │   └── TokenBlacklist.java
│   ├── lockout/
│   │   ├── AccountLockoutService.java
│   │   └── LockoutConfig.java
│   └── validation/
│       ├── InputSanitizer.java
│       ├── Sanitized.java
│       ├── SanitizationAspect.java
│       └── validators/
│           ├── NoHtml.java
│           ├── SafeString.java
│           └── ValidTenantSlug.java
├── token/
│   ├── RefreshToken.java
│   ├── RefreshTokenRepository.java
│   └── RefreshTokenService.java
├── permission/
│   ├── Permission.java
│   ├── RolePermission.java
│   ├── PermissionRepository.java
│   ├── PermissionService.java
│   ├── RequirePermission.java
│   └── PermissionCheckAspect.java
├── tenant/
│   ├── TenantContext.java (new)
│   ├── TenantContextFilter.java (new)
│   ├── TenantIsolationAspect.java (new)
│   └── TenantIsolated.java (new)
└── logging/
    ├── SecureLoggingFilter.java
    ├── SensitiveDataMasker.java
    └── RequestIdFilter.java

quickstack-core/src/main/resources/
├── application.properties (modified)
├── logback-spring.xml (new)
└── db/migration/
    ├── V003__create_audit_logs.sql
    ├── V004__create_jwt_keys.sql
    ├── V005__create_refresh_tokens.sql
    ├── V006__add_lockout_columns.sql
    ├── V007__add_encryption_metadata.sql
    ├── V008__create_permissions.sql
    └── V009__create_active_sessions.sql
```

---

**Document Version**: 1.0
**Created**: January 12, 2026
**Last Updated**: January 12, 2026
**Author**: Sprint Planning Team
