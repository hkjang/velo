package io.velo.was.servlet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JavaSessionSerializer implements SessionSerializer {

    private final SessionSerializationPolicy policy;

    public JavaSessionSerializer(SessionSerializationPolicy policy) {
        this.policy = policy == null ? SessionSerializationPolicy.STRICT : policy;
    }

    @Override
    public byte[] serialize(Map<String, Object> attributes) {
        Map<String, Object> serializableAttributes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            Object value = entry.getValue();
            if (value == null || value instanceof Serializable) {
                serializableAttributes.put(entry.getKey(), value);
                continue;
            }
            if (policy == SessionSerializationPolicy.STRICT) {
                throw new IllegalArgumentException(
                        "Session attribute '%s' is not serializable: %s".formatted(
                                entry.getKey(), value.getClass().getName()));
            }
        }

        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ObjectOutputStream stream = new ObjectOutputStream(buffer)) {
            stream.writeObject(serializableAttributes);
            stream.flush();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize session attributes", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> deserialize(byte[] serializedAttributes) {
        if (serializedAttributes == null || serializedAttributes.length == 0) {
            return Map.of();
        }
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(serializedAttributes))) {
            Object value = stream.readObject();
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof String key) {
                        result.put(key, entry.getValue());
                    }
                }
                return result;
            }
            throw new IllegalStateException("Serialized session payload is not a map");
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Failed to deserialize session attributes", e);
        }
    }
}
