# Velo WAS 테스트 가이드

이 가이드는 `velo-was` 프로젝트에서 Git Bash를 이용하여 `LiveWarDeploymentTest`를 실행하는 방법을 설명합니다.
이 테스트는 Bash 환경(Windows의 경우 Git Bash 권장)에서 실행해야 하며, Velo WAS 환경 내에서 WAR 파일 배포 기능이 정상적으로 작동하는지 검증합니다.

## 실행 명령어

터미널(Git Bash 또는 Bash 호환 셸)을 열고 프로젝트 루트 경로에서 다음 명령어를 차례로 입력하여 실행하십시오:

```bash
JAVA_HOME="D:\\project\\velo\\.tools\\jdk\\jdk-21.0.10+7" \
"D:/project/velo/.tools/maven/apache-maven-3.9.13/bin/mvn" test \
  -pl was-bootstrap \
  -am \
  -Dtest=LiveWarDeploymentTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -f d:/project/velo/pom.xml 2>&1 | tail -80
```

### 명령어 개요:
1. `JAVA_HOME="..."`: 빌드 및 테스트 단계에서 사용할 특정 JDK 버전을 가리킵니다. (이곳에서는 JDK 21 사용)
2. `".../bin/mvn" test`: 로컬에 설치된 Maven을 사용하여 테스트 단계를 실행합니다.
3. `-pl was-bootstrap -am`: `was-bootstrap` 모듈과 그 의존성 모듈들을 함께 빌드 및 테스트하도록 지정합니다.
4. `-Dtest=LiveWarDeploymentTest`: `LiveWarDeploymentTest.java` 테스트 케이스만 실행하도록 제한합니다.
5. `-Dsurefire.failIfNoSpecifiedTests=false`: 다른 모듈에 해당 테스트가 존재하지 않더라도 Maven 빌드가 실패하지 않게 합니다.
6. `-f d:/project/velo/pom.xml`: 프로젝트 루트의 `pom.xml` 지정.
7. `2>&1 | tail -80`: 에러 출력을 표준 출력으로 리다이렉션하고 결과의 마지막 80줄만 잘라서 보여줍니다.

## 성공 판별 기준

테스트가 정상적으로 통과하면 콘솔 창 마지막 즈음에 아래와 같은 출력결과를 확인할 수 있습니다:
```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

실패 시 `was-bootstrap` 모듈 아래 `target/surefire-reports` 디렉토리의 로그를 확인하거나, 실행 명령어의 마지막 파이프(`| tail -80`)를 지우고 전체 에러 로그를 확인하세요.
