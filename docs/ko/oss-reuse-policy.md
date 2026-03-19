# Apache/MIT 재사용 중심 WAS 개발 정책

이 문서는 `velo-was`를 Servlet 6 호환 WAS로 고도화하면서, 외부 오픈소스는
Apache License 2.0 또는 MIT 범위 안에서 우선 재사용하고 자체 구현은
차별화 영역에 집중하기 위한 개발 원칙을 고정한다.

## 목표

- 검증된 코어는 재작성하지 않고 우선 재사용한다.
- Tomcat 계열 구현체를 1순위 참고/반입 후보로 유지한다.
- 외부 소스 반입 시 원본, 패치, 내부 확장을 분리해 추적 가능하게 만든다.
- 배포 전 라이선스, NOTICE, 상표 리스크를 자동 점검한다.

## 적용 범위

- `vendor/` 아래에 커밋되는 외부 소스
- Maven 직접 의존성
- 향후 배포 산출물에 포함되는 NOTICE, THIRD PARTY, 라이선스 메타데이터

## 재사용 우선순위

1. Apache Tomcat 최신 안정 계열의 Apache-2.0 소스
2. Apache-2.0 또는 MIT 라이선스의 보조 라이브러리와 레퍼런스 구현
3. 내부 래퍼, 어댑터, 정책 계층
4. 자체 신규 구현

## 기본 원칙

- 코어 직접 수정 최소화: 가능하면 wrapper, adapter, facade, extension으로 우회한다.
- 업스트림 추적 유지: 외부 소스는 반드시 upstream tag 또는 release 기준으로 반입한다.
- 복붙 금지: 출처, 버전, 라이선스, NOTICE 여부가 불명확한 코드는 반입하지 않는다.
- 상표 분리: Apache, Tomcat 명칭은 코드 출처와 제품 브랜딩을 분리해 관리한다.

## 반드시 재사용 우선 검토할 영역

- HTTP 요청 파싱
- Servlet 매핑과 dispatcher
- Filter chain
- AsyncContext 기초 동작
- 세션 기본 로직
- 정적 리소스 조건부 응답과 캐시 기초
- 에러 dispatch 흐름
- WebApp classloader / WAR 처리 기반

## 자체 구현 우선 영역

- 운영 콘솔
- 관측성 계층
- CSP / CORS / reverse proxy 정책 엔진
- JS 렌더링 친화 분석 기능
- 구성 관리와 secret 연계
- 확장 플러그인 구조

## 저장소 구조 규칙

`vendor/` 아래는 아래 구조를 강제한다.

```text
vendor/
  upstream/<component>/<version>/   # 원본 소스, 무수정 보존
  patches/<component>/<version>/    # 업스트림 대비 내부 패치
  adapters/<component>/<version>/   # 우리 쪽 래퍼/확장 계층
```

추가 규칙:

- `vendor/upstream` 에는 원본 소스 외 임의 수정 파일을 넣지 않는다.
- 업스트림 소스 반입 시 `LICENSE`, `NOTICE`, 원본 헤더를 그대로 보존한다.
- 모든 반입 건은 `third_party/reuse-manifest.json` 에 등록한다.
- 소스 변경이 필요한 경우 `vendor/patches` 에 patch 또는 변경 이력을 남긴다.

## 반입 절차

1. SPDX 라이선스 식별
2. `LICENSE` / `NOTICE` 존재 확인
3. 상표 사용 여부 분리 검토
4. 업스트림 tag, source URL, 로컬 경로 기록
5. `third_party/reuse-manifest.json` 갱신
6. `scripts/check-oss-compliance.ps1` 실행
7. 배포 전 `NOTICE` 와 `THIRD_PARTY.md` 갱신

Apache Tomcat 업스트림 소스 반입의 기본 스캐폴드는
`scripts/import-apache-tomcat-source.ps1` 로 제공한다.

## CI 게이트

`scripts/check-oss-compliance.ps1` 는 다음을 검증한다.

- 필수 거버넌스 파일 존재 여부
- 직접 의존성의 allow / review-required / blocked 판정
- `vendor/upstream` 반입 소스의 manifest 등록 여부
- vendored source 의 라이선스가 `Apache-2.0` 또는 `MIT` 인지 여부
- vendored source 의 `LICENSE` / `NOTICE` 보존 여부

GitHub Actions 워크플로 `oss-compliance.yml` 은 위 스크립트를 실행하고 보고서를
artifact 로 남긴다.

## 현재 기준선의 해석

현재 저장소의 직접 의존성 중 아래 항목은 `Apache-2.0` 또는 `MIT` 로 닫히지 않기
때문에 `review-required` 로 관리한다.

- `jakarta.servlet:jakarta.servlet-api`
- `jakarta.servlet.jsp:jakarta.servlet.jsp-api`
- `jakarta.el:jakarta.el-api`
- `org.jline:jline`
- `com.h2database:h2`
- `org.junit.jupiter:junit-jupiter`

이 항목들은 즉시 금지 대상으로 숨기지 않고, 배포/법무 승인 트랙이 필요하다는
사실을 드러내는 용도로 관리한다. 반면, 소스 반입 경로인 `vendor/upstream` 은
현 시점부터 Apache-2.0 / MIT 외 라이선스를 허용하지 않는다.

## 다음 개발 순서

1. Tomcat 11 기준 재사용 후보를 `third_party/reuse-manifest.json` 초안으로 등록
2. servlet mapping, dispatcher, filter chain 후보 코드를 `vendor/upstream` 구조로 반입
3. adapter 계층에서 기존 `was-servlet-core` 와 연결
4. Servlet 호환 회귀 테스트를 업스트림 테스트 자산과 함께 확장
