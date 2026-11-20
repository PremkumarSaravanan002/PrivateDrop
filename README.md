# PrivateDrop Backend

PrivateDrop is an early-stage Spring Boot backend for secure, temporary secret sharing. It allows clients to store encrypted secret payloads and retrieve them using a randomly generated, view-limited access token.

The backend is designed to handle encrypted data only. Encryption and decryption are intended to happen on the client side.

> **Project status: Work in Progress — Not Production Ready**
>
> This repository currently contains the backend implementation. A frontend and complete end-to-end secret-sharing experience are still planned.

## 1. Project Overview

PrivateDrop is intended to provide a simple way to share sensitive information temporarily without storing plaintext secrets on the server.

The planned application flow:

1. Sender enters a secret in the browser.
2. Browser encrypts the secret using Web Crypto API and AES-256-GCM.
3. Backend stores the encrypted payload, nonce, expiry information and access-token hash.
4. Sender receives a secret ID and access token to share.
5. Recipient explicitly requests the secret using the access token.
6. Backend validates the request and returns the encrypted payload.
7. Browser decrypts the payload using the encryption key held by the client.

**Important:** Browser-side encryption, decryption and the complete frontend workflow have not yet been implemented in this repository.

## 2. Technology Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.2 |
| Web | Spring Web MVC |
| Persistence | Spring Data JPA |
| Database | PostgreSQL 18 |
| Security | Spring Security |
| Build Tool | Maven |
| Testing | JUnit 5, Mockito, Spring Boot Test |

## 3. Architecture

The backend follows a layered architecture:

```text
Controller
    |
    v
Service
    |
    v
Repository
    |
    v
PostgreSQL Database
```

### Main packages

| Package | Responsibility |
|---|---|
| `controller` | Handles REST API requests |
| `service` | Contains application and business logic |
| `repository` | Handles database operations |
| `entity` | Defines JPA database entities |
| `dto` | Defines request and response objects |
| `exception` | Handles application exceptions |
| `config` | Contains application configuration |

## 4. Implemented Features

### Secret Creation

**Endpoint:** `POST /api/v1/secrets`

The backend currently:

- Accepts an encrypted payload and nonce.
- Validates encrypted payload length (minimum 16 bytes).
- Validates nonce length (exactly 12 bytes).
- Checks that the expiry time is in the future.
- Ensures maximum views is at least 1.
- Generates a cryptographically secure 32-byte access token using `SecureRandom`.
- Calculates the SHA-256 hash of the access token.
- Stores the hash instead of the original token.
- Persists the encrypted payload, nonce and associated metadata.
- Returns the generated secret ID and raw access token.

The access token is encoded using unpadded Base64URL. Base64URL is encoding, not encryption.

### Secret Retrieval

**Endpoint:** `POST /api/v1/secrets/{secretId}/reveal`

The retrieval process:

1. Accepts the secret ID and access token.
2. Validates the access-token length.
3. Calculates the SHA-256 hash of the supplied token.
4. Executes an atomic conditional database update to claim a view.
5. Checks the secret ID, token hash, expiry and remaining view count.
6. Increments the view count only when all conditions are satisfied.
7. Retrieves and returns the encrypted payload and nonce.

The retrieval operation is transactional.

### View Limit Enforcement

The repository uses a conditional JPQL update to ensure that concurrent requests cannot both claim the final available view.

The update succeeds only when:

- The secret ID matches.
- The access-token hash matches.
- The secret has not expired.
- The current view count is below the maximum allowed views.

A successful claim returns `1`; an unsuccessful claim returns `0`.

### Exception Handling

The application currently includes:

- `SecretUnavailableException` for unavailable secrets.
- `GlobalExceptionHandler` for handling application exceptions.
- Generic HTTP 404 responses for unavailable secrets.
- HTTP 400 responses for supported bean-validation failures.

Wrong tokens, expired secrets and exhausted view limits produce the same generic unavailable-secret response.

## 5. API Documentation

All current endpoints require HTTP Basic authentication and JSON requests require `Content-Type: application/json`.

Spring Boot's default generated user is used; no custom user store is currently configured.

### Available Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/v1/hello` | Demo greeting endpoint |
| POST | `/api/v1/echo` | Validates and echoes a message |
| POST | `/api/v1/notes` | Saves a demo note |
| POST | `/api/v1/secrets` | Creates a temporary secret |
| POST | `/api/v1/secrets/{secretId}/reveal` | Retrieves encrypted secret data |

### Create Secret

Request:

```http
POST /api/v1/secrets
Content-Type: application/json
Authorization: Basic <CREDENTIALS>
```

```json
{
  "encryptedPayload": "<BASE64_CIPHERTEXT>",
  "nonce": "<BASE64_NONCE>",
  "expiresAt": "2030-01-01T00:00:00Z",
  "maxViews": 1
}
```

Successful response:

```json
{
  "secretId": "<GENERATED_UUID>",
  "accessToken": "<GENERATED_BASE64URL_TOKEN>"
}
```

### Reveal Secret

Request:

```http
POST /api/v1/secrets/<SECRET_ID>/reveal
Authorization: Basic <CREDENTIALS>
X-Access-Token: <ACCESS_TOKEN>
```

Successful response:

```json
{
  "encryptedPayload": "<BASE64_CIPHERTEXT>",
  "nonce": "<BASE64_NONCE>"
}
```

### Unavailable Secret Response

HTTP `404 Not Found`:

```json
{
  "status": 404,
  "error": "SECRET_UNAVAILABLE",
  "message": "Secret is unavailable"
}
```

The generic response avoids exposing whether the secret is missing, expired, has an invalid token or has exhausted its view limit.

## 6. Database Overview

PrivateDrop currently uses PostgreSQL.

**Connection configuration:**

- Database: `privatedrop`
- Username: `privatedrop_app`
- Host: `localhost`
- Port: `5432`
- Password: Supplied through the `DB_PASSWORD` environment variable.

### Secrets Table

The `Secret` entity maps to the `secrets` table.

| Column | Type | Description |
|---|---|---|
| `id` | UUID | Primary key |
| `encrypted_payload` | BYTEA | Encrypted secret data |
| `nonce` | BYTEA | Encryption nonce |
| `token_hash` | BYTEA | SHA-256 hash of access token |
| `expires_at` | TIMESTAMP | Expiration time |
| `max_views` | INTEGER | Maximum allowed views |
| `view_count` | INTEGER | Number of successfully claimed views |
| `created_at` | TIMESTAMP | Creation timestamp |
| `consumed_at` | TIMESTAMP | Nullable; currently not updated by application code |

A database check constraint enforces:

- `max_views >= 1`
- `view_count >= 0`
- `view_count <= max_views`

The entity also defensively copies its byte-array fields.

A separate `note` table supports the demonstration note endpoint.

## 7. Local Development Setup

### Prerequisites

- Java 21
- PostgreSQL
- Git
- IntelliJ IDEA or another Java IDE (optional)

### Step 1: Clone the Repository

```powershell
git clone https://github.com/PremkumarSaravanan002/PrivateDrop.git
cd PrivateDrop
```

### Step 2: Configure PostgreSQL

Create the application role and database:

```sql
CREATE ROLE privatedrop_app WITH LOGIN PASSWORD '<YOUR_LOCAL_PASSWORD>';

CREATE DATABASE privatedrop OWNER privatedrop_app;
```

Use your own local password. Never commit database credentials.

### Step 3: Configure Environment Variable

Windows PowerShell:

```powershell
$env:DB_PASSWORD = '<YOUR_LOCAL_PASSWORD>'
```

macOS/Linux:

```bash
export DB_PASSWORD='<YOUR_LOCAL_PASSWORD>'
```

### Step 4: Run the Application

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
./mvnw spring-boot:run
```

The backend starts on:

```text
http://localhost:8080
```

## 8. Automated Testing

The project uses JUnit 5 and Mockito.

The current `SecretServiceTest` contains six service-level unit tests:

| Test | Expected behaviour |
|---|---|
| Valid token and available view | Returns encrypted payload and nonce |
| View cannot be claimed | Throws `SecretUnavailableException` |
| Invalid token length | Throws `IllegalArgumentException` before repository access |
| Null secret ID | Throws `SecretUnavailableException` before repository access |
| Token hashing | Verifies the correct SHA-256 hash is passed to the repository |
| Secret missing after view claim | Throws `IllegalStateException` |

These tests use a mocked `SecretRepository`, so they do not execute actual PostgreSQL queries.

An additional Spring Boot context-load test exists in `PrivatedropBackendApplicationTests`.

### Run Tests

Windows PowerShell:

```powershell
.\mvnw.cmd test
```

To run the complete Maven verification lifecycle:

```powershell
.\mvnw.cmd clean verify
```

The application context test requires a working database configuration and the `DB_PASSWORD` environment variable.

**Testing limitations:** Automated repository integration tests, database concurrency tests and end-to-end tests are not yet implemented.

A previous manual concurrency check using concurrent PowerShell requests produced one successful claim and one unavailable response for a secret limited to one view. This was a manual verification, not an automated test.

## 9. Security Design

Important security decisions in the current backend:

- **Client-encrypted data:** The backend accepts encrypted payloads and does not implement plaintext encryption or decryption.
- **Random access tokens:** Tokens are generated using Java's `SecureRandom`.
- **Token hashing:** Only the SHA-256 hash is stored in the database.
- **Atomic view claiming:** Conditional database updates protect view limits against concurrent claims.
- **Generic error responses:** Unavailable-secret conditions share the same response to reduce information leakage.
- **Defensive byte-array copying:** Prevents callers from directly modifying the entity's internal byte-array values.

### Current Security Limitations

The current application is not production-ready.

- All endpoints currently require HTTP Basic authentication.
- CSRF is disabled in the current security configuration.
- There is no custom user store or recipient-specific authentication flow.
- HTTPS is not enforced by the application.
- Rate limiting and account lockout are not implemented.
- CORS and Content Security Policy have not been configured for a production frontend.
- There is no dedicated request-size limiting strategy.
- Malformed input handling is not yet consistent across all endpoints.
- The frontend and browser-side encryption/decryption are not implemented.
- Screenshots, copying and malicious browser extensions cannot be prevented by this architecture.

Production deployment requires additional security hardening, security review and appropriate infrastructure configuration.

## 10. Planned Improvements

- Build the frontend using a suitable modern web framework.
- Implement client-side AES-256-GCM encryption and decryption using Web Crypto API.
- Keep the encryption key separate from the backend access token.
- Implement a secure share-link and reveal workflow.
- Introduce an appropriate recipient authentication mechanism.
- Improve validation and consistent API error responses.
- Add automated repository integration and concurrency tests.
- Implement secret revocation and expiry cleanup.
- Add database migrations using Flyway or Liquibase.
- Introduce production security controls, including HTTPS, CORS, CSP, rate limiting and security headers.
- Review logging, monitoring and sensitive-data handling.

## 11. Documentation

Additional technical implementation details are available in:

[`docs/PrivateDrop-Technical-Documentation.docx`](docs/PrivateDrop-Technical-Documentation.docx)

---

**PrivateDrop — Built incrementally, with security and learning at the centre.**
