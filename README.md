# Email Campaign Management API

Simple Spring Boot REST API for creating campaigns, adding recipients, scheduling
campaigns, simulating delivery, and viewing statistics.

## Requirements

- Java 17+
- Maven (or use the included Maven wrapper)
- H2 is used by default for a quick local run.
- PostgreSQL is supported with the `postgres` profile.

## Run

```bash
./mvnw spring-boot:run
```

The API starts at `http://localhost:8080`.

For PostgreSQL, copy `.env.example` values into the environment and run:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/email_campaign
export DB_USERNAME=postgres
export DB_PASSWORD=your-password
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

The PostgreSQL schema is in `src/main/resources/db/schema-postgresql.sql`.

## API usage

Create a campaign:

```bash
curl -X POST http://localhost:8080/api/campaigns \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Welcome campaign",
    "subject": "Welcome",
    "senderEmail": "sender@example.com",
    "content": "Hello there",
    "scheduledAt": "2030-01-01T10:00:00"
  }'
```

Add one or more recipients:

```bash
curl -X POST http://localhost:8080/api/campaigns/{campaignId}/recipients \
  -H 'Content-Type: application/json' \
  -d '[
    {"name": "Asha", "email": "asha@example.com"},
    {"name": "Ravi", "email": "ravi@example.com"}
  ]'
```

Schedule and process:

```bash
curl -X POST http://localhost:8080/api/campaigns/{campaignId}/schedule
curl -X POST http://localhost:8080/api/campaigns/process
```

List campaigns:

```text
GET /api/campaigns?page=0&size=10&status=scheduled&search=welcome&sort=desc
```

`status` accepts `draft`, `scheduled`, `processing`, or `completed`.
`sort` controls created-date order and accepts `asc` or `desc`.

Get details and statistics:

```text
GET /api/campaigns/{campaignId}
GET /api/campaigns/{campaignId}/statistics
```

## Design notes

- `campaigns` stores campaign information and lifecycle status.
- `recipients` stores one row per campaign recipient and delivery status.
- A database unique constraint prevents the same email from being added twice to
  one campaign. Emails are normalized to lowercase before saving.
- Processing uses a conditional status update (`scheduled` to `processing`) so
  only one caller can claim a scheduled campaign. Each recipient is then marked
  randomly as delivered or failed, and the campaign becomes completed.
- Validation and business-rule failures return `400`, missing campaigns return
  `404`, and database uniqueness conflicts return `409`.

## Tests

```bash
./mvnw test
```

The tests cover valid creation, invalid input, duplicate recipients, scheduling
without recipients, processing a campaign twice, and statistics.

## Production improvements

For production use, the simulated processor would be replaced with a durable
queue and retry policy. I would also add authentication, rate limiting,
structured audit logging, monitoring, and migrations managed by Flyway or
Liquibase.