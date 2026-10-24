# eGastro Bruno collection

In Bruno, choose **Open Collection** and select this `bruno` directory, then
select the **Local** environment. Start the backend using [RUN.md](../RUN.md).

There is one request for each HTTP method/path pair: 24 backend API requests,
OpenAPI JSON, Swagger UI, and the documented CORS preflight. Query variations,
failure cases, and customer/admin login reuse the same request. The external
recommendation service contract and the nonexistent `/api/unknown` route are
not backend endpoints in this collection.

## Setup

1. Adjust `baseUrl` and credentials in **Local** if needed.
2. Send **Register** once for `userEmail`/`userPassword`. On later runs, log in
   to the existing account or change the email.
3. Set `loginAs` to `USER` and send **Login**. Set it to `ADMIN` and send the
   same **Login** request again. Both tokens remain available for later requests.
4. Send **Create category**, **Rename category**, then **Create product**.
   Creation responses automatically capture `categoryId` and `productId`.
   Alternatively, fill these IDs manually from the catalog.
5. Use cart, recommendation, and checkout requests as described in `RUN.md`.
   Checkout automatically captures `orderId`.

Tokens and captured IDs use Bruno runtime variables (`bru.setVar`), which
override the blank environment placeholders. They are not written to disk;
repeat login/setup or manually fill IDs when starting a new session. No script
logs tokens or passwords. Login selects credentials using `loginAs`; protected
requests select `userToken` or `adminToken` explicitly in their Auth tab.

## Use requests individually

Numbers organize the requests; this is an interactive collection, not a single
successful end-to-end runner sequence. For example:

- **Replace product** follows RUN.md's example with `available: false`. Set it
  to `true` and resend before adding that product to a cart or checking out.
- **Remove cart item** can empty the cart. Add an item again before checkout.
- **Checkout** clears the cart. Add items and reuse checkout to create another order.
- **Cancel customer order** makes the order final. Create a fresh order before
  testing **Change order status**. A repeated identical status returns 409.
- Run **Delete product** and **Delete category** after the other checks.

Optional `category`, `q`, and admin order `status` filters are disabled in the
Params tab. Enable them when needed. Use the same requests for validation,
missing-ID and authorization checks from RUN.md; change the body, parameters,
ID, or Auth rather than creating copies. Expected statuses and prerequisites
are also recorded in each request's Docs tab.
