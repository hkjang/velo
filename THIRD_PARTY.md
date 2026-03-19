# Third-Party Inventory

This repository applies an Apache-2.0 / MIT-first reuse policy for source code.
Operational rules, vendoring structure, and intake workflow are documented in
[docs/ko/oss-reuse-policy.md](docs/ko/oss-reuse-policy.md).

## Governance Files

- `NOTICE`
- `third_party/dependency-policy.json`
- `third_party/reuse-manifest.json`
- `vendor/README.md`
- `scripts/check-oss-compliance.ps1`
- `scripts/import-apache-tomcat-source.ps1`

## Direct Dependency Baseline

| Dependency | Status | License | Notes |
|---|---|---|---|
| `io.netty:*` | allowed | Apache-2.0 | Core transport and HTTP stack |
| `org.slf4j:*` | allowed | MIT | Logging API and simple backend |
| `org.yaml:snakeyaml` | allowed | Apache-2.0 | YAML configuration |
| `jakarta.servlet:jakarta.servlet-api` | review-required | EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0 | Servlet specification API; keep outside Apache/MIT source-vendoring path |
| `jakarta.servlet.jsp:jakarta.servlet.jsp-api` | review-required | EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0 | JSP specification API |
| `jakarta.el:jakarta.el-api` | review-required | EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0 | EL specification API |
| `org.jline:jline` | review-required | BSD-3-Clause | Admin CLI dependency |
| `com.h2database:h2` | review-required | MPL-2.0 OR EPL-1.0 | Test/runtime datasource dependency |
| `org.junit.jupiter:junit-jupiter` | review-required | EPL-2.0 | Test-only dependency |

## Vendored Source Status

- `vendor/upstream/apache-tomcat/11.0.18`
  - License: `Apache-2.0`
  - Upstream archive: `https://dlcdn.apache.org/tomcat/tomcat-11/v11.0.18/src/apache-tomcat-11.0.18-src.tar.gz`
  - NOTICE preserved in vendored tree
- Future source imports must be registered in `third_party/reuse-manifest.json`.
- Source imports with licenses outside `Apache-2.0` or `MIT` must not be committed without legal approval.

## Scope Note

This inventory covers third-party reuse governance. The repository's own product
license is intentionally not declared here because product distribution policy
has not been finalized in this workspace.
