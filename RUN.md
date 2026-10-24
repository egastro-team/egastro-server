# Run and manually test eGastro

This guide covers the implemented T01/T02 application: registration, login,
current user, JWT security, and API documentation. Catalog, cart, recommendations,
and order endpoints described in `README.md` belong to later tasks and are not
implemented yet.

## 1. Prerequisites

- JDK 21, with `JAVA_HOME` pointing to the JDK directory.
- Docker Desktop (or Docker Engine with Docker Compose), running.
- Internet access for the first Maven dependency download and PostgreSQL image pull.
- Available local ports: `5432` for PostgreSQL and `8080` for the backend.
- For the request examples below: Windows PowerShell and `curl.exe`.

Run commands from the repository root. Maven is provided by the wrapper; a
separate Maven installation is unnecessary.

```powershell
Set-Location D:\Uni\sem1\egastro-server
java -version
docker version
docker compose version
```

If Java is not on PATH, set your installed JDK location for the current terminal:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

## 2. Start PostgreSQL and the application

```powershell
docker compose up -d
docker compose ps
docker compose exec postgres pg_isready -U egastro -d egastro
```

Wait for PostgreSQL to report `accepting connections`. If it does not, inspect:

```powershell
docker compose logs postgres
```

Compile, then run the backend in this terminal:

```powershell
./mvnw.cmd -q compile
./mvnw.cmd spring-boot:run
```

On macOS/Linux, use `./mvnw -q compile` and `./mvnw spring-boot:run` instead.
The backend is ready after the log reports `Started EgastroApplication` and
Tomcat listening on port `8080`. Hibernate creates or updates the schema.
Keep this terminal running and use a second PowerShell terminal for requests.

The defaults are:

| Setting | Default | Override |
| --- | --- | --- |
| Database URL | `jdbc:postgresql://localhost:5432/egastro` | `DB_URL` |
| Database username | `egastro` | `DB_USERNAME` |
| Database password | `egastro` | `DB_PASSWORD` |
| JWT secret | Local placeholder of at least 32 bytes | `JWT_SECRET` |
| JWT lifetime | `PT24H` (86,400 seconds) | `egastro.security.jwt.ttl` in `application.yml` |
| CORS origins | `http://localhost:3000`, `http://localhost:5173` | `egastro.security.cors.allowed-origins` in `application.yml` |
| Initial admin email | `admin@egastro.local` | `ADMIN_EMAIL` |
| Initial admin password | `Admin12345` | `ADMIN_PASSWORD` |

Set environment overrides in the terminal that starts the backend, before
running Maven. For example:

```powershell
$env:JWT_SECRET = 'replace-with-your-own-local-secret-at-least-32-bytes'
```

Changing the JWT secret invalidates previously issued tokens. Admin bootstrap
creates an `ADMIN` only if its email does not already exist. Restarting, or
changing `ADMIN_PASSWORD`, does not reset an existing user's password or role.
Database environment overrides affect the backend connection; the Compose
database itself uses the credentials in `docker-compose.yml`.

## 3. Request setup and response conventions

In the second PowerShell terminal:

```powershell
$base = 'http://localhost:8080'
$email = 'customer@example.com'
$password = 'Customer123'
```

Run the examples in order. Use a new email if you have already registered this
account. The examples use `curl.exe` explicitly because Windows PowerShell can
map `curl` to a different command. JSON is piped into curl to avoid native
command-line quote escaping issues. `-i` includes HTTP status and headers.

IDs, timestamps, and tokens in the response examples are illustrative. An admin
is normally inserted first, so a newly registered customer need not have ID 1.
All API errors use this shape:

```json
{
  "timestamp": "2026-10-24T12:00:00Z",
  "status": 401,
  "message": "Authentication required",
  "path": "/api/auth/me"
}
```

Validation failures also contain `fieldErrors`. Other errors omit that field.

## 4. POST /api/auth/register

### Success: 201 Created

```powershell
@{ email = "  $($email.ToUpperInvariant())  "; password = $password; role = 'ADMIN' } |
    ConvertTo-Json -Compress |
    curl.exe -i "$base/api/auth/register" -H 'Content-Type: application/json' --data-binary '@-'
```

```json
{
  "id": 2,
  "email": "customer@example.com",
  "role": "USER"
}
```

Email is trimmed and stored lowercase. The supplied `role` is ignored: every
registration creates a `USER`. The response contains no token or password/hash.
Passwords are preserved exactly, including any spaces.

### Duplicate email: 409 Conflict

Run the same registration again, including with different email capitalization:

```powershell
@{ email = $email; password = $password } |
    ConvertTo-Json -Compress |
    curl.exe -i "$base/api/auth/register" -H 'Content-Type: application/json' --data-binary '@-'
```

```json
{
  "timestamp": "2026-10-24T12:00:00Z",
  "status": 409,
  "message": "A user with this email already exists",
  "path": "/api/auth/register"
}
```

### Invalid fields: 400 Bad Request

```powershell
'{"email":"not-an-email","password":"short"}' |
    curl.exe -i "$base/api/auth/register" -H 'Content-Type: application/json' --data-binary '@-'
```

```json
{
  "timestamp": "2026-10-24T12:00:00Z",
  "status": 400,
  "message": "Validation failed",
  "path": "/api/auth/register",
  "fieldErrors": [
    { "field": "email", "message": "Invalid email format" },
    { "field": "password", "message": "Password must be at least 8 characters long" },
    { "field": "password", "message": "Password must contain at least one digit" }
  ]
}
```

Use these bodies in the same command to check other validation rules:

| Input | Expected field error(s), with HTTP 400 |
| --- | --- |
| `{"email":"","password":"Customer123"}` | `email`: `Email is required` |
| `{"email":"new@example.com"}` | `password`: `Password is required` |
| `{"email":"new@example.com","password":"abcdefgh"}` | `password`: `Password must contain at least one digit` |
| `{"email":"new@example.com","password":"abc1"}` | `password`: `Password must be at least 8 characters long` |
| Email longer than 254 characters | `email`: `Email must be at most 254 characters long`; an email format error may also apply |
| Password longer than 72 characters | `password`: `Password must be at most 72 characters long`; other violated rules also appear |

Each violated rule produces its own field error. BCrypt additionally limits
passwords to 72 UTF-8 bytes, so some multi-byte passwords hit the limit before
72 characters; these receive the same maximum-length error.

### Malformed JSON: 400 Bad Request

```powershell
'{"email":' |
    curl.exe -i "$base/api/auth/register" -H 'Content-Type: application/json' --data-binary '@-'
```

```json
{
  "timestamp": "2026-10-24T12:00:00Z",
  "status": 400,
  "message": "Malformed request body",
  "path": "/api/auth/register"
}
```

## 5. POST /api/auth/login

### Success: 200 OK

Capture the login response and token for the following requests:

```powershell
$loginBody = @{ email = $email; password = $password } | ConvertTo-Json -Compress
$login = $loginBody |
    curl.exe -sS "$base/api/auth/login" -H 'Content-Type: application/json' --data-binary '@-' |
    ConvertFrom-Json
$token = $login.accessToken
$login | ConvertTo-Json -Depth 4
```

```json
{
  "accessToken": "<signed JWT>",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 2,
    "email": "customer@example.com",
    "role": "USER"
  }
}
```

`expiresIn` is in seconds and follows the configured JWT lifetime. Email lookup
ignores capitalization and trims surrounding spaces.

### Wrong password or unknown email: 401 Unauthorized

```powershell
@{ email = $email; password = 'Wrong12345' } |
    ConvertTo-Json -Compress |
    curl.exe -i "$base/api/auth/login" -H 'Content-Type: application/json' --data-binary '@-'

'{"email":"unknown@example.invalid","password":"Customer123"}' |
    curl.exe -i "$base/api/auth/login" -H 'Content-Type: application/json' --data-binary '@-'
```

Both return:

```json
{
  "timestamp": "2026-10-24T12:00:00Z",
  "status": 401,
  "message": "Incorrect email or password",
  "path": "/api/auth/login"
}
```

### Missing or blank fields: 400 Bad Request

```powershell
'{"email":" ","password":" "}' |
    curl.exe -i "$base/api/auth/login" -H 'Content-Type: application/json' --data-binary '@-'
```

```json
{
  "timestamp": "2026-10-24T12:00:00Z",
  "status": 400,
  "message": "Validation failed",
  "path": "/api/auth/login",
  "fieldErrors": [
    { "field": "email", "message": "Email is required" },
    { "field": "password", "message": "Password is required" }
  ]
}
```

Malformed JSON also returns 400 `Malformed request body`, with the login path.

## 6. GET /api/auth/me

### Success: 200 OK

Use the token captured by the successful customer login:

```powershell
curl.exe -i "$base/api/auth/me" -H "Authorization: Bearer $token"
```

```json
{
  "id": 2,
  "email": "customer@example.com",
  "role": "USER"
}
```

### Missing, malformed, tampered, or expired token: 401 Unauthorized

```powershell
curl.exe -i "$base/api/auth/me"
curl.exe -i "$base/api/auth/me" -H 'Authorization: Bearer not-a-jwt'
curl.exe -i "$base/api/auth/me" -H "Authorization: Bearer x$token"
```

Each returns:

```json
{
  "timestamp": "2026-10-24T12:00:00Z",
  "status": 401,
  "message": "Authentication required",
  "path": "/api/auth/me"
}
```

An expired token returns the same response. To check expiry quickly, stop the
backend and launch it with a temporary two-second lifetime:

```powershell
./mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--egastro.security.jwt.ttl=PT2S'
```

In the request terminal, repeat the successful login to obtain a fresh `$token`,
run `Start-Sleep -Seconds 3`, then call `/me`. Expect 401. Restart normally and
log in again afterward to restore the default 24-hour flow.

### User removed after token issuance: 404 Not Found

If a token is still valid but its user has been removed directly from the local
database, `/me` returns:

```json
{
  "timestamp": "2026-10-24T12:00:00Z",
  "status": 404,
  "message": "User not found",
  "path": "/api/auth/me"
}
```

There is currently no user-deletion API.

## 7. Admin access and route checks

Log in through the same endpoint using the bootstrap credentials (or your
configured credentials):

```powershell
'{"email":"admin@egastro.local","password":"Admin12345"}' |
    curl.exe -sS "$base/api/auth/login" -H 'Content-Type: application/json' --data-binary '@-' |
    ConvertFrom-Json |
    Set-Variable -Name adminLogin
$adminToken = $adminLogin.accessToken
curl.exe -i "$base/api/auth/me" -H "Authorization: Bearer $adminToken"
```

Login returns 200 with `user.role` equal to `ADMIN`; `/me` returns 200 with the
admin's ID, email, and role. Incorrect admin credentials receive the same 401
`Incorrect email or password` response as customer login.

With a fresh customer `$token`, check the admin gate:

```powershell
curl.exe -i "$base/api/admin/orders" -H "Authorization: Bearer $token"
```

```json
{
  "timestamp": "2026-10-24T12:00:00Z",
  "status": 403,
  "message": "Access denied",
  "path": "/api/admin/orders"
}
```

The following checks distinguish authorization from endpoint implementation:

| Request | Expected result at T02 |
| --- | --- |
| `GET /api/admin/orders`, no token | 401 `Authentication required` |
| `GET /api/admin/orders`, valid customer token | 403 `Access denied` |
| `GET /api/admin/orders`, valid admin token | 404 `Resource not found` (controller not implemented yet) |
| `GET /api/products`, no token | 404 `Resource not found` (public rule exists, controller does not yet) |
| `GET /api/unknown`, valid token | 404 `Resource not found` |
| `GET /api/unknown`, no token | 401 `Authentication required` |

## 8. API documentation and CORS

Open [Swagger UI](http://localhost:8080/swagger-ui.html) in a browser. It redirects
to the Swagger UI page. Expand an operation, choose **Try it out**, enter the
request body, and choose **Execute**. After login, use **Authorize** and paste
the access token without the `Bearer ` prefix to call `/me`.

Both documentation routes are public:

```powershell
curl.exe -i "$base/v3/api-docs"
curl.exe -i -L "$base/swagger-ui.html"
```

Expect 200 JSON containing the OpenAPI document and 200 HTML after following
the Swagger UI redirect, respectively. These routes do not require a token;
a malformed bearer token does not block them either.

Check a browser preflight request:

```powershell
curl.exe -i -X OPTIONS "$base/api/auth/me" -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: GET' -H 'Access-Control-Request-Headers: Authorization,Content-Type'
```

Expect 200 with `Access-Control-Allow-Origin: http://localhost:5173`, allowed
methods including GET, POST, PUT, PATCH, DELETE, OPTIONS, and allowed headers
Authorization and Content-Type. `Access-Control-Allow-Credentials` is absent.
The localhost origin on port 3000 is also allowed. An unlisted origin's
preflight receives 403 from the CORS processor (its response is not the API's
standard JSON error body).

## 9. Stop and rerun

Stop the backend with **Ctrl+C** in its terminal. Stop the database with:

```powershell
docker compose down
```

The database volume is retained, so registered accounts still exist when you
restart. Repeating registration then returns 409; log in with the existing
account or choose another email. There is no logout endpoint: discard the client
token to log out.

## 10. Troubleshooting

| Symptom | Check |
| --- | --- |
| `JAVA_HOME` error or Java not found | Point `JAVA_HOME` to JDK 21 and add its `bin` directory to PATH in the launch terminal. |
| Docker cannot connect to its engine | Start Docker Desktop and wait for it to become ready. |
| Database connection refused | Run `docker compose ps`, `docker compose logs postgres`, and the `pg_isready` command above; verify port 5432. |
| Database authentication fails | Check backend `DB_*` overrides against the existing database credentials. |
| Port 8080 already in use | Stop the previous backend instance before starting another. |
| Requests cannot connect | Keep the backend terminal running and wait for startup to finish. |
| `/me` unexpectedly returns 401 | Log in again, use the captured `accessToken`, and check token lifetime and whether the JWT secret changed. |
| Admin login fails after changing environment variables | Bootstrap leaves existing users unchanged; use the credentials originally used to create that account. |
| Registration unexpectedly returns 409 | The email is already stored, including case-insensitive matches. |

The current Compose file does not mount `docker/init` into PostgreSQL, so SQL
files in that directory are not automatically executed by this setup.
