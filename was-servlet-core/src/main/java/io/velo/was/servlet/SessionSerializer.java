package io.velo.was.servlet;

import java.util.Map;

public interface SessionSerializer {

    byte[] serialize(Map<String, Object> attributes);

    Map<String, Object> deserialize(byte[] serializedAttributes);
}
