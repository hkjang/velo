# `was-jsp` Module Guide

The `was-jsp` module is an integrated JSP compiler and runtime that translates Jakarta Server Pages (`.jsp`) into valid Servlets at runtime.

## Key Components

### 1. Extractor & Parser Engine (`JspParser` / `JspDocument`)
Parses raw `.jsp` files (containing mixed HTML and Java snippets) into an abstract syntax tree representation (`JspDocument`).

### 2. The JSP Compiler (`JspCompiler`)
A foundational component that ingests the parsed `JspDocument` and dynamically generates a `TranslatedSource` (valid Java source code bridging `javax.servlet.jsp.HttpJspPage`).
- Leverages the internal Jasper or equivalent ECJ (Eclipse Compiler for Java) backend to compile the translated `.java` file into executable `.class` bytecodes residing in the `scratchDir` initialized by `was-config`.

### 3. Execution Runtime (`JspServlet` & `VeloJspWriter`)
- **`JspServlet`**: The standard entry point mapped via wildcard `*.jsp` in the default `web.xml`. If a `.class` representation of the requested JSP is missing or outdated, it triggers the compilation cycle seamlessly.
- **`VeloJspWriter`**: A specifically tuned implementation of `JspWriter` that buffers stream outputs securely before flushing back down the `was-servlet-core` response proxy.
- **`SimpleElEvaluator`**: The evaluation engine for Jakarta Expression Language (e.g., `${user.name}`). Integrates directly with the `PageContext` boundaries.

### 4. Hot Reloading (`JspResourceManager` / `JspReloadManager`)
Monitors timestamp changes on underlying `.jsp` scripts ondisk. If modified (`developmentMode = true`), the `JspReloadManager` discards the older `CompiledJsp` object instance and signals the `JspServlet` to re-execute compilation automatically without downtime.
