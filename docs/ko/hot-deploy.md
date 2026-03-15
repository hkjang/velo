# Hot Deploy 가이드

velo-was는 무중단으로 애플리케이션을 배포, 갱신, 제거할 수 있는 핫 디플로이(Hot Deploy) 기능을 제공합니다.

## 설정 방법
`conf/server.yaml` 파일의 `deploy` 섹션에서 핫 디플로이 기능을 활성화하고 관련 설정을 조정할 수 있습니다.

```yaml
server:
  deploy:
    directory: deploy             # 배포 대상 디렉토리 (기본값: deploy)
    hotDeploy: true               # 핫 디플로이 활성화 여부 (true 로 변경)
    scanIntervalSeconds: 5        # 디렉토리 감시 주기 및 복사 대기 시간(디바운스)
```

## 동작 방식
velo-was는 내부적으로 `HotDeployWatcher`와 자바 NIO의 `WatchService`를 사용하여 지정된 `directory` 내부의 `.war` 파일 또는 엑스플로디드(Exploded) 디렉토리 변경 사항을 감시합니다.

- **신규 배포 (ENTRY_CREATE)**: 새로운 `.war` 파일이 감지되면 자동으로 서버에 인스턴스화되어 지정된 패스로 배포됩니다.
- **재배포 (ENTRY_MODIFY)**: 기존에 존재하는 `.war` 파일이 덮어씌워지면 변경을 감지하고, 기존 애플리케이션을 언배포(Undeploy)한 후 새로운 애플리케이션으로 재배포(Redeploy)합니다.
- **배포 해제 (ENTRY_DELETE)**: 배포 디렉토리에서 대상 애플리케이션 파일이나 디렉토리가 삭제되면 서버 내 애플리케이션을 즉시 자동 언배포합니다.

## 주의 사항
- **디바운싱 대기 시간**: 대용량 WAR 파일을 복사할 때 파일 복사 도중에 불완전한 껍데기 파일이 배포되는 것을 막기 위해 `scanIntervalSeconds`에 설정된 시간(초 단위)만큼 변경이 일어나지 않을 때까지 대기한 후 최종 배포를 수행합니다. 
- **클래스로더 격리**: 핫 디플로이 시 기존 애플리케이션의 `WebAppClassLoader`는 JVM 메모리에서 해제(Close) 되고 새로운 클래스로더가 다시 생성되어 애플리케이션이 초기화됩니다. 메모리 누수를 막기 위해, 삭제되는 애플리케이션이 생성했던 리소스(ThreadLocal 등)가 `ServletContextListener`의 `contextDestroyed()` 등에서 올바르게 정리되도록 주의해야 합니다.
