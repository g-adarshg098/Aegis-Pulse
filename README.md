# Aegis Pulse Engine 🛡️⚡
> **Enterprise RAG & Agentic Hybrid Knowledge Platform powered by Spring AI**

Aegis Pulse Engine is an enterprise-grade AI gateway and retrieval-augmented generation (RAG) platform that balances **ultra-fast cloud reasoning** with **strict on-premise data governance**. 

It intelligently routes prompts between **Groq Cloud (Llama 3.3 70B)** for maximum throughput and a **Local Ollama instance (RTX 2050)** for confidential/air-gapped execution, backed by **PostgreSQL (PGVector)**, **Redis Cache**, and comprehensive **Security Guardrails**.

---

## 🏛️ Architecture Overview

```
                                +-----------------------------+
                                |  Client / Web Dashboard UI  |
                                +--------------+--------------+
                                               |
                                               v
                                +-----------------------------+
                                |      AgentController        |
                                +--------------+--------------+
                                               |
                                               v
                                +-----------------------------+
                                |   Security Guardrail Engine |
                                |  (PII, SSN, API Keys, Cards)|
                                +--------------+--------------+
                                               |
                                               v
                                +-----------------------------+
                                |     Aegis Router Service    |
                                +-------+-------------+-------+
                                        |             |
                     [Confidential /    |             |    [Standard Speed]
                     Air-Gapped Data]   |             |
                                        v             v
                        +---------------+--+       +--+----------------+
                        |   Local Ollama   |       |    Cloud Groq     |
                        |   (RTX 2050)     |<------+    (Llama 3.3 70B)|
                        +------------------+ [Auto  +-------------------+
                                              Failover]
                                        |
                 +----------------------+----------------------+
                 |                                             |
                 v                                             v
+----------------------------------+          +----------------------------------+
|    RAG & Knowledge Ingestion     |          |       Dual-Tier Memory           |
| (PDF, Word, Markdown, Text, Tika)|          | (Redis Cache + PostgreSQL JPA)   |
|         + PgVectorStore          |          |     + Comprehensive Audit Logs   |
+----------------------------------+          +----------------------------------+
```

---

## 🌟 Key Features

1. **Intelligent Hybrid Routing with Auto-Failover**:
   - **Cloud Groq Inference**: Blazing-fast inference using Llama 3.3 70B.
   - **Local Ollama Inference**: Private, secure execution on local GPU (RTX 2050).
   - **Confidentiality / PII Guardrail**: Automatic detection of credit cards, SSNs, API tokens, and classified keywords — automatically redirecting prompts to local Ollama.
   - **Resilient Failover**: If Groq experiences rate limits or network latency, the engine automatically fails over to the local engine.

2. **Universal RAG & Document Ingestion Pipeline**:
   - Ingests **PDFs**, **Word (.docx)**, **Presentations**, **Markdown (.md)**, **Plain Text (.txt)**, and raw JSON payloads.
   - Chunked using `TokenTextSplitter` (configurable chunk sizes and overlap).
   - Embedded and indexed into PostgreSQL `pgvector` with a resilient in-memory fallback.
   - Dynamic citation extraction with source document names, chunk indexes, and similarity scores.

3. **Dual-Tier Conversation Memory**:
   - **Tier 1 (Speed)**: Redis cache for rapid multi-turn context retrieval.
   - **Tier 2 (Persistence)**: PostgreSQL `chat_messages` table for permanent session history and recovery.
   - **Graceful Fallback**: Automatically degrades to in-memory caching if Redis is temporarily offline.

4. **Agentic Tool Execution**:
   - `searchKnowledgeBase`: Allows the LLM agent to dynamically query the vector store on-demand during conversation.
   - `checkSystemPulse`: Allows the LLM to inspect live database connectivity, document count, and platform status.

5. **Integrated Glassmorphic Web Dashboard**:
   - Built-in UI served at `http://localhost:8080/` with live streaming SSE chat, document uploader, vector search tester, and real-time audit logs.

---

## 🚀 Getting Started

### Prerequisites
- **Java 21**
- **Maven 3.9+** (or use included `./mvnw.cmd`)
- **PostgreSQL** with `pgvector` extension
- **Ollama** (optional for local offline execution, run `ollama run llama3.2`)
- **Redis** (optional for distributed session caching)

### Running the Application

```powershell
# 1. Clone repository and navigate to folder
cd aegis-pulse-engine

# 2. Run with Maven Wrapper
.\mvnw.cmd spring-boot:run
```

Once started, open your browser at **`http://localhost:8080`** to access the Aegis Pulse Dashboard.

---

## 📡 REST API Reference

### 1. Agent & Chat Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/agent/chat` | Non-streaming JSON response with citations, execution time, and model information. |
| `POST` | `/api/v1/agent/chat/stream` | Server-Sent Events (SSE) streaming chat with JSON payload and session management. |
| `GET` | `/api/v1/agent/stream` | Browser/Curl friendly SSE streaming query (`?message=...&confidential=false`). |
| `GET` | `/api/v1/agent/sessions/{sessionId}/history` | Retrieves full conversation history for a given session. |
| `DELETE` | `/api/v1/agent/sessions/{sessionId}` | Clears active conversation memory for a session. |

#### Example Request (`POST /api/v1/agent/chat`)
```json
{
  "prompt": "What are our internal data security policies?",
  "sessionId": "usr-session-001",
  "confidential": false,
  "useRag": true
}
```

### 2. Document & RAG Ingestion Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/documents/upload` | Multipart file upload (`PDF`, `DOCX`, `TXT`, `MD`) with optional tags. |
| `POST` | `/api/v1/documents/text` | Direct text / JSON payload ingestion (`{"title": "...", "content": "..."}`). |
| `GET` | `/api/v1/documents` | Lists all ingested documents and their chunk counts. |
| `DELETE` | `/api/v1/documents/{documentId}` | Deletes document metadata and vector embeddings. |
| `GET` | `/api/v1/documents/search` | Tests similarity search directly (`?query=encryption&topK=3`). |

### 3. System Observability & Audit Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/system/status` | Returns connectivity status of Groq, Ollama, PostgreSQL, and Redis. |
| `GET` | `/api/v1/system/audit-logs` | Retrieves recent audit records (`?limit=20`). |

---

## 🧪 Testing

To execute the automated unit and integration tests:

```powershell
.\mvnw.cmd test
```

All 13 tests covering guardrails, hybrid routing, dual-tier memory, and vector search execute in an isolated in-memory H2 profile without requiring external cloud/database connections.
