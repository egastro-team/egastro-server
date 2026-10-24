# eGastro API contracts

This document describes the implemented backend through T06. Local base URL:
`http://localhost:8080`. Endpoint paths in sections 1–11 are relative to that URL.
Section 12 defines the separate backend-to-ML-service contract, which uses its
own base URL and snake_case JSON fields.
See [RUN.md](RUN.md) for startup and seed instructions and [bruno/](bruno/README.md)
for runnable requests. The running backend also exposes `/v3/api-docs` and
`/swagger-ui.html` publicly.

## 1. HTTP and data conventions

### Access and required headers

Every endpoint below states one of these header sets:

| Header set | Required request headers | Access |
| --- | --- | --- |
| Public | None | Anyone |
| Public JSON | `Content-Type: application/json` | Anyone |
| Authenticated | `Authorization: Bearer <accessToken>` | USER or ADMIN |
| Authenticated JSON | `Authorization: Bearer <accessToken>` and `Content-Type: application/json` | USER or ADMIN |
| Admin | `Authorization: Bearer <adminAccessToken>` | ADMIN only |
| Admin JSON | `Authorization: Bearer <adminAccessToken>` and `Content-Type: application/json` | ADMIN only |

`Accept: application/json` is optional. GET, DELETE, and order cancellation
requests have no body and do not require Content-Type. Do not send cookies or
`credentials: 'include'`; authentication uses the bearer token. No CSRF header
or API key is required. The current user always comes from the token; customer
requests do not accept a user ID.

API response bodies use `application/json`, except successful DELETE responses
with status 204, which have no body. Do not call `response.json()` on a 204.
Creation responses for products, categories, and orders include a relative
`Location` header. CORS currently does not expose Location to browser JavaScript;
use the returned JSON `id` to navigate or construct detail URLs.

### JSON values

- Property names use camelCase. Enum values are case-sensitive.
- IDs are integer JSON numbers backed by Java `Long`. Use actual IDs from
  responses; do not assume a sequence. Values above JavaScript's safe integer
  range require a lossless JSON strategy if such IDs are ever used.
- Money is a JSON number, with up to two decimal places and no currency field.
  JSON parsing may drop trailing zeros; format values for display. Treat backend
  prices and totals as authoritative.
- Quantities are integers, including for products measured in KG, G, L, or ML.
- Timestamps are ISO 8601 UTC strings, for example `2026-10-25T10:00:00Z`.
- Null response fields are **omitted**, rather than serialized as `null`.
  Empty arrays remain arrays. Optional fields in the types below use `?`.
- Text inputs are trimmed where specified. Passwords are never trimmed.
- Unknown JSON properties are ignored. Sending `role`, totals, or a user ID
  does not assign privileges or override server-calculated values.

### Pagination and path parameters

All `{id}` and `{productId}` path parameters are numeric resource IDs. A value
such as `abc`, or a number outside the Java Long range, returns 400 with a
field error named after the path parameter. A numeric but nonexistent ID uses
the endpoint's missing-resource behavior.

| Query parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `page` | 32-bit integer | `0` | Zero-based; negative becomes 0 |
| `size` | 32-bit integer | `20` | Clamped to 1–100 |

These parameters apply only to endpoints that list them. Invalid numeric query
values return 400. Pages past the end return empty `content` and the actual
totals. The common response is `PageResponse<T>`:

```ts
interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
```

### Browser CORS

Default allowed origins are exactly `http://localhost:3000` and
`http://localhost:5173`. Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS.
Allowed request headers: Authorization and Content-Type. Credentials are disabled.
Other frontend origins require backend CORS configuration changes.

The browser automatically sends a preflight when needed, for example:

```http
OPTIONS /api/auth/me
Origin: http://localhost:5173
Access-Control-Request-Method: GET
Access-Control-Request-Headers: Authorization,Content-Type
```

No token or body is required for preflight. An allowed preflight returns 200
with CORS headers. A disallowed origin can return 403 outside the JSON error
format below, and the browser may hide the response from JavaScript.

## 2. Shared response types

These TypeScript declarations describe JSON response shapes; they do not add
frontend implementation files to the backend.

```ts
type Role = 'USER' | 'ADMIN';
type UnitOfMeasure = 'PIECE' | 'KG' | 'G' | 'L' | 'ML' | 'PACK';
type OrderStatus = 'NEW' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';
type ProductSort = 'name_asc' | 'name_desc' | 'price_asc' | 'price_desc';

interface UserResponse {
  id: number;
  email: string;
  role: Role;
}

interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number; // Lifetime in seconds; default 86400, configurable.
  user: UserResponse;
}

interface CategoryResponse {
  id: number;
  name: string;
}

interface ProductResponse {
  id: number;
  name: string;
  description?: string;
  price: number;
  unitOfMeasure: UnitOfMeasure;
  imageUrl?: string;
  available: boolean;
  categoryId: number;
  categoryName: string;
}

interface CartItemResponse {
  productId: number;
  name: string;
  imageUrl?: string;
  unitPrice: number;
  unitOfMeasure: UnitOfMeasure;
  quantity: number;
  lineTotal: number;
  available: boolean;
}

interface CartResponse {
  items: CartItemResponse[];
  totalQuantity: number; // Sum of quantities, not distinct product count.
  totalAmount: number;
}

interface RuleMetrics {
  support?: number;
  confidence?: number;
  lift?: number;
}

interface RecommendedProductResponse {
  product: ProductResponse;
  score?: number;
  metrics?: RuleMetrics;
}

interface CartRecommendationsResponse {
  source: 'MODEL' | 'FALLBACK';
  items: RecommendedProductResponse[];
}

interface OrderItemResponse {
  productId: number; // Snapshot reference; the catalog product may be deleted.
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

interface OrderResponse {
  id: number;
  status: OrderStatus;
  totalAmount: number;
  deliveryAddress: string;
  contactPhone: string;
  comment?: string;
  createdAt: string;
  items: OrderItemResponse[];
}

interface OrderSummaryResponse {
  id: number;
  status: OrderStatus;
  totalAmount: number;
  itemsCount: number; // Sum of item quantities.
  createdAt: string;
}

interface AdminOrderResponse extends OrderResponse {
  userId: number;
  userEmail: string;
}

interface AdminOrderSummaryResponse extends OrderSummaryResponse {
  userId: number;
  userEmail: string;
}

interface ApiError {
  timestamp: string;
  status: number;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
}
```

For example, an empty cart response is:

```json
{ "items": [], "totalQuantity": 0, "totalAmount": 0.00 }
```

## 3. Authentication

### POST /api/auth/register

Headers: **Public JSON**. Path/query parameters: none.

```json
{ "email": "customer@example.com", "password": "Customer123" }
```

| Field | Required | Validation and normalization |
| --- | --- | --- |
| `email` | Yes | Valid email, nonblank, max 254 characters after trimming; stored lowercase; unique ignoring case |
| `password` | Yes | Nonblank, 8–72 characters, at least one ASCII digit; also at most 72 UTF-8 bytes; spaces preserved |

Success: **201**, `UserResponse`, always role `USER`. Registration does not issue
a token; call login afterward. Errors: 400 for validation; **409**
`A user with this email already exists` for a duplicate.

### POST /api/auth/login

Headers: **Public JSON**. Path/query parameters: none. Same endpoint for both roles.

```json
{ "email": "customer@example.com", "password": "Customer123" }
```

Both fields are required and nonblank. Email is trimmed and matched ignoring
case; password spaces are preserved. Registration-only email/password strength
constraints are not reapplied during login.

Success: **200**, `LoginResponse`, for example:

```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": { "id": 1, "email": "customer@example.com", "role": "USER" }
}
```

Use `accessToken` in Authorization on protected requests. Errors: 400 for missing
or blank fields; **401** `Incorrect email or password` for unknown email or a
wrong password. Tokens expire; there is no refresh-token or server logout
endpoint. Logout discards the token on the client. On an expired-token 401,
return to login. Passwords/hashes are never returned.

### GET /api/auth/me

Headers: **Authenticated**. No body, query, or path parameters.
Success: **200**, `UserResponse`. Error: **404** `User not found` if the token's
user no longer exists; missing/invalid/expired authentication returns 401.

## 4. Public catalog

### GET /api/categories

Headers: **Public**. No body, query, or path parameters.
Success: **200**, `CategoryResponse[]` (possibly empty), sorted by name ignoring
case, then ID ascending. This endpoint is not paginated.

### GET /api/products

Headers: **Public**. No body or path parameters.

| Query parameter | Type | Default | Rules |
| --- | --- | --- | --- |
| `category` | Long ID | Omitted | Filter by category; nonexistent category returns 404 `Category not found` |
| `q` | String | Omitted | Trimmed, max 100 characters; case-insensitive substring of product name; blank means no text filter |
| `sort` | ProductSort | `name_asc` when omitted | Exactly `name_asc`, `name_desc`, `price_asc`, or `price_desc` |
| `page` | Integer | `0` | Common pagination rules |
| `size` | Integer | `20` | Common pagination rules |

Example: `/api/products?category=2&q=milk&sort=price_asc&page=0&size=20`.
Success: **200**, `PageResponse<ProductResponse>`. Unavailable products remain
visible. Every sort uses ID ascending as its final tie-breaker. Name sorts
ignore case. Search treats `%`, `_`, and backslash literally; use URLSearchParams
to encode query values once.

Errors: 400 for invalid numeric parameters, `q` over 100 characters, or an
invalid sort (`Invalid product sort value`, field `sort`). `sort=` and uppercase
values such as `NAME_ASC` are invalid; omit the parameter to use the default.

### GET /api/products/{id}

Headers: **Public**. Path: `id` = product ID. No body/query parameters.
Success: **200**, `ProductResponse`, including unavailable products.
Error: **404** `Product not found`.

## 5. Admin categories

### POST /api/admin/categories

Headers: **Admin JSON**. No path/query parameters.

```json
{ "name": "Dairy" }
```

`name` is required, trimmed, nonblank, and at most 100 characters. Names are
unique ignoring case. Success: **201**, `CategoryResponse`, with
`Location: /api/admin/categories/{id}`. Errors: 400 for validation; **409**
`A category with this name already exists`.

### PUT /api/admin/categories/{id}

Headers: **Admin JSON**. Path: `id` = category ID. No query parameters.
Body and validation: same as category creation. Success: **200**,
`CategoryResponse`. Keeping the category's current name succeeds. Errors: 400
for validation; **404** `Category not found`; **409**
`A category with this name already exists` if another category has that name.

### DELETE /api/admin/categories/{id}

Headers: **Admin**. Path: `id` = category ID. No body/query parameters.
Success: **204**, no body. Errors: **404** `Category not found`; **409**
`Category contains products and cannot be deleted`. Delete or move all products
out of the category first, including unavailable products.

## 6. Admin products

### Product create/replace body

```json
{
  "name": "Whole Milk",
  "description": "Fresh milk, 1 litre.",
  "categoryId": 2,
  "price": 2.49,
  "unitOfMeasure": "L",
  "imageUrl": "https://example.com/milk.png",
  "available": true
}
```

| Field | Required | Validation and normalization |
| --- | --- | --- |
| `name` | Yes | Trimmed, nonblank, max 255 characters |
| `description` | No | String or null; trimmed, max 5000 characters |
| `categoryId` | Yes | Existing category ID; unknown ID is **400**, field `categoryId`: `Category does not exist` |
| `price` | Yes | Number greater than 0, at most 9999999999.99, at most 10 integer digits and 2 decimal places |
| `unitOfMeasure` | Yes | Exactly PIECE, KG, G, L, ML, or PACK |
| `imageUrl` | No | String or null; trimmed, max 1000 characters, absolute HTTP/HTTPS URL; blank becomes null |
| `available` | No | Boolean; omitted/null becomes true |

Only a URL is accepted for an image; there is no image upload endpoint.
Invalid enum JSON values return 400 `Malformed request body`.

### POST /api/admin/products

Headers: **Admin JSON**. No path/query parameters. Body: product create/replace
body above. Success: **201**, `ProductResponse`, with
`Location: /api/products/{id}`. Errors: 400 for invalid fields or category.

### PUT /api/admin/products/{id}

Headers: **Admin JSON**. Path: `id` = product ID. No query parameters.
Body: product create/replace body above. This is a **full replacement**:
all required fields must be supplied, omitted description/image URL are cleared,
and omitted/null `available` resets it to true. Success: **200**,
`ProductResponse`. Errors: 400 for invalid fields/category; **404**
`Product not found`.

### DELETE /api/admin/products/{id}

Headers: **Admin**. Path: `id` = product ID. No body/query parameters.
Success: **204**, no body. Error: **404** `Product not found`. Deletion also
removes that product from all carts. Existing order snapshot items remain.
Reads use the public catalog endpoints; there are no separate admin catalog GETs.

## 7. Shopping cart

All cart endpoints refer to the token owner's cart. ADMIN can also use these
endpoints for its own cart. There is no guest cart or public clear-cart endpoint.
Responses use current catalog prices/availability; the frontend should replace
its displayed cart with the returned `CartResponse` after a mutation.

### GET /api/cart

Headers: **Authenticated**. No body, path, or query parameters.
Success: **200**, `CartResponse`. Creates an empty cart if necessary. Items are
ordered by cart item ID ascending. `lineTotal = unitPrice × quantity`;
`totalAmount` sums line totals and `totalQuantity` sums quantities. Unavailable
items already in the cart remain visible and count toward totals.

### POST /api/cart/items

Headers: **Authenticated JSON**. No path/query parameters.

```json
{ "productId": 12, "quantity": 2 }
```

Both fields are required; `quantity` must be an integer from 1 to 99. An existing
product's quantity is increased, rather than replaced. The resulting per-product
quantity must also be at most 99. Success: **200**, `CartResponse`.
Errors: **400** for validation or overflow (field `quantity`,
`Maximum quantity per product is 99`); **404** `Product not found`; **409**
`Product is currently unavailable`. A rejected addition leaves the cart unchanged.

### PUT /api/cart/items/{productId}

Headers: **Authenticated JSON**. Path: `productId` = catalog product ID,
not a cart item ID. No query parameters.

```json
{ "quantity": 3 }
```

`quantity` is required, integer 1–99, and replaces the existing quantity.
Success: **200**, `CartResponse`. Errors: 400 for validation; **404**
`Item not found in cart`. Setting quantity does not recheck product availability;
an existing unavailable item can have its quantity changed. To remove an item,
use DELETE rather than quantity 0.

### DELETE /api/cart/items/{productId}

Headers: **Authenticated**. Path: `productId` = catalog product ID.
No body/query parameters. Success: **200**, `CartResponse`, including when the
item is absent. Deleting the last item returns an empty cart.

### GET /api/cart/recommendations

Headers: **Authenticated**. No body/path parameters.

| Query parameter | Type | Default | Rules |
| --- | --- | --- | --- |
| `limit` | Integer | `5` | Clamped to 1–20 by default; defaults/maximum are backend-configurable |

Example: `/api/cart/recommendations?limit=5`. Success: **200**,
`CartRecommendationsResponse`. These are **related products for your cart**,
based on cart contents, rather than a personal taste profile. The frontend
calls only this backend endpoint, never the external model service.

```json
{
  "source": "MODEL",
  "items": [
    {
      "product": {
        "id": 101,
        "name": "Bread",
        "price": 1.50,
        "unitOfMeasure": "PIECE",
        "available": true,
        "categoryId": 4,
        "categoryName": "Bakery"
      },
      "score": 3.42,
      "metrics": { "support": 0.012, "confidence": 0.41, "lift": 3.42 }
    }
  ]
}
```

Results exclude cart products, duplicates, unavailable products, and unknown IDs.
Keep response order; model scores sort descending. Any metric or the metrics
object may be absent. Fallback recommendations omit score and metrics entirely.

- Empty cart: `{ "source": "MODEL", "items": [] }`, without creating a cart.
- Model unavailable or no usable model results: `source: "FALLBACK"`; popularity
  among noncancelled orders, filled with other available products when needed.
- A partial usable model result is returned as-is, without fallback top-up.
- If fallback cannot return products, it returns `source: "FALLBACK"` with empty
  items. Model/fallback failure does not itself produce an HTTP error.
- Invalid noninteger limit returns **400**, field `limit`: `Invalid parameter value`.

Refresh recommendations after cart mutations. Do not assume the result has
exactly `limit` entries.

## 8. Customer orders

### POST /api/orders

Headers: **Authenticated JSON**. No path/query parameters.

```json
{
  "deliveryAddress": "10 Main Street, Kyiv",
  "contactPhone": "+380 (67) 123-45-67",
  "comment": "Call on arrival"
}
```

| Field | Required | Validation and normalization |
| --- | --- | --- |
| `deliveryAddress` | Yes | Trimmed, nonblank, max 500 characters |
| `contactPhone` | Yes | Remove spaces, parentheses and hyphens; remaining value must match `^\+?[0-9]{10,15}$`; normalized value is stored |
| `comment` | No | String or null; trimmed, max 1000 characters |

Success: **201**, `OrderResponse`, status `NEW`, with
`Location: /api/orders/{id}`. The backend copies the current cart's product IDs,
names, prices, and quantities into order snapshots, computes totals, and clears
the cart atomically. The client sends no items, prices, total, status, or user ID.
There is no online payment step. Refresh cart, recommendations, and order history
after success. Do not automatically retry checkout as if it were idempotent.

Errors: 400 for validation; **409** `Cart is empty`; **409**
`Some products are unavailable: <comma-separated product names>`. Rejected
checkout does not clear the cart or create an order. A removed user can produce
404 `User not found`.

### GET /api/orders

Headers: **Authenticated**. No body/path parameters. Query: `page`, `size` using
the common pagination rules. Example: `/api/orders?page=0&size=20`.
Success: **200**, `PageResponse<OrderSummaryResponse>`, current user's orders
only, sorted by `createdAt desc, id desc`. `itemsCount` sums quantities.
Invalid query parameter types return 400.

### GET /api/orders/{id}

Headers: **Authenticated**. Path: `id` = order ID. No body/query parameters.
Success: **200**, `OrderResponse`. Missing orders and orders belonging to
another user both return **404** `Order not found`. Order items keep their
snapshot values even if catalog products change or are deleted; productId does
not guarantee a currently accessible product detail page.

### POST /api/orders/{id}/cancel

Headers: **Authenticated**. Path: `id` = order ID. No body/query parameters.
Success: **200**, `OrderResponse`, status `CANCELLED`. Allowed only from `NEW`.
Does not restore cart contents. Errors: **404** `Order not found` for missing
or another user's orders; **409** `Order can no longer be cancelled` for other
statuses, including repeated cancellation.

## 9. Admin orders

### GET /api/admin/orders

Headers: **Admin**. No body/path parameters.

| Query parameter | Type | Default | Rules |
| --- | --- | --- | --- |
| `status` | OrderStatus | Omitted | Optional exact uppercase NEW, CONFIRMED, COMPLETED, CANCELLED; omit for all orders |
| `page` | Integer | `0` | Common pagination rules |
| `size` | Integer | `20` | Common pagination rules |

Example: `/api/admin/orders?status=NEW&page=0&size=20`. Success: **200**,
`PageResponse<AdminOrderSummaryResponse>`, all users, sorted by
`createdAt desc, id desc`. Invalid status or numeric parameters return 400
`Validation failed` with the corresponding field error.

### GET /api/admin/orders/{id}

Headers: **Admin**. Path: `id` = order ID. No body/query parameters.
Success: **200**, `AdminOrderResponse` (full order plus `userId`, `userEmail`).
Error: **404** `Order not found`.

### PATCH /api/admin/orders/{id}/status

Headers: **Admin JSON**. Path: `id` = order ID. No query parameters.

```json
{ "status": "CONFIRMED" }
```

`status` is required and must be an OrderStatus. Success: **200**,
`AdminOrderResponse`. Allowed transitions:

| Current status | Allowed new statuses |
| --- | --- |
| NEW | CONFIRMED, COMPLETED, CANCELLED |
| CONFIRMED | NEW, COMPLETED, CANCELLED |
| COMPLETED | None |
| CANCELLED | None |

Errors: **400** `Validation failed`, field `status`: `Status is required` for
omitted/null status; **400** `Malformed request body` for unknown or lowercase
enum values; **404** `Order not found`. Business conflicts are **409**:

- Setting the current status: `Order already has status X` (also for final statuses).
- Changing COMPLETED: `Cannot change status of a COMPLETED order`.
- Changing CANCELLED: `Cannot change status of a CANCELLED order`.

## 10. Errors

Protected endpoints can return **401** `Authentication required` for missing,
invalid, or expired tokens. All admin endpoints can additionally return **403**
`Access denied` for an authenticated USER. These apply in addition to each
endpoint's listed business/validation errors. Authentication may reject a
request before its body or parameter validation runs.

```json
{
  "timestamp": "2026-10-25T10:00:00Z",
  "status": 400,
  "message": "Validation failed",
  "path": "/api/cart/items",
  "fieldErrors": [
    { "field": "quantity", "message": "Quantity must be at least 1" }
  ]
}
```

```json
{
  "timestamp": "2026-10-25T10:00:00Z",
  "status": 409,
  "message": "Cart is empty",
  "path": "/api/orders"
}
```

| Status | Meaning / common messages |
| --- | --- |
| 400 | `Validation failed` with fieldErrors; `Malformed request body` for malformed JSON, missing required body, or invalid enum body values |
| 401 | `Authentication required`; login can instead return `Incorrect email or password` |
| 403 | `Access denied` for insufficient role |
| 404 | Resource-specific messages above; `Resource not found` for an unknown authenticated route |
| 405 | `Method not allowed` when a route is reached using an unsupported method |
| 409 | Business conflicts above; `Data conflict` for an uncaught persistence constraint conflict |
| 500 | `Unexpected error` |

For invalid numeric/path parameter types, message is `Validation failed`, with
`fieldErrors: [{ "field": "<parameter name>", "message": "Invalid parameter value" }]`.
Multiple validation errors may target the same field; do not assume exactly one.
`path` contains the request path without the query string. `fieldErrors` is
omitted when empty. Match HTTP status and field names for UI behavior, and show
the provided message as needed; do not expect stack traces or SQL details.
An unknown protected route without a valid token can return 401 before 404.

## 11. Frontend integration flow

1. Browse categories/products publicly. Keep filter/sort/page state in query
   parameters and handle unavailable product cards using `available`.
2. Register if needed, then log in; use the returned role for navigation and
   token for requests. The backend enforces access independently of the UI.
3. Read the cart. After each add/update/delete, use the returned cart and request
   fresh related products. Cart quantities and prices are server-authoritative.
4. Submit only delivery/contact/comment fields for checkout. On 409, display
   the reason and reload the cart if necessary. On 201, show the returned order
   and refresh the emptied cart.
5. Use order summaries for lists and detail responses for order pages. Offer
   customer cancellation only for NEW; handle 409 if its status changed meanwhile.
6. Admin screens reuse public catalog reads, use admin catalog writes, and use
   admin order list/detail/status endpoints. Refresh affected data after writes.

There are no logout, refresh-token, password-reset, payment, upload, or customer
account-management endpoints in this contract.

## 12. External ML recommendation service contract

The backend calls this service to obtain related products for the current cart.
The frontend continues to call `GET /api/cart/recommendations` (section 7).
The ML service is a separate application; this repository implements its HTTP
client, not the ML endpoint. Recommendations describe cart context, not a
personal taste profile.

### Endpoint and headers

```http
POST http://localhost:8000/v1/recommendations
Content-Type: application/json
```

The general URL is `{ml.base-url}/v1/recommendations`. Trailing slashes on the
configured base URL are removed before appending the path. There are no path
or query parameters. The backend sends no Authorization header, JWT, cookies,
or API key. Successful responses should use `Content-Type: application/json`.
Unlike the frontend API, external request and response fields use **snake_case**.

### Request body

```json
{
  "cart_item_ids": [12, 45, 78],
  "max_k": 10
}
```

| Field | Type | Required | Contract |
| --- | --- | --- | --- |
| `cart_item_ids` | Array of integer product IDs | Yes | Distinct catalog product IDs, at most 50; these are product IDs, not cart item row IDs |
| `max_k` | Integer | Yes | Number of candidate recommendations requested, between 1 and 50 |

The backend preserves the cart's product order when removing duplicates and
sends the first 50 distinct IDs if there are more. Cart quantities, prices,
user IDs, and user profiles are not sent. An empty cart bypasses this call.

For the public recommendation endpoint's effective limit `L`, the backend asks
the primary provider for `min(L * 2, 50)` candidates. The ML provider clamps
the HTTP `max_k` to 1–50. For example, the default frontend limit of 5 produces
`max_k: 10`. Extra candidates allow for later catalog filtering. Products in the
entire cart are excluded from results, including cart IDs beyond the 50 sent.

### Successful response

Return **200** with this JSON body:

```json
{
  "recommendations": [
    {
      "product_id": 101,
      "score": 3.42,
      "metrics": {
        "support": 0.012,
        "confidence": 0.41,
        "lift": 3.42
      }
    }
  ]
}
```

| Field | Type | Contract |
| --- | --- | --- |
| `recommendations` | Array | Required, non-null; an empty array is a valid result with no recommendations |
| `recommendations[].product_id` | Integer product ID | Must be positive and fit Java Long to identify a usable product |
| `recommendations[].score` | Number | Required for a usable entry; must be finite; higher means more relevant; zero and negative finite scores are accepted |
| `recommendations[].metrics` | Object or null | Optional; may be omitted or null |
| `metrics.support` | Number or null | Optional rule support |
| `metrics.confidence` | Number or null | Optional rule confidence |
| `metrics.lift` | Number or null | Optional rule lift; need not equal score |

Metrics can be partially supplied, for example
`"metrics": { "confidence": 0.41, "support": null }`. No metrics are required
to use a recommendation. Return fewer candidates than requested when appropriate;
use `{ "recommendations": [] }` when there are no matches.

### Backend normalization and catalog filtering

The ML response may be unsorted or contain duplicate, in-cart, or unknown IDs.
After a successfully decoded response, the ML provider:

1. Drops null entries, null/non-positive product IDs, and null/non-finite scores.
2. Drops IDs already in the full cart.
3. Removes duplicate product IDs, keeping the entry with the highest score
   and that entry's metrics. Equal-score duplicates retain the first entry.
4. Sorts by score descending, then product ID ascending, and truncates to the
   provider's requested candidate limit.

`SmartCartService` then loads the candidates in one catalog query, removes
unknown/deleted and unavailable products, preserves candidate order, and returns
at most the frontend's effective limit. Catalog names, prices, categories,
images, and availability come from the backend; the model supplies IDs, scores,
and optional rule metrics only.

If at least one model candidate survives, the frontend receives `source: "MODEL"`.
A partial result is not topped up with fallback products. External fields
`product_id` and `recommendations` are converted to the frontend response's
`items[].product` representation; they are not forwarded as raw ML JSON.

### Failures and fallback

The expected success status is 200. The current client accepts other 2xx statuses
only when their body also decodes to the required response shape. Every non-2xx
status, including redirects, is treated as unavailable; redirects are not followed.

| Condition | Backend behavior |
| --- | --- |
| Base URL blank or null | Skip HTTP and treat the provider as unavailable |
| Connection error, connect/read timeout, or non-2xx status | Treat the provider as unavailable |
| Malformed/unreadable JSON, incompatible field types, missing body, or absent/null recommendations array | Treat the provider as unavailable |
| Empty array or no candidates survive filtering | Use fallback |
| At least one usable candidate survives | Return model results without fallback top-up |

The ML error response body has no required schema: the backend does not expose
or rely on it. Provider call/decoding problems become
`RecommendationUnavailableException` and produce one WARN line with a sanitized
exception message; payloads and cart contents are not logged. There are no
application-level retries, caching, or circuit breakers.

Fallback selects available products outside the cart by total ordered quantity,
excluding CANCELLED orders, with product ID ascending for ties. It fills any
remaining slots with other available products by ID descending. The frontend
receives **200**, `source: "FALLBACK"`, and items without score/metrics. If fallback
cannot produce items, the response is `{ "source": "FALLBACK", "items": [] }`.
An ML failure itself is not propagated as a frontend HTTP error.

### Configuration

All keys below are under `egastro.recommendations` in `application.yml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `ml.base-url` | `${ML_SERVICE_URL:http://localhost:8000}` | External service base URL; blank disables HTTP calls |
| `ml.connect-timeout` | `PT0.5S` | Connection timeout, 500 milliseconds |
| `ml.read-timeout` | `PT2S` | Read timeout, 2 seconds |
| `default-limit` | `5` | Frontend recommendation limit when omitted |
| `max-limit` | `20` | Maximum effective frontend recommendation limit |

Timeouts must be positive. Limits must satisfy
`1 <= default-limit <= max-limit`. The external `max_k` remains capped at 50
even if the configured frontend maximum is higher.

### Internal provider interface

Both recommendation strategies implement this Java contract:

```java
List<ScoredRecommendation> recommend(List<Long> cartProductIds, int limit);
```

It returns at most `limit` entries, best first, without duplicate or cart IDs.
Each `ScoredRecommendation` contains `productId`, `score`, and `metrics`.
The ML implementation supplies scores and optional metrics; the popularity
implementation supplies null score and metrics. An empty result is valid.
`RecommendationUnavailableException` indicates that a provider cannot produce
a result; the fallback implementation catches failures and returns an empty list.
This Java interface is internal and does not add another HTTP endpoint.
