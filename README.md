# LogSage

A self-hosted log analysis workbench. First working draft of the supplied portfolio blueprint.

## Run locally

Requires Java 21 and Maven 3.9+.

```powershell
mvn clean package
java -jar logsage-api/target/logsage-api-0.1.0.jar --server.port=8087
```

Open http://127.0.0.1:8087. Click **Load sample logs**, select an incident, and choose **Analyze incident**. Or upload `samples/hikari-timeout.log`.

Default port is 8080; the command above uses 8087 to avoid conflicts. The server binds only to loopback. Data persists under `./data` relative to the working directory. This is a single-user development application without authentication; keep the loopback binding.

## Implemented in this draft

- Java 21 / Spring Boot 4 backend serving a responsive HTML/CSS/JavaScript dashboard.
- Log file upload and text ingestion, multiline stack traces, common secret/email redaction.
- SHA-256 incident fingerprints with numeric/UUID normalization and top stack frame.
- Incident search, service/severity filters, error timeline, log explorer, resolve/reopen.
- Real persisted evidence, saved diagnoses and useful/incorrect feedback.
- Guided demo diagnoses explicitly identified as deterministic, without an LLM call.
- Optional direct Ollama inference with a single-analysis concurrency limit, context budget, timeout, JSON validation, and evidence-ID allowlist.
- H2 file persistence for a first run without Docker. Six automated parser, validation, and workflow tests.

No sample data is loaded automatically. The sample dataset is synthetic and idempotent: 128 entries, 43 errors, four incident groups. Dashboard values reflect stored data. The timeline and log explorer use the newest 500 entries; total counters query the entire dataset. External Google Fonts are optional; system fonts are used offline. Logs are sent only to the configured Ollama endpoint when Local AI is chosen.

## Local AI

Install Ollama and run:

```powershell
ollama pull qwen3:1.7b
ollama serve
```

If Ollama already runs in the background, omit `ollama serve`. Select **Local AI · Ollama** in an incident. CPU inference can take up to two minutes. Ollama failures are shown explicitly; there is no silent substitution with demo output. Live inference has not been verified on this machine.

Optional configuration:

```powershell
$env:OLLAMA_URL = 'http://127.0.0.1:11434'
$env:OLLAMA_MODEL = 'qwen3:1.7b'
```

## API

| Method | Path | Body / purpose |
|---|---|---|
| GET | `/api/v1/dashboard/stats` | Workspace totals |
| POST | `/api/v1/demo` | Load synthetic sample once |
| POST | `/api/v1/logs/upload` | Multipart `file`, `source`; 5 MB limit |
| POST | `/api/v1/logs/ingest` | JSON `{ "text": "...", "source": "order-service" }` |
| GET | `/api/v1/logs?incidentId=...` | Latest 500 logs |
| GET | `/api/v1/incidents` | Grouped errors |
| GET | `/api/v1/incidents/{id}` | Incident and evidence |
| PATCH | `/api/v1/incidents/{id}` | `{ "status": "OPEN" }` or `RESOLVED` |
| POST | `/api/v1/incidents/{id}/analyze?demo=true` | Guided diagnosis; omit demo for Ollama |
| GET | `/api/v1/incidents/{id}/analysis` | Latest saved diagnosis |
| POST | `/api/v1/analyses/{id}/feedback` | `{ "rating": "USEFUL" }` or `INCORRECT` |

Supported header: `2026-10-22T10:32:01.000Z ERROR [order-service] com.example.Controller : message`. Continuation lines attach to the preceding entry. Lines before the first recognized header are ignored; unrecognized entire files return 400. ERROR/FATAL entries become incidents; WARN entries remain available in the explorer. Severity and demo hypotheses use simple heuristics, not measured diagnostic confidence. This initial parser does not support JSON log files or all possible Spring logging configurations. Redaction is best-effort and not a substitute for reviewing sensitive logs.

## Validation

```powershell
mvn test
node --check logsage-api/src/main/resources/static/app.js
```

## Next milestones from the blueprint

1. Extract parser, incident, persistence, and analysis packages; move schema to Flyway and PostgreSQL with explicit migrations.
2. Replace direct Ollama HTTP adapter with Spring AI and schema-constrained model responses, bounded retry, and persisted failed-analysis states.
3. Add the separate MCP service with read-only database credentials, bounded tools and authenticated client access. **MCP and Spring AI are not implemented in this draft.**
4. Add authentication, structured JSON ingestion, trace correlation, complete pagination, Docker Compose, and Testcontainers coverage.
5. Build the labeled evaluation dataset and measure model quality, grouping accuracy, latency, and RAM. No benchmark claims yet.
