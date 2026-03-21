package io.velo.was.mcp.resource;

/**
 * Reads the content of a specific MCP resource.
 *
 * <p>Implementations must be thread-safe.
 */
@FunctionalInterface
public interface McpResourceProvider {

    /**
     * Read the resource content.
     *
     * @param uri the URI from the {@code resources/read} request
     * @return resource contents; never {@code null}
     * @throws Exception if the resource cannot be read
     */
    McpResourceContents read(String uri) throws Exception;
}
