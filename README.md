# Banking MCP Server

A standalone Model Context Protocol (MCP) server that exposes the [Banking AI Agent](../banking-ai-agent)'s capabilities — FAQ grounding, per-document Q&A, and document upload — to any MCP-compatible AI client (Claude Desktop, Claude Code, MCP Inspector, or a future custom agent).

Built as a deliberately separate project from the banking assistant it wraps, calling it over its existing REST API rather than reaching into its internal Spring beans — the same way you'd wrap a real production service you didn't write yourself, or one written in a different stack entirely.

## What It Does

Exposes three MCP tools, each a thin wrapper around an existing REST endpoint on the Banking AI Agent:

| MCP Tool | Wraps | Purpose |
|---|---|---|
| `answerBankingFaq` | `POST /api/chat` | Answer a banking policy/FAQ question, grounded in the bank's official documents |
| `askAboutDocument` | `POST /api/documents/{documentId}/ask` | Answer a question scoped to one specific previously-uploaded PDF |
| `uploadDocument` | `POST /api/documents/upload` | Upload a new PDF (as Base64) and receive a `documentId` for use with `askAboutDocument` |

## Why a Separate Repository

The MCP tools here don't call `RagChatService` or `DocumentQnaService` directly as Java beans — they make real HTTP calls to `banking-ai-agent`'s existing REST API. This is intentional: the point of MCP is exposing *existing* services to AI clients, potentially services you don't own or that run in a different stack entirely. Co-locating the MCP server inside the app it wraps would have been a shortcut that only works because this project happens to own both sides — not a realistic pattern to demonstrate.

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 4.1.0 |
| MCP Integration | Spring AI 2.0.0 (`spring-ai-starter-mcp-server-webmvc`) |
| Transport | Streamable HTTP |
| HTTP Client (to the wrapped app) | Spring's `RestClient` |

## Architecture

```
                  ┌──────────────────────┐
  MCP Client ──►  │  banking-mcp-server  │  (port 8081)
  (Inspector,     │  /mcp endpoint       │
   Claude, etc.)  └──────────┬───────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │  McpToolsService     │
                  │  (@McpTool methods)  │
                  └──────────┬───────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │BankingAssistantClient│
                  │ (RestClient wrapper) │
                  └──────────┬───────────┘
                             │  HTTP calls
                             ▼
                  ┌──────────────────────┐
                  │ banking-ai-agent     │  (port 8080)
                  │  /api/chat           │
                  │  /api/documents/*    │
                  └──────────────────────┘
```

An MCP client calls a tool over Streamable HTTP → Spring AI routes it to the matching `@McpTool`-annotated method → that method delegates to `BankingAssistantClient`, which makes a real REST call to the original banking assistant app → the response flows back through the same chain to the MCP client.

## Getting Started

### Prerequisites

- Java 21, Maven
- The [`banking-ai-agent`](../banking-ai-agent) project running on port 8080 (this server has nothing to expose without it)

### 1. Configure environment variables

```
ADMIN_API_KEY=<same value as banking-ai-agent's .env>
```

Must match the key `banking-ai-agent` expects on its admin/upload endpoints, since `uploadDocument` calls through to a protected endpoint there.

### 2. Run both apps

```bash
# Terminal 1 — the app being wrapped, port 8080
cd banking-ai-agent
mvn spring-boot:run

# Terminal 2 — this MCP server, port 8081
cd banking-mcp-server
mvn spring-boot:run
```

## Configuration Reference

```properties
server.port=8081
banking.assistant.base-url=http://localhost:8080
banking.assistant.admin-api-key=${ADMIN_API_KEY}

spring.ai.mcp.server.name=banking-mcp-server
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.type=SYNC
spring.ai.mcp.server.protocol=STREAMABLE
```

The MCP endpoint is auto-registered at `http://localhost:8081/mcp` — no controller code needed for it.

## Testing with MCP Inspector

The [official MCP Inspector](https://github.com/modelcontextprotocol/inspector) is the fastest way to verify this server without writing any client code:

```bash
npx @modelcontextprotocol/inspector
```

Open `http://localhost:6274`, set **Transport Type** to **Streamable HTTP**, set the URL to `http://localhost:8081/mcp`, click **Connect**, then **List Tools**.

**For `answerBankingFaq` and `askAboutDocument`** — short text parameters — Inspector's CLI mode also works well and is scriptable:

```bash
npx @modelcontextprotocol/inspector --cli http://localhost:8081/mcp \
  --transport http \
  --method tools/call \
  --tool-name answerBankingFaq \
  --tool-arg question="What is the penalty for early FD withdrawal?"
```

**For `uploadDocument`, use the web UI, not the CLI.** Base64-encoding a whole PDF and passing it as a command-line argument runs into OS-level argument length limits (encountered directly during development — Windows caps total command-line length at roughly 32KB, well below what a Base64-encoded PDF needs). The browser text field has no such limit:

```bash
base64 yourfile.pdf | tr -d '\n'
```
Paste the output into the `base64Content` field in Inspector's web UI alongside a `fileName`, and run the tool from there.

## Design Decisions Worth Noting

- **Calls the original app over HTTP, not in-process.** See "Why a Separate Repository" above — this mirrors how MCP servers wrap real, independently-owned services in practice.
- **Conversation IDs are freshly generated per call, not reused.** Each `answerBankingFaq` call gets a new UUID, so this tool doesn't carry conversation memory between MCP calls the way the original app's `/api/chat` does for a single ongoing session. A reasonable simplification, since MCP clients typically treat each tool call independently.
- **File upload uses Base64 encoding, a real but known-limited approach.** Works for portfolio-scale test files; not how a production MCP server would handle large file transfer (a dedicated resource/attachment mechanism would be more appropriate at scale). This isn't a hypothetical caveat — it's the direct cause of the CLI argument-length failure documented above.

## Known Limitations (Honest Scope)

- **Account-scoped tools (balance, transactions, transfers) are not exposed here.** The original app's `AccountTools` deliberately binds the current account ID at construction time, per request, so the model never controls which account it acts on — that scoping mechanism doesn't map cleanly onto MCP's usual "tools live on an auto-discovered singleton bean" model. Exposing these safely over MCP would require pulling account context from the MCP transport layer itself (via `McpTransportContext`/`contextExtractor`, reading something like an auth header) rather than a constructor parameter — a deliberate follow-up, not an oversight.
- **No upload-size handling or validation beyond what the wrapped app already does.** Large files will hit the Base64/CLI limitations described above before they hit any application-level validation.
- **Shares the same admin API key as the wrapped app**, rather than having its own independent authentication layer for MCP clients. Acceptable for a portfolio project; a production deployment would likely want MCP-level auth (e.g., bearer tokens per client) independent of the wrapped app's own admin key.

## Relationship to `banking-ai-agent`

This project has no value without `banking-ai-agent` running alongside it — it is a protocol adapter, not a standalone service. See that project's own README for the underlying RAG, document Q&A, and banking actions implementation this server exposes.

## What's Next

- Expose document upload's counterpart, `GET /api/documents/{documentId}` (status check), as a fourth MCP tool
- Solve the account-scoping gap described above, so balance/transaction/transfer tools can be safely exposed via MCP
- Use this server as a tool provider for a future multi-agent orchestration layer — the original motivation for building an MCP server before tackling multi-agent design