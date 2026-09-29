# egastro-server

Backend for "єГастро".

## Tech stack
- Java 17
- Spring Boot 3.3.4
- Maven
- H2 in-memory database

## Run locally
```bash
./mvnw spring-boot:run
```

Or with Maven:
```bash
mvn spring-boot:run
```

## Health check
```bash
curl http://localhost:8080/api/health
```
