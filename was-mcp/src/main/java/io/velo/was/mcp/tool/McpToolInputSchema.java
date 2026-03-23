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
                sb.append(valueToJson(e.getValue()));
            }
            return sb.append('}').toString();
        }
        return "{}";
    }

    /** Recursively serialize any JSON-compatible value. */
    @SuppressWarnings("unchecked")
    private static String valueToJson(Object v) {
        if (v == null) return "null";
        if (v instanceof String s) return "\"" + escape(s) + "\"";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        if (v instanceof Map<?, ?>) return propertyJson(v);  // recursive for nested objects
        if (v instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                sb.append(valueToJson(item));
            }
            sb.append(']');
            return sb.toString();
        }
        return "\"" + escape(v.toString()) + "\"";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
