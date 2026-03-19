# Apache Tomcat 11.0.18 Adapter Layer

Use this directory for integration glue that connects vendored Tomcat code to
`velo-was` modules without modifying the upstream snapshot.

## Candidate integrations

- Servlet mapping and dispatcher adapter
- Filter-chain bridge
- Web resource and cache adapter
- WAR / webapp loader bridge

## Current status

- `was-servlet-core` exposes `TOMCAT_COMPAT` as the default mapping strategy.
- The current mapper mirrors Tomcat wrapper precedence and wildcard
  backtracking without depending on Catalina runtime types yet.
- Filter matching now mirrors Tomcat's URL-pattern-first and servlet-name-second
  chain ordering, including extension patterns.
- Named `RequestDispatcher` forward/include now route through the compatibility
  bridge with Tomcat-like filter selection.
- Forward dispatch now clears only the buffered body and preserves response
  headers, which aligns better with Tomcat's `ApplicationDispatcher`.
- Forward dispatch now rejects already committed responses instead of trying to
  reset the buffer, which matches Tomcat's `ApplicationDispatcher` guard.
- Forward dispatch now closes the effective response for additional output once
  the target returns, so caller writes after `forward()` no longer leak into
  the final body.
- Nested include dispatches now restore the caller's
  `jakarta.servlet.include.*` attributes after completion.
- RequestDispatcher and AsyncContext now preserve user-supplied
  request/response wrappers across dispatch boundaries.
- Full `ApplicationDispatcher` attribute parity is still pending.
