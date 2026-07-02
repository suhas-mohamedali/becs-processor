# BECS BPY File Processor

Spring Boot 3.3 / JDK 21 application that consumes BECS Direct Entry `.bpy` bundle files,
debulks payment records by BSB, stores each record in an **embedded H2 database**, and
writes individual BECS DE files to an output directory — all containerised with Docker.

No external database server required. H2 data persists to disk and is inspectable with
DataGrip, DBeaver, IntelliJ Database, or a browser.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                      Docker Container                        │
│                                                              │
│  Spring Boot App (port 8080)                                 │
│  ├── InboxPollerService   polls every 30 s                   │
│  ├── BpyProcessingService  parse → debulk → persist         │
│  ├── FileStorageService    write output files                │
│  └── REST API / Swagger UI  /api/becs/**                    │
│                                                              │
│  H2 Embedded DB  (/data/becsdb.mv.db)  ←── named volume    │
│  H2 TCP Server   (port 9092)           ←── DataGrip/DBeaver │
│  H2 Web Console  (port 8080/h2-console) ←── browser        │
│                                                              │
│  /data/becs/inbox    ← drop .bpy files here                  │
│  /data/becs/output   ← debulked .de files (by BSB)          │
│  /data/becs/archive  ← processed originals                  │
│  /data/becs/error    ← failed files                         │
└──────────────────────────────────────────────────────────────┘
```

---

## Quick Start

### 1 — Run with Docker Compose

```bash
git clone <repo>
cd becs-processor

docker compose up --build -d
```

The app starts in ~60 s. Verify:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP", ...}
```

### 2 — Drop a BPY file into the inbox

```bash
# Copy a file into the Docker volume via the running container
docker cp my-payments.bpy becs-processor:/data/becs/inbox/

# The poller picks it up within 30 seconds.
# Watch logs:
docker logs -f becs-processor
```

### 3 — OR upload via REST API

```bash
curl -F "file=@my-payments.bpy" http://localhost:8080/api/becs/upload
```

### 4 — Query results

```bash
# List all processed files
curl http://localhost:8080/api/becs/files

# Payment records for file ID 1
curl http://localhost:8080/api/becs/files/1/payments

# Credit/debit summary
curl http://localhost:8080/api/becs/files/1/summary
```

Swagger UI: **http://localhost:8080/swagger-ui.html**

---

## Running locally without Docker

```bash
# Requires JDK 21 + Maven 3.9+
mvn spring-boot:run
```

H2 file will be created at `./data/becsdb.mv.db` relative to the working directory.
BPY inbox is `./data/becs/inbox/`.

---

## Inspecting the Database

Three options — pick whichever suits you.

---

### Option A — H2 Web Console (browser, zero setup)

1. Open **http://localhost:8080/h2-console**
2. Fill in the form:

   | Field | Value |
   |-------|-------|
   | Driver Class | `org.h2.Driver` |
   | JDBC URL | `jdbc:h2:tcp://localhost:9092/~/data/becsdb` |
   | User Name | `becs` |
   | Password | `becs` |

3. Click **Connect**.

> **Tip:** The JDBC URL above uses the TCP server so you are connecting to the same
> live database file the application is using. You can also use the file URL
> `jdbc:h2:file:/path/to/data/becsdb` when the app is stopped.

---

### Option B — DataGrip

#### Step 1 — Add a new data source

1. Open DataGrip → **+** (New) → **H2**
2. Switch to the **Advanced** or **General** tab and enter:

   | Setting | Value |
   |---------|-------|
   | **Connection type** | Remote |
   | **Host** | `localhost` |
   | **Port** | `9092` |
   | **Database** | `/data/becsdb` *(path inside container / volume)* |
   | **User** | `becs` |
   | **Password** | `becs` |

3. Alternatively paste the JDBC URL directly:
   ```
   jdbc:h2:tcp://localhost:9092//data/becsdb
   ```
4. Click **Test Connection** → should show *Succeeded*.
5. Click **OK**.

#### Step 2 — Download the H2 driver (first time only)

DataGrip will prompt to download the H2 driver automatically.
Click **Download** when prompted, or go to:
**Driver** → **H2** → **Download**.

#### Step 3 — Browse the schema

Expand the data source → **PUBLIC** schema → **Tables**:

```
BECS_BPY_FILE          – one row per .bpy file processed
BECS_FILE_HEADER       – parsed Type-0 header record
BECS_FILE_TRAILER      – parsed Type-7 trailer/control record
BECS_PAYMENT_RECORD    – every debulked Type-1 payment row
BECS_PROCESSING_LOG    – audit log
```

#### Useful DataGrip queries

```sql
-- All processed files
SELECT id, file_name, status, record_count, processed_at
FROM becs_bpy_file
ORDER BY received_at DESC;

-- Payment records for the most recent file
SELECT p.bsb_number, p.account_number, p.transaction_code,
       p.amount_cents / 100.0 AS amount_dollars,
       p.account_name, p.lodgement_reference, p.output_file_path
FROM becs_payment_record p
JOIN becs_bpy_file f ON f.id = p.bpy_file_id
ORDER BY f.received_at DESC, p.line_number;

-- Credit/Debit totals per file
SELECT f.file_name,
       SUM(CASE WHEN p.transaction_code IN ('50','51','52','53','54','55')
                THEN p.amount_cents ELSE 0 END) / 100.0 AS credits,
       SUM(CASE WHEN p.transaction_code = '13'
                THEN p.amount_cents ELSE 0 END) / 100.0 AS debits,
       COUNT(*) AS record_count
FROM becs_payment_record p
JOIN becs_bpy_file f ON f.id = p.bpy_file_id
GROUP BY f.id, f.file_name;

-- Records grouped by BSB (shows debulk grouping)
SELECT bsb_number, COUNT(*) AS records,
       SUM(amount_cents) / 100.0 AS total_dollars
FROM becs_payment_record
WHERE bpy_file_id = 1    -- change to your file id
GROUP BY bsb_number
ORDER BY total_dollars DESC;

-- Failed files
SELECT id, file_name, error_message, received_at
FROM becs_bpy_file
WHERE status = 'FAILED';
```

---

### Option C — DBeaver (free alternative to DataGrip)

1. **Database** → **New Database Connection** → choose **H2**
2. Select **Remote** (not embedded)
3. Enter:
   - Host: `localhost`
   - Port: `9092`
   - Database: `/data/becsdb`
   - Username: `becs` / Password: `becs`
4. **Test Connection** → **Finish**

> DBeaver auto-downloads the H2 JDBC driver on first connect.

---

### Option D — IntelliJ IDEA Ultimate (built-in Database tab)

1. **View** → **Tool Windows** → **Database**
2. **+** → **Data Source** → **H2**
3. Set connection type to **Remote** and fill in the same details as DataGrip above.

---

## Connecting to the H2 file directly (app stopped)

When the application is **not running** you can open the database file directly:

```
JDBC URL:  jdbc:h2:file:/path/to/data/becsdb
```

To find the Docker volume path on your host:

```bash
docker inspect becs-processor \
  --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{"\n"}}{{end}}'
```

The line ending in `/data` shows the host path. Append `/becsdb` to get the DB file prefix.

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BECS_DB_PATH` | `./data/becsdb` | H2 file path (no extension) |
| `DB_USERNAME` | `becs` | H2 username |
| `DB_PASSWORD` | `becs` | H2 password |
| `BECS_INBOX_DIR` | `./data/becs/inbox` | Inbox directory |
| `BECS_ARCHIVE_DIR` | `./data/becs/archive` | Archive directory |
| `BECS_OUTPUT_DIR` | `./data/becs/output` | Debulked output files |
| `BECS_ERROR_DIR` | `./data/becs/error` | Failed files |
| `BECS_POLL_MS` | `30000` | Polling interval (ms) |

---

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET`  | `/api/becs/files` | List all BPY files |
| `GET`  | `/api/becs/files?status=COMPLETED` | Filter by status |
| `GET`  | `/api/becs/files/{id}` | Get file record |
| `GET`  | `/api/becs/files/{id}/payments` | All payment records |
| `GET`  | `/api/becs/files/{id}/summary` | Record count & totals |
| `POST` | `/api/becs/upload` | Upload & immediately process a BPY file |
| `POST` | `/api/becs/trigger/{fileName}` | Re-trigger an inbox file manually |

Swagger UI: http://localhost:8080/swagger-ui.html
H2 Console: http://localhost:8080/h2-console

---

## Migrating to PostgreSQL later

When you're ready to switch to a production database:

1. Add `postgresql` driver and `flyway-database-postgresql` back to `pom.xml`
2. Change `spring.datasource.url` to `jdbc:postgresql://...`
3. Change dialect to `PostgreSQLDialect`
4. Update `V1__init_becs_schema.sql`: replace `BIGINT GENERATED BY DEFAULT AS IDENTITY` → `BIGSERIAL`, and `CLOB` → `TEXT`
5. Add the PostgreSQL service back to `docker-compose.yml`

---

## Running Tests

```bash
mvn test
```

Tests use H2 in-memory mode automatically — no extra setup needed.
