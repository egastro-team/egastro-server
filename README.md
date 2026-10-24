# eGastro Backend — Implementation Prompts (Java / Spring Boot) — Task prompts

## Running locally

Requires Java 21 and Docker Compose. Start PostgreSQL with:

```sh
docker compose up -d
```

SQL scripts in `docker/init/` run only when the database data volume is empty.
To re-seed, run `docker compose down -v` (this deletes the local database), then
`docker compose up -d` again.

Run the application with:

```sh
./mvnw spring-boot:run
```

On Windows PowerShell, use `./mvnw.cmd spring-boot:run`. The database defaults to
`jdbc:postgresql://localhost:5432/egastro`, with user and password `egastro`.
Override these with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` if needed.
The application uses UTC as its JVM default and Hibernate JDBC timezone.
Swagger UI is available at `http://localhost:8080/swagger-ui.html` and the OpenAPI
document at `http://localhost:8080/v3/api-docs`.

Authentication endpoints: `POST /api/auth/register` creates a customer (201, no
token), `POST /api/auth/login` returns a bearer token, and `GET /api/auth/me`
returns the current user with `Authorization: Bearer <token>`.

JWT settings are `egastro.security.jwt.secret` (`JWT_SECRET`, a local placeholder
by default) and `egastro.security.jwt.ttl` (default `PT24H`). CORS origins are in
`egastro.security.cors.allowed-origins` (localhost ports 3000 and 5173 by default).
The initial admin is configured by `egastro.admin.email` / `egastro.admin.password`
(`ADMIN_EMAIL` / `ADMIN_PASSWORD`, defaults `admin@egastro.local` / `Admin12345`).
Startup creates this admin only when the email does not already exist.

Emails are trimmed and stored lowercase. Passwords are preserved exactly;
registration requires 8–72 characters and a digit. BCrypt also limits UTF-8
passwords to 72 bytes, so multi-byte passwords may reach that limit sooner.

## T01 — Project bootstrap and core infrastructure

````markdown
Task T01 — Project bootstrap and core infrastructure.
Prerequisites: none (empty repository). AGENTS.md exists and must be followed.
 
1. Maven project: groupId `com.egastro`, artifactId `egastro-backend`, Java 21, Spring Boot parent (latest stable 3.x), Maven Wrapper committed. Dependencies: spring-boot-starter-web, spring-boot-starter-validation, spring-boot-starter-data-jpa, postgresql (runtime), springdoc-openapi-starter-webmvc-ui, lombok (optional/provided, configured as annotation processor; excluded from the repackaged jar). Do NOT add Flyway, Actuator or any other dependency. Main class `com.egastro.EgastroApplication`; enable `@ConfigurationPropertiesScan`. Add a `lombok.config` in the project root: `config.stopBubbling = true`, `lombok.addLombokGeneratedAnnotation = true`.
 
2. `src/main/resources/application.yml`: datasource from env `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (defaults `jdbc:postgresql://localhost:5432/egastro`, `egastro`, `egastro`); `spring.jpa.hibernate.ddl-auto: update`; `spring.jpa.open-in-view: false`; `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`; Jackson: ISO-8601 dates (`write-dates-as-timestamps: false`), `fail-on-unknown-properties: false`, `default-property-inclusion: non_null`; `server.error.include-stacktrace: never`.
 
3. Docker Compose for the local database: `docker-compose.yml` in the project root with a single `postgres:16` service named `db` — database/user/password `egastro`, port `5432:5432`, a named volume for data, and the directory `./docker/init` mounted read-only to `/docker-entrypoint-initdb.d`. Create `docker/init/` (with a `.gitkeep`); later tasks put their SQL scripts there. Add a short `README.md` section: `docker compose up -d` starts the database; init scripts run only when the data volume is empty (re-seed with `docker compose down -v`); run the app with `./mvnw spring-boot:run`.
 
4. Create the packages from AGENTS.md section 3 that this task needs and put the classes in them:
   - package `exception`: `FieldErrorItem(String field, String message)` and `ErrorResponse(Instant timestamp, int status, String message, String path, List<FieldErrorItem> fieldErrors)` records (`fieldErrors` omitted from JSON when null/empty), with a static factory and a helper that writes an `ErrorResponse` as JSON to an `HttpServletResponse` (the security handlers in the next task use it).
   - package `exception`: `ApiException extends RuntimeException` (Lombok `@Getter`): `HttpStatus status`, `List<FieldErrorItem> fieldErrors`; static factories `notFound(String)`, `conflict(String)`, `badRequest(String)`, `validation(String field, String message)`.
   - package `exception`: `GlobalExceptionHandler` (`@RestControllerAdvice`): `ApiException` → its status/message/fieldErrors; `MethodArgumentNotValidException` and `ConstraintViolationException` → 400 `"Validation failed"` with one field error per violation (sorted by field); `HttpMessageNotReadableException` → 400 `"Malformed request body"`; `MethodArgumentTypeMismatchException` / `MissingServletRequestParameterException` → 400 `"Validation failed"` with the parameter name as the field; `NoResourceFoundException` → 404 `"Resource not found"`; `HttpRequestMethodNotSupportedException` → 405; `DataIntegrityViolationException` → 409 `"Data conflict"`; any other `Exception` → log with stack trace, 500 `"Unexpected error"`. Spring Security exceptions (`AccessDeniedException`, `AuthenticationException`) must NOT be swallowed — rethrow them so Spring Security handles them.
   - package `dto.response`: `PageResponse<T>` record `(List<T> content, int page, int size, long totalElements, int totalPages)` with static `from(Page<T>)`. Package `util`: a tiny final helper `PageParams.of(int page, int size, Sort sort)` returning a `Pageable` (negative page → 0, size clamped to 1–100).
   - package `config`: `OpenApiConfig`: springdoc `OpenAPI` bean with title "eGastro API", version "1.0" and a global HTTP bearer JWT security scheme named `bearerAuth`.
 
5. The application must start against the empty local database (`docker compose up -d`).
 
Out of scope: security, entities, business endpoints.
````

## T02 — Light security and user management

````markdown
Task T02 — Security (jjwt filter), users, registration, login.
Prerequisites: T01 completed (ApiException, ErrorResponse + JSON writer helper, GlobalExceptionHandler, OpenApiConfig, application.yml).
 
1. Dependencies: `spring-boot-starter-security`, `io.jsonwebtoken:jjwt-api` + runtime `jjwt-impl` and `jjwt-jackson` (0.12.x). Do not add the OAuth2 resource server.
 
2. Config `egastro.security.jwt.secret` (env `JWT_SECRET`, local default: a long placeholder string of at least 32 bytes) and `egastro.security.jwt.ttl` (`Duration`, default `PT24H`), and `egastro.security.cors.allowed-origins` (list, default `http://localhost:3000,http://localhost:5173`), bound with `@ConfigurationProperties` records in package `config.properties` (`JwtProperties`, `CorsProperties`, `AdminProperties`).
 
3. Users: `Role` enum `USER, ADMIN` in package `enums`; `User` entity in package `entity` (table `users`: `id`, `email` unique, `password_hash`, `role`; Lombok per AGENTS.md, `@ToString` must exclude `passwordHash`); `UserRepository` in package `repository` with `Optional<User> findByEmailIgnoreCase(String)` and `boolean existsByEmailIgnoreCase(String)`.
   - `AdminBootstrapRunner` in package `config` (`ApplicationRunner`): properties `egastro.admin.email` / `egastro.admin.password` (env `ADMIN_EMAIL`, `ADMIN_PASSWORD`; defaults `admin@egastro.local` / `Admin12345`). If no user with that email exists, create it with role `ADMIN` and a BCrypt hash; if it exists do nothing. Never log the password.
 
4. Security — classes in package `security` unless noted:
   - `PasswordEncoder` bean (`BCryptPasswordEncoder`), declared in `config.SecurityConfig`.
   - `JwtService` using jjwt: `String generateToken(User user)` — HS256 with `Keys.hmacShaKeyFor(secret bytes)`, `subject` = user id, claims `email` and `role`, `issuedAt`, `expiration` = now + ttl; `Optional<AuthenticatedUser> parse(String token)` returning empty on any invalid/expired/malformed token (never throws). `AuthenticatedUser(Long id, String email, Role role)` record.
   - `JwtAuthenticationFilter extends OncePerRequestFilter`: reads `Authorization: Bearer <token>`; if the token parses, puts a `UsernamePasswordAuthenticationToken` (principal = `AuthenticatedUser`, authority `ROLE_<role>`) into the `SecurityContext`; otherwise continues the chain without authentication.
   - `CurrentUser` helper: `static Long id()` (throws `ApiException` 401 `"Authentication required"` if absent), `static AuthenticatedUser get()`.
   - `SecurityConfig` in package `config` (`SecurityFilterChain`): CSRF disabled; stateless sessions; CORS from the config (methods GET, POST, PUT, PATCH, DELETE, OPTIONS; headers Authorization, Content-Type; no credentials); the JWT filter added before `UsernamePasswordAuthenticationFilter`; rules in this order: permitAll for `OPTIONS /**`, `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/categories`, `GET /api/products`, `GET /api/products/*`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`; `/api/admin/**` → `hasRole("ADMIN")`; every other request authenticated. Custom `RestAuthenticationEntryPoint` (401, message `Authentication required`) and `RestAccessDeniedHandler` (403, message `Access denied`) in package `security`, writing the standard `ErrorResponse` JSON.
 
5. Auth API — `AuthController` (package `controller`, base path `/api/auth`, thin) delegating to `AuthService` (package `service`: `register`, `login`, `me`); request records `RegisterRequest`, `LoginRequest` in `dto.request`; response records `UserResponse`, `LoginResponse` in `dto.response`; `UserMapper` in package `mapper`:
   - `POST /register` body `RegisterRequest(String email, String password)`. `email`: required, trimmed, max 254, valid format ("Invalid email format"; required → "Email is required"). `password`: required, 8–72 characters ("Password must be at least 8 characters long" / "Password must be at most 72 characters long"), at least one digit ("Password must contain at least one digit"); one field error per violated rule, all on `password`. Normalize email (trim + lower-case), role always `USER` (ignore any `role` in the payload). Duplicate email (check `existsByEmailIgnoreCase`, also translate a unique-constraint violation) → 409 with message exactly `A user with this email already exists`. Success → 201 with `UserResponse(Long id, String email, String role)`; no token, no password/hash in any response.
   - `POST /login` body `LoginRequest(String email, String password)` (both required, blank → 400). Unknown email or wrong password → 401 with the same message exactly `Incorrect email or password`. Success → 200 `LoginResponse(String accessToken, String tokenType = "Bearer", long expiresIn /* seconds */, UserResponse user)`. No lockout or attempt counting.
   - `GET /me` (authenticated) → 200 `UserResponse` of the current user (404 `"User not found"` if missing).
   - Both request records override `toString` so the password is never printed; never log passwords or tokens.
 
Out of scope: logout endpoint (client discards the token), refresh tokens, password reset, lockout, rate limiting.
````

## T03 — Catalog: public browsing and admin CRUD with hard deletes

````markdown
Task T03 — Catalog (categories + products), public browsing, admin CRUD, SQL seed script.
Prerequisites: T01, T02 completed (error model, PageResponse/PageParams, security rules already permit the public GET endpoints and require ADMIN for `/api/admin/**`; Docker Compose mounts `./docker/init` into the database container).
 
Classes go into the layer packages from AGENTS.md section 3: `entity` (`Category`, `Product`), `enums` (`UnitOfMeasure`, `ProductSort`), `repository` (`CategoryRepository`, `ProductRepository`), `dto.request` (`CategoryRequest`, `ProductRequest`), `dto.response` (`CategoryResponse`, `ProductResponse`), `mapper` (`CategoryMapper`, `ProductMapper`), `service` (`CategoryService`, `ProductService`), `controller` (`CategoryController` and `ProductController` for the public endpoints, `AdminCategoryController` and `AdminProductController` for the admin endpoints), `event` (`ProductDeletedEvent`).
 
**Entities and repositories** (explicit snake_case table/column names; Lombok per AGENTS.md; no archived/soft-delete flags anywhere)
- `UnitOfMeasure` enum: `PIECE, KG, G, L, ML, PACK`.
- `Category` (`categories`): `id`, `name` varchar(100) unique (case-insensitive uniqueness is enforced in the service).
- `Product` (`products`): `id`, `name` varchar(255), `description` text, `category` (`@ManyToOne(fetch = LAZY)`, column `category_id`, required), `price` numeric(12,2), `unit_of_measure` varchar(20) (`EnumType.STRING`), `image_url` varchar(1000), `available` boolean.
- `CategoryRepository` (`existsByNameIgnoreCase`, `existsByNameIgnoreCaseAndIdNot`), `ProductRepository` (`existsByCategoryId`, plus what the queries below need).
 
**DTOs** (records): `CategoryResponse(Long id, String name)`; `ProductResponse(Long id, String name, String description, BigDecimal price, UnitOfMeasure unitOfMeasure, String imageUrl, boolean available, Long categoryId, String categoryName)` — one DTO used for list, details and admin responses (this DTO is reused by later tasks, converted by `ProductMapper.toResponse(Product)`); `CategoryRequest(String name)`; `ProductRequest`.
 
**Public endpoints**
- `GET /api/categories` → array of `CategoryResponse` sorted by name (case-insensitive), then id.
- `GET /api/products?category=&q=&sort=&page=&size=` → `PageResponse<ProductResponse>`:
  - `category` (optional id): only that category's products; unknown id → 404 `Category not found`; an existing category without products → 200 with empty `content`.
  - `q` (optional): trimmed, blank ignored, max 100 chars (longer → 400); case-insensitive "contains" match on product name; `%`, `_`, `\` are matched literally (escape them).
  - `sort`: `name_asc` (default), `name_desc`, `price_asc`, `price_desc`; anything else → 400 with a field error on `sort`. Implement this as an enum `ProductSort` whose constants carry their request value and their Spring Data `Sort` (name sorts case-insensitive), with a static `fromParam(String)` lookup — no `switch`/`if` chain in the service or controller. `id` ascending is always the final tie-break (appended by the enum) so pages never overlap or skip.
  - Products with `available = false` are returned normally (the frontend shows "Out of stock"). Fetch the category together with the product (join fetch / entity graph) — no N+1 — and use a correct count query. Page/size per AGENTS.md.
- `GET /api/products/{id}` → `ProductResponse`; unknown id → 404 `Product not found`; non-numeric id → 400.
 
**Admin endpoints** (`/api/admin/...`, role ADMIN; USER → 403, anonymous → 401)
- `POST /api/admin/products` → 201 + `Location` + `ProductResponse`; `PUT /api/admin/products/{id}` → 200 `ProductResponse` (full replacement); unknown id → 404 `Product not found`.
  Validation on `ProductRequest`: `name` required/not blank/max 255; `description` optional/max 5000; `categoryId` required and must exist (else 400 with field error `categoryId`: "Category does not exist"); `price` required, > 0, at most 2 decimals, max 9999999999.99 ("Price must be greater than 0"); `unitOfMeasure` required, valid enum value; `imageUrl` optional, absolute `http://` or `https://` URL, max 1000 chars; `available` optional, default `true`. Errors → 400 with one field error per problem, nothing saved. Changes are visible immediately in the public endpoints.
- `DELETE /api/admin/products/{id}` → 204, a real hard delete via `repository.deleteById`; unknown id → 404. Cart rows referencing the product must not block the delete: implement the delete in `ProductService.delete(Long id)` (`@Transactional`), which first publishes a Spring event `ProductDeletedEvent(Long productId)` via `ApplicationEventPublisher` and then calls `deleteById` (the cart task adds a listener that removes the product's cart items; until then the event simply has no listeners). `order_items` never block the delete (no foreign key there).
- `POST /api/admin/categories` → 201 + `Location` + `CategoryResponse`; `PUT /api/admin/categories/{id}` → 200; `DELETE /api/admin/categories/{id}` → 204, hard delete, rejected with 409 `Category contains products and cannot be deleted` when any product references it; unknown id → 404 `Category not found`. `name` required, trimmed, 1–100 chars, unique case-insensitively → 409 `A category with this name already exists` (renaming to its own current name is allowed).
 
**SQL seed script** — create `docker/init/01-catalog-seed.sql` (executed by the PostgreSQL container on first start, i.e. BEFORE the application creates its tables, so it must create the two catalog tables itself):
- `CREATE TABLE IF NOT EXISTS categories (id bigint generated by default as identity primary key, name varchar(100) not null unique)` and `CREATE TABLE IF NOT EXISTS products (id bigint generated by default as identity primary key, name varchar(255) not null, description text, category_id bigint not null references categories(id), price numeric(12,2) not null, unit_of_measure varchar(20) not null, image_url varchar(1000), available boolean not null)` — column names/types exactly matching the entities so Hibernate's `update` leaves them alone.
- Insert 6 categories: `Dairy`, `Bakery`, `Fruits & Vegetables`, `Meat & Fish`, `Beverages`, `Snacks & Sweets`.
- Insert at least 48 realistic grocery products spread across them (milk, kefir, cheese, butter, yogurt, white and rye bread, croissant, apples, bananas, tomatoes, potatoes, chicken fillet, salmon, minced pork, mineral water, orange juice, coffee, tea, chips, chocolate, cookies, …), each with a short description, a plausible price, a fitting `unit_of_measure` (`PIECE|KG|G|L|ML|PACK`), `image_url` of the form `https://placehold.co/400x300?text=<URL-encoded name>`, `available = true` except for 3 products with `false`. Reference categories via `(select id from categories where name = '...')` instead of hard-coded ids.
 
Add `@Tag`/`@Operation(summary)` annotations. Out of scope: price filters, image upload, cart/order handling.
````

## T04 — Backend-backed shopping cart

````markdown
Task T04 — Server-side shopping cart.
Prerequisites: T01–T03 completed (User, Product (fields `available`, `price`, `unitOfMeasure`, `imageUrl`), ProductRepository, `ProductDeletedEvent(Long productId)` published by `ProductService.delete` before a product is deleted, `CurrentUser.id()`, error model). All cart endpoints require authentication.
 
Classes go into the layer packages from AGENTS.md section 3: `entity` (`Cart`, `CartItem`), `repository` (`CartRepository`, `CartItemRepository`), `dto.request` (`AddCartItemRequest`, `UpdateCartItemRequest`), `dto.response` (`CartResponse`, `CartItemResponse`), `mapper` (`CartMapper`), `service` (`CartService`), `controller` (`CartController`).
 
**Entities** (explicit snake_case names, Lombok per AGENTS.md): `Cart` (`carts`: `id`, `user` OneToOne unique, column `user_id`); `CartItem` (`cart_items`: `id`, `cart` ManyToOne, `product` ManyToOne, `quantity` int; unique constraint on `(cart_id, product_id)`). Repositories with the methods you need (find cart by user id, find item by cart and product id, `deleteByProductId`).
 
**Lifecycle**: a user's cart is created lazily — when the cart is requested and none exists, create an empty `Cart` row for the user inside the same service call (so `GET /api/cart` always returns a cart).
 
**Response DTOs** (records): `CartResponse(List<CartItemResponse> items, int totalQuantity, BigDecimal totalAmount)` and `CartItemResponse(Long productId, String name, String imageUrl, BigDecimal unitPrice, UnitOfMeasure unitOfMeasure, int quantity, BigDecimal lineTotal, boolean available)`. All totals are calculated on the fly in Java when mapping entities to the DTO (`unitPrice` = the product's current price, `lineTotal = unitPrice × quantity`, `totalQuantity` = sum of quantities, `totalAmount` = sum of line totals); nothing is stored. Items are ordered by item id ascending. Load products together with items in one query (no N+1).
 
**Endpoints** (every mutating call returns 200 with the updated `CartResponse`):
- `GET /api/cart`.
- `POST /api/cart/items` body `{ "productId": long, "quantity": int }`, both required, `quantity` 1–99. If the product is already in the cart the quantities are added; a resulting quantity above 99 → 400 with field error `quantity`: "Maximum quantity per product is 99". Unknown product → 404 `Product not found`; product with `available = false` → 409 `Product is currently unavailable`.
- `PUT /api/cart/items/{productId}` body `{ "quantity": int }` (1–99) sets the quantity; item not in the cart → 404 `Item not found in cart`.
- `DELETE /api/cart/items/{productId}` removes the item (not being in the cart is not an error).
 
**Product deletion hook**: add a method in `CartService` annotated `@EventListener` for `ProductDeletedEvent` (package `event`; same transaction) that deletes all `cart_items` of that product, so hard-deleting a product never fails because of carts.
 
**For later tasks** expose in `CartService`: a method returning the user's cart items as `(Product, quantity)` pairs without creating a cart, and a method that deletes all items of a user's cart.
 
Add `@Tag`/`@Operation(summary)` annotations. Out of scope: guest carts, stock counts, a "clear cart" endpoint (checkout clears the cart).
````

## T05 — Checkout and order management

````markdown
Task T05 — Orders: checkout from the cart, customer order history, admin management.
Prerequisites: T01–T04 completed (CartService: read the user's cart items as `(Product, quantity)` pairs and delete all items of a user's cart; Product; User; `CurrentUser.id()`; PageResponse and `util.PageParams`; error model). No online payment exists.
 
Classes go into the layer packages from AGENTS.md section 3: `enums` (`OrderStatus`), `entity` (`Order`, `OrderItem`), `repository` (`OrderRepository`, `OrderItemRepository` — the latter is used by the next task for a popularity query, create it here with the methods this task needs), `dto.request` (`CreateOrderRequest`, `UpdateOrderStatusRequest`), `dto.response` (`OrderResponse`, `OrderItemResponse`, `OrderSummaryResponse`, `AdminOrderResponse`, `AdminOrderSummaryResponse`), `mapper` (`OrderMapper`), `service` (`OrderService` for checkout and the customer's own orders, `AdminOrderService` for the admin operations), `controller` (`OrderController`, `AdminOrderController`).
 
**Entities** (explicit snake_case names, Lombok per AGENTS.md)
- `OrderStatus` enum (package `enums`): `NEW, CONFIRMED, COMPLETED, CANCELLED`.
- `Order` (table `orders`; the class name `Order` is a reserved word — always map to `orders`): `id`, `user` (ManyToOne lazy, `user_id`), `status`, `total_amount` numeric(12,2), `delivery_address` varchar(500), `contact_phone` varchar(20), `comment` varchar(1000) nullable, `created_at` (`Instant`, set on creation with `@CreationTimestamp` or in the service), `items` (`@OneToMany(mappedBy = "order", cascade = ALL, orphanRemoval = true)`).
- `OrderItem` (`order_items`): `id`, `order` (ManyToOne), `product_id` (plain `Long` column — NO foreign key, so products can be hard-deleted without touching order history), `product_name`, `unit_price` numeric(12,2), `quantity` int.
 
**DTOs** (records): `CreateOrderRequest(String deliveryAddress, String contactPhone, String comment)`; `OrderItemResponse(Long productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal)` (`lineTotal` computed in Java); `OrderResponse(Long id, OrderStatus status, BigDecimal totalAmount, String deliveryAddress, String contactPhone, String comment, Instant createdAt, List<OrderItemResponse> items)`; `OrderSummaryResponse(Long id, OrderStatus status, BigDecimal totalAmount, int itemsCount, Instant createdAt)`; for admins `AdminOrderResponse` (= `OrderResponse` + `userId`, `userEmail`) and `AdminOrderSummaryResponse` (= `OrderSummaryResponse` + `userId`, `userEmail`).
 
**Customer endpoints** (authenticated)
- `POST /api/orders` → 201 + `Location: /api/orders/{id}` + `OrderResponse`. Validation: `deliveryAddress` required, trimmed, 1–500 chars; `contactPhone` required — after removing spaces, dashes and parentheses it must match `^\+?[0-9]{10,15}$` (store the cleaned value; message "Invalid phone number"); `comment` optional, ≤ 1000 chars. In ONE `@Transactional` method: read the user's cart; empty cart → 409 `Cart is empty`; if any cart product has `available = false` → 409 `Some products are unavailable: <comma-separated product names>` and nothing is created; otherwise create the `Order` (status `NEW`) with `OrderItem` snapshot rows (product id, name, current price, quantity), compute `total_amount`, then delete all of the user's cart items.
- `GET /api/orders?page=&size=` → `PageResponse<OrderSummaryResponse>` of the current user's orders, newest first (`created_at desc, id desc`); compute `itemsCount` without N+1 (e.g. a count/sum query or a `@Formula`-free projection).
- `GET /api/orders/{id}` → `OrderResponse` (items fetched with the order); an order that does not exist or belongs to another user → 404 `Order not found`.
- `POST /api/orders/{id}/cancel` → 200 `OrderResponse` with status `CANCELLED`; only allowed while status is `NEW`, else 409 `Order can no longer be cancelled`; same 404 rule.
 
**Admin endpoints** (role ADMIN)
- `GET /api/admin/orders?status=&page=&size=` → `PageResponse<AdminOrderSummaryResponse>`; optional `status` filter (invalid value → 400 with a field error); newest first; fetch the user together with the order (no N+1, no collection fetch-join combined with paging).
- `GET /api/admin/orders/{id}` → `AdminOrderResponse`; unknown id → 404 `Order not found`.
- `PATCH /api/admin/orders/{id}/status` body `{ "status": "CONFIRMED" }` (required, valid enum) → 200 `AdminOrderResponse`. `COMPLETED` and `CANCELLED` are final: changing the status of such an order → 409 `Cannot change status of a COMPLETED/CANCELLED order` (use the actual current status in the message); setting the same status is also a 409 `Order already has status X`. Any other change among the four statuses is allowed. A plain transaction is enough (no locking).
 
Add `@Tag`/`@Operation(summary)` annotations. Out of scope: payments, delivery tracking, reports.
````

## T06 — Smart cart: recommendation service integration with fallback

````markdown
Task T06 — Smart cart: related products for the cart (external service + fallback).
Prerequisites: T01–T05 completed (CartService returning the user's cart items as `(Product, quantity)` pairs without creating a cart, `ProductResponse` DTO and `ProductMapper`, ProductRepository, `OrderItem` entity and `OrderItemRepository` (columns `product_id`, `quantity`; `Order.status`), `CurrentUser.id()`, error model).
 
Classes go into the layer packages from AGENTS.md section 3: `config.properties` (`RecommendationProperties`), `config` (`RestClientConfig` with a `RestClient` bean for the external service), `service.recommendation` (`RecommendationProvider`, `MlRecommendationProvider`, `PopularProductsRecommendationProvider`, `ScoredRecommendation`, `RecommendationUnavailableException`), `service` (`SmartCartService`), `dto.response` (`CartRecommendationsResponse`, `RecommendedProductResponse`, `RuleMetrics`), `controller` (`RecommendationController` exposing `GET /api/cart/recommendations`); the popularity aggregate query is added to the existing `OrderItemRepository`. Keep it simple: no circuit breakers, no metrics, no custom thread pools — just a `RestClient` call wrapped in a plain try/catch.
 
**Config** (`@ConfigurationProperties("egastro.recommendations")` record, defaults in `application.yml`): `ml.base-url` (env `ML_SERVICE_URL`, default `http://localhost:8000`; blank = skip the external call), `ml.connect-timeout` (`PT0.5S`), `ml.read-timeout` (`PT2S`), `default-limit` (5), `max-limit` (20).
 
**External service contract** (follow exactly; JSON property names are snake_case)
- `POST {base-url}/v1/recommendations`, `Content-Type: application/json`.
- Request: `{ "cart_item_ids": [12, 45, 78], "max_k": 10 }` — distinct product ids (at most 50; if the cart has more, send the first 50), `max_k` between 1 and 50.
- Response 200:
```json
{ "recommendations": [
  { "product_id": 101, "score": 3.42, "metrics": { "support": 0.012, "confidence": 0.41, "lift": 3.42 } }
] }
```
  Higher `score` = more relevant. Entries may be unsorted, may contain products already in the cart or unknown ids, and `metrics` may be missing/partially null. Ignore entries with a null/non-positive `product_id` or a non-finite `score`; if a product appears twice keep the highest score.
 
**Design (strategy pattern — the only place with an interface)**
```java
public interface RecommendationProvider {
    /** Related products for the cart, best first. Contract: returns at most `limit` entries, never contains
        ids from `cartProductIds`, no duplicates. Throws RecommendationUnavailableException only if the
        provider cannot produce a result. */
    List<ScoredRecommendation> recommend(List<Long> cartProductIds, int limit);
}
public record ScoredRecommendation(long productId, Double score, RuleMetrics metrics) {}   // score/metrics null for fallback
// RuleMetrics(Double support, Double confidence, Double lift) is a record in package dto.response (it is also part of the API response)
public class RecommendationUnavailableException extends RuntimeException {}
```
- `MlRecommendationProvider` (bean name `mlRecommendationProvider`): uses the `RestClient` bean from `RestClientConfig` (built from the config with a `JdkClientHttpRequestFactory` or `SimpleClientHttpRequestFactory` using the connect/read timeouts); the JSON request/response shapes of the external service are private nested records of this class. Sends at most 50 distinct cart ids and `max_k = min(limit, 50)`. A plain try/catch around the call converts ANY problem — blank `ml.base-url`, non-2xx status, timeout, connection error, malformed/missing body — into `RecommendationUnavailableException` (log one WARN line with the exception message, never payloads). On success: drop entries with null/non-positive `product_id` or non-finite `score`, drop ids already in the cart, de-duplicate by product id keeping the highest score, sort by score descending then product id ascending, truncate to `limit`.
- `PopularProductsRecommendationProvider` (bean name `popularProductsRecommendationProvider`, the fallback; never throws): most popular available products not in the cart, where popularity = `SUM(quantity)` over `order_items` joined to orders whose status is not `CANCELLED` (one aggregate JPQL/native query with a `Pageable` limit, ordered by the sum descending, then product id ascending, restricted to `products.available = true` and excluding the cart ids). If fewer than `limit` products qualify (e.g. no order history yet), fill the remainder with other available products not in the cart ordered by id descending. Returns `ScoredRecommendation` with null score/metrics.
- `SmartCartService` depends only on the `RecommendationProvider` interface: a constructor (write it explicitly, with `@Qualifier` on the two parameters) receives a `primary` and a `fallback` provider. It contains no ML, HTTP or popularity logic.
 
**Algorithm** (`SmartCartService`)
1. Load the cart's product ids. Empty cart → `{ "source": "MODEL", "items": [] }` without calling any provider.
2. Ask `primary.recommend(cartIds, min(limit * 2, 50))` inside try/catch for `RecommendationUnavailableException` (the extra candidates compensate for filtering in the next step).
3. Load the candidate products in ONE query and keep only those that exist, have `available = true` and are not in the cart, preserving the provider's order; keep the first `limit`.
4. If the call succeeded and at least one item remains → `source = MODEL` (do not top up with fallback items).
5. Otherwise (provider unavailable, empty result, or everything filtered out) → ask `fallback.recommend(cartIds, limit)`, run the same load/filter step, and return `source = FALLBACK`. If even that yields nothing, return an empty list with `source = FALLBACK`. The fallback path must never throw.
 
Description text for the endpoint: "related products for your cart" (contextual) — not "personalized". Never log the cart contents. Add `@Tag`/`@Operation(summary)` annotations.
 
Out of scope: caching, retries, circuit breakers, metrics, health indicators, a stub provider (when the external service is not running the fallback simply takes over), additional provider implementations.
````
