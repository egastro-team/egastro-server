# eGastro Backend — Agent Context

## 1. What the application is

eGastro is a food e-commerce web platform. This repository is the **backend REST API** (Java, Spring Boot) of a university project that runs locally. A separate single-page frontend consumes it. Keep the code simple, readable and small: this is a clean CRUD application, not an enterprise system.

Customers register, log in, browse a product catalog (categories, pagination, sorting, text filter), keep a shopping cart on the server and place orders. Online payment does not exist: orders are placed without payment.

Its distinguishing feature is the **smart cart**: for the current contents of a user's cart the API returns *related products* (cross-sell recommendations, "customers who bought these also bought…"). The recommendations come from a separate **external recommendation service** that this backend calls over HTTP. That service is not part of this repository; the backend only follows a fixed HTTP contract and must fall back to a simple "most popular products" algorithm whenever the service fails. Recommendations are *contextual* (based on cart contents), never a personal taste profile — do not call them "personalized".

Roles: `USER` (customer) and `ADMIN` (manages products, categories and orders).

## 2. Tech stack (fixed — do not substitute or add heavy extras)

- Java 21, Maven (with Maven Wrapper `./mvnw`), Spring Boot 3.x (latest stable 3.x)
- spring-boot-starter-web, spring-boot-starter-validation, spring-boot-starter-data-jpa (Hibernate), spring-boot-starter-security, PostgreSQL driver
- JWT with **jjwt** (`io.jsonwebtoken:jjwt-api`, plus `jjwt-impl` and `jjwt-jackson` at runtime) and a custom `OncePerRequestFilter` — NOT the OAuth2 resource server
- springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) for API documentation
- **Lombok** for entities (`@Getter`, `@Setter`, `@NoArgsConstructor`; `@ToString`/`@EqualsAndHashCode` only with explicit field selection — never include lazy associations or secrets; entity equality on `id` only; never `@Data` on entities), Spring components (`@RequiredArgsConstructor`, `@Slf4j`) and exceptions. Java records for DTOs.
- Schema is created by Hibernate: `spring.jpa.hibernate.ddl-auto=update`. **No Flyway/Liquibase.**
- Not part of this project (do not add them): migrations tooling, soft deletes/archive flags, rate limiting, circuit breakers (Resilience4j), Actuator/Micrometer/Prometheus metrics, correlation-ID/MDC logging filters, login lockout, token revocation/refresh tokens, password reset, reports, exports, e-mail sending.

## 3. Package layout (standard layered structure)

Base package `com.egastro`. Packages are organized **by technical layer**, not by feature. Do not create feature packages (`catalog`, `cart`, …) and do not invent extra top-level packages.

```
com.egastro
├── EgastroApplication
├── config            Spring configuration beans: SecurityConfig, OpenApiConfig, RestClientConfig, AdminBootstrapRunner
│   └── properties    @ConfigurationProperties records: JwtProperties, CorsProperties, AdminProperties, RecommendationProperties
├── controller        REST controllers only (thin)
├── dto
│   ├── request       request records (with Bean Validation annotations)
│   └── response      response records (incl. PageResponse<T>)
├── entity            JPA entities
├── enums             Role, UnitOfMeasure, OrderStatus, ProductSort
├── event             Spring application events (ProductDeletedEvent)
├── exception         ApiException, ErrorResponse, FieldErrorItem, GlobalExceptionHandler
├── mapper            entity → DTO mappers (UserMapper, ProductMapper, CategoryMapper, CartMapper, OrderMapper)
├── repository        Spring Data repositories
├── security          JwtService, JwtAuthenticationFilter, CurrentUser, AuthenticatedUser, RestAuthenticationEntryPoint, RestAccessDeniedHandler
├── service           business logic: AuthService, CategoryService, ProductService, CartService, OrderService, AdminOrderService, SmartCartService
│   └── recommendation  RecommendationProvider (interface), MlRecommendationProvider, PopularProductsRecommendationProvider, ScoredRecommendation, RecommendationUnavailableException
└── util              small static helpers (PageParams)
```

Controllers: `AuthController`, `CategoryController`, `ProductController`, `AdminCategoryController`, `AdminProductController`, `CartController`, `RecommendationController`, `OrderController`, `AdminOrderController`. Repositories: `UserRepository`, `CategoryRepository`, `ProductRepository`, `CartRepository`, `CartItemRepository`, `OrderRepository`, `OrderItemRepository`.

Rules: naming uses the layer suffix (`…Controller`, `…Service`, `…Repository`, `…Request`, `…Response`, `…Mapper`, `…Properties`, `…Config`). Dependencies point one way: `controller → service → repository`; controllers never touch repositories or entities directly; services never depend on controllers; entities never depend on DTOs; entity → DTO conversion lives only in `mapper`.

## 4. Domain model

Use explicit `@Table(name = ...)` and `@Column(name = ...)` names in snake_case (they must match the SQL seed script). IDs are `Long` with `GenerationType.IDENTITY`. Enums are stored as strings (`@Enumerated(EnumType.STRING)`). Money is `BigDecimal` with scale 2 (no currency). Timestamps are `Instant`.

- **users** (`User`): id, email (unique, stored lowercase), password_hash, role (`USER`|`ADMIN`)
- **categories** (`Category`): id, name (unique, max 100)
- **products** (`Product`): id, name, description, category (ManyToOne, required), price numeric(12,2) > 0, unit_of_measure (`PIECE|KG|G|L|ML|PACK`), image_url (URL string only, no uploads), available (boolean; false = "out of stock")
- **carts** (`Cart`): id, user (OneToOne, unique); **cart_items** (`CartItem`): id, cart (ManyToOne), product (ManyToOne), quantity; unique (cart, product)
- **orders** (`Order`): id, user (ManyToOne), status (`NEW|CONFIRMED|COMPLETED|CANCELLED`), total_amount, delivery_address, contact_phone, comment, created_at; **order_items** (`OrderItem`): id, order (ManyToOne), product_id (plain `Long`, NO foreign key — it is a snapshot reference so products can be hard-deleted), product_name, unit_price, quantity

Products and categories are **hard-deleted** (`deleteById`); there are no archive/soft-delete flags anywhere.

## 5. API surface

| Method & path | Access | Purpose |
|---|---|---|
| POST /api/auth/register | public | register (role USER) |
| POST /api/auth/login | public | log in, receive JWT |
| GET /api/auth/me | authenticated | current user |
| GET /api/categories | public | list categories |
| GET /api/products?category=&q=&sort=&page=&size= | public | paged catalog |
| GET /api/products/{id} | public | product details |
| POST /api/admin/products, PUT/DELETE /api/admin/products/{id} | ADMIN | product CRUD |
| POST /api/admin/categories, PUT/DELETE /api/admin/categories/{id} | ADMIN | category CRUD |
| GET /api/cart, POST /api/cart/items, PUT/DELETE /api/cart/items/{productId} | authenticated | server-side cart |
| GET /api/cart/recommendations?limit= | authenticated | smart cart: related products |
| POST /api/orders, GET /api/orders, GET /api/orders/{id}, POST /api/orders/{id}/cancel | authenticated | customer orders |
| GET /api/admin/orders, GET /api/admin/orders/{id}, PATCH /api/admin/orders/{id}/status | ADMIN | order management |

Everything is authenticated except register, login, `GET /api/categories`, `GET /api/products`, `GET /api/products/{id}` and API-docs paths. `/api/admin/**` requires role ADMIN.

## 6. Conventions

**Errors.** All errors use one JSON body, produced by a single `@RestControllerAdvice` (and by the security entry point/access-denied handler for 401/403):
```json
{ "timestamp": "2025-01-01T12:00:00Z", "status": 400, "message": "Validation failed", "path": "/api/auth/register",
  "fieldErrors": [ { "field": "password", "message": "Password must contain at least one digit" } ] }
```
`fieldErrors` is omitted when empty. Business errors are thrown as `ApiException(HttpStatus, message, optional fieldErrors)` from services. Mapping: bean-validation failures and malformed JSON / bad parameter types → 400 (`"Validation failed"` / `"Malformed request body"`); missing authentication → 401 `"Authentication required"`; wrong role → 403 `"Access denied"`; unknown route → 404; business conflicts → 409; anything unexpected → 500 `"Unexpected error"` (log it, never leak stack traces or SQL).

**Pagination.** Page numbers are 0-based; `page` default 0 (negative → 0), `size` default 20 (clamped to 1–100). Paged responses use `{ "content": [...], "page": 0, "size": 20, "totalElements": 135, "totalPages": 7 }` (`PageResponse<T>`). Always sort deterministically (add `id` as the last sort key).

**Security.** Stateless JWT bearer auth, passwords hashed with BCrypt only. Passwords, hashes and tokens never appear in responses or logs (DTO records with a password override `toString`; entities use `@ToString.Exclude` on secrets). There is no server-side logout: the client discards its token.

**Persistence.** Entities are never returned from controllers — use DTO records. Avoid N+1 queries (join fetch / entity graph). `@Transactional` on service methods that write. No business logic in controllers or repositories.

**Validation.** Trim string inputs; use Bean Validation annotations on request records plus service-level checks.

**Configuration.** All tunables are in a single `application.yml` under the `egastro.*` prefix, bound with `@ConfigurationProperties` records; datasource from env `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` with local defaults (`jdbc:postgresql://localhost:5432/egastro`, `egastro`, `egastro`). One profile only (no dev/prod split).

**Documentation.** Add springdoc `@Tag` on controllers and a short `@Operation(summary = ...)` per endpoint; do not spend effort on exhaustive response annotations.

## 7. Design principles (SOLID, applied pragmatically)

- **Single responsibility.** Controllers only map HTTP ↔ DTOs and delegate; services hold business rules and transactions; repositories only access data; entity → DTO conversion lives in the `mapper` package, not in entities or controllers. A class that grows beyond one clear job (roughly > 200 lines or > 5 collaborators) must be split.
- **Open/closed.** Variation points are expressed as types, not `switch`/`if` chains: e.g. product sort options are an enum that carries its own `Sort`; recommendation sources are implementations of one interface. Adding a new option or source must not require editing existing logic.
- **Liskov.** Every implementation of an interface honors its documented contract (exceptions, nullability, ordering) so callers never need `instanceof` checks.
- **Interface segregation.** Keep interfaces small and focused (one or two methods); DTOs are specific per use case (do not reuse a request record as a response).
- **Dependency inversion.** Depend on abstractions only at real seams — currently the recommendation sources (`RecommendationProvider`). Do NOT create an interface for every service or repository (no `FooService` + `FooServiceImpl` pairs); concrete Spring beans injected by constructor are fine elsewhere. A service calls other domains' services (or publishes Spring events) instead of using their repositories; direct repository use across domains is acceptable only for read-only queries that belong to the calling use case.

## 8. Rules for working on a task

1. Read this file, then inspect the existing code before writing anything; reuse existing classes (`ApiException`, `PageResponse`, `CurrentUser`, DTOs, …).
2. Implement only the scope of the current task; do not change earlier tasks except where the task says so.
3. Write tests for all new logic and update relevant tests when changing existing behavior. Follow `.github/docs/TESTING-CONVENTIONS.md`; cover successful behavior, validation boundaries, and error paths, including successful and failed HTTP responses for new endpoints.
4. When done, the project must compile (`./mvnw -q compile`), all new and existing tests must pass (`./mvnw -q test`), and the application must start against the local PostgreSQL.
5. Whenever the API contract changes (adding, editing, or removing endpoints; changing methods, paths, parameters, request/response fields, validation, status codes, errors, authentication, roles, or required headers), update `RUN.md` in the same task. Keep it aligned with the implemented behavior and remove obsolete examples. Provide Bruno-friendly, copyable URLs and JSON bodies, explicitly list required headers (or state that none are required), explain token/ID variable setup, and include success and failure paths with expected status codes and responses. Update startup, configuration, and seed instructions when the changes affect how these requests are run.
6. Finish with a short summary: files added/changed, new endpoints, new configuration keys, assumptions made.

### Testing
When generating or modifying test code for this project, you **must strictly adhere** to our internal automation standards.

*   **Core Rules:** Tests must be based on JUnit 5 + Mockito, cover all edge cases/exceptions, and follow the Given-When-Then pattern. Controller integration tests must validate both successful and failed HTTP responses.
*   **Full Specifications & Code Templates:** All technical requirements, method naming conventions, and structural architecture examples are detailed in a separate document.

**Read full rules here: [Project Testing Conventions](.github/docs/TESTING-CONVENTIONS.md)**

*Note: Before generating any test class, you must read the file linked above and structure the code exactly according to the provided templates.*
