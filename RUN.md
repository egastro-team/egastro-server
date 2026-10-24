# Run eGastro and test it with Bruno

Implemented through T05: authentication, categories, products, public browsing,
admin catalog CRUD, server-side shopping carts, checkout, customer order history,
and admin order management. Recommendations belong to a later task. Run sections
11 and 13 before section 8's catalog cleanup.

## 1. Start PostgreSQL and the backend

Requirements: JDK 21, Docker Desktop running, Bruno, and free ports 5432/8080.
Run from the repository root in PowerShell:

```powershell
Set-Location D:\Uni\sem1\egastro-server
docker compose up -d
docker compose exec postgres pg_isready -U egastro -d egastro
./mvnw.cmd -q compile
./mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--spring.jpa.hibernate.ddl-auto=update'
```

Wait for PostgreSQL to report `accepting connections` before running Maven.
The backend is ready when the log reports `Started EgastroApplication`.
Keep that terminal open while testing.

**Use the run command above:** the current `application.yml` uses
`ddl-auto: create-drop`. The command overrides it to `update`, preserving
the seed and your data. With `create-drop`, tables are recreated at startup
and dropped on shutdown.

If Java is not on PATH, set your installed JDK 21 location first:

```powershell
$env:JAVA_HOME = 'C:\Users\green\.jdks\corretto-21.0.12.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

Use your own JDK path if different. On macOS/Linux use `./mvnw` instead
of `./mvnw.cmd`. Maven comes with the wrapper.

### Catalog seed

Compose mounts `docker/init` into PostgreSQL. On **first initialization**
PostgreSQL executes `01-catalog-seed.sql`: 6 categories, 48 products,
including 3 unavailable products. Existing volumes do not rerun the script.

After starting the backend with `update`, check counts in a second terminal:

```powershell
docker compose exec postgres psql -U egastro -d egastro -c "SELECT count(*) FROM categories; SELECT count(*) FROM products;"
```

If **both catalog tables are empty**, apply the seed once:

```powershell
Get-Content -Raw docker/init/01-catalog-seed.sql | docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U egastro -d egastro
```

The inserts are not idempotent; do not rerun against a populated catalog.
No volume deletion is needed.

### Defaults and overrides

| Setting | Default | Environment override |
| --- | --- | --- |
| Database URL | `jdbc:postgresql://localhost:5432/egastro` | `DB_URL` |
| Database username/password | `egastro` / `egastro` | `DB_USERNAME` / `DB_PASSWORD` |
| Admin email | `admin@egastro.local` | `ADMIN_EMAIL` |
| Admin password | `Admin12345` | `ADMIN_PASSWORD` |
| JWT secret | Local development placeholder | `JWT_SECRET` |

Set overrides before starting Maven. Admin bootstrap only creates a missing
account; changing `ADMIN_PASSWORD` does not reset an existing password.
JWTs last 24 hours by default. Changing the signing secret invalidates old tokens.

## 2. Bruno setup

Create a collection and select a local environment with these variables:

| Variable | Value |
| --- | --- |
| `baseUrl` | `http://localhost:8080` |
| `adminToken` | Fill from admin login's `accessToken` |
| `userToken` | Fill from customer login's `accessToken` |
| `categoryId` | Fill from category creation's `id` |
| `productId` | Fill from product creation's `id` |
| `missingId` | `999999999` (must be unused) |

For each request:

1. Select the stated HTTP method and paste the URL block into the URL field.
2. For a body, select **JSON**, paste the JSON block, and use
   `Content-Type: application/json`.
3. Set request authentication explicitly:
   **None** for public requests; **Bearer Token** with `{{adminToken}}` for
   admin requests; **Bearer Token** with `{{userToken}}` for customer requests.
   The token field takes only the token, without a `Bearer ` prefix.

### Headers in Bruno

You can use Bruno's **Auth → Bearer Token** setting to generate the
`Authorization` header, or enter it manually in **Headers**. Choose one method
so the request contains a single Authorization header. When using the Auth tab,
enter `{{adminToken}}` or `{{userToken}}` without the `Bearer ` prefix.
When entering the header manually, include the prefix exactly as shown below.

| Request | Required headers |
| --- | --- |
| Register or login with a JSON body | `Content-Type: application/json` |
| Public catalog GET or API docs GET | None |
| GET `/api/auth/me` | `Authorization: Bearer {{userToken}}` (or admin token) |
| Admin POST or PUT with a JSON body | `Authorization: Bearer {{adminToken}}` and `Content-Type: application/json` |
| Admin DELETE | `Authorization: Bearer {{adminToken}}` |
| Cart GET or DELETE | `Authorization: Bearer {{userToken}}` (or admin token) |
| Cart POST or PUT | `Authorization: Bearer {{userToken}}` and `Content-Type: application/json` |

For example, an admin product POST/PUT uses:

```http
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

Bruno's JSON body mode normally sets Content-Type automatically; check it in the
Headers tab. GET and DELETE examples have no body, so they need no Content-Type.
No cookies or API key are required. `Accept: application/json` is optional.
For public requests and missing-token checks, select Auth: None and remove any
manually added Authorization header.

Numeric ID variables are unquoted in JSON. Response IDs and timestamps below are
illustrative. Run sections 3–6 in order, then failures in section 7,
then cleanup in section 8. Use a new email/name if an earlier run was not cleaned up.

## 3. Authentication

### Register — POST, Auth: None

Required headers:

```http
Content-Type: application/json
```

```text
{{baseUrl}}/api/auth/register
```

```json
{
  "email": "bruno.customer@example.com",
  "password": "Customer123"
}
```

Expected: **201**, with `id`, `email`, `role: "USER"`; no token or password.
Email is trimmed and stored lowercase. Password spaces are preserved.
Supplying a role does not grant ADMIN.

| Change to the body | Expected |
| --- | --- |
| Repeat registration, including different email capitalization | 409, `A user with this email already exists` |
| Email `not-an-email` | 400, field `email`: `Invalid email format` |
| Omit/blank email | 400, field `email`: `Email is required` |
| Omit password | 400, field `password`: `Password is required` |
| Password `abcdefgh` | 400, field `password`: `Password must contain at least one digit` |
| Password `abc1` | 400, field `password`: `Password must be at least 8 characters long` |
| Password over 72 characters or 72 UTF-8 bytes | 400, password maximum-length error |
| Email over 254 characters | 400, email maximum-length error; format error may also apply |

### Customer login — POST, Auth: None

Required headers:

```http
Content-Type: application/json
```

```text
{{baseUrl}}/api/auth/login
```

```json
{
  "email": "bruno.customer@example.com",
  "password": "Customer123"
}
```

Expected: **200**:

```json
{
  "accessToken": "<signed JWT>",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 2,
    "email": "bruno.customer@example.com",
    "role": "USER"
  }
}
```

Copy `accessToken` into `userToken`.

Wrong password or unknown email → **401**, `Incorrect email or password`.
Missing/blank fields → **400**, `Validation failed`, with field errors.

### Admin login — POST, Auth: None

Required headers:

```http
Content-Type: application/json
```

```text
{{baseUrl}}/api/auth/login
```

```json
{
  "email": "admin@egastro.local",
  "password": "Admin12345"
}
```

Expected: **200**, same login shape, with `user.role: "ADMIN"`.
Copy `accessToken` into `adminToken`. Use your configured credentials if changed.

### Current user — GET, Auth: customer or admin

Required header for a customer:

```http
Authorization: Bearer {{userToken}}
```

For an admin, use `Authorization: Bearer {{adminToken}}` instead.
No `Content-Type` header is needed for this GET request.

```text
{{baseUrl}}/api/auth/me
```

Expected: **200**, with the matching user's `id`, `email`, and `role`.
With Auth: None, token `not-a-jwt`, a tampered token, or an expired token:
**401**, `Authentication required`.

There is no logout endpoint: discard the client token.

## 4. Public catalog — Auth: None

### Categories — GET

Headers: none required.

```text
{{baseUrl}}/api/categories
```

Expected: **200**, an array of `{"id":1,"name":"Dairy"}` objects,
sorted by name ignoring case, then ID. Fresh seed has 6 categories.
Use actual returned IDs rather than assuming IDs start at 1.

### Products — GET

Headers: none required. This also applies to all filter, sort, and pagination examples below.

```text
{{baseUrl}}/api/products
```

Expected: **200**, with a page of ProductResponse objects:

```json
{
  "content": [
    {
      "id": 36,
      "name": "Apple Juice",
      "description": "Clear apple juice, 1 litre.",
      "price": 1.89,
      "unitOfMeasure": "L",
      "imageUrl": "https://placehold.co/400x300?text=Apple%20Juice",
      "available": true,
      "categoryId": 5,
      "categoryName": "Beverages"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 48,
  "totalPages": 3
}
```

The example abbreviates `content` to one item; the default first page has
20 items. Unavailable products remain in results.

Copy each URL into the GET request:

```text
{{baseUrl}}/api/products?q=MILK&sort=name_asc&page=0&size=5
```

Expected: **200**, case-insensitive matches including Whole Milk and Milk Chocolate.

```text
{{baseUrl}}/api/products?sort=name_desc&page=0&size=10
```

```text
{{baseUrl}}/api/products?sort=price_asc&page=0&size=10
```

```text
{{baseUrl}}/api/products?sort=price_desc&page=1&size=10
```

Expected: **200**, requested ordering. Pages are zero-based, and every sort
uses ID ascending for ties. Omitted sort defaults to `name_asc`.

```text
{{baseUrl}}/api/products?page=-1&size=101&q=%20%20
```

Expected: **200**, `page: 0`, `size: 100`; blank search is ignored.
`size=0` becomes 1. Pages beyond the final page have empty `content`.

Category filtering and product details below use IDs created in the admin flow.

## 5. Admin categories — Auth: admin

### Create — POST

Required headers:

```http
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

```text
{{baseUrl}}/api/admin/categories
```

```json
{
  "name": "  Bruno Test Category  "
}
```

Expected: **201**, `Location: /api/admin/categories/<id>`, and:

```json
{
  "id": 7,
  "name": "Bruno Test Category"
}
```

Copy `id` into `categoryId`. Surrounding spaces are removed.
Public category listing immediately includes the new category.

### Browse an empty category — GET, Auth: None

Headers: none required.

```text
{{baseUrl}}/api/products?category={{categoryId}}
```

Expected: **200**, `content: []`, `totalElements: 0`, `totalPages: 0`.

### Rename — PUT, Auth: admin

Required headers:

```http
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

```text
{{baseUrl}}/api/admin/categories/{{categoryId}}
```

```json
{
  "name": "Bruno Renamed Category"
}
```

Expected: **200**, same ID with the updated name.
Repeating this PUT with its own current name succeeds.
Public category listing immediately reflects the change.

## 6. Admin products — Auth: admin

### Create — POST

Required headers:

```http
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

```text
{{baseUrl}}/api/admin/products
```

```json
{
  "name": "  Bruno Sample Milk  ",
  "description": "  Whole milk for the Bruno catalog check.  ",
  "categoryId": {{categoryId}},
  "price": 2.49,
  "unitOfMeasure": "L",
  "imageUrl": "https://placehold.co/400x300?text=Bruno%20Sample%20Milk"
}
```

Expected: **201**, `Location: /api/products/<id>`, and ProductResponse with
trimmed strings, `available: true`, and
`categoryName: "Bruno Renamed Category"`. Copy `id` into `productId`.

Description/image URL are optional. Blank image URL becomes absent.
Omitted/null `available` defaults to `true`.
Units are uppercase: `PIECE`, `KG`, `G`, `L`, `ML`, `PACK`.

### Details — GET, Auth: None

Headers: none required.

```text
{{baseUrl}}/api/products/{{productId}}
```

Expected: **200**, same ProductResponse as creation.
Use public endpoints for reads; there is no separate admin GET endpoint.

### Filter by category and name — GET, Auth: None

Headers: none required.

```text
{{baseUrl}}/api/products?category={{categoryId}}&q=milk&sort=price_asc
```

Expected: **200**, one matching product and `totalElements: 1`.

### Full replacement — PUT, Auth: admin

Required headers:

```http
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

```text
{{baseUrl}}/api/admin/products/{{productId}}
```

```json
{
  "name": "Bruno %_\\ Sample",
  "description": "Updated product for literal search checks.",
  "categoryId": {{categoryId}},
  "price": 3.75,
  "unitOfMeasure": "PACK",
  "available": false
}
```

Expected: **200**, same ID with updated fields and `available: false`.
The JSON `\\` represents one literal backslash in the name.
The omitted previous image URL is cleared and omitted from the response.

PUT requires all required fields again. Omitting `available` on a later PUT
sets it to `true`. Repeat public details and category browsing without the
old `q=milk` filter to see the replacement immediately.

### Literal search — GET, Auth: None

Headers: none required.

Run after replacement. Each URL returns **200**, matching the temporary product;
percent/underscore/backslash are literal characters, not SQL wildcards.

```text
{{baseUrl}}/api/products?category={{categoryId}}&q=%25
```

```text
{{baseUrl}}/api/products?category={{categoryId}}&q=_
```

```text
{{baseUrl}}/api/products?category={{categoryId}}&q=%5C
```

Paste these encoded URLs as written. If using Bruno's query editor instead,
enter the raw percent/backslash once and let it encode the value.

## 7. Failure paths — run before cleanup

Use Auth: admin for admin requests unless testing authorization.

### Public browsing failures — GET, Auth: None

Headers: none required.

| Copyable URL | Expected |
| --- | --- |
| `{{baseUrl}}/api/products?category={{missingId}}` | 404, `Category not found` |
| `{{baseUrl}}/api/products/{{missingId}}` | 404, `Product not found` |
| `{{baseUrl}}/api/products/not-a-number` | 400, field `id`: `Invalid parameter value` |
| `{{baseUrl}}/api/products?category=abc` | 400, field `category`: `Invalid parameter value` |
| `{{baseUrl}}/api/products?page=abc` | 400, field `page`: `Invalid parameter value` |
| `{{baseUrl}}/api/products?size=abc` | 400, field `size`: `Invalid parameter value` |
| `{{baseUrl}}/api/products?sort=unknown` | 400, field `sort`: `Invalid product sort value` |
| `{{baseUrl}}/api/products?sort=NAME_ASC` | 400, same sort error; request values are lowercase |
| `{{baseUrl}}/api/products?sort=` | 400, same sort error; omit sort for its default |

This URL contains 101 search characters:

```text
{{baseUrl}}/api/products?q=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

Expected: **400**, field `q`: `Search must be at most 100 characters long`.
Length is checked after trimming.

### Category failures

For POST and PUT, use:

```http
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

For DELETE, use only `Authorization: Bearer {{adminToken}}`; send no body.

POST, Auth: admin:

```text
{{baseUrl}}/api/admin/categories
```

```json
{
  "name": "  bruno renamed category  "
}
```

Expected: **409**, `A category with this name already exists`.
Uniqueness ignores capitalization and surrounding spaces.

PUT `{{baseUrl}}/api/admin/categories/{{categoryId}}` with
`{"name":"Dairy"}` conflicts with the seed category: same **409**.
The current category name remains unchanged.

For POST or PUT, use these bodies:

| Body/change | Expected |
| --- | --- |
| `{}` | 400, field `name`: `Name is required` |
| `{"name":"   "}` | 400, field `name`: `Name is required` |
| Name longer than 100 characters | 400, field `name`: `Name must be at most 100 characters long` |

DELETE, Auth: admin, while the product still references the category:

```text
{{baseUrl}}/api/admin/categories/{{categoryId}}
```

Expected: **409**, `Category contains products and cannot be deleted`.
Both the category and product remain.

### Product validation failures — POST or PUT, Auth: admin

Required headers:

```http
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

Use the following baseline, then change **one field at a time** using the table.
Restore the baseline between checks. Invalid requests save nothing.

POST URL:

```text
{{baseUrl}}/api/admin/products
```

PUT URL:

```text
{{baseUrl}}/api/admin/products/{{productId}}
```

```json
{
  "name": "Validation Sample",
  "description": "Validation check.",
  "categoryId": {{categoryId}},
  "price": 2.49,
  "unitOfMeasure": "L",
  "imageUrl": "https://placehold.co/400x300?text=Validation",
  "available": true
}
```

Do not submit the unchanged valid baseline unless you intend to create/update a
product. The table describes invalid variants.

| Change | Expected: HTTP 400 |
| --- | --- |
| Omit/null/blank `name` | `name`: `Name is required` |
| Name over 255 characters | `name`: `Name must be at most 255 characters long` |
| Description over 5000 characters | `description`: `Description must be at most 5000 characters long` |
| Omit/null `categoryId` | `categoryId`: `Category is required` |
| `"categoryId": {{missingId}}` | `categoryId`: `Category does not exist` |
| Omit/null `price` | `price`: `Price is required` |
| Price `0` or `-1` | `price`: `Price must be greater than 0` |
| Price `1.234` | `price`: `Price must have at most 10 integer digits and 2 decimals` |
| Price `10000000000` | `price`: maximum-value and digit-limit errors |
| Omit/null `unitOfMeasure` | `unitOfMeasure`: `Unit of measure is required` |
| Unit `"INVALID"` or `"kg"` | `Malformed request body`, no `fieldErrors` |
| Image URL `"ftp://example.com/image.png"` | `imageUrl`: `Image URL must be an absolute HTTP or HTTPS URL` |
| Image URL `"relative/image.png"` or `"https:///missing-host"` | Same `imageUrl` error |
| Image URL over 1000 characters | `imageUrl`: maximum-length error; format error may also apply |

Complete invalid-category example:

```json
{
  "name": "Validation Sample",
  "categoryId": {{missingId}},
  "price": 2.49,
  "unitOfMeasure": "L"
}
```

Expected: **400**:

```json
{
  "timestamp": "2026-10-24T12:00:00Z",
  "status": 400,
  "message": "Validation failed",
  "path": "/api/admin/products",
  "fieldErrors": [
    {
      "field": "categoryId",
      "message": "Category does not exist"
    }
  ]
}
```

Send raw malformed JSON such as `{"name":` to either admin POST endpoint:
**400**, `Malformed request body`, without `fieldErrors`.
Use valid admin authentication so security does not reject it first.

### Unknown admin resources — Auth: admin

Every request below requires `Authorization: Bearer {{adminToken}}`.
PUT also requires `Content-Type: application/json`; DELETE has no body and
needs no `Content-Type` header.

| Method | Copyable URL | Body | Expected |
| --- | --- | --- | --- |
| PUT | `{{baseUrl}}/api/admin/products/{{missingId}}` | Valid product baseline | 404, `Product not found` |
| DELETE | `{{baseUrl}}/api/admin/products/{{missingId}}` | None | 404, `Product not found` |
| PUT | `{{baseUrl}}/api/admin/categories/{{missingId}}` | `{"name":"Missing Category"}` | 404, `Category not found` |
| DELETE | `{{baseUrl}}/api/admin/categories/{{missingId}}` | None | 404, `Category not found` |

PUT bodies must be valid: Bean Validation runs before resource lookup.

### Authorization failures

Keep `Content-Type: application/json` on POST/PUT requests. DELETE needs no
Content-Type. Change only the authentication for these checks:

| Check | Authorization header |
| --- | --- |
| Anonymous → 401 | Omit the header entirely; select Auth: None |
| Invalid token → 401 | `Authorization: Bearer not-a-jwt` |
| Customer role → 403 | `Authorization: Bearer {{userToken}}` |


Repeat each row with a valid body for POST/PUT:

| Method | Copyable URL |
| --- | --- |
| POST | `{{baseUrl}}/api/admin/categories` |
| PUT | `{{baseUrl}}/api/admin/categories/{{categoryId}}` |
| DELETE | `{{baseUrl}}/api/admin/categories/{{categoryId}}` |
| POST | `{{baseUrl}}/api/admin/products` |
| PUT | `{{baseUrl}}/api/admin/products/{{productId}}` |
| DELETE | `{{baseUrl}}/api/admin/products/{{productId}}` |

For **every row**:

- Auth: **None**, or Bearer Token `not-a-jwt` → **401**, `Authentication required`.
- Bearer Token `{{userToken}}` → **403**, `Access denied`.

Rejected requests leave data unchanged. Restore Auth: admin for cleanup.

## 8. Successful deletes and cleanup — Auth: admin

### Delete the product — DELETE

Required header:

```http
Authorization: Bearer {{adminToken}}
```

Send no request body; no `Content-Type` header is needed.

```text
{{baseUrl}}/api/admin/products/{{productId}}
```

Expected: **204**, empty body. This is a hard delete.

Public GET `{{baseUrl}}/api/products/{{productId}}` → **404**,
`Product not found`. Repeat admin DELETE → the same **404**.
Public GET `{{baseUrl}}/api/products?category={{categoryId}}` → **200** with
empty `content`; the category still exists.

### Delete the now-empty category — DELETE

Required header:

```http
Authorization: Bearer {{adminToken}}
```

Send no request body; no `Content-Type` header is needed.

```text
{{baseUrl}}/api/admin/categories/{{categoryId}}
```

Expected: **204**, empty body. Public category listing no longer includes it.
Public GET `{{baseUrl}}/api/products?category={{categoryId}}` → **404**,
`Category not found`. Repeat category DELETE → the same **404**.

With only the original seed remaining, public GET
`{{baseUrl}}/api/products?size=100` returns 48 products, 3 unavailable.
The customer account remains available for future login.

## 9. API docs, error conventions, and route checks

Open [Swagger UI](http://localhost:8080/swagger-ui.html), or use Bruno
GET, Auth: None. Headers: none required:

```text
{{baseUrl}}/v3/api-docs
```

Expected: **200**, OpenAPI JSON containing authentication, catalog, cart, and order endpoints.
Swagger's Authorize dialog takes the token without the `Bearer ` prefix.

Public GET follow-up checks in the cleanup section also require no headers.

All API errors have `timestamp`, `status`, `message`, and `path`.
Validation errors add `fieldErrors`; empty field errors are omitted.
Multiple violated rules can produce multiple entries.

| Request | Expected |
| --- | --- |
| GET `{{baseUrl}}/api/unknown`, authenticated | 404, `Resource not found` |
| GET `{{baseUrl}}/api/unknown`, Auth: None | 401, `Authentication required` |
| GET `{{baseUrl}}/api/admin/orders`, admin | 200, paged order summaries |
| GET `{{baseUrl}}/api/admin/orders`, customer | 403, `Access denied` |
| GET `{{baseUrl}}/api/admin/orders`, Auth: None | 401, `Authentication required` |

Bruno is not subject to browser CORS enforcement. For a browser preflight check,
send OPTIONS, Auth: None, to `{{baseUrl}}/api/auth/me` with these headers:

```text
Origin: http://localhost:5173
Access-Control-Request-Method: GET
Access-Control-Request-Headers: Authorization,Content-Type
```

Expected: **200**, matching `Access-Control-Allow-Origin`, allowed methods
including GET/POST/PUT/PATCH/DELETE/OPTIONS, and allowed headers
Authorization/Content-Type. Credentials are not enabled. Port 3000 is also
allowed. Unlisted origins receive **403** from the CORS processor, outside
the standard API JSON error format.

To check expiry, restart with a temporary two-second lifetime:

```powershell
./mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--spring.jpa.hibernate.ddl-auto=update --egastro.security.jwt.ttl=PT2S'
```

Log in, copy the new token, wait at least 3 seconds, then GET `/api/auth/me`.
Expect **401**. Restart normally and log in again afterward.
If a token's user is removed directly from the database, `/me` returns
**404**, `User not found`; no user-deletion endpoint exists.

## 10. Stop and troubleshoot

Stop the backend with **Ctrl+C**, then:

```powershell
docker compose down
```

The database volume remains. With the documented `update` command, accounts
and catalog changes persist. Repeated registration returns 409; log in instead.
Repeat the catalog flow after cleanup, or choose a new category name.

| Symptom | Check |
| --- | --- |
| Java not found | Set `JAVA_HOME` to JDK 21 and add its `bin` to PATH. |
| Docker cannot connect | Start Docker Desktop and wait for it to be ready. |
| Database connection refused | Run `docker compose ps` and `docker compose logs postgres`; check port 5432. |
| Database authentication fails | Match backend `DB_*` overrides to existing database credentials. |
| Empty catalog after startup | Use `ddl-auto=update`; check counts and seed instructions above. |
| Port 8080 in use | Stop the previous backend instance. |
| Bruno variables unresolved | Select the environment and fill values from responses. |
| Unexpected 401 | Check request Auth, copy a fresh `accessToken`, and check expiry/signing-secret changes. |
| Admin request returns 403 | Use a login token whose `user.role` is `ADMIN`. |
| Admin login fails after env change | Bootstrap does not reset existing credentials. |
| Category POST returns 409 on rerun | Finish cleanup or choose a new name. |
| Literal search gives unexpected results | Avoid double encoding: URL uses `%25`/`%5C`; query editor uses raw characters. |

## 11. Shopping cart (T04) — run before catalog cleanup

Use the customer login from section 3: copy `accessToken` into `userToken`.
Use the temporary product created in section 6 for `productId`, and an unused
numeric ID for `missingId`. All four cart endpoints require authentication;
admins also have their own carts. No user ID is accepted from the client.
There are no new configuration keys or seed steps. Hibernate creates `carts`
and `cart_items` when you start with the documented `ddl-auto=update` command.

GET and DELETE require only this header and no body:

```http
Authorization: Bearer {{userToken}}
```

POST and PUT require both headers:

```http
Authorization: Bearer {{userToken}}
Content-Type: application/json
```

### Get the cart — GET

```text
{{baseUrl}}/api/cart
```

Expected **200**. The first GET creates an empty cart row for this user:

```json
{"items":[],"totalQuantity":0,"totalAmount":0.00}
```

Subsequent GETs preserve the contents. Items are ordered by cart item ID
ascending. Prices and availability reflect the current product; totals are
calculated on every response. A missing product image omits `imageUrl` under
the application's existing null-field serialization setting.

### Add a product — POST

```text
{{baseUrl}}/api/cart/items
```

```json
{"productId":{{productId}},"quantity":2}
```

Expected **200** with the updated cart. For a product priced at 2.49, an
illustrative response is:

```json
{
  "items": [
    {
      "productId": 49,
      "name": "Example product",
      "imageUrl": "https://placehold.co/400x300",
      "unitPrice": 2.49,
      "unitOfMeasure": "L",
      "quantity": 2,
      "lineTotal": 4.98,
      "available": true
    }
  ],
  "totalQuantity": 2,
  "totalAmount": 4.98
}
```

Names, IDs, prices and units depend on your product. Repeat the POST: the
existing quantity becomes 4, rather than creating another item. Add a second
available product using its ID from `GET {{baseUrl}}/api/products?size=100`
to check ordering and sums. Each user has a separate cart: GET with
`adminToken` does not show the customer's items.

### Set quantity — PUT

```text
{{baseUrl}}/api/cart/items/{{productId}}
```

```json
{"quantity":99}
```

Expected **200** with quantity 99 for this item and recalculated totals.
POST the same product with quantity 1 now returns **400** and leaves the
quantity at 99:

```json
{
  "timestamp": "2026-10-24T12:00:00Z",
  "status": 400,
  "message": "Validation failed",
  "path": "/api/cart/items",
  "fieldErrors": [
    {"field":"quantity","message":"Maximum quantity per product is 99"}
  ]
}
```

PUT with `{"quantity":3}` resets the quantity to 3. If an admin changes the
product's price, the next cart response uses that price. If the admin marks
the product unavailable, it stays visible with `available:false` and still
contributes to totals. PUT may change its quantity; POST rejects adding more
with **409**, `Product is currently unavailable`.

### Remove an item — DELETE

```text
{{baseUrl}}/api/cart/items/{{productId}}
```

Expected **200** with the updated cart. Repeat: **200**, unchanged cart.
Deleting `{{missingId}}` also succeeds. If this was the only item, the
response is `{"items":[],"totalQuantity":0,"totalAmount":0.00}`.

### Validation and lookup failures

Use the headers above. Every rejected request leaves quantities unchanged.
For POST checks unrelated to product availability, use a known available
`productId`. For PUT checks, use an item already added to the cart.

| Method and copyable URL | JSON body | Expected |
| --- | --- | --- |
| POST `{{baseUrl}}/api/cart/items` | `{"quantity":1}` | 400, `productId`: `Product is required` |
| POST `{{baseUrl}}/api/cart/items` | `{"productId":{{productId}}}` | 400, `quantity`: `Quantity is required` |
| POST `{{baseUrl}}/api/cart/items` | `{"productId":{{productId}},"quantity":0}` | 400, `quantity`: `Quantity must be at least 1` |
| POST `{{baseUrl}}/api/cart/items` | `{"productId":{{productId}},"quantity":100}` | 400, `quantity`: `Maximum quantity per product is 99` |
| POST `{{baseUrl}}/api/cart/items` | `{"productId":{{missingId}},"quantity":1}` | 404, `Product not found` |
| PUT `{{baseUrl}}/api/cart/items/{{productId}}` | `{}` or `{"quantity":null}` | 400, `quantity`: `Quantity is required` |
| PUT `{{baseUrl}}/api/cart/items/{{productId}}` | `{"quantity":0}` | 400, `quantity`: `Quantity must be at least 1` |
| PUT `{{baseUrl}}/api/cart/items/{{productId}}` | `{"quantity":100}` | 400, `quantity`: `Maximum quantity per product is 99` |
| PUT `{{baseUrl}}/api/cart/items/{{missingId}}` | `{"quantity":1}` | 404, `Item not found in cart` |
| PUT `{{baseUrl}}/api/cart/items/not-a-number` | `{"quantity":1}` | 400, `productId`: `Invalid parameter value` |
| POST `{{baseUrl}}/api/cart/items` | Raw malformed JSON `{"quantity":` | 400, `Malformed request body` |

Negative quantities have the same minimum error as zero. Explicit nulls have
the same required errors as omitted fields. To check unavailable-product POST,
copy an ID with `available:false` from the catalog into `unavailableProductId`:

```text
{{baseUrl}}/api/cart/items
```

```json
{"productId":{{unavailableProductId}},"quantity":1}
```

Expected **409**:

```json
{"timestamp":"2026-10-24T12:00:00Z","status":409,"message":"Product is currently unavailable","path":"/api/cart/items"}
```

404 responses use this shape with `status:404` and the table's message/path,
without `fieldErrors`. Validation responses use the 400 shape above with the
table's field/message and the request path.

For each of GET `/api/cart`, POST `/api/cart/items`, and PUT/DELETE
`/api/cart/items/{{productId}}`, select Auth: None and remove Authorization:
expect **401**. Repeat with `Authorization: Bearer not-a-jwt`: also **401**.
Keep Content-Type and a valid JSON body on POST/PUT. Example for GET:

```json
{"timestamp":"2026-10-24T12:00:00Z","status":401,"message":"Authentication required","path":"/api/cart"}
```

### Product deletion removes references in every cart

Restore the temporary product to `available:true` using the admin PUT from
section 6, then POST it into both the customer's and admin's carts (use each
token in turn). Run the product DELETE in section 8 with `adminToken`:
expect **204**, even while the product is in carts. GET `/api/cart` with each
token returns **200** without that product; other items remain. This cleanup
runs in the same transaction as product deletion. Continue section 8's category
cleanup afterward. There is no public clear-cart endpoint; remove other items
individually if desired.

## 12. Automated tests

Use JDK 21 and run from the repository root:

```powershell
./mvnw.cmd -q test
```

On macOS/Linux use `./mvnw -q test`. No running backend, PostgreSQL, Docker,
environment overrides, or seed data are needed. Fixtures exist only in memory.
The tests use JUnit 5, Mockito and JUnit assertions, with Given/When/Then
sections and `should…_When_…` method names.

Service/component tests run without Spring. Controller integration tests use
`@WebMvcTest`, MockMvc, and `@MockitoBean` services; they load the real security
configuration, JWT filter, security error handlers, request validation, and
exception advice. Token parsing is mocked in the controller slices, with real
JWT generation/parsing tested separately. These slices verify the HTTP contract
and authorization; they do not exercise database queries or transaction rollback.

Integration test classes use the `IT` suffix (for example, `ProductControllerIT`).
Public and admin product/category controllers each have a separate test slice.
Surefire includes the unit-test patterns, `IT*`, and `*IT`, so `test` runs
the entire suite. Shared entities, DTO factories, security fixtures, JSON bodies,
and parameterized inputs live in `src/test/java/com/egastro/testData/`.
Factories return fresh objects for each call; assertion and MockMvc helpers stay
in `support`.

Coverage includes authentication, admin bootstrap, catalog CRUD/filtering,
cart lifecycle and quantity rules, DTO validation, current-price totals,
pagination/sorting, JWT rejection cases, secret redaction, CORS, and error JSON.
Product-delete event ordering and cart cleanup are verified as unit interactions;
section 11 covers the PostgreSQL deletion flow manually.

Order coverage includes checkout snapshots/totals, cart clearing and failure
paths, input normalization and validation bounds, customer ownership and
cancellation, batched summary counts, pagination, and all admin status
transitions. Separate customer/admin order controller slices verify response
bodies, checkout Location, authentication, role checks, validation and business
errors. These tests use mocked repositories/services; section 13 covers the
PostgreSQL order lifecycle and product-deletion snapshot check manually.

Surefire results are written to `target/surefire-reports/`. The test phase also
generates a JaCoCo coverage report: open `target/site/jacoco/index.html`.
To run a single class:

```powershell
./mvnw.cmd '-Dtest=CartServiceTest' test
```

To run only the controller integration tests:

```powershell
./mvnw.cmd '-Dtest=*IT' test
```

## 13. Orders (T05) — run before catalog cleanup

No new configuration keys or seed scripts are needed. Start with section 1's
`ddl-auto=update` override; Hibernate adds `orders` and `order_items` without
recreating the existing catalog. Checkout has no payment step.

Use `baseUrl`, `userToken`, `adminToken`, `productId`, and `missingId` from
sections 2–6. Choose an available product and add it to the customer's cart
using section 11. Save a checkout response's `id` as Bruno variable `orderId`.
For privacy checks, register and log in a second customer and save that login's
`accessToken` as `otherUserToken`. Each login token belongs in Bruno's Bearer
Token field without the `Bearer ` prefix.

All customer requests below require:

```http
Authorization: Bearer {{userToken}}
```

All admin requests below require:

```http
Authorization: Bearer {{adminToken}}
```

Checkout POST and status PATCH additionally require
`Content-Type: application/json`. GET and cancellation POST have no body and
need no Content-Type header. These are the only required headers.

### Checkout — POST

```text
{{baseUrl}}/api/orders
```

```json
{
  "deliveryAddress": "  10 Main Street, Kyiv  ",
  "contactPhone": "+380 (67) 123-45-67",
  "comment": "  Call on arrival  "
}
```

Expected **201**, `Location: /api/orders/{id}`. For a cart containing two units
of a product priced at 2.49, an illustrative response is:

```json
{
  "id": 1,
  "status": "NEW",
  "totalAmount": 4.98,
  "deliveryAddress": "10 Main Street, Kyiv",
  "contactPhone": "+380671234567",
  "comment": "Call on arrival",
  "createdAt": "2026-10-24T12:00:00Z",
  "items": [
    {"productId":49,"productName":"Example product","unitPrice":2.49,"quantity":2,"lineTotal":4.98}
  ]
}
```

IDs, timestamps, names and prices depend on your data. Save `id` as `orderId`.
Address/comment are trimmed; spaces, dashes and parentheses are removed from
the phone. A null/omitted comment is allowed and omitted from the response.
GET `{{baseUrl}}/api/cart` now returns an empty cart. Repeat checkout with the
same valid body: **409**, `Cart is empty`. Checkout creates the order/items
and clears the cart in one transaction; a rejected checkout preserves the cart.

To check availability, add an available product, then use the admin product
PUT from section 6 to set it unavailable. Submit checkout: **409**,
`Some products are unavailable: Example product` (the actual names are joined
with comma and space). No order is created. Restore availability before retrying.

### Customer history and details — GET

```text
{{baseUrl}}/api/orders?page=0&size=20
```

Expected **200**:

```json
{
  "content":[{"id":1,"status":"NEW","totalAmount":4.98,"itemsCount":2,"createdAt":"2026-10-24T12:00:00Z"}],
  "page":0,"size":20,"totalElements":1,"totalPages":1
}
```

Only this user's orders appear, newest first (`createdAt desc, id desc`).
`itemsCount` is the sum of quantities, not the number of distinct products.
Page defaults to 0; size defaults to 20. Negative page becomes 0; size is clamped
to 1–100. Out-of-range pages return empty `content` with the matching totals.

```text
{{baseUrl}}/api/orders/{{orderId}}
```

Expected **200** with the checkout response fields and current order status.
Later product renaming, repricing, unavailability or hard deletion does not
alter these snapshot fields or remove order history. Check this GET after
section 8's product deletion: the saved product ID/name/price remain.

### Customer cancellation — POST, no body

```text
{{baseUrl}}/api/orders/{{orderId}}/cancel
```

For a NEW order, expected **200** with the full order and `status:"CANCELLED"`.
No cart items are restored. Repeat: **409**, `Order can no longer be cancelled`.
CONFIRMED, COMPLETED and CANCELLED orders all reject customer cancellation.
Create another order by adding products and checking out again for admin checks.

### Admin list and details — GET

```text
{{baseUrl}}/api/admin/orders?page=0&size=20
```

```text
{{baseUrl}}/api/admin/orders?status=NEW&page=0&size=20
```

Expected **200** with the same pagination envelope and summary fields as customer
history, plus `userId` and `userEmail` on every content entry. It includes all
users, sorted newest first. Omit `status` for all orders; valid filter values
are exactly `NEW`, `CONFIRMED`, `COMPLETED`, `CANCELLED`. Pagination uses the same
defaults and clamping as customer history.

```text
{{baseUrl}}/api/admin/orders/{{orderId}}
```

Expected **200** with the full customer order fields plus `userId` and
`userEmail`. No password, password hash or token fields are included.

### Admin status change — PATCH

```text
{{baseUrl}}/api/admin/orders/{{orderId}}/status
```

```json
{"status":"CONFIRMED"}
```

Use a NEW order: **200**, full admin detail response with `status:"CONFIRMED"`.
Repeat unchanged: **409**, `Order already has status CONFIRMED`. Customer
cancellation now returns **409**. PATCH `{"status":"NEW"}` is allowed;
PATCH `{"status":"COMPLETED"}` or `{"status":"CANCELLED"}` is also allowed
from NEW or CONFIRMED. These last two statuses are final. Changing a completed
order to CONFIRMED returns **409**, `Cannot change status of a COMPLETED order`;
changing a cancelled order returns **409**,
`Cannot change status of a CANCELLED order`. Setting the current status always
returns `Order already has status X`, including final statuses.

### Validation, ownership and authorization failures

Use the valid checkout body above, changing one field at a time. Every checkout
validation failure is **400**, `Validation failed`, with a `fieldErrors` entry:

| Change | Field and message |
| --- | --- |
| Missing/null/blank address | `deliveryAddress`: `Delivery address is required` |
| Trimmed address longer than 500 | `deliveryAddress`: `Delivery address must be at most 500 characters long` |
| Missing/null/blank phone, letters, misplaced `+`, or cleaned number outside 10–15 digits | `contactPhone`: `Invalid phone number` |
| Trimmed comment longer than 1000 | `comment`: `Comment must be at most 1000 characters long` |

Example invalid phone response:

```json
{
  "timestamp":"2026-10-24T12:00:00Z","status":400,"message":"Validation failed","path":"/api/orders",
  "fieldErrors":[{"field":"contactPhone","message":"Invalid phone number"}]
}
```

Multiple violated constraints can produce multiple errors. The shared error
format applies to every failure below; `fieldErrors` is omitted for nonvalidation
errors. A business error example:

```json
{"timestamp":"2026-10-24T12:00:00Z","status":409,"message":"Cart is empty","path":"/api/orders"}
```

| Method and copyable URL | Body / setup | Expected |
| --- | --- | --- |
| GET `{{baseUrl}}/api/orders/{{missingId}}` | Customer token | 404, `Order not found` |
| POST `{{baseUrl}}/api/orders/{{missingId}}/cancel` | No body | 404, `Order not found` |
| GET `{{baseUrl}}/api/orders/{{orderId}}` | Use `otherUserToken` for the original customer's order | 404, `Order not found` |
| POST `{{baseUrl}}/api/orders/{{orderId}}/cancel` | Use `otherUserToken`, no body | 404, `Order not found` |
| GET `{{baseUrl}}/api/admin/orders/{{missingId}}` | Admin token | 404, `Order not found` |
| PATCH `{{baseUrl}}/api/admin/orders/{{missingId}}/status` | `{"status":"CONFIRMED"}` | 404, `Order not found` |
| GET `{{baseUrl}}/api/admin/orders?status=INVALID` | Admin token | 400, `Validation failed`, field `status`: `Invalid parameter value` |
| GET `{{baseUrl}}/api/orders?page=abc` | Customer token | 400, `Validation failed`, field `page`: `Invalid parameter value` |
| GET `{{baseUrl}}/api/orders/not-a-number` | Customer token | 400, `Validation failed`, field `id`: `Invalid parameter value` |
| PATCH `{{baseUrl}}/api/admin/orders/{{orderId}}/status` | `{}` or `{"status":null}` | 400, `Validation failed`, field `status`: `Status is required` |
| PATCH `{{baseUrl}}/api/admin/orders/{{orderId}}/status` | `{"status":"INVALID"}` or `{"status":"new"}` | 400, `Malformed request body` |
| POST `{{baseUrl}}/api/orders` | Raw malformed JSON `{"deliveryAddress":` | 400, `Malformed request body` |

For all seven endpoints (checkout, customer list/detail/cancel, admin
list/detail/status), remove Authorization, then try `Bearer not-a-jwt`:
both return **401**, `Authentication required`. Use valid bodies and JSON
Content-Type for checkout/status so these checks isolate authentication.
For all three admin endpoints, use `userToken`: **403**, `Access denied`.
The existing security rules also let admins use the customer endpoints for
their own carts/orders. Customers cannot supply a different user ID.
