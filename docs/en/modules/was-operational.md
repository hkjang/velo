# `was-bootstrap` / `was-admin` Modules Overview

These modules represent the operational frontend points for interacting with the Velo WAS ecosystem.

## 1. `was-bootstrap` (The Start-Up Engine)
The `was-bootstrap` module is effectively the `main()` class implementation. It stitches together all dependent libraries, loads user configurations, and starts server listeners.

- It initializes dependencies on config loaders (YAML parsed via `SnakeYAML`), protocol adapters, Netty bootups, and proxy deployments.
- Provides default internal health, info, and administrative routing configurations before injecting the fallback `ServletContainer`.
- Includes lightweight testing and mocked deployments (like `LiveWarDeploymentTest`) necessary to assert complete startup boundaries natively.

## 2. `was-admin` (Administrative Tooling)
Built for compatibility and systemic observability, this CLI mimics the look, feel, and functionality of conventional JEUS administrative tooling (`jeusadmin`).

- **Interface**: Implemented entirely via standard `JLine`, offering familiar terminal aesthetics with history tracking and auto-completions.
- **Commands**: Orchestrates over 14 functional categories natively translating to 73 distinct execution directives (e.g., Domain info, Server lifecycles, Memory metrics, Active Thread profiles, log adjustments, JMX inspection).
- **Execution Context**: Routes commands to `AdminClient` endpoints (both `LocalAdminClient` in-memory interactions, and theoretically `RemoteAdminClient` for REST configurations).
- **Automation**: Features an internal scripting engine to record repetitive sequences (`record-script` and `run-script`) into flat `.velo` macros that can be executed consecutively without manual oversight.
