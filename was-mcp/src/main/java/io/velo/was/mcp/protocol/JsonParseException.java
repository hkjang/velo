package io.velo.was.mcp.protocol;

/** Thrown when the {@link SimpleJsonParser} encounters malformed JSON. */
public class JsonParseException extends RuntimeException {
    public JsonParseException(String message) {
        super(message);
    }
}
