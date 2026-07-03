# SpringAdmin

A Spring Boot 3 / Java 21 port of [NodeAdmin](../NodeAdmin) — a full-stack admin panel with session-based web UI (Thymeleaf) and a stateless REST API (JWT), sharing the same database schema and RBAC permission model.

---

## Features

- **Dual authentication**: session-based web UI and stateless JWT API, side-by-side on the same application
- **RBAC access control**: role → permission → guard_name, enforced by `AccessInterceptor` on every authenticated route
- **HTTP Method Override**: `HiddenHttpMethodFilter` lets HTML forms tunnel `PUT` / `DELETE` via `_method` parameter
- **Modular architecture**: each feature lives in `modules/<module>/{entity,repository,service,controller,dto}` — add new modules without touching existing ones
- **Flyway migrations**: versioned SQL schema management, compatible with MySQL 8 and H2 (test)
- **Redis session store**: Spring Session backed by Redis for horizontal scalability; gracefully degrades in tests (in-memory mock)
- **JWT blacklisting**: logout revokes the token in Redis (or in-memory map for tests) with TTL equal to remaining token lifetime
- **BCrypt passwords**: configurable rounds via `app.bcrypt.rounds`
- **Rate limiting**: Bucket4j token-bucket per IP on auth endpoints
- **Input sanitisation**: OWASP HTML Sanitizer on all user-supplied rich-text input
- **File upload**: local-disk storage with MIME-type validation via Apache Tika
- **ArchUnit architecture tests**: enforced layer rules prevent architectural drift
- **Cucumber BDD smoke tests**: HTTP Method Override and verbose-path API scenarios
- **Module generator**: `scripts/make-module.sh` scaffolds a complete module in seconds

---

## Requirements

| Dependency | Version  |
|------------|----------|
| Java (Temurin recommended) | 21 |
| Maven (wrapper included) | 3.9+ |
| MySQL | 8.0 or 8.4 |
| Redis | 6+ (7 recommended) |

> **Multi-database note:** The production target is MySQL 8. For automated tests, H2 is used in MySQL-compatibility mode (`MODE=MySQL`) — no real MySQL or Redis is required to run the test suite locally. See [Testing](#testing).

---

## Quick Start

```bash
# 1. Clone
git clone https://github.com/your-org/SpringAdmin.git
cd SpringAdmin

# 2. Copy and fill environment variables (see table below)
cp .env.example .env          # optional — or export directly

# 3. Start MySQL and Redis (example with Docker)
docker run -d --name mysql8 -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=springadmin -e MYSQL_USER=springadmin \
  -e MYSQL_PASSWORD=springadmin -p 3306:3306 mysql:8.0

docker run -d --name redis7 -p 6379:6379 redis:7-alpine

# 4. Run the application
./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=default"
```

The application starts on `http://localhost:8080`.
Default admin credentials (seeded by Flyway V2): **admin@example.com** / **Admin1234!**

---

## Environment Variables

All configuration is bound through `AppProperties` (`app.*` prefix). Pass these as environment variables, system properties, or in `application.yml`.

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/springadmin` | JDBC URL for MySQL |
| `SPRING_DATASOURCE_USERNAME` | `root` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | _(empty)_ | DB password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `APP_JWT_SECRET` | _(must set)_ | Base64-encoded HMAC-SHA256 signing key (min 32 bytes) |
| `APP_JWT_EXPIRATION` | `86400` | Token lifetime in seconds (default: 24 h) |
| `APP_BCRYPT_ROUNDS` | `12` | BCrypt cost factor (use 4 in tests for speed) |
| `APP_STORAGE_ROOT` | `./storage` | Filesystem root for uploaded files |
| `APP_STORAGE_PROVIDER` | `local` | Storage backend (`local` only currently) |
| `APP_ENV` | `development` | Application environment (`development`, `production`) |
| `APP_SESSION_SECRET` | _(must set)_ | Secret for session cookie signing |

> **Security:** Never commit real secrets. In production, inject via your deployment platform's secret manager.

### Generating a JWT secret

```bash
# 32-byte random secret, Base64-encoded
openssl rand -base64 32
```

---

## Multi-DB Note

| Profile | Database | Redis | When to use |
|---------|----------|-------|-------------|
| `default` (no profile) | MySQL 8 | Required | Production / full local stack |
| `sqlite` | SQLite (file-based) | Not required | Local development tanpa MySQL/Redis |
| `test` | H2 in-memory (MySQL mode) | Mocked (in-memory map) | `./mvnw test` — no external services needed |
| `mysql-test` (CI only) | MySQL 8 via env vars | Required | GitHub Actions MySQL matrix job |

H2 runs Flyway migrations using the same SQL files, so schema divergence between test and production is caught early.

### Menjalankan dengan SQLite (tanpa MySQL & Redis)

Untuk development lokal tanpa perlu instalasi MySQL atau Redis:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=sqlite
```

Aplikasi berjalan di `http://localhost:8080`.
Default admin credentials (seeded oleh Flyway): **admin@example.com** / **Admin1234!**

---

## Testing

### Run all tests (H2, no external services required)

```bash
./mvnw test -Dspring.profiles.active=test
```

### Run only ArchUnit architecture tests

```bash
./mvnw test -Dtest=ArchitectureTest -Dspring.profiles.active=test
```

### Run only BDD (Cucumber) scenarios

```bash
./mvnw test -Dtest="**/bdd/**" -Dspring.profiles.active=test
```

### Run a specific test class

```bash
./mvnw test -Dtest=UserServiceTest -Dspring.profiles.active=test
./mvnw test -Dtest=AuthApiTest     -Dspring.profiles.active=test
```

### Test suite overview

| Class | Type | What it covers |
|-------|------|----------------|
| `ArchitectureTest` | ArchUnit | Layer isolation, repository interfaces, entity inheritance, no raw JDBC, no cross-module controller imports |
| `BaseIntegrationTest` | Abstract base | Shared MockMvc, helpers: `createTestUser`, `loginAsAdmin`, `getCsrfToken` |
| `UserServiceTest` | Integration | store/reject duplicate/update/delete/pagination with filters |
| `AuthApiTest` | Integration | Login → JWT, GET /me with token, logout then /me → 401 (real blacklist) |
| `DeleteMethodSmokeSteps` | Cucumber BDD | `_method=DELETE` form override; verbose API paths return 200 |

---

## Module Generator

The `scripts/make-module.sh` script scaffolds a complete module in one command.

### Usage

```bash
./scripts/make-module.sh ModuleName module_name
```

| Argument | Format | Example |
|----------|--------|---------|
| `ModuleName` | PascalCase | `Product` |
| `module_name` | snake_case | `product` |

### Example

```bash
./scripts/make-module.sh Product product
```

### Generated files

```
src/main/java/com/nodeadmin/modules/product/
  entity/ProductEntity.java          — JPA entity extending BaseEntity
  repository/ProductRepository.java  — Spring Data JPA interface
  service/IProductService.java       — service contract
  service/ProductService.java        — @Service implementation
  dto/ProductRequest.java            — validated DTO (@NotBlank, @Size)
  controller/web/v1/ProductWebController.java   — Thymeleaf CRUD controller
  controller/api/v1/ProductApiController.java   — REST JSON controller

src/main/resources/
  templates/modules/product/
    index.html   — paginated list with delete form (_method=DELETE)
    create.html  — create form
    edit.html    — edit form (_method=PUT)
  db/migration/V{N}__create_products_table.sql  — Flyway migration

src/test/java/com/nodeadmin/modules/product/
  ProductServiceTest.java            — integration tests (extends BaseIntegrationTest)
```

Routes registered follow the NodeAdmin `namedRoutes` convention:

| Name | Method | Path |
|------|--------|------|
| `admin.v1.product.index` | GET | `/admin/v1/product` |
| `admin.v1.product.create` | GET | `/admin/v1/product/create` |
| `admin.v1.product.store` | POST | `/admin/v1/product/store` |
| `admin.v1.product.edit` | GET | `/admin/v1/product/{id}/edit` |
| `admin.v1.product.update` | PUT | `/admin/v1/product/{id}/update` |
| `admin.v1.product.delete` | DELETE | `/admin/v1/product/{id}/delete` |
| `admin.v1.product.delete_selected` | POST | `/admin/v1/product/delete_selected` |

API paths mirror the above under `/api/v1/product/...`.

### After generation

1. Review and customise `ProductEntity.java` — add domain-specific columns.
2. Edit the Flyway migration SQL to match.
3. Register permissions in the DB seed or admin UI.
4. Run tests: `./mvnw test -Dspring.profiles.active=test`

---

## Architecture Overview

```
HTTP Request
  → HiddenHttpMethodFilter        (POST _method=PUT/DELETE → real verb)
  → SecurityFilterChain
      → apiFilterChain  (order 1) — /api/**  — stateless JWT
      → webFilterChain  (order 2) — /**      — session-based
  → AccessInterceptor             (RBAC: role → permission → guard_name)
  → Controller (@Controller / @RestController)
      → I*Service (interface)
          → *Service (@Service implementation)
              → *Repository (Spring Data JPA interface)
              → Entity (extends BaseEntity — audit columns)
      ← throws AppError subclass
  ← GlobalExceptionHandler
      → web: Thymeleaf view redirect with flash message
      → api: { status, message, data } JSON envelope
```

### Package layout

```
com.nodeadmin
  common/
    entity/BaseEntity.java          — audit columns for all entities
    error/                          — AppError, NotFoundError, ConflictError, …
    handler/GlobalExceptionHandler  — @ControllerAdvice
    model/SessionUser               — serialisable session principal
    response/ResponseHandler        — API response envelope builder
    route/RouteRegistry             — named-route registration (mirrors NodeAdmin)
    util/                           — PaginateResult, SanitizerUtil, UuidUtil, …
  config/
    AppProperties.java              — ALL config via this class (no process.env in modules)
    SecurityConfig.java             — dual filter chains (API + web)
    WebMvcConfig.java               — HiddenHttpMethodFilter, Thymeleaf interceptors
    security/
      JwtAuthenticationFilter       — extracts + validates Bearer token
      UserDetailsServiceImpl        — loads UserEntity for Spring Security
  modules/
    access/                         — users, roles, permissions
    auth/                           — login, logout, register, OTP reset
    dashboard/                      — admin dashboard
    profile/                        — user profile update
    setting/                        — application settings
    home/                           — public-facing FE templates
    media/                          — file upload/management
    components/                     — UI component showcase
```

### Architecture rules (enforced by ArchUnit)

| Rule | Description |
|------|-------------|
| Service implements interface | Every `*Service` class in `..service..` must implement `I*Service` |
| No `new Service()` in controllers | Controllers inject services — never instantiate directly |
| Services do not access controllers | Strict SoC — service layer is controller-agnostic |
| Repositories are interfaces | `*Repository` in `..repository..` must be a Java interface |
| No raw JDBC in service/controller | All DB access via JPA; `Statement`/`PreparedStatement` forbidden |
| Entities extend `BaseEntity` | All `@Entity` classes inherit audit columns |
| No cross-module controller imports | Controllers in different modules must not import each other |

---

## Contributing

1. **Read `AGENTS.md` first** — it is the authoritative rule document.
2. Branch from `main`: `git checkout -b feat/your-feature`
3. Follow the architecture rules — ArchUnit will catch violations at test time.
4. For new modules, use the generator: `./scripts/make-module.sh ModuleName module_name`
5. Tests must pass before opening a PR: `./mvnw test -Dspring.profiles.active=test`
6. Keep service methods throwing `AppError` subclasses — never return error objects.
7. All config goes through `AppProperties` — no `process.env` / `@Value` in module code.
8. Use constructor injection — no `@Autowired` field injection.

---

## License

MIT — see [LICENSE](LICENSE).
