# `was-deploy` Module Guide

The `was-deploy` module bridges the gap between raw web application archive files (`.war`) and the executable `ServletApplication` environments defined in `was-servlet-core`.

## Key Components

### 1. `WarExtractor`
Responsible for the physical unpacking of zipped `.war` files.
- Unzips applications to a designated temporary or scratch directory (typically managed by the OS or configured in `server.yaml`).
- Prepares the standard `WEB-INF` file tree required by the classloader and contexts.

### 2. `WebXmlParser` & `WebXmlDescriptor`
These classes form the XML parsing engine targeting `WEB-INF/web.xml`.
- **Parser**: Converts XML configuration elements (using standard SAX/DOM tooling) into an in-memory `WebXmlDescriptor`.
- **Descriptor**: A strongly-typed representation of Servlet mappings, Filter mappings, Listeners, static initializers, context parameters, and welcome files.

### 3. `WarDeployer`
The overarching coordinator for the deployment cycle.
1. Calls `WarExtractor` to stage the binaries.
2. Invokes `WebXmlParser` to understand the routing definitions.
3. Instantiates `WebAppClassLoader` (from `was-classloader`) to load the application's bytecode.
4. Constructs the final `SimpleServletApplication` and yields it to the `ServletContainer` for live traffic ingestion.
