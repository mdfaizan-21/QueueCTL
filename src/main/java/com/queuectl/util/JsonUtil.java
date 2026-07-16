package com.queuectl.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized JSON utility using Jackson.
 *
 * <p>Provides a pre-configured singleton {@link ObjectMapper} and
 * convenience methods for serialization/deserialization.
 *
 * <p>Configuration:
 * <ul>
 *   <li>Java 8 time module registered</li>
 *   <li>Unknown properties ignored (forward compatibility)</li>
 *   <li>Timestamps written as strings, not numeric</li>
 * </ul>
 */
public final class JsonUtil {

    private static final Logger logger = LoggerFactory.getLogger(JsonUtil.class);

    private static final ObjectMapper MAPPER = createMapper();

    private JsonUtil() {
        // Utility class — no instantiation
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }

    /**
     * Returns the shared ObjectMapper instance.
     *
     * @return configured ObjectMapper
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /**
     * Serializes an object to JSON string.
     *
     * @param obj the object to serialize
     * @return JSON string
     * @throws IllegalArgumentException if serialization fails
     */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("JSON serialization failed for: {}", obj, e);
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    /**
     * Serializes an object to pretty-printed JSON string.
     *
     * @param obj the object to serialize
     * @return pretty JSON string
     * @throws IllegalArgumentException if serialization fails
     */
    public static String toPrettyJson(Object obj) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("JSON pretty serialization failed for: {}", obj, e);
            throw new IllegalArgumentException("Failed to serialize to JSON", e);
        }
    }

    /**
     * Deserializes a JSON string to the given type.
     *
     * @param json  the JSON string
     * @param clazz the target class
     * @param <T>   the target type
     * @return deserialized object
     * @throws IllegalArgumentException if deserialization fails
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            logger.error("JSON deserialization failed for type {}: {}", clazz.getSimpleName(), json, e);
            throw new IllegalArgumentException("Failed to deserialize JSON: " + e.getMessage(), e);
        }
    }
}
