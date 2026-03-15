# WAR 배포 + 클래스로더 격리

WAR 파일 및 Exploded WAR 디렉토리 배포를 지원하며, 애플리케이션별 클래스로더 격리를 제공한다.

## 모듈

- `was-deploy` — WAR 배포 파이프라인
- `was-classloader` — 웹 애플리케이션 클래스로더

## 배포 흐름

```
WAR 파일 / Exploded 디렉토리
    │
    ▼
┌─────────────────────────────────────────┐
│ 1. WarExtractor                          │
│    .war → 임시 디렉토리 추출             │
│    ZIP 슬립 방어 포함                    │
│    Exploded WAR → 그대로 사용            │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│ 2. WebXmlParser                          │
│    WEB-INF/web.xml DOM 파싱              │
│    DOCTYPE 비활성화 (XXE 방어)           │
│    → WebXmlDescriptor (불변 record)      │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│ 3. WebAppClassLoader                     │
│    WEB-INF/classes → classpath           │
│    WEB-INF/lib/*.jar → classpath         │
│    parent-first 또는 child-first 전략    │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────┐
│ 4. WarDeployer                           │
│    서블릿/필터/리스너 인스턴스화         │
│    URL 패턴 매핑 등록                    │
│    context-param 설정                    │
│    → DeploymentResult                    │
└─────────────────────────────────────────┘
```

## 핵심 컴포넌트

### WarDeployer

배포 파이프라인의 오케스트레이터.

```java
WarDeployer deployer = new WarDeployer(workDirectory);
DeploymentResult result = deployer.deploy(warPath, "/myapp");
```

**deploy() 처리 순서:**

1. 소스 유형 판별: `.war` 파일이면 `WarExtractor`로 추출, 디렉토리면 직접 사용
2. `WEB-INF/web.xml` 파싱 → `WebXmlDescriptor`
3. `WebAppClassLoader` 생성 (격리된 classpath)
4. 서블릿 인스턴스화 + URL 패턴 등록
5. 필터 인스턴스화 + URL 패턴 + `DispatcherType` 등록
6. 리스너 인스턴스화 (`ServletContextListener`, `ServletRequestListener`)
7. `SimpleServletApplication` 빌드 → `DeploymentResult` 반환

**cleanup():** 추출된 임시 디렉토리 삭제 + 클래스로더 close

### WarExtractor

WAR 파일 추출 유틸리티.

| 메서드 | 설명 |
|---|---|
| `extract(Path warFile, Path targetDir)` | WAR 압축 해제, ZIP 슬립 방어 포함 |
| `isExplodedWar(Path directory)` | `WEB-INF` 디렉토리 존재 여부로 Exploded WAR 판별 |

**보안:** 엔트리 경로를 정규화하여 `..` 을 통한 디렉토리 탈출(ZIP Slip)을 방지한다.

### WebXmlParser

DOM 기반 `web.xml` 파서.

- `DocumentBuilderFactory` 보안 설정: DOCTYPE, 외부 엔티티 비활성화 (XXE 방어)
- `web.xml`이 없으면 빈 `WebXmlDescriptor` 반환
- 네임스페이스 인식 활성화

### WebXmlDescriptor

파싱된 배포 기술자의 불변 record.

```java
record WebXmlDescriptor(
    String displayName,
    Map<String, String> contextParams,
    List<ServletDef> servlets,
    List<ServletMapping> servletMappings,
    List<FilterDef> filters,
    List<FilterMapping> filterMappings,
    List<String> listenerClasses,
    List<String> welcomeFiles
)
```

**중첩 record:**

| Record | 필드 |
|---|---|
| `ServletDef` | name, className, initParams, loadOnStartup, asyncSupported |
| `ServletMapping` | servletName, urlPattern |
| `FilterDef` | name, className, initParams, asyncSupported |
| `FilterMapping` | filterName, urlPattern, dispatchers |

## 클래스로더 격리

### WebAppClassLoader

`URLClassLoader`를 확장하여 웹 애플리케이션별 격리된 클래스 로딩을 제공한다.

```java
WebAppClassLoader cl = WebAppClassLoader.create("myApp", appRoot, parentClassLoader);
// 또는 child-first:
WebAppClassLoader cl = WebAppClassLoader.create("myApp", appRoot, parentClassLoader, true);
```

**classpath 구성:**
1. `WEB-INF/classes/` 디렉토리
2. `WEB-INF/lib/*.jar` 파일들

### 위임 전략

| 전략 | 동작 | 사용 시나리오 |
|---|---|---|
| **Parent-first** (기본) | 부모 → 자식 순으로 탐색 | 서버 안정성 우선 |
| **Child-first** | 자식 → 부모 순으로 탐색 | 앱별 라이브러리 버전 격리 |

**Child-first 예외:** `java.*` 및 `jakarta.servlet.*` 패키지는 항상 부모에 위임하여 `LinkageError`를 방지한다.

### 격리 원리

```
Server ClassLoader (parent)
  ├── WebAppClassLoader[app-A]    ← 독립적 Class<?> 인스턴스
  │     ├── WEB-INF/classes/
  │     └── WEB-INF/lib/*.jar
  └── WebAppClassLoader[app-B]    ← 독립적 Class<?> 인스턴스
        ├── WEB-INF/classes/
        └── WEB-INF/lib/*.jar
```

- 동일한 FQCN이라도 서로 다른 `Class<?>` 객체로 로드
- 앱 간 static 변수 등 상태 공유 없음
- `getResource()` 도 동일한 위임 전략 적용

## 배포 디렉토리 및 핫 디플로이

### DeploymentRegistry

배포된 애플리케이션의 라이프사이클을 관리하는 레지스트리. `WarDeployer`와 `SimpleServletContainer`를 연동하여 배포/언배포/재배포를 조율한다.

```java
DeploymentRegistry registry = new DeploymentRegistry(deployer, container);

// WAR 파일 배포 (context path 자동 결정)
registry.deploy(Paths.get("deploy/myapp.war"));   // → /myapp
registry.deploy(Paths.get("deploy/ROOT.war"));     // → "" (루트)

// 언배포
registry.undeploy("myapp");

// 재배포 (언배포 + 배포)
registry.redeploy(Paths.get("deploy/myapp.war"));

// 전체 언배포
registry.undeployAll();
```

**Context path 결정 규칙:**

| WAR 파일명 | Context Path |
|---|---|
| `myapp.war` | `/myapp` |
| `ROOT.war` | `""` (루트) |
| `api-v2.war` | `/api-v2` |

### HotDeployWatcher

`java.nio.file.WatchService`를 사용하여 배포 디렉토리를 감시하고, WAR 파일 변경을 감지하여 자동으로 배포/언배포/재배포를 수행한다.

```java
HotDeployWatcher watcher = new HotDeployWatcher(deployDir, registry, debounceSeconds);
watcher.start();  // 데몬 스레드 시작
// ...
watcher.close();  // 감시 중지
```

**이벤트 매핑:**

| 파일 시스템 이벤트 | 동작 |
|---|---|
| `ENTRY_CREATE` (.war) | `registry.deploy()` |
| `ENTRY_DELETE` (.war) | `registry.undeploy()` |
| `ENTRY_MODIFY` (.war) | `registry.redeploy()` |

**디바운스:** `scanIntervalSeconds` 설정값만큼 대기 후 이벤트를 처리하여 파일 복사 완료를 보장한다.

**서버 설정:**

```yaml
server:
  deploy:
    directory: deploy             # WAR 배포 디렉토리
    hotDeploy: false              # 핫 디플로이 활성화
    scanIntervalSeconds: 5        # 디렉토리 감시 디바운스
```

### 기동 시 자동 배포

서버 기동 시 `VeloWasApplication`이 배포 디렉토리의 기존 `.war` 파일을 스캔하여 자동으로 배포한다. `hotDeploy`가 `true`이면 이후 파일 변경도 자동 감지한다.

## DeploymentResult

배포 결과를 담는 불변 record.

```java
record DeploymentResult(
    ServletApplication application,
    Path appRoot,
    WebAppClassLoader classLoader,
    WebXmlDescriptor descriptor,
    boolean extracted        // WAR 추출 여부
)
```

## 소스 구조

```
was-deploy/src/main/java/io/velo/was/deploy/
├── WarDeployer.java           배포 오케스트레이터
├── WarExtractor.java          WAR 추출 유틸리티
├── WebXmlParser.java          web.xml DOM 파서
├── WebXmlDescriptor.java      파싱 결과 record
├── DeploymentException.java   배포 예외
├── DeploymentRegistry.java    배포 레지스트리 (배포/언배포/재배포 관리)
└── HotDeployWatcher.java      핫 디플로이 감시 (WatchService)

was-classloader/src/main/java/io/velo/was/classloader/
└── WebAppClassLoader.java     격리 클래스로더
```

## 테스트

```bash
# was-deploy 테스트 (11개)
mvn test -pl was-deploy -am

# was-classloader 테스트 (6개)
mvn test -pl was-classloader -am
```

| 테스트 | 검증 내용 |
|---|---|
| `deploysExplodedWarWithWebXml` | Exploded WAR 배포 + 서블릿/필터/리스너 등록 |
| `deploysWarFileWithExtraction` | WAR 파일 추출 배포 |
| `parsesWebXmlDescriptorCorrectly` | web.xml 전체 파싱 |
| `emptyDescriptorWhenNoWebXml` | web.xml 없을 때 빈 descriptor |
| `warExtractorDetectsExplodedWar` | Exploded WAR 판별 |
| `rejectsNonWarFile` | .war 아닌 파일 거부 |
| `cleanupRemovesExtractedDirectory` | 추출 디렉토리 정리 |
| `loadsClassFromWebInfClasses` | WEB-INF/classes 클래스 로드 |
| `classLoaderIsolation` | 앱 간 클래스 격리 |
| `childFirstOverridesParent` | Child-first 전략 (java.* 예외) |
| `findsResourceInClasses` | 리소스 검색 |
| `returnsNullForNonExistentClass` | 없는 클래스 예외 |
| `toStringContainsAppName` | toString 식별 |
