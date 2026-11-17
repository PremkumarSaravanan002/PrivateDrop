# PrivateDrop Backend

PrivateDrop Backend is an early-stage Spring Boot service for storing **client-encrypted** secrets and revealing them through a random, view-limited access token. The server is designed never to handle plaintext: callers submit an already-encrypted payload and nonce, and the API returns that same encrypted material when a valid token claims a view.

> **Status: early development / work in progress — not production-ready.** This repository contains backend APIs only. There is no frontend, no browser-side encryption/decryption, and no complete end-to-end secret-sharing workflow here.

## What it is intended to do

A sender encrypts a secret outside this backend, stores the ciphertext with an expiry and a maximum number of views, and shares the returned secret ID together with a one-time-delivered access token. A recipient supplies both to reveal the ciphertext; the server enforces expiry and view limits and returns only opaque encrypted bytes. Decryption is intended to happen in a future client, not in this service.

## How the application is designed to work

- The backend stores encrypted payload bytes, a nonce, access metadata (expiry, maximum views, view count) and a **SHA-256 hash** of a random access token. It never stores the raw token.
- The raw token is returned only in the creation response, encoded as Base64URL (encoding, not encryption).
- Retrieval supplies the token in the `X-Access-Token` header. A single conditional database `UPDATE` atomically checks the secret ID, token hash, expiry and remaining views, and increments the view count. The encrypted payload and nonce are returned only after that claim succeeds.
- An unavailable secret (wrong token, expired, or no views remaining) produces the same generic HTTP 404 response.

## Current architecture and technology stack

**IMPLEMENTED**

- **Language / build:** Java 21, Maven wrapper (`mvnw`, `mvnw.cmd`), Spring Boot 4.1.2 parent, `spring-boot-maven-plugin`.
- **Web:** `spring-boot-starter-webmvc` (REST controllers).
- **Data:** `spring-boot-starter-data-jpa`, Spring Data JPA, PostgreSQL JDBC driver (runtime scope).
- **Security:** `spring-boot-starter-security` (HTTP Basic, CSRF disabled).
- **Validation / ops:** `spring-boot-starter-validation`, `spring-boot-starter-actuator`.
- **Testing:** Spring Boot test starters; the only test class is a `@SpringBootTest` context-load test.
- **Layering:** `controller` → `service` → `repository` → JPA `entity`, with `dto`, `config` (`SecurityConfig`) and `exception` (`GlobalExceptionHandler`) cross-cutting packages. `spring.jpa.open-in-view=false` keeps database access in the service layer.

## Features

### IMPLEMENTED

- Spring Boot application bootstrap and layered package structure.
- Demo endpoints: greeting, echo (bean-validated) and simple note persistence.
- **Secret creation** (`POST /api/v1/secrets`): validates payload length (≥ 16 bytes), nonce length (exactly 12 bytes), future expiry and `maxViews ≥ 1`; generates a 32-byte `SecureRandom` token; stores its SHA-256 hash; persists the encrypted payload and nonce.
- **Secret retrieval** (`POST /api/v1/secrets/{secretId}/reveal`): decodes the `X-Access-Token` header, hashes it, atomically claims one view, then returns the encrypted payload and nonce in a `@Transactional` service method.
- Generic HTTP 404 handling for unavailable secrets, and HTTP 400 handling for bean-validation failures, via `GlobalExceptionHandler` / `ApiErrorResponse`.
- PostgreSQL persistence with a database check constraint on view counts.
- **Manual concurrency verification (not an automated test):** two concurrent PowerShell background jobs against a secret with one allowed view produced one HTTP 200 and one HTTP 404; the database view count was 1 afterwards.

### PLANNED / NOT IMPLEMENTED

- Frontend, browser-side encryption/decryption and an end-to-end share/reveal user experience.
- Public, token-based recipient access (all endpoints currently require HTTP Basic authentication).
- Secret revocation, deletion, status management and expiry cleanup jobs.
- Consistent API error mapping for malformed input, and automated tests for the secret flows.
- Production hardening (TLS enforcement, secrets management, abuse controls, audit logging, migration strategy).

## API documentation

All endpoints require HTTP Basic authentication and, for JSON bodies, `Content-Type: application/json`. No custom user store or credentials are configured in this repository; Spring Boot's default auto-configured user applies. Replace every `<...>` placeholder below — these are not real credentials, tokens or encrypted data.

| Method | Path | Headers | Description |
|---|---|---|---|
| GET | `/api/v1/hello` | `Authorization` | Demo greeting; returns `Hello from PrivateDrop!!` |
| POST | `/api/v1/echo` | `Authorization`, `Content-Type` | Validates `message` and echoes it |
| POST | `/api/v1/notes` | `Authorization`, `Content-Type` | Persists a note (`message` becomes `content`) |
| POST | `/api/v1/secrets` | `Authorization`, `Content-Type` | Creates a secret; returns `secretId` and `accessToken` |
| POST | `/api/v1/secrets/{secretId}/reveal` | `Authorization`, `X-Access-Token` | Claims one view and returns the encrypted payload and nonce |

### Create a secret

```http
POST /api/v1/secrets HTTP/1.1
Authorization: Basic <BASIC_AUTH_CREDENTIALS>
Content-Type: application/json

{
  "encryptedPayload": "<BASE64_CIPHERTEXT_AT_LEAST_16_BYTES>",
  "nonce": "<BASE64_NONCE_EXACTLY_12_BYTES>",
  "expiresAt": "2030-01-01T00:00:00Z",
  "maxViews": 1
}
```

Successful response (`200 OK`):

```json
{
  "secretId": "00000000-0000-0000-0000-000000000000",
  "accessToken": "<BASE64URL_TOKEN_RETURNED_BY_CREATE>"
}
```

`encryptedPayload`, `nonce` and `accessToken` above are placeholders. The token is 32 random bytes encoded as unpadded Base64URL and is the only time the raw token is returned.

### Reveal a secret

```http
POST /api/v1/secrets/00000000-0000-0000-0000-000000000000/reveal HTTP/1.1
Authorization: Basic <BASIC_AUTH_CREDENTIALS>
X-Access-Token: <BASE64URL_TOKEN_RETURNED_BY_CREATE>
```

Successful response (`200 OK`) — `byte[]` fields are rendered as Base64 strings by Jackson's default JSON handling:

```json
{
  "encryptedPayload": "<BASE64_CIPHERTEXT>",
  "nonce": "<BASE64_NONCE>"
}
```

Unavailable secret (`404 Not Found`) — used for a wrong token, an expired secret, or no views remaining:

```json
{
  "status": 404,
  "error": "SECRET_UNAVAILABLE",
  "message": "Secret is unavailable"
}
```

Bean-validation failure example (`400 Bad Request`, e.g. empty echo message):

```json
{
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Message cannot be empty"
}
```

Other malformed inputs (bad Base64, wrong token length, missing `X-Access-Token`, malformed UUID, service validation errors such as a short payload) are **not** mapped by `GlobalExceptionHandler`; Spring Boot's default error handling applies. This inconsistency is planned to be addressed.

## Database overview

**IMPLEMENTED**

- PostgreSQL at `jdbc:postgresql://localhost:5432/privatedrop`, username `privatedrop_app`, password injected from the `DB_PASSWORD` environment variable.
- Schema is managed by `spring.jpa.hibernate.ddl-auto=update`; there are no Flyway/Liquibase migrations in this repository.

`secrets` table (JPA entity `com.privatedrop.entity.Secret`):

| Column | Java type | Notes |
|---|---|---|
| `id` | `UUID` | Primary key, generated with `GenerationType.UUID` |
| `encrypted_payload` | `byte[]` / `BYTEA` | Not null; stored as provided |
| `nonce` | `byte[]` / `BYTEA` | Not null; stored as provided |
| `token_hash` | `byte[]` / `BYTEA` | Not null; SHA-256 hash of the access token |
| `expires_at` | `Instant` | Not null |
| `max_views` | `int` | Not null |
| `view_count` | `int` | Not null; incremented by successful claims |
| `created_at` | `Instant` | Not null; set by `@PrePersist` when absent |
| `consumed_at` | `Instant` | Nullable field; currently never written by application code |

Check constraint `chk_secrets_view_counts`: `max_views >= 1 AND view_count >= 0 AND view_count <= max_views`. Byte-array fields are defensively copied in the entity constructor and getters.

A small `note` table backs the demo note endpoint (`Note` entity, `Long` identity primary key, `content` string).

## Local development setup

1. Install **Java 21** and a local **PostgreSQL** server. No other JDK version is declared by `pom.xml`.
2. Create a database and role (choose your own local password — do not commit it):

   ```sql
   CREATE ROLE privatedrop_app WITH LOGIN PASSWORD '<YOUR_LOCAL_PASSWORD>';
   CREATE DATABASE privatedrop OWNER privatedrop_app;
   ```

3. Export the only required environment variable, `DB_PASSWORD`, with that same local password.

   ```powershell
   # Windows PowerShell
   $env:DB_PASSWORD = '<YOUR_LOCAL_PASSWORD>'
   ```

   ```bash
   # macOS / Linux
   export DB_PASSWORD='<YOUR_LOCAL_PASSWORD>'
   ```

4. Run the application with the Maven wrapper (no system Maven installation required):

   ```powershell
   # Windows PowerShell
   .\mvnw.cmd spring-boot:run
   ```

   ```cmd
   :: Windows CMD
   mvnw.cmd spring-boot:run
   ```

   ```bash
   # macOS / Linux
   ./mvnw spring-boot:run
   ```

The service starts on Spring Boot's default port `8080`. `.\mvnw.cmd test` runs the existing context-load test only; there are no automated secret-flow tests.

## Security design and limitations

**IMPLEMENTED design**

- 256-bit access tokens from `SecureRandom`; only their SHA-256 hash is persisted, reducing exposure if database contents are read.
- Base64URL encoding is transport encoding, not encryption.
- A single conditional `UPDATE` claims views atomically, so concurrent requests cannot both consume the last view.
- Unavailable-secret conditions return a uniform 404 body through `SecretUnavailableException` and `GlobalExceptionHandler`.

**Current limitations — important**

- `SecurityConfig` requires authentication for **every** endpoint using HTTP Basic and disables CSRF. There is no custom user store, no recipient token authentication and no secret-specific authorization. Spring Boot's generated default credentials are not a production identity solution.
- The application does not enforce HTTPS, rate limiting, account lockout, or request-size limits of its own.
- The backend does not encrypt or decrypt payloads and cannot reveal plaintext. Client-side encryption/decryption is an architectural goal, not code in this repository.
- Screenshots, copying and malicious browser extensions cannot be prevented by this design.
- Error handling for malformed input is inconsistent, and automated test coverage is minimal.
- **Production deployment requires additional security hardening and review.** Do not treat this code as production-ready.

## Documentation

Technical implementation notes are in [`docs/PrivateDrop-Technical-Documentation.docx`](docs/PrivateDrop-Technical-Documentation.docx).
