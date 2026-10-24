# Database configuration using LuxWay as a reference

LuxWay uses Spring Boot 3.5.10, Spring Data JPA, and PostgreSQL 16.2 for normal application runs. Tests use H2 instead. The setup below reproduces its database configuration in another Spring Boot application, such as Egastro.

## 1. Add the Maven dependencies

With the Spring Boot parent managing dependency versions, include these dependencies in `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
```

These match [LuxWay's pom.xml](pom.xml). Reload the Maven project in the IDE after updating dependencies.

## 2. Configure the application datasource

Add or merge the following into `src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/luxway}
    username: ${DB_USERNAME:luxway}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: none
```

This is the database section from [LuxWay's main configuration](src/main/resources/application.yaml). Merge it into an existing `spring:` block rather than creating duplicate YAML keys.

The values after `:` inside the placeholders are defaults. Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` to override them. The database name in the URL, username, and password must match the PostgreSQL instance you actually connect to. The example credentials are for local development.

Spring Boot configures the datasource through `spring.datasource.*` and can infer the PostgreSQL JDBC driver from the URL when the driver dependency is available. You do not need to add `jakarta.persistence.jdbc.url` for this standard setup. See the [Spring Boot 3.5 datasource documentation](https://docs.spring.io/spring-boot/3.5/reference/data/sql.html).

## 3. Start PostgreSQL

LuxWay's [docker-compose.yml](docker-compose.yml) defines:

```yaml
services:
  postgres:
    image: postgres:16.2
    container_name: postgres_db
    restart: unless-stopped
    environment:
      POSTGRES_USER: luxway
      POSTGRES_PASSWORD: password
      POSTGRES_DB: luxway
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U luxway -d luxway"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

From the directory containing the Compose file, run:

```powershell
docker compose up -d postgres
docker compose ps
docker compose logs postgres
```

Wait until PostgreSQL is ready to accept connections. Verify the database and role inside the container:

```powershell
docker compose exec postgres pg_isready -U luxway -d luxway
docker compose exec postgres psql -U luxway -d luxway -c "SELECT current_database(), current_user;"
```

These checks verify the container's database. The application must also be able to reach the published host port with its configured credentials. On Windows, check that port with:

```powershell
Test-NetConnection localhost -Port 5432
```

If LuxWay's database is already running, you can reuse it for a connection check without starting another container. For a separate Egastro database, use a distinct database and credentials and update both Compose and the datasource settings consistently. Two containers cannot publish the same host port or share the same container name. For example, a mapping of `5433:5432` requires `jdbc:postgresql://localhost:5433/egastro` for an application running on the host.

PostgreSQL image initialization settings create the database and role on first initialization of an empty data directory. Changing `POSTGRES_DB`, `POSTGRES_USER`, or `POSTGRES_PASSWORD` later does not update an existing database; update the existing database/role explicitly or use a separate fresh development volume.

## 4. Run the application

For a host application using the defaults above, run from its project root:

```powershell
.\mvnw.cmd spring-boot:run
```

To override the defaults in the current PowerShell session:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/luxway'
$env:DB_USERNAME = 'luxway'
$env:DB_PASSWORD = 'password'
.\mvnw.cmd spring-boot:run
```

For an IntelliJ run configuration, set the same environment variables before launching the application's main class. A `.env` file by itself is not automatically loaded by a normally launched Spring Boot application.

`localhost` is correct when the application runs on your machine and PostgreSQL publishes port 5432. If the application runs in another container on the same Compose network, use `jdbc:postgresql://postgres:5432/luxway`, where `postgres` is the Compose service name.

### Schema behavior and the local profile

The main configuration uses `ddl-auto: none`: Hibernate does not create tables. Supply an existing schema or initialize it separately before using features that need tables.

LuxWay's [application-local.yaml](src/main/resources/application-local.yaml) overrides this with `ddl-auto: create-drop`. To reproduce that behavior, add the following to the other application's `src/main/resources/application-local.yaml`:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop
```

Activate it explicitly:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The profile inherits the main datasource settings. `create-drop` recreates mapped tables at startup and drops them at shutdown; use it only with disposable development data. It is not needed to resolve a database connection failure.

## 5. Keep H2 configuration limited to tests

LuxWay includes this test dependency:

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

Its [unit test configuration](src/test/resources/application.yaml) and [integration test configuration](src/it/resources/application.yaml) use:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
```

The explicit H2 dialect belongs to tests. Normal application runs still require PostgreSQL. LuxWay adds `src/it/resources` to Maven's test resources using `build-helper-maven-plugin`; a different application needs equivalent configuration if it uses that directory.

## Troubleshooting the reported dialect error

`Unable to determine Dialect without JDBC metadata` means Hibernate could not obtain the metadata needed to select a dialect. The supplied stack trace does not identify why metadata was unavailable. Check the log messages **before** `Application run failed` for a JDBC connection error.

| Earlier error or condition | What to check |
| --- | --- |
| Connection refused or timeout | PostgreSQL is running, the host/port are correct, and the application can reach that port. |
| Password authentication failed | The configured credentials match the actual database role, including any previously initialized volume. |
| Database does not exist | The final URL segment names a database that exists. |
| Driver cannot be loaded | The PostgreSQL dependency is on the application's runtime classpath; reload Maven. |
| Settings seem ignored | The YAML is in `src/main/resources`, the intended profile is active, and environment variables or launch arguments are not overriding it. |
| Application runs in Docker | Use the database service hostname on the shared network instead of `localhost`. |

LuxWay's normal configuration does not set a PostgreSQL dialect. Hibernate detects it from the connected database. An explicit `spring.jpa.database-platform: org.hibernate.dialect.PostgreSQLDialect` can select a dialect, but cannot repair an unreachable database, invalid credentials, or a missing database. See [Spring Boot's JPA configuration documentation](https://docs.spring.io/spring-boot/3.5/how-to/data-access.html#howto.data-access.configure-jpa-properties).

For Egastro, first match the Maven dependencies, datasource properties, and running PostgreSQL instance described above. If startup still fails, use the earlier JDBC error to identify the remaining connection problem.
