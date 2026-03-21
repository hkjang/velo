package io.velo.was.mcp.tool;

import java.util.List;
import java.util.Map;

/**
 * JSON Schema (subset) describing the input parameters for a {@link McpTool}.
 *
 * <p>Only the subset relevant to MCP tool definitions is modelled here:
 * object type with named properties and an optional required list.
 */
public record McpToolInputSchema(
        /** Always "object" per MCP spec. */
        String type,
        /** Property name → property schema object (type, description, enum, …). */
        Map<String, Object> properties,
        /** Names of required properties. */
        List<String> required
) {

    /** Convenience constructor for an object schema with no required fields. */
    public McpToolInputSchema(Map<String, Object> properties) {
        this("object", properties, List.of());
    }

    /** Serialize to a JSON Schema snippet for use in tools/list responses. */
    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"type\":\"").append(type).append('"');

        if (properties != null && !properties.isEmpty()) {
            sb.append(",\"properties\":{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(entry.getKey())).append("\":");
                sb.append(propertyJson(entry.getValue()));
            }
            sb.append('}');
        }

        if (required != null && !required.isEmpty()) {
            sb.append(",\"required\":[");
            boolean first = true;
            for (String req : required) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(req)).append('"');
            }
            sb.append(']');
        }

        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String propertyJson(Object prop) {
        if (prop instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder(128).append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<String, Object>) map).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(e.getKey().toString())).append("\":");
                Object v = e.getValue();
                if (v instanceof String s) {
                    sb.append('"').append(escape(s)).append('"');
                } else if (v instanceof List<?> list) {
                    sb.append('[');
                    boolean fi = true;
                    for (Object item : list) {
                        if (!fi) sb.append(',');
                        fi = false;
                        sb.append('"').append(escape(item.toString())).append('"');
                    }
                    sb.append(']');
                } else {
                    sb.append(v);
                }
            }
            return sb.append('}').toString();
        }
        return "{}";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
