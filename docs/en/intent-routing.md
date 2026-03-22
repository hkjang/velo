# Intent-Based Routing

The core routing engine of Velo AI Platform that automatically analyzes user prompts, extracts keywords to determine intent, and routes requests to the optimal model.

## Overview

### Why Intent-Based Routing?

Routing all AI requests to a single model leads to suboptimal cost and quality.

| Intent | Optimal Model | Reason |
|--------|--------------|--------|
| Summarization | Long-context model | Specialized for long document processing |
| Code | Code-specialized model | Higher accuracy for SQL/function generation |
| Translation | Lightweight translation model | Cost savings + faster response |
| Conversation | General-purpose model | Handles everyday dialogue |

Intent-based routing **automatically selects** the right model by analyzing the prompt, reducing costs while improving quality.

### Processing Pipeline

```
Request → Text Normalization → Keyword Detection → Intent Resolution → Policy Lookup → Route Execution → Audit Log
```

| Step | Component | Description |
|------|-----------|-------------|
| 1 | RequestNormalizer | Lowercase, remove special chars, strip stop words, tokenize |
| 2 | KeywordMatcher | Match against registered keywords/synonyms, sort by priority |
| 3 | IntentResolver | Determine final intent from matches (priority/frequency-based) |
| 4 | RoutingPolicyService | Look up routing policy mapped to the intent |
| 5 | RouteDecisionEngine | Determine final model/endpoint |
| 6 | RouteAuditLogger | Record decision history (max 1000 entries, FIFO) |

## Supported Intent Types (10)

| Intent | Enum | Description | Default Keywords |
|--------|------|-------------|-----------------|
| Summarization | `SUMMARIZATION` | Route to long-context model | 요약, 정리, 핵심, summarize, summary |
| Generation | `GENERATION` | Route to high-quality generation model | 작성, 만들어, generate, draft |
| Code | `CODE` | Route to code-specialized model | 코드, 함수, SQL, 쿼리, script, code |
| Classification | `CLASSIFICATION` | Route to small fast model | 분류, 카테고리, classify |
| Extraction | `EXTRACTION` | Route to structured output model | 추출, 항목화, extract |
| Search | `SEARCH` | Route to RAG pipeline | 검색, 찾아, 문서, search, find |
| Validation | `VALIDATION` | Route to reasoning/guard model | 검토, 점검, validate, review |
| Translation | `TRANSLATION` | Route to lightweight translation model | 번역, translate |
| Conversation | `CONVERSATION` | Route to general conversation model | 안녕, chat, 상담 |
| General | `GENERAL` | Default model (no keyword match) | — |

## Default Routing Policies

The following policies are bootstrapped automatically on server start.

| Priority | Intent | Route Target | Model | Fallback |
|----------|--------|-------------|-------|----------|
| 100 | SUMMARIZATION | vllm-long | qwen-long | llm-general |
| 95 | GENERATION | vllm-premium | deepseek-large | llm-general |
| 90 | CODE | vllm-code | codestral | llm-general |
| 85 | CLASSIFICATION | vllm-fast | small-classifier | llm-general |
| 85 | EXTRACTION | vllm-structured | extract-model | llm-general |
| 80 | SEARCH | rag-pipeline | embed-rerank-llm | llm-general |
| 80 | VALIDATION | vllm-reasoning | reasoning-model | llm-general |
| 75 | TRANSLATION | vllm-translate | nllb-large | llm-general |
| 50 | CONVERSATION | vllm-balanced | llm-general | llm-general |
| 10 | GENERAL | default | llm-general | llm-general |

## API Endpoints

### Route Test

Analyze a prompt and return the routing decision.

```bash
curl -X POST http://localhost:8080/ai-platform/api/intent/test \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Summarize this month sales report"}'
```

Response:
```json
{
  "resolvedIntent": "SUMMARIZATION",
  "intentLabel": "요약",
  "matchedKeyword": "summarize",
  "policyId": "pol-sum",
  "routeTarget": "vllm-long",
  "modelName": "qwen-long",
  "fallbackModel": "llm-general",
  "priority": 100,
  "reasoning": "Keyword 'summarize' matched → SUMMARIZATION intent",
  "candidateKeywords": ["summarize"],
  "processingTimeMicros": 2150
}
```

### Keyword Analysis Preview

View normalized text and matched keywords before routing.

```bash
curl -X POST http://localhost:8080/ai-platform/api/intent/preview \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Review this code and write a SQL query"}'
```

### Keyword CRUD

```bash
# List all keywords
curl http://localhost:8080/ai-platform/api/intent/keywords

# Create a keyword
curl -X POST http://localhost:8080/ai-platform/api/intent/keywords \
  -H "Content-Type: application/json" \
  -d '{
    "primaryKeyword": "report",
    "synonyms": "reporting,dashboard",
    "intent": "SUMMARIZATION",
    "priority": 90
  }'

# Update a keyword
curl -X PUT http://localhost:8080/ai-platform/api/intent/keywords/{keywordId} \
  -H "Content-Type: application/json" \
  -d '{
    "primaryKeyword": "report",
    "synonyms": "reporting,dashboard,analytics",
    "intent": "SUMMARIZATION",
    "priority": 95,
    "enabled": true
  }'

# Delete a keyword
curl -X DELETE http://localhost:8080/ai-platform/api/intent/keywords/{keywordId}
```

### Policy CRUD

```bash
# List all policies
curl http://localhost:8080/ai-platform/api/intent/policies

# Create a policy
curl -X POST http://localhost:8080/ai-platform/api/intent/policies \
  -H "Content-Type: application/json" \
  -d '{
    "intent": "SUMMARIZATION",
    "priority": 90,
    "routeTarget": "vllm-long",
    "modelName": "qwen-72b",
    "fallbackModel": "llm-general",
    "streamingPreferred": false,
    "tenantOverride": "",
    "maxInputTokens": 128000
  }'

# Update a policy
curl -X PUT http://localhost:8080/ai-platform/api/intent/policies/{policyId} \
  -H "Content-Type: application/json" \
  -d '{
    "intent": "SUMMARIZATION",
    "priority": 95,
    "routeTarget": "vllm-long",
    "modelName": "qwen-72b",
    "fallbackModel": "llm-general",
    "enabled": true
  }'

# Delete a policy
curl -X DELETE http://localhost:8080/ai-platform/api/intent/policies/{policyId}
```

### Audit Log & Statistics

```bash
# Recent routing audit log (default 50 entries)
curl http://localhost:8080/ai-platform/api/intent/audit?limit=20

# Intent distribution statistics
curl http://localhost:8080/ai-platform/api/intent/stats
```

Statistics response:
```json
{
  "totalRoutes": 150,
  "fallbackRoutes": 3,
  "avgProcessingMicros": 245.3,
  "auditLogSize": 150,
  "keywordCount": 12,
  "policyCount": 10,
  "intentDistribution": {
    "SUMMARIZATION": 45,
    "CODE": 32,
    "GENERATION": 28,
    "TRANSLATION": 15,
    "CONVERSATION": 30
  }
}
```

## Architecture

### Class Structure

```
io.velo.was.aiplatform.intent/
  ├── IntentType.java                  # Intent type enum (10 types)
  ├── IntentKeyword.java               # Keyword + synonyms + priority model
  ├── RoutingPolicy.java               # Per-intent routing policy model
  ├── IntentRouteDecision.java         # Routing decision result
  ├── RouteAuditEntry.java             # Audit log entry
  ├── RequestNormalizer.java           # Text normalizer
  ├── KeywordMatcher.java              # Keyword matching engine
  ├── IntentResolver.java              # Intent resolver
  ├── IntentRoutingPolicyService.java  # Policy CRUD + in-memory cache
  ├── RouteDecisionEngine.java         # Pipeline orchestrator
  └── RouteAuditLogger.java            # Audit logger (FIFO 1000 entries)
```

### Data Flow

```
                   ┌──────────────────┐
                   │   User Prompt    │
                   └────────┬─────────┘
                            ▼
              ┌─────────────────────────┐
              │  RequestNormalizer       │
              │  Lowercase + Stop Words │
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │  KeywordMatcher         │
              │  Keyword/Synonym Match  │
              │  Priority Sort          │
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │  IntentResolver         │
              │  Top Priority Intent    │
              │  Frequency Tiebreak     │
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │  RoutingPolicyService   │
              │  Intent → Policy Lookup │
              │  Tenant/Token Filter    │
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │  RouteDecisionEngine    │
              │  Final Model Decision   │
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │  RouteAuditLogger       │
              │  Record Audit Entry     │
              └─────────────────────────┘
```

### Intent Resolution Algorithm

1. Match all registered keywords against normalized text
2. Sort matches by **priority descending**
3. Within the top-priority group:
   - Count matches per intent type
   - Select the intent with the most matches
4. If no matches, return `GENERAL` (default routing)

### Persistence

All keywords and policies are automatically persisted to JSON files.

```
data/ai-platform/
  ├── keywords.json    # Intent keywords
  └── policies.json    # Routing policies
```

- **Write-through**: Changes are saved immediately on CRUD operations
- **On restart**: Automatically restored from files (defaults bootstrapped if missing)
- **Jackson 2.18** based serialization

## Dashboard UI

Managed through the **🎯 Intent Routing** tab in the AI Platform console.

### Features

| Feature | Description |
|---------|-------------|
| Route Test | Enter prompt → keyword analysis → intent decision → routing result |
| Keyword Preview | View normalized text, matched keywords, estimated token count |
| Keyword Management | Register/update/delete keywords with synonyms and priorities |
| Policy Management | View/modify routing policies per intent |
| Statistics | Intent distribution, avg processing time, fallback rate |
| Audit Log | Recent routing decision history |

## Roadmap

| Phase | Scope | Status |
|-------|-------|--------|
| Phase 1 | Static keyword rules + memory cache | ✅ Complete |
| Phase 2 | Redis integration (shared policies across instances) | Planned |
| Phase 3 | Admin real-time policy editing | ✅ Complete |
| Phase 4 | Pub/Sub cache synchronization | Planned |
| Phase 5 | Tenant Override (per-customer routing) | Partial |
| Phase 6 | ML Intent Classifier enhancement | Planned |

## Python Client Example

```python
import requests

BASE = "http://localhost:8080/ai-platform"

# Test intent routing
resp = requests.post(f"{BASE}/api/intent/test", json={
    "prompt": "Summarize this month's sales report"
})
decision = resp.json()
print(f"Intent: {decision['intentLabel']}")
print(f"Model: {decision['modelName']}")
print(f"Processing: {decision['processingTimeMicros']}μs")

# Register a keyword
requests.post(f"{BASE}/api/intent/keywords", json={
    "primaryKeyword": "report",
    "synonyms": "reporting,dashboard",
    "intent": "SUMMARIZATION",
    "priority": 90
})
```
