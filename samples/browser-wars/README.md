# Browser WAR Samples

`velo-was` 브라우저 렌더링 호환성을 확인하기 위한 샘플 WAR 소스 모음이다.

포함된 샘플:

- `sample-vanilla-js.war`
- `sample-typescript.war`
- `sample-jquery.war`
- `sample-reactjs.war`
- `sample-vuejs.war`
- `sample-angular.war`

각 샘플은 공통 `dashboard.json` 데이터를 비동기로 읽어 와서 요약 카드, 서비스 목록,
활동 로그를 렌더링한다. `scripts/build-browser-sample-wars.ps1` 가 WAR 패키징을 담당한다.

Angular, React, Vue, jQuery 런타임은 CDN 자산을 사용한다. 샘플 앱 코드와 로컬 JSON/CSS는
WAR 안에 포함된다.

## 빌드

```powershell
.\scripts\use-local-toolchain.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\build-browser-sample-wars.ps1
```

## 로컬 테스트 링크

- JavaScript: `http://127.0.0.1:8080/sample-vanilla-js/`
- TypeScript: `http://127.0.0.1:8080/sample-typescript/`
- jQuery: `http://127.0.0.1:8080/sample-jquery/`
- ReactJS: `http://127.0.0.1:8080/sample-reactjs/`
- VueJS: `http://127.0.0.1:8080/sample-vuejs/`
- Angular: `http://127.0.0.1:8080/sample-angular/`
