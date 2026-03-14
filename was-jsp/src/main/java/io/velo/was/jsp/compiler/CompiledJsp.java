package io.velo.was.jsp.compiler;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a compiled JSP servlet class.
 */
public record CompiledJsp(
        String className,
        byte[] mainClassBytes,
        Map<String, byte[]> allClassBytes,
        Instant compiledAt
) {
}
