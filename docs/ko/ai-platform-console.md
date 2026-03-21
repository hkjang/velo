# AI Platform Console

Velo WAS에 기본 탑재되는 별도 AI Platform 모듈 문서입니다.

- `was-webadmin`에 붙지 않고 독립적으로 배포됩니다.
- `server.aiPlatform.console.contextPath` 설정으로 별도 콘솔 경로를 가집니다.
- 콘솔 화면뿐 아니라 공개 AI Gateway API와 개발자 포털도 함께 제공합니다.

## 기본 접속 경로

- 콘솔: `http://localhost:8080/ai-platform`
- 인증: 콘솔 화면은 기존 WAS 관리자 계정을 재사용합니다.
- 공개 API: Gateway와 API 문서 엔드포인트는 서비스 API 용도로 공개됩니다.

## 핵심 설정

```yaml
server:
  aiPlatform:
    enabled: true
    mode: PLATFORM
    console:
      enabled: true
      contextPath: /ai-platform
```

## 제공 기능

- Overview: 핵심 방향, 기본 전략, P99 지연 목표, 캐시 TTL 표시
- Serving: 멀티모델 라우팅, A/B 테스트, 자동 모델 선택, 번들 모델, 정책 목록
- Platform: 모델 등록, API 자동 생성, 버전 관리, 과금, 개발자 포털, 멀티테넌트 표시
- Advanced: 프롬프트 라우팅, 컨텍스트 캐시, AI Gateway, 관측성, GPU 스케줄링 표시
- Gateway Sandbox: 콘솔 안에서 route/infer/stream 호출 가능
- Roadmap: 기본 서빙부터 사업화까지 단계별 로드맵 제공

## 공개 API

- `GET {contextPath}/api/status`
- `GET {contextPath}/api/overview`
- `GET {contextPath}/gateway`
- `POST {contextPath}/gateway/route`
- `POST {contextPath}/gateway/infer`
- `GET {contextPath}/gateway/stream`
- `GET {contextPath}/api-docs`
- `GET {contextPath}/api-docs/ui`

## 위치

- 모듈: `was-ai-platform`
- 부트스트랩 연결: `was-bootstrap`