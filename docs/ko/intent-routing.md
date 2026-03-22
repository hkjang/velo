# 의도 기반 라우팅 (Intent-Based Routing)

Velo AI Platform의 핵심 라우팅 엔진으로, 사용자 프롬프트에서 키워드를 추출하여 의도를 자동 파악하고 최적 모델로 라우팅합니다.

## 개요

### 왜 의도 기반 라우팅인가?

모든 AI 요청을 단일 모델로 처리하면 비용과 품질이 최적화되지 않습니다.

| 의도 | 최적 모델 | 이유 |
|------|---------|------|
| 요약 | long-context 모델 | 긴 문서 처리에 특화 |
| 코드 | 코드 특화 모델 | SQL/함수 생성 정확도 |
| 번역 | 경량 번역 모델 | 비용 절감 + 빠른 응답 |
| 대화 | 범용 모델 | 일상 대화 처리 |

의도 기반 라우팅은 프롬프트를 분석하여 적합한 모델을 **자동 선택**함으로써 비용을 절감하고 품질을 높입니다.

### 처리 파이프라인

```
요청 수신 → 텍스트 정규화 → 키워드 탐지 → 의도 결정 → 정책 조회 → 라우팅 실행 → 감사 기록
```

| 단계 | 컴포넌트 | 설명 |
|------|---------|------|
| 1 | RequestNormalizer | 소문자화, 특수문자 제거, 불용어 제거, 토큰화 |
| 2 | KeywordMatcher | 등록된 키워드/동의어와 매칭, 우선순위 정렬 |
| 3 | IntentResolver | 매칭 결과에서 최종 의도 결정 (우선순위/빈도 기반) |
| 4 | RoutingPolicyService | 의도에 매핑된 라우팅 정책 조회 |
| 5 | RouteDecisionEngine | 최종 모델/엔드포인트 결정 |
| 6 | RouteAuditLogger | 결정 이력 기록 (최대 1000건) |

## 지원 의도 유형 (10종)

| 의도 | Enum | 설명 | 기본 키워드 |
|------|------|------|-----------|
| 요약 | `SUMMARIZATION` | long-context 모델로 라우팅 | 요약, 정리, 핵심, summarize |
| 생성 | `GENERATION` | 고품질 생성 모델로 라우팅 | 작성, 만들어, 초안, generate |
| 코드 | `CODE` | 코드 특화 모델로 라우팅 | 코드, 함수, SQL, 쿼리, script |
| 분류 | `CLASSIFICATION` | 소형 고속 모델로 라우팅 | 분류, 카테고리, 판별, classify |
| 추출 | `EXTRACTION` | 구조화 출력 모델로 라우팅 | 추출, 항목화, extract, 파싱 |
| 검색 | `SEARCH` | RAG 파이프라인으로 라우팅 | 검색, 찾아, 문서, 조회, search |
| 검증 | `VALIDATION` | reasoning/guard 모델로 라우팅 | 검토, 점검, 리스크, validate |
| 번역 | `TRANSLATION` | 경량 번역 모델로 라우팅 | 번역, 영문, 한글로, translate |
| 대화 | `CONVERSATION` | 범용 대화 모델로 라우팅 | 안녕, chat, 상담, 질문 |
| 일반 | `GENERAL` | 기본 범용 모델 (미매칭 시) | — |

## 기본 라우팅 정책

서버 시작 시 아래 정책이 자동 부트스트랩됩니다.

| 우선순위 | 의도 | 라우팅 대상 | 모델 | Fallback |
|---------|------|-----------|------|---------|
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

## API 엔드포인트

### 라우팅 테스트

프롬프트를 분석하여 라우팅 결정 결과를 반환합니다.

```bash
# 의도 라우팅 테스트
curl -X POST http://localhost:8080/ai-platform/api/intent/test \
  -H "Content-Type: application/json" \
  -d '{"prompt": "이번 달 매출 보고서를 요약해 주세요"}'
```

응답 예시:
```json
{
  "resolvedIntent": "SUMMARIZATION",
  "intentLabel": "요약",
  "matchedKeyword": "요약",
  "policyId": "pol-sum",
  "routeTarget": "vllm-long",
  "modelName": "qwen-long",
  "fallbackModel": "llm-general",
  "priority": 100,
  "reasoning": "키워드 '요약' 매칭 → 요약 의도 결정",
  "candidateKeywords": ["요약"],
  "processingTimeMicros": 3482
}
```

### 키워드 분석 미리보기

정규화된 텍스트와 매칭된 키워드를 확인합니다.

```bash
curl -X POST http://localhost:8080/ai-platform/api/intent/preview \
  -H "Content-Type: application/json" \
  -d '{"prompt": "이 코드를 검토하고 SQL 쿼리를 작성해줘"}'
```

응답 예시:
```json
{
  "normalizedText": "코드 검토하고 sql 쿼리 작성해줘",
  "estimatedTokens": 8,
  "matchedKeywords": [
    {"matchedText": "작성", "intent": "GENERATION", "priority": 95},
    {"matchedText": "코드", "intent": "CODE", "priority": 90},
    {"matchedText": "검토", "intent": "VALIDATION", "priority": 80}
  ],
  "resolvedIntent": "GENERATION",
  "intentLabel": "생성",
  "reasoning": "키워드 '작성' 매칭 → 생성 의도 결정"
}
```

### 키워드 CRUD

```bash
# 키워드 목록 조회
curl http://localhost:8080/ai-platform/api/intent/keywords

# 키워드 등록
curl -X POST http://localhost:8080/ai-platform/api/intent/keywords \
  -H "Content-Type: application/json" \
  -d '{
    "primaryKeyword": "리포트",
    "synonyms": "보고서,리포팅,report",
    "intent": "SUMMARIZATION",
    "priority": 90
  }'

# 키워드 수정
curl -X PUT http://localhost:8080/ai-platform/api/intent/keywords/kw-l \
  -H "Content-Type: application/json" \
  -d '{
    "primaryKeyword": "리포트",
    "synonyms": "보고서,리포팅,report,레포트",
    "intent": "SUMMARIZATION",
    "priority": 95,
    "enabled": true
  }'

# 키워드 삭제
curl -X DELETE http://localhost:8080/ai-platform/api/intent/keywords/kw-l
```

### 정책 CRUD

```bash
# 정책 목록 조회
curl http://localhost:8080/ai-platform/api/intent/policies

# 정책 등록
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

# 정책 수정
curl -X PUT http://localhost:8080/ai-platform/api/intent/policies/pol-l \
  -H "Content-Type: application/json" \
  -d '{
    "intent": "SUMMARIZATION",
    "priority": 95,
    "routeTarget": "vllm-long",
    "modelName": "qwen-72b",
    "fallbackModel": "llm-general",
    "enabled": true
  }'

# 정책 삭제
curl -X DELETE http://localhost:8080/ai-platform/api/intent/policies/pol-l
```

### 감사 로그 및 통계

```bash
# 최근 라우팅 감사 로그 (기본 50건)
curl http://localhost:8080/ai-platform/api/intent/audit?limit=20

# 의도별 통계
curl http://localhost:8080/ai-platform/api/intent/stats
```

통계 응답 예시:
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

## 아키텍처

### 클래스 구조

```
io.velo.was.aiplatform.intent/
  ├── IntentType.java                  # 의도 유형 열거형 (10종)
  ├── IntentKeyword.java               # 키워드 + 동의어 + 우선순위 모델
  ├── RoutingPolicy.java               # 의도별 라우팅 정책 모델
  ├── IntentRouteDecision.java         # 라우팅 결정 결과
  ├── RouteAuditEntry.java             # 감사 로그 엔트리
  ├── RequestNormalizer.java           # 텍스트 정규화기
  ├── KeywordMatcher.java              # 키워드 매칭 엔진
  ├── IntentResolver.java              # 의도 결정기
  ├── IntentRoutingPolicyService.java  # 정책 CRUD + 메모리 캐시
  ├── RouteDecisionEngine.java         # 파이프라인 오케스트레이터
  └── RouteAuditLogger.java            # 감사 로거 (FIFO 1000건)
```

### 데이터 흐름

```
                   ┌──────────────────┐
                   │  사용자 프롬프트    │
                   └────────┬─────────┘
                            ▼
              ┌─────────────────────────┐
              │  RequestNormalizer       │
              │  소문자화 + 불용어 제거    │
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │  KeywordMatcher         │
              │  키워드/동의어 매칭       │
              │  우선순위 정렬           │
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │  IntentResolver         │
              │  최고 우선순위 의도 선택   │
              │  동률 시 빈도 기반 결정    │
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │  RoutingPolicyService   │
              │  의도 → 정책 조회        │
              │  테넌트/토큰 수 필터링    │
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │  RouteDecisionEngine    │
              │  최종 모델/대상 결정      │
              └────────────┬────────────┘
                           ▼
              ┌─────────────────────────┐
              │  RouteAuditLogger       │
              │  감사 로그 기록          │
              └─────────────────────────┘
```

### 의도 결정 알고리즘

1. 정규화된 텍스트에서 등록된 모든 키워드를 매칭
2. 매칭 결과를 **우선순위 내림차순** 정렬
3. 최고 우선순위와 동일한 그룹 내에서:
   - 의도 유형별 매칭 빈도 집계
   - 가장 많이 매칭된 의도 선택
4. 매칭 없으면 `GENERAL` (기본 라우팅)

### 영속화

모든 키워드와 정책은 JSON 파일로 자동 영속화됩니다.

```
data/ai-platform/
  ├── keywords.json    # 의도 키워드
  └── policies.json    # 라우팅 정책
```

- **Write-through**: CRUD 변경 시 즉시 파일 저장
- **서버 재시작 시**: 파일에서 자동 복원 (없으면 기본값 부트스트랩)
- **Jackson 2.18** 기반 직렬화

## 대시보드 UI

AI Platform 콘솔의 **🎯 의도 라우팅** 탭에서 관리합니다.

### 제공 기능

| 기능 | 설명 |
|------|------|
| 라우팅 테스트 | 프롬프트 입력 → 키워드 분석 → 의도 결정 → 라우팅 결과 확인 |
| 키워드 미리보기 | 정규화된 텍스트, 매칭 키워드, 추정 토큰 수 확인 |
| 키워드 등록 | 주 키워드 + 동의어 + 의도 유형 + 우선순위 설정 |
| 키워드 조회 | 등록된 전체 키워드 목록 (우선순위 정렬) |
| 정책 조회 | 의도별 라우팅 정책 목록 |
| 통계 조회 | 의도별 분포, 평균 처리 시간, fallback 비율 |
| 감사 로그 | 최근 라우팅 결정 이력 |

## 확장 로드맵

| 단계 | 범위 | 상태 |
|------|------|------|
| 1단계 | 정적 키워드 룰 + 메모리 캐시 | ✅ 완료 |
| 2단계 | Redis 연동 (다중 인스턴스 공통 정책) | 예정 |
| 3단계 | Admin 실시간 정책 수정 | ✅ 완료 |
| 4단계 | Pub/Sub 캐시 동기화 | 예정 |
| 5단계 | Tenant Override (고객별 분기) | 일부 지원 |
| 6단계 | ML Intent Classifier 보완 | 예정 |

## Python 클라이언트 예시

```python
import requests

BASE = "http://localhost:8080/ai-platform"

# 의도 라우팅 테스트
resp = requests.post(f"{BASE}/api/intent/test", json={
    "prompt": "이번 달 매출 보고서를 요약해 주세요"
})
decision = resp.json()
print(f"의도: {decision['intentLabel']}")
print(f"모델: {decision['modelName']}")
print(f"처리시간: {decision['processingTimeMicros']}μs")

# 키워드 등록
requests.post(f"{BASE}/api/intent/keywords", json={
    "primaryKeyword": "리포트",
    "synonyms": "보고서,리포팅",
    "intent": "SUMMARIZATION",
    "priority": 90
})
```
