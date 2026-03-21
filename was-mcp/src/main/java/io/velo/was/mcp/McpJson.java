package io.velo.was.mcp;

/**
 * Helpers for building MCP-specific JSON fragments.
 */
final class McpJson {

    private McpJson() {}

    /**
     * Build the {@code result} JSON for the {@code initialize} response.
     *
     * @param serverInfo        server identity
     * @param protocolVersion   negotiated protocol version
     * @param hasTools          whether tools capability is advertised
     * @param hasResources      whether resources capability is advertised
     * @param hasPrompts        whether prompts capability is advertised
     */
    static String initializeResult(McpServerInfo serverInfo,
                                   String protocolVersion,
                                   boolean hasTools,
                                   boolean hasResources,
                                   boolean hasPrompts) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"protocolVersion\":\"").append(escape(protocolVersion)).append('"')
          .append(",\"capabilities\":{");

        boolean firstCap = true;
        if (hasTools) {
            sb.append("\"tools\":{\"listChanged\":false}");
            firstCap = false;
        }
        if (hasResources) {
            if (!firstCap) sb.append(',');
            sb.append("\"resources\":{\"subscribe\":false,\"listChanged\":false}");
            firstCap = false;
        }
        if (hasPrompts) {
            if (!firstCap) sb.append(',');
            sb.append("\"prompts\":{\"listChanged\":false}");
        }
        sb.append('}');

        sb.append(",\"serverInfo\":{\"name\":\"").append(escape(serverInfo.name()))
          .append("\",\"version\":\"").append(escape(serverInfo.version())).append("\"}}");
        return sb.toString();
    }

    static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
