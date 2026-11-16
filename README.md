# PrivateDrop Backend

## 1. Project Overview

**IMPLEMENTED**

PrivateDrop Backend is a Spring Boot service (`com.privatedrop.PrivatedropBackendApplication`) that currently provides:
- basic demo/sample and persistence endpoints (`HelloController`, `EchoService`, `Note`), including a simple greeting endpoint, and
- an initial **secret creation** API (`SecretController`, `SecretService`) that accepts a client-provided, already-encrypted payload and nonce, generates an access token, stores only a hash of that token, and persists the encrypted payload and nonce in PostgreSQL.

Client-side encryption is the intended architecture. The current repository does not contain a frontend/client implementation. The backend does **not** perform encryption or decryption; it accepts an already-encrypted payload and nonce and currently stores them as provided.

This repository currently implements **secret creation/storage primitives only**. It does **not** yet provide a complete public secret-sharing workflow, and it cannot currently decrypt or reveal a stored secret.

## 2. Problem Statement

**IMPLEMENTED (project direction, partially addressed)**

The codebase is structured toward a system for sharing sensitive data where the server stores encrypted secret material and access-control metadata (expiry, max views) rather than a raw access token. At present, the implemented portion addresses only creating and persisting such a secret record; retrieval and lifecycle enforcement flows are not yet implemented.

## 3. Current Features

**IMPLEMENTED**
- Spring Boot application bootstrap (`PrivatedropBackendApplication`).
- HTTP endpoints:
  - `GET /api/v1/hello` — returns a greeting string.
  - `POST /api/v1/echo` — validates and echoes a message.
  - `POST /api/v1/notes` — saves a simple note to the database.
  - `POST /api/v1/secrets` — creates a secret record and returns a secret ID and access token.
- Secret creation flow in `SecretService`:
  - validates encrypted payload, nonce, expiry, and max views,
  - generates a 32-byte cryptographically random access token,
  - hashes the token with SHA-256 for storage,
  - Base64 URL-encodes the raw token (no padding) for the client response,
  - persists a `Secret` entity via `SecretRepository`.
- JPA persistence for `Secret` (`secrets` table) and `Note`.
- Global handling for bean validation errors (`MethodArgumentNotValidException`) via `GlobalExceptionHandler` returning `ApiErrorResponse`.
- Spring Security configuration requiring authentication for all requests (HTTP Basic, CSRF disabled).

**PLANNED / NOT YET IMPLEMENTED**
- Secret retrieval/consumption by token.
- View-count increment and enforcement during retrieval.
- Expiry enforcement during retrieval.
- Public token-based access for recipients.
- Secret revocation/deletion and other lifecycle operations.
- Client-side encryption and decryption in the future frontend application.

## 4. Technology Stack

**IMPLEMENTED (from `pom.xml` and source)**

- **Language:** Java 21
- **Framework:** Spring Boot 4.1.2 (`spring-boot-starter-parent`)
- **Web:** `spring-boot-starter-webmvc`
- **Data:** `spring-boot-starter-data-jpa`, Spring Data JPA
- **Database:** PostgreSQL (`postgresql` JDBC driver, runtime scope)
- **Security:** `spring-boot-starter-security`
- **Validation:** `spring-boot-starter-validation` (Jakarta Validation)
- **Observability/ops starter:** `spring-boot-starter-actuator`
- **Build:** Maven (`mvnw`, `mvnw.cmd`, `spring-boot-maven-plugin`)
- **Testing:** `spring-boot-starter-test` modules in `pom.xml`, JUnit-based context test (`PrivatedropBackendApplicationTests`)

## 5. Architecture

**IMPLEMENTED**

Layered structure:
- **Controller layer** (`com.privatedrop.controller`): `HelloController`, `SecretController` — HTTP request/response handling.
- **Service layer** (`com.privatedrop.service`): `EchoService`, `SecretService` — application/business logic.
- **Repository layer** (`com.privatedrop.repository`): `NoteRepository`, `SecretRepository` — Spring Data JPA interfaces (`JpaRepository`).
- **Entity/model layer** (`com.privatedrop.entity`): `Note`, `Secret` — JPA entities.
- **DTO layer** (`com.privatedrop.dto`): `EchoRequest`, `CreateSecretRequest`, `CreateSecretResult`, `ApiErrorResponse`.
- **Configuration** (`com.privatedrop.config`): `SecurityConfig`.
- **Exception handling** (`com.privatedrop.exception`): `GlobalExceptionHandler`.

**Persistence/configuration notes (implemented):**
- Datasource: `jdbc:postgresql://localhost:5432/privatedrop`, user `privatedrop_app`, password from `${DB_PASSWORD}` (`application.properties`).
- `spring.jpa.open-in-view=false`
- `spring.jpa.hibernate.ddl-auto=update`

## 6. Project Structure

**IMPLEMENTED**

```text
src/main/java/com/privatedrop/
  PrivatedropBackendApplication.java
  config/
    SecurityConfig.java
  controller/
    HelloController.java
    SecretController.java
  dto/
    ApiErrorResponse.java
    CreateSecretRequest.java
    CreateSecretResult.java
    EchoRequest.java
  entity/
    Note.java
    Secret.java
  exception/
    GlobalExceptionHandler.java
  repository/
    NoteRepository.java
    SecretRepository.java
  service/
    EchoService.java
    SecretService.java

src/main/resources/
  application.properties

src/test/java/com/privatedrop/
  PrivatedropBackendApplicationTests.java

Other files: pom.xml, api-tests.http, mvnw, mvnw.cmd
```

## 7. Current API Endpoints

**IMPLEMENTED** (all requests require authentication per `SecurityConfig`)

| Method | Path | Controller | Description |
|---|---|---|---|
| GET | `/api/v1/hello` | `HelloController#sayHello` | Simple/demo greeting endpoint; returns `"Hello from PrivateDrop!!"` |
| POST | `/api/v1/echo` | `HelloController#echoMessage` | Accepts `EchoRequest` (`message`, `@NotBlank` + `@Valid`) and returns the echoed string |
| POST | `/api/v1/notes` | `HelloController#createNote` | Accepts `EchoRequest` and persists a `Note`; returns the saved `Note` |
| POST | `/api/v1/secrets` | `SecretController#createSecret` | Accepts `CreateSecretRequest`, creates/stores a secret, returns `CreateSecretResult` (`secretId`, `accessToken`) |

**PLANNED / NOT YET IMPLEMENTED:** no endpoints for reading/consuming, revoking, or otherwise managing secrets after creation.

## 8. Secret Creation Flow

**IMPLEMENTED** (`SecretController` + `SecretService`)

1. Client sends `POST /api/v1/secrets` with JSON body (`CreateSecretRequest`):
   - `encryptedPayload` (String, Base64)
   - `nonce` (String, Base64)
   - `expiresAt` (Instant)
   - `maxViews` (int)
2. `SecretController` decodes `encryptedPayload` and `nonce` from Base64 to `byte[]`.
3. `SecretService.createSecret(...)` validates the request:
   - `encryptedPayload` must be non-null and at least 16 bytes
   - `nonce` must be non-null and exactly 12 bytes
   - `expiresAt` must be non-null and in the future
   - `maxViews` must be >= 1
4. `SecretService` generates a raw access token (32 random bytes via `SecureRandom`).
5. `SecretService` computes a SHA-256 hash of the raw token for storage.
6. `SecretService` encodes the raw token as Base64 URL without padding for the client.
7. A `Secret` entity is created and saved via `SecretRepository`.
8. Response: `CreateSecretResult { secretId: UUID, accessToken: String }`.

**Important:** client-side encryption is the intended architecture. The current repository does not contain a frontend/client implementation. The backend does **not** perform encryption or decryption; it accepts an already-encrypted payload and nonce and currently stores them. The backend cannot currently decrypt or reveal a stored secret.

## 9. Access Token Security

**IMPLEMENTED**

- **Generation:** `SecretService.generateAccessToken()` creates a 32-byte (256-bit) token using `java.security.SecureRandom#nextBytes`.
- **Hashing for storage:** `SecretService.hashAccessToken(byte[])` computes a SHA-256 digest using `java.security.MessageDigest` and stores the hash in `Secret.tokenHash` (`secrets.token_hash`, `BYTEA`).
- **Client-facing encoding:** `SecretService.encodeAccessToken(byte[])` returns the raw token encoded with `Base64.getUrlEncoder().withoutPadding()` (encoding, not encryption).
- **Storage model:** only the token hash is persisted; the raw token is returned to the caller in the create response.

**PLANNED / NOT YET IMPLEMENTED:** token-based lookup/verification and any secret retrieval/consumption using the access token.

## 10. Database Design

**IMPLEMENTED**

### Table: `secrets` (`com.privatedrop.entity.Secret`)
| Field | Java type | Column | Details |
|---|---|---|---|
| `id` | `UUID` | `id` | Primary key, `@GeneratedValue(strategy = GenerationType.UUID)` |
| `encryptedPayload` | `byte[]` | `encrypted_payload` | `BYTEA`, not null |
| `nonce` | `byte[]` | `nonce` | `BYTEA`, not null |
| `tokenHash` | `byte[]` | `token_hash` | `BYTEA`, not null |
| `expiresAt` | `Instant` | `expires_at` | not null |
| `maxViews` | `int` | `max_views` | not null (default field value `1`) |
| `viewCount` | `int` | `view_count` | not null (default `0`) |
| `createdAt` | `Instant` | `created_at` | not null, set in `@PrePersist` if absent |
| `consumedAt` | `Instant` | `consumed_at` | nullable |

- Check constraint: `chk_secrets_view_counts` → `max_views >=1 AND view_count >= 0 AND view_count <= max_views`
- Byte-array fields are defensively copied in constructor/getters.

### Table/entity: `Note` (`com.privatedrop.entity.Note`)
| Field | Java type | Details |
|---|---|---|
| `id` | `Long` | Primary key, `@GeneratedValue(strategy = GenerationType.IDENTITY)` |
| `content` | `String` | Note content |

Schema management: `spring.jpa.hibernate.ddl-auto=update`.

## 11. Validation

**IMPLEMENTED**

- **Bean validation (Jakarta):**
  - `EchoRequest.message` is annotated with `@NotBlank(message="Message cannot be empty")`.
  - `POST /api/v1/echo` applies `@Valid` to the request body.
  - `CreateSecretRequest` has no bean validation annotations; `POST /api/v1/secrets` and `POST /api/v1/notes` do not apply `@Valid` in controllers.
- **Global handling:** `GlobalExceptionHandler` handles `MethodArgumentNotValidException` and returns HTTP 400 with `ApiErrorResponse { status, error: "VALIDATION_FAILED", message }`.
- **Service-level validation (secret creation):** `SecretService.validateCreateSecretRequest(...)` throws `IllegalArgumentException` for invalid payload length (<16), nonce length (!=12), non-future/null expiry, or `maxViews < 1`. These exceptions are not specifically handled by `GlobalExceptionHandler`.
- **Decoding:** `SecretController` decodes Base64 input for payload/nonce; invalid Base64 is not explicitly handled in application code.

## 12. Security Configuration

**IMPLEMENTED** (`com.privatedrop.config.SecurityConfig`)

- CSRF: disabled (`csrf(AbstractHttpConfigurer::disable)`).
- Authorization: `anyRequest().authenticated()` — all endpoints require authentication.
- Authentication mechanism: HTTP Basic (`httpBasic`).

There is no custom user store, token-based recipient access, or secret-specific authorization logic implemented in this class.

## 13. Running the Backend Locally

**IMPLEMENTED (based on repository configuration)**

### Prerequisites
- Java 21
- Maven (or use included wrapper `mvnw` / `mvnw.cmd`)
- PostgreSQL accessible at `localhost:5432`

### Database/setup
- Database name (configured): `privatedrop`
- Username (configured): `privatedrop_app`
- Password: provided via environment/property `DB_PASSWORD` (`spring.datasource.password=${DB_PASSWORD}`)
- Ensure the database/user exist and credentials are valid.

### Run
```bash
# Windows (PowerShell)
.\mvnw.cmd spring-boot:run

# Windows (CMD)
mvnw.cmd spring-boot:run

# macOS/Linux
./mvnw spring-boot:run
```

The application uses `spring.jpa.hibernate.ddl-auto=update` and `spring.jpa.open-in-view=false` as configured in `application.properties`.

## 14. Testing the API

**IMPLEMENTED examples/artifacts**

- Automated test present: `src/test/java/com/privatedrop/PrivatedropBackendApplicationTests.java` (context load test).
- HTTP examples: `api-tests.http` (includes a sample `POST /api/v1/echo` request).

### Example requests
```http
GET http://localhost:8080/api/v1/hello

POST http://localhost:8080/api/v1/echo
Content-Type: application/json

{
  "message": "Hello PrivateDrop"
}

POST http://localhost:8080/api/v1/notes
Content-Type: application/json

{
  "message": "A note"
}

POST http://localhost:8080/api/v1/secrets
Content-Type: application/json

{
  "encryptedPayload": "<base64-encoded-encrypted-payload>",
  "nonce": "<base64-encoded-12-byte-nonce>",
  "expiresAt": "2030-01-01T00:00:00Z",
  "maxViews": 1
}
```

**Note:** due to current security configuration, requests must include valid HTTP Basic credentials.

## 15. Current Limitations

**IMPLEMENTED state (limitations observed in code)**

- Only secret **creation** is implemented; there is no way to retrieve or consume a stored secret.
- The backend does not perform payload encryption or decryption; it stores the client-provided encrypted payload and nonce.
- No view-count increment, no consumption tracking workflow, and no expiry enforcement during retrieval/access (fields/constraints exist, but access flow does not).
- All endpoints require HTTP Basic authentication; there is no public/token-based recipient access path.
- Secret request validation/error handling is incomplete for API consistency (e.g., no bean validation on `CreateSecretRequest`, `IllegalArgumentException` and Base64 decode errors are not mapped by `GlobalExceptionHandler`).
- Limited automated coverage (context-load test only; no secret-flow-specific tests).

## 16. Planned Features

**PLANNED / NOT YET IMPLEMENTED**

- **Secret retrieval/consumption** (lookup and return/consume flow using the access token).
- **View-count enforcement** (increment and enforce `maxViews`/`view_count` during access).
- **Expiry enforcement during retrieval** (reject/handle expired secrets on access).
- **Public token-based access** for recipients (separate from authenticated administrative/app access).
- Additional **secret lifecycle operations** (e.g., revocation/deletion/status handling) and related API endpoints.
- More complete validation and consistent API error responses for secret operations.

## 17. Project Status

**Early development / work in progress.**

The repository currently contains a working Spring Boot foundation and an implemented secret-creation/storage slice (`POST /api/v1/secrets`, `Secret` persistence, token generation/hashing). Core sharing lifecycle capabilities—especially secret retrieval/consumption, access-time expiry/view enforcement, public token-based access, and other lifecycle operations—are **not yet implemented**.
